package pl.sviete.dom;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.net.sip.SipAudioCall;
import android.net.sip.SipErrorCode;
import android.net.sip.SipException;
import android.net.sip.SipManager;
import android.net.sip.SipProfile;
import android.net.sip.SipRegistrationListener;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;


import android.util.Log;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;

import com.koushikdutta.async.http.body.JSONObjectBody;
import com.koushikdutta.async.http.server.AsyncHttpServer;


import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.net.URL;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;


import pl.sviete.dom.data.DomCustomRequest;
import pl.sviete.dom.sip.IncomingCallReceiver;

import static pl.sviete.dom.AisCoreUtils.AIS_DOM_CHANNEL_ID;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_CAST_COMMAND;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_EXO_PLAYER_COMMAND;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_ON_END_SPEECH_TO_TEXT_MOB;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_ON_END_TEXT_TO_SPEECH;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_ON_START_SPEECH_TO_TEXT_MOB;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_ON_START_TEXT_TO_SPEECH;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_SERVICE_SAY_IT;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_SAY_IT_TEXT;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_SIP_COMMAND;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_SIP_INCOMING_CALL;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_SIP_STATUS;
import static pl.sviete.dom.AisCoreUtils.GO_TO_HA_APP_VIEW_INTENT_EXTRA;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_CAMERA_COMMAND;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_CAMERA_COMMAND_URL;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_CAMERA_HA_ID;
import static pl.sviete.dom.AisCoreUtils.mAisSipStatus;


public class AisPanelService extends Service implements TextToSpeech.OnInitListener, ExoPlayer.EventListener {
    public static final String BROADCAST_EVENT_DO_STOP_TTS = "BROADCAST_EVENT_DO_STOP_TTS";
    public static final String BROADCAST_EVENT_CHANGE_CONTROLLER_MODE = "BROADCAST_EVENT_CHANGE_CONTROLLER_MODE";
    public static final String  BROADCAST_ON_AIS_REQUEST = "BROADCAST_ON_AIS_REQUEST";

    //
    private final String TAG = AisPanelService.class.getName();
    private static Config mConfig;

    private PowerManager.WakeLock partialWakeLock;
    private WifiManager.WifiLock wifiLock;

    private AsyncHttpServer mHttpServer;
    private final IBinder mBinder = new AisPanelServiceBinder();
    private TextToSpeech mTts;


    // cast
    public static SimpleExoPlayer mCastExoPlayer;
    public static String mCastStreamUrl;
    public static String m_cast_media_title = null;
    public static String m_cast_media_source = AisCoreUtils.mAudioSourceAndroid;
    public static String m_cast_media_stream_image = null;
    public static String m_cast_media_album_name = null;
    // Create Handler for main thread (can be reused).
    //Handler mMainThreadHandler = new Handler(Looper.getMainLooper());

    //
    Notification mAisServiceNotification;
    NotifyServiceReceiver aisNotifyServiceReceiver;
    public static String m_ais_media_title = "AI-Speaker";
    public static String m_ais_media_source = AisCoreUtils.mAudioSourceAndroid;
    public static String m_ais_media_stream_image_url = "ais";
    public static Bitmap m_ais_media_bitmap_image;
    public static String m_ais_media_album_name = " ";
    public PendingIntent mGoToAisPendingIntent;
    public PendingIntent mPendingIntentAisStop;
    public PendingIntent mPendingIntentAisNext;
    public PendingIntent mPendingIntentAisPause;
    public PendingIntent mPendingIntentAisPrev;
    public PendingIntent mPendingIntentAisMic;

    //
    public static SipManager mAisSipManager = null;
    public static SipProfile mAisSipProfile = null;
    public static SipAudioCall mAisSipIncomingCall = null;
    public IncomingCallReceiver mAisIncomingCallReceiver;



    private Bitmap getSpeakerImage(){
        Bitmap largeIcon = null;
        try {
            largeIcon = BitmapFactory.decodeStream(getAssets().open("speaker.jpg"));
        } catch (Exception e) {
            Log.e(TAG, "largeIcon: " + e.toString());
        }
        return largeIcon;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(
                    AIS_DOM_CHANNEL_ID,
                    "AI-Speaker Cast",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            assert manager != null;
            manager.createNotificationChannel(notificationChannel);
        }
    }

    private void onResponse(JSONObject response) {
        try {
            setAudioInfo(response);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    //ExoPlayer - end


    //    Speech To Text
    public class AisPanelServiceBinder extends Binder {
        AisPanelService getService() {
            return AisPanelService.this;
        }
    }

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
        mTts = new TextToSpeech(this,this);
        mTts.setSpeechRate(1.0f);
    }
     // ----------------------
    // --- AIS SIP START  ----
    // -----------------------
    public void initializeSipManager() {

        if (mAisIncomingCallReceiver != null) {
            try {
                this.unregisterReceiver(mAisIncomingCallReceiver);
            } catch (Exception e){
                Log.e(TAG, "Exception " + e.toString());
            }
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(BROADCAST_SIP_INCOMING_CALL);

        mAisIncomingCallReceiver = new IncomingCallReceiver();
        this.registerReceiver(mAisIncomingCallReceiver, filter);

        if(mAisSipManager == null) {
            mAisSipManager = SipManager.newInstance(this);
        }
        initializeLocalSipProfile();
    }

    public void destroySip() {
        if (mAisSipIncomingCall != null) {
            mAisSipIncomingCall.close();
        }

        closeLocalSipProfile();

        if (mAisIncomingCallReceiver != null) {
            try {
                this.unregisterReceiver(mAisIncomingCallReceiver);
            } catch (Exception e){
                Log.e(TAG, "Exception " + e.toString());
            }
        }
    }

    /**
     * Closes out your local profile, freeing associated objects into memory
     * and unregistering your device from the server.
     */
    public void closeLocalSipProfile() {
        if (mAisSipManager == null) {
            return;
        }
        try {
            if (mAisSipProfile != null) {
                mAisSipManager.close(mAisSipProfile.getUriString());
            }
        } catch (Exception ee) {
            Log.d(TAG, "Failed to close local profile.", ee);
        }
    }

    /**
     * Updates the status box at the top of the UI with a message of your choice.
     * @param status The String to display in the status box.
     */
    public void updateAisSipStatus(final String status) {
        mAisSipStatus = status;
        Intent intent = new Intent(BROADCAST_SIP_STATUS);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
        bm.sendBroadcast(intent);
    }


    /**
     * Logs you into your SIP provider, registering this device as the location to
     * send SIP calls to for your SIP address.
     */
    public void initializeLocalSipProfile() {
        if (mAisSipManager == null) {
            return;
        }

        if (mAisSipProfile != null) {
            closeLocalSipProfile();
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String username = prefs.getString("setting_local_sip_client_name", "");
        String domain = prefs.getString("setting_local_gate_ip", "");
        String password = prefs.getString("setting_local_sip_client_password", "");
        if (username.equals("ais_auto")) {
            username = mConfig.getAppLocalGateIp();
        }

        if (domain.equals("ais_auto")) {
            domain = mConfig.getAppLocalGateIp();
        }

        if (username.length() == 0 || domain.length() == 0 || password.length() == 0) {
            // showDialog(UPDATE_SETTINGS_DIALOG);
            return;
        }

        try {
            SipProfile.Builder builder = new SipProfile.Builder(username, domain);
            builder.setAutoRegistration(true);
            builder.setSendKeepAlive(true);
            builder.setPort(5060);
            builder.setPassword(password);
            builder.setAuthUserName(username);
            mAisSipProfile = builder.build();

            Intent i = new Intent();
            i.setAction(BROADCAST_SIP_INCOMING_CALL);
            SystemClock.sleep(1000);

            PendingIntent pi = PendingIntent.getBroadcast(this, 0, i, Intent.FILL_IN_DATA);
            mAisSipManager.open(mAisSipProfile, pi, null);


            // This listener must be added AFTER manager.open is called,
            // Otherwise the methods aren't guaranteed to fire.
            String sipUri = mAisSipProfile.getUriString();
            mAisSipManager.setRegistrationListener(sipUri, getSipRegistrationListener());
        } catch (ParseException pe) {
            updateAisSipStatus("Connection Error.");
        } catch (SipException se) {
            updateAisSipStatus("Connection error.");
        }
    }

    private SipRegistrationListener getSipRegistrationListener() {
        return new SipRegistrationListener() {

            public void onRegistering(String localProfileUri) {
                updateAisSipStatus("Registering with SIP Server...");
            }

            public void onRegistrationDone(String localProfileUri, long expiryTime) {
                updateAisSipStatus("Ready");
            }

            public void onRegistrationFailed(String localProfileUri, int errorCode, String errorMessage) {
                updateAisSipStatus("Registration failed for " + localProfileUri + " [Error " + errorCode + ": " + SipErrorCode.toString(errorCode) + "] " + errorMessage);
            }
        };
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        // only sip change return
        if (!intent.hasExtra(BROADCAST_SIP_COMMAND)) {

            createNotificationChannel();


            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction("ais_stop");
            intentFilter.addAction("ais_next");
            intentFilter.addAction("ais_play_pause");
            intentFilter.addAction("ais_prev");
            intentFilter.addAction("ais_mic");
            registerReceiver(aisNotifyServiceReceiver, intentFilter);


            mAisServiceNotification = new NotificationCompat.Builder(this, AIS_DOM_CHANNEL_ID)
                    .setContentTitle("AIS dom")
                    // Show controls on lock screen even when user hides sensitive content.
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    // Add media control buttons that invoke intents in your media service
                    .addAction(R.drawable.ic_app_exit, "Stop", mPendingIntentAisStop) // #0
                    .addAction(R.drawable.exo_icon_previous, "Previous", mPendingIntentAisPrev) // #1
                    .addAction(R.drawable.ic_app_play_pause, "Pause", mPendingIntentAisPause)  // #2
                    .addAction(R.drawable.exo_icon_next, "Next", mPendingIntentAisNext)     // #3
                    .addAction(R.drawable.ais_icon_mic, "Mic", mPendingIntentAisMic)     // #4
                    .setStyle(new androidx.media.app.NotificationCompat.MediaStyle())
                    .setContentText("AI-Speaker")
                    .setSmallIcon(R.drawable.ic_ais_logo)
                    .setLargeIcon(m_ais_media_bitmap_image)
                    .setContentIntent(mGoToAisPendingIntent)
                    .setSound(null)
                    .build();

            startForeground(AisCoreUtils.AIS_DOM_NOTIFICATION_ID, mAisServiceNotification);

            if (mConfig.getAppDiscoveryMode()) {
                // player auto discovery on gate
                JSONObject json;
                json = getDeviceInfo();
                DomWebInterface.publishJson(json, "player_auto_discovery", getApplicationContext());
                Log.i(TAG, "player_auto_discovery");
            }
        }


        // SIP
        if (mConfig.getDoorbellMode()) {
            // enable sip
            initializeSipManager();
        } else {
            // disable sip
            destroySip();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    public Bitmap getCurrentAisLargeIcon() {
        Thread thread = new Thread(() -> {
            try {
                if (m_ais_media_stream_image_url.equals("ais")) {
                    m_ais_media_bitmap_image = getSpeakerImage();
                } else {
                    URL url = new URL(m_ais_media_stream_image_url);
                    m_ais_media_bitmap_image = BitmapFactory.decodeStream(url.openConnection().getInputStream());
                    refreshAisNotification();
                }
            } catch (Exception e) {
                m_ais_media_bitmap_image = getSpeakerImage();
            }
        });
        thread.start();
        return getSpeakerImage();
    }


    @Override
    public void onInit(int status) {
        Log.d(TAG, "AisPanelService onInit");
        if (status != TextToSpeech.ERROR) {
            int result = mTts.setLanguage(new Locale("pl_PL"));
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Language is not available.");
                Toast.makeText(getApplicationContext(), "TTS język polski nie jest obsługiwany",Toast.LENGTH_SHORT).show();
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
            }
        } else {
            Log.e(TAG, "Could not initialize TextToSpeech.");
        }

    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate Called");

        aisNotifyServiceReceiver = new NotifyServiceReceiver();

        mConfig = new Config(getApplicationContext());

        // prepare the lock types we may use
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        partialWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dom:partialWakeLock");
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, "dom:wifiLock");


        IntentFilter filter = new IntentFilter();
        filter.addAction(BROADCAST_EVENT_DO_STOP_TTS);
        filter.addAction(BROADCAST_ON_END_SPEECH_TO_TEXT_MOB);
        filter.addAction(BROADCAST_ON_START_SPEECH_TO_TEXT_MOB);
        filter.addAction(BROADCAST_ON_END_TEXT_TO_SPEECH);
        filter.addAction(BROADCAST_ON_START_TEXT_TO_SPEECH);
        filter.addAction(BROADCAST_EVENT_CHANGE_CONTROLLER_MODE);
        filter.addAction((BROADCAST_EXO_PLAYER_COMMAND));
        filter.addAction((BROADCAST_CAST_COMMAND));
        filter.addAction(BROADCAST_SERVICE_SAY_IT);
        filter.addAction(BROADCAST_ON_AIS_REQUEST);
        filter.addAction(BROADCAST_CAMERA_COMMAND);

        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(this);
        bm.registerReceiver(mBroadcastReceiver, filter);

        // http api server
        configureHttp();

        //
        createTTS();
        //

        // notification
        m_ais_media_bitmap_image = getSpeakerImage();
        // Go to frame
        Intent goToAppView = new Intent(getApplicationContext(), BrowserActivityNative.class);
        int iUniqueId = (int) (System.currentTimeMillis() & 0xfffffff);
        goToAppView.putExtra(GO_TO_HA_APP_VIEW_INTENT_EXTRA, "/aisaudio");
        goToAppView.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        mGoToAisPendingIntent = PendingIntent.getActivity(getApplicationContext(), iUniqueId, goToAppView, PendingIntent.FLAG_UPDATE_CURRENT);
        // notification buttons
        Intent intentAisStop = new Intent("ais_stop").setPackage(AisPanelService.this.getPackageName());
        mPendingIntentAisStop = PendingIntent.getBroadcast(AisPanelService.this, 1, intentAisStop, PendingIntent.FLAG_UPDATE_CURRENT);
        Intent intentAisNext = new Intent("ais_next").setPackage(AisPanelService.this.getPackageName());
        mPendingIntentAisNext = PendingIntent.getBroadcast(AisPanelService.this, 1, intentAisNext, PendingIntent.FLAG_UPDATE_CURRENT);
        Intent intentAisPause = new Intent("ais_play_pause").setPackage(AisPanelService.this.getPackageName());
        mPendingIntentAisPause = PendingIntent.getBroadcast(AisPanelService.this, 1, intentAisPause, PendingIntent.FLAG_UPDATE_CURRENT);
        Intent intentAisPrev = new Intent("ais_prev").setPackage(AisPanelService.this.getPackageName());
        mPendingIntentAisPrev = PendingIntent.getBroadcast(AisPanelService.this, 1, intentAisPrev, PendingIntent.FLAG_UPDATE_CURRENT);
        Intent intentAisMic = new Intent("ais_mic").setPackage(AisPanelService.this.getPackageName());
        mPendingIntentAisMic = PendingIntent.getBroadcast(AisPanelService.this,1, intentAisMic, PendingIntent.FLAG_UPDATE_CURRENT);

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy Called");
        //
        try {
            LocalBroadcastManager bm = LocalBroadcastManager.getInstance(this);
            bm.unregisterReceiver(mBroadcastReceiver);
        } catch (Exception e){
            Log.e(TAG, "unregisterReceiver " + e.toString());
        }

        if (mTts != null) {
            mTts.stop();
            mTts.shutdown();
            mTts = null;
        }

        if (mHttpServer != null){
            try {
                mHttpServer.stop();
                mHttpServer = null;
            } catch (Exception e){
                Log.e(TAG, e.toString());
            }

        }

        if (partialWakeLock != null) {
            if (partialWakeLock.isHeld()) {
                partialWakeLock.release();
            }
        }
        if (wifiLock != null) {
            if (wifiLock.isHeld()) {
                wifiLock.release();
            }
        }
        try {
            this.unregisterReceiver(aisNotifyServiceReceiver);
        } catch (Exception e){
            Log.e(TAG, "Exception " + e.toString());
        }

        Log.i(TAG, "destroy");
    }


    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();

            switch (action) {
                case BROADCAST_EVENT_DO_STOP_TTS:
                    Log.d(TAG, "Speech started, stoping the tts");
                    stopTextToSpeech();
                    break;
                case BROADCAST_SERVICE_SAY_IT:
                    Log.d(TAG, BROADCAST_SERVICE_SAY_IT + " going to processTTS");
                    final String txtMessage = intent.getStringExtra(BROADCAST_SAY_IT_TEXT);
                    processTTS(txtMessage);
                    break;
                case BROADCAST_ON_START_SPEECH_TO_TEXT_MOB:
                    Log.d(TAG, BROADCAST_ON_START_SPEECH_TO_TEXT_MOB + " turnDownVolume");
                    break;
                case BROADCAST_ON_END_SPEECH_TO_TEXT_MOB:
                    Log.d(TAG, BROADCAST_ON_END_SPEECH_TO_TEXT_MOB + " turnUpVolume");
                    break;
                case BROADCAST_ON_START_TEXT_TO_SPEECH:
                    Log.d(TAG, BROADCAST_ON_START_TEXT_TO_SPEECH + " turnDownVolume");
                    break;
                case BROADCAST_ON_END_TEXT_TO_SPEECH:
                    Log.d(TAG, BROADCAST_ON_END_TEXT_TO_SPEECH + " turnUpVolume");
                    break;
                case BROADCAST_EXO_PLAYER_COMMAND: {
                    final String command = intent.getStringExtra(AisCoreUtils.BROADCAST_EXO_PLAYER_COMMAND_TEXT);
                    if (command != null) {
                        executeCastPlayerCommand(command);
                    }
                    break;
                }
                case BROADCAST_CAST_COMMAND: {
                    final String command = intent.getStringExtra(AisCoreUtils.BROADCAST_CAST_COMMAND_TEXT);
                    if (command != null) {
                        executeCastPlayerCommand(command);
                    }
                    break;
                }
                case (BROADCAST_CAMERA_COMMAND):
                    final String streamUrl = intent.getStringExtra(BROADCAST_CAMERA_COMMAND_URL);
                    final String haId = intent.getStringExtra(BROADCAST_CAMERA_HA_ID);
                    showCamView(streamUrl, haId);
                    break;
                case BROADCAST_ON_AIS_REQUEST:
                    Log.d(TAG, BROADCAST_ON_AIS_REQUEST);
                    if (intent.hasExtra("aisRequest")) {
                        String aisRequest = intent.getStringExtra("aisRequest");
                        switch (Objects.requireNonNull(aisRequest)) {
                            case "micOn":
                                onStartStt();
                                break;
                            case "micOff":
                                AisCoreUtils.mSpeech.stopListening();
                                break;
                            case "playAudio":
                                final String audioUrl = intent.getStringExtra("url");
                                playCastMedia(audioUrl);
                                break;
                            case "findPhone":
                                // set audio volume to 100
                                setVolume(100);
                                // play
                                playCastMedia("asset:///find_my_phone.mp3");
                                break;
                        }
                    }
                    break;
            }
        }
    };

    private void onStartStt() {
        try {
            AisCoreUtils.mRecognizerIntent = null;
            AisCoreUtils.mRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            AisCoreUtils.mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString());
            AisCoreUtils.mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            AisCoreUtils.mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, Long.valueOf(5000));
            AisCoreUtils.mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, "pl.sviete.dom");
            AisCoreUtils.mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

        } catch (Exception e){
            Log.e(TAG, "" + e.getMessage());
        }

        try {
            AisCoreUtils.mSpeech.cancel();
            AisCoreUtils.mSpeech.destroy();
            AisCoreUtils.mSpeech = null;
        } catch (Exception e){
            Log.e(TAG, "" + e.getMessage());
        }

        try {
            AisCoreUtils.mSpeech = SpeechRecognizer.createSpeechRecognizer(getApplicationContext());
            AisRecognitionListener listener = new AisRecognitionListener(getApplicationContext(), AisCoreUtils.mSpeech);
            AisCoreUtils.mSpeech.setRecognitionListener(listener);
        } catch (Exception e){
            Log.e(TAG, "" + e.getMessage());
        }


        AisCoreUtils.mSpeechIsRecording = true;
        stopTextToSpeech();
        AisCoreUtils.mSpeech.startListening(AisCoreUtils.mRecognizerIntent);
    }

    // ---------------------------------------
    //******** HTTP Related Functions
   // ----------------------------------------
    private JSONObject getDeviceInfo(){
        JSONObject json = new JSONObject();
        Context context = getApplicationContext();
        try {
            // the seme structure like in sonoff http://<device-ip>/cm?cmnd=status%205
            json.put("Hostname", AisNetUtils.getHostName());
            json.put("ais_gate_client_id", AisCoreUtils.AIS_GATE_ID);
            json.put("MacWlan0", AisNetUtils.getMACAddress("wlan0"));
            json.put("MacEth0", AisNetUtils.getMACAddress("eth0"));
            json.put("IPAddressIPv4", AisNetUtils.getIPAddress(true));
            json.put("IPAddressIPv6", AisNetUtils.getIPAddress(false));
            json.put("ApiLevel", AisNetUtils.getApiLevel());
            json.put("Device", AisNetUtils.getDevice());
            json.put("OsVersion", AisNetUtils.getOsVersion());
            json.put("Model", AisNetUtils.getModel());
            json.put("Product", AisNetUtils.getProduct());
            json.put("Manufacturer", AisNetUtils.getManufacturer());
            json.put("NetworkSpeed", AisNetUtils.getNetworkSpeed(context));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return json;
    }

    private void configureHttp(){
        mHttpServer = new AsyncHttpServer();

        mHttpServer.get("/", (request, response) -> {
            Log.d(TAG, "request: " + request);
            JSONObject json = new JSONObject();
            if (mConfig.getAppDiscoveryMode()){
                json = getDeviceInfo();
            }
            response.send(json);
            // response.end();
        });

        mHttpServer.post("/command", (request, response) -> {
            Log.d(TAG, "command: " + request);
            response.send("ok");
            JSONObject body = ((JSONObjectBody)request.getBody()).get();
            processCommand(body.toString());
        });



        mHttpServer.post("/text_to_speech", (request, response) -> {
            Log.d(TAG, "text_to_speech: " + request);
            response.send("ok");
            //
            JSONObject body = ((JSONObjectBody)request.getBody()).get();
            String text_to_say;
            try {
                text_to_say = body.getString("text");
                processTTS(text_to_say);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });
        mHttpServer.post("/text_to_speech", (request, response) -> {
            Log.d(TAG, "text_to_speech: " + request);
            JSONObject body = ((JSONObjectBody)request.getBody()).get();
            processTTS(body);
            response.send("ok");
            response.end();
        });

        mHttpServer.get("/text_to_speech", (request, response) -> {
            String text;
            try {
                Log.d(TAG, "text_to_speech: " + request.getQuery().toString());
                text = request.getQuery().getString("text");
                JSONObject json = new JSONObject();
                if (text == null){
                    text = "Nie wiem co mam powiedzieć";
                }
                json.put("text", text);
                String pitch = request.getQuery().getString("pitch");
                if (pitch != null){
                    json.put("pitch", pitch);
                }
                String rate = request.getQuery().getString("rate");
                if (pitch != null){
                    json.put("rate", rate);
                }
                String language = request.getQuery().getString("language");
                if (language != null){
                    json.put("language", language);
                }
                String voice = request.getQuery().getString("voice");
                if (voice != null){
                    json.put("voice", voice);
                }
                String path = request.getQuery().getString("format");
                if (path != null){
                    json.put("path", path);
                }
                String format = request.getQuery().getString("format");
                if (format != null){
                    json.put("format", format);
                }
                processTTS(json);
            } catch (Exception e) {
                e.printStackTrace();
            }
            response.send("ok");
            response.end();
        });

        // show cast media player info
        mHttpServer.get("/audio_status", (request, response) -> {
            Log.d(TAG, "request: " + request);
            JSONObject jState = new JSONObject();
            try {
                boolean playing = false;
                long duration = 0;
                long currentPosition = 0;
                int state = Player.STATE_IDLE;
                if (mCastExoPlayer!= null) {
                    playing = mCastExoPlayer.isPlaying();
                    duration = mCastExoPlayer.getDuration();
                    currentPosition = mCastExoPlayer.getCurrentPosition();
                    state = mCastExoPlayer.getPlaybackState();
                }
                jState.put("currentStatus", state);
                jState.put("currentMedia", m_cast_media_title);
                jState.put("playing", playing);
                jState.put("currentVolume", getVolume());
                jState.put("duration", duration);
                jState.put("currentPosition", currentPosition);
                jState.put("currentSpeed", 1);
                jState.put("media_source", m_cast_media_source);
                jState.put("media_album_name", m_cast_media_album_name);
                if (m_cast_media_stream_image == null) {
                    jState.put("media_stream_image", "");
                } else {
                    jState.put("media_stream_image", m_cast_media_stream_image);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

            response.setContentType("application/json");
            response.send(jState);
        });

        // listen on port 8122
        mHttpServer.listen(8122);
    }


    //******** API Functions *****************
    private void getAudioInfoFromCloud(String audioUrl) {
        // Get the full audio info from AIS Cloud
        String url = AisCoreUtils.getAisDomCloudWsUrl(true) + "get_audio_full_info";
        JSONObject audioInfo = new JSONObject();
        try {
            audioInfo.put("audio_url", audioUrl);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        Map<String, String> heders = new HashMap<>();
        heders.put("Content-Type", "application/json");
        DomCustomRequest jsonObjectRequest = new DomCustomRequest(Request.Method.POST, url, heders, audioInfo.toString(), response -> {
            try {
                setCastMediaInfo(response);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, error -> Log.e(TAG, "getAudioInfoFromCloud: " + error.toString())
        ){
        };
        RequestQueue requestQueue = Volley.newRequestQueue(getApplicationContext());
        requestQueue.add(jsonObjectRequest);
    }

    private boolean processCommand(JSONObject commandJson) {
        Log.d(TAG, "processCommand Called " + commandJson.toString());
        try {
            if(commandJson.has("playAudio")) {
                String url = commandJson.getString("playAudio");
                // get info about audio from AIS
                getAudioInfoFromCloud(url);
                //
                LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
                Intent castIntent = new Intent(AisCoreUtils.BROADCAST_CAST_COMMAND);
                castIntent.putExtra(AisCoreUtils.BROADCAST_CAST_COMMAND_TEXT, url);
                bm.sendBroadcast(castIntent);
            }
            else if(commandJson.has("setAudioInfo")) {
                JSONObject audioInfo = commandJson.getJSONObject("setAudioInfo");
                setAudioInfo(audioInfo);
            }
            else if(commandJson.has("stopAudio")) {
                LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
                Intent stopIntent = new Intent(AisCoreUtils.BROADCAST_CAST_COMMAND);
                stopIntent.putExtra(AisCoreUtils.BROADCAST_CAST_COMMAND_TEXT, "stop");
                bm.sendBroadcast(stopIntent);
            }
            else if(commandJson.has("pauseAudio")) {
                boolean pauseAudio = commandJson.getBoolean("pauseAudio");
                LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
                Intent pauseIntent = new Intent(AisCoreUtils.BROADCAST_CAST_COMMAND);
                if (pauseAudio) {
                    pauseIntent.putExtra(AisCoreUtils.BROADCAST_CAST_COMMAND_TEXT, "pause");
                } else {
                    pauseIntent.putExtra(AisCoreUtils.BROADCAST_CAST_COMMAND_TEXT, "play");
                }
                bm.sendBroadcast(pauseIntent);
            }
            else if(commandJson.has("setVolume")) {
                int vol = commandJson.getInt("setVolume");
                setVolume(vol);
            }
            else if(commandJson.has("upVolume")) {
                int vol = Math.min(getVolume() + 10, 100);
                setVolume(vol);
            }
            else if(commandJson.has("downVolume")) {
                int vol = Math.max(getVolume() - 10, 0);
                setVolume(vol);
            }
            else if(commandJson.has("setPlaybackPitch")) {
                // TODO
                // setPlaybackPitch(Float.parseFloat(commandJson.getString("setPlaybackPitch")));
            }
            else if(commandJson.has("seekTo")) {
                // TODO
                // seekTo(commandJson.getLong("seekTo"));
            }
            else if(commandJson.has("skipTo")) {
                // TODO
                // skipTo(commandJson.getLong("skipTo"));
            }
            else if(commandJson.has("micOn")) {
                Intent requestIntent = new Intent(AisPanelService.BROADCAST_ON_AIS_REQUEST);
                requestIntent.putExtra("aisRequest", "micOn");
                requestIntent.putExtra("url", "");
                LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
                bm.sendBroadcast(requestIntent);
            }
            else if(commandJson.has("micOff")) {
                Intent requestIntent = new Intent(AisPanelService.BROADCAST_ON_AIS_REQUEST);
                requestIntent.putExtra("aisRequest", "micOff");
                requestIntent.putExtra("url", "");
                LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
                bm.sendBroadcast(requestIntent);
            } else if (commandJson.has("showCamera")) {
                Intent requestIntent = new Intent(BROADCAST_CAMERA_COMMAND);
                JSONObject showCamera = commandJson.getJSONObject("showCamera");
                String url = showCamera.getString("streamUrl");
                String haCamId = showCamera.getString("haCamId");
                requestIntent.putExtra(BROADCAST_CAMERA_COMMAND_URL, url);
                requestIntent.putExtra(BROADCAST_CAMERA_HA_ID, haCamId);
                LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
                bm.sendBroadcast(requestIntent);
            }
        }
        catch (JSONException ex) {
            Log.e(TAG, "Invalid JSON passed as a command: " + commandJson.toString());
            return false;
        }
        return true;
    }

    private boolean processCommand(String command) {
        Log.d(TAG, "processCommand Called");
        try {
            return processCommand(new JSONObject(command));
        }
        catch (JSONException ex) {
            Log.e(TAG, "Invalid JSON passed as a command: " + command);
            return false;
        }
    }

    private String getVoiceForName(String voice) {
        if (voice.toLowerCase().equals("jola")) {
            return "pl-pl-x-oda-local";
        }
        if (voice.toLowerCase().equals("jola online")) {
            return "pl-pl-x-oda-network";
        }
        if (voice.toLowerCase().equals("celina")) {
            return "pl-pl-x-oda#female_1-local";
        }
        if (voice.toLowerCase().equals("anżela")) {
            return "pl-pl-x-oda#female_2-local";
        }
        if (voice.toLowerCase().equals("asia")) {
            return "pl-pl-x-oda#female_3-local";
        }
        if (voice.toLowerCase().equals("sebastian")) {
            return "pl-pl-x-oda#male_1-local";
        }
        if (voice.toLowerCase().equals("bartek")) {
            return "pl-pl-x-oda#male_2-local";
        }
        if (voice.toLowerCase().equals("andrzej")) {
            return "pl-pl-x-oda#male_3-local";
        }

        // US
        if (voice.toLowerCase().equals("sophia")) {
            return "en-us-x-sfg#female_2-local";
        }
        if (voice.toLowerCase().equals("sam")) {
            return "en-us-x-sfg#male_2-local";
        }

        // GB
        if (voice.toLowerCase().equals("allison")) {
            return "en-GB-language";
        }
        if (voice.toLowerCase().equals("jon")) {
            return "en-gb-x-fis#male_2-local";
        }


        // UK
        if (voice.toLowerCase().equals("mariya")) {
            return "uk-UA-language";
        }

        return voice;
    }

    // New json way
    private boolean processTTS(JSONObject message) {
        String textForReading;
        String lang = "pl_PL";
        String voice = "pl-pl-x-oda-local";
        float pitch = 1;
        float rate = 1;


        // to get voice from config
        if (mConfig == null){
            mConfig = new Config(this.getApplicationContext());
        }
        //
        try {
            textForReading = message.getString("text");

            if (message.has("pitch")) {
                pitch = BigDecimal.valueOf(message.getDouble("pitch")).floatValue();
                mTts.setPitch(pitch);
            }
            if (message.has("rate")) {
                rate = BigDecimal.valueOf(message.getDouble("rate")).floatValue();
                mTts.setSpeechRate(rate);
            }
            if  (message.has("language")) {
                lang = message.getString("language");
            }
            if (message.has("voice")) {
                voice = getVoiceForName(message.getString("voice"));
            }
            Voice voiceobj = new Voice(
                    voice, new Locale(lang),
                    Voice.QUALITY_HIGH,
                    Voice.LATENCY_NORMAL,
                    false,
                    null);
            mTts.setVoice(voiceobj);
        }
        catch (Exception ex) {
            Log.e(TAG, "Exception: " + ex.toString());
            return false;
        }


        //textToSpeech can only cope with Strings with < 4000 characters
        if(textForReading.length() >= 4000) {
            textForReading = textForReading.substring(0, 3999);
        }
        mTts.speak(textForReading, TextToSpeech.QUEUE_FLUSH, null,"123");

        Intent intent = new Intent(BROADCAST_ON_START_TEXT_TO_SPEECH);
        intent.putExtra(AisCoreUtils.TTS_TEXT, textForReading);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
        bm.sendBroadcast(intent);

        return true;
    }



    // old text way
    private void processTTS(String text) {
        Log.d(TAG, "processTTS Called: " + text);

        if(!AisCoreUtils.shouldIsayThis(text, "service_ais_panel")){
            return;
        }

        // stop current TTS
        stopTextToSpeech();

        // speak failed: not bound to TTS engine
        if (mTts == null){
            Log.w(TAG, "mTts == null");
            try {
                createTTS();
                return;
            }
            catch (Exception e) {
                Log.e(TAG, e.toString());
            }
        }

        // to get voice from config
        if (mConfig == null){
            mConfig = new Config(this.getApplicationContext());
        }

        String ttsVoice = mConfig.getAppTtsVoice();
        Voice voiceObj = new Voice(
                    ttsVoice, new Locale("pl_PL"),
                    Voice.QUALITY_HIGH,
                    Voice.LATENCY_NORMAL,
                    false,
                    null);
        mTts.setVoice(voiceObj);


        //textToSpeech can only cope with Strings with < 4000 characters
        if(text.length() >= 4000) {
            text = text.substring(0, 3999);
        }
        mTts.speak(text, TextToSpeech.QUEUE_FLUSH, null,"123");

        Intent intent = new Intent(BROADCAST_ON_START_TEXT_TO_SPEECH);
        intent.putExtra(AisCoreUtils.TTS_TEXT, text);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
        bm.sendBroadcast(intent);

    }

    // play audio on local exo
    private void playCastMedia(String streamUrl){
        mCastStreamUrl = streamUrl;
        //
        Intent playerActivity = new Intent(this, AisMediaPlayerActivity.class);
        playerActivity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        playerActivity.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(playerActivity);
    }
    // show camera view
    public void showCamView(String streamUrl, String haCamId){
        Intent camActivity = new Intent(this, AisCamActivity.class);
        // "rtsp://192.168.2.38/unicast"
        camActivity.putExtra(BROADCAST_CAMERA_COMMAND_URL, streamUrl);
        camActivity.putExtra(BROADCAST_CAMERA_HA_ID, haCamId);
        camActivity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        camActivity.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(camActivity);

    }

    private void getAudioInfoForNotification() {
        Log.e(TAG, "get info ");
        if (AisCoreUtils.AIS_REMOTE_GATE_ID != null && AisCoreUtils.AIS_REMOTE_GATE_ID.startsWith("dom-")) {
            String url = AisCoreUtils.getAisDomCloudWsUrl(true) + "get_audio_full_info";
            JSONObject audioInfo = new JSONObject();
            try {
                audioInfo.put("audio_url", AisCoreUtils.AIS_REMOTE_GATE_ID);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            DomCustomRequest jsonObjectRequest = new DomCustomRequest(
                    Request.Method.POST, url, headers, audioInfo.toString(), this::onResponse, error -> Log.e(TAG, "refreshAudioNotification: " + error.toString())
            ) {
            };
            RequestQueue requestQueue = Volley.newRequestQueue(getApplicationContext());
            requestQueue.add(jsonObjectRequest);
        }
    }


    private void setCastMediaInfo(JSONObject audioInfo) {
        Log.d(TAG, "setCastMediaInfo Called: " + audioInfo.toString());
        try {
            m_cast_media_title = audioInfo.getString("media_title");
            m_cast_media_source = audioInfo.getString("media_source");
            m_cast_media_album_name = audioInfo.getString("media_album_name");
            m_cast_media_stream_image = audioInfo.getString("media_stream_image");
        } catch (Exception e) {
            Log.e(TAG, "setCastMediaInfo " + e.toString());
        }
    }

    private void refreshAisNotification(){
        mAisServiceNotification = new NotificationCompat.Builder(this, AIS_DOM_CHANNEL_ID)
                .setContentTitle(m_ais_media_title)
                .setContentText(m_ais_media_source)
                // Show controls on lock screen even when user hides sensitive content.
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                // Add media control buttons that invoke intents in your media service
                .addAction( R.drawable.ic_app_exit, "Stop", mPendingIntentAisStop) // #0
                .addAction( R.drawable.exo_icon_previous, "Previous", mPendingIntentAisPrev) // #1
                .addAction(R.drawable.ic_app_play_pause, "Pause", mPendingIntentAisPause)  // #2
                .addAction(R.drawable.exo_icon_next, "Next", mPendingIntentAisNext)     // #3
                .addAction(R.drawable.ais_icon_mic, "Mic", mPendingIntentAisMic)     // #4
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle())
                .setSmallIcon(R.drawable.ic_ais_logo)
                .setLargeIcon(m_ais_media_bitmap_image)
                .setContentIntent(mGoToAisPendingIntent)
                .setSound(null)
                .build();
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert notificationManager != null;
        notificationManager.notify(AisCoreUtils.AIS_DOM_NOTIFICATION_ID, mAisServiceNotification);
    }

    private void setAudioInfo(JSONObject audioInfo) {
        Log.d(TAG, "setAudioInfo Called: " + audioInfo.toString());
        try {
            m_ais_media_bitmap_image = getSpeakerImage();
            m_ais_media_title = audioInfo.getString("media_title");
            m_ais_media_source = audioInfo.getString("media_source");
            m_ais_media_album_name = audioInfo.getString("media_album_name");
            m_ais_media_stream_image_url = audioInfo.getString("media_stream_image");
            refreshAisNotification();
            // get new image and refresh notification
            final Handler handler = new Handler();
            handler.postDelayed(this::getCurrentAisLargeIcon, 2500);
        } catch (Exception e) {
            Log.e(TAG, "setAudioInfo " + e.toString());
        }
    }

    private void setVolume(int vol){
        Log.d(TAG, "setVolume Called: " + vol);
        try {
            AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
            int maxVolume =  audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int volToSet = Math.max(Math.round((vol * maxVolume) / 100), 1);
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volToSet, AudioManager.FLAG_SHOW_UI);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    private int getVolume(){
        Log.d(TAG, "getVolume Called ");
        //return exoPlayer.getVolume();
        try {
            AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
            int currVolume =  audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            int maxSystemVolume =  audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

            return Math.round(((currVolume * 100) / maxSystemVolume));
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            return 100;
        }
    }


    //
    private void executeCastPlayerCommand(String command) {
        switch (command) {
            case "pause":
                try {
                    if (mCastExoPlayer != null) {
                        mCastExoPlayer.setPlayWhenReady(false);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error pauseAudio: " + e.getMessage());
                }
                break;
            case "play":
                try {
                    if (mCastExoPlayer != null) {
                        mCastExoPlayer.setPlayWhenReady(true);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error pauseAudio: " + e.getMessage());
                }
                break;
            case "stop":
                try {
                    if (mCastExoPlayer != null) {
                        mCastExoPlayer.stop();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error pauseAudio: " + e.getMessage());
                }
                break;
            case "refresh_notification":
                getAudioInfoForNotification();
                break;
            default:
                playCastMedia(command);
                break;
        }
    }

    public class NotifyServiceReceiver extends BroadcastReceiver{

        @Override
        public void onReceive(Context arg0, Intent intent) {

            String action = intent.getAction();

            switch (action) {
                case "ais_play_pause":
                    DomWebInterface.publishMessage("media_play_pause", "media_player", getApplicationContext());
                    break;
                case "ais_prev":
                    DomWebInterface.publishMessage("media_previous_track", "media_player", getApplicationContext());
                    break;
                case "ais_next":
                    DomWebInterface.publishMessage("media_next_track", "media_player", getApplicationContext());
                    break;
                case "ais_mic":
                    if (AisCoreUtils.mSpeechIsRecording) {
                        Log.e(TAG, "mSpeechIsRecording = false");
                        AisCoreUtils.mSpeechIsRecording = false;
                        if (AisCoreUtils.mSpeech != null) {
                            AisCoreUtils.mSpeech.stopListening();
                        }

                    } else {
                        Log.e(TAG, "mSpeechIsRecording = true");
                        AisCoreUtils.mSpeechIsRecording = true;
                        if (AisCoreUtils.mSpeech == null) {
                            AisCoreUtils.mSpeech = SpeechRecognizer.createSpeechRecognizer(getApplicationContext());
                            AisRecognitionListener listener = new AisRecognitionListener(getApplicationContext(), AisCoreUtils.mSpeech);
                            AisCoreUtils.mSpeech.setRecognitionListener(listener);
                        }
                        stopTextToSpeech();
                        AisCoreUtils.mSpeech.startListening(AisCoreUtils.mRecognizerIntent);
                    }
                    break;
                case "ais_stop":
                    //
                    mConfig.setAppDiscoveryMode(false);

                    // stop audio service
                    Intent stopIntent = new Intent(getApplicationContext(), AisPanelService.class);
                    getApplicationContext().stopService(stopIntent);

                    break;
            }
        }
    }

}


