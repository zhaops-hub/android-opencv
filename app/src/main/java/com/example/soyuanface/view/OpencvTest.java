package com.example.soyuanface.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import com.example.soyuanface.R;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.core.*;
import org.opencv.dnn.Net;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.*;

/**
 * @author zps
 */
public class OpencvTest extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2, View.OnClickListener {
    private static final String TAG = OpencvTest.class.getSimpleName();
    /** 包含有人脸的图像 */
    protected static Bitmap mFaceBitmap;
    /** 相机预览 */
    private CameraBridgeViewBase cameraView;
    /**正脸 级联分类器*/
    private CascadeClassifier mFrontalFaceClassifier = null;
    /**侧脸 级联分类器 */
    private CascadeClassifier mProfileFaceClassifier = null;
    /**正脸分类器*/
    private CascadeClassifier mJavaDetector;

    private Mat mGray;
    private Mat mRgba;

    /**是否是前置摄像头 */
    private boolean isFrontCamera;


    /** 设置检测区域 */
    private final Size m55Size = new Size(55, 55);
    private final Size m65Size = new Size(65, 65);
    private final Size mDefault = new Size();

    private Rect[] mFrontalFacesArray;
    private Rect[] mProfileFacesArray;

    private int mFrontFaces = 0;
    private int mProfileFaces = 0;


    /** 手动装载openCV库文件，以保证手机无需安装OpenCV Manager */
    static {
        System.loadLibrary("opencv_java4");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initWindowSettings();
        setContentView(R.layout.activity_opencv_test);
        initFrontalFace();
        initProfileFace();
        initLabFrontalFace();
        initCamera();
        Button switchCamera = findViewById(R.id.switch_camera);
        switchCamera.setOnClickListener(this);
    }


    /**
     * 设置屏幕样式
     */
    private void initWindowSettings() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    /**
     * @Description 初始化摄像头
     */
    protected void initCamera() {
//        int cameraId = 0;
//        Camera.CameraInfo info = new Camera.CameraInfo();
//        int numCameras = Camera.getNumberOfCameras();
//        for (int i = 0; i < numCameras; i++) {
//            Camera.getCameraInfo(i, info);
//            Log.v("yaoxumin", "在 CameraRenderer 类的 openCamera 方法 中执行了开启摄像 Camera.open 方法,cameraId:" + i);
//            cameraId = i;
//            break;
//        }
//
//        //摄像头索引        -1/0：后置双摄     1：前置
//        cameraView.setCameraIndex(cameraId);
        cameraView = findViewById(R.id.camera_view);
        cameraView.setVisibility(CameraBridgeViewBase.VISIBLE);
        cameraView.setCameraPermissionGranted();
        cameraView.setCvCameraViewListener(this);
//        cameraView.enableFpsMeter(); //显示FPS
        cameraView.enableView();
        //设置帧大小
//        cameraView.setMaxFrameSize(640, 480);
    }

    /**
     * @Description 初始化正脸分类器
     */
    public void initLabFrontalFace() {
        try {
            InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
            File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
            File mCascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");
            FileOutputStream os = new FileOutputStream(mCascadeFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();
            // 加载 正脸分类器
            mJavaDetector = new CascadeClassifier(mCascadeFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    /**
     * @Description 初始化正脸分类器
     */
    public void initFrontalFace() {
        try {

            InputStream is = getResources().openRawResource(R.raw.haarcascade_frontalface_alt);
            File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
            File mCascadeFile = new File(cascadeDir, "haarcascade_frontalface_alt.xml");
            FileOutputStream os = new FileOutputStream(mCascadeFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();
            // 加载 正脸分类器
            mFrontalFaceClassifier = new CascadeClassifier(mCascadeFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    /**
     * @Description 初始化侧脸分类器
     */
    public void initProfileFace() {
        try {
            //这个模型是我觉得来说相对不错的
            InputStream is = getResources().openRawResource(R.raw.haarcascade_profileface);
            File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
            File mCascadeFile = new File(cascadeDir, "haarcascade_profileface.xml");
            FileOutputStream os = new FileOutputStream(mCascadeFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();
            // 加载 侧脸分类器
            mProfileFaceClassifier = new CascadeClassifier(mCascadeFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        mGray = new Mat();
        mRgba = new Mat();
    }

    @Override
    public void onCameraViewStopped() {
        mGray.release();
        mRgba.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        return frontalFaceOnFrame(inputFrame);
    }


    @Override
    protected void onPause() {
        super.onPause();
        if (cameraView != null) {
            cameraView.disableView();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraView.disableView();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.switch_camera:
                cameraView.disableView();
                if (isFrontCamera) {
                    cameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_BACK);
                    isFrontCamera = false;
                } else {
                    cameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_FRONT);
                    isFrontCamera = true;
                }
                cameraView.enableView();
                break;
            default:
        }
    }

    /**
     * 正脸侧脸检测算法
     *
     * @param inputFrame
     * @return
     */
    private Mat labFrontalFaceOnFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();

        MatOfRect faces = new MatOfRect();
        int height = mGray.rows();
        float mRelativeFaceSize = 0.2f;
        int mAbsoluteFaceSize = 0;
        if (Math.round(height * mRelativeFaceSize) > 0) {
            mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
        }

        mJavaDetector.detectMultiScale(mGray, faces, 1.1, 2, 2,
                new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());

        Rect[] facesArray = faces.toArray();

        if (facesArray.length > 0) {
            for (int i = 0; i < facesArray.length; i++) {
                Imgproc.rectangle(mRgba, facesArray[i].tl(), facesArray[i].br(), new Scalar(0, 255, 0, 255), 3);
            }
            Log.i(TAG, "正脸人数为 : " + facesArray.length);
        }


        return mRgba;
    }

    /**
     * 正脸侧脸检测算法
     *
     * @param inputFrame
     * @return
     */
    private Mat frontalFaceOnFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();
        // 前置摄像头问题
        // 旋转,变成镜像
        // Core.flip(mRgba, mRgba, 1);

        // 检测并显示
        MatOfRect frontalFaces = new MatOfRect();
        MatOfRect profileFaces = new MatOfRect();

        //这里2个 Size 是用于检测人脸的，越小，检测距离越远，1.1, 5, 2, m65Size, mDefault着四个参数可以提高检测的准确率，5表示确认五次，具体百度 detectMultiScale 这个方法
        if (mFrontalFaceClassifier != null) {
            mFrontalFaceClassifier.detectMultiScale(mGray, frontalFaces, 1.1, 5, 2, m65Size, mDefault);
            mFrontalFacesArray = frontalFaces.toArray();
            if (mFrontalFacesArray.length > 0) {
                Log.i(TAG, "正脸人数为 : " + mFrontalFacesArray.length);
            }
             mFrontFaces = mFrontalFacesArray.length;
        }

//        //这里2个 110 是用于检测人脸的，越小，检测距离越远
//        if (mProfileFaceClassifier != null) {
//            mProfileFaceClassifier.detectMultiScale(mGray, profileFaces, 1.1, 6, 0, m55Size, mDefault);
//            mProfileFacesArray = profileFaces.toArray();
//            if (mProfileFacesArray.length > 0) {
//                Log.i(TAG, "侧脸人数为 : " + mProfileFacesArray.length);
//            }
//
//            mProfileFaces = mProfileFacesArray.length;
//        }
//
//        if (mProfileFacesArray.length > 0) {
//            // 用框标记
//            for (int i = 0; i < mProfileFacesArray.length; i++) {
//                Imgproc.rectangle(mRgba, mProfileFacesArray[i].tl(), mProfileFacesArray[i].br(), new Scalar(0, 255, 0, 255), 3);
//            }
//        }

        if (mFrontalFacesArray.length > 0) {
            // 用框标记
            for (int i = 0; i < mFrontalFacesArray.length; i++) {
                Imgproc.rectangle(mRgba, mFrontalFacesArray[i].tl(), mFrontalFacesArray[i].br(), new Scalar(0, 255, 0, 255), 3);
            }

//            Bitmap bitmap = Bitmap.createBitmap(mRgba.width(), mRgba.height(), Bitmap.Config.RGB_565);
//            Utils.matToBitmap(mRgba, bitmap);
//            mFaceBitmap = bitmap;
//            ImageHelper.save(mFaceBitmap);
        }
        return mRgba;
    }
}