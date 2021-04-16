package com.example.cameracapturedemo;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class CameraController {
    private static final String TAG = "CameraController";
    private CameraDevice mCameraDevice;
    private int mCameraId = 0;
    private Size mPreviewSize = new Size(1440, 1080);
    private Size mCaptureSize = new Size(4000, 3000);
    private Size mVideoSize = new Size(1920, 1080);
    private ImageReader mImageReader;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest mPreviewRequest;
    private CameraCaptureSession mCaptureSession;
    private File mFile;
    private AutoFitTextureView mPreviewTexture;
    private Activity mActivity;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private int mPhoneOrientation;
    private int mCurrentMode = CameraConstant.PHOTO_MODE;
    private static final int BACK_CAMERA_ID = 0;
    private static final int FRONT_CAMERA_ID = 1;

    private static final String APP_FLASH_MODE_ON = "on";
    private static final String APP_FLASH_MODE_OFF = "off";
    private static final String APP_FLASH_MODE_AUTO = "auto";
    private static final String APP_FLASH_MODE_TORCH = "torch";
    private String mFlashMode = APP_FLASH_MODE_OFF;

    public static final float PREVIEW_SIZE_RATIO_OFFSET = 0.01f;
    private float mTargetRatio = 1.333f;
    //private float target = 1.777f;
    private CameraManager manager;
    private int mSensorOrientation;
    private MediaRecorder mMediaRecorder;
    private float density;

    public CameraController(Activity activity, AutoFitTextureView textureView) {
        mPreviewTexture = textureView;
        mActivity = activity;
        manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
    }

    public void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    public void openCamera() {
        if (ContextCompat.checkSelfPermission(mActivity, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        try {
            if (mCurrentMode == CameraConstant.VIDEO_MODE) {
                mMediaRecorder = new MediaRecorder();
            }
            manager.openCamera(String.valueOf(mCameraId), mStateCallback, mBackgroundHandler);
        } catch (Exception exception) {
            Log.e(TAG, "exception:" + exception);
        }
    }

    private void choosePreviewAndCaptureSize() {
        CameraCharacteristics characteristics
                = null;
        try {
            characteristics = manager.getCameraCharacteristics(String.valueOf(mCameraId));
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

        StreamConfigurationMap map = characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] previewSizeMap = map.getOutputSizes(SurfaceTexture.class);//preview
        for (int i = 0; i < previewSizeMap.length; i++) {
            Log.d("yanweitim", "testing = " + previewSizeMap[i].getWidth() + "," + previewSizeMap[i].getHeight());
        }
        Size[] captureSizeMap = map.getOutputSizes(ImageFormat.JPEG);//拍照
//        Size[] vedioSizeMap = map.getOutputSizes(MediaRecorder.class);//拍照
        int screenWidth = Utils.getScreenWidth(mActivity.getApplicationContext());
        mPreviewSize = getPreviewSize(previewSizeMap, mTargetRatio, screenWidth);
        if (mCurrentMode == CameraConstant.VIDEO_MODE) {
            mVideoSize = mPreviewSize;
        }

        mCaptureSize = getPictureSize(mTargetRatio, captureSizeMap);
        mImageReader = ImageReader.newInstance(mCaptureSize.getWidth(), mCaptureSize.getHeight(),
                ImageFormat.JPEG, /*maxImages*/2);//RAW_SENSOR YUV_420_888
        mImageReader.setOnImageAvailableListener(
                mOnImageAvailableListener, mBackgroundHandler);
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mPreviewTexture.setAspectRatio(
                        mPreviewSize.getHeight(), mPreviewSize.getWidth());//
            }
        });
    }

    public Size getPreviewSize(Size[] mapPreview, float targetRatio, int screenWidth) {
        Size previewSize = null;
        int minOffSize = Integer.MAX_VALUE;
        for (int i = 0; i < mapPreview.length; i++) {
            float ratio = mapPreview[i].getWidth() / (float) mapPreview[i].getHeight();
            if (Math.abs(ratio - targetRatio) > PREVIEW_SIZE_RATIO_OFFSET) {
                continue;
            }
            int diff = Math.abs(mapPreview[i].getHeight() - screenWidth);
            if (diff < minOffSize) {
                previewSize = mapPreview[i];
                minOffSize = Math.abs(mapPreview[i].getHeight() - screenWidth);
            } else if ((diff == minOffSize) && (mapPreview[i].getHeight() > screenWidth)) {
                previewSize = mapPreview[i];
            }
        }
        return previewSize;
    }

    public Size getPictureSize(float targetRatio, Size[] mapPicture) {
        Size maxPicSize = new Size(0, 0);
        for (int i = 0; i < mapPicture.length; i++) {
            float ratio = mapPicture[i].getWidth() / (float) mapPicture[i].getHeight();
            if (Math.abs(ratio - targetRatio) > PREVIEW_SIZE_RATIO_OFFSET) {
                continue;
            }
            if (mapPicture[i].getWidth() * mapPicture[i].getHeight() >= maxPicSize.getWidth() * maxPicSize.getHeight()) {
                maxPicSize = mapPicture[i];
            }
        }
        return maxPicSize;
    }

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.d(TAG, "onImageAvailable");
            saveImage(reader);
        }
    };

    private void saveNoWaterMark(byte[] bytes, Image image) {
        BitmapRegionDecoder decoder = null;
        try {
            decoder = BitmapRegionDecoder.newInstance(bytes, 0, bytes.length, true);
        } catch (IOException e) {
            e.printStackTrace();
        }
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opt);

        int w = opt.outWidth;
        int h = opt.outHeight;
        int d = w > h ? h : w;

        final int target = mActivity.getResources().getDimensionPixelSize(R.dimen.thumbnail_size);
        int sample = 1;
        if (d > target) {
            while (d / sample / 2 > target) {
                sample *= 2;
            }
        }
        int st = sample * target;
        final Rect rect = new Rect((w - st) / 2, (h - st) / 2, (w + st) / 2, (h + st) / 2);
        Bitmap showThumbnail = decoder.decodeRegion(rect, opt);
        Matrix matrix = new Matrix();
        matrix.postRotate(getJpegRotation(mCameraId, mPhoneOrientation));
        if (lenFaceFront()) {
            matrix.postScale(-1, 1);
        }
        Bitmap srcBitmap = BitmapFactory.decodeByteArray(bytes,0,bytes.length,null);
        Bitmap bitmapThumbnail = Bitmap.createBitmap(showThumbnail, 0, 0, showThumbnail.getWidth(),
                showThumbnail.getHeight(), matrix, true);
        mCameraCallback.onThumbnailCreated(bitmapThumbnail);
        //mCameraCallback.onThumbnailCreated(srcBitmap);

        FileOutputStream output = null;
        try {
            output = new FileOutputStream(mFile);
            output.write(bytes);//保存图片到文件

            Uri photouri = Uri.fromFile(mFile);

            mActivity.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, photouri));
            mCameraCallback.onTakePictureFinished();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            image.close();
            if (null != output) {
                try {
                    output.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void saveWithWaterMark(byte[] bytes, Image image) {
        Bitmap bitmapStart = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

        Matrix matrix = new Matrix();
        matrix.postRotate(getJpegRotation(mCameraId, mPhoneOrientation));
        if (lenFaceFront()) {
            matrix.postScale(-1, 1);
        }
        Bitmap bitmapSrc = Bitmap.createBitmap(bitmapStart, 0, 0, bitmapStart.getWidth(),
                bitmapStart.getHeight(), matrix, true);
        mCameraCallback.onThumbnailCreated(bitmapSrc);
        Bitmap bitmapNew = Bitmap.createBitmap(bitmapSrc.getWidth(), bitmapSrc.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvasNew = new Canvas(bitmapNew);
        canvasNew.drawBitmap(bitmapSrc, 0, 0, null);

        Paint paintText = new Paint();
        paintText.setColor(Color.argb(80, 255, 255, 255));
        if (lenFaceFront()) {
            paintText.setTextSize(60);
        } else {
            paintText.setTextSize(150);
        }
        paintText.setDither(true);
        paintText.setFilterBitmap(true);
        Rect rectText = new Rect();
        String drawTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        paintText.getTextBounds(drawTime, 0, drawTime.length(), rectText);
        int beginX = bitmapNew.getWidth() - rectText.width() - 100;
        int beginY = bitmapNew.getHeight() - rectText.height();
        canvasNew.drawText(drawTime, beginX, beginY, paintText);
        FileOutputStream output = null;
        try {
            output = new FileOutputStream(mFile);
            bitmapNew.compress(Bitmap.CompressFormat.JPEG, 100, output);
            output.flush();
            Uri photouri = Uri.fromFile(mFile);
            mActivity.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, photouri));
            mCameraCallback.onTakePictureFinished();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            image.close();
            if (null != output) {
                try {
                    output.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void saveImage(ImageReader reader) {
        Image image = reader.acquireNextImage();
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);

        boolean addMark = SharedPreferencesController.getInstance(mActivity).spGetBoolean(CameraConstant.ADD_WATER_MARK);
        if (addMark) {
            saveWithWaterMark(bytes, image);
        } else {
            saveNoWaterMark(bytes, image);
        }
    }

    private void tipTakePicFinish() {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(mActivity.getApplicationContext(), "拍照完成", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            choosePreviewAndCaptureSize();
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            cameraDevice.close();
            mCameraDevice = null;
            mActivity.finish();
        }
    };

    public void updateManualFocus(Rect rect) {
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{new MeteringRectangle(rect, 1000)});
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, new MeteringRectangle[]{new MeteringRectangle(rect, 1000)});
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);//对焦模式下发
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START);
    }

    /**
     * 获取点击区域
     *
     * @param x：手指触摸点x坐标
     * @param y:         手指触摸点y坐标
     */
    public Rect getFocusRect(int x, int y) {
        int screenW = getScreenWidth(mActivity.getApplicationContext());//获取屏幕长度
        int screenH = getScreenHeight(mActivity.getApplicationContext());//获取屏幕宽度

        //因为获取的SCALER_CROP_REGION是宽大于高的，也就是默认横屏模式，竖屏模式需要对调width和height
        int realPreviewWidth = mPreviewSize.getHeight();
        int realPreviewHeight = mPreviewSize.getWidth();

        //根据预览像素与拍照最大像素的比例，调整手指点击的对焦区域的位置
        float focusX = realPreviewWidth * 1.0f / screenW * x;
        float focusY = realPreviewHeight * 1.0f / screenH * y;

        //获取SCALER_CROP_REGION，也就是拍照最大像素的Rect
        Rect totalPicSize = mPreviewRequest.get(CaptureRequest.SCALER_CROP_REGION);

        //计算出摄像头剪裁区域偏移量
        int cutDx = (totalPicSize.height() - mPreviewSize.getHeight()) / 2;

        //我们默认使用10dp的大小，也就是默认的对焦区域长宽是10dp，这个数值可以根据需要调节
        float width = dip2px(mActivity, 10.0f);
        float height = dip2px(mActivity, 10.0f);

        //返回最终对焦区域Rect
        return new Rect((int) focusY, (int) focusX + cutDx, (int) (focusY + height), (int) (focusX + cutDx + width));
    }

    public float dip2px(Context context, float dip) {
        if (density <= 0) {
            setDensity(context);
        }
        return dip * density + 0.5f * (dip >= 0 ? 1 : -1);
    }

    private void setDensity(Context context) {
        if (density <= 0) {
            DisplayMetrics dm = context.getResources().getDisplayMetrics();
            density = dm.density;
        }
    }

    public int getScreenWidth(Context context) {
        return context.getResources().getDisplayMetrics().widthPixels;
    }

    public int getScreenHeight(Context context) {
        return context.getResources().getDisplayMetrics().heightPixels;
    }

    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mPreviewTexture.getSurfaceTexture();
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            Surface surface = new Surface(texture);
            mPreviewRequestBuilder
                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {


                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            if (null == mCameraDevice) {
                                return;
                            }

                            mCaptureSession = cameraCaptureSession;
                            setPreviewFrameParams();
                            updatePreview();
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {

                        }
                    }, mBackgroundHandler
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void updatePreview() {
        try {
            mPreviewRequest = mPreviewRequestBuilder.build();
            mCaptureSession.setRepeatingRequest(mPreviewRequest,
                    mCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            Log.d("yanweitim","onCaptureCompleted");
            /*Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
             if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState
                || CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            mPreviewRequest = mPreviewRequestBuilder.build();
            try {
                mCaptureSession.setRepeatingRequest(mPreviewRequest, null, mBackgroundHandler);
            } catch (CameraAccessException e) { Log.e(TAG, "setRepeatingRequest failed, errMsg: " + e.getMessage()); } }*/
        }
    };


    public void captureStillPicture() {
        try {
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());

            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {

                }
            };

            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getJpegRotation(mCameraId, mPhoneOrientation));
            //90
            //0
            //180
            //270

            mCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void closeCamera() {
        closeSessionAndImageReader();
        if (null != mCameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    public void closeSession() {
        if (null != mCaptureSession) {
            mCaptureSession.close();
            mCaptureSession = null;
        }
    }

    public void closeSessionAndImageReader() {
        if (null != mCaptureSession) {
            mCaptureSession.close();
            mCaptureSession = null;
        }

        if (null != mImageReader) {
            mImageReader.close();
            mImageReader = null;
        }
    }

    /*public void setFlashMode(String flashMode) {
        mFlashMode = flashMode;
        switch (flashMode) {
            case APP_FLASH_MODE_ON:
                mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE);
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                break;
            case APP_FLASH_MODE_OFF:
                mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                break;
            case APP_FLASH_MODE_AUTO:
                mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE);
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                break;
            case APP_FLASH_MODE_TORCH:
                mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                break;
        }
        updatePreview();
    }*/

    private void setPreviewFrameParams() {
        mPreviewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY,//iso
                100);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        //characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
        mPreviewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME,
                100l);//快门打开时间 ns
    }

    public void setPhotoPath(File file) {
        mFile = file;
    }

    public void changeToFourRatioThird() {
        mTargetRatio = 1.333f;
        //closeCamera();
        closeSessionAndImageReader();
        choosePreviewAndCaptureSize();
        createCameraPreviewSession();
    }

    public void changToSixTeenRatioNine() {
        mTargetRatio = 1.777f;//16:9
        closeSessionAndImageReader();
        //closeCamera();
        choosePreviewAndCaptureSize();
        createCameraPreviewSession();
    }

    public void changeCameraId() {
        closeCamera();
        updateCameraId();//0 1
        openCamera();
    }

    private void updateCameraId() {
        try {
            CameraCharacteristics characteristics
                    = manager.getCameraCharacteristics(String.valueOf(mCameraId));

            // We don't use a front facing camera in this sample.
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);

            if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                mCameraId = BACK_CAMERA_ID;
            } else {
                mCameraId = FRONT_CAMERA_ID;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void openFlashMode() {
        mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
        updatePreview();
    }

    public void closeFlashMode() {
        mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
        updatePreview();
    }

    private CameraControllerInterFaceCallback mCameraCallback;

    public void setPhoneDeviceDegree(int degree) {
        mPhoneOrientation = degree;
    }

    public int getJpegRotation(int cameraId, int orientation) {
        int rotation = 0;
        if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) {
            orientation = 0;
        }
        if (cameraId == -1) {
            cameraId = 0;
        }
        CameraCharacteristics cameraInfo = null;
        try {
            cameraInfo = manager.getCameraCharacteristics(String.valueOf(cameraId));
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        if (cameraInfo.get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_FRONT) {//front camera
            rotation = (mSensorOrientation - orientation + 360) % 360;
        } else {// back-facing camera
            rotation = (mSensorOrientation + orientation + 360) % 360;
        }
        return rotation;
    }

    public boolean lenFaceFront() {
        CameraCharacteristics cameraInfo = null;
        try {
            cameraInfo = manager.getCameraCharacteristics(String.valueOf(mCameraId));
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        if (cameraInfo.get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_FRONT) {//front camera
            return true;
        }

        return false;
    }

    public void setCurrentMode(int currentMode) {
        mCurrentMode = currentMode;
    }

    public void setTargetRatio(float ratio) {
        mTargetRatio = ratio;
    }

    public void startVideoRecording() {
        try {
            closeSession();
            choosePreviewAndCaptureSize();
            setUpMediaRecorder();

            SurfaceTexture texture = mPreviewTexture.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            List<Surface> surfaces = new ArrayList<>();

            // Set up Surface for the camera preview
            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            mPreviewRequestBuilder.addTarget(previewSurface);

            // Set up Surface for the MediaRecorder
            Surface recorderSurface = mMediaRecorder.getSurface();
            surfaces.add(recorderSurface);
            mPreviewRequestBuilder.addTarget(recorderSurface);

            // Start a capture session
            // Once the session starts, we can update the UI and start recording
            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    mCaptureSession = cameraCaptureSession;
                    updatePreview();
                    mMediaRecorder.start();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

                }
            }, null);
        } catch (CameraAccessException | IOException e) {
            e.printStackTrace();
        }
    }

    public void stopVideoRecording() {
        mMediaRecorder.stop();
        mMediaRecorder.reset();

        final int target = mActivity.getResources().getDimensionPixelSize(R.dimen.thumbnail_size);
        Uri uri = Uri.fromFile(mFile);

        Bitmap bitmapThumbnail = createVideoThumbnailBitmap(mFile.toString(), null, target);
        if (mCameraCallback != null)
            mCameraCallback.onThumbnailCreated(bitmapThumbnail);
        mActivity.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri));
        closeSession();
        choosePreviewAndCaptureSize();
        createCameraPreviewSession();
    }

    private static Bitmap createVideoThumbnailBitmap(String filePath, FileDescriptor fd,
                                                     int targetWidth) {
        Bitmap bitmap = null;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            if (filePath != null) {
                retriever.setDataSource(filePath);
            } else {
                retriever.setDataSource(fd);
            }
            bitmap = retriever.getFrameAtTime(-1);
        } catch (IllegalArgumentException ex) {
            // Assume this is a corrupt video file
        } catch (RuntimeException ex) {
            // Assume this is a corrupt video file.
        } finally {
            try {
                retriever.release();
            } catch (RuntimeException ex) {
                // Ignore failures while cleaning up.
            }
        }
        if (bitmap == null) return null;

        // Scale down the bitmap if it is bigger than we need.
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width > targetWidth) {
            float scale = (float) targetWidth / width;
            int w = Math.round(scale * width);
            int h = Math.round(scale * height);
            bitmap = Bitmap.createScaledBitmap(bitmap, w, h, true);
        }
        return bitmap;
    }

    private void setUpMediaRecorder() throws IOException {
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setOutputFile(mFile.getPath());
        mMediaRecorder.setVideoEncodingBitRate(10000000);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mMediaRecorder.setOrientationHint(getJpegRotation(mCameraId, mPhoneOrientation));
        mMediaRecorder.prepare();
    }

    public void setCameraId(int cameraId) {
        mCameraId = cameraId;
    }

    interface CameraControllerInterFaceCallback {
        void onThumbnailCreated(Bitmap bitmap);

        void onTakePictureFinished();
    }

    public void setCameraControllerInterFaceCallback(CameraControllerInterFaceCallback cameraCallback) {
        mCameraCallback = cameraCallback;
    }
}
