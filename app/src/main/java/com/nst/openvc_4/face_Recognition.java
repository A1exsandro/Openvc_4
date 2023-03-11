package com.nst.openvc_4;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.util.Log;

import org.opencv.objdetect.CascadeClassifier;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
