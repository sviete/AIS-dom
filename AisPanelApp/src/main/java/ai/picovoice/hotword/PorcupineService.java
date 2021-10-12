package ai.picovoice.hotword;

import static pl.sviete.dom.AisCoreUtils.BROADCAST_ON_END_TEXT_TO_SPEECH;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_ON_START_TEXT_TO_SPEECH;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_SAY_IT_TEXT;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_SERVICE_SAY_IT;

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
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.Locale;

import ai.picovoice.porcupine.Porcupine;
import ai.picovoice.porcupine.PorcupineException;
import ai.picovoice.porcupine.PorcupineManager;
import pl.sviete.dom.AisCoreUtils;
import pl.sviete.dom.AisPanelService;
import pl.sviete.dom.AisRecognitionListener;
import pl.sviete.dom.BrowserActivityNative;
import pl.sviete.dom.Config;
import pl.sviete.dom.R;

public class PorcupineService extends Service implements TextToSpeech.OnInitListener{


    private int numKeywordsDetected;
    private final String TAG = PorcupineService.class.getName();
    private TextToSpeech mTts;
    private static final String CHANNEL_ID = "HotWordServiceChannel";

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(
                    CHANNEL_ID,
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
        String hotword = config.getSelectedHotWordName();
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

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(subText)
                .setContentText("Rozpoznano : " + numKeywordsDetected)
                .setSmallIcon(R.drawable.ais_icon_mic)
                .setContentIntent(pendingIntent)
                .addAction(exitAction)
                .build();

        startForeground(9997, notification);
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

        // tts not on box
        if (!AisCoreUtils.onBox()) {
            createTTS();
        }
        //

        return super.onStartCommand(intent, flags, startId);
    }

    private void startHotWordListening() {
        Config config = new Config(this.getApplicationContext());
        String hotword = config.getSelectedHotWord();
        int hotWordSensitivity = config.getSelectedHotWordSensitivity();
        Porcupine.BuiltInKeyword selectedKeyword = Porcupine.BuiltInKeyword.ALEXA;
        if (hotword.equals("computer")) {
            selectedKeyword = Porcupine.BuiltInKeyword.COMPUTER;
        } else if (hotword.equals("hey_google")) {
            selectedKeyword = Porcupine.BuiltInKeyword.HEY_GOOGLE;
        } else if (hotword.equals("hey_siri")) {
            selectedKeyword = Porcupine.BuiltInKeyword.HEY_SIRI;
        } else if (hotword.equals("ok_google")) {
            selectedKeyword = Porcupine.BuiltInKeyword.OK_GOOGLE;
        }
        //
        if (AisCoreUtils.mPorcupineManager == null) {
            try {
                AisCoreUtils.mPorcupineManager = new PorcupineManager.Builder()
                        .setKeyword(selectedKeyword)
                        .setSensitivity((float) hotWordSensitivity / 100).build(
                                getApplicationContext(),
                                (keywordIndex) -> {
                                    startTheSpeechToText();
                                });
                AisCoreUtils.mPorcupineManager.start();
                // modelFilePath,
            }

            catch(PorcupineException e){
                Log.e("HWL PORCUPINE_SERVICE", e.toString());
            }
        }
    }

    private void stopHotWordListening(){
        if (AisCoreUtils.mPorcupineManager != null) {
            Log.i(TAG, "HWL onDestroy PorcupineService");
            try {
                AisCoreUtils.mPorcupineManager.stop();
                AisCoreUtils.mPorcupineManager.delete();
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
                    try {
                        AisCoreUtils.mSpeech.startListening(AisCoreUtils.mRecognizerIntent);
                    } catch (Exception e){
                        Log.e(TAG, e.getMessage());
                    }
                }

                // check if started after 20 seconds
                final Handler handler = new Handler();
                handler.postDelayed(() -> {
                    Log.d(TAG, "HWL check the status 3");
                    startHotWordListening();
                }, 20000);
            }

            else if (action.equals(AisCoreUtils.BROADCAST_SERVICE_SAY_IT)) {
                if ( AisCoreUtils.onBox()){
                    Log.d(TAG, BROADCAST_SERVICE_SAY_IT + " going to processTTS");
                    final String txtMessage = intent.getStringExtra(BROADCAST_SAY_IT_TEXT);
                    processTTS(txtMessage);
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
        numKeywordsDetected ++;
        // start mic
        Intent SttIntent = new Intent(AisCoreUtils.BROADCAST_ON_END_HOT_WORD_LISTENING);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
        bm.sendBroadcast(SttIntent);

        //
        Intent exitIntent = new Intent(this, BrowserActivityNative.class);
        exitIntent.setAction("exit_mic_service");
        exitIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent exitPendingIntent = PendingIntent.getActivity(
                this,
                0,
                exitIntent,
                0);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                new Intent(this, BrowserActivityNative.class),
                0);
        NotificationCompat.Action exitAction = new NotificationCompat.Action.Builder(R.drawable.ic_app_exit, "STOP", exitPendingIntent).build();
        Config config = new Config(this.getApplicationContext());
        String hotword = config.getSelectedHotWordName();
        int sensitivity = config.getSelectedHotWordSensitivity();
        String subText = hotword + " " + sensitivity;
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(subText)
                .setContentText("Rozpoznano : " + numKeywordsDetected)
                .setSmallIcon(R.drawable.ais_icon_mic)
                .setContentIntent(pendingIntent)
                .addAction(exitAction)
                .build();

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert notificationManager != null;
        notificationManager.notify(9997, notification);
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


        mTts.setLanguage(new Locale("pl_PL"));


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
