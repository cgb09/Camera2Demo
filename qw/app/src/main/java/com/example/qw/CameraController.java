package com.example.qw;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
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
    private static final String TAG = "CameraController";
    private CameraDevice mCameraDevice;
    private String mCameraId = "0";
    private Size mPreviewSize = new Size(14040, 1080);
    private Size mCaptureSize = new Size(4000, 3000);
    private Size mVideoSize = new Size(1920, 1080);
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest mPreviewRequest;
    private CameraCaptureSession mCaptureSession;
    private File mFile ;
    private AutoFitTextureView mPreviewTexture;
    private Activity mActivity;
    private MediaRecorder mMediaRecorder;


    public static final float PREVIEW_SIZE_RATIO_OFFSET = 0.01f;
    private float mTargetRatio = 1.333f;
    private CameraManager manager;


    public CameraController(Activity activity, AutoFitTextureView textureView) {
        mPreviewTexture = textureView;
        mActivity = activity;
        manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
    }

    public void openCamera() {
        if (ContextCompat.checkSelfPermission(mActivity, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(mActivity,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mMediaRecorder = new MediaRecorder();
        try {
            manager.openCamera(String.valueOf(mCameraId), mStateCallback, mBackgroundHandler);
        } catch (Exception exception) {
            Log.e(TAG, "exception:" + exception);
        }
    }

    private void choosePreviewAndCaptureSize() {
        CameraCharacteristics characteristics = null;
        try {
            characteristics = manager.getCameraCharacteristics(String.valueOf(mCameraId));
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        StreamConfigurationMap map = characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] previewSizeMap = map.getOutputSizes(SurfaceTexture.class);
        Size[] videoSizeMap = map.getOutputSizes(MediaRecorder.class);

        int screenWidth = getScreenWidth(mActivity.getApplicationContext());
        mPreviewSize = getPreviewSize(previewSizeMap, mTargetRatio, screenWidth);
//        mVideoSize = getVideoSize(mTargetRatio, videoSizeMap);

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
    }

    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            creatImageReader();
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

                            /**TODO  ??????*/

     public void updatePreview() {
        try{
            mPreviewRequest = mPreviewRequestBuilder.build();
            mCaptureSession.setRepeatingRequest(mPreviewRequest,mCaptureCallback,null);
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

     public void closeCamera(){
          closeSession();
          closeMediaRecorder();
          if(null != mCameraDevice){
              mCameraDevice.close();
              mCameraDevice = null;
          }
     }

    public void closeMediaRecorder() {
          if(null != mMediaRecorder){
              mMediaRecorder.release();
              mMediaRecorder = null;
          }
    }

    public void closeSession() {
        if( null != mCaptureSession){
            mCaptureSession.close();
            mCaptureSession = null;
        }
    }

      private void setParams() {
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
     }

    public void startRecordingVideo() {
          try{
              closeSession();//TODO 1
              choosePreviewAndCaptureSize();//TODO 2
              setUpMediaRecorder();//TODO 3
              Log.d("chenxinhao","setUpMediaRecorder startRecordingVideo");

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

              Log.d("chenxinhao","createCaptureSession");
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
          Log.d("chenxinhao","setUpMediaRecorder");

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
          mMediaRecorder.prepare();
          Log.d("chenxinhao","setUpMediaRecorder prepare");

    }

    public void stopRecordingVideo() {
        //Stop recording
        if(mMediaRecorder != null) {
            mMediaRecorder.stop();
            mMediaRecorder.reset();
        }
        closeSession();
        createCameraPreviewSession();
    }

    public void setVideoPath(File file) { mFile = file; }

    private CameraControllerInterFaceCallback mCameraCallback;
    interface CameraControllerInterFaceCallback{
          void startRecordVideo();
      }
      public void setCameraControllerInterFaceCallback(CameraControllerInterFaceCallback cameraCallback){
          mCameraCallback = cameraCallback;
      }


    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private ImageReader mImageReader;//?????????????????????????????????????????????


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
    public void startBackgroundThread(){
        //HandlerThread???Android API??????????????????????????????????????????????????????????????????????????????Looper?????????
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    //??????????????????,?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
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
        FileOutputStream output = null;
        try{
            output = new FileOutputStream(mFile);
            output.write(bytes);
            Toast.makeText(mActivity,"???????????????"+mFile.toString(),Toast.LENGTH_SHORT).show();
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

                          /**TODO ????????????*/
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

    public void changeToFourRatioThird() {
        mTargetRatio = 1.333f;
        //closeCamera();
        closeSession();
        choosePreviewAndCaptureSize();
        createCameraPreviewSession();
    }
            /**TODO ????????????*/
    public void changToSixTeenRatioNine() {
        mTargetRatio = 1.777f;//16:9
        closeSession();
        //closeCamera();
        choosePreviewAndCaptureSize();
        createCameraPreviewSession();
    }

}
