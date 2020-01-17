package ai.picovoice.hotword;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.speech.SpeechRecognizer;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.File;

import ai.picovoice.porcupinemanager.PorcupineManager;
import ai.picovoice.porcupinemanager.PorcupineManagerException;

import pl.sviete.dom.AisCoreUtils;
import pl.sviete.dom.AisRecognitionListener;
import pl.sviete.dom.BrowserActivityNative;
import pl.sviete.dom.Config;
import pl.sviete.dom.R;

public class PorcupineService extends Service {


    private int numKeywordsDetected;
    private final String TAG = PorcupineService.class.getName();

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(
                    AisCoreUtils.AIS_DOM_CHANNEL_ID,
                    "AI-Speaker Mic",
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
        //
        Config config = new Config(this.getApplicationContext());
        String hotword = config.getSelectedHotWord();
        Notification notification = new NotificationCompat.Builder(this, AisCoreUtils.AIS_DOM_CHANNEL_ID)
                .setContentTitle("AI-Speaker")
                .setContentText(getString(R.string.hotword_selected_word_info) + hotword.substring(0, 1).toUpperCase() + hotword.substring(1))
                .setSmallIcon(R.drawable.ic_ais_logo)
                .setContentIntent(pendingIntent)
                .build();

        startForeground(AisCoreUtils.AIS_DOM_NOTIFICATION_ID, notification);

        // Brodcast
        IntentFilter filter = new IntentFilter();
        filter.addAction(AisCoreUtils.BROADCAST_ON_END_SPEECH_TO_TEXT);
        filter.addAction(AisCoreUtils.BROADCAST_ON_START_SPEECH_TO_TEXT);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(this);
        bm.registerReceiver(mBroadcastReceiver, filter);

        startHotWordListening(intent);

        return super.onStartCommand(intent, flags, startId);
    }

    private void startHotWordListening(Intent intent) {
        String modelFilePath = new File(this.getFilesDir(), "porcupine_params.pv").getAbsolutePath();

        Config config = new Config(this.getApplicationContext());
        String hotword = config.getSelectedHotWord();
        int hotWordSensitivity = config.getSelectedHotWordSensitivity();
        String keywordFileName = hotword + ".ppn";
        String keywordFilePath = new File(this.getFilesDir(), keywordFileName).getAbsolutePath();


        try {
            if (AisCoreUtils.mPorcupineManager == null) {

                AisCoreUtils.mPorcupineManager = new PorcupineManager(
                        modelFilePath,
                        keywordFilePath,
                (float) hotWordSensitivity / 100,
                        (keywordIndex) -> {
                            numKeywordsDetected++;
                            Log.i(TAG, "numKeywordsDetected " + numKeywordsDetected);
                            startTheSpeechToText();
                        });
            }
            AisCoreUtils.mPorcupineManager.start();
        } catch (PorcupineManagerException e) {
            Log.e("PORCUPINE_SERVICE", e.toString());
        }
    }

    private void stopHotWordListening(){
        if (AisCoreUtils.mPorcupineManager != null) {
            Log.i(TAG, "onDestroy PorcupineService");
            try {
                AisCoreUtils.mPorcupineManager.stop();
                AisCoreUtils.mPorcupineManager = null;
            } catch (PorcupineManagerException e) {
                Log.e("PORCUPINE_SERVICE", e.toString());
            }
        }
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(AisCoreUtils.BROADCAST_ON_END_SPEECH_TO_TEXT)) {
                startHotWordListening(intent);
            }
            if (action.equals(AisCoreUtils.BROADCAST_ON_START_SPEECH_TO_TEXT)){
                stopHotWordListening();
                if (!AisCoreUtils.mSpeechIsRecording) {
                    stopHotWordListening();
                    if (AisCoreUtils.mSpeech == null) {
                        AisCoreUtils.mSpeech = SpeechRecognizer.createSpeechRecognizer(context);
                        AisRecognitionListener listener = new AisRecognitionListener(context, AisCoreUtils.mSpeech);
                        AisCoreUtils.mSpeech.setRecognitionListener(listener);
                    }
                    AisCoreUtils.mSpeech.startListening(AisCoreUtils.mRecognizerIntent);
                }
            }
        }
    };


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopHotWordListening();
        super.onDestroy();
    }

    private void startTheSpeechToText(){
        // start mic
        Intent SttIntent = new Intent(AisCoreUtils.BROADCAST_ON_START_SPEECH_TO_TEXT);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
        bm.sendBroadcast(SttIntent);
    }

}
