package pl.sviete.dom;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import ai.picovoice.hotword.PorcupineService;

public class AisLocationService extends Service {
    private static final String TAG = "AisLocationService";

    private LocationManager mLocationManager = null;
    private static final int LOCATION_INTERVAL = 1000;
    private static final float LOCATION_DISTANCE = 10f;

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(
                    AisCoreUtils.AIS_DOM_CHANNEL_ID,
                    "AI-Speaker Location",
                    NotificationManager.IMPORTANCE_LOW);

            NotificationManager manager = getSystemService(NotificationManager.class);
            assert manager != null;
            manager.createNotificationChannel(notificationChannel);
        }
    }

    private class LocationListener implements android.location.LocationListener {
        Location mLastLocation;

        public LocationListener(String provider) {
            Log.d(TAG, "LocationListener " + provider);
            mLastLocation = new Location(provider);
        }

        @Override
        public void onLocationChanged(Location location) {
            Log.d(TAG, "onLocationChanged: " + location);
            mLastLocation.set(location);
            // report location to AIS gate
            
            //
        }

        @Override
        public void onProviderDisabled(String provider) {
            Log.d(TAG, "onProviderDisabled: " + provider);
        }

        @Override
        public void onProviderEnabled(String provider) {
            Log.d(TAG, "onProviderEnabled: " + provider);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            Log.d(TAG, "onStatusChanged: " + provider);
        }
    }

    LocationListener[] mLocationListeners = new LocationListener[]{
            new LocationListener(LocationManager.GPS_PROVIDER),
            new LocationListener(LocationManager.NETWORK_PROVIDER)
    };

    @Override
    public IBinder onBind(Intent arg0) {
        Log.d(TAG, "onBind");
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        createNotificationChannel();

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                new Intent(this, BrowserActivityNative.class),
                0);



        // Exit action
        Intent exitIntent = new Intent(this, BrowserActivityNative.class);
        exitIntent.setAction("exit_location_service");
        exitIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent exitPendingIntent = PendingIntent.getActivity(
                this,
                0,
                exitIntent,
                0);
        NotificationCompat.Action exitAction = new NotificationCompat.Action.Builder(R.drawable.ic_app_exit, "STOP", exitPendingIntent).build();

        String subText = "";
        Config config = new Config(getApplicationContext());
        if (config.getHotWordMode()) {
            String hotword = config.getSelectedHotWord();
            int sensitivity = config.getSelectedHotWordSensitivity();
            subText = hotword.substring(0, 1).toUpperCase() + hotword.substring(1) + " " + sensitivity;
        }

        subText = subText + getString(R.string.title_notification_report_location) ;


        Notification notification = new NotificationCompat.Builder(this, AisCoreUtils.AIS_DOM_CHANNEL_ID)
                .setContentTitle(subText)
                .setContentText("")
                .setSmallIcon(R.drawable.ic_ais_logo)
                .setContentIntent(pendingIntent)
                .addAction(exitAction)
                .build();

        startForeground(AisCoreUtils.AIS_DOM_NOTIFICATION_ID, notification);

        //
        if (AisCoreUtils.isServiceRunning(this.getApplicationContext(), AisPanelService.class)) {
            //
            LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());

            // try to stop and start ExoPlayer
            Intent palyIntent = new Intent(AisCoreUtils.BROADCAST_EXO_PLAYER_COMMAND);
            palyIntent.putExtra(AisCoreUtils.BROADCAST_EXO_PLAYER_COMMAND_TEXT, "play");
            bm.sendBroadcast(palyIntent);

            // try to stop and stop ExoPlayer
            Intent pauseIntent = new Intent(AisCoreUtils.BROADCAST_EXO_PLAYER_COMMAND);
            pauseIntent.putExtra(AisCoreUtils.BROADCAST_EXO_PLAYER_COMMAND_TEXT, "pause");
            bm.sendBroadcast(pauseIntent);
        }


        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCreate() {

        Log.d(TAG, "onCreate");

        initializeLocationManager();

        try {
            mLocationManager.requestLocationUpdates(
                    LocationManager.PASSIVE_PROVIDER,
                    LOCATION_INTERVAL,
                    LOCATION_DISTANCE,
                    mLocationListeners[0]
            );
        } catch (java.lang.SecurityException ex) {
            Log.i(TAG, "fail to request location update, ignore", ex);
        } catch (IllegalArgumentException ex) {
            Log.d(TAG, "network provider does not exist, " + ex.getMessage());
        }

        try {
            mLocationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    LOCATION_INTERVAL,
                    LOCATION_DISTANCE,
                    mLocationListeners[1]
            );
        } catch (java.lang.SecurityException ex) {
            Log.i(TAG, "fail to request location update, ignore", ex);
        } catch (IllegalArgumentException ex) {
            Log.d(TAG, "gps provider does not exist " + ex.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
        if (mLocationManager != null) {
            for (int i = 0; i < mLocationListeners.length; i++) {
                try {
                    if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                    mLocationManager.removeUpdates(mLocationListeners[i]);
                } catch (Exception ex) {
                    Log.i(TAG, "fail to remove location listener, ignore", ex);
                }
            }
        }
    }

    private void initializeLocationManager() {
        Log.d(TAG, "initializeLocationManager - LOCATION_INTERVAL: "+ LOCATION_INTERVAL + " LOCATION_DISTANCE: " + LOCATION_DISTANCE);
        if (mLocationManager == null) {
            mLocationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        }
    }
}
