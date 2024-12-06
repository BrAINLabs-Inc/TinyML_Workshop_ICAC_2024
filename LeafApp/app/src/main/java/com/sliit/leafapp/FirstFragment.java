package com.sliit.leafapp;

import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.sliit.leafapp.databinding.FragmentFirstBinding;

import com.sliit.leafapp.ml.Mobilenet;;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class FirstFragment extends Fragment {

    private FragmentFirstBinding binding;
    private ImageView imageView;
    private TextView textView;
    private ActivityResultLauncher<String> pickImageLauncher;
    private ImageProcessor imageProcessor;
    
    private int SELECT_PICTURE=200;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {


        binding = FragmentFirstBinding.inflate(inflater, container, false);
        imageView = binding.imageView.findViewById(R.id.imageView); // Ensure this is initialized correctly
        textView=binding.textviewFirst.findViewById(R.id.textview_first);
        imageProcessor = new ImageProcessor.Builder()

                .add(new NormalizeOp(0.0f, 1.0f)) // Normalize to [0, 1]
                .build();

        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        // Display the selected image in the ImageView

                        Log.d("url", String.valueOf(uri));
                        displayImageFromUri(uri);
                    //    imageView.setImageURI(Uri.parse(uri.getPath()));
                    }
                }
        );

        return binding.getRoot();



    }

    private void displayImageFromUri(Uri uri) {
        try {
            ContentResolver contentResolver = requireContext().getContentResolver();
            InputStream inputStream = contentResolver.openInputStream(uri);

            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            imageView.setImageBitmap(bitmap);

            processImage(bitmap);
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Failed to load image: " + e.getMessage());
        }

    }

    private void processImage(Bitmap bitmap) {

        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true); // Adjust size to match model input
        TensorImage tensorImage = new TensorImage(DataType.FLOAT32);
        tensorImage.load(resizedBitmap);
       // tensorImage = imageProcessor.process(tensorImage);
        ByteBuffer byteBuffer = tensorImage.getBuffer();


        try {

            Mobilenet model = Mobilenet.newInstance(this.getContext());

            // Creates inputs for reference.
            TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 224, 224, 3}, DataType.FLOAT32);
            inputFeature0.loadBuffer(byteBuffer);


            // Runs model inference and gets result.
            Mobilenet.Outputs outputs = model.process(inputFeature0);
            TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();

            float[] outputProbabilities = outputFeature0.getFloatArray();
            //int predictedIndex = (int) outputProbabilities[0];
            Log.d("out", String.valueOf(outputProbabilities.length));
           textView.setText("Prediction Index: " + outputProbabilities[0]);

            // Releases model resources if no longer used.
            model.close();
        } catch (IOException e) {
            // TODO Handle the exception
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

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.buttonSelect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                imageChooser();
                pickImageLauncher.launch("image/*");
//                NavHostFragment.findNavController(FirstFragment.this)
//                        .navigate(R.id.action_FirstFragment_to_SecondFragment);

            }
        });
    }

//    private void imageChooser() {
//        Intent i = new Intent();
//        i.setType("image/*");
//        i.setAction(Intent.ACTION_GET_CONTENT);
//
//        // pass the constant to compare it
//        // with the returned requestCode
//        startActivity(Intent.createChooser(i, "Select Picture"));
//
//    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}