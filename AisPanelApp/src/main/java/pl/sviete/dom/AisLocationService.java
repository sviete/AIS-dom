package pl.sviete.dom;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Html;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

public class AisLocationService extends Service {
    private static final String TAG = "AisLocationService";

    private LocationManager mLocationManager = null;
    private static final int LOCATION_INTERVAL = 60000;
    private static final float LOCATION_DISTANCE = 10f;
    Location mLastLocation = null;

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

    private Notification getNotification(){
        // Go to frame
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(),0,
                new Intent(getApplicationContext(), BrowserActivityNative.class),0);


        // Report action button
        // TODO

        // Exit action
        Intent exitIntent = new Intent(getApplicationContext(), BrowserActivityNative.class);
        exitIntent.setAction("exit_location_service");
        exitIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent exitPendingIntent = PendingIntent.getActivity(getApplicationContext(),0, exitIntent,0);
        NotificationCompat.Action exitAction = new NotificationCompat.Action.Builder(R.drawable.ic_app_exit, "STOP", exitPendingIntent).build();

        CharSequence contentTitle = getString(R.string.gps_loc_detected) + ": " + AisCoreUtils.GPS_SERVICE_LOCATIONS_DETECTED;

        CharSequence contentText = getString(R.string.app_name);
        if (mLastLocation != null) {
            contentText = Html.fromHtml(
                    "<b>[" + mLastLocation.getLatitude() + ", " + mLastLocation.getLongitude() + "]</b> "
                    + "<b>" + getString(R.string.txt_altitude) + "</b> " + AisCoreUtils.getDistanceDisplay(getApplicationContext(), mLastLocation.getAltitude(), false) + "  "
                    + "<b>" + getString(R.string.txt_accuracy) + "</b> " + AisCoreUtils.getDistanceDisplay(getApplicationContext(), mLastLocation.getAccuracy(), true) + "  "
                    + "<b>" + getString(R.string.txt_travel_duration) + "</b> " + AisCoreUtils.getDescriptiveDurationString((int) (System.currentTimeMillis() - Long.parseLong(AisCoreUtils.GPS_SERVICE_START_TIMESTAMP)) / 1000, this)
                    );
        }

        Notification notification = new NotificationCompat.Builder(getApplicationContext(), AisCoreUtils.AIS_LOCATION_CHANNEL_ID)
                .setContentTitle(contentTitle)
                .setSmallIcon(R.drawable.ic_ais_gps_logo)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.gps_satellite))
                //.setContentText(contentText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(contentText).setBigContentTitle(contentTitle))
                .setContentIntent(pendingIntent)
                .addAction(exitAction)
                .build();
        return notification;
    }

    private class LocationListener implements android.location.LocationListener {

        public LocationListener(String provider) {
            Log.d(TAG, "LocationListener " + provider);
            mLastLocation = new Location(provider);
        }

        @Override
        public void onLocationChanged(Location location) {
            Log.d(TAG, "onLocationChanged: " + location);
            mLastLocation.set(location);

            // update notification
            AisCoreUtils.GPS_SERVICE_LOCATIONS_DETECTED ++;
            Notification notification = getNotification();
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            assert notificationManager != null;
            notificationManager.notify(AisCoreUtils.AIS_LOCATION_NOTIFICATION_ID, notification);

            // report location to AIS gate
            DomWebInterface.updateDeviceLocation(getApplicationContext(), location);
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
            new LocationListener(LocationManager.PASSIVE_PROVIDER)
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

        AisCoreUtils.GPS_SERVICE_LOCATIONS_DETECTED = 0;

        Notification notification = getNotification();
        startForeground(AisCoreUtils.AIS_LOCATION_NOTIFICATION_ID, notification);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        //
        AisCoreUtils.GPS_SERVICE_START_TIMESTAMP = String.valueOf(System.currentTimeMillis());

        initializeLocationManager();

        try {
            mLocationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    LOCATION_INTERVAL,
                    LOCATION_DISTANCE,
                    mLocationListeners[0]
            );
        } catch (java.lang.SecurityException ex) {
            Log.i(TAG, "fail to request location update, ignore", ex);
        } catch (Exception ex) {
            Log.d(TAG, "network provider does not exist, " + ex.getMessage());
        }

        try {
            mLocationManager.requestLocationUpdates(
                    LocationManager.PASSIVE_PROVIDER,
                    LOCATION_INTERVAL,
                    LOCATION_DISTANCE,
                    mLocationListeners[1]
            );
        } catch (java.lang.SecurityException ex) {
            Log.i(TAG, "fail to request location update, ignore", ex);
        } catch (Exception ex) {
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
