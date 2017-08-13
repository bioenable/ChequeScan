package com.bioenable.chequescan;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v4.content.FileProvider;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

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
public class ImageProvider extends Activity {

    // Instance variables
    private String pathToPhoto;

    // constants
    private static final int CAMERA_CODE = 4818;
    private static final int LIBRARY_CODE = 1469;
    private static final int DRIVE_CODE = 1470;

    private ImageView image;
    private Button cameraButton;
    private Button galleryButton;

    private enum ImageSource {
        Drive, Camera, Library
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.get_image_activity);
        initialiseComponents();
        setButtonListeners();
    }

    private void initialiseComponents() {
        image = (ImageView) findViewById(R.id.pic);
        cameraButton = (Button) findViewById(R.id.camera_btn);
        galleryButton = (Button) findViewById(R.id.gallery_btn);
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
            case Drive:
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
