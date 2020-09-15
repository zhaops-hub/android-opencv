package com.example.soyuanface.common;


import android.graphics.Bitmap;
import com.example.soyuanface.model.ImageFaceModel;
import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

/**
 * @author SoYuan
 */
public class OpencvHelper {

    /**
     * 比较来个矩阵的相似度
     *
     * @param srcMat
     * @param desMat
     */
    public static double comPareHist(Mat mGrayFace1, Mat mGrayFace2) {
        mGrayFace1.convertTo(mGrayFace1, CvType.CV_32F);
        Mat mat2 = new Mat();
        Imgproc.resize(mGrayFace2, mat2, new Size(mGrayFace1.cols(), mGrayFace1.rows()));
        mat2.convertTo(mat2, CvType.CV_32F);
        double target = Imgproc.compareHist(mGrayFace1, mat2, Imgproc.CV_COMP_CORREL);
        return target;
    }

    /**
     * 获取人脸的灰度图片
     *
     * @param mBitmap
     * @return
     */
    public static ImageFaceModel getImageFace(Bitmap mBitmap, CascadeClassifier classifier) {
        ImageFaceModel result = new ImageFaceModel();
        Mat mat1 = new Mat();
        Mat mat11 = new Mat();
        Utils.bitmapToMat(mBitmap, mat1);
        Imgproc.cvtColor(mat1, mat11, Imgproc.COLOR_BGR2GRAY);
        // 图片上只有一个人，当然也就只能识别出一张人脸，所以我们直接取第一个就行了
        Rect[] object = detectObjectImage(mat11, classifier);
        if (object != null && object.length > 0) {
            Mat mat = mat11.submat(object[0]);
            result.setMat(mat);
            final Bitmap bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(mat, bitmap);
            result.setBitmap(bitmap);
        }

        return result;
    }

    /**
     * 获取人脸
     *
     * @param gray
     * @return
     */
    public static Rect[] detectObjectImage(Mat gray, CascadeClassifier classifier) {
        MatOfRect faces = new MatOfRect();
        classifier.detectMultiScale(gray, faces);
        return faces.toArray();
    }
}
