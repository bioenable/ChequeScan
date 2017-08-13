package com.bioenable.chequescan;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.FileProvider;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.DriveResource;
import com.google.android.gms.drive.OpenFileActivityBuilder;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * The activity is used to get the image for analysis for the app. This class provides all the methods
 * to get the correct picture asked by the user. The user can get the image from:
 * 1. Camera
 * 2. Gallery
 * 3. Google Drive
 *
 * @author ayushranjan
 * @since 13/08/17.
 */
public class ImageProvider extends Activity implements ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    // Instance variables
    private String pathToPhoto;
    private String urlLinkToFile;
    private ImageView image;
    private Button cameraButton;
    private Button galleryButton;
    private Button googleDriveButton;
    private GoogleApiClient googleApiClient;


    // constants
    private static final int CAMERA_CODE = 4818;
    private static final int LIBRARY_CODE = 1469;
    private static final int DRIVE_CODE = 1470;
    private static final int RESOLVE_CONNECTION_REQUEST_CODE = 123;


    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        if (connectionResult.hasResolution()) {
            try {
                connectionResult.startResolutionForResult(this, RESOLVE_CONNECTION_REQUEST_CODE);
            } catch (IntentSender.SendIntentException e) {
                // Unable to resolve, message user appropriately
            }
        } else {
            GooglePlayServicesUtil.getErrorDialog(connectionResult.getErrorCode(), this, 0).show();
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Toast.makeText(this, "You are connected to Google Drive", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onConnectionSuspended(int i) {
        Toast.makeText(this, "Connection Suspended", Toast.LENGTH_SHORT).show();
    }

    private enum ImageSource {
        GoogleDrive, Camera, Library
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.get_image_activity);
        initialiseComponents();
        setButtonListeners();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (googleApiClient != null) {
            // disconnect Google Android Drive API connection.
            googleApiClient.disconnect();
        }
    }

    private void initialiseComponents() {
        image = (ImageView) findViewById(R.id.pic);
        cameraButton = (Button) findViewById(R.id.camera_btn);
        galleryButton = (Button) findViewById(R.id.gallery_btn);
        googleDriveButton = (Button) findViewById(R.id.drive_btn);
    }

    private void setButtonListeners() {
        cameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getImage(ImageSource.Camera);
            }
        });

        galleryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getImage(ImageSource.Library);
            }
        });

        googleDriveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getImage(ImageSource.GoogleDrive);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK) {
            if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "Cancelled image capture", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getApplicationContext(), "Sorry! Failed to get image", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (requestCode == LIBRARY_CODE) {
            pathToPhoto = getRealPathFromURI(data.getData());
        }

        if (resultCode == DRIVE_CODE) {
            //this extra contains the drive id of the selected file
            DriveId driveId = (DriveId) data.getParcelableExtra(OpenFileActivityBuilder.EXTRA_RESPONSE_DRIVE_ID);
            DriveFile file = Drive.DriveApi.getFile(googleApiClient, driveId);
            DriveResource.MetadataResult mdRslt = file.getMetadata(googleApiClient).await();
            if (mdRslt != null && mdRslt.getStatus().isSuccess()) {
                urlLinkToFile = mdRslt.getMetadata().getWebContentLink();
            }
        }

        image.setImageBitmap(BitmapFactory.decodeFile(pathToPhoto));
    }

    public String getRealPathFromURI(Uri uri) {
        String[] projection = {MediaStore.Images.Media.DATA};
        @SuppressWarnings("deprecation")
        Cursor cursor = managedQuery(uri, projection, null, null, null);
        int column_index = cursor
                .getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        return cursor.getString(column_index);
    }

    private void getImage(ImageSource type) {
        switch (type) {
            case Camera:
                getImageFromCamera();
                break;
            case Library:
                getImageFromGallery();
                break;
            case GoogleDrive:
                getImageFromDrive();
        }
    }

    public void getImageFromCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Toast.makeText(this, "Could not find a file to store the image", Toast.LENGTH_SHORT).show();
                return;
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "com.example.android.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, CAMERA_CODE);
            }
        }
    }

    public void getImageFromGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), LIBRARY_CODE);
    }

    public void getImageFromDrive() {
        if (googleApiClient == null) {
            googleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Drive.API)
                    .addScope(Drive.SCOPE_FILE)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
        }

        googleApiClient.connect();
    }

    /**
     * This method creates a new file in the external storage to save a full size photo. I have used
     * getExternalFilesDir method so that the file is private only to the app. If the photo needs
     * to be saved in a public directory so that it is accessible to other apps then look at link.
     *
     * @return file where the photo has to be saved
     * @throws IOException throws generic exception if error occurs
     * @see <a href="https://developer.android.com/training/camera/photobasics.html#TaskPath">Saving Full Photo</a>
     */
    private File createImageFile() throws IOException {
        // File name is current time and date to avoid conflict.
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";

        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        pathToPhoto = image.getAbsolutePath();
        return image;
    }
}