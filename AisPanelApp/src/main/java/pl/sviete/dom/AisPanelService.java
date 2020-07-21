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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.support.v4.media.session.MediaSessionCompat;

import android.util.Log;
import android.widget.Toast;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;

import com.koushikdutta.async.http.body.JSONObjectBody;
import com.koushikdutta.async.http.server.AsyncHttpServer;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


import ai.picovoice.hotword.PorcupineService;

import static pl.sviete.dom.AisCoreUtils.AIS_DOM_CHANNEL_ID;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_EXO_PLAYER_COMMAND;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_ON_END_SPEECH_TO_TEXT;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_ON_END_TEXT_TO_SPEECH;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_ON_START_SPEECH_TO_TEXT;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_ON_START_TEXT_TO_SPEECH;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_SERVICE_SAY_IT;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_SAY_IT_TEXT;
import static pl.sviete.dom.AisCoreUtils.isServiceRunning;


public class AisPanelService extends Service implements TextToSpeech.OnInitListener, ExoPlayer.EventListener {
    public static final String BROADCAST_EVENT_DO_STOP_TTS = "BROADCAST_EVENT_DO_STOP_TTS";
    public static final String BROADCAST_EVENT_CHANGE_CONTROLLER_MODE = "BROADCAST_EVENT_CHANGE_CONTROLLER_MODE";
    public static final String  BROADCAST_ON_AIS_REQUEST = "BROADCAST_ON_AIS_REQUEST";

    // Spotify
    public static final String  BROADCAST_SPOTIFY_PLAY_AUDIO = "BROADCAST_SPOTIFY_PLAY_AUDIO";
    public static final String  BROADCAST_SPOTIFY_PLAY_AUDIO_ID_VALUE = "BROADCAST_SPOTIFY_PLAY_AUDIO_ID_VALUE";
    public static final String  BROADCAST_SPOTIFY_PLAY_AUDIO_SET_SHUFFLE = "BROADCAST_SPOTIFY_PLAY_AUDIO_SET_SHUFFLE";
    public static final String  BROADCAST_SPOTIFY_PLAY_AUDIO_SET_SEEK_TO = "BROADCAST_SPOTIFY_PLAY_AUDIO_SET_SEEK_TO";

    //
    private final String TAG = AisPanelService.class.getName();
    private static Config mConfig;

    private PowerManager.WakeLock fullWakeLock;
    private PowerManager.WakeLock partialWakeLock;
    private WifiManager.WifiLock wifiLock;

    private AsyncHttpServer mHttpServer;
    private static String currentUrl;

    private final IBinder mBinder = new AisPanelServiceBinder();

    private TextToSpeech mTts;

    //ExoPlayer -start
    private Handler mainHandler;
    private RenderersFactory renderersFactory;
    private BandwidthMeter bandwidthMeter;
    private LoadControl loadControl;
    private DataSource.Factory dataSourceFactory;
    private ExtractorsFactory extractorsFactory;
    private MediaSource mediaSource;
    private TrackSelection.Factory trackSelectionFactory;
    private SimpleExoPlayer mExoPlayer;
    private TrackSelector trackSelector;
    public static String m_media_title = null;
    public static String m_media_source = AisCoreUtils.mAudioSourceAndroid;
    public static String m_media_stream_image = null;
    public static String m_media_album_name = null;
    private String m_media_content_id = null;
    private Boolean m_media_controll_only = false;
    private PlayerNotificationManager playerNotificationManager;
    private String m_exo_player_last_media_stream_image = null;
    Bitmap m_exo_player_large_icon = null;
    private MediaSessionCompat mediaSession;
    private String MEDIA_SESSION_TAG = "ais";


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

    private JSONObject getCurrentMediaInfo(){
        JSONObject jState = new JSONObject();
        try {
            jState.put("duration", mExoPlayer.getDuration());
            jState.put("currentMedia", m_media_title);
            jState.put("playing", mExoPlayer.getPlayWhenReady());
            jState.put("currentPosition", mExoPlayer.getCurrentPosition());
            jState.put("currentSpeed", mExoPlayer.getPlaybackParameters().speed);
            jState.put("media_source", m_media_source);
            jState.put("media_album_name", m_media_album_name);
            if (m_media_stream_image == null) {
                jState.put("media_stream_image", m_media_stream_image);
            } else {
                jState.put("media_stream_image", "");
            }
        } catch(JSONException e) { e.printStackTrace(); }
        return jState;
    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

    }

    @Override
    public void onLoadingChanged(boolean isLoading) {

    }


    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        // not inform about intro - giveMeNextOne problem
        if (m_media_source.equals(AisCoreUtils.mAudioSourceAndroid)) {
            return;
        }

        // do not inform about status change if it's only remote controller
        if (m_media_controll_only) {
            return;
        }

        JSONObject jState = new JSONObject();
        if (playbackState == ExoPlayer.STATE_BUFFERING) {
            Log.v(TAG, "STATE_BUFFERING");
        } else if (playbackState == ExoPlayer.STATE_IDLE) {
            Log.v(TAG, "STATE_IDLE");
        } else {
            // inform client about status change
            jState = getCurrentMediaInfo();
//            if (playbackState == ExoPlayer.STATE_ENDED){
//                if (!m_media_content_id.equals("")) {
//                    try {
//                        jState.put("giveMeNextOne", true);
//                    } catch (JSONException e) {
//                        e.printStackTrace();
//                    }
//                }
//            }
            try {
                jState.put("currentStatus", playbackState);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            DomWebInterface.publishJson(jState, "player_status", getApplicationContext());
        }
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {

    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {

    }


    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {

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


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        createNotificationChannel();

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                new Intent(this, BrowserActivityNative.class),
                0);

        String aisTitle = "Player";
        if (intent.hasExtra("aisRequest")){
            aisTitle = intent.getStringExtra("aisRequest");
        }


        Notification serviceNotification = new NotificationCompat.Builder(this, AIS_DOM_CHANNEL_ID)
                .setContentTitle("AIS dom")
                .setContentText(aisTitle)
                .setSmallIcon(R.drawable.ic_ais_logo)
                .setContentIntent(pendingIntent)
                .setSound(null)
                .build();

        startForeground(AisCoreUtils.AIS_DOM_NOTIFICATION_ID, serviceNotification);


        // play join intro
        playAudio("asset:///1-second-of-silence.mp3", false, 0);

        if (mConfig.getAppDiscoveryMode()) {
            // player auto discovery on gate
            JSONObject json = new JSONObject();
            json = getDeviceInfo();
            DomWebInterface.publishJson(json, "player_auto_discovery", getApplicationContext());
            Log.i(TAG, "player_auto_discovery");
        }
        //
        return super.onStartCommand(intent, flags, startId);
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
            };
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

        m_exo_player_large_icon = getSpeakerImage();

        mConfig = new Config(getApplicationContext());
        // get current url without discovery
        currentUrl = mConfig.getAppLaunchUrl(false);

        // prepare the lock types we may use
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        partialWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dom:partialWakeLock");
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, "dom:wifiLock");


        IntentFilter filter = new IntentFilter();
        filter.addAction(BROADCAST_EVENT_DO_STOP_TTS);
        filter.addAction(BROADCAST_ON_END_SPEECH_TO_TEXT);
        filter.addAction(BROADCAST_ON_START_SPEECH_TO_TEXT);
        filter.addAction(BROADCAST_ON_END_TEXT_TO_SPEECH);
        filter.addAction(BROADCAST_ON_START_TEXT_TO_SPEECH);
        filter.addAction(BROADCAST_EVENT_CHANGE_CONTROLLER_MODE);
        filter.addAction(BROADCAST_SPOTIFY_PLAY_AUDIO);
        filter.addAction((BROADCAST_EXO_PLAYER_COMMAND));
        filter.addAction(BROADCAST_SERVICE_SAY_IT);
        filter.addAction(BROADCAST_ON_AIS_REQUEST);
        filter.addAction("com.spotify.music.playbackstatechanged");
        filter.addAction("com.spotify.music.metadatachanged");
        filter.addAction("com.spotify.music.queuechanged");

        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(this);
        bm.registerReceiver(mBroadcastReceiver, filter);

        // http api server
        configureHttp();

        //ExoPlayer
        renderersFactory = new DefaultRenderersFactory(getApplicationContext());
        bandwidthMeter = new DefaultBandwidthMeter();
        trackSelectionFactory = new AdaptiveTrackSelection.Factory(bandwidthMeter);
        trackSelector = new DefaultTrackSelector(trackSelectionFactory);
        loadControl = new DefaultLoadControl();
        mExoPlayer = ExoPlayerFactory.newSimpleInstance(this, renderersFactory, trackSelector, loadControl);
        mExoPlayer.addListener(this);
        playerNotificationManager = new customPlayerNotificationManager(
                this,
                AIS_DOM_CHANNEL_ID,
                //R.string.playback_channel_name,
                //R.string.playback_channel_descr,
                AisCoreUtils.AIS_DOM_NOTIFICATION_ID,
                new DescriptionAdapter(),
                new customNotificationListener(),
                new customActionReceiver()
        );

        playerNotificationManager.setPlayer(mExoPlayer);
        // omit skip previous and next actions
        playerNotificationManager.setUseNavigationActions(false);
        // omit fast forward action by setting the increment to zero
        playerNotificationManager.setFastForwardIncrementMs(0);
        // omit rewind action by setting the increment to zero
        playerNotificationManager.setRewindIncrementMs(0);
        //
        playerNotificationManager.setUsePlayPauseActions(false);
        playerNotificationManager.setSmallIcon(R.drawable.ic_ais_logo);
        //
        mediaSession = new MediaSessionCompat(this, MEDIA_SESSION_TAG);
        mediaSession.setActive(true);
        playerNotificationManager.setMediaSessionToken(mediaSession.getSessionToken());
        playerNotificationManager.setUseNavigationActionsInCompactView(true);

        dataSourceFactory = new DefaultDataSourceFactory(getApplicationContext(), "AisDom");
        extractorsFactory = new DefaultExtractorsFactory();


        createTTS();
        //
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

        if (mExoPlayer != null) {
            mExoPlayer.stop();
            playerNotificationManager.setPlayer(null);
//            try {
//                playerNotificationManager.notify();
//            } catch (Exception e){
//                Log.e(TAG, e.toString());
//            }
            mExoPlayer.release();
            mExoPlayer = null;
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

        Log.i(TAG, "destroy");
    }


    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();

            if (action.equals(BROADCAST_EVENT_DO_STOP_TTS)) {
                Log.d(TAG, "Speech started, stoping the tts");
                stopTextToSpeech();
            } else if (action.equals(BROADCAST_SERVICE_SAY_IT)) {
                Log.d(TAG, BROADCAST_SERVICE_SAY_IT + " going to processTTS");
                final String txtMessage = intent.getStringExtra(BROADCAST_SAY_IT_TEXT);
                processTTS(txtMessage);
            } else if (action.equals(BROADCAST_ON_START_SPEECH_TO_TEXT)) {
                Log.d(TAG, BROADCAST_ON_START_SPEECH_TO_TEXT + " turnDownVolume");
            } else if (action.equals(BROADCAST_ON_END_SPEECH_TO_TEXT)) {
                Log.d(TAG, BROADCAST_ON_END_SPEECH_TO_TEXT + " turnUpVolume");
            } else if (action.equals(BROADCAST_ON_START_TEXT_TO_SPEECH)) {
                Log.d(TAG, BROADCAST_ON_START_TEXT_TO_SPEECH + " turnDownVolume");
            } else if (action.equals(BROADCAST_ON_END_TEXT_TO_SPEECH)) {
                Log.d(TAG, BROADCAST_ON_END_TEXT_TO_SPEECH + " turnUpVolume");
            } else if (action.equals(BROADCAST_EXO_PLAYER_COMMAND)){
                final String command = intent.getStringExtra(AisCoreUtils.BROADCAST_EXO_PLAYER_COMMAND_TEXT);
                executeExoPlayerCommand(command, m_media_content_id);
            } else if (action.equals(BROADCAST_ON_AIS_REQUEST)) {
                Log.d(TAG, BROADCAST_ON_AIS_REQUEST);
                if (intent.hasExtra("aisRequest")){
                    String aisRequest = intent.getStringExtra("aisRequest");
                    if (aisRequest.equals("micOn")) {
                        onStartStt();
                    }
                }
            }
        }
    };

    private void onStartStt() {
        try {
            AisCoreUtils.mRecognizerIntent = null;
            AisCoreUtils.mRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            AisCoreUtils.mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString());
            AisCoreUtils.mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            AisCoreUtils.mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, new Long(5000));
            AisCoreUtils.mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, "pl.sviete.dom");
            AisCoreUtils.mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

        } catch (Exception e){
            Log.e(TAG, e.getMessage());
        }

        try {
            AisCoreUtils.mSpeech.cancel();
            AisCoreUtils.mSpeech.destroy();
            AisCoreUtils.mSpeech = null;
        } catch (Exception e){
            Log.e(TAG, e.getMessage());
        }

        try {
            AisCoreUtils.mSpeech = SpeechRecognizer.createSpeechRecognizer(getApplicationContext());
            AisRecognitionListener listener = new AisRecognitionListener(getApplicationContext(), AisCoreUtils.mSpeech);
            AisCoreUtils.mSpeech.setRecognitionListener(listener);
        } catch (Exception e){
            Log.e(TAG, e.getMessage());
        }


        AisCoreUtils.mSpeechIsRecording = true;
        stopTextToSpeech();
        AisCoreUtils.mSpeech.startListening(AisCoreUtils.mRecognizerIntent);
    }


    //******** HTTP Related Functions

    private JSONObject getDeviceInfo(){
        JSONObject json = new JSONObject();
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
            response.end();
        });

        mHttpServer.post("/command", (request, response) -> {
            Log.d(TAG, "command: " + request);
            response.send("ok");
            response.end();
            JSONObject body = ((JSONObjectBody)request.getBody()).get();
            processCommand(body.toString());
        });



        mHttpServer.post("/text_to_speech", (request, response) -> {
            Log.d(TAG, "text_to_speech: " + request);
            response.send("ok");
            response.end();
            //
            JSONObject body = ((JSONObjectBody)request.getBody()).get();
            String text_to_say = "";
            try {
                text_to_say = body.getString("text");
                processTTS(text_to_say);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });

        // listen on port 8122
        mHttpServer.listen(8122);
    }


    //******** API Functions *****************

    private boolean processCommand(JSONObject commandJson) {
        Log.d(TAG, "processCommand Called " + commandJson.toString());
        try {
            if(commandJson.has("playAudioFullInfo")) {
                m_media_controll_only = false;
                JSONObject audioInfo = commandJson.getJSONObject("playAudioFullInfo");
                setAudioInfo(audioInfo);
                String url = audioInfo.getString("media_content_id");
                boolean shuffle = audioInfo.getBoolean("setPlayerShuffle");
                long position = audioInfo.getLong("setMediaPosition");
                m_media_content_id = url;
                if (!url.equals("")) {
                    LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
                    Intent playIntent = new Intent(AisCoreUtils.BROADCAST_EXO_PLAYER_COMMAND);
                    playIntent.putExtra(AisCoreUtils.BROADCAST_EXO_PLAYER_COMMAND_TEXT, "play_audio");
                    bm.sendBroadcast(playIntent);
                }
            }
            else if(commandJson.has("playAudio")) {
                // depricated - switch to playAudioFullInfo
                m_media_controll_only = false;
                String url = commandJson.getString("playAudio");
                m_media_content_id = url;
                LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
                Intent playIntent = new Intent(AisCoreUtils.BROADCAST_EXO_PLAYER_COMMAND);
                playIntent.putExtra(AisCoreUtils.BROADCAST_EXO_PLAYER_COMMAND_TEXT, "play_audio");
                bm.sendBroadcast(playIntent);
            }
            else if(commandJson.has("setAudioInfo")) {
                JSONObject audioInfo = commandJson.getJSONObject("setAudioInfo");
                setAudioInfo(audioInfo);
            }
            else if(commandJson.has("setAudioInfoNoPlay")) {
                m_media_controll_only = true;
                JSONObject audioInfo = commandJson.getJSONObject("setAudioInfoNoPlay");
                setAudioInfoNoPlay(audioInfo);
            }
            else if(commandJson.has("stopAudio")) {
                stopAudio();
                LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
                Intent stopIntent = new Intent(AisCoreUtils.BROADCAST_EXO_PLAYER_COMMAND);
                stopIntent.putExtra(AisCoreUtils.BROADCAST_EXO_PLAYER_COMMAND_TEXT, "stop");
                bm.sendBroadcast(stopIntent);
            }
            else if(commandJson.has("pauseAudio")) {
                Boolean pauseAudio = commandJson.getBoolean("pauseAudio");
                LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
                Intent pauseIntent = new Intent(AisCoreUtils.BROADCAST_EXO_PLAYER_COMMAND);
                if (pauseAudio) {
                    pauseIntent.putExtra(AisCoreUtils.BROADCAST_EXO_PLAYER_COMMAND_TEXT, "pause");
                } else {
                    pauseIntent.putExtra(AisCoreUtils.BROADCAST_EXO_PLAYER_COMMAND_TEXT, "play");
                }
                bm.sendBroadcast(pauseIntent);
            }
            else if(commandJson.has("getAudioStatus")) {
                JSONObject jState = getAudioStatus();
                DomWebInterface.publishJson(jState, "player_status", getApplicationContext());
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
                setPlaybackPitch(Float.parseFloat(commandJson.getString("setPlaybackPitch")));
            }
            else if(commandJson.has("seekTo")) {
                seekTo(commandJson.getLong("seekTo"));
            }
            else if(commandJson.has("skipTo")) {
                skipTo(commandJson.getLong("skipTo"));
            }
            else if(commandJson.has("setPlayerShuffle")) {
                setPlayerShuffle(commandJson.getBoolean("setPlayerShuffle"));
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


    private boolean processTTS(String text) {
        Log.d(TAG, "processTTS Called: " + text);

        if(!AisCoreUtils.shouldIsayThis(text, "service_ais_panel")){
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
        if (mConfig == null){
            mConfig = new Config(this.getApplicationContext());
        }

        String ttsVoice = mConfig.getAppTtsVoice();
        Voice voiceobj = new Voice(
                    ttsVoice, new Locale("pl_PL"),
                    Voice.QUALITY_HIGH,
                    Voice.LATENCY_NORMAL,
                    false,
                    null);
        mTts.setVoice(voiceobj);


        //textToSpeech can only cope with Strings with < 4000 characters
        if(text.length() >= 4000) {
            text = text.substring(0, 3999);
        }
        mTts.speak(text, TextToSpeech.QUEUE_FLUSH, null,"123");

        Intent intent = new Intent(BROADCAST_ON_START_TEXT_TO_SPEECH);
        intent.putExtra(AisCoreUtils.TTS_TEXT, text);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
        bm.sendBroadcast(intent);

        return true;

    }

    private void playAudio(String streamUrl, boolean shuffle, long position){
        Log.d(TAG, "playAudio Called: " + streamUrl + " shuffle: " + shuffle + " position: " + position);
        m_media_content_id = streamUrl;
        if (streamUrl.startsWith("spotify:")){
            // stop exo
            stopExoPlayer();
            Log.d(TAG, "spotifyConnectToAppRemote Called");
            Intent intent = new Intent(BROADCAST_SPOTIFY_PLAY_AUDIO);
            intent.putExtra(BROADCAST_SPOTIFY_PLAY_AUDIO_ID_VALUE, streamUrl);
            intent.putExtra(BROADCAST_SPOTIFY_PLAY_AUDIO_SET_SHUFFLE, shuffle);
            intent.putExtra(BROADCAST_SPOTIFY_PLAY_AUDIO_SET_SEEK_TO, position);
            LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
            bm.sendBroadcast(intent);
        } else {
            try {
                mExoPlayer.stop();
                mediaSource = new ExtractorMediaSource(Uri.parse(streamUrl),
                        dataSourceFactory,
                        extractorsFactory,
                        mainHandler,
                        null);
                mExoPlayer.prepare(mediaSource);
                mExoPlayer.setPlayWhenReady(true);
                if (position > 0){
                    mExoPlayer.seekTo(position);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error playAudio: " + e.getMessage());
            }
        }
    }

    private void setAudioInfo(JSONObject audioInfo) {
        Log.d(TAG, "setAudioInfo Called: " + audioInfo.toString());
        try {
            m_media_title = audioInfo.getString("media_title");
            m_media_source = audioInfo.getString("media_source");
            m_media_album_name = audioInfo.getString("media_album_name");
            m_media_stream_image = audioInfo.getString("media_stream_image");
        } catch (Exception e) {
            Log.e(TAG, "setAudioInfo " + e.toString());
        }
    }

    private void setAudioInfoNoPlay(JSONObject audioInfo) {
        // to set only audio info on mobile phone and let controll the player
        Log.d(TAG, "setAudioInfoNoPlay Called: " + audioInfo.toString());
        try {
            m_media_title = audioInfo.getString("media_title");
            m_media_source = audioInfo.getString("media_source");
            m_media_album_name = audioInfo.getString("media_album_name");
            m_media_stream_image = audioInfo.getString("media_stream_image");
            m_media_content_id = null;
            // try to stop and start ExoPlayer
            LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
            Intent pauseIntent = new Intent(AisCoreUtils.BROADCAST_EXO_PLAYER_COMMAND);
            pauseIntent.putExtra(AisCoreUtils.BROADCAST_EXO_PLAYER_COMMAND_TEXT, "play_empty");
            bm.sendBroadcast(pauseIntent);
        } catch (Exception e) {
            Log.e(TAG, "setAudioInfoNoPlay " + e.toString());
        }
    }


    private void pauseExoPlayer(Boolean pause){
        Log.d(TAG, "pauseExoPlayer Called: " + pause.toString());
        // pause / play
        try {
            mExoPlayer.setPlayWhenReady(!pause);
        } catch (Exception e) {
            Log.e(TAG, "Error pauseAudio: " + e.getMessage());
        }
    }

    private void stopExoPlayer(){
        Log.d(TAG, "stopExoPlayer Called ");
        try {
            mExoPlayer.stop();
        } catch (Exception e) {
            Log.e(TAG, "Error stopAudio: " + e.getMessage());
        }
    }

    private void pauseAudio(Boolean pause){
        Log.d(TAG, "pauseAudio Called: " + pause.toString());
        pauseExoPlayer(pause);
    }

    private void stopAudio(){
        Log.d(TAG, "stopAudio Called ");
        stopExoPlayer();
    }

    private void seekTo(long positionMs){
        mExoPlayer.seekTo(mExoPlayer.getCurrentPosition() + positionMs);
    }

    private void skipTo(long positionMs){
        mExoPlayer.seekTo(positionMs);
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

    private void setPlaybackPitch(float pitch){
        Log.d(TAG, "setPlaybackPitch Called ");
        PlaybackParameters pp = new  PlaybackParameters(1.0f, pitch);
        mExoPlayer.setPlaybackParameters(pp);
    }

    private void setPlayerShuffle(boolean Shuffle){
        // currently we are able to set Shuffle only on Spotify
    }

    private JSONObject getAudioStatus(){
        // TODO do this for Spotify too
        Log.d(TAG, "getAudioStatus Called ");
        JSONObject jState = new JSONObject();
        if (m_media_source.equals(AisCoreUtils.mAudioSourceSpotify)){
            // TODO

        } else {
            int state = 0;
            try {
                state = mExoPlayer.getPlaybackState();
            } catch (Exception e) {
                Log.e(TAG, "Error getAudioStatus: " + e.getMessage());
            }
            try {
                jState.put("currentStatus", state);
                jState.put("currentMedia", m_media_title);
                jState.put("playing", mExoPlayer.getPlayWhenReady());
                jState.put("currentVolume", getVolume());
                jState.put("duration", mExoPlayer.getDuration());
                jState.put("currentPosition", mExoPlayer.getCurrentPosition());
                jState.put("currentSpeed", mExoPlayer.getPlaybackParameters().speed);
                jState.put("media_source", m_media_source);
                jState.put("media_album_name", m_media_album_name);
                if (m_media_stream_image == null) {
                    jState.put("media_stream_image", "");
                } else {
                    jState.put("media_stream_image", m_media_stream_image);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return jState;
    }

    private void executeExoPlayerCommand(String command, String url) {
        if (command.equals("pause")){
            pauseAudio(true);
        } else if (command.equals("play")){
            pauseAudio(false);
        } else if (command.equals("play_audio")){
            playAudio(url,false,0);
        } else if (command.equals("stop")){
            stopAudio();
        } else if (command.equals("play_empty")){
            playAudio("asset:///1-second-of-silence.mp3",false,0);
        }
    }

    //
    // Exo Player notification
    private class DescriptionAdapter implements
            PlayerNotificationManager.MediaDescriptionAdapter {

        @Override
        public String getCurrentContentTitle(Player player) {
            return m_media_title;
        }

        @Nullable
        @Override
        public String getCurrentContentText(Player player) {
            if (m_media_source.equals(AisCoreUtils.mAudioSourceAndroid)){
                return "intro";
            }
            return m_media_source;
        }

        @Override
        public Bitmap getCurrentLargeIcon(Player player,
                                          PlayerNotificationManager.BitmapCallback callback) {
            // StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().permitAll().build());
            Log.e(TAG, "1 getCurrentLargeIcon: m_media_stream_image: " + m_media_stream_image);
            if (m_media_stream_image != null && !m_media_stream_image.equals(m_exo_player_last_media_stream_image)) {
                try {
                    URL url = new URL(m_media_stream_image);
                    Thread thread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try  {
                                m_exo_player_large_icon = BitmapFactory.decodeStream(url.openConnection().getInputStream());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
                    thread.start();
                    m_exo_player_last_media_stream_image = m_media_stream_image;
                } catch (Exception e) {
                    Log.e(TAG, "1 getCurrentLargeIcon: " + e.toString());
                }
            }
            Log.e(TAG, "2 getCurrentLargeIcon: " + m_media_stream_image);
            if (m_exo_player_large_icon == null) {
                return getSpeakerImage();
            }
            return m_exo_player_large_icon;
        }

        @Nullable
        @Override
        public PendingIntent createCurrentContentIntent(Player player) {
            //  open AIS app by clicking on notification
            Intent intent = new Intent(AisPanelService.this, BrowserActivityNative.class);
            PendingIntent contentPendingIntent = PendingIntent.getActivity(
                    AisPanelService.this,
                    0,
                    intent,
                    0);
            return contentPendingIntent;
        }

        @Override
        public String getCurrentSubText(Player player) {
            String subText = "";
            Config config = new Config(getApplicationContext());
            if (config.getHotWordMode()) {
                String hotword = config.getSelectedHotWord();
                int sensitivity = config.getSelectedHotWordSensitivity();
                subText = hotword.substring(0, 1).toUpperCase() + hotword.substring(1) + " " + sensitivity;
            }

            if (config.getReportLocationMode()) {
                subText = subText + " " + getString(R.string.title_notification_report_location);
            }
            return subText;
        }
    }

    private class customActionReceiver implements PlayerNotificationManager.CustomActionReceiver {
        @Override
        public Map<String, NotificationCompat.Action> createCustomActions(Context context, int instanceId) {
            Map<String, NotificationCompat.Action> actionMap = new HashMap<>();
            // STOP
            Intent intentAisStop = new Intent("ais_stop")
                    .setPackage(AisPanelService.this.getPackageName());
            PendingIntent pendingIntentAisStop = PendingIntent.getBroadcast(
                    AisPanelService.this,
                    instanceId,
                    intentAisStop,
                    PendingIntent.FLAG_CANCEL_CURRENT);

            NotificationCompat.Action actionStop = new NotificationCompat.Action(
                    R.drawable.ic_app_exit,
                    "ais_stop",
                    pendingIntentAisStop
            );
            actionMap.put("ais_stop",actionStop);

            // PREV
            Intent intentAisPrev = new Intent("ais_prev")
                    .setPackage(AisPanelService.this.getPackageName());
            PendingIntent pendingIntentAisPrev = PendingIntent.getBroadcast(
                    AisPanelService.this,
                    instanceId,
                    intentAisPrev,
                    PendingIntent.FLAG_CANCEL_CURRENT);


            NotificationCompat.Action actionPrev = new NotificationCompat.Action(
                    R.drawable.exo_icon_previous,
                    "ais_prev",
                    pendingIntentAisPrev
            );
            actionMap.put("ais_prev",actionPrev);


            // PLAY
            Intent intentAisPlay = new Intent("ais_play")
                    .setPackage(AisPanelService.this.getPackageName());
            PendingIntent pendingIntentPlay = PendingIntent.getBroadcast(
                    AisPanelService.this,
                    instanceId,
                    intentAisPlay,
                    PendingIntent.FLAG_CANCEL_CURRENT);


            NotificationCompat.Action actionPlay = new NotificationCompat.Action(
                    R.drawable.exo_icon_play,
                    "ais_play",
                    pendingIntentPlay
            );
            actionMap.put("ais_play",actionPlay);


            // PAUSE
            Intent intentAisPause = new Intent("ais_pause")
                    .setPackage(AisPanelService.this.getPackageName());
            PendingIntent pendingIntentPause = PendingIntent.getBroadcast(
                    AisPanelService.this,
                    instanceId,
                    intentAisPause,
                    PendingIntent.FLAG_CANCEL_CURRENT);


            NotificationCompat.Action actionPause = new NotificationCompat.Action(
                    R.drawable.exo_icon_pause,
                    "ais_pause",
                    pendingIntentPause
            );
            actionMap.put("ais_pause",actionPause);


            // NEXT
            Intent intentAisNext = new Intent("ais_next")
                    .setPackage(AisPanelService.this.getPackageName());
            PendingIntent pendingIntentAisNext = PendingIntent.getBroadcast(
                    AisPanelService.this,
                    instanceId,
                    intentAisNext,
                    PendingIntent.FLAG_CANCEL_CURRENT);


            NotificationCompat.Action actionNext = new NotificationCompat.Action(
                    R.drawable.exo_icon_next,
                    "ais_next",
                    pendingIntentAisNext
            );
            actionMap.put("ais_next",actionNext);


            // MIC ON
            Intent intentAisMic = new Intent("ais_mic")
                    .setPackage(AisPanelService.this.getPackageName());
            PendingIntent pendingIntentAisMic = PendingIntent.getBroadcast(
                    AisPanelService.this,
                    instanceId,
                    intentAisMic,
                    PendingIntent.FLAG_CANCEL_CURRENT);
            NotificationCompat.Action actionMic = new NotificationCompat.Action(
                    R.drawable.ais_icon_mic,
                    "ais_mic",
                    pendingIntentAisMic
            );
            actionMap.put("ais_mic",actionMic);


            return actionMap;


        }

        @Override
        public List<String> getCustomActions(Player player) {
            List<String> customActions = new ArrayList<>();
            customActions.add("ais_stop");
            customActions.add("ais_prev");
            if(player.getPlayWhenReady()) {
                customActions.add("ais_pause");
            }else{
                customActions.add("ais_play");
            }
            customActions.add("ais_next");

            customActions.add("ais_mic");

            return customActions;
        }

        @Override
        public void onCustomAction(Player player, String action, Intent intent) {
            if (action.equals("ais_play")) {
                pauseAudio(false);
                DomWebInterface.publishMessage("media_play", "media_player", getApplicationContext());
            } else if (action.equals("ais_pause")) {
                pauseAudio(true);
                DomWebInterface.publishMessage("media_pause", "media_player", getApplicationContext());
            } else if (action.equals("ais_prev")) {
                // publish prev command
                DomWebInterface.publishMessage("media_previous_track", "media_player", getApplicationContext());
            } else if (action.equals("ais_next")) {
                // publish next command
                DomWebInterface.publishMessage("media_next_track", "media_player", getApplicationContext());
            } else if (action.equals("ais_mic")) {
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
            } else if (action.equals("ais_stop")) {
                //
                mConfig.setAppDiscoveryMode(false);

                // 1. stop hot word service
                if(AisCoreUtils.isServiceRunning(getApplicationContext(), PorcupineService.class)){
                    mConfig.setHotWordMode(false);
                    Intent startAisApp = new Intent(getApplicationContext(), BrowserActivityNative.class);
                    startAisApp.setAction("exit_mic_service");
                    startAisApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(startAisApp);
                }
                // 2. stop location service
                if(AisCoreUtils.isServiceRunning(getApplicationContext(), AisLocationService.class)){
                    mConfig.setReportLocationMode(false);
                }

                // 3. stop audio service
                Intent stopIntent = new Intent(getApplicationContext(), AisPanelService.class);
                getApplicationContext().stopService(stopIntent);

            }
        }
    }

    private class customNotificationListener implements PlayerNotificationManager.NotificationListener{
//        @Override
//        public void onNotificationPosted(int notificationId, Notification notification, boolean ongoing) {
//            // call service.startForeground(notificationId, notification) if required
//        }
//        @Override
//        public void onNotificationCancelled(int notificationId, boolean dismissedByUser) {
//            if (dismissedByUser) {
//                // Do what the app wants to do when dismissed by the user.
//            }
//        }

    }

    private class customPlayerNotificationManager extends PlayerNotificationManager {


        public customPlayerNotificationManager(AisPanelService aisPanelService, String aisDomChannelId, int aisDomNotificationId, DescriptionAdapter descriptionAdapter, customNotificationListener customNotificationListener, AisPanelService.customActionReceiver customActionReceiver) {
            super(aisPanelService, aisDomChannelId, aisDomNotificationId, descriptionAdapter, customNotificationListener, customActionReceiver);
        }

        @Override
        protected int[] getActionIndicesForCompactView(List<String> actionNames, Player player) {
            int[] actionIndices = new int[2];
            // play / pause and mic
            actionIndices[0] = 2;
            actionIndices[1] = 4;
            return actionIndices;
        }
    }

}
