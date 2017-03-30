package com.shuai.followme2.activity;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.LoaderManager.LoaderCallbacks;
import android.app.ProgressDialog;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;

import com.shuai.followme2.R;
import com.shuai.followme2.bean.KeyObject;
import com.shuai.followme2.bean.MyCustomApplication;
import com.shuai.followme2.util.Utils;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.util.ArrayList;
import java.util.List;

import info.guardianproject.netcipher.proxy.OrbotHelper;

/**
 * A login screen that offers login via email/password.
 */
public class LoginActivity extends AppCompatActivity implements LoaderCallbacks<Cursor> {
    private Activity loginActivity = this;
    /**
     * Id to identity READ_CONTACTS permission request.
     */
    private static final int REQUEST_READ_CONTACTS = 0;
    /**
     * Id to identity READ_CONTACTS permission request.
     */
    private static final int REQUEST_ACCESS_FINE_LOCATION = 1;
    /**
     * Keep track of the login task to ensure we can cancel it if requested.
     */
    private UserLoginTask mAuthTask = null;


    // UI references.
    private AutoCompleteTextView usernameView;
    private EditText mPasswordView;
    private View mProgressView;
    private View mLoginFormView;

    CookieManager cookieManager = null;

    public CookieManager getCookieManager() {
        return cookieManager;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        cookieManager = new CookieManager();
        CookieHandler.setDefault(cookieManager);


        setContentView(R.layout.activity_login);
        // Set up the login form.
        usernameView = (AutoCompleteTextView) findViewById(R.id.email);
        if (ActivityCompat.checkSelfPermission(loginActivity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(loginActivity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_ACCESS_FINE_LOCATION);
        }

        mPasswordView = (EditText) findViewById(R.id.password);

        Button signInButton = (Button) findViewById(R.id.sign_in_button);
        signInButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                attemptLoginRegister(true);
            }
        });

        Button registerButton = (Button) findViewById(R.id.register_button);
        registerButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                attemptLoginRegister(false);
            }
        });

        mLoginFormView = findViewById(R.id.login_form);
        mProgressView = findViewById(R.id.login_progress);
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        final OrbotHelper orbotHelper = ((MyCustomApplication) getApplication()).getOrbotHelper();
        if(!orbotHelper.isInstalled()){
            Log.i(Utils.APP_LABEL,"Tor is not installed!");
            this.loginActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    AlertDialog.Builder builder = new AlertDialog.Builder(findViewById(R.id.usrnamepwd_login_form).getContext());
                    builder.setMessage(R.string.msg_installOrbot);
                    builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setData(Uri.parse("https://guardianproject.info/apps/orbot/"));
                            startActivity(intent);
                            dialog.dismiss();
                        }
                    });
                    builder.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();
                        }
                    });
                    final AlertDialog dialog = builder.create();
                    dialog.show();
                }
            });
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        OrbotHelper orbotHelper = ((MyCustomApplication) getApplication()).getOrbotHelper();
        orbotHelper.init();
        if (orbotHelper.isInstalled()) {
            Log.i(Utils.APP_LABEL, "Orbot is installed!");
            Utils.isTorEnabled = true;
        }
    }

    /**
     * Callback received when a permissions request has been completed.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_ACCESS_FINE_LOCATION:
                if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // do nothing
                    Log.i(Utils.APP_LABEL, "ACCESS_FINE_LOCATION granted!");
                }
        }
    }


    /**
     * Attempts to sign in or register the account specified by the login form.
     * If there are form errors (invalid email, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    private void attemptLoginRegister(boolean isLogin) {
        if (mAuthTask != null) {
            return;
        }

        // Reset errors.
        usernameView.setError(null);
        mPasswordView.setError(null);

        // Store values at the time of the login attempt.
        String username = usernameView.getText().toString();
        String password = mPasswordView.getText().toString();

        boolean cancel = false;
        View focusView = null;

        // Check for a valid password, if the user entered one.
        if (!TextUtils.isEmpty(password) && !isPasswordValid(password)) {
            mPasswordView.setError(getString(R.string.error_invalid_password));
            focusView = mPasswordView;
            cancel = true;
        }

        // Check for a valid username address.
        if (TextUtils.isEmpty(username)) {
            usernameView.setError(getString(R.string.error_field_required));
            focusView = usernameView;
            cancel = true;
        }

        if (cancel) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            focusView.requestFocus();
        } else {
            // Show a progress spinner, and kick off a background task to
            // perform the user login attempt.
            showProgress(true);
            mAuthTask = new UserLoginTask(username, password, isLogin);
            mAuthTask.execute((Void) null);

        }
    }

    private boolean isPasswordValid(String password) {
        //TODO: Replace this with your own logic
        return password.length() > 4;
    }

    /**
     * Shows the progress UI and hides the login form.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    private void showProgress(final boolean show) {
        // On Honeycomb MR2 we have the ViewPropertyAnimator APIs, which allow
        // for very easy animations. If available, use these APIs to fade-in
        // the progress spinner.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

            mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
            mLoginFormView.animate().setDuration(shortAnimTime).alpha(
                    show ? 0 : 1).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
                }
            });

            mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            mProgressView.animate().setDuration(shortAnimTime).alpha(
                    show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
                }
            });
        } else {
            // The ViewPropertyAnimator APIs are not available, so simply show
            // and hide the relevant UI components.
            mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return new CursorLoader(this,
                // Retrieve data rows for the device user's 'profile' contact.
                Uri.withAppendedPath(ContactsContract.Profile.CONTENT_URI,
                        ContactsContract.Contacts.Data.CONTENT_DIRECTORY), ProfileQuery.PROJECTION,

                // Select only email addresses.
                ContactsContract.Contacts.Data.MIMETYPE +
                        " = ?", new String[]{ContactsContract.CommonDataKinds.Email
                .CONTENT_ITEM_TYPE},

                // Show primary email addresses first. Note that there won't be
                // a primary email address if the user hasn't specified one.
                ContactsContract.Contacts.Data.IS_PRIMARY + " DESC");
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        List<String> emails = new ArrayList<>();
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            emails.add(cursor.getString(ProfileQuery.ADDRESS));
            cursor.moveToNext();
        }

        addEmailsToAutoComplete(emails);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {

    }

    private void addEmailsToAutoComplete(List<String> emailAddressCollection) {
        //Create adapter to tell the AutoCompleteTextView what to show in its dropdown list.
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(LoginActivity.this,
                        android.R.layout.simple_dropdown_item_1line, emailAddressCollection);

        usernameView.setAdapter(adapter);
    }


    private interface ProfileQuery {
        String[] PROJECTION = {
                ContactsContract.CommonDataKinds.Email.ADDRESS,
                ContactsContract.CommonDataKinds.Email.IS_PRIMARY,
        };

        int ADDRESS = 0;
        int IS_PRIMARY = 1;
    }

    /**
     * Represents an asynchronous login/registration task used to authenticate
     * the user.
     */
    public class UserLoginTask extends AsyncTask<Void, Integer, Boolean> {

        private final String username;
        private final String mPassword;
        private final boolean isLogin;
        ProgressDialog mProgressDialog = new ProgressDialog(findViewById(R.id.usrnamepwd_login_form).getContext());

        UserLoginTask(String username, String password, boolean isLogin) {
            ;
            this.username = username;
            this.mPassword = password;
            this.isLogin = isLogin;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            // TODO: attempt authentication against a network service.
            List<NameValuePair> nameValuePair = new ArrayList<>();
            nameValuePair.add(new BasicNameValuePair("username", username));
            nameValuePair.add(new BasicNameValuePair("userpass", mPassword));

            byte[] responseByteArray = Utils.sendHTTPSWithNameValuePair(Utils.SERVER_DOMAIN + (isLogin ? "/login" : "/register"), nameValuePair, cookieManager, loginActivity);

            String responseStr = new String(responseByteArray);

            if (StringUtils.isNotEmpty(responseStr) && responseStr.toLowerCase().contains("success")) {
                publishProgress(50);
                // DH-EKE key exchange
                // Creating HTTP client
//                try {
//                    keyObject = new KeyObject(mPassword);
//                    byte[] httpResponseByteArr = Utils.sendByteArrAsFileViaHTTP(keyObject.generateKeyExchangeMsg(), cookieManager, Utils.SERVER_DOMAIN + "/key", "keyUpload", loginActivity);
//                    keyObject.parseKeyExchangeMsg(httpResponseByteArr);
//                } catch (Exception e) {
//                    e.printStackTrace();
//                    return false;
//                }
                // finish
                publishProgress(100);
                return true;
            }

            return false;
        }

        protected void onPreExecute() {
            super.onPreExecute();
            mProgressDialog.setMessage("Login/Registration in progress, please wait...");
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.setCancelable(false);
            mProgressDialog.show();
        }

        protected void onProgressUpdate(Integer... progress) {
            mProgressDialog.setProgress(progress[0]);
        }

        @Override
        protected void onPostExecute(final Boolean success) {
            mAuthTask = null;
            showProgress(false);
            if (success) {
                Intent intent = new Intent(loginActivity, MapsActivity.class);
                MyCustomApplication appObject = (MyCustomApplication) getApplication();
                appObject.setCookieManager(cookieManager);
                loginActivity.startActivity(intent);
            } else {
                usernameView.setError((isLogin ? getString(R.string.error_incorrect_password) : getString(R.string.error_registration_failed)));
                usernameView.requestFocus();
            }

            if (mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }
        }

        @Override
        protected void onCancelled() {
            mAuthTask = null;
            showProgress(false);
        }
    }
}

