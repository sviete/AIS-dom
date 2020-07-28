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
import android.os.Handler;
import android.os.IBinder;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
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
import pl.sviete.dom.Config;
import pl.sviete.dom.R;

import static pl.sviete.dom.AisCoreUtils.BROADCAST_ON_END_TEXT_TO_SPEECH;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_ON_START_TEXT_TO_SPEECH;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_SAY_IT_TEXT;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_SERVICE_SAY_IT;

public class PorcupineService extends Service implements TextToSpeech.OnInitListener{


    private int numKeywordsDetected;
    private final String TAG = PorcupineService.class.getName();
    private TextToSpeech mTts;

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(
                    AisCoreUtils.AIS_DOM_CHANNEL_ID,
                    "AI-Speaker Mic",
                    NotificationManager.IMPORTANCE_LOW);

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
        hotword = hotword.substring(0, 1).toUpperCase() + hotword.substring(1);
        int sensitivity = config.getSelectedHotWordSensitivity();


        // Exit action
        Intent exitIntent = new Intent(this, BrowserActivityNative.class);
        exitIntent.setAction("exit_mic_service");
        exitIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent exitPendingIntent = PendingIntent.getActivity(
                this,
                0,
                exitIntent,
                0);
        NotificationCompat.Action exitAction = new NotificationCompat.Action.Builder(R.drawable.ic_app_exit, "STOP", exitPendingIntent).build();
        String subText = hotword + " " + sensitivity;

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

        // Brodcast
        IntentFilter filter = new IntentFilter();
        filter.addAction(AisCoreUtils.BROADCAST_ON_START_HOT_WORD_LISTENING);
        filter.addAction(AisCoreUtils.BROADCAST_ON_END_HOT_WORD_LISTENING);
        filter.addAction(AisCoreUtils.BROADCAST_SERVICE_SAY_IT);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(this);
        bm.registerReceiver(mBroadcastReceiver, filter);

        startHotWordListening();

        createTTS();
        //

        return super.onStartCommand(intent, flags, startId);
    }

    private void startHotWordListening() {
        String modelFilePath = new File(this.getFilesDir(), "porcupine_params.pv").getAbsolutePath();

        Config config = new Config(this.getApplicationContext());
        String hotword = config.getSelectedHotWord();
        int hotWordSensitivity = config.getSelectedHotWordSensitivity();
        String keywordFileName = hotword + ".ppn";
        String keywordFilePath = new File(this.getFilesDir(), keywordFileName).getAbsolutePath();

        if (AisCoreUtils.mPorcupineManager == null) {
            try {
                AisCoreUtils.mPorcupineManager = new PorcupineManager(
                        modelFilePath,
                        keywordFilePath,
                        (float) hotWordSensitivity / 100,
                        (keywordIndex) -> {
                            startTheSpeechToText();
                        });
                AisCoreUtils.mPorcupineManager.start();
            }

            catch(PorcupineManagerException e){
                Log.e("HWL PORCUPINE_SERVICE", e.toString());
            }
        }
    }

    private void stopHotWordListening(){
        if (AisCoreUtils.mPorcupineManager != null) {
            Log.i(TAG, "HWL onDestroy PorcupineService");
            try {
                AisCoreUtils.mPorcupineManager.stop();
                AisCoreUtils.mPorcupineManager = null;
            } catch (Exception e) {
                Log.e("HWL PORCUPINE_SERVICE", e.toString());
            }
        }
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(AisCoreUtils.BROADCAST_ON_START_HOT_WORD_LISTENING)) {
                Log.d(TAG, "HWL startHotWordListening");
                startHotWordListening();
                // check if started after 3 seconds
                final Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(TAG, "HWL check the status 1");
                        stopHotWordListening();
                    }
                }, 2500);
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        //ask about current media after 6sec...
                        Log.d(TAG, "HWL check the status 2");
                        startHotWordListening();
                    }
                }, 6000);
            }
            else if (action.equals(AisCoreUtils.BROADCAST_ON_END_HOT_WORD_LISTENING)){
                Log.d(TAG, "HWL stopHotWordListening");
                stopHotWordListening();
                if (!AisCoreUtils.mSpeechIsRecording) {
                    //stopHotWordListening();
                    if (AisCoreUtils.mSpeech == null) {
                        AisCoreUtils.mSpeech = SpeechRecognizer.createSpeechRecognizer(context);
                        AisRecognitionListener listener = new AisRecognitionListener(context, AisCoreUtils.mSpeech);
                        AisCoreUtils.mSpeech.setRecognitionListener(listener);
                    }
                    stopTextToSpeech();
                    AisCoreUtils.mSpeech.startListening(AisCoreUtils.mRecognizerIntent);
                }

                // check if started after 20 seconds
                final Handler handler = new Handler();
                handler.postDelayed(() -> {
                    Log.d(TAG, "HWL check the status 3");
                    startHotWordListening();
                }, 20000);
            }

            else if (action.equals(AisCoreUtils.BROADCAST_SERVICE_SAY_IT)) {
                Log.d(TAG, BROADCAST_SERVICE_SAY_IT + " going to processTTS");
                final String txtMessage = intent.getStringExtra(BROADCAST_SAY_IT_TEXT);
                processTTS(txtMessage);
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
        //
        try {
            LocalBroadcastManager bm = LocalBroadcastManager.getInstance(this);
            bm.unregisterReceiver(mBroadcastReceiver);
        } catch (Exception e){
            Log.e(TAG, "unregisterReceiver " + e.toString());
        }

        stopHotWordListening();
        if (mTts != null) {
            mTts.stop();
            mTts.shutdown();
            mTts = null;
        }
        super.onDestroy();
    }

    private void startTheSpeechToText(){
        // start mic
        Intent SttIntent = new Intent(AisCoreUtils.BROADCAST_ON_END_HOT_WORD_LISTENING);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
        bm.sendBroadcast(SttIntent);
    }


    // TTS
    public void stopTextToSpeech(){
        Log.i(TAG, "Speech started, stoping the tts");
        try {
            mTts.stop();
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    public void createTTS() {
        Log.i(TAG, "starting TTS initialization");
        mTts = new TextToSpeech(this, this);
        mTts.setSpeechRate(1.0f);
    }
    private boolean processTTS(String text) {
        Log.d(TAG, "processTTS Called: " + text);

        if(!AisCoreUtils.shouldIsayThis(text, "service_hot_word")){
            return true;
        }

        // stop current TTS
        stopTextToSpeech();


        String voice = "";
        float pitch = 1;
        float rate = 1;

        // speak failed: not bound to TTS engine
        if (mTts == null){
            Log.w(TAG, "mTts == null");
            try {
                createTTS();
                return true;
            }
            catch (Exception e) {
                Log.e(TAG, e.toString());
            }
        }

        // to get voice from config
        Config config = new Config(this.getApplicationContext());
        String ttsVoice = config.getAppTtsVoice();
        Voice voiceobj = new Voice(
                    ttsVoice, new Locale("pl_PL"),
                    Voice.QUALITY_HIGH,
                    Voice.LATENCY_NORMAL,
                    false,
                    null);
        mTts.setVoice(voiceobj);


        //textToSpeech can only cope with Strings with < 4000 characters
        if(text.length()  >= 4000) {
            text = text.substring(0, 3999);
        }
        mTts.speak(text, TextToSpeech.QUEUE_FLUSH, null,"123");

        Intent intent = new Intent(BROADCAST_ON_START_TEXT_TO_SPEECH);
        intent.putExtra(AisCoreUtils.TTS_TEXT, text);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
        bm.sendBroadcast(intent);

        return true;

    }

    @Override
    public void onInit(int status) {

        Log.d(TAG, "PorcupineService onInit");
        if (status != TextToSpeech.ERROR) {
            int result = mTts.setLanguage(new Locale("pl_PL"));
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Language is not available.");
            }

            if(result == TextToSpeech.SUCCESS) {
                mTts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onDone(String utteranceId) {
                        Log.d(TAG, "TTS finished");
                        Intent intent = new Intent(BROADCAST_ON_END_TEXT_TO_SPEECH);
                        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
                        bm.sendBroadcast(intent);
                    }

                    @Override
                    public void onError(String utteranceId) {
                        Log.d(TAG, "TTS onError");
                        Intent intent = new Intent(BROADCAST_ON_END_TEXT_TO_SPEECH);
                        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
                        bm.sendBroadcast(intent);
                    }

                    @Override
                    public void onStart(String utteranceId) {
                        Log.d(TAG, "TTS onStart");
                    }
                });
            };
        } else {
            Log.e(TAG, "Could not initialize TextToSpeech.");
        }

    }
}
