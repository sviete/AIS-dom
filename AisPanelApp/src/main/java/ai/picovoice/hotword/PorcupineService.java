package ai.picovoice.hotword;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.File;
import java.util.Locale;

import ai.picovoice.porcupinemanager.PorcupineManager;
import ai.picovoice.porcupinemanager.PorcupineManagerException;

import pl.sviete.dom.AisCoreUtils;
import pl.sviete.dom.AisPanelService;
import pl.sviete.dom.AisRecognitionListener;
import pl.sviete.dom.BrowserActivityNative;
import pl.sviete.dom.R;

public class PorcupineService extends Service {
    private static final String CHANNEL_ID = "PorcupineServiceChannel";

    private int numKeywordsDetected;
    private final String TAG = PorcupineService.class.getName();

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "PorcupineServiceChannel",
                    NotificationManager.IMPORTANCE_HIGH);

            NotificationManager manager = getSystemService(NotificationManager.class);
            assert manager != null;
            manager.createNotificationChannel(notificationChannel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                new Intent(this, BrowserActivityNative.class),
                0);

        numKeywordsDetected = 0;

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("AIS hotword")
                .setContentText("ilość wywołań : " + numKeywordsDetected)
                .setSmallIcon(R.drawable.ic_launcher_hot_word)
                .setContentIntent(pendingIntent)
                .build();

        startForeground(1234, notification);

        String modelFilePath = new File(this.getFilesDir(), "porcupine_params.pv").getAbsolutePath();

        String keywordFileName = intent.getStringExtra("keywordFileName");
        assert keywordFileName != null;
        String keywordFilePath = new File(this.getFilesDir(), keywordFileName).getAbsolutePath();

        try {
            if (AisCoreUtils.mPorcupineManager == null) {
                AisCoreUtils.mPorcupineManager = new PorcupineManager(
                        modelFilePath,
                        keywordFilePath,
                        0.9f,
                        (keywordIndex) -> {
                            numKeywordsDetected++;

                            CharSequence title = "Jolka";
                            PendingIntent contentIntent = PendingIntent.getActivity(
                                    this,
                                    0,
                                    new Intent(this, BrowserActivityNative.class),
                                    0);

                            Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                                    .setContentTitle(title)
                                    .setContentText("Nasłuchiwanie słowa: " )
                                    .setSmallIcon(R.drawable.ic_ais_logo)
                                    .setContentIntent(contentIntent)
                                    .build();
                            //
                            //try {
                            //    AisCoreUtils.mPorcupineManager.stop();
                            //    AisCoreUtils.mPorcupineManager = null;
                            //} catch (PorcupineManagerException e) {
                            //    Log.e("PORCUPINE_SERVICE", e.toString());
                            //}

                            // start mic
                            //Intent SttIntent = new Intent(AisCoreUtils.BROADCAST_ON_START_SPEECH_TO_TEXT);
                            //LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
                            //bm.sendBroadcast(SttIntent);

                            //
                            //NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                            //assert notificationManager != null;
                            //notificationManager.notify(1234, n);
                            Log.i(TAG, "numKeywordsDetected " + numKeywordsDetected);
                        });
            }
            AisCoreUtils.mPorcupineManager.start();
        } catch (PorcupineManagerException e) {
            Log.e("PORCUPINE_SERVICE", e.toString());
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (AisCoreUtils.mPorcupineManager != null) {
            Log.i(TAG, "onDestroy PorcupineService");
            try {
                AisCoreUtils.mPorcupineManager.stop();
                AisCoreUtils.mPorcupineManager = null;
            } catch (PorcupineManagerException e) {
                Log.e("PORCUPINE_SERVICE", e.toString());
            }
        }

        super.onDestroy();
    }
}
