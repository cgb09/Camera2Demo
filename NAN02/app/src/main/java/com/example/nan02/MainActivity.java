package com.example.nan02;

import androidx.appcompat.app.AppCompatActivity;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import com.example.nan02.view.FocusView;
import java.io.File;
import java.lang.ref.WeakReference;
import static android.graphics.Color.*;

public class MainActivity extends AppCompatActivity implements CameraController.CameraControllerInterFaceCallback,TwoStateSwitch.CustomCheckBoxChangeListener{
    private AutoFitTextureView mPreviewTexture;
    private Button mTakePicture;
    private ImageView mSetting;

    private FocusView mFocusView;//对焦
    private TextView vdo_btn;
    private TextView pho_btn;
    private CameraController mCameraController;
    private File mFile;
    private boolean model = true;
    private boolean ratio = false;
    private boolean recordingVideo = false;
    private Button mbt_switch;
    private ImageView chg_btn;
    private Resources resources;
    private TwoStateSwitch mFlashSwitch;
    private RoundImageView mGoToGallery;
    private Handler mHandler;
    private MyOrientationEventListener mOrientationListener;
    private int mPhoneOrientation;

    //定向时间侦听器
    private class MyOrientationEventListener extends OrientationEventListener {
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
            changeOrientation = (dist >= 45 + 5);
        }
        if (changeOrientation) {
            return ((orientation + 45) / 90 * 90) % 360;
        }
        return orientationHistory;
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
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestFullScreenActivity(); //全屏
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

                        int focusViewWidth = mFocusView.dip2px(MainActivity.this, FocusView.mOuterRadiusDP);

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
                        mFocusView.playAnimation();
                        if (mHandler != null) {
                            mHandler.removeCallbacksAndMessages(null);
                            mHandler.sendEmptyMessageDelayed(0, 3000);
                        }
                        break;
                }
                return true;
            }
        });
    }

    private void registerOrientationLister() {
        mOrientationListener = new MyOrientationEventListener(this);//方向旋转监听
    }
    //初始化方向传感器
    private void initOrientationSensor() {
        mOrientationListener.enable();//开始方向监听
    }

    private void initView() {
        mHandler = new MyHandler(this);
        mPreviewTexture = findViewById(R.id.preview_texture);
        mTakePicture = findViewById(R.id.takePicture);
        mbt_switch = findViewById(R.id.bt_switch);
        mFocusView = findViewById(R.id.fv_focus);
        chg_btn = findViewById(R.id.change_camera_id);
        vdo_btn = findViewById(R.id.take_video);
        pho_btn = findViewById(R.id.take_picture);
        mGoToGallery = findViewById(R.id.iv_goto_gallery);
        mFlashSwitch = findViewById(R.id.flash_switch);
        mGoToGallery.setBackground(getDrawable(R.drawable.drawable_shape));
        pho_btn.setTextColor(GREEN);
        mTakePicture.setText("拍照");
        mSetting = findViewById(R.id.settings);
        mCameraController = new CameraController(this,mPreviewTexture);
        mCameraController.setCameraControllerInterFaceCallback(this);
        resources = getApplicationContext().getResources();
        //闪光灯
        mFlashSwitch.setCustomCheckBoxChangeListener(this);

        //拍照切换
        pho_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                mCameraController.closeCamera();

                model = true;
                pho_btn.setTextColor(GREEN);
                vdo_btn.setTextColor(0xFFFFFFFF);
                mSetting.setVisibility(View.VISIBLE);
                mbt_switch.setVisibility(View.VISIBLE);
                mFlashSwitch.setVisibility(View.VISIBLE);
                mCameraController.changeToFourRatioThird();
                mbt_switch.setText("4:3");
                mTakePicture.setText("拍照");
                mTakePicture.setBackground(resources.getDrawable(R.drawable.camera_btn));

                if (recordingVideo == true){
                    recordingVideo = false;
                    mCameraController.stopRecordingVideo();//如果在录像，停止录像
                }
            }
        });
        //录像切换
        vdo_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                mCameraController.closeCamera();
//                mCameraController.openCamera();
                model = false;
                vdo_btn.setTextColor(GREEN);
                pho_btn.setTextColor(0xFFFFFFFF);
                mbt_switch.setVisibility(View.GONE);
                mSetting.setVisibility(View.GONE);
                mFlashSwitch.setVisibility(View.GONE);
                mCameraController.changToSixTeenRatioNine();
                mTakePicture.setText("录制");
                mTakePicture.setBackground(resources.getDrawable(R.drawable.video_start_btn));
            }
        });
        //点击执行
        mTakePicture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(model){
                    //设置文件路径和保存的格式
                    mFile = new File(Environment.getExternalStorageDirectory(), "DCIM/Camera/" + System.currentTimeMillis() + ".jpg");
                    mCameraController.setPhotoPath(mFile);
                    mCameraController.beginTakePicture();
                    mTakePicture.setEnabled(false);
                }else{
                    if (recordingVideo) {
                        mTakePicture.setText("录制");
                        recordingVideo = false;
                        mCameraController.stopRecordingVideo();
                    } else {
                        mFile = new File(MainActivity.this.getExternalFilesDir(null), "DCIM/Camera/"+System.currentTimeMillis()+"test.mp4");
                        mCameraController.setPhotoPath(mFile);
                        recordingVideo = true;
                        mCameraController.startRecordingVideo();
                    }
                }
            }
        });
        //分辨率切换
        mbt_switch.setOnClickListener(
                new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(ratio) {
                    mCameraController.changeToFourRatioThird();
                    mbt_switch.setText("4:3");
                } else {
                    mCameraController.changToSixTeenRatioNine();
                    mbt_switch.setText("16:9");
                }
                ratio = !ratio;
            }
        });
        //摄像头切换
        chg_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCameraController.changeCameraBtn();
                playChangeIdAnimation();
            }
        });
        //设置水印
        mSetting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, SettingActivity.class);
                startActivity(intent);
            }
        });

        //缩略图
        mGoToGallery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gotoGallery();
            }
        });

    }
    //点击缩略图进图库
    private void gotoGallery() {
        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());
        builder.detectFileUriExposure();
        if (null == mFile){
            mFile = new File(Environment.getExternalStorageDirectory(),"DCIM/Camera/");
            return;
        }
        Uri uri = Uri.fromFile(mFile);
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "image/jpeg");
        startActivity(intent);
    }
    //全屏
    private void requestFullScreenActivity() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }
    private void updateThumbnailView() {//更新缩略图
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

    @Override
    protected void onResume() {
        super.onResume();
        mCameraController.startBackgroundThread();//开启线程
        updateThumbnailView();
        if (mFocusView != null) {
            mFocusView.setNeedToDrawView(false);
        }
        initOrientationSensor();//初始化方向传感器
        if(mPreviewTexture.isAvailable()){
            mCameraController.openCamera();
        }else{
            mPreviewTexture.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            mCameraController.openCamera();//1.打开相机
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    @Override
    public void startRecordVideo() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mTakePicture.setText("停止");
            }
        });
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

    @Override
    public void onTakePictureFinished() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mTakePicture.setEnabled(true);
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        mOrientationListener.disable();//禁用方向监听
        mCameraController.closeCamera();
        if (recordingVideo) {
            mCameraController.stopRecordingVideo();
        }
        if (mFocusView != null) {
            mFocusView.setNeedToDrawView(false);
        }

    }

    @Override
    protected void onDestroy() {
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }
        super.onDestroy();
    }
    //切换摄像头动画
    private void playChangeIdAnimation() {
        ObjectAnimator animator = ObjectAnimator.ofFloat(chg_btn, "rotation", 0, 360, 0);
        animator.setDuration(600);
        animator.start();
    }
    //开灯
    @Override
    public void customCheckBoxOn(int flashSwitch) {
        mCameraController.openFlash();
    }
    //关灯
    @Override
    public void customCheckBoxOff(int flashSwitch) {
        mCameraController.closeFlash();
    }

}
