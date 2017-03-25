package com.shuai.followme2.activity;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.widget.CheckBox;
import android.widget.EditText;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.shuai.followme2.R;
import com.shuai.followme2.bean.GpsTransfer;
import com.shuai.followme2.bean.KeyObject;
import com.shuai.followme2.bean.MyCustomApplication;
import com.shuai.followme2.util.Utils;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpContext;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.shuai.followme2.util.Utils.APP_LABEL;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {
    private Activity mapsActivity = this;
    private GoogleMap mMap;

    private CheckBox follow;
    private EditText followID;
    private EditText followSecrect;
    private ScheduledFuture<?> fetchGPSwithID;
    private final AtomicBoolean isTargetOnline = new AtomicBoolean(true);

    private CheckBox followMe;
    private EditText followPwd;
    private ScheduledFuture<?> pushGPS;
    private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    private Location lastkLocation;

    SupportMapFragment mFragment;
    private Marker currLocationMarker;
    private Marker targetLocationMarker;
    private KeyObject keyObject;
    private java.net.CookieManager cookieManager;

    @Override
    protected void onStart() {
        super.onStart();
        // Connect the client.
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        // Disconnecting the client invalidates it.
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        try {
            // check permission
            if (ActivityCompat.checkSelfPermission(mapsActivity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // don't have permission
                AlertDialog.Builder builder = new AlertDialog.Builder(findViewById(R.id.map).getContext());
                builder.setMessage(R.string.msg_noPermission);
                builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                        mapsActivity.startActivity(new Intent(mapsActivity, LoginActivity.class));
                    }
                });
                final AlertDialog dialog = builder.create();
                dialog.show();
                // Hide after 10 seconds
                final Handler handler = new Handler();
                final Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        if (dialog.isShowing()) {
                            dialog.dismiss();
                        }
                        mapsActivity.startActivity(new Intent(mapsActivity, LoginActivity.class));
                    }
                };

                dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        handler.removeCallbacks(runnable);
                    }
                });
                handler.postDelayed(runnable, 3000); // auto-close alert dialog after 3 seconds
            } else {
                mLocationRequest = LocationRequest.create();
                mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
                mLocationRequest.setInterval(5000); // Update location every 5 seconds
                LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
                lastkLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);

                if (lastkLocation != null) {
                    //place marker at current position
                    //mGoogleMap.clear();
                    LatLng latLng = new LatLng(lastkLocation.getLatitude(), lastkLocation.getLongitude());
                    if (currLocationMarker != null) {
                        currLocationMarker.remove();
                    }
                    MarkerOptions markerOptions = new MarkerOptions();
                    markerOptions.position(latLng);
                    markerOptions.title("Current Position");
                    markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
                    if (mMap != null) {
                        currLocationMarker = mMap.addMarker(markerOptions);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(APP_LABEL, "exception", e);
            e.printStackTrace();
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onLocationChanged(Location location) {
        this.lastkLocation = location;

        //place marker at current position
        //mGoogleMap.clear();
        if (currLocationMarker != null) {
            currLocationMarker.remove();
        }
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(latLng);
        markerOptions.title("Current Position");
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
        if (mMap != null) {
            currLocationMarker = mMap.addMarker(markerOptions);
        }
        //zoom to current position:
        //mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng,11));

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            MyCustomApplication appObject = (MyCustomApplication) getApplication();
            this.keyObject = appObject.getKeyObject();
            this.cookieManager = appObject.getCookieManager();
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(LocationServices.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();

            setContentView(R.layout.activity_maps);
            // Obtain the SupportMapFragment and get notified when the map is ready to be used.
            SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.map);
            mapFragment.getMapAsync(this);
        } catch (Exception e) {
            Log.e(APP_LABEL, "exception", e);
            e.printStackTrace();
        }
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        try {
            mMap = googleMap;
            mFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
            mFragment.getMapAsync(this);

            // push local GPS to server
            followPwd = (EditText) findViewById(R.id.follow_pwd);
            followMe = (CheckBox) findViewById(R.id.follow_me);
            followMe.setOnClickListener(new View.OnClickListener() {
                @Override
                public synchronized void onClick(View view) {
                    if (followMe.isChecked()) {
                        followPwd.setFocusable(false);

                        // fetch GPS every second
                        pushGPS = scheduler.scheduleAtFixedRate(new Runnable() {
                            @Override
                            public void run() {
                                // todo add scheduled pull task and draw map trace
                                try {
                                    // generate GPS transfer object
                                    GpsTransfer gpsTransfer = new GpsTransfer(lastkLocation);
                                    // encrypt GPS with shared secrect between clients
                                    String pushPwd = followPwd.getText().toString();
                                    byte[] encryptedGps = Utils.encryptJsonObject(gpsTransfer, pushPwd.getBytes());
                                    // encrypt GPS again with session key between clients and web server
                                    encryptedGps = Utils.encryptJsonObject(encryptedGps, keyObject.getSecretKey());

                                    byte[] rsp = Utils.sendByteArrAsFileViaHTTP(encryptedGps,cookieManager , Utils.SERVER_DOMAIN + "/push", false);
                                } catch (Exception e) {
                                    Log.e(APP_LABEL, "exception", e);
                                    e.printStackTrace();
                                }
                            }
                        }, 0, 5, TimeUnit.SECONDS);
                    } else {
                        // todo remove scheduled push task
                        // stop scheduled push task
                        if (pushGPS != null) {
                            pushGPS.cancel(false);
                        }
                        followPwd.setFocusableInTouchMode(true);
                        followPwd.setFocusable(true);
                    }
                }
            });


            // fetch GPS from server with ID
            followSecrect = (EditText) findViewById(R.id.follow_secrect);
            followID = (EditText) findViewById(R.id.follow_id);
            follow = (CheckBox) findViewById(R.id.follow);
            follow.setOnClickListener(new View.OnClickListener() {
                @Override
                public synchronized void onClick(View view) {
                    if (follow.isChecked()) {
                        followSecrect.setFocusable(false);
                        followID.setFocusable(false);
                        // fetch GPS every second
                        fetchGPSwithID = scheduler.scheduleAtFixedRate(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    // todo add scheduled pull task and draw map trace
                                    String fetchId = followID.getText().toString();
                                    String secrect = followSecrect.getText().toString();

                                    List<NameValuePair> nameValuePair = new ArrayList<>();
                                    nameValuePair.add(new BasicNameValuePair("fetchId", fetchId));

//                                    HttpResponse response = Utils.sendHTTPSWithNameValuePair(Utils.SERVER_DOMAIN + "/pull", nameValuePair, httpContext);
//                                    HttpEntity entity = response.getEntity();
//                                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
//                                    entity.writeTo(baos);
//                                    byte[] GPSCipher = baos.toByteArray();

                                    byte[] GPSCipher = Utils.sendHTTPSWithNameValuePair(Utils.SERVER_DOMAIN + "/pull", nameValuePair, cookieManager);

                                    // decrypt using session key between client and web server
                                    GPSCipher = ArrayUtils.toPrimitive(Utils.decryptJsonObject(GPSCipher, keyObject.getSecretKey(), Byte[].class));
                                    // decrypt using shared secrect between clients
                                    GpsTransfer gpsTransfer = Utils.decryptJsonObject(GPSCipher, secrect.getBytes(), GpsTransfer.class);
                                    if (gpsTransfer != null) {
                                        if (!isTargetOnline.get()) {
                                            mapsActivity.runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    AlertDialog.Builder builder = new AlertDialog.Builder(findViewById(R.id.map).getContext());
                                                    builder.setMessage(R.string.msg_pullSucceeded);
                                                    builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                                        public void onClick(DialogInterface dialog, int id) {
                                                            dialog.dismiss();
                                                        }
                                                    });
                                                    final AlertDialog dialog = builder.create();
                                                    dialog.show();
                                                    // Hide after 10 seconds
                                                    final Handler handler = new Handler();
                                                    final Runnable runnable = new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            if (dialog.isShowing()) {
                                                                dialog.dismiss();
                                                            }
                                                        }
                                                    };

                                                    dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                                                        @Override
                                                        public void onDismiss(DialogInterface dialog) {
                                                            handler.removeCallbacks(runnable);
                                                        }
                                                    });
                                                    handler.postDelayed(runnable, 10000); // auto-close alert dialog after 10 seconds
                                                }
                                            });
                                        }

                                        //place marker at target position
                                        final LatLng latLng = new LatLng(gpsTransfer.getLat(), gpsTransfer.getLng());
                                        final MarkerOptions markerOptions = new MarkerOptions();
                                        markerOptions.position(latLng);
                                        markerOptions.title("Target Position");
                                        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ROSE));
                                        mapsActivity.runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                // first marker or back online
                                                if (targetLocationMarker == null || !isTargetOnline.get()) {
                                                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, mMap.getCameraPosition().zoom));
                                                    isTargetOnline.set(true); // mark target online
                                                }
                                                if (targetLocationMarker != null) {
                                                    targetLocationMarker.remove();
                                                    // draw target moving trace
                                                    mMap.addPolyline(new PolylineOptions()
                                                            .add(targetLocationMarker.getPosition(), latLng)
                                                            .width(5)
                                                            .color(Color.RED));
                                                }
                                                targetLocationMarker = mMap.addMarker(markerOptions);
                                            }
                                        });
                                    } else {
                                        if (isTargetOnline.get()) {
                                            isTargetOnline.set(false); // mark target offline
                                            mapsActivity.runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    AlertDialog.Builder builder = new AlertDialog.Builder(findViewById(R.id.map).getContext());
                                                    builder.setMessage(R.string.msg_pullFailed);
                                                    builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                                        public void onClick(DialogInterface dialog, int id) {
                                                            // if user is offline, do nothing and clean edittext
                                                            dialog.dismiss();
                                                        }
                                                    });
                                                    final AlertDialog dialog = builder.create();
                                                    dialog.show();
                                                    // Hide after 10 seconds
                                                    final Handler handler = new Handler();
                                                    final Runnable runnable = new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            if (dialog.isShowing()) {
                                                                dialog.dismiss();
                                                            }
                                                        }
                                                    };

                                                    dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                                                        @Override
                                                        public void onDismiss(DialogInterface dialog) {
                                                            handler.removeCallbacks(runnable);
                                                        }
                                                    });
                                                    handler.postDelayed(runnable, 10000); // auto-close alert dialog after 10 seconds
                                                }
                                            });
                                        }
                                    }
                                } catch (Exception e) {
                                    Log.e(APP_LABEL, "exception", e);
                                    e.printStackTrace();
                                }
                            }
                        }, 0, 5, TimeUnit.SECONDS);
                    } else {
                        isTargetOnline.set(true);
                        targetLocationMarker = null;
                        // stop scheduled pull task
                        followSecrect.setFocusableInTouchMode(true);
                        followSecrect.setFocusable(true);
                        followID.setFocusableInTouchMode(true);
                        followID.setFocusable(true);
                        if (fetchGPSwithID != null) {
                            fetchGPSwithID.cancel(false);
                        }
                        mapsActivity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mMap.clear();
                                MarkerOptions markerOptions = new MarkerOptions();
                                markerOptions.position(currLocationMarker.getPosition());
                                markerOptions.title("Current Position");
                                markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
                                currLocationMarker = mMap.addMarker(markerOptions);
                            }
                        });
                    }
                }
            });
        } catch (Exception e) {
            Log.e(APP_LABEL, "exception", e);
            e.printStackTrace();
        }
    }


}
