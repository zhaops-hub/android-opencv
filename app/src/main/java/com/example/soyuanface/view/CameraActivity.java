package com.example.soyuanface.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.*;
import android.os.Bundle;
import butterknife.BindView;
import butterknife.ButterKnife;
import com.example.soyuanface.R;
import com.serenegiant.common.BaseActivity;
import com.serenegiant.usb.CameraDialog;
import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usbcameracommon.UVCCameraHandler;
import com.serenegiant.widget.CameraViewInterface;
import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;


/**
 * @author SoYuan
 */
public class CameraActivity extends BaseActivity implements CameraDialog.CameraDialogParent {
    /**
     * TODO set false on release
     */
    private static final boolean DEBUG = true;
    private static final String TAG = "CameraActivity";

    /**
     * 操作锁
     */
    private final Object mSync = new Object();

    /**
     * set true if you want to record movie using MediaSurfaceEncoder
     * (writing frame data into Surface camera from MediaCodec
     * by almost same way as USBCameratest2)
     * set false if you want to record movie using MediaVideoEncoder
     */
    private static final boolean USE_SURFACE_ENCODER = false;

    /**
     * preview resolution(width)
     * if your camera does not support specific resolution and mode,
     * {@link UVCCamera#setPreviewSize(int, int, int)} throw exception
     */
    private static final int PREVIEW_WIDTH = 640;

    /**
     * preview resolution(height)
     * if your camera does not support specific resolution and mode,
     * {@link UVCCamera#setPreviewSize(int, int, int)} throw exception
     */
    private static final int PREVIEW_HEIGHT = 480;

    /**
     * preview mode
     * if your camera does not support specific resolution and mode,
     * {@link UVCCamera#setPreviewSize(int, int, int)} throw exception
     * 0:YUYV, other:MJPEG
     */
    private static final int PREVIEW_MODE = 0;

    protected static final int SETTINGS_HIDE_DELAY_MS = 2500;

    /**
     * for accessing USB
     */
    private USBMonitor usbMonitor;

    /**
     * Handler to execute camera related methods sequentially on private thread
     */
    private UVCCameraHandler mCameraHandler;

    /**
     * for camera preview display
     */
    private CameraViewInterface cameraViewInterface;

    /**
     * for open&start / stop&close camera preview
     */
    @BindView(R.id.imageButton)
    public ImageButton mCameraButton;
    @BindView(R.id.imageView)
    public ImageView mImageView;
    private boolean isScaling = false;
    private boolean isInCapturing = false;
    private int[][] captureSolution = {{640, 480}, {800, 600}, {1024, 768}, {1280, 1024}};
    private int mCaptureWidth = captureSolution[0][0];
    private int mCaptureHeight = captureSolution[0][1];

    private Bitmap bitmap = null;
    private Bitmap tempBitmap = null;
    private final Bitmap srcBitmap = Bitmap.createBitmap(PREVIEW_WIDTH, PREVIEW_HEIGHT, Bitmap.Config.RGB_565);

    /**
     * 正脸 级联分类器
     */
    private CascadeClassifier mFrontalFaceClassifier = null;
    private final Size m65Size = new Size(65, 65);
    private final Size mDefault = new Size();
    private Rect[] mFrontalFacesArray;
    /**
     * event handler when click camera / capture button
     */
    private final View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(final View view) {
            synchronized (mSync) {
                if ((mCameraHandler != null) && !mCameraHandler.isOpened()) {
                    CameraDialog.showDialog(CameraActivity.this);
                } else {
                    mCameraHandler.close();
                }
            }
        }
    };

    /**
     * usb 回调事件
     */
    private final USBMonitor.OnDeviceConnectListener mOnDeviceConnectListener = new USBMonitor.OnDeviceConnectListener() {
        @Override
        public void onAttach(final UsbDevice device) {
            Toast.makeText(CameraActivity.this, "USB_DEVICE_ATTACHED", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onConnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock, final boolean createNew) {
            if (DEBUG) {
                Log.v(TAG, "onConnect:");
            }
            synchronized (mSync) {
                if (mCameraHandler != null) {
                    mCameraHandler.open(ctrlBlock);
                    startPreview();
                    updateItems();
                }
            }
        }

        @Override
        public void onDisconnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock) {
            if (DEBUG) {
                Log.v(TAG, "onDisconnect:");
            }
            synchronized (mSync) {
                if (mCameraHandler != null) {
                    queueEvent(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                // maybe throw java.lang.IllegalStateException: already released
                                mCameraHandler.setPreviewCallback(null); //zhf
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            mCameraHandler.close();
                        }
                    }, 0);
                }
            }
        }

        @Override
        public void onDettach(final UsbDevice device) {
            Toast.makeText(CameraActivity.this, "USB_DEVICE_DETACHED", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onCancel(final UsbDevice device) {
        }
    };

    /**
     * 预览帧回调
     */
    private final IFrameCallback mIFrameCallback = new IFrameCallback() {
        @Override
        public void onFrame(final ByteBuffer frame) {
            frame.clear();
            if (!isActive() || isInCapturing) {
                return;
            }
            if (bitmap == null) {
                Toast.makeText(CameraActivity.this, "错误：Bitmap为空", Toast.LENGTH_SHORT).show();
                return;
            }
            /**
             * 这里进行opencv操作
             * srcBitmap:源
             * bitmap:处理后
             */
            synchronized (bitmap) {
                srcBitmap.copyPixelsFromBuffer(frame);

                if (bitmap.getWidth() != mCaptureWidth || bitmap.getHeight() != mCaptureHeight) {
                    bitmap = Bitmap.createBitmap(mCaptureWidth, mCaptureHeight, Bitmap.Config.RGB_565);
                }

                Mat rgbMat = new Mat();
                Mat grayMat = new Mat();
                bitmap = Bitmap.createBitmap(srcBitmap.getWidth(), srcBitmap.getHeight(), Bitmap.Config.RGB_565);
                Utils.bitmapToMat(srcBitmap, rgbMat);//convert original bitmap to Mat, R G B.
                Imgproc.cvtColor(rgbMat, grayMat, Imgproc.COLOR_RGB2GRAY);//rgbMat to gray grayMat

                /**检测算法 */
                if (mFrontalFaceClassifier != null) {
                    MatOfRect frontalFaces = new MatOfRect();

                    mFrontalFaceClassifier.detectMultiScale(grayMat, frontalFaces, 1.1, 5, 2, m65Size, mDefault);
                    mFrontalFacesArray = frontalFaces.toArray();

                    if (mFrontalFacesArray.length > 0) {
                        for (int i = 0; i < mFrontalFacesArray.length; i++) {
                            Imgproc.rectangle(rgbMat, mFrontalFacesArray[i].tl(), mFrontalFacesArray[i].br(), new Scalar(0, 255, 0, 255), 3);
                        }
                    }
                }


                Utils.matToBitmap(rgbMat, bitmap); //convert mat to bitmap
                Log.i(TAG, "procSrc2Gray sucess...");
            }

            tempBitmap = bitmap;
            mImageView.post(mUpdateImageTask);
        }
    };

    /**
     * 更新界面图片事件
     */
    private final Runnable mUpdateImageTask = new Runnable() {
        @Override
        public void run() {
            synchronized (bitmap) {
                mImageView.setImageBitmap(tempBitmap);
            }
        }
    };

    /**
     * 利用Activity.runOnUiThread(Runnable)把更新ui的代码创建在Runnable中，
     * 然后在需要更新ui时，把这个Runnable对象传给Activity.runOnUiThread(Runnable)
     */
    private void updateItems() {
        runOnUiThread(mUpdateItemsOnUITask, 100);
    }

    private final Runnable mUpdateItemsOnUITask = new Runnable() {
        @Override
        public void run() {
            if (isFinishing()) {
                return;
            }
            final int visible_active = isActive() ? View.VISIBLE : View.INVISIBLE;
            mImageView.setVisibility(visible_active);
        }
    };

    /** 手动装载openCV库文件，以保证手机无需安装OpenCV Manager */
    static {
        System.loadLibrary("opencv_java4");
    }

    private void initView() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // 设置全屏没有写
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initView();
        setContentView(R.layout.activity_camera);
        ButterKnife.bind(this);
        Log.v(TAG, "onCreate:");

        mCameraButton.setOnClickListener(mOnClickListener);

        mCaptureWidth = captureSolution[0][0];
        mCaptureHeight = captureSolution[0][1];

        bitmap = Bitmap.createBitmap(mCaptureWidth, mCaptureHeight, Bitmap.Config.RGB_565);

        /** usb 预览窗口 */
        final View view = findViewById(R.id.camera_view);
        cameraViewInterface = (CameraViewInterface) view;
        cameraViewInterface.setAspectRatio(PREVIEW_WIDTH / (float) PREVIEW_HEIGHT);

        /** 创建usb对象 */
        synchronized (mSync) {
            usbMonitor = new USBMonitor(this, mOnDeviceConnectListener);
            mCameraHandler = UVCCameraHandler.createHandler(this, cameraViewInterface,
                    USE_SURFACE_ENCODER ? 0 : 1, PREVIEW_WIDTH, PREVIEW_HEIGHT, PREVIEW_MODE);
        }

        /**初始化 opencv 分类器 */
        initFrontalFace();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.v(TAG, "onStart:");
        synchronized (mSync) {
            usbMonitor.register();
        }
        if (cameraViewInterface != null) {
            cameraViewInterface.onResume();
        }
    }

    @Override
    protected void onStop() {
        Log.v(TAG, "onStop:");
        synchronized (mSync) {
            mCameraHandler.close();    // #close include #stopRecording and #stopPreview
            usbMonitor.unregister();
        }
        if (cameraViewInterface != null) {
            cameraViewInterface.onPause();
        }

        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.v(TAG, "onDestroy:");
        synchronized (mSync) {
            if (mCameraHandler != null) {
                mCameraHandler.setPreviewCallback(null);
                mCameraHandler.release();
                mCameraHandler = null;
            }
            if (usbMonitor != null) {
                usbMonitor.destroy();
                usbMonitor = null;
            }
        }

        super.onDestroy();
    }

    /**
     * to access from CameraDialog
     *
     * @return
     */
    @Override
    public USBMonitor getUSBMonitor() {
        synchronized (mSync) {
            return usbMonitor;
        }
    }

    @Override
    public void onDialogResult(boolean b) {
        if (DEBUG) {
            Log.v(TAG, "onDialogResult:canceled=" + b);
        }
    }


    private void showShortMsg(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    /**
     * 开始预览
     */
    private void startPreview() {
        synchronized (mSync) {
            if (mCameraHandler != null) {
                final SurfaceTexture st = cameraViewInterface.getSurfaceTexture();
                /**
                 * 由于surfaceview由另一个线程处理，这里使用消息处理机制
                 * 对Frame进行回调处理
                 */
                mCameraHandler.setPreviewCallback(mIFrameCallback);
                mCameraHandler.startPreview(new Surface(st));
            }
        }
        updateItems();
    }

    /**
     * usb相机是否打开
     *
     * @return
     */
    private boolean isActive() {
        return mCameraHandler != null && mCameraHandler.isOpened();
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

}