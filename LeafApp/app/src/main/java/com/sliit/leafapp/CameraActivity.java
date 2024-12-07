package com.sliit.leafapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.core.internal.utils.ImageUtil;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;


import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.widget.TextView;

import com.google.common.util.concurrent.ListenableFuture;
import com.sliit.leafapp.ml.Mobilenet;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class CameraActivity extends AppCompatActivity {

    private PreviewView previewView;
    private ExecutorService cameraExecutor;
    private TextView resultText;
    Mobilenet model;
    TensorBuffer inputFeature0;
    private ImageProcessor imageProcessor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera); // Layout with a TextureView (or PreviewView)

        previewView = findViewById(R.id.view_finder);
        resultText = findViewById(R.id.text_result);
        inputFeature0 =TensorBuffer.createFixedSize(new int[]{1, 224, 224, 3}, DataType.FLOAT32);
        imageProcessor = new ImageProcessor.Builder()
                .add(new ResizeOp(224, 224, ResizeOp.ResizeMethod.BILINEAR)) // Resize to model input size
                .add(new NormalizeOp(0.0f, 1.0f)) // Normalize to [0, 1]
                .build();
        // Initialize the camera executor
        cameraExecutor = Executors.newSingleThreadExecutor();
        try {
            model=Mobilenet.newInstance(this);
        } catch (IOException e) {
            e.printStackTrace();
        }


        startCamera();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Select the camera
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, image -> {
                    try {

                        // Convert the image to a ByteBuffer or TensorImage for processing
//                        ByteBuffer byteBuffer = ImageUtil.createDirectByteBuffer(image.toBitmap());
//                        TensorImage inputImage = TensorImage.fromByteBuffer(byteBuffer);

                        // Run the model inference
                        Bitmap resizedBitmap = Bitmap.createScaledBitmap(image.toBitmap(), 224, 224, true); // Adjust size to match model input
                        TensorImage tensorImage = new TensorImage(DataType.FLOAT32);
                        tensorImage.load(resizedBitmap);


                        tensorImage = imageProcessor.process(tensorImage);

                        ByteBuffer byteBuffer = tensorImage.getBuffer();

//                        for (int i = 0; i < byteBuffer.capacity(); i++) {
//                            byteBuffer.putFloat((byteBuffer.get(i) & 0xFF) / 255.0f); // Normalize each pixel
//                        }

                            inputFeature0.loadBuffer(byteBuffer);
                            byteBuffer.clear();
                            // Runs model inference and gets result.
                            Mobilenet.Outputs outputs = model.process(inputFeature0);
                            TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();

                            float[] outputProbabilities = outputFeature0.getFloatArray();
                            int predictedIndex = (int) outputProbabilities[0];
                            Log.d("out", String.valueOf(outputProbabilities[0]));
                            runOnUiThread(() -> resultText.setText("Prediction Index: " +  outputProbabilities[0]));
//


                            // Releases model resources if no longer used.



                        // Handle the model output (e.g., print to log, update UI)
                    //    Log.d("CameraActivity", "Inference result: " + outputFeature0.toString());

                    } catch (Exception e) {
                        Log.e("CameraActivity", "Error processing image", e);
                    } finally {
                        image.close();
                    }
                });

                // Build the preview use case
                Preview preview = new Preview.Builder()
                        .build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // Bind the use case to the lifecycle
                Camera camera = cameraProvider.bindToLifecycle(
                        this,
                        cameraSelector,
                        imageAnalysis,
                        preview
                );

            } catch (Exception e) {
                Log.e("CameraActivity", "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Shutdown the camera executor when not needed
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
            try {
                cameraExecutor.awaitTermination(1, TimeUnit.SECONDS);
                model.close();
            } catch (InterruptedException e) {
                Log.e("CameraActivity", "Executor service shutdown interrupted", e);
            }
        }
    }
    private int getMaxIndex(float[] probabilities) {
        int maxIndex = 0;
        float maxProb = probabilities[0];
        for (int i = 1; i < probabilities.length; i++) {
            if (probabilities[i] > maxProb) {
                maxProb = probabilities[i];
                maxIndex = i;
            }
        }
        return maxIndex;
    }
}