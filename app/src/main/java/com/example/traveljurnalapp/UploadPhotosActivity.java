package com.example.traveljurnalapp;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UploadPhotosActivity extends AppCompatActivity {

    private static final int REQUEST_CODE = 1;
    private static final String PERFORMANCE_TAG = "PERFORMANCE_UPLOAD";

    private final List<Uri> imageUris = new ArrayList<>();

    private PhotoAdapter adapter;
    private FloatingActionButton addPhotosButton;
    private Button doneButton;
    private int favoritePosition;
    private String tripId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload_photos);

        tripId = getIntent().getStringExtra("tripId");

        ArrayList<Uri> incomingUris = getIntent().getParcelableArrayListExtra("selectedImages");
        if (incomingUris != null && !incomingUris.isEmpty()) {
            imageUris.addAll(incomingUris);
        }

        favoritePosition = getIntent().getIntExtra("favoriteIndex", -1);

        RecyclerView recyclerView = findViewById(R.id.photoRecyclerView);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));

        adapter = new PhotoAdapter(this, imageUris, new PhotoActionListener() {
            @Override
            public void onRemovePhoto(int position) {
                imageUris.remove(position);
                if (position == favoritePosition) {
                    favoritePosition = -1;
                }
                adapter.notifyItemRemoved(position);
            }

            @Override
            public void onFavoritePhoto(int position) {
                adapter.promptFavoriteChange(position);
                favoritePosition = position;
            }
        });

        recyclerView.setAdapter(adapter);

        addPhotosButton = findViewById(R.id.addPhotosButton);
        doneButton = findViewById(R.id.doneButton);

        addPhotosButton.setOnClickListener(v -> checkPermissionAndPickImages());

        doneButton.setOnClickListener(v -> {
            Log.d(PERFORMANCE_TAG, "DONE BUTTON CLICKED");

            if (imageUris.isEmpty()) {
                Toast.makeText(this, "No photos selected", Toast.LENGTH_SHORT).show();
                return;
            }

            if (tripId != null) {
                uploadPhotosToFirebase();
            } else {
                Intent resultIntent = new Intent();
                resultIntent.putParcelableArrayListExtra("selectedImages", new ArrayList<>(imageUris));
                resultIntent.putExtra("favoriteIndex", favoritePosition);
                setResult(RESULT_OK, resultIntent);
                finish();
            }
        });
    }

    private void uploadPhotosToFirebase() {
        Log.d(PERFORMANCE_TAG, "UPLOAD FUNCTION STARTED");

        long startTime = System.currentTimeMillis();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseStorage storage = FirebaseStorage.getInstance();

        db.collection("users")
                .document(user.getUid())
                .collection("trips")
                .document(tripId)
                .collection("photos")
                .whereEqualTo("isFavorite", true)
                .get()
                .addOnSuccessListener(existingFavorites -> {
                    boolean alreadyHasFavorite = !existingFavorites.isEmpty();

                    Runnable uploadTask = () -> uploadSelectedPhotos(
                            user,
                            db,
                            storage,
                            alreadyHasFavorite,
                            startTime
                    );

                    if (alreadyHasFavorite && favoritePosition != -1) {
                        new CustomActionDialogFragment(
                                "This trip already has a favorite photo. Do you want to replace it with the new one?",
                                uploadTask::run,
                                () -> {
                                    favoritePosition = -1;
                                    uploadTask.run();
                                }
                        ).show(getSupportFragmentManager(), "ReplaceDialog");
                    } else {
                        uploadTask.run();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(PERFORMANCE_TAG, "Favorite check failed: " + e.getMessage());
                    Toast.makeText(this, "Failed to check favorite photo", Toast.LENGTH_SHORT).show();
                });
    }

    private void uploadSelectedPhotos(
            FirebaseUser user,
            FirebaseFirestore db,
            FirebaseStorage storage,
            boolean alreadyHasFavorite,
            long startTime
    ) {
        final int totalPhotos = imageUris.size();
        final int[] uploadedPhotos = {0};
        final int[] failedPhotos = {0};

        for (int i = 0; i < imageUris.size(); i++) {
            Uri imageUri = imageUris.get(i);

            try {
                long compressionStart = System.currentTimeMillis();

                File originalFile = copyUriToFile(this, imageUri);
                long originalSize = originalFile.length();

                File compressedFile = compressImageToFile(this, imageUri);
                long compressedSize = compressedFile.length();

                long compressionEnd = System.currentTimeMillis();
                long compressionDuration = compressionEnd - compressionStart;

                double reduction = 0.0;
                if (originalSize > 0) {
                    reduction = (1.0 - ((double) compressedSize / originalSize)) * 100.0;
                }

                Log.d(
                        PERFORMANCE_TAG,
                        "Original size = " + originalSize + " bytes"
                );

                Log.d(
                        PERFORMANCE_TAG,
                        "Compressed size = " + compressedSize + " bytes"
                );

                Log.d(
                        PERFORMANCE_TAG,
                        "Size reduction = " + String.format("%.2f", reduction) + "%"
                );

                Log.d(
                        PERFORMANCE_TAG,
                        "Compression duration = " + compressionDuration + " ms"
                );

                Uri tempUri = Uri.fromFile(compressedFile);

                StorageReference ref = storage.getReference(
                        "users/" + user.getUid()
                                + "/trips/" + tripId
                                + "/photo_" + System.currentTimeMillis()
                                + "_" + i
                                + ".jpg"
                );

                int finalI = i;
                long uploadStart = System.currentTimeMillis();

                ref.putFile(tempUri)
                        .addOnSuccessListener(taskSnapshot ->
                                ref.getDownloadUrl().addOnSuccessListener(downloadUrl -> {
                                    long uploadEnd = System.currentTimeMillis();
                                    long uploadOnlyDuration = uploadEnd - uploadStart;

                                    Log.d(
                                            PERFORMANCE_TAG,
                                            "Firebase upload duration = "
                                                    + uploadOnlyDuration
                                                    + " ms"
                                    );

                                    Map<String, Object> photoData = new HashMap<>();
                                    photoData.put("url", downloadUrl.toString());
                                    photoData.put("isFavorite", finalI == favoritePosition && !alreadyHasFavorite);

                                    db.collection("users")
                                            .document(user.getUid())
                                            .collection("trips")
                                            .document(tripId)
                                            .collection("photos")
                                            .add(photoData)
                                            .addOnSuccessListener(documentReference -> {
                                                uploadedPhotos[0]++;

                                                checkUploadCompletion(
                                                        uploadedPhotos[0],
                                                        failedPhotos[0],
                                                        totalPhotos,
                                                        startTime
                                                );
                                            })
                                            .addOnFailureListener(e -> {
                                                failedPhotos[0]++;

                                                Log.e(
                                                        PERFORMANCE_TAG,
                                                        "Firestore save failed: " + e.getMessage()
                                                );

                                                checkUploadCompletion(
                                                        uploadedPhotos[0],
                                                        failedPhotos[0],
                                                        totalPhotos,
                                                        startTime
                                                );
                                            });
                                })
                        )
                        .addOnFailureListener(e -> {
                            failedPhotos[0]++;

                            Log.e(
                                    PERFORMANCE_TAG,
                                    "Storage upload failed: " + e.getMessage()
                            );

                            Toast.makeText(
                                    this,
                                    "Upload failed: " + e.getMessage(),
                                    Toast.LENGTH_SHORT
                            ).show();

                            checkUploadCompletion(
                                    uploadedPhotos[0],
                                    failedPhotos[0],
                                    totalPhotos,
                                    startTime
                            );
                        });

            } catch (IOException e) {
                failedPhotos[0]++;

                Log.e(
                        PERFORMANCE_TAG,
                        "Image processing failed: " + e.getMessage()
                );

                Toast.makeText(
                        this,
                        "Failed to process image",
                        Toast.LENGTH_SHORT
                ).show();

                checkUploadCompletion(
                        uploadedPhotos[0],
                        failedPhotos[0],
                        totalPhotos,
                        startTime
                );
            }
        }
    }

    private void checkUploadCompletion(
            int uploadedPhotos,
            int failedPhotos,
            int totalPhotos,
            long startTime
    ) {
        if (uploadedPhotos + failedPhotos == totalPhotos) {
            long endTime = System.currentTimeMillis();
            long uploadDuration = endTime - startTime;

            Log.d(
                    PERFORMANCE_TAG,
                    "Photos uploaded: " + uploadedPhotos
                            + "/" + totalPhotos
                            + ", failed: " + failedPhotos
                            + ", duration: " + uploadDuration
                            + " ms"
            );

            Toast.makeText(
                    this,
                    "Upload completed in " + uploadDuration + " ms",
                    Toast.LENGTH_LONG
            ).show();

            finish();
        }
    }

    private File compressImageToFile(Context context, Uri uri) throws IOException {
        InputStream inputStream = context.getContentResolver().openInputStream(uri);
        Bitmap originalBitmap = BitmapFactory.decodeStream(inputStream);

        if (inputStream != null) {
            inputStream.close();
        }

        if (originalBitmap == null) {
            throw new IOException("Could not decode image");
        }

        int maxWidth = 1280;
        int maxHeight = 1280;

        int originalWidth = originalBitmap.getWidth();
        int originalHeight = originalBitmap.getHeight();

        float ratio = Math.min(
                (float) maxWidth / originalWidth,
                (float) maxHeight / originalHeight
        );

        if (ratio > 1.0f) {
            ratio = 1.0f;
        }

        int newWidth = Math.round(originalWidth * ratio);
        int newHeight = Math.round(originalHeight * ratio);

        Bitmap resizedBitmap = Bitmap.createScaledBitmap(
                originalBitmap,
                newWidth,
                newHeight,
                true
        );

        File compressedFile = File.createTempFile("compressed_", ".jpg", context.getCacheDir());
        FileOutputStream outputStream = new FileOutputStream(compressedFile);

        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream);

        outputStream.flush();
        outputStream.close();

        originalBitmap.recycle();

        if (resizedBitmap != originalBitmap) {
            resizedBitmap.recycle();
        }

        return compressedFile;
    }

    private File copyUriToFile(Context context, Uri uri) throws IOException {

        InputStream inputStream =
                context.getContentResolver().openInputStream(uri);

        File tempFile =
                File.createTempFile(
                        "original_",
                        ".jpg",
                        context.getCacheDir()
                );

        FileOutputStream outputStream =
                new FileOutputStream(tempFile);

        byte[] buffer = new byte[1024];
        int length;

        while ((length = inputStream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, length);
        }

        inputStream.close();
        outputStream.close();

        return tempFile;
    }

    private void checkPermissionAndPickImages() {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(this, permission) != getPackageManager().PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{permission}, REQUEST_CODE);
        } else {
            pickImages.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        }
    }

    private final ActivityResultLauncher<PickVisualMediaRequest> pickImages =
            registerForActivityResult(new ActivityResultContracts.PickMultipleVisualMedia(), uris -> {
                if (!uris.isEmpty()) {
                    for (Uri uri : uris) {
                        imageUris.add(uri);
                    }
                    adapter.notifyDataSetChanged();
                }
            });
}