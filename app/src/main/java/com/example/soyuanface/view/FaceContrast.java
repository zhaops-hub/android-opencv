package com.example.soyuanface.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import com.example.soyuanface.R;
import com.example.soyuanface.common.OpencvHelper;
import com.example.soyuanface.model.ImageFaceModel;
import org.opencv.core.*;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * @author zps
 */
public class FaceContrast extends AppCompatActivity implements View.OnClickListener {
    private ImageView mIvGrayFace1;
    private ImageView mIvGrayFace2;
    private Mat mGrayFace1, mGrayFace2;
    private Button btnGrayFace, btnContrasFace;
    private CascadeClassifier classifier;

    /** 手动装载openCV库文件，以保证手机无需安装OpenCV Manager */
    static {
        System.loadLibrary("opencv_java4");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_contrast);

        mIvGrayFace1 = findViewById(R.id.iv_face_1);
        mIvGrayFace2 = findViewById(R.id.iv_face_2);
        btnGrayFace = findViewById(R.id.btn_gray_face);
        btnContrasFace = findViewById(R.id.btn_contras_face);
        btnGrayFace.setOnClickListener(this);
        btnContrasFace.setOnClickListener(this);
        initClassifier();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_gray_face:
                //检测人脸是耗时的操作
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        getImage1Face();
                        getImage2Face();
                    }
                }).start();
                break;
            case R.id.btn_contras_face:
                if (mGrayFace1 == null || mGrayFace2 == null) {
                    Toast.makeText(getApplicationContext(), "请先获取灰度图", Toast.LENGTH_SHORT).show();
                } else {
                    double target = OpencvHelper.comPareHist(mGrayFace1, mGrayFace2);
                    Toast.makeText(getApplicationContext(), "相识度:" + target, Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                break;
        }
    }

    private void initClassifier() {
        try {
            InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
            File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
            File cascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");
            FileOutputStream os = new FileOutputStream(cascadeFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();
            classifier = new CascadeClassifier(cascadeFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void getImage1Face() {
        Bitmap mBitmap1 = BitmapFactory.decodeResource(getResources(), R.drawable.img2);
        ImageFaceModel result = OpencvHelper.getImageFace(mBitmap1, classifier);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mIvGrayFace1.setImageBitmap(result.getBitmap());
            }
        });

        mGrayFace1 = result.getMat();
    }

    private void getImage2Face() {
        Bitmap mBitmap1 = BitmapFactory.decodeResource(getResources(), R.drawable.img3);
        ImageFaceModel result = OpencvHelper.getImageFace(mBitmap1, classifier);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mIvGrayFace2.setImageBitmap(result.getBitmap());
            }
        });

        mGrayFace2 = result.getMat();
    }
}