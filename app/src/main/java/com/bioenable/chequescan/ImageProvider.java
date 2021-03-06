package com.bioenable.chequescan;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
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
 * <p>
 * Note : The image irrespective of the source is saved in a file in external storage which is
 * private to the app. The path to that file is held by pathToPhoto by the end of onActivityResult
 * method. This has been done so that high resolution images can be stored and analysed.
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
    private static final int PICK_FROM_GALLERY_PERMISSION = 1998;

    // Instance variables
    private String pathToPhoto;
    private Button cameraButton;
    private Button galleryButton;
    private Button googleDriveButton;
    private GoogleApiClient googleApiClient;
    private ProgressDialog waitingDrivePhotoDownload;
    private FloatingActionButton backButton;
    private ImageView pic;

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
        backButton = (FloatingActionButton) findViewById(R.id.back_btn);
        pic = (ImageView) findViewById(R.id.image);
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

        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
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
     * <p>
     * NOTE: For Android 6 onwards we need to ask permission at run time. Look at link below for more
     * information. If permission not granted then asks for permission and handles it in
     * onRequestPermissionsResult method. Otherwise starts getting the image from gallery
     *
     * @see <a href="https://developer.android.com/training/permissions/requesting.html"></a>
     */
    public void getImageFromGallery() {
        try {
            if (ActivityCompat.checkSelfPermission(ImageProvider.this, android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(ImageProvider.this, new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, PICK_FROM_GALLERY_PERMISSION);
            } else {
                Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(Intent.createChooser(intent, "Select Picture"), LIBRARY_CODE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * This is triggered after the user gives permission for access into anything. Checks if permission
     * is granted then starts the correct intent to get image from gallery. Otherwise, appropriate
     * message is given out.
     *
     * @param requestCode  request code sent in used to determine which method called for permission
     * @param permissions  permissions object which
     * @param grantResults int array which contains codes for granted requests
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PICK_FROM_GALLERY_PERMISSION:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    startActivityForResult(Intent.createChooser(intent, "Select Picture"), LIBRARY_CODE);
                } else {
                    Toast.makeText(getApplicationContext(), "Permission to access gallery was denied.", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    /*
     * GOOGLE DRIVE FUNCTIONALITY
     *
     * All the methods below are executed in order when an image has to be downloaded from Google
     * Drive. The following process follows:
     *
     * 1. getImageFromDrive() is called when google drive button is pressed. This is responsible for
     *    building the googleApiClient and connecting it to Google Drive. It terminates by calling
     *    driveContentsCallback ResultCallback method.
     *
     * 2. driveContentsCallback ResultCallback object starts up the Google Drive interface after
     *    successfully getting the DriveContents locally. This is done by creating an intentSender
     *    object (allowing only images to download from Drive) and calling startIntentSenderForResult.
     *
     * 3. After the user chooses the image, onActivityResult is triggered where the DriveFile is
     *    extracted and is opened. A ResultCallback object named contentsOpenedCallback is attached.
     *
     * 4. contentsOpenedCallback's onResult method is triggered when the Drive file is successfully
     *    opened. It further goes to save it on a local file in external memory.
     */

    /**
     * Builds the googleApiClient and connects it to Google Drive. Downloads Google Drive contents
     * attaching driveContentsCallback ResultCallback object. A lot of help for getImageFromDrive()
     * and driveContentsCallback object was taken from the Tutorial linked below.
     *
     * @see <a href="https://www.numetriclabz.com/integrate-google-drive-in-android-tutorial/#Read_Google_Drive_file">Tutorial</a>
     * @see <a href="https://developers.google.com/drive/android/auth">Google Drive Auth docs</a>
     */
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
     * For getting the image from Google Drive, we use a resultCallBack object which takes the
     * DriveFile, extracts its contents and fills a File with it.
     *
     * @param requestCode determines which method was used to get the image
     * @param resultCode  determined success of that method
     * @param data        contains the file or image
     * @see <a href="https://developers.google.com/drive/android/files">Saving Drive File Contents</a>
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Return if result is not okay
        if (resultCode != RESULT_OK) {
            if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "Cancelled image capture", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getApplicationContext(), "Sorry! Failed to get image", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        // connection failure resolved and fixed
        if (requestCode == RESOLVE_CONNECTION_REQUEST_CODE) {
            Toast.makeText(getApplicationContext(), "Connection failure fixed!", Toast.LENGTH_SHORT).show();
            googleApiClient.connect();
        }

        // photo taken from gallery
        if (requestCode == LIBRARY_CODE) {
            pathToPhoto = getRealPathFromURI(data.getData());
        }

        // photo chosen from Google Drive
        if (requestCode == DRIVE_CODE) {
            //this extra contains the drive id of the selected file
            DriveId driveId = (DriveId) data.getParcelableExtra(OpenFileActivityBuilder.EXTRA_RESPONSE_DRIVE_ID);
            DriveFile file = driveId.asDriveFile();
            waitingDrivePhotoDownload.show();
            file.open(googleApiClient, DriveFile.MODE_READ_ONLY, null)
                    .setResultCallback(contentsOpenedCallback);
        }

        if (pathToPhoto != null)
            pic.setImageBitmap(BitmapFactory.decodeFile(pathToPhoto));
    }

    /*
    Some code was taken from the link below.
    https://stackoverflow.com/questions/31111658/making-local-copy-of-file-using-google-drive-api
     */
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
                        while ((b = in.read()) >= 0) {
                            out.write(b);
                        }
                        in.close();
                        out.close();
                        waitingDrivePhotoDownload.cancel();
                        if (pathToPhoto != null)
                            pic.setImageBitmap(BitmapFactory.decodeFile(pathToPhoto));
                    } catch (Exception e) {
                        waitingDrivePhotoDownload.cancel();
                        Toast.makeText(getApplicationContext(), "This file could not be read.", Toast.LENGTH_SHORT).show();
                    }
                    contents.discard(googleApiClient);
                }
            };


    /**
     * Triggered when connection fails. This method tries to resolve the connection failure and if
     * there is a resolution it solves it by calling startResolutionForResult which is caught by
     * onActivityResult.
     *
     * @param connectionResult ConnectionResult object which contains information about connection status
     */
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

    /**
     * Triggered when connected to Google Drive
     *
     * @param bundle
     */
    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Toast.makeText(this, "You are connected to Google Drive", Toast.LENGTH_SHORT).show();
    }

    /**
     * Triggered when connection is suspended.
     *
     * @param i
     */
    @Override
    public void onConnectionSuspended(int i) {
        Toast.makeText(this, "Connection Suspended", Toast.LENGTH_SHORT).show();
    }
}