package com.bioenable.chequescan;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.OptionalPendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.squareup.picasso.Picasso;

public class GoogleSignIn extends AppCompatActivity
        implements GoogleApiClient.OnConnectionFailedListener {

    // Constants
    private static final int SIGN_IN_CODE = 1470;

    // Instance Variables
    private GoogleApiClient mGoogleApiClient;
    private SignInButton signInButton;
    private Button signOutButton;
    private ImageView userProfilePicture;
    private Uri userProfilePictureUri;
    private String userDisplayName;
    private TextView userNameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.log_in_activity);

        initialiseComponents();
        initialiseButtonListeners();
        checkSilentSignIn();
    }

    private void updateUI(boolean loggedIn) {
        if (loggedIn) {
            signInButton.setVisibility(View.INVISIBLE);
            signOutButton.setVisibility(View.VISIBLE);
            userNameView.setVisibility(View.VISIBLE);
            userNameView.setText((userDisplayName == null) ? "" : userDisplayName);

            if (userProfilePicture == null) {
                Picasso.with(this).load(R.drawable.unknown_user).into(userProfilePicture);
            } else {
                Picasso.with(this).load(userProfilePictureUri)
                        .transform(new CircleTransformation())
                        .into(userProfilePicture);
            }
        } else {
            userNameView.setVisibility(View.INVISIBLE);
            signOutButton.setVisibility(View.INVISIBLE);
            signInButton.setVisibility(View.VISIBLE);
            Picasso.with(this).load(R.drawable.unknown_user).into(userProfilePicture);
        }
    }

    private void initialiseButtonListeners() {
        signInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                signIn();
            }
        });

        signOutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                signOut();
            }
        });
    }

    /**
     * @see <a href="https://developers.google.com/identity/sign-in/android/sign-in">Google Sign In Docs</a>
     */
    private void initialiseComponents() {
        /*
         * Configure sign-in to request the user's ID, email address, and basic profile. ID and
         * basic profile are included in DEFAULT_SIGN_IN.
         *
         * Note: If you want to request additional information from the user, specify them with
         * requestScopes.
         */
        GoogleSignInOptions googleSignInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();

        // Build a GoogleApiClient with access to the Google Sign-In API and the
        // options specified by gso.
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this, this)
                .addApi(Auth.GOOGLE_SIGN_IN_API, googleSignInOptions)
                .build();

        signInButton = (SignInButton) findViewById(R.id.sign_in_button);
        signInButton.setSize(SignInButton.SIZE_WIDE);
        signInButton.setColorScheme(SignInButton.COLOR_DARK);

        signOutButton = (Button) findViewById(R.id.sign_out_button);
        userProfilePicture = (ImageView) findViewById(R.id.profile_picture_holder);
        userNameView = (TextView) findViewById(R.id.user_name_view);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == SIGN_IN_CODE) {
            handleSignInResult(Auth.GoogleSignInApi.getSignInResultFromIntent(data));
        }
    }

    private void handleSignInResult(GoogleSignInResult result) {
        if (result.isSuccess()) {
            GoogleSignInAccount acct = result.getSignInAccount();
            userProfilePictureUri = acct.getPhotoUrl();
            userDisplayName = acct.getDisplayName();
            updateUI(true);
        } else {
            Toast.makeText(this, "Could not Sign In", Toast.LENGTH_SHORT).show();
        }
    }

    private void signIn() {
        Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
        startActivityForResult(signInIntent, SIGN_IN_CODE);
    }

    private void signOut() {
        Auth.GoogleSignInApi.signOut(mGoogleApiClient).setResultCallback(
                new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        updateUI(false);
                    }
                });
    }

    private void checkSilentSignIn() {
        OptionalPendingResult<GoogleSignInResult> opr = Auth.GoogleSignInApi.silentSignIn(mGoogleApiClient);
        if (opr.isDone()) {
            handleSignInResult(opr.get());
        } else {
            updateUI(false);
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Toast.makeText(this, "Connection Failed", Toast.LENGTH_SHORT).show();
    }
}