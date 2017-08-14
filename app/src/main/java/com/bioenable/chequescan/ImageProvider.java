package com.bioenable.chequescan;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.IntentSender;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.FileProvider;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.OpenFileActivityBuilder;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * The activity is used to get the image for analysis for the app. This class provides all the methods
 * to get the correct picture asked by the user. The user can get the image from:
 * 1. Camera
 * 2. Gallery
 * 3. Google Drive
 *
 * @author Ayush Ranjan
 * @since 13/08/17.
 */
public class ImageProvider extends Activity implements ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    // constants
    private static final int CAMERA_CODE = 4818;
    private static final int LIBRARY_CODE = 1469;
    private static final int DRIVE_CODE = 1470;
    private static final int RESOLVE_CONNECTION_REQUEST_CODE = 123;

    // Instance variables
    private String pathToPhoto;
    private Button cameraButton;
    private Button galleryButton;
    private Button googleDriveButton;
    private GoogleApiClient googleApiClient;
    private ProgressDialog waitingDrivePhotoDownload;

    // Enum represents the mode in which the user wants to get the image.
    private enum ImageSource {
        GoogleDrive, Camera, Library
    }

    /**
     * Oncreate just sets the layout with button listeners and initialises all elements correctly.
     *
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.get_image_activity);
        initialiseComponents();
        setButtonListeners();
    }

    /**
     * When this activity stops, we have to disconnect from the google drive
     */
    @Override
    protected void onStop() {
        super.onStop();
        if (googleApiClient != null) {
            // disconnect Google Android Drive API connection.
            googleApiClient.disconnect();
        }
    }

    /**
     * Hooks up the UI with the instance variables
     */
    private void initialiseComponents() {
        cameraButton = (Button) findViewById(R.id.camera_btn);
        galleryButton = (Button) findViewById(R.id.gallery_btn);
        googleDriveButton = (Button) findViewById(R.id.drive_btn);
        waitingDrivePhotoDownload = new ProgressDialog(this);
        waitingDrivePhotoDownload.setTitle("Downloading image from Google Drive");
        waitingDrivePhotoDownload.setMessage("Please wait...");
    }

    /**
     * This method is called whenever the user asks for an image
     *
     * @param type this determines which source the user wants the image from
     */
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

    /**
     * Sets the three button listeners which call the GetImage method correctly
     */
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

    /**
     * When photo is taken from gallery, this method is called to get the path to the photo from the
     * URI of the photo passed into onActivityResult. This code was taken from the given link.
     *
     * @param uri URI containing the image from gallery
     * @return String representation of path to the image
     * @see <a href="https://stackoverflow.com/questions/20324155/get-filepath-and-filename-of-selected-gallery-image-in-android">
     * Get filepath and filename of selected gallery image</a>
     */
    public String getRealPathFromURI(Uri uri) {
        String[] projection = {MediaStore.Images.Media.DATA};
        @SuppressWarnings("deprecation")
        Cursor cursor = managedQuery(uri, projection, null, null, null);
        int column_index = cursor
                .getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        return cursor.getString(column_index);
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

    /**
     * This method gets a valid file in external memory to save the picture and starts ActivityForResult
     * which fills the file with the complete full size image from the camera.
     *
     * @see <a href="https://developer.android.com/training/camera/photobasics.html#TaskPath">Full Size Pic from Camera</a>
     */
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

    /**
     * This method simply starts activity fot result with the correct intent to get image from the
     * gallery.
     */
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
        Drive.DriveApi.newDriveContents(googleApiClient).setResultCallback(driveContentsCallback);
    }

    private final ResultCallback<DriveApi.DriveContentsResult> driveContentsCallback =
            new ResultCallback<DriveApi.DriveContentsResult>() {
                @Override
                public void onResult(@NonNull DriveApi.DriveContentsResult result) {
                    if (result.getStatus().isSuccess()) {
                        IntentSender intentSender = Drive.DriveApi
                                .newOpenFileActivityBuilder()
                                .setMimeType(new String[]{"image/jpeg", "image/jpg", "image/png"})
                                .build(googleApiClient);
                        try {
                            startIntentSenderForResult(intentSender, DRIVE_CODE, null, 0, 0, 0);
                        } catch (IntentSender.SendIntentException e) {
                            Toast.makeText(getApplicationContext(), "Could not open Google Drive", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            };

    /**
     * Google Drive and Gallery methods call this method. All three methods of getting image should
     * ultimately give a valid file path to the image.
     * For getting the image from gallery, we just get the path to that image.
     * For getting the image from Google Drive, we use a resultCallBack function which takes the
     * DriveFile, extracts its contents and fills a File with it.
     *
     * @param requestCode determines which method was used to get the image
     * @param resultCode  determined success of that method
     * @param data        contains the file or image
     */
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

        if (requestCode == RESOLVE_CONNECTION_REQUEST_CODE)
            googleApiClient.connect();

        if (requestCode == LIBRARY_CODE) {
            pathToPhoto = getRealPathFromURI(data.getData());
        }

        if (requestCode == DRIVE_CODE) {
            //this extra contains the drive id of the selected file
            DriveId driveId = (DriveId) data.getParcelableExtra(OpenFileActivityBuilder.EXTRA_RESPONSE_DRIVE_ID);
            DriveFile file = driveId.asDriveFile();
            waitingDrivePhotoDownload.show();
            file.open(googleApiClient, DriveFile.MODE_READ_ONLY, null)
                    .setResultCallback(contentsOpenedCallback);
        }
    }

    private ResultCallback<DriveApi.DriveContentsResult> contentsOpenedCallback =
            new ResultCallback<DriveApi.DriveContentsResult>() {
                @Override
                public void onResult(@NonNull DriveApi.DriveContentsResult result) {
                    if (!result.getStatus().isSuccess()) {
                        waitingDrivePhotoDownload.cancel();
                        Toast.makeText(getApplicationContext(), "This file cannot be opened.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // DriveContents object contains pointers to the actual byte stream
                    DriveContents contents = result.getDriveContents();
                    try {
                        File localFile = createImageFile();
                        OutputStream out = new BufferedOutputStream(new FileOutputStream(localFile));
                        InputStream in = contents.getInputStream();
                        int b;
                        while ( (b = in.read()) >= 0) {
                            out.write(b);
                        }
                        in.close();
                        out.close();
                        waitingDrivePhotoDownload.cancel();
                    } catch (Exception e) {
                        waitingDrivePhotoDownload.cancel();
                        Toast.makeText(getApplicationContext(), "This file could not be read.", Toast.LENGTH_SHORT).show();
                    }
                    contents.discard(googleApiClient);
                }
            };

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        if (connectionResult.hasResolution()) {
            try {
                connectionResult.startResolutionForResult(this, RESOLVE_CONNECTION_REQUEST_CODE);
            } catch (IntentSender.SendIntentException e) {
                Toast.makeText(this, "Unable to resolve the connection issue", Toast.LENGTH_SHORT).show();
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

}