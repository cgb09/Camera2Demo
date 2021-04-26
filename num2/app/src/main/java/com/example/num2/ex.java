package com.example.num2;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.os.Bundle;
import android.os.Handler;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.TextView;

@SuppressLint({"NewApi", "SdCardPath"})
public class ex extends Activity implements Runnable{
    // 预览图片范围
    private SurfaceView surfaceView;
    private TextView tv_time;
    // 倒计时拍摄
    private int cameratime = 4;
    private Camera camera;
    private boolean preview = false;
    // 文件名字
    private String filename;
    // 文件名字的带的时间戳
    private String timeString;
    // 格式化时间
    private SimpleDateFormat dateFormat;
    // 日期对象
    private Date date;
    // 控制线程
    boolean stopThread = false;
    private File file;
    String photo;
    private Handler mHandler = new Handler() {
        public void handleMessage(android.os.Message msg) {
            int what = msg.what;
            switch (what) {
                case 222:
                    tv_time.setText("" + cameratime);
                    if ("0".equals(tv_time.getText().toString())) {
                        tv_time.setText("拍摄成功！");
//                        takePhoto();
                    }
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // 初始化数据
        findView();
        surfaceView.getHolder().addCallback(new SufaceListener());
        /* 下面设置Surface不维护自己的缓冲区，而是等待屏幕的渲染引擎将内容推送到用户面前 */
        surfaceView.getHolder()
                .setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        surfaceView.getHolder().setFixedSize(200, 200); // 设置分辨率
    }

    @Override
    protected void onStart() {
        // TODO Auto-generated method stub
        super.onStart();
        // 开启线程
        new Thread(this).start();
    }

    private final class SufaceListener implements SurfaceHolder.Callback {
        /**
         * surface改变
         */
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width,
                                   int height) {
        }

        /**
         * surface创建
         */
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            try {
                for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
                    CameraInfo info = new CameraInfo();
                    Camera.getCameraInfo(i, info);
                    // 调用系统的前置摄像头
                    if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
                        camera = Camera.open(i);
                    }
                }
                Camera.Parameters parameters = camera.getParameters();
                /* 每秒从摄像头捕获5帧画面， */
                parameters.setPreviewFrameRate(5);
                /* 设置照片的输出格式:jpg */
                parameters.setPictureFormat(PixelFormat.JPEG);
                /* 照片质量 */
                parameters.set("jpeg-quality", 85);
                WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
                camera.setParameters(parameters);
                camera.setPreviewDisplay(surfaceView.getHolder());// 通过SurfaceView显示取景画面
                camera.startPreview();
                preview = true;
            } catch (Exception e) {
            }
        }

        /**
         * surface销毁
         */
        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            if (camera != null) {
                if (preview)
                    camera.stopPreview();
                camera.release();
                camera = null;
            }
        }
    }

    /**
     * 拍摄照片
     */
//    private void takePhoto() {
//        // 执行拍照效果
//        camera.takePicture(null, null, new Camera.PictureCallback() {
//            @Override
//            public void onPictureTaken(byte[] data, Camera camera) {
//                try {
//                    Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0,
//                            data.length);
//                    timeString = formatDate();
//                    //保存到data/data目录自定义文件夹下
//                    filename = "/data/data/com.example.pujiejiaapp/images/"
//                            + timeString + ".jpg";
//                    File file = new File(filename);
//                    boolean createNewFile = file.createNewFile();
//                    System.out.println("创建文件夹成功没有" + createNewFile);
//                    System.out.println(file);
//                    FileOutputStream outStream = new FileOutputStream(file);
//                    bitmap.compress(Bitmap.CompressFormat.JPEG, 60, outStream);
//                    outStream.flush();
//                    outStream.close();
//                    // 重新浏览
//                    camera.stopPreview();
//                    camera.startPreview();
//                    preview = true;
//                } catch (Exception e) {
//                    e.printStackTrace();
//                } finally {
//                }
//            }
//        });
//    }

    @Override
    public void run() {
        while (!stopThread) {
            try {
                //按秒数倒计时
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            cameratime--;
            mHandler.sendEmptyMessage(222);
            if (cameratime <= 0) {
                break;
            }
        }
    }

    // 初始化数据
    private void findView() {
        surfaceView = (SurfaceView) this.findViewById(R.id.preview_view);
        tv_time = (TextView) findViewById(R.id.tv_time);
    }

    // 格式化系统的时间
    public String formatDate() {
        date = new Date(System.currentTimeMillis());
        // 日期格式
        dateFormat = new SimpleDateFormat("'IMG'_yyyyMMddHHmmss");
        return dateFormat.format(date);
    }

    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        // 线程已关闭
        super.onDestroy();
        stopThread = true;
    }
}