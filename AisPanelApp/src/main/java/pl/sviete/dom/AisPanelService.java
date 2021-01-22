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
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
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

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.Volley;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;
import com.google.android.exoplayer2.MediaItem;

import com.koushikdutta.async.http.body.JSONObjectBody;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;

import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


import pl.sviete.dom.data.DomCustomRequest;

import static pl.sviete.dom.AisCoreUtils.AIS_DOM_CHANNEL_ID;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_CAST_COMMAND;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_EXO_PLAYER_COMMAND;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_ON_END_SPEECH_TO_TEXT_MOB;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_ON_END_TEXT_TO_SPEECH;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_ON_START_SPEECH_TO_TEXT_MOB;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_ON_START_TEXT_TO_SPEECH;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_SERVICE_SAY_IT;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_SAY_IT_TEXT;
import static pl.sviete.dom.AisCoreUtils.GO_TO_HA_APP_VIEW_INTENT_EXTRA;


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

    //ExoPlayer -start
    private SimpleExoPlayer mExoPlayer;
    public static String m_media_title = null;
    public static String m_media_source = AisCoreUtils.mAudioSourceAndroid;
    public static String m_media_stream_image = "ais";
    public static String m_media_album_name = null;
    private PlayerNotificationManager playerNotificationManager;

    // cast
    private SimpleExoPlayer mCastExoPlayer;
    private PlayerNotificationManager mCastPlayerNotificationManager;
    public static String m_cast_media_title = null;
    public static String m_cast_media_source = AisCoreUtils.mAudioSourceAndroid;
    public static String m_cast_media_stream_image = null;
    public static String m_cast_media_album_name = null;
    CastDescriptionAdapter mCasttDescriptionAdapter  = new CastDescriptionAdapter();
    // Create Handler for main thread (can be reused).
    Handler mMainThreadHandler = new Handler(Looper.getMainLooper());


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

        // Go to frame
        Intent goToAppView = new Intent(getApplicationContext(), BrowserActivityNative.class);
        int iUniqueId = (int) (System.currentTimeMillis() & 0xfffffff);
        goToAppView.putExtra(GO_TO_HA_APP_VIEW_INTENT_EXTRA, "/aisaudio");
        goToAppView.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), iUniqueId, goToAppView, PendingIntent.FLAG_UPDATE_CURRENT);


        String aisTitle = "Player";

        Notification serviceNotification = new NotificationCompat.Builder(this, AIS_DOM_CHANNEL_ID)
                .setContentTitle("AIS dom")
                .setContentText(aisTitle)
                .setSmallIcon(R.drawable.ic_ais_logo)
                .setContentIntent(pendingIntent)
                .setSound(null)
                .build();

        startForeground(AisCoreUtils.AIS_DOM_NOTIFICATION_ID, serviceNotification);


        // play intro - to have notification player
        playAudio();

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

        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(this);
        bm.registerReceiver(mBroadcastReceiver, filter);

        // http api server
        configureHttp();

        //ExoPlayer
        mExoPlayer = new SimpleExoPlayer.Builder(getApplicationContext()).build();
        mExoPlayer.addListener(this);
        playerNotificationManager = new customPlayerNotificationManager(
                this,
                AIS_DOM_CHANNEL_ID,
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
        String MEDIA_SESSION_TAG = "ais";
        MediaSessionCompat mediaSession = new MediaSessionCompat(this, MEDIA_SESSION_TAG);
        mediaSession.setActive(true);
        playerNotificationManager.setMediaSessionToken(mediaSession.getSessionToken());

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
            } else if (action.equals(BROADCAST_ON_START_SPEECH_TO_TEXT_MOB)) {
                Log.d(TAG, BROADCAST_ON_START_SPEECH_TO_TEXT_MOB + " turnDownVolume");
            } else if (action.equals(BROADCAST_ON_END_SPEECH_TO_TEXT_MOB)) {
                Log.d(TAG, BROADCAST_ON_END_SPEECH_TO_TEXT_MOB + " turnUpVolume");
            } else if (action.equals(BROADCAST_ON_START_TEXT_TO_SPEECH)) {
                Log.d(TAG, BROADCAST_ON_START_TEXT_TO_SPEECH + " turnDownVolume");
            } else if (action.equals(BROADCAST_ON_END_TEXT_TO_SPEECH)) {
                Log.d(TAG, BROADCAST_ON_END_TEXT_TO_SPEECH + " turnUpVolume");
            } else if (action.equals(BROADCAST_EXO_PLAYER_COMMAND)){
                final String command = intent.getStringExtra(AisCoreUtils.BROADCAST_EXO_PLAYER_COMMAND_TEXT);
                executeExoPlayerCommandOnAis(command);
            }  else if (action.equals(BROADCAST_CAST_COMMAND)){
                final String command = intent.getStringExtra(AisCoreUtils.BROADCAST_CAST_COMMAND_TEXT);
                playCastMedia(command);
            } else if (action.equals(BROADCAST_ON_AIS_REQUEST)) {
                Log.d(TAG, BROADCAST_ON_AIS_REQUEST);
                if (intent.hasExtra("aisRequest")){
                    String aisRequest = intent.getStringExtra("aisRequest");
                    if (aisRequest.equals("micOn")) {
                        onStartStt();
                    } else if (aisRequest.equals("micOff")) {
                        AisCoreUtils.mSpeech.stopListening();
                    } else if (aisRequest.equals("playAudio")) {
                        final String audioUrl = intent.getStringExtra("url");
                        playCastMedia(audioUrl);
                    } else if (aisRequest.equals("findPhone")) {
                        // set audio volume to 100
                        setVolume(100);
                        // play
                        playCastMedia("asset:///find_my_phone.mp3");
                    } else if (aisRequest.equals("stopAudio")) {
                        stopAudio();
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
            String text_to_say = "";
            try {
                text_to_say = body.getString("text");
                processTTS(text_to_say);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });
        mHttpServer.post("/text_to_speech", new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
                Log.d(TAG, "text_to_speech: " + request);
                JSONObject body = ((JSONObjectBody)request.getBody()).get();
                processTTS(body);
                response.send("ok");
                response.end();
            }
        });

        mHttpServer.get("/text_to_speech", new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
                String text = "text_to_speech";
                try {
                    Log.d(TAG, "text_to_speech: " + request.getQuery().toString());
                    text = request.getQuery().getString("text");
                    JSONObject json = new JSONObject();
                    if (text == null){
                        text = "Nie wiem co mam powiedzieć";
                    } else json.put("text", text);
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
            }
        });

        // show cast media player info
        mHttpServer.get("/audio_status", new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
                Log.d(TAG, "request: " + request);
                JSONObject jState = new JSONObject();
                try {
                    jState.put("currentStatus", 0);
                    jState.put("currentMedia", m_cast_media_title);
                    jState.put("playing", false);
                    jState.put("currentVolume", getVolume());
                    jState.put("duration",0);
                    jState.put("currentPosition", 0);
                    jState.put("currentSpeed", 0);
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
            }
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
        Map<String, String> heders = new HashMap<String, String>();
        heders.put("Content-Type", "application/json");
        DomCustomRequest jsonObjectRequest = new DomCustomRequest(Request.Method.POST, url, heders, audioInfo.toString(), new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            setCastMediaInfo(response);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e(TAG, "getAudioInfoFromCloud: " + error.toString());
                    }
                }
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
        String textForReading = "";
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

    // play audio on local exo just to show the status to user
    private void playCastMedia(String streamUrl){
        String ais_cast_channel = "ais_cast_channel";
        int ais_cast_notification_id = 8888888;
        if (mCastExoPlayer != null) {
            try {
                mCastExoPlayer.stop();
                mCastPlayerNotificationManager.setPlayer(null);
                mCastExoPlayer.release();
                mCastExoPlayer = null;
            } catch (Exception e) {
                Log.e(TAG, "Error stop playCastMedia: " + e.getMessage());
            }
        }
        try {
            mCastExoPlayer = new SimpleExoPlayer.Builder(getApplicationContext()).build();
            MediaItem mediaItem = MediaItem.fromUri(streamUrl);
            mCastExoPlayer.setMediaItem(mediaItem);
            mCastExoPlayer.prepare();
            mCastPlayerNotificationManager = PlayerNotificationManager.createWithNotificationChannel(
                    getApplicationContext(),
                    ais_cast_channel,
                    R.string.playback_channel_name,
                    R.string.cast_channel_descr,
                    ais_cast_notification_id,
                    mCasttDescriptionAdapter
            );

            mCastPlayerNotificationManager.setPlayer(mCastExoPlayer);
            mCastExoPlayer.setPlayWhenReady(true);
            mCastPlayerNotificationManager.setUseNextAction(false);
            mCastPlayerNotificationManager.setUsePreviousAction(false);
            mCastPlayerNotificationManager.setUseStopAction(true);
            mCastPlayerNotificationManager.setRewindIncrementMs(0);
            mCastPlayerNotificationManager.setFastForwardIncrementMs(0);
            mCastPlayerNotificationManager.setSmallIcon(R.drawable.ais_icon_cast);
            //
        } catch (Exception e) {
            Log.e(TAG, "Error playCastMedia: " + e.getMessage());
        }
    }

    private void invalidatePlayerNotification() {
        m_media_title = "AI-Speaker";
        m_media_source = AisCoreUtils.mAudioSourceAndroid;
        m_media_stream_image = "ais";
        m_media_album_name = " ";
        // try to stop and start ExoPlayer to refresh the status
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
        Intent pauseIntent = new Intent(AisCoreUtils.BROADCAST_EXO_PLAYER_COMMAND);
        pauseIntent.putExtra(AisCoreUtils.BROADCAST_EXO_PLAYER_COMMAND_TEXT, "play_empty");
        bm.sendBroadcast(pauseIntent);
    }
    private void refreshAudioNotification() {
        Log.e(TAG, "get info ");
        if (AisCoreUtils.AIS_REMOTE_GATE_ID != null && AisCoreUtils.AIS_REMOTE_GATE_ID.startsWith("dom-")) {
            String url = AisCoreUtils.getAisDomCloudWsUrl(true) + "get_audio_full_info";
            JSONObject audioInfo = new JSONObject();
            try {
                audioInfo.put("audio_url", AisCoreUtils.AIS_REMOTE_GATE_ID);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            Map<String, String> heders = new HashMap<String, String>();
            heders.put("Content-Type", "application/json");
            DomCustomRequest jsonObjectRequest = new DomCustomRequest(Request.Method.POST, url, heders, audioInfo.toString(), new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    try {
                        setAudioInfo(response);
                        playerNotificationManager.invalidate();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "refreshAudioNotification: " + error.toString());
                }
            }
            ) {
            };
            RequestQueue requestQueue = Volley.newRequestQueue(getApplicationContext());
            requestQueue.add(jsonObjectRequest);
        }
    }

    private void playAudio(){
        String streamUrl = "asset:///1-second-of-silence.mp3";
        // String streamUrl = "asset:///find_my_phone.mp3";
        try {
            mExoPlayer.stop();
            MediaItem mediaItem = MediaItem.fromUri(streamUrl);
            mExoPlayer.setMediaItem(mediaItem);
            mExoPlayer.prepare();
            mExoPlayer.setPlayWhenReady(true);
        } catch (Exception e) {
            Log.e(TAG, "Error playAudio: " + e.getMessage());
        }
    }


    private void setCastMediaInfo(JSONObject audioInfo) {
        Log.d(TAG, "setAudioInfo Called: " + audioInfo.toString());
        try {
            m_cast_media_title = audioInfo.getString("media_title");
            m_cast_media_source = audioInfo.getString("media_source");
            m_cast_media_album_name = audioInfo.getString("media_album_name");
            m_cast_media_stream_image = audioInfo.getString("media_stream_image");
        } catch (Exception e) {
            Log.e(TAG, "setCastMediaInfo " + e.toString());
        }
    }

    private void setAudioInfo(JSONObject audioInfo) {
        Log.d(TAG, "setAudioInfo Called: " + audioInfo.toString());
        try {
            m_media_title = audioInfo.getString("media_title");
            m_media_source = audioInfo.getString("media_source");
            m_media_album_name = audioInfo.getString("media_album_name");
            m_media_stream_image = audioInfo.getString("media_stream_image");
            // try to stop and start ExoPlayer to refresh the status
            LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
            Intent pauseIntent = new Intent(AisCoreUtils.BROADCAST_EXO_PLAYER_COMMAND);
            pauseIntent.putExtra(AisCoreUtils.BROADCAST_EXO_PLAYER_COMMAND_TEXT, "play_empty");
            bm.sendBroadcast(pauseIntent);
        } catch (Exception e) {
            Log.e(TAG, "setAudioInfo " + e.toString());
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


    //
    private void executeExoPlayerCommandOnAis(String command) {
        if (command.equals("pause")){
            pauseAudio(true);
        } else if (command.equals("play")){
            pauseAudio(false);
        } else if (command.equals("play_audio")){
            playAudio();
        } else if (command.equals("stop")){
            stopAudio();
        } else if (command.equals("play_empty")){
            playAudio();
        } else if (command.equals("refresh_notification")) {
            invalidatePlayerNotification();
            // give 3 seconds
            mMainThreadHandler.postDelayed(new Runnable() {
                public void run() {
                    refreshAudioNotification();
                }
            }, 2000);

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

            Thread thread = new Thread(() -> {
                try {
                    if (m_media_stream_image.equals("ais")) {
                        callback.onBitmap(getSpeakerImage());
                    } else {
                        URL url = new URL(m_media_stream_image);
                        Bitmap bitmap = BitmapFactory.decodeStream(url.openConnection().getInputStream());
                        callback.onBitmap(bitmap);
                    }
                } catch (Exception e) {
                    callback.onBitmap(getSpeakerImage());
                    e.printStackTrace();
                }
            });
            thread.start();
            return getSpeakerImage();
        }

        @Nullable
        @Override
        public PendingIntent createCurrentContentIntent(Player player) {
            // Go to frame - open AIS app by clicking on notification
            Intent goToAppView = new Intent(AisPanelService.this, BrowserActivityNative.class);
            int iUniqueId = (int) (System.currentTimeMillis() & 0xfffffff);
            goToAppView.putExtra(GO_TO_HA_APP_VIEW_INTENT_EXTRA, "/aisaudio");
            goToAppView.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent contentPendingIntent = PendingIntent.getActivity(AisPanelService.this,iUniqueId, goToAppView,PendingIntent.FLAG_UPDATE_CURRENT);
            return contentPendingIntent;
        }

        @Override
        public String getCurrentSubText(Player player) {
            String subText = "";
            Config config = new Config(getApplicationContext());
            if (config.getHotWordMode()) {
                String hotword = config.getSelectedHotWordName();
                int sensitivity = config.getSelectedHotWordSensitivity();
                subText = hotword + " " + sensitivity;
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

                // stop audio service
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

    // cast notification
    private class CastDescriptionAdapter implements
            PlayerNotificationManager.MediaDescriptionAdapter {

        @Override
        public String getCurrentContentTitle(Player player) {
            return m_cast_media_title;
        }

        @Nullable
        @Override
        public PendingIntent createCurrentContentIntent(Player player) {
            return null;
        }

        @Nullable
        @Override
        public String getCurrentContentText(Player player) {
            int window = player.getCurrentWindowIndex();
            return "Cast " +  m_cast_media_source;
        }

        @Nullable
        @Override
        public CharSequence getCurrentSubText(Player player) {
            return null;
        }

        @Nullable
        @Override
        public Bitmap getCurrentLargeIcon(Player player,
                                          PlayerNotificationManager.BitmapCallback callback) {

            Thread thread = new Thread(() -> {
                try {
                    URL url = new URL(m_cast_media_stream_image);
                    Bitmap bitmap = BitmapFactory.decodeStream(url.openConnection().getInputStream());
                    callback.onBitmap(bitmap);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            thread.start();

            return null;
        }

    }

}


