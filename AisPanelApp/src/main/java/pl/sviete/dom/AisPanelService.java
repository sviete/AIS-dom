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
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
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
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;

import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Locale;


import static pl.sviete.dom.AisCoreUtils.AIS_DOM_CHANNEL_ID;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_ON_END_SPEECH_TO_TEXT;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_ON_END_TEXT_TO_SPEECH;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_ON_START_SPEECH_TO_TEXT;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_ON_START_TEXT_TO_SPEECH;

public class AisPanelService extends Service implements TextToSpeech.OnInitListener, ExoPlayer.EventListener {
    public static final String BROADCAST_EVENT_URL_CHANGE = "BROADCAST_EVENT_URL_CHANGE";
    public static final String BROADCAST_EVENT_DO_STOP_TTS = "BROADCAST_EVENT_DO_STOP_TTS";
    public static final String BROADCAST_READ_THIS_TXT_NOW = "BROADCAST_READ_THIS_TXT_NOW";
    public static final String READ_THIS_TXT_MESSAGE_VALUE = "READ_THIS_TXT_MESSAGE_VALUE";
    public static final String BROADCAST_EVENT_KEY_BUTTON_PRESSED = "BROADCAST_EVENT_KEY_BUTTON_PRESSED";
    public static final String EVENT_KEY_BUTTON_PRESSED_VALUE = "EVENT_KEY_BUTTON_PRESSED_VALUE";
    public static final String BROADCAST_EVENT_CHANGE_CONTROLLER_MODE = "BROADCAST_EVENT_CHANGE_CONTROLLER_MODE";

    // STT
    public static final String  BROADCAST_ON_END_SPEECH_TO_TEXT_KEY_PRES = "BROADCAST_ON_END_SPEECH_TO_TEXT_KEY_PRES";
    public static final String  BROADCAST_ON_START_SPEECH_TO_TEXT_KEY_PRES = "BROADCAST_ON_START_SPEECH_TO_TEXT_KEY_PRES";

    // WIFI
    public static final String  BROADCAST_ON_WIFI_DISCONNECTED = "BROADCAST_ON_WIFI_DISCONNECTED";
    public static final String  BROADCAST_ON_WIFI_CONNECTED = "BROADCAST_ON_WIFI_CONNECTED";

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
    private String mReadThisTextWhenReady;

    public static ToneGenerator toneGenerator = null;

    //ExoPlayer -start
    private Handler mainHandler;
    private RenderersFactory renderersFactory;
    private BandwidthMeter bandwidthMeter;
    private LoadControl loadControl;
    private DataSource.Factory dataSourceFactory;
    private ExtractorsFactory extractorsFactory;
    private MediaSource mediaSource;
    private TrackSelection.Factory trackSelectionFactory;
    private SimpleExoPlayer exoPlayer;
    private TrackSelector trackSelector;
    private String m_media_title = null;
    private String m_media_source = AisCoreUtils.mAudioSourceAndroid;
    private String m_media_stream_image = null;
    private String m_media_album_name = null;
    private String m_media_content_id = null;
    private PlayerNotificationManager playerNotificationManager;


    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(
                    AIS_DOM_CHANNEL_ID,
                    "AI-Speaker Cast",
                    NotificationManager.IMPORTANCE_HIGH);

            NotificationManager manager = getSystemService(NotificationManager.class);
            assert manager != null;
            manager.createNotificationChannel(notificationChannel);
        }
    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

    }

    @Override
    public void onLoadingChanged(boolean isLoading) {

    }


    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        // not inform about intro and assistant change voice
        if (m_media_source.equals(AisCoreUtils.mAudioSourceAndroid)) {
            return;
        }

        JSONObject jState = new JSONObject();
        if (playbackState == ExoPlayer.STATE_ENDED) {
            // inform client that next song can be played
            try {
                jState.put("currentStatus", playbackState);
                jState.put("currentMedia", m_media_title);
                jState.put("playing", false);
                jState.put("giveMeNextOne", true);
                jState.put("duration", exoPlayer.getDuration());
                jState.put("currentPosition", exoPlayer.getCurrentPosition());
                jState.put("currentSpeed", exoPlayer.getPlaybackParameters().speed);
                jState.put("media_source", m_media_source);
                jState.put("media_album_name", m_media_album_name);
                jState.put("media_stream_image", m_media_stream_image);
            } catch(JSONException e) { e.printStackTrace(); }
            publishAudioPlayerStatus(jState.toString());
        } else if (playbackState == ExoPlayer.STATE_BUFFERING) {
            Log.v(TAG, "STATE_BUFFERING");
        } else if (playbackState == ExoPlayer.STATE_IDLE) {
            Log.v(TAG, "STATE_IDLE");
        } else {
            // inform client about status change
            // media source is null on intro audio
            publishAudioPlayerStatus(jState.toString());
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

    public void stopSpeechToText(){
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

        Notification notification = new NotificationCompat.Builder(this, AIS_DOM_CHANNEL_ID)
                .setContentTitle("AI-Speaker")
                .setContentText("ok odtwarzacz działa")
                .setSmallIcon(R.drawable.ic_ais_logo)
                .setContentIntent(pendingIntent)
                .build();

        startForeground(AisCoreUtils.AIS_DOM_NOTIFICATION_ID, notification);

        return super.onStartCommand(intent, flags, startId);

    }


    @Override
    public void onInit(int status) {
        Log.e(TAG, "AisPanelService onInit");
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
                        publishSpeechStatus("DONE");
                    }

                    @Override
                    public void onError(String utteranceId) {
                        Log.d(TAG, "TTS onError");
                        Intent intent = new Intent(BROADCAST_ON_END_TEXT_TO_SPEECH);
                        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
                        bm.sendBroadcast(intent);
                        publishSpeechStatus("ERROR");
                    }

                    @Override
                    public void onStart(String utteranceId) {
                        publishSpeechStatus("START");
                    }
                });

                if (mReadThisTextWhenReady != null){
                    processTTS(mReadThisTextWhenReady, AisCoreUtils.TTS_TEXT_TYPE_OUT);
                    mReadThisTextWhenReady = null;
                }

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
        Log.e(TAG, "onCreate Called");

        mConfig = new Config(getApplicationContext());
        // get current url without discovery
        currentUrl = mConfig.getAppLaunchUrl(false);
        // prepare the lock types we may use
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        fullWakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK |
                PowerManager.ON_AFTER_RELEASE |
                PowerManager.ACQUIRE_CAUSES_WAKEUP, "dom:fullWakeLock");
        partialWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dom:partialWakeLock");
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, "wifiLock");


        IntentFilter filter = new IntentFilter();
        filter.addAction(BROADCAST_EVENT_URL_CHANGE);
        filter.addAction(BROADCAST_EVENT_DO_STOP_TTS);
        filter.addAction(AisCoreUtils.BROADCAST_EVENT_ON_SPEECH_COMMAND);;
        filter.addAction(BROADCAST_READ_THIS_TXT_NOW);
        filter.addAction(BROADCAST_EVENT_KEY_BUTTON_PRESSED);
        filter.addAction(BROADCAST_ON_END_SPEECH_TO_TEXT);
        filter.addAction(BROADCAST_ON_START_SPEECH_TO_TEXT);
        filter.addAction(BROADCAST_ON_END_TEXT_TO_SPEECH);
        filter.addAction(BROADCAST_ON_START_TEXT_TO_SPEECH);
        filter.addAction(BROADCAST_EVENT_CHANGE_CONTROLLER_MODE);
        filter.addAction(BROADCAST_ON_START_SPEECH_TO_TEXT_KEY_PRES);
        filter.addAction(BROADCAST_ON_END_SPEECH_TO_TEXT_KEY_PRES);
        filter.addAction(BROADCAST_ON_WIFI_CONNECTED);
        filter.addAction(BROADCAST_ON_WIFI_DISCONNECTED);
        filter.addAction(BROADCAST_SPOTIFY_PLAY_AUDIO);
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
        exoPlayer = ExoPlayerFactory.newSimpleInstance(this, renderersFactory, trackSelector, loadControl);
        exoPlayer.addListener(this);
        playerNotificationManager = new PlayerNotificationManager(
                this,
                AIS_DOM_CHANNEL_ID,
                AisCoreUtils.AIS_DOM_NOTIFICATION_ID,
                new DescriptionAdapter());
        playerNotificationManager.setPlayer(exoPlayer);
        // omit skip previous and next actions
        playerNotificationManager.setUseNavigationActions(false);
        // omit fast forward action by setting the increment to zero
        playerNotificationManager.setFastForwardIncrementMs(0);
        // omit rewind action by setting the increment to zero
        playerNotificationManager.setRewindIncrementMs(0);
        // omit the stop action
        //playerNotificationManager.setStopAction(null);


        dataSourceFactory = new DefaultDataSourceFactory(getApplicationContext(), "AisDom");
        extractorsFactory = new DefaultExtractorsFactory();


        createTTS();
        //
        toneGenerator = new ToneGenerator(AudioManager.STREAM_SYSTEM, 100);
        //
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy Called");

        mConfig.stopListeningForConfigChanges();

        if (mTts != null) {
            mTts.stop();
            mTts.shutdown();
            mTts = null;
        }

        toneGenerator.release();

        if (exoPlayer != null) {
            playerNotificationManager.setPlayer(null);
            exoPlayer.release();
            exoPlayer = null;
        }

        Log.i(TAG, "destroy");
    }


    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();

            if (action.equals(BROADCAST_EVENT_URL_CHANGE)) {
                final String url = intent.getStringExtra(BROADCAST_EVENT_URL_CHANGE);
                if (!url.equals(currentUrl)) {
                    Log.d(TAG, "Url changed to " + url);
                    currentUrl = url;
                }
            } else if (action.equals(BROADCAST_EVENT_DO_STOP_TTS)) {
                Log.d(TAG, "Speech started, stoping the tts");
                stopSpeechToText();
            } else if (action.equals(AisCoreUtils.BROADCAST_EVENT_ON_SPEECH_COMMAND)) {
                Log.d(TAG, AisCoreUtils.BROADCAST_EVENT_ON_SPEECH_COMMAND + " going to publishSpeechCommand");
                final String command = intent.getStringExtra(AisCoreUtils.BROADCAST_EVENT_ON_SPEECH_COMMAND_TEXT);
                publishSpeechCommand(command);
            } else if (action.equals(BROADCAST_READ_THIS_TXT_NOW)) {
                Log.d(TAG, BROADCAST_READ_THIS_TXT_NOW + " going to processTTS");
                final String txtMessage = intent.getStringExtra(READ_THIS_TXT_MESSAGE_VALUE);
                processTTS(txtMessage, AisCoreUtils.TTS_TEXT_TYPE_OUT);
            } else if (action.equals(BROADCAST_EVENT_KEY_BUTTON_PRESSED)) {
                Log.d(TAG, BROADCAST_EVENT_KEY_BUTTON_PRESSED + " going to publishKeyEvent");
                final String key_event = intent.getStringExtra(EVENT_KEY_BUTTON_PRESSED_VALUE);
                publishKeyEvent(key_event);
            } else if (action.equals(BROADCAST_ON_START_SPEECH_TO_TEXT)) {
                Log.d(TAG, BROADCAST_ON_START_SPEECH_TO_TEXT + " turnDownVolume");
            } else if (action.equals(BROADCAST_ON_END_SPEECH_TO_TEXT)) {
                Log.d(TAG, BROADCAST_ON_END_SPEECH_TO_TEXT + " turnUpVolume");
            } else if (action.equals(BROADCAST_ON_START_TEXT_TO_SPEECH)) {
                Log.d(TAG, BROADCAST_ON_START_TEXT_TO_SPEECH + " turnDownVolume");
            } else if (action.equals(BROADCAST_ON_END_TEXT_TO_SPEECH)) {
                Log.d(TAG, BROADCAST_ON_END_TEXT_TO_SPEECH + " turnUpVolume");
            } else if (action.equals(BROADCAST_ON_END_SPEECH_TO_TEXT_KEY_PRES)) {
                Log.d(TAG, BROADCAST_ON_END_SPEECH_TO_TEXT_KEY_PRES + " onEndStt");
            } else if (action.equals(BROADCAST_ON_START_SPEECH_TO_TEXT_KEY_PRES)) {
                Log.d(TAG, BROADCAST_ON_START_SPEECH_TO_TEXT_KEY_PRES + " onStartStt");
            }
        }
    };


    //******** HTTP Related Functions

    private void configureHttp(){
        mHttpServer = new AsyncHttpServer();

        mHttpServer.get("/", (request, response) -> {
            Log.d(TAG, "request: " + request);
            JSONObject json = new JSONObject();
            if (mConfig.getAppDiscoveryMode()){
                try {
                    // the seme structure like in sonoff http://<device-ip>/cm?cmnd=status%205
                    json.put("Hostname", AisNetUtils.getHostName());
                    json.put("gate_id", AisCoreUtils.AIS_GATE_ID);
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
            } else {
                try {
                    // to enable search mqtt host by devices in network class C with default subnet masks 255.255.255.0
                    json.put("Hostname", AisNetUtils.getHostName());
                    json.put("gate_id", AisCoreUtils.AIS_GATE_ID);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            response.send(json);
            response.end();
        });

        mHttpServer.post("/command", (request, response) -> {
            Log.d(TAG, "command: " + request);
            JSONObject body = ((JSONObjectBody)request.getBody()).get();
            processCommand(body.toString());
            response.send("ok");
            response.end();
        });



        mHttpServer.post("/text_to_speech", (request, response) -> {
            Log.d(TAG, "text_to_speech: " + request);
            JSONObject body = ((JSONObjectBody)request.getBody()).get();
            processTTS(body.toString(), AisCoreUtils.TTS_TEXT_TYPE_OUT);
            response.send("ok");
            response.end();
        });

        // listen on port 8122
        mHttpServer.listen(8122);
    }


    //******** API Functions *****************

    private boolean processCommand(JSONObject commandJson) {
        Log.d(TAG, "processCommand Called " + commandJson.toString());
        try {
            if(commandJson.has("playAudioFullInfo")) {
                JSONObject audioInfo = commandJson.getJSONObject("playAudioFullInfo");
                setAudioInfo(audioInfo);
                String url = audioInfo.getString("media_content_id");
                boolean shuffle = audioInfo.getBoolean("setPlayerShuffle");
                long position = audioInfo.getLong("setMediaPosition");
                playAudio(url, shuffle, position);
            }
            else if(commandJson.has("playAudio")) {
                // depricated - switch to playAudioFullInfo
                String url = commandJson.getString("playAudio");
                playAudio(url, false, 0);
            }
            else if(commandJson.has("setAudioInfo")) {
                JSONObject audioInfo = commandJson.getJSONObject("setAudioInfo");
                setAudioInfo(audioInfo);
            }
            else if(commandJson.has("stopAudio")) {
                stopAudio();
            }
            else if(commandJson.has("pauseAudio")) {
                pauseAudio(commandJson.getBoolean("pauseAudio"));
            }
            else if(commandJson.has("getAudioStatus")) {
                JSONObject jState = getAudioStatus();
                publishAudioPlayerStatus(jState.toString());
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
            else if(commandJson.has("setPlaybackSpeed")) {
                setPlaybackSpeed(Float.parseFloat(commandJson.getString("setPlaybackSpeed")));
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


    private boolean processTTS(String text, String type) {
        Log.d(TAG, "processTTS Called: " + text);
        String textForReading = "";
        String voice = "";
        float pitch = 1;
        float rate = 1;

        // speak failed: not bound to TTS engine
        if (mTts == null){
            Log.w(TAG, "mTts == null");
            try {
                createTTS();
                mReadThisTextWhenReady = text;
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

        //
        try {
            JSONObject textJson = new JSONObject(text);
            try {
                if (textJson.has("text")) {
                    textForReading = textJson.getString("text");
                }
                if (textJson.has("pitch")) {
                    pitch = BigDecimal.valueOf(textJson.getDouble("pitch")).floatValue();
                    mTts.setPitch(pitch);
                }
                if (textJson.has("rate")) {
                    rate = BigDecimal.valueOf(textJson.getDouble("rate")).floatValue();
                    mTts.setSpeechRate(rate);
                }
                if (textJson.has("voice")) {
                    voice = textJson.getString("voice");
                    Voice voiceobj = new Voice(
                            voice, new Locale("pl_PL"),
                            Voice.QUALITY_HIGH,
                            Voice.LATENCY_NORMAL,
                            false,
                            null);
                    mTts.setVoice(voiceobj);
                } else {
                    String ttsVoice = mConfig.getAppTtsVoice();
                    Voice voiceobj = new Voice(
                            ttsVoice, new Locale("pl_PL"),
                            Voice.QUALITY_HIGH,
                            Voice.LATENCY_NORMAL,
                            false,
                            null);
                    mTts.setVoice(voiceobj);
                }
            }
            catch (JSONException ex) {
                Log.e(TAG, "Invalid JSON passed as a text: " + text);
                return false;
            }

        }
        catch (JSONException ex) {
            textForReading = text;
            String ttsVoice = mConfig.getAppTtsVoice();
            Voice voiceobj = new Voice(
                    ttsVoice, new Locale("pl_PL"),
                    Voice.QUALITY_HIGH,
                    Voice.LATENCY_NORMAL,
                    false,
                    null);
            mTts.setVoice(voiceobj);
        }

        //textToSpeech can only cope with Strings with < 4000 characters
        int dividerLimit = 3900;
        if(textForReading.length() >= dividerLimit) {
            int textLength = textForReading.length();
            ArrayList<String> texts = new ArrayList<String>();
            int count = textLength / dividerLimit + ((textLength % dividerLimit == 0) ? 0 : 1);
            int start = 0;
            int end = textForReading.indexOf(" ", dividerLimit);
            for(int i = 1; i<=count; i++) {
                texts.add(textForReading.substring(start, end));
                start = end;
                if((start + dividerLimit) < textLength) {
                    end = textForReading.indexOf(" ", start + dividerLimit);
                } else {
                    end = textLength;
                }
            }
            for(int i=0; i<texts.size(); i++) {
                mTts.speak(texts.get(i), TextToSpeech.QUEUE_ADD, null,"123");
            }
        } else {
            mTts.speak(textForReading, TextToSpeech.QUEUE_FLUSH, null,"456");
        }

        // TODO android.type.time TYPE_TIME and others TtsSpan

        Intent intent = new Intent(BROADCAST_ON_START_TEXT_TO_SPEECH);
        intent.putExtra(AisCoreUtils.TTS_TEXT, textForReading);
        intent.putExtra(AisCoreUtils.TTS_TEXT_TYPE, type);
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
                exoPlayer.stop();
                mediaSource = new ExtractorMediaSource(Uri.parse(streamUrl),
                        dataSourceFactory,
                        extractorsFactory,
                        mainHandler,
                        null);
                exoPlayer.prepare(mediaSource);
                exoPlayer.setPlayWhenReady(true);
                if (position > 0){
                    exoPlayer.seekTo(position);
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


    private void pauseExoPlayer(Boolean pause){
        Log.d(TAG, "pauseExoPlayer Called: " + pause.toString());
        // pause / play
        try {
            exoPlayer.setPlayWhenReady(!pause);
        } catch (Exception e) {
            Log.e(TAG, "Error pauseAudio: " + e.getMessage());
        }
    }

    private void stopExoPlayer(){
        Log.d(TAG, "stopExoPlayer Called ");
        try {
            exoPlayer.stop();
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
        exoPlayer.seekTo(exoPlayer.getCurrentPosition() + positionMs);
    }

    private void skipTo(long positionMs){
        exoPlayer.seekTo(positionMs);
    }



    private void setVolume(int vol){
        Log.d(TAG, "setVolume Called: " + vol);
        try {
            AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
            int maxVolume =  audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int volToSet = Math.max(Math.round((vol * maxVolume) / 100), 1);

            int maxSystemVolume =  audioManager.getStreamMaxVolume(AudioManager.STREAM_SYSTEM);
            int volSystemToSet = Math.max(Math.round(((volToSet * maxSystemVolume) / maxVolume)/2),1);

            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volToSet, AudioManager.FLAG_SHOW_UI);
            audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, volSystemToSet, AudioManager.FLAG_SHOW_UI);

        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    private void turnDownVolumeForSTT(){
        Log.d(TAG, "turnDownVolumeForSTT Called ");
        float vol = 0.2f;
        exoPlayer.setVolume(vol);
    }

    private void turnUpVolumeForSTT(){
        Log.d(TAG, "turnUpVolumeForSTT Called ");
        float vol = 0.6f;
        exoPlayer.setVolume(vol);
    }

    private void turnDownVolumeForTTS(){
        Log.d(TAG, "turnDownVolumeForTTS Called ");
        float vol = 0.2f;
        exoPlayer.setVolume(vol);
    }

    private void turnUpVolumeForTTS(){
        Log.d(TAG, "turnUpVolumeForTTS Called ");
        float vol = 1.0f;
        exoPlayer.setVolume(vol);
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
        exoPlayer.setPlaybackParameters(pp);
    }

    private void setPlaybackSpeed(float speed){

            PlaybackParameters pp = new PlaybackParameters(speed, 1.0f);
            exoPlayer.setPlaybackParameters(pp);
            Log.d(TAG, "setPlaybackSpeed speed: " + speed);
            JSONObject jState = new JSONObject();
            try {
                jState.put("currentSpeed", speed);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            publishAudioPlayerSpeed(jState.toString());
    }

    private void setPlayerShuffle(boolean Shuffle){
        // currently we are able to set Shuffle only on Spotify
    }

    private void publishAudioPlayerSpeed(String speed) {
        Log.d(TAG, "publishAudioPlayerSpeed: " + speed);
        DomWebInterface.publishMessage(speed, "player_speed");
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
                state = exoPlayer.getPlaybackState();
            } catch (Exception e) {
                Log.e(TAG, "Error getAudioStatus: " + e.getMessage());
            }
            try {
                jState.put("currentStatus", state);
                jState.put("currentMedia", m_media_title);
                jState.put("playing", exoPlayer.getPlayWhenReady());
                jState.put("currentVolume", getVolume());
                jState.put("duration", exoPlayer.getDuration());
                jState.put("currentPosition", exoPlayer.getCurrentPosition());
                jState.put("currentSpeed", exoPlayer.getPlaybackParameters().speed);
                jState.put("media_source", m_media_source);
                jState.put("media_album_name", m_media_album_name);
                jState.put("media_stream_image", m_media_stream_image);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return jState;
    }

    private void publishSpeechCommand(String message) {
        Log.d(TAG, "publishSpeechCommand " + message);
        DomWebInterface.publishMessage(message, "speech_command");
    }

    private void publishKeyEvent(String event) {
        Log.d(TAG, "publishKeyEvent " + event);
        // sample message: {"KeyCode":24,"Action":0,"DownTime":5853415}
        DomWebInterface.publishMessage(event, "key_command");
    }

    private void publishAudioPlayerStatus(String status) {
        Log.d(TAG, "publishAudioPlayerStatus: " + status);
        DomWebInterface.publishMessage(status, "player_status");
    }

    private void publishSpeechStatus(String status) {
        Log.d(TAG, "publishSpeechStatus: " + status);
        DomWebInterface.publishMessage(status, "speech_status");
    }


    // ExoPlauer notification
    private class DescriptionAdapter implements
            PlayerNotificationManager.MediaDescriptionAdapter {

        @Override
        public String getCurrentContentTitle(Player player) {
            return m_media_title;
        }

        @Nullable
        @Override
        public String getCurrentContentText(Player player) {
            return m_media_album_name;
        }

        @Nullable
        @Override
        public Bitmap getCurrentLargeIcon(Player player,
                                          PlayerNotificationManager.BitmapCallback callback) {
            // m_media_stream_image
            Bitmap largeIcon = null;
            return largeIcon;
        }

        @Nullable
        @Override
        public PendingIntent createCurrentContentIntent(Player player) {
            int window = player.getCurrentWindowIndex();
            return null;
        }
    }

}
