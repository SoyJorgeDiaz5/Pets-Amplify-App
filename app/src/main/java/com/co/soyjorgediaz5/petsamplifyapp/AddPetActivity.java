package com.co.soyjorgediaz5.petsamplifyapp;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.amazonaws.amplify.generated.graphql.CreatePetMutation;
import com.amazonaws.amplify.generated.graphql.ListPetsQuery;
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient;
import com.amazonaws.mobileconnectors.appsync.fetcher.AppSyncResponseFetchers;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.apollographql.apollo.GraphQLCall;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.exception.ApolloException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;

import type.CreatePetInput;

public class AddPetActivity extends AppCompatActivity {

    private static final String TAG = AddPetActivity.class.getSimpleName();

    // Photo selector application code
    private static int RESULT_LOAD_IMAGE = 1;
    private String photoPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_pet);

        Button btnAddItem = findViewById(R.id.btn_save);
        btnAddItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                uploadAndSave();
            }
        });

        Button btnAddPhoto = findViewById(R.id.btn_add_photo);
        btnAddPhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                choosePhoto();
            }
        });
    }

    private void save() {
        CreatePetInput input = getCreatePetInput();

        CreatePetMutation addPetMutation = CreatePetMutation.builder()
                .input(input)
                .build();

        ClientFactory.appSyncClient().mutate(addPetMutation)
                .refetchQueries(ListPetsQuery.builder().build())
                .enqueue(mutateCallback);

        // Enables offline support via an optimistic update
        // Add to event list while offline or before request returns
        addPetOffline(input);
    }

    private void addPetOffline(CreatePetInput input) {
        final CreatePetMutation.CreatePet expected =
                new CreatePetMutation.CreatePet(
                "Pet",
                UUID.randomUUID().toString(),
                input.name(),
                input.description(),
                input.photo()
        );

        final AWSAppSyncClient awsAppSyncClient = ClientFactory.appSyncClient();
        final ListPetsQuery listPetsQuery = ListPetsQuery.builder().build();

        awsAppSyncClient.query(listPetsQuery)
                .responseFetcher(AppSyncResponseFetchers.CACHE_ONLY)
                .enqueue(new GraphQLCall.Callback<ListPetsQuery.Data>() {
                    @Override
                    public void onResponse(@Nonnull Response<ListPetsQuery.Data> response) {
                        List<ListPetsQuery.Item> items = new ArrayList<>();
                        if (response.data() != null){
                            items.addAll(response.data().listPets().items());
                        }

                        items.add(new ListPetsQuery.Item(expected.__typename(),
                                expected.id(),
                                expected.name(),
                                expected.description(),
                                expected.photo()));

                        ListPetsQuery.Data data = new ListPetsQuery.Data(
                                new ListPetsQuery.ListPets(
                                        "ModelPetConnection",
                                        items,
                                        null));
                        awsAppSyncClient.getStore().write(listPetsQuery, data). enqueue(null);
                        Log.d(TAG, "Succesfully wrote item to local store while being offline");

                        finishIfOffline();
                    }

                    @Override
                    public void onFailure(@Nonnull ApolloException e) {
                        Log.e(TAG, "Failed to update event query list", e);
                    }
                });
    }

    private void finishIfOffline() {
        // Close the add activity when offline otherwise allow callback to close
        ConnectivityManager cm = (ConnectivityManager) getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = null;
        if (cm != null) {
            activeNetwork = cm.getActiveNetworkInfo();
        }
        boolean isConnected = activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();

        if (!isConnected) {
            Log.d(TAG, "App is offline. Returning to MainActivity.");
            finish();
        }
    }

    // Mutation callback code
    GraphQLCall.Callback<CreatePetMutation.Data> mutateCallback = new GraphQLCall.Callback<CreatePetMutation.Data>() {
        @Override
        public void onResponse(@Nonnull Response<CreatePetMutation.Data> response) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(AddPetActivity.this, "Pet added", Toast.LENGTH_LONG).show();
                    AddPetActivity.this.finish();
                }
            });
        }

        @Override
        public void onFailure(@Nonnull final ApolloException e) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.e(TAG, "Failed to perform AddPetMutation", e);
                    Toast.makeText(AddPetActivity.this, "Failed to add pet", Toast.LENGTH_LONG).show();
                    AddPetActivity.this.finish();
                }
            });
        }
    };

    public void choosePhoto(){
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, RESULT_LOAD_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RESULT_LOAD_IMAGE && resultCode == RESULT_OK && null != data) {
            Uri selectedImage = data.getData();
            String[] filePathColumn = {MediaStore.Images.Media.DATA};
            Cursor cursor = getContentResolver().query(selectedImage,
                    filePathColumn, null, null, null);
            if (cursor != null) {
                cursor.moveToFirst();
            }
            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            String picturePath = cursor.getString(columnIndex);
            cursor.close();
            // String picturePath contains the path of selected image
            photoPath = picturePath;

        }
    }

    private String getS3Key(String localPath){
        // We have read and write ability under the public folder
        return "public/" + new File(localPath).getName();
    }

    public void uploadWithTransferUtility(String localPath){
        String key = getS3Key(localPath);
        Log.d(TAG, "Uploading file from " + localPath + " to " + key);

        TransferObserver uploadObserver =
                ClientFactory.transferUtility().upload(
                        key,
                        new File(localPath));

        // Attach a listener to the observer to get state update and progress notifications
        uploadObserver.setTransferListener(new TransferListener() {
            @Override
            public void onStateChanged(int id, TransferState state) {
                if (TransferState.COMPLETED == state) {
                    // Handle a completed upload
                    Log.d(TAG, "Upload is completed.");

                    // Upload is successful. Save the rest and send the mutation server.
                    save();
                }
            }

            @Override
            public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                float percentDonef = ((float) bytesCurrent / (float) bytesTotal) * 100;
                int percentDone = (int) percentDonef;

                Log.d(TAG, "ID: " + id + " bytesCurrent: " + bytesCurrent +
                        " bytesTotal: " + bytesTotal + " " + percentDone + "%");
            }

            @Override
            public void onError(int id, Exception ex) {
                // Handle errors
                Log.e(TAG, "Failed to upload photo. ", ex);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(AddPetActivity.this, "Failed to upload photo", Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }
    private CreatePetInput getCreatePetInput(){
        final String name = ((EditText)findViewById(R.id.editTxt_name)).getText().toString();
        final String description = ((EditText)findViewById(R.id.editTxt_description)).getText().toString();

        if (photoPath != null && !photoPath.isEmpty()) {
            return CreatePetInput.builder()
                    .name(name)
                    .description(description)
                    .photo(getS3Key(photoPath))
                    .build();
        } else {
            return CreatePetInput.builder()
                    .name(name)
                    .description(description)
                    .build();
        }
    }

    private void uploadAndSave(){
        if (photoPath != null) {
            // For higher Android levels, we need to check permission at runtime
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                // Permission is not granted
                Log.d(TAG, "READ_EXTERNAL_STORAGE permission not granted! Requesting...");
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
            }

            // Upload a photo first. We will only call save on its successfull callback.
            uploadWithTransferUtility(photoPath);
        } else {
            save();
        }
    }
}
