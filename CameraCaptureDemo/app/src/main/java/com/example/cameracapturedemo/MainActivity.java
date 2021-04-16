package com.example.cameracapturedemo;

import androidx.appcompat.app.AppCompatActivity;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.params.MeteringRectangle;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.lang.ref.WeakReference;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, CameraController.CameraControllerInterFaceCallback, TwoStateSwitch.CustomCheckBoxChangeListener, ShutterButton.OnShutterButtonClickLister {
    private static final String TAG = "MainActivity";
    private AutoFitTextureView mPreviewTexture;
    private ShutterButton mTakePicture;
    private ImageView mChangeCameraId;
    private ImageView mSetting;

    private FocusView mFocusView;
    private TwoStateSwitch mFlashSwitch;
    private TwoStateSwitch mRatioSwitch;
    private RoundImageView mGoToGallery;
    private TextView mPhotoMode;
    private TextView mVideoMode;
    private File mFile;
    private CameraController mCameraController;
    private MyOrientationEventListener mOrientationListener;
    public static final int ORIENTATION_HYSTERESIS = 5;
    private int mPhoneOrientation;
    private Handler mHandler;
    private int mCurrentMode = CameraConstant.PHOTO_MODE;
    private boolean mRecording;

    @Override
    public void customCheckBoxOn(int flashSwitch) {
        switch (flashSwitch) {
            case R.id.flash_switch:
                openFlashMode();
                break;
            case R.id.ratio_switch:
                changToSixTeenRatioNine();
                break;
        }
    }

    @Override
    public void customCheckBoxOff(int flashSwitch) {
        switch (flashSwitch) {
            case R.id.flash_switch:
                closeFlashMode();
                break;
            case R.id.ratio_switch:
                changeToFourRatioThird();
                break;
        }
    }

    @Override
    public void onShutterButtonClick(int mode) {
        switch (mode) {
            case CameraConstant.PHOTO_MODE:
                takePicture();
                break;
            case CameraConstant.VIDEO_MODE:
                takeVideo();
                break;
        }
    }

    private void takeVideo() {
        if (mRecording) {
            mRecording = false;
            mTakePicture.setVideoRecordingState(mRecording);
            stopVideoRecording();
        } else {
            mRecording = true;
            mTakePicture.setVideoRecordingState(mRecording);
            startVideoRecording();
        }
    }

    private void startVideoRecording() {
        mFile = new File(Environment.getExternalStorageDirectory(), "DCIM/Camera/" + System.currentTimeMillis() + ".mp4");
        mCameraController.setPhotoPath(mFile);
        mCameraController.startVideoRecording();
    }

    private void stopVideoRecording() {
        mCameraController.stopVideoRecording();
    }

    public class MyHandler extends Handler {
        WeakReference<Activity> mWeakReference;

        public MyHandler(Activity activity) {
            mWeakReference = new WeakReference<Activity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            final Activity activity = mWeakReference.get();
            if (activity != null) {
                switch (msg.what) {
                    case 0:
                        mFocusView.setNeedToDrawView(false);
                        break;
                }
            }
        }
    }

    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            mCameraController.openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestFullScreenActivity();
        setContentView(R.layout.activity_main);
        initView();
        registerOrientationLister();
        initTextureViewListener();
    }

    private void initTextureViewListener() {
        mPreviewTexture.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_UP:
                        int currentY = (int) event.getRawY();

                        int left = mPreviewTexture.getLeft();
                        int top = mPreviewTexture.getTop();

                        int focusViewWidth = DensityUtils.dip2px(MainActivity.this, FocusView.mOuterRadiusDP);

                        int minDrawY = top + focusViewWidth / 2;
                        int maxDrawY = top + mPreviewTexture.getHeight() - focusViewWidth / 2;
                        if (currentY <= minDrawY || currentY >= maxDrawY) return false;

                        int currentX = (int) event.getRawX();
                        int minDrawX = left + focusViewWidth / 2;
                        int maxDrawX = left + mPreviewTexture.getWidth() - focusViewWidth / 2;

                        if (currentX <= minDrawX || currentX >= maxDrawX) return false;

                        mFocusView.setNeedToDrawView(true);
                        mFocusView.setFocusViewCenter(currentX, currentY);

                        mCameraController.updateManualFocus(mCameraController.getFocusRect(currentX, currentY));
                        playAnimation();
                        if (mHandler != null) {
                            mHandler.removeCallbacksAndMessages(null);
                            mHandler.sendEmptyMessageDelayed(0, 1500);
                        }
                        break;
                }
                return true;
            }
        });
    }

    private void playAnimation() {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(mFocusView, "scaleX", 1.0f, 1.2f, 0.8f, 1.0f)
                .setDuration(500);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(mFocusView, "scaleY", 1.0f, 1.2f, 0.8f, 1.0f)
                .setDuration(500);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY);
        set.start();
    }

    private void registerOrientationLister() {
        mOrientationListener = new MyOrientationEventListener(this);//方向旋转监听
    }

    private void initOrientationSensor() {
        mOrientationListener.enable();//开始方向监听
    }

    private void initView() {
        mHandler = new MyHandler(this);
        mPreviewTexture = findViewById(R.id.preview_texture);
        mTakePicture = findViewById(R.id.take_picture);
        mSetting = findViewById(R.id.settings);
        mChangeCameraId = findViewById(R.id.change_camera_id);
        mFocusView = findViewById(R.id.fv_focus);
        mFlashSwitch = findViewById(R.id.flash_switch);
        mRatioSwitch = findViewById(R.id.ratio_switch);
        mGoToGallery = findViewById(R.id.iv_goto_gallery);
        mPhotoMode = findViewById(R.id.take_picture_mode);
        mVideoMode = findViewById(R.id.take_video_mode);
        mPhotoMode.setTextColor(Color.GREEN);
        mSetting.setOnClickListener(this);
        mFlashSwitch.setCustomCheckBoxChangeListener(this);
        mRatioSwitch.setCustomCheckBoxChangeListener(this);
        mGoToGallery.setBackground(getDrawable(R.drawable.drawable_shape));

        mChangeCameraId.setImageResource(R.mipmap.change_id);
        mTakePicture.setOnShutterButtonClickListener(this);
        mChangeCameraId.setOnClickListener(this);
        mGoToGallery.setOnClickListener(this);
        mPhotoMode.setOnClickListener(this);
        mVideoMode.setOnClickListener(this);


        mCameraController = new CameraController(this, mPreviewTexture);
        mCameraController.setCameraControllerInterFaceCallback(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        mCameraController.startBackgroundThread();
        updateThumbnailView();

        if (mFocusView != null) {
            mFocusView.setNeedToDrawView(false);
        }
        initOrientationSensor();
        if (mPreviewTexture.isAvailable()) {
            mCameraController.openCamera();
        } else {
            mPreviewTexture.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    private void updateThumbnailView() {
        boolean setThumbnailSuccess = false;
        Uri targetUrl = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        Uri targetVideoUrl = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        ContentResolver resolver = getContentResolver();
        Cursor imagesCursor = resolver.query(targetUrl, new String[]{
                        MediaStore.Images.Media.DATA, MediaStore.Images.Media._ID}, null, null,
                null);
        if (imagesCursor != null) {
            while (imagesCursor.moveToNext()) {
                long imageId = imagesCursor.getInt(imagesCursor.getColumnIndex(MediaStore.Images.Media._ID));
                String filePathImage = imagesCursor.getString(imagesCursor.getColumnIndex(MediaStore.Images.Media.DATA));
                if (!filePathImage.contains("DCIM")) continue;
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 32;
                Bitmap bitmap = MediaStore.Images.Thumbnails.getThumbnail(resolver, imageId, MediaStore.Images.Thumbnails.MINI_KIND, options);
                if (bitmap != null) {
                    setThumbnailSuccess = true;
                    mGoToGallery.setBitmap(bitmap);
                    break;
                }
            }
            imagesCursor.close();
        }

        if (!setThumbnailSuccess) {
            Cursor videoCursor = resolver.query(targetVideoUrl, new String[]{
                            MediaStore.Video.Media.DATA, MediaStore.Video.Media._ID}, null, null,
                    null);
            if (videoCursor != null) {
                while (videoCursor.moveToNext()) {
                    long videoId = videoCursor.getInt(videoCursor.getColumnIndex(MediaStore.Video.Media._ID));
                    String filePathVideo = videoCursor.getString(videoCursor.getColumnIndex(MediaStore.Video.Media.DATA));
                    if (!filePathVideo.contains("DCIM")) continue;
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 32;
                    Bitmap bitmap = MediaStore.Video.Thumbnails.getThumbnail(resolver, videoId, MediaStore.Video.Thumbnails.MINI_KIND, options);
                    if (bitmap != null) {
                        mGoToGallery.setBitmap(bitmap);
                        break;
                    }
                }
                videoCursor.close();
            }
        }
    }

    private void requestFullScreenActivity() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    @Override
    public void onPause() {
        mOrientationListener.disable();//禁用方向监听
        mCameraController.closeCamera();
        if (mFocusView != null) {
            mFocusView.setNeedToDrawView(false);
        }
        if (mRecording) {
            mCameraController.stopVideoRecording();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.settings:
                goToSettingsActivity();
                break;
            case R.id.change_camera_id:
                changeCameraId();
                break;
            case R.id.iv_goto_gallery:
                gotoGallery();
                break;
            case R.id.take_picture_mode:
                changeToPictureMode();
                break;
            case R.id.take_video_mode:
                changeToVideoMode();
                break;
        }
    }

    private void changeToVideoMode() {
        if (mCurrentMode == CameraConstant.VIDEO_MODE) return;

        mCurrentMode = CameraConstant.VIDEO_MODE;
        mTakePicture.setCurrentMode(mCurrentMode);
        mVideoMode.setTextColor(Color.GREEN);
        mPhotoMode.setTextColor(Color.WHITE);
        mSetting.setVisibility(View.GONE);
        mFlashSwitch.setVisibility(View.GONE);
        mRatioSwitch.setVisibility(View.GONE);
        mTakePicture.setVideoRecordingState(false);
        mCameraController.closeCamera();
        mCameraController.setCurrentMode(mCurrentMode);
        mCameraController.setTargetRatio(CameraConstant.RATIO_SIXTEEN_NINE);
        mCameraController.openCamera();
    }

    private void changeToPictureMode() {
        if (mCurrentMode == CameraConstant.PHOTO_MODE) return;
        mVideoMode.setTextColor(Color.WHITE);
        mPhotoMode.setTextColor(Color.GREEN);
        mSetting.setVisibility(View.VISIBLE);
        mFlashSwitch.setVisibility(View.VISIBLE);
        mRatioSwitch.setVisibility(View.VISIBLE);
        mCurrentMode = CameraConstant.PHOTO_MODE;
        mTakePicture.setCurrentMode(mCurrentMode);
        mCameraController.closeCamera();
        mCameraController.setCurrentMode(mCurrentMode);
        mCameraController.setTargetRatio(CameraConstant.RATIO_FOUR_THREE);
        mCameraController.openCamera();
    }

    private void goToSettingsActivity() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    private void gotoGallery() {
        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());
        builder.detectFileUriExposure();
        if (null == mFile) return;
        Uri uri = Uri.fromFile(mFile);
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "image/jpeg");
        startActivity(intent);
    }

    private void openFlashMode() {
        mCameraController.openFlashMode();
    }

    private void closeFlashMode() {
        mCameraController.closeFlashMode();
    }

    private void changeCameraId() {
        mCameraController.changeCameraId();
        playChangeIdAnimation();
    }

    private void takePicture() {
        //mFile = new File(MainActivity.this.getExternalFilesDir(null), System.currentTimeMillis() + ".jpg");
        mFile = new File(Environment.getExternalStorageDirectory(), "DCIM/Camera/" + System.currentTimeMillis() + ".jpg");
        mCameraController.setPhotoPath(mFile);
        mCameraController.captureStillPicture();
        mTakePicture.startPictureAnimator();
        mTakePicture.setEnabled(false);
    }

    public void changeToFourRatioThird() {
        mCameraController.changeToFourRatioThird();
    }

    public void changToSixTeenRatioNine() {
        mCameraController.changToSixTeenRatioNine();
    }

    @Override
    public void onThumbnailCreated(final Bitmap bitmap) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mGoToGallery.setBitmap(bitmap);
            }
        });
    }

    private class MyOrientationEventListener
            extends OrientationEventListener {
        public MyOrientationEventListener(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            if (orientation == ORIENTATION_UNKNOWN) {
                return;
            }

            mPhoneOrientation = roundOrientation(orientation, mPhoneOrientation);
            mCameraController.setPhoneDeviceDegree(mPhoneOrientation);
        }
    }

    public int roundOrientation(int orientation, int orientationHistory) {
        boolean changeOrientation = false;
        if (orientationHistory == OrientationEventListener.ORIENTATION_UNKNOWN) {
            changeOrientation = true;
        } else {
            int dist = Math.abs(orientation - orientationHistory);
            dist = Math.min(dist, 360 - dist);
            changeOrientation = (dist >= 45 + ORIENTATION_HYSTERESIS);
        }
        if (changeOrientation) {
            return ((orientation + 45) / 90 * 90) % 360;
        }
        return orientationHistory;
    }

    private void playChangeIdAnimation() {
        ObjectAnimator animator = ObjectAnimator.ofFloat(mChangeCameraId, "rotation", 0, 180, 0);
        animator.setDuration(500);
        animator.start();
    }

    public void onTakePictureFinished() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mTakePicture.setEnabled(true);
            }
        });
    }
}
