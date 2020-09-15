package com.example.soyuanface.model;

import android.graphics.Bitmap;
import org.opencv.core.Mat;

/**
 * @author zps
 */
public class ImageFaceModel {
    private Bitmap bitmap;
    private Mat mat;


    public Bitmap getBitmap() {
        return bitmap;
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    public Mat getMat() {
        return mat;
    }

    public void setMat(Mat mat) {
        this.mat = mat;
    }
}
