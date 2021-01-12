package pl.sviete.dom;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.text.Html;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static pl.sviete.dom.AisCoreUtils.GO_TO_HA_APP_VIEW_INTENT_EXTRA;


public class AisFuseLocationService extends Service{
    private static final String TAG = "AisFuseLocationService";

    private static final int UPDATE_INTERVAL_IN_MILLISECONDS = 30000; // 30 sec
    private static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = UPDATE_INTERVAL_IN_MILLISECONDS / 2;
    private static final float LOCATION_CHANGE_IN_DISTANCE_TO_NOTIFY = 10f;
    private static final int LOCATION_ACCURACY_SUITABLE_TO_REPORT = 30;

    // sync work
    private static final String TAG_SYNC_DATA = "AIS_SYNC_WITH_GATE";
    public static final String SYNC_DATA_WORK_NAME = "AIS_SYNC_WITH_GATE_WORK_NAME";


    private Handler mHandler;
    private Context mContext;

    // fuse
    private FusedLocationProviderClient mFusedLocationClient;
    private LocationCallback mLocationCallback;
    private Location mCurrentLocation;
    private String mCurrentAddress;
    private LocationRequest mLocationRequest;
    //
    private BroadcastReceiver mBroadcastReceiver;

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(
                    AisCoreUtils.AIS_LOCATION_CHANNEL_ID,
                    "AI-Speaker Location",
                    NotificationManager.IMPORTANCE_LOW
            );
            notificationChannel.setShowBadge(true);

            NotificationManager manager = getSystemService(NotificationManager.class);
            assert manager != null;
            manager.createNotificationChannel(notificationChannel);
        }
    }

    private Notification getNotification() {

        // check if wifi is connected
        ConnectivityManager connectivityManager = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
        int activeNetworkType = -1;
        if (null != activeNetwork) {
            activeNetworkType = activeNetwork.getType();
        }
        // no connection - try to report location very often
        if (activeNetworkType == -1) {
            mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS * 10);
        }
        else if (activeNetworkType == ConnectivityManager.TYPE_WIFI) {
            // wifi connection  - report location less often
            mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS * 5);
        } else if (activeNetworkType == ConnectivityManager.TYPE_MOBILE) {
            // mobile connection - we are moving - update location more often
            mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
        }
        // Go to frame
        Intent goToAppView = new Intent(getApplicationContext(), BrowserActivityNative.class);
        int iUniqueId = (int) (System.currentTimeMillis() & 0xfffffff);
        goToAppView.putExtra(GO_TO_HA_APP_VIEW_INTENT_EXTRA, "/map");
        goToAppView.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), iUniqueId, goToAppView, PendingIntent.FLAG_UPDATE_CURRENT);

        // Report action button - button to report location now
        Intent reportIntent = new Intent(getApplicationContext(), AisFuseLocationService.class);
        reportIntent.putExtra("getLastKnownLocation", true);
        PendingIntent reportPendingIntent = PendingIntent.getService(getApplicationContext(), 0, reportIntent, 0);
        NotificationCompat.Action reportAction = new NotificationCompat.Action.Builder(R.drawable.ic_ais_sync_logo, getString(R.string.report_button), reportPendingIntent).build();

        // Exit action
        Intent exitIntent = new Intent(this, BrowserActivityNative.class);
        exitIntent.setAction("exit_location_service");
        exitIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent exitPendingIntent = PendingIntent.getActivity(this, 0, exitIntent, 0);
        NotificationCompat.Action exitAction = new NotificationCompat.Action.Builder(R.drawable.ic_app_exit, "STOP", exitPendingIntent).build();

        CharSequence contentTitle = getString(R.string.gps_loc_detected) + ": " + AisCoreUtils.GPS_SERVICE_LOCATIONS_DETECTED;
        contentTitle = contentTitle + ", " + getString(R.string.gps_loc_sent) + ": " + AisCoreUtils.GPS_SERVICE_LOCATIONS_SENT;
        ;
        CharSequence contentText = getString(R.string.app_name);
        mCurrentAddress = mCurrentAddress == null ? "" : mCurrentAddress;
        if (mCurrentLocation != null) {
            if (mCurrentLocation.hasAccuracy() && mCurrentLocation.getAccuracy() <= LOCATION_ACCURACY_SUITABLE_TO_REPORT) {
                contentText = Html.fromHtml(
                        ((mCurrentAddress == "") ? "" : "<b>" + mCurrentAddress + "</b> "  + "  ")
                                + ((mCurrentAddress == "") ? "<b>[" + mCurrentLocation.getLatitude() + ", " + mCurrentLocation.getLongitude() + "]</b> " : " ")
                                + "(" + getString(R.string.txt_accuracy_ok) + " " + AisCoreUtils.getDistanceDisplay(getApplicationContext(), mCurrentLocation.getAccuracy(), true) + ")"
                );
            } else {
                contentText = Html.fromHtml(
                        ((mCurrentAddress == "") ? "" : "<b>" + mCurrentAddress + "</b> "  + "  ")
                                + ((mCurrentAddress == "") ? "<b>[" + mCurrentLocation.getLatitude() + ", " + mCurrentLocation.getLongitude() + "]</b> " : " ")
                                + "(<span style='color:red'>" + getString(R.string.txt_accuracy_nok) + " " + AisCoreUtils.getDistanceDisplay(getApplicationContext(), mCurrentLocation.getAccuracy(), true) + "</span>)"
                );
            }
        }



        Bitmap bImage = BitmapFactory.decodeResource(getResources(), ((activeNetworkType == ConnectivityManager.TYPE_WIFI) ? R.drawable.gps_wifi_on : (activeNetworkType == ConnectivityManager.TYPE_MOBILE) ? R.drawable.gps_mobile_on :  R.drawable.gps_no_network));
        contentTitle = Html.fromHtml(contentTitle + ", " + ((activeNetworkType == ConnectivityManager.TYPE_WIFI) ? "WIFI": (activeNetworkType == ConnectivityManager.TYPE_MOBILE) ? "MOBILE": "<span style='text-decoration: line-through;'>CONNECTION</span>"));

        Notification notification = new NotificationCompat.Builder(getApplicationContext(), AisCoreUtils.AIS_LOCATION_CHANNEL_ID)
                .setContentTitle(contentTitle)
                .setSmallIcon(R.drawable.ic_ais_sync_logo)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setLargeIcon(bImage)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(contentText).setBigContentTitle(contentTitle))
                .setColor(getResources().getColor(R.color.black))
                .setContentIntent(pendingIntent)
                .addAction(reportAction)
                .addAction(exitAction)
                .build();
        return notification;
    }

    @Override
    public IBinder onBind(Intent arg0) {
        Log.d(TAG, "onBind");
        return null;
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        // restart...
        try {
            stopLocationUpdates();
        } catch (Exception ex){
            Log.e(TAG, ex.getMessage());
        }

        createNotificationChannel();
        Notification notification = getNotification();
        startForeground(AisCoreUtils.AIS_LOCATION_NOTIFICATION_ID, notification);

        // fuse restart
        startLocationUpdates();

        // TODO update rest of the sensors
        DomWebInterface.updateBatteryState(getApplicationContext());
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateNotification();
            }
        }, 2000);

        // Return START_STICKY so we can ensure that if the
        // service dies for some reason, it should start back.
        return Service.START_STICKY;
    }


    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        //
        mHandler = new Handler(); // this is attached to the main thread and the main looper
        mContext = this.getApplicationContext();

        initializeFuseLocationManager();
        createLocationCallback();
        createLocationRequest();
        buildLocationSettingsRequest();

        //
        createWifiBrodcastReceiver();

        //
        createSyncDataWithGate();
    }

    private void cancelSyncDataWithGate() {
        try {
            AisCoreUtils.GATE_SYNC_WORK_MANAGER.cancelAllWork();
            AisCoreUtils.GATE_SYNC_WORK_MANAGER = null;
        } catch (Exception ex) {
            Log.e(TAG, ex.getMessage());
        }
    }

    private void createSyncDataWithGate() {
        Log.i(TAG, "createSyncDataWithGate AisSyncGateWorker");
        if (AisCoreUtils.GATE_SYNC_WORK_MANAGER == null) {
            Log.i(TAG, "createSyncDataWithGate AisSyncGateWorker GATE_SYNC_WORK_MANAGER is null");
            AisCoreUtils.GATE_SYNC_WORK_MANAGER = WorkManager.getInstance(getApplication());
            // Create Network constraint
            Constraints constraints = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
            PeriodicWorkRequest periodicSyncDataWork = new PeriodicWorkRequest.Builder(
                    AisSyncGateWorker.class, 15, TimeUnit.MINUTES).setConstraints(constraints).addTag(TAG_SYNC_DATA).build();
            AisCoreUtils.GATE_SYNC_WORK_MANAGER.enqueueUniquePeriodicWork(SYNC_DATA_WORK_NAME, ExistingPeriodicWorkPolicy.REPLACE, periodicSyncDataWork);
        }
    }

    private void createWifiBrodcastReceiver(){
        //
        // handler for received data from service
        mBroadcastReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                if (action.equals(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION)) {
                    // report location
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            reportLocationToAisGate();
                        }
                    }, 3500);
                }
                else if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                    // update notification
                    updateNotification();
                }
            }
        };
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(mBroadcastReceiver, intentFilter);
    }

    private void buildLocationSettingsRequest() {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(mLocationRequest);
    }

    private void createLocationRequest() {
        mLocationRequest = new LocationRequest();

        // Sets the desired interval for active location updates. This interval is
        // inexact. You may not receive updates at all if no location sources are available, or
        // you may receive them slower than requested. You may also receive updates faster than
        // requested if other applications are requesting location at a faster interval.
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);

        // Sets the fastest rate for active location updates. This interval is exact, and your
        // application will never receive updates faster than this value.
        mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);

        // Request the most precise location possible
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        // No location updates if the device does not move or cross that distance.
        mLocationRequest.setSmallestDisplacement(LOCATION_CHANGE_IN_DISTANCE_TO_NOTIFY);

        //
    }

    // update notification
    private void updateNotification() {
        Notification notification = getNotification();
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert notificationManager != null;
        notificationManager.notify(AisCoreUtils.AIS_LOCATION_NOTIFICATION_ID, notification);
    }


    // send location info to gate and show in notification
    private void reportLocationToAisGate() {
        //
        updateNotification();

        // report location to AIS gate only if it's precise 30m
        try {
            if (mCurrentLocation.hasAccuracy() && mCurrentLocation.getAccuracy() <= LOCATION_ACCURACY_SUITABLE_TO_REPORT) {
                DomWebInterface.updateDeviceLocation(getApplicationContext(), mCurrentLocation);

                // update notification after 2.5 sec - to check if the location report was sent
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        updateNotification();
                    }
                }, 2500);
                // update notification after 10 sec
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        updateNotification();
                    }
                }, 10000);
            }
        }
        catch (Exception ex) {
                // oppo report in Google Play java.lang.NullPointerException:
                Log.e(TAG, "reportLocationToAisGate error", ex);
        }
    }

    /**
     * Creates a callback for receiving location events.
     */
    private void createLocationCallback() {
        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                // This is your most accurate location.
                mCurrentLocation = locationResult.getLastLocation();
                reportLocationToAisGate();

                // get the address from location
                getAddressFromLocation(mCurrentLocation, getApplicationContext(), new GeocoderHandler());
            }
        };
    }

    /**
     * Requests location updates from the FusedLocationApi. Note: we don't call this unless location
     * runtime permission has been granted.
     */
    private void startLocationUpdates() {
        // Begin by checking if the device has the necessary location settings.
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.getMainLooper());
    }

    /**
     * Removes location updates from the FusedLocationApi.
     */
    private void stopLocationUpdates() {
        // It is a good practice to remove location requests when the activity is in a paused or
        // stopped state. Doing so helps battery performance and is especially
        // recommended in applications that request frequent location updates.
        try {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
        } catch (Exception ex) {
            Log.e(TAG, "fail to stopLocationUpdates", ex);
        }
    }


    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        // fuse
        stopLocationUpdates();
        //
        unregisterReceiver(mBroadcastReceiver);

        // reset counters
        AisCoreUtils.GPS_SERVICE_LOCATIONS_DETECTED = 0;
        AisCoreUtils.GPS_SERVICE_LOCATIONS_SENT = 0;

        //
        cancelSyncDataWithGate();

        //
        super.onDestroy();
    }

    private void initializeFuseLocationManager() {
        Log.d(TAG, "initializeFuseLocationManager");
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
    }


    // get the Geocoder answer without blocking
    public static void getAddressFromLocation(
            final Location location, final Context context, final Handler handler) {
        Thread thread = new Thread() {
            @Override public void run() {
                Geocoder geocoder = new Geocoder(context, Locale.getDefault());
                String result = null;
                try {
                    List<Address> list = geocoder.getFromLocation(
                            location.getLatitude(), location.getLongitude(), 1);
                    if (list != null && list.size() > 0) {
                        Address address = list.get(0);

                        result = address.getAddressLine(0);
                        if (address.getMaxAddressLineIndex() > 0) {
                            for (int i = 0; i < address.getMaxAddressLineIndex(); i++) {
                                result = result + " " + address.getAddressLine(i);
                            }
                        } else {
                                // sending back first address line
                                result = address.getAddressLine(0);
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Impossible to connect to Geocoder", e);
                } finally {
                    Message msg = Message.obtain();
                    msg.setTarget(handler);
                    if (result != null) {
                        msg.what = 1;
                        Bundle bundle = new Bundle();
                        bundle.putString("address", result);
                        msg.setData(bundle);
                    } else
                        msg.what = 0;
                    msg.sendToTarget();
                }
            }
        };
        thread.start();
    }

    // handler to show the address in the notification
    private class GeocoderHandler extends Handler {
        @Override
        public void handleMessage(Message message) {
            String result;
            switch (message.what) {
                case 1:
                    Bundle bundle = message.getData();
                    mCurrentAddress = bundle.getString("address");
                    DomWebInterface.updateDeviceAddress(getApplicationContext(), mCurrentAddress);
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            updateNotification();
                        }
                    }, 2000);
                    break;
                default:
                    mCurrentAddress = "";
            }
        }
    }

}
