package com.example.num2;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class CameraController {

    private CameraControllerInterFaceCallback mCameraCallback;
    interface CameraControllerInterFaceCallback{
    }
    public void setCameraControllerInterFaceCallback(CameraControllerInterFaceCallback cameraCallback){
        mCameraCallback = cameraCallback;
    }
    private CameraDevice mCameraDevice;
    private String mCameraId = "0";
    private Size mPreviewSize = new Size(1440, 1080);
    private Size mCaptureSize = new Size(1440, 1080);
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest mPreviewRequest;
    private CameraCaptureSession mCaptureSession;
    private AutoFitTextureView mPreviewTexture;
    private Activity mActivity;
    private MediaRecorder mMediaRecorder;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private ImageReader mImageReader;//?????????????????????????????????????????????
    private CameraManager manager;

    public CameraController(Activity activity, AutoFitTextureView textureView) {
        mPreviewTexture = textureView;
        mActivity = activity;

    }

    public void openCamera() {
        if (ContextCompat.checkSelfPermission(mActivity, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
        try {
            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    public void closeCamera(){
        closeSession();
        closeMediaRecorder();
        if(null != mCameraDevice){
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }
    public void closeSession() {
        if( null != mCaptureSession){
            mCaptureSession.close();
            mCaptureSession = null;
        }
    }
    public void closeMediaRecorder() {
        if(null != mMediaRecorder){
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
    }
   /* private void choosePreviewAndCaptureSize() {
        CameraCharacteristics characteristics = null;
        try {
            characteristics = manager.getCameraCharacteristics(String.valueOf(mCameraId));
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        StreamConfigurationMap map = characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] previewSizeMap = map.getOutputSizes(SurfaceTexture.class);


        int screenWidth = getScreenWidth(mActivity.getApplicationContext());
        mPreviewSize = getPreviewSize(previewSizeMap, mTargetRatio, screenWidth);


            mVideoSize = mPreviewSize;


        mCaptureSize = getVideoSize(mTargetRatio, previewSizeMap);
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
        mPreviewTexture.setAspectRatio(
                mPreviewSize.getHeight(), mPreviewSize.getWidth());
            }
        });

    }

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

    public Size getPreviewSize(Size[] mapPreview, float targetRatio, int screenWidth) {
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

    public int getScreenWidth(Context context) {
        return context.getResources().getDisplayMetrics().widthPixels;
    }*/
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            creatImageReader();
//            choosePreviewAndCaptureSize();
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraDevice.close();

        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            cameraDevice.close();

        }
    };

    private void creatImageReader() {
        mImageReader = ImageReader.newInstance(mCaptureSize.getWidth(), mCaptureSize.getHeight(),
                ImageFormat.JPEG, /*maxImages*/2);//RAW_SENSOR YUV_420_888
        mImageReader.setOnImageAvailableListener(
                mOnImageAvailableListener, mBackgroundHandler);
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mPreviewTexture.setAspectRatio(mPreviewSize.getHeight(),mPreviewSize.getWidth());
            }
        });
    }
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            saveImage(reader);  //????????????
        }

    };

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

                            mCaptureSession = cameraCaptureSession;
                            try {
                                //????????????????????????
                                mPreviewRequest = mPreviewRequestBuilder.build();
                                //?????????????????????????????????????????????????????????????????????????????????
                                mCaptureSession.setRepeatingRequest(mPreviewRequest,//mPreviewRequest???target??????????????????surface?????????????????????
                                        // ????????????setRepeatingRequest???????????????????????????
                                        mCaptureCallback,mBackgroundHandler);
                            }catch (CameraAccessException e){
                                e.printStackTrace();
                            }
                        }
                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                        }
                    },mBackgroundHandler);
        }catch (CameraAccessException e){
            e.printStackTrace();
        }
    }

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
    public void startBackgroundThread(){
        //HandlerThread???Android API??????????????????????????????????????????????????????????????????????????????Looper?????????
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private File mFile ;
    /**TODO ??????*/
    //??????????????????
    public void beginTakePicture() {
        //????????????????????????????????????
        mFile = new File(Environment.getExternalStorageDirectory(),"DCIM/Camera/"+System.currentTimeMillis()+".jpg");
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
            //???????????????????????????????????????????????????????????????captureBuilder??????ImageReader??????target????????????????????????ImageReader???onImageAvailable()??????????????????
            mCaptureSession.capture(captureBuilder.build(),CaptureCallback,mBackgroundHandler);
        }catch (CameraAccessException e){
            e.printStackTrace();
        }
    }

    //????????????
    private void saveImage(ImageReader reader) {
        Image mImage = reader.acquireNextImage();//acquireNextImage???acquireLatestImage????????????????????????
//            Image mImage = reader.acquireLatestImage();
        ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        FileOutputStream output = null;
        try{
            output = new FileOutputStream(mFile);
            output.write(bytes);
            Uri photouri = Uri.fromFile(mFile);
            Toast.makeText(mActivity,"???????????????"+mFile.toString(),Toast.LENGTH_SHORT).show();
            mActivity.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, photouri));
        }catch (IOException e){
            e.printStackTrace();
        }finally {
            mImage.close();
            if(null !=output){
                try{
                    output.close();
                }catch (IOException e){
                    e.printStackTrace();
                }
            }
        }
    }
}
