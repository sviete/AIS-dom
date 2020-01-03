package pl.sviete.dom;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
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
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpPost;
import com.koushikdutta.async.http.body.JSONObjectBody;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;


import static pl.sviete.dom.AisCoreUtils.BROADCAST_ON_END_SPEECH_TO_TEXT;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_ON_END_TEXT_TO_SPEECH;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_ON_START_SPEECH_TO_TEXT;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_ON_START_TEXT_TO_SPEECH;

public class AisPanelService extends Service implements TextToSpeech.OnInitListener, ExoPlayer.EventListener {
    private static final int ONGOING_NOTIFICATION_ID = 1;
    public static final String BROADCAST_EVENT_URL_CHANGE = "BROADCAST_EVENT_URL_CHANGE";
    public static final String BROADCAST_EVENT_DO_STOP_TTS = "BROADCAST_EVENT_DO_STOP_TTS";
    public static final String BROADCAST_READ_THIS_TXT_NOW = "BROADCAST_READ_THIS_TXT_NOW";
    public static final String READ_THIS_TXT_MESSAGE_VALUE = "READ_THIS_TXT_MESSAGE_VALUE";
    public static final String BROADCAST_EVENT_KEY_BUTTON_PRESSED = "BROADCAST_EVENT_KEY_BUTTON_PRESSED";
    public static final String EVENT_KEY_BUTTON_PRESSED_VALUE = "EVENT_KEY_BUTTON_PRESSED_VALUE";
    public static final String BROADCAST_EVENT_CHANGE_CONTROLLER_MODE = "BROADCAST_EVENT_CHANGE_CONTROLLER_MODE";
    public static final String EVENT_CHANGE_CONTROLLER_MODE_VALUE = "EVENT_CHANGE_CONTROLLER_MODE_VALUE";

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


    // MDNS
    private NsdManager.RegistrationListener mRegistrationListenerAisDom;
    private NsdManager mNsdManager;
    private String mServiceName;


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
        Log.i(TAG, "AisPanelService onStartCommand");
        try {
            String action = intent.getAction();
            Log.i(TAG, "AisPanelService onStartCommand, action: " + action);
        } catch (Exception e) {
            Log.i(TAG, "AisPanelService onStartCommand no action");
        }
        return START_STICKY;
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

        dataSourceFactory = new DefaultDataSourceFactory(getApplicationContext(), "AisDom");
        extractorsFactory = new DefaultExtractorsFactory();
        playIntro();


        createTTS();
        //
        toneGenerator = new ToneGenerator(AudioManager.STREAM_SYSTEM, 100);
        //
        startForeground();

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

        Log.i(TAG, "destroy");
    }

    public void unRegisterMdnsServices(){
        Log.i(TAG, "MDNS unRegisterMdnsServices");
        if (mRegistrationListenerAisDom != null) {
            try {
                mNsdManager.unregisterService(mRegistrationListenerAisDom);
                mRegistrationListenerAisDom = null;
            } catch (Exception e){
                Log.e(TAG, "MDNS unRegisterMdnsServices error " +  e.toString());
            }

        }
    }

    public void registerMdnsServices(Context context) {
        Log.i(TAG, "MDNS registerMdnsServices");
        String AisDomHostName = AisNetUtils.getHostName();
        // AIS dom
        try {
            NsdServiceInfo serviceInfofoAisDom = new NsdServiceInfo();
            serviceInfofoAisDom.setServiceName(AisDomHostName);
            serviceInfofoAisDom.setServiceType("_ais-dom._tcp");
            serviceInfofoAisDom.setPort(8180);
            serviceInfofoAisDom.setAttribute("gate_id", AisCoreUtils.AIS_GATE_ID);
            serviceInfofoAisDom.setAttribute("company_url", "https://ai-speaker.com");

            if (mRegistrationListenerAisDom == null)
                initializeRegistrationListenerAisDom();

            mNsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
            mNsdManager.registerService(serviceInfofoAisDom, NsdManager.PROTOCOL_DNS_SD, mRegistrationListenerAisDom);
        } catch (Exception e){
            Log.e(TAG, "MDNS registerMdnsServices error " +  e.toString());
        }
    }

    private void initializeRegistrationListenerAisDom() {
        mRegistrationListenerAisDom = new NsdManager.RegistrationListener() {

            @Override
            public void onServiceRegistered(NsdServiceInfo NsdServiceInfo) {
                // Save the service name.  Android may have changed it in order to
                // resolve a conflict, so update the name you initially requested
                // with the name Android actually used.
                mServiceName = NsdServiceInfo.getServiceName();
                Log.i(TAG, "MDNS onServiceRegistered " + mServiceName);
            }

            @Override
            public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                // Registration failed!  Put debugging code here to determine why.
                Log.i(TAG, "MDNS onServiceUnregistered");
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo arg0) {
                // Service has been unregistered.  This only happens when you call
                // NsdManager.unregisterService() and pass in this listener.
                Log.i(TAG, "MDNS onServiceUnregistered");
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                // Unregistration failed.  Put debugging code here to determine why.
                Log.i(TAG, "MDNS onUnregistrationFailed");
            }
        };
    }

    private void startForeground(){
        Log.d(TAG, "startForeground Called");
        Context context = getApplicationContext();
        Intent notificationIntent = new Intent(context, BrowserActivityNative.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, notificationIntent, 0);

        Notification notification = null;

        if (notification != null) {
            notification.flags |= Notification.FLAG_AUTO_CANCEL;
            startForeground(ONGOING_NOTIFICATION_ID, notification);
        }
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
            } else if (action.equals(BROADCAST_EVENT_CHANGE_CONTROLLER_MODE)) {
                Log.d(TAG, BROADCAST_EVENT_CHANGE_CONTROLLER_MODE + " onChangeRemoteControllerMode");
                final String newControlMode = intent.getStringExtra(EVENT_CHANGE_CONTROLLER_MODE_VALUE);
            } else if (action.equals(BROADCAST_ON_END_SPEECH_TO_TEXT_KEY_PRES)) {
                Log.d(TAG, BROADCAST_ON_END_SPEECH_TO_TEXT_KEY_PRES + " onEndStt");
            } else if (action.equals(BROADCAST_ON_START_SPEECH_TO_TEXT_KEY_PRES)) {
                Log.d(TAG, BROADCAST_ON_START_SPEECH_TO_TEXT_KEY_PRES + " onStartStt");
            } else if (action.equals(BROADCAST_ON_WIFI_CONNECTED)) {
                Log.d(TAG, BROADCAST_ON_WIFI_CONNECTED + " onWiFiConnected");
            } else if (action.equals(BROADCAST_ON_WIFI_DISCONNECTED)) {
                Log.d(TAG, BROADCAST_ON_WIFI_DISCONNECTED + " onWiFiDisconnected");
            } else if (action.equals(BROADCAST_SPOTIFY_PLAY_AUDIO)) {
                Log.d(TAG, BROADCAST_SPOTIFY_PLAY_AUDIO + " spotifyOnPlayAudio");
                final String audio_id = intent.getStringExtra(BROADCAST_SPOTIFY_PLAY_AUDIO_ID_VALUE);
                final boolean shuffle = intent.getBooleanExtra(BROADCAST_SPOTIFY_PLAY_AUDIO_SET_SHUFFLE, false);
                final long seek_to = intent.getLongExtra(BROADCAST_SPOTIFY_PLAY_AUDIO_SET_SEEK_TO, 0);
            } else if (action.equals("com.spotify.music.playbackstatechanged")) {
                Log.d(TAG, "com.spotify.music.playbackstatechanged");
            } else if (action.equals("com.spotify.music.queuechanged")) {
                Log.d(TAG, "com.spotify.music.queuechanged");
            } else if (action.equals("com.spotify.music.metadatachanged")) {
                Log.d(TAG, "com.spotify.music.metadatachanged");
            }
        }
    };


    //******** HTTP Related Functions

    private void configureHttp(){
        mHttpServer = new AsyncHttpServer();

        mHttpServer.get("/", new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
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
            }
        });



        mHttpServer.post("/text_to_speech", new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
                Log.d(TAG, "text_to_speech: " + request);
                JSONObject body = ((JSONObjectBody)request.getBody()).get();
                processTTS(body.toString(), AisCoreUtils.TTS_TEXT_TYPE_OUT);
                response.send("ok");
                response.end();
            }
        });

        // listen on port 8122
        mHttpServer.listen(8122);
    }

    public static void publishIpToCloud(String IpAddress, Context context, String netType){
        Log.d("publishIpToCloud", "publishIpToCloud Called: " + IpAddress);
        String url = AisCoreUtils.getAisDomCloudWsUrl(false) + "/gate_ip_info";
        // prepare the json message
        JSONObject json = new JSONObject();
        try {
            json.put("local_ip", IpAddress);
            //settings get secure android_id
            if (AisCoreUtils.AIS_GATE_ID == null) {
                AisCoreUtils.AIS_GATE_ID = "dom-" + Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            }
            json.put("gate_id",  AisCoreUtils.AIS_GATE_ID);
            json.put("net_type",  netType);
            Log.d("publishIpToCloud", "local_ip: " + IpAddress);
            Log.d("publishIpToCloud", "gate_id: " + AisCoreUtils.AIS_GATE_ID);
        } catch (JSONException e) {
            Log.e("publishIpToCloud", e.toString());
        }
        // do the simple HTTP post
        AsyncHttpPost post = new AsyncHttpPost(url);
        JSONObjectBody body = new JSONObjectBody(json);
        post.addHeader("Content-Type", "application/json");
        post.setBody(body);
        AsyncHttpClient.getDefaultInstance().executeJSONObject(post, null);

    }


    //******** API Functions *****************


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

    // publish the text to hass - this text will be displayed in app
    // and send back to frame to read...
    public static void publishSpeechText(String message) {
        Log.d("TTS", "publishSpeechText " + message);
        DomWebInterface.publishMessage(message, "speech_text");
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

    private void publishAudioPlayerSpeed(String speed) {
        Log.d(TAG, "publishAudioPlayerSpeed: " + speed);
        DomWebInterface.publishMessage(speed, "player_speed");
    }


    private void publishSpeechStatus(String status) {
        Log.d(TAG, "publishSpeechStatus: " + status);
        DomWebInterface.publishMessage(status, "speech_status");
    }

    // AUDIO
    private void playIntro(){
        // to prevent the play next on gate
        m_media_source = AisCoreUtils.mAudioSourceAndroid;

        exoPlayer.stop();
        exoPlayer.prepare(mediaSource);
        exoPlayer.setPlayWhenReady(true);

        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // text info after 9sec
                processTTS("Uruchamianie systemu Asystent domowy, poczekaj.", AisCoreUtils.TTS_TEXT_TYPE_OUT);
            }
        }, 9000);
    }

    private void speakVoiceIntro(){
        String text = "Raz, dwa, raz, dwa, trzy. Próba głosu asystenta.";
        Random random = new Random();
        List<String> voiceTest = Arrays.asList(
                "Bzyczy bzyg znad Bzury zbzikowane bzdury, bzyczy bzdury, bzdurstwa bzdurzy i nad Bzurą w bzach bajdurzy, bzyczy bzdury, bzdurnie bzyka, bo zbzikował i ma bzika!",
                "Centrum handlowe to tutaj punkt zborny, to tutaj dobierają do butów torby, temat paznokcia pozornie pozorny, jej twarz to miraż jak makijaż upiorny.",
                "Chrząszcz brzmi w trzcinie w Szczebrzeszynie, w szczękach chrząszcza trzeszczy miąższ. Czcza szczypawka czka w Szczecinie. Chrząszcza szczudłem przechrzcił wąż, strząsa skrzydła z dżdżu, a trzmiel w puszczy, tuż przy Pszczynie, straszny wszczyna szum.",
                "Cóż potrzeba strzelcowi do zestrzelenia cietrzewia drzemiącego w dżdżysty dzień na strzelistym drzewie.",
                "Czesał czyżyk czarny koczek, czyszcząc w koczku każdy loczek. Po czym przykrył koczek toczkiem, lecz część loczków wyszła boczkiem.",
                "Dżdżystym rankiem gżegżółki i piegże, zamiast wziąć się za dżdżownice, nażarły się na czczo miąższu rzeżuchy i rzędem rzygały do rozżarzonej brytfanny.",
                "Gdy Pomorze nie pomoże, to pomoże może morze, a gdy morze nie pomoże to pomoże może las.",
                "Hasał huczek z tłuczkiem wnuczka i niechcący huknął żuczka. Ale heca... – wnuczek mruknął i z hurkotem w hełm się stuknął. Leży żuczek, leży wnuczek, a pomiędzy nimi tłuczek. Stąd dla huczka jest nauczka by nie hasać z tłuczkiem wnuczka.",
                "Mała muszka spod Łopuszki chciała mieć różowe nóżki – różdżką nóżki czarowała, lecz wciąż nóżki czarne miała. – Po cóż czary, moja muszko? Ruszże móżdżkiem, a nie różdżką! Wyrzuć wreszcie różdżkę wróżki i unurzaj w różu nóżki!",
                "My indywidualiści wyindywidualizowaliśmy się z rozentuzjazmowanego tłumu, który oklaskiwał przeintelektualizowane i przeliteraturalizowane dzieło.",
                "Raz w szuwarach się zaszywszy w jednym szyku wyszły trzy wszy. Po chwili się rozmnożywszy ruch w szuwarach stał się żywszy.",
                "Szedł Mojżesz przez morze, jak żniwiarz przez zboże, a za nim przez morze trzy cytrzystki szły. Paluszki cytrzystek nie mogą być duże gdyż w strunach cytry uwięzłyby.",
                "Warzy żaba smar, pełen smaru gar, z wnętrza gara bucha para, z pieca bucha żar, smar jest w garze, gar na żarze, wrze na żarze smar.",
                "W gąszczu szczawiu we Wrzeszczu klaszczą kleszcze na deszczu, szepcze szczygieł w szczelinie, szczeka szczeniak w Szczuczynie, piszczy pszczoła pod Pszczyną, świszcze świerszcz pod leszczyną, a trzy pliszki i liszka taszczą płaszcze w Szypliszkach.",
                "W grząskich trzcinach i szuwarach kroczy jamnik w szarawarach, szarpie kłącza oczeretu i przytracza do beretu, ważkom pęki skrzypu wręcza, traszkom suchych trzcin naręcza, a gdy zmierzchać się zaczyna z jaszczurkami sprzeczkę wszczyna, po czym znika w oczerecie w szarawarach i berecie.",
                "W krzakach rzekł do trznadla trznadel: – Możesz mi pożyczyć szpadel? Muszę nim przetrzebić chaszcze, bo w nich straszą straszne paszcze. Odrzekł na to drugi trznadel: – Niepotrzebny, trznadlu, szpadel! Gdy wytrzeszczysz oczy w chaszczach, z krzykiem pierzchnie każda, paszcza!",
                "W wysuszonych sczerniałych trzcinowych szuwarach sześcionogi szczwany trzmiel bezczelnie szeleścił w szczawiu trzymając w szczękach strzęp szczypiorku i często trzepocąc skrzydłami.",
                "Wróbelek Walerek miał mały werbelek, werbelek Walerka miał mały felerek, felerek werbelka naprawił Walerek, Walerek wróbelek na werbelku swym grał.",
                "Z czeskich strzech szło Czechów trzech. Gdy nadszedł zmierzch, pierwszego w lesie zagryzł zwierz, bez śladu drugi w gąszczach sczezł, a tylko trzeci z Czechów trzech osiągnął marzeń kres.");
        int index = random.nextInt(voiceTest.size());
        text = text + voiceTest.get(index);
        processTTS(text, AisCoreUtils.TTS_TEXT_TYPE_OUT);
    }

}
