package com.nst.openvc_4;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class face_Recognition {
    private Interpreter interpreter;
    private  int INPUT_SIZE;
    private int height=0;
    private int width=0;
    private GpuDelegate gpuDelegate=null;
    private CascadeClassifier cascadeClassifier;

    face_Recognition(AssetManager assetManager, Context context, String modelPath, int input_size) throws IOException {
        INPUT_SIZE = input_size;
        Interpreter.Options options = new Interpreter.Options();
        gpuDelegate = new GpuDelegate();
        
        // if you are using MobileNet model
        //options.addDelegate(gpuDelegate);

        options.setNumThreads(4); // chosse number of thread according to your phone
        
        interpreter = new Interpreter(loadModel(assetManager, modelPath), options);
        Log.d("face_Recognition", "Model is loaded");

        // load haar cascade model
        try{
            // define input steam to read haar cascade file
            InputStream inputStream = context.getResources().openRawResource(R.raw.haarcascade_frontalface_alt);
            File cascadeDir = context.getDir("cascade", Context.MODE_PRIVATE);
            File mCascadeFile = new File(cascadeDir, "haarcascade_frontalface_alt");
            FileOutputStream outputStream = new FileOutputStream(mCascadeFile);
            byte[] buffer = new byte[4096];
            int byteRead;

            while ((byteRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, byteRead);
            }

            inputStream.close();
            outputStream.close();

            cascadeClassifier = new CascadeClassifier(mCascadeFile.getAbsolutePath());
            Log.d("face_recognition", "Classifier is loaded");
        }catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Create a new function with input Mat and output Mat
    public Mat recognizeImage(Mat mat_image) {
        // call this function in onCameraFrame of CameraActivity

        // process rotate mat_image by 90 degree
        Core.flip(mat_image.t(), mat_image, 1);

        // convert mat_image to grayscale
        Mat grayscaleImage = new Mat();
        //                input           output            type
        Imgproc.cvtColor(mat_image, grayscaleImage, Imgproc.COLOR_RGBA2GRAY);

        height = grayscaleImage.height();
        width = grayscaleImage.width();

        int absoluteFaceSize = (int) (height * 0.1);
        MatOfRect faces = new MatOfRect();

        if(cascadeClassifier != null) {
            cascadeClassifier.detectMultiScale(grayscaleImage, faces, 1.1, 2, 2,
                    new Size(absoluteFaceSize, absoluteFaceSize), new Size());
        }

        // convert faces to array
        Rect[] faceArray = faces.toArray();

        for (int i = 0; i < faceArray.length; i++) {
            Imgproc.rectangle(mat_image,faceArray[i].tl(), faceArray[i].br(), new Scalar(0,255,0,255), 2);

            Rect roi = new Rect((int)faceArray[i].tl().x, (int) faceArray[i].tl().y,
                    ((int) faceArray[i].br().x)-((int) faceArray[i].tl().x),
                    ((int) faceArray[i].br().y)-((int) faceArray[i].tl().y));

            Mat cropped_rbg = new Mat(mat_image, roi);

            // convert cropped_rbg to bitmap
            Bitmap bitmap = null;
            bitmap = Bitmap.createBitmap(cropped_rbg.cols(), cropped_rbg.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(cropped_rbg, bitmap);
            Bitmap scaleBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, false);

            // convert scaledBitmap to byteBuffer
            ByteBuffer byteBuffer = convertBitmapToByteBuffer(scaleBitmap);

            // create output
            float[][] face_value = new float[1][1];
            interpreter.run(byteBuffer, face_value);

            Log.d("face_Recognition", "Out: "+Array.get(Array.get(face_value, 0), 0));
        }

        // returning rotate it back by -90 degree
        Core.flip(mat_image.t(), mat_image, 0);

        return mat_image;
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap scaleBitmap) {
        ByteBuffer byteBuffer;
        int input_size = INPUT_SIZE;
        byteBuffer = ByteBuffer.allocateDirect(4 * 1 * input_size * input_size);
        byteBuffer.order(ByteOrder.nativeOrder());

        int[] intValues = new int[input_size * input_size];
        scaleBitmap.getPixels(intValues, 0, scaleBitmap.getWidth(), 0, 0, scaleBitmap.getWidth(), scaleBitmap.getHeight());

        int pixels = 0;
        for (int i = 0; i < input_size; i++) {
            for (int j = 0; j < input_size; j++) {
                final int val =  intValues[pixels++];
                byteBuffer.putFloat((((val>>16)&0xFF))/255.0f);
                byteBuffer.putFloat((((val>>8)&0xFF))/255.0f);
                byteBuffer.putFloat(((val>>16 & 0xFF))/255.0f);
                // this thing is important
                // it is placing RBG to MSB to LSB
            }
        }

        return byteBuffer;
    }

    //this function will load model
    private MappedByteBuffer loadModel(AssetManager assetManager, String modelPath) throws IOException {

        // this will give description of modelPath
        AssetFileDescriptor assetFileDescriptor = assetManager.openFd(modelPath);

        FileInputStream inputStream = new FileInputStream(assetFileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = assetFileDescriptor.getStartOffset();
        long declaredLength = assetFileDescriptor.getDeclaredLength();

        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
}
