package com.example.nan02;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import android.hardware.Camera;
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
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.StrictMode;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.Policy;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;


public class CameraController {

    public void setPhoneDeviceDegree(int degree) {
        mPhoneOrientation = degree;
    }

    public CameraController(Activity activity, AutoFitTextureView textureView) {
        mPreviewTexture = textureView;
        mActivity = activity;
        manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
    }
    private CameraControllerInterFaceCallback mCameraCallback;

    interface CameraControllerInterFaceCallback{
        void onThumbnailCreated(Bitmap bitmap);
        void onTakePictureFinished();
        void startRecordVideo();
    }
    public void setCameraControllerInterFaceCallback(CameraControllerInterFaceCallback cameraCallback){
        mCameraCallback = cameraCallback;
    }
    private Size mPreviewSize = new Size(14040, 1080);
    private Size mCaptureSize = new Size(4000, 3000);
    private Size mVideoSize = new Size(1920, 1080);
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    //????????????
    public void startBackgroundThread(){
        //HandlerThread???Android API??????????????????????????????????????????????????????????????????????????????Looper?????????
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private Activity mActivity;
    private MediaRecorder mMediaRecorder;
    private CameraManager manager;
    private String mCameraId = "0";
    private  boolean mCurrentMode = true;
    //1.????????????
    public void openCamera() {
        if (ContextCompat.checkSelfPermission(mActivity, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(mActivity,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        try {
            if(mCurrentMode){
                mMediaRecorder = new MediaRecorder();
            }
            manager.openCamera(String.valueOf(mCameraId), mStateCallback, mBackgroundHandler);
        } catch (Exception exception) {
            Log.e("ERROR", "exception:" + exception);
        }
    }
    public void closeCamera() {
        closeSession();
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

    private CameraDevice mCameraDevice;
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            creatImageReader();//????????????
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
    private int mPhoneOrientation;
    private int mSensorOrientation;
    /**TODO ??????*/
    //??????????????????
    public void beginTakePicture() {
        //?????????????????????????????????CaptureRequest
        CaptureRequest.Builder captureBuilder = null;
        try{
            captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());//??????CaptureRequest?????????mImageReader
        }catch (CameraAccessException e){
            e.printStackTrace();
        }
        //???????????????????????????????????????????????????????????????????????????????????????
        CameraCaptureSession.CaptureCallback CaptureCallback =
                new CameraCaptureSession.CaptureCallback(){
                    @Override
                    public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                                   @NonNull CaptureRequest request,
                                                   @NonNull TotalCaptureResult result) {
                    }
                };
        try{
            //??????????????????
           captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getJpegRotation(Integer.parseInt(mCameraId), mPhoneOrientation));
            //???????????????????????????????????????????????????????????????captureBuilder??????ImageReader??????target????????????????????????ImageReader???onImageAvailable()??????????????????
            mCaptureSession.capture(captureBuilder.build(),CaptureCallback,mBackgroundHandler);
        }catch (CameraAccessException e){
            e.printStackTrace();
        }

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

    //??????????????????
    public void setPhotoPath(File file) { mFile = file; }
    public void startRecordingVideo() {
        try{
            closeSession();//TODO 1
            choosePreviewAndCaptureSize();//TODO 2
            setUpMediaRecorder();//TODO 3

            SurfaceTexture texture = mPreviewTexture.getSurfaceTexture();
            assert  texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(),mPreviewSize.getHeight());
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            List<Surface> surfaces = new ArrayList<>();

            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            mPreviewRequestBuilder.addTarget(previewSurface);

            Surface recorderSurface = mMediaRecorder.getSurface();
            surfaces.add(recorderSurface);
            mPreviewRequestBuilder.addTarget(recorderSurface);


            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    mCaptureSession = session;
                    updatePreview();
                    mCameraCallback.startRecordVideo();
                    mMediaRecorder.start();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                }
            },null);
        }catch (CameraAccessException  | IOException e){
            e.printStackTrace();
        }
    }
    private void setUpMediaRecorder() throws IOException{
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mFile = new File(Environment.getExternalStorageDirectory(),"DCIM/Camera/"+System.currentTimeMillis()+".mp4");
        mMediaRecorder.setOutputFile(mFile.getPath());
        mMediaRecorder.setVideoEncodingBitRate(10000000);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setVideoSize(mVideoSize.getWidth(),mVideoSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mMediaRecorder.setOrientationHint(getJpegRotation(Integer.parseInt(mCameraId), mPhoneOrientation));//????????????
        mMediaRecorder.prepare();

    }

    private ImageReader mImageReader;//?????????????????????????????????????????????
    private AutoFitTextureView mPreviewTexture;
    private float mTargetRatio = 1.333f;
    //????????????
    private void creatImageReader() {
        //?????????????????????????????????????????????????????????????????????????????????????????????????????????
        mImageReader = ImageReader.newInstance(mCaptureSize.getWidth(),mCaptureSize.getHeight(),
                ImageFormat.JPEG,2);
        //??????ImageReader???????????????????????????????????????????????????onImageAvailable??????????????????????????????????????????????????????????????????????????????
        mImageReader.setOnImageAvailableListener(mOnImageAvailableListener,mBackgroundHandler);
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mPreviewTexture.setAspectRatio(mPreviewSize.getHeight(),mPreviewSize.getWidth());

            }
        });
    }
    private void choosePreviewAndCaptureSize() {
        CameraCharacteristics characteristics = null;
        try {
            characteristics = manager.getCameraCharacteristics(String.valueOf(mCameraId));
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        StreamConfigurationMap map = characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] previewSizeMap = map.getOutputSizes(SurfaceTexture.class);
        Size[] videoSizeMap = map.getOutputSizes(MediaRecorder.class);

        int screenWidth = getScreenWidth(mActivity.getApplicationContext());
        mPreviewSize = getPreviewSize(previewSizeMap, mTargetRatio, screenWidth);//????????????
//        mVideoSize = getVideoSize(mTargetRatio, videoSizeMap);

        if (mCurrentMode ) {
            mVideoSize = mPreviewSize;
        }
        mVideoSize = mPreviewSize;
        mCaptureSize = getVideoSize(mTargetRatio, previewSizeMap);
        mImageReader = ImageReader.newInstance(mCaptureSize.getWidth(), mCaptureSize.getHeight(),
                ImageFormat.JPEG, /*maxImages*/2);//RAW_SENSOR YUV_420_888
        mImageReader.setOnImageAvailableListener(
                mOnImageAvailableListener, mBackgroundHandler);
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mPreviewTexture.setAspectRatio(
                        mPreviewSize.getHeight(), mPreviewSize.getWidth());
            }
        });

    }

    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CameraCaptureSession mCaptureSession;
    private CaptureRequest mPreviewRequest;
    private File mFile;
    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mPreviewTexture.getSurfaceTexture();
            texture.setDefaultBufferSize(mPreviewSize.getWidth(),mPreviewSize.getHeight());
            Surface surface = new Surface(texture);
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);
            mCameraDevice.createCaptureSession(Arrays.asList(surface,mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            if(null == mCameraDevice){
                                return;
                            }
                            mCaptureSession = cameraCaptureSession;
                            setParams();
                            updatePreview();
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                        }
                    },mBackgroundHandler);
        }catch (CameraAccessException e){
            e.printStackTrace();
        }
    }
    //??????????????? createCameraPreviewSession
    private void setParams() {
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        mPreviewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME,100l);//?????????????????? ns
//        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);//????????????
//        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);//??????????????????,??????????????????????????????
    }
    //createCameraPreviewSession
    private void updatePreview() {
        //????????????????????????
        mPreviewRequest = mPreviewRequestBuilder.build();
        try{
            //?????????????????????????????????????????????????????????????????????????????????
            mCaptureSession.setRepeatingRequest(mPreviewRequest,//mPreviewRequest???target??????????????????surface?????????????????????
                    // ????????????setRepeatingRequest???????????????????????????
                    mCaptureCallback,mBackgroundHandler);
        }catch (CameraAccessException e){
            e.printStackTrace();
        }
    }

    // creatImageReader
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            saveImage(reader);
        }
    };
    //????????????
    private void saveImage(ImageReader reader) {
        Image mImage = reader.acquireNextImage();//acquireNextImage???acquireLatestImage????????????????????????
//            Image mImage = reader.acquireLatestImage();
        ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);

        boolean addMark = SharedPreferencesController.getInstance(mActivity).spGetBoolean("add_water_mark");
        if (addMark) {
            saveWithWaterMark(bytes, mImage); //???????????????
        } else {
            saveNoWaterMark(bytes, mImage);//??????????????????
        }

    }
    //??????????????????????????????
    private void saveWithWaterMark(byte[] bytes, Image image) {
        Bitmap bitmapStart = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

        Matrix matrix = new Matrix();//??????

        //TODO ??????????????????????????????90???????????????????????????????????????????????????????????????
       /**matrix.postRotate(getJpegRotation(Integer.parseInt(mCameraId), mPhoneOrientation));
        if (lenFaceFront()) {
            matrix.postScale(-1, 1);
        }*/

        Bitmap bitmapSrc = Bitmap.createBitmap(bitmapStart, 0, 0, bitmapStart.getWidth(), bitmapStart.getHeight(), matrix, true);
        mCameraCallback.onThumbnailCreated(bitmapSrc);
        Bitmap bitmapNew = Bitmap.createBitmap(bitmapSrc.getWidth(), bitmapSrc.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvasNew = new Canvas(bitmapNew);
        canvasNew.drawBitmap(bitmapSrc, 0, 0, null);

        Paint paintText = new Paint();//???????????????
        paintText.setColor(Color.argb(255, 255, 255, 255));
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
    //?????????????????????????????????
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
        matrix.postRotate(getJpegRotation(Integer.parseInt(mCameraId), mPhoneOrientation));
        //????????????
        if (lenFaceFront()) {
            matrix.postScale(-1, 1);
        }
        Bitmap srcBitmap = BitmapFactory.decodeByteArray(bytes,0,bytes.length,null);
        Bitmap bitmapThumbnail = Bitmap.createBitmap(showThumbnail, 0, 0, showThumbnail.getWidth(),
                showThumbnail.getHeight(), matrix, true);
        mCameraCallback.onThumbnailCreated(bitmapThumbnail);
        mCameraCallback.onThumbnailCreated(srcBitmap);
        FileOutputStream output = null;
        try {
            output = new FileOutputStream(mFile);
            output.write(bytes);//?????????????????????

            Uri photouri = Uri.fromFile(mFile);
//            Toast.makeText(mActivity,"???????????????"+mFile.toString(),Toast.LENGTH_SHORT).show();
            Log.d("????????????",mFile.toString());
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
    //saveNoWaterMark????????????
    //????????????
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

    //choosePreviewAndCaptureSize
    public int getScreenWidth(Context context) {//????????????
        return context.getResources().getDisplayMetrics().widthPixels;
    }
    //???????????????getFocusRect
    public int getScreenHeight(Context context) {//??????????????????
        return context.getResources().getDisplayMetrics().heightPixels;
    }
    //choosePreviewAndCaptureSize
    public static final float PREVIEW_SIZE_RATIO_OFFSET = 0.01f;
    public Size getPreviewSize(Size[] mapPreview, float targetRatio, int screenWidth) {//??????????????????????????????????????????????????????
        Size previewSize = null;
        int minOffSize = Integer.MAX_VALUE;
        for (int i = 0; i < mapPreview.length; i++) {
            float ratio = mapPreview[i].getWidth() / (float)mapPreview[i].getHeight();
            if(Math.abs(ratio - targetRatio) > PREVIEW_SIZE_RATIO_OFFSET){
                continue;
            }
            int diff = Math.abs(mapPreview[i].getHeight() - screenWidth);
            if(diff < minOffSize){
                previewSize = mapPreview[i];
                minOffSize = Math.abs(mapPreview[i].getHeight() - screenWidth);
            }else if((diff == minOffSize) && (mapPreview[i].getHeight() > screenWidth)){
                previewSize = mapPreview[i];
            }
        }
        return  previewSize;
    }
    //choosePreviewAndCaptureSize
    public Size getVideoSize(float targetRatio, Size[] mapVideo) {
        Size maxVideoPicSize = new Size(0, 0);
        for (Size size : mapVideo) {
            float ratio = size.getWidth() / (float) size.getHeight();
            if (Math.abs(ratio - targetRatio) > PREVIEW_SIZE_RATIO_OFFSET) {
                continue;
            }
            if (size.getWidth() * size.getHeight() >= maxVideoPicSize.getWidth() * maxVideoPicSize.getHeight()) {
                maxVideoPicSize = size;
            }
        }
        return maxVideoPicSize;
    }
    //createCameraPreviewSession
    private CameraCaptureSession.CaptureCallback mCaptureCallback =
            new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                                @NonNull CaptureRequest request,
                                                @NonNull CaptureResult partialResult) {
                }
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                }
            };

    //????????????
    public void stopRecordingVideo() {
        //Stop recording
        if(mMediaRecorder != null) {
            mMediaRecorder.stop();
            mMediaRecorder.reset();
        }
        final int target = mActivity.getResources().getDimensionPixelSize(R.dimen.thumbnail_size);
        Uri uri = Uri.fromFile(mFile);
//        Toast.makeText(mActivity,"???????????????"+mFile.toString(),Toast.LENGTH_SHORT).show();
        Log.d("????????????",mFile.toString());
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
    /**TODO ????????????*/
    public void changeToFourRatioThird() {
        mTargetRatio = 1.333f;
        //closeCamera();
        closeSession();
        choosePreviewAndCaptureSize();
        createCameraPreviewSession();

    }
    public void changToSixTeenRatioNine() {
        mTargetRatio = 1.777f;//16:9
        closeSession();
        //closeCamera();
        choosePreviewAndCaptureSize();
        createCameraPreviewSession();
    }
    /**TODO ???????????????*/
    public void changeCameraBtn(){
        closeSession();
        if (mCameraDevice!=null){
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (mCameraId.equals("0")){
            mCameraId="1";
        }else if(mCameraId.equals("1")){
            mCameraId="0";
        }
        openCamera();
    }
    //??????
    public void openFlash(){
        mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
        updatePreview();
    }
    //??????
    public void closeFlash()  {
        mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
        updatePreview();
    }

    private boolean mStartTapFocus = false;
    private float density;

    public void updateCapture() {
        try {
            mPreviewRequest = mPreviewRequestBuilder.build();
            Log.d("comeOnTest", "updateCapture");
            mCaptureSession.capture(mPreviewRequest,
                    mCaptureCallback, mBackgroundHandler);
            //mCaptureSession.captureBurst()
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    public void updateManualFocus(Rect rect) {
        if (mStartTapFocus) return;
        mStartTapFocus = true;
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{new MeteringRectangle(rect, 1000)});
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, new MeteringRectangle[]{new MeteringRectangle(rect, 1000)});
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);//????????????
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START);
        updateCapture();
    }
    /**
     * ??????????????????
     *
     * @param x??????????????????x??????
     * @param y:         ???????????????y??????
     */
    public Rect getFocusRect(int x, int y) {
        int screenW = getScreenWidth(mActivity.getApplicationContext());//??????????????????
        int screenH = getScreenHeight(mActivity.getApplicationContext());//??????????????????

        //???????????????SCALER_CROP_REGION???????????????????????????????????????????????????????????????????????????width???height
        int realPreviewWidth = mPreviewSize.getHeight();
        int realPreviewHeight = mPreviewSize.getWidth();

        //?????????????????????????????????????????????????????????????????????????????????????????????
        float focusX = realPreviewWidth * 1.0f / screenW * x;
        float focusY = realPreviewHeight * 1.0f / screenH * y;

        //??????SCALER_CROP_REGION?????????????????????????????????Rect
        Rect totalPicSize = mPreviewRequest.get(CaptureRequest.SCALER_CROP_REGION);

        //???????????????????????????????????????
        int cutDx = (totalPicSize.height() - mPreviewSize.getHeight()) / 2;

        //??????????????????10dp???????????????????????????????????????????????????10dp???????????????????????????????????????
        float width = dip2px(mActivity, 5.0f);
        float height = dip2px(mActivity, 5.0f);

        //????????????????????????Rect
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
}
