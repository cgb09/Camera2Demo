package com.example.num2;


import android.graphics.ColorSpace;
import android.graphics.SurfaceTexture;
import android.os.Bundle;

import android.os.Handler;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;


import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity implements Runnable, CameraController.CameraControllerInterFaceCallback {
    // 预览图片范围
    private AutoFitTextureView mPreviewView;
    private CameraController mCameraController;
    private TextView tv_time;
    private Button take_picture;
    public int cameratime;
    private boolean model = true;
    // 控制线程
    boolean stopThread = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // 初始化数据
        findView();
    }


    private void findView() {
        mPreviewView = findViewById(R.id.preview_view);
        tv_time = findViewById(R.id.tv_time);
        take_picture = findViewById(R.id.take_picture);
        take_picture.setText("TAKEPHOTO");
        mCameraController = new CameraController(this, mPreviewView);
        mCameraController.setCameraControllerInterFaceCallback(this);

        take_picture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (model) {
                    take_picture.setText("倒计时");
                    start();
                }
            }
        });
    }

    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            mCameraController.openCamera();
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

    private void start() {
        super.onStart();
        // 开启线程
        new Thread(this).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mCameraController.startBackgroundThread();
        if (mPreviewView.isAvailable()) {
//            mCameraController.openCamera();
        } else {
            mPreviewView.setSurfaceTextureListener(mSurfaceTextureListener);
        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        mCameraController.closeCamera();
    }
    @Override
    protected void onDestroy() {
        // 线程已关闭
        super.onDestroy();
        stopThread = true;

    }

    @Override
    public void run() {
        cameratime = 10;
        while (!stopThread) {
            try {
                //按秒数倒计时
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (cameratime <= 0) {
                break;
            }
            cameratime--;
            mHandler.sendEmptyMessage(222);

        }
    }

    private Handler mHandler = new Handler() {
        public void handleMessage(android.os.Message msg) {
            int what = msg.what;
            switch (what) {
                case 222:
                    tv_time.setText("" + cameratime);
                    if ("0".equals(tv_time.getText().toString())) {
                        tv_time.setText("拍摄成功！");
                        take_picture.setText("重新计时");
                        mCameraController.beginTakePicture();

                    }
                    break;


            }
        }
    };
}