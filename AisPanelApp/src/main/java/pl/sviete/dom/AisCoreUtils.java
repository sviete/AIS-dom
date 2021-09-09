package pl.sviete.dom;

import static android.content.Context.BATTERY_SERVICE;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.webkit.WebView;

import androidx.work.WorkManager;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

import org.linphone.core.Call;

import java.io.File;
import java.text.DecimalFormat;

import ai.picovoice.porcupine.PorcupineManager;

public class AisCoreUtils {

    // AIS_GATE ID
    public static String AIS_GATE_ID = null;
    public static String AIS_REMOTE_GATE_ID = "";
    public static String AIS_DEVICE_TYPE = "MOB";

    // STT
    public static final String BROADCAST_ON_END_SPEECH_TO_TEXT_MOB = "BROADCAST_ON_END_SPEECH_TO_TEXT_MOB";
    public static final String BROADCAST_ON_START_SPEECH_TO_TEXT_MOB = "BROADCAST_ON_START_SPEECH_TO_TEXT_MOB";
    public static final String  BROADCAST_EVENT_ON_SPEECH_PARTIAL_RESULTS = "BROADCAST_EVENT_ON_SPEECH_PARTIAL_RESULTS";
    public static final String  BROADCAST_EVENT_ON_SPEECH_PARTIAL_RESULTS_TEXT = "BROADCAST_EVENT_ON_SPEECH_PARTIAL_RESULTS_TEXT";

    // TTS
    public static final String BROADCAST_ACTIVITY_SAY_IT = "BROADCAST_ACTIVITY_SAY_IT";
    public static final String BROADCAST_SERVICE_SAY_IT = "BROADCAST_SERVICE_SAY_IT";
    public static final String BROADCAST_SAY_IT_TEXT = "BROADCAST_SAY_IT_TEXT";
    public static final String BROADCAST_ON_END_TEXT_TO_SPEECH = "BROADCAST_ON_END_TEXT_TO_SPEECH";
    public static final String BROADCAST_ON_START_TEXT_TO_SPEECH = "BROADCAST_ON_START_TEXT_TO_SPEECH";
    public static final String TTS_TEXT = "TTS_TEXT";

    // USB
    private static final String TAG = "AisCoreUtils";
    private static String AIS_DOM_URL = "";
    private static final String AIS_DOM_CLOUD_WS_URL = "https://powiedz.co/ords/dom/dom/";
    private static final String AIS_DOM_CLOUD_WS_URL_HTTP = "http://powiedz.co/ords/dom/dom/";

    // HOT WORD
    public static PorcupineManager mPorcupineManager = null;
    public static final String BROADCAST_ON_END_HOT_WORD_LISTENING = "BROADCAST_ON_END_HOT_WORD_LISTENING";
    public static final String BROADCAST_ON_START_HOT_WORD_LISTENING = "BROADCAST_ON_START_HOT_WORD_LISTENING";


    // EXO PLAYER
    public static final String BROADCAST_EXO_PLAYER_COMMAND = "BROADCAST_EXO_PLAYER_COMMAND";
    public static final String BROADCAST_EXO_PLAYER_COMMAND_TEXT = "BROADCAST_EXO_PLAYER_COMMAND_TEXT";

    // CAST
    public static final String BROADCAST_CAST_COMMAND = "BROADCAST_CAST_COMMAND";
    public static final String BROADCAST_CAST_COMMAND_TEXT = "BROADCAST_CAST_COMMAND_TEXT";

    // CAMERA
    public static final String BROADCAST_CAMERA_COMMAND = "BROADCAST_CAMERA_COMMAND";
    public static final String BROADCAST_CAMERA_COMMAND_URL = "BROADCAST_CAMERA_COMMAND_URL";
    public static final String BROADCAST_CAMERA_HA_ID = "BROADCAST_CAMERA_HA_ID";
    public static final String BROADCAST_CAMERA_SIP_CALL = "BROADCAST_CAMERA_SIP_CALL";

    // GPS
    public static int GPS_SERVICE_LOCATIONS_DETECTED = 0;
    public static int GPS_SERVICE_LOCATIONS_SENT = 0;
    public static WorkManager GATE_SYNC_WORK_MANAGER = null;

    // GO TO VIEW FROM NOTIFICATION
    public static final String GO_TO_HA_APP_VIEW_INTENT_EXTRA = "GO_TO_HA_APP_VIEW_INTENT_EXTRA";


    public static String getAisDomUrl(){
        if (onBox()){
            return "http://127.0.0.1:8180";
        }
        return AIS_DOM_URL;
    }

    public static String getAisDomCloudWsUrl(boolean http){
        if (http){
            return AIS_DOM_CLOUD_WS_URL_HTTP;
        }
        return AIS_DOM_CLOUD_WS_URL;
    }

    public static void setAisDomUrl(String url){
         AIS_DOM_URL = url;
    }

    public static Activity mBrowserActivity = null;
    public static WebView mWebView = null;

    // STT
    public static SpeechRecognizer mSpeech = null;
    public static Intent mRecognizerIntent = null;
    public static boolean mSpeechIsRecording = false;


    // Audio source
    public static String mAudioSourceAndroid = "Android";

    // Notification
    public static int AIS_DOM_NOTIFICATION_ID = 1234;
    public static final String AIS_DOM_CHANNEL_ID = "AisServiceChannelId";
    public static String AIS_DOM_LAST_TTS = "";

    public static int AIS_LOCATION_NOTIFICATION_ID = 5678;
    public static final String AIS_LOCATION_CHANNEL_ID = "AisServiceLocationChannelId";

    // HA
    public static String HA_CLIENT_ID = "";
    public static String HA_CODE = "";
    public static String AIS_PUSH_NOTIFICATION_KEY = "";
    public static String AIS_HA_ACCESS_TOKEN = "";
    public static String AIS_HA_WEBHOOK_ID = "";

    // PERMISSION
    public static final int REQUEST_RECORD_PERMISSION = 100;
    public static final int REQUEST_HOT_WORD_MIC_PERMISSION = 200;
    public static final int REQUEST_LOCATION_PERMISSION = 300;
    public static final int  REQUEST_BACKGROUND_LOCATION_PERMISSION = 400;
    public static final int REQUEST_SIP_PERMISSION = 500;

    // REQUEST
    private static RequestQueue mRequestQueue;

    // SIP
    public static String mAisSipStatus = "";
    public static final String BROADCAST_SIP_COMMAND = "BROADCAST_SIP_COMMAND";
    public static final String BROADCAST_SIP_STATUS = "BROADCAST_SIP_STATUS";
    public static Call mAisSipActiveCall;
    public static String mLastCallingSipAddress = "domofon";


    /*
     * Check if we are on gate or phone
     *
     * @return <code>true</code> if we are on Box, otherwise <code>false</code>
     */
    public static boolean onBox() {
        // default for box and phone
        File conf = new File("/data/data/pl.sviete.dom/files/home/AIS/configuration.yaml");
        if (conf.exists()){
            return true;
        }
        // during the first start on box only bootstrap exists
        File bootstrap = new File("/data/data/pl.sviete.dom/files.tar.7z");
        if (bootstrap.exists()){
            return true;
        }
        // test on phone
        File test_file = new File("/sdcard/test_ais_dom_iot_on_phone_on");
        if (test_file.exists()){
            return true;
        }
        return false;
    }

    public static boolean onTv() {
        if (AIS_DEVICE_TYPE.equals("TV")){
            return true;
        }
        return false;

    }


    public static boolean isServiceRunning(Context context, Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    // whether this text has already been spoken
    public static boolean shouldIsayThis(String text, String source) {


        String text_to_say = text.trim();
        if (AIS_DOM_LAST_TTS.substring(0, Math.min(AIS_DOM_LAST_TTS.length(), 250)).equals(text_to_say.substring(0, Math.min(text_to_say.length(), 250)))){
            AIS_DOM_LAST_TTS = text_to_say;
            return false;
        }
        AIS_DOM_LAST_TTS = text_to_say;
        return true;

//        // source can be browser or service or app or app_service
//        Log.d(TAG, "shouldIsayThis: " + text + " source: " + source);
//
//        // if the source is the same we should say (to allow Jolka repeating herself)
//        if (AIS_DOM_LAST_TTS_SOURCE.equals(source)){
//            AIS_DOM_LAST_TTS = text_to_say;
//            return true;
//        }
//        AIS_DOM_LAST_TTS_SOURCE = source;
//        // if the source is different but the text is the same we should not allow to say
//        // we should compare only 250 first characters
//        if (AIS_DOM_LAST_TTS.substring(0, Math.min(AIS_DOM_LAST_TTS.length(), 250)).equals(text_to_say.substring(0, Math.min(text_to_say.length(), 250)))){
//            return false;
//        }
//
//        AIS_DOM_LAST_TTS = text_to_say;
//        return true;
    }

    public static String getDistanceDisplay(Context context, double meters, boolean autoscale) {
        DecimalFormat df = new DecimalFormat("#.###");
        String result = df.format(meters) + context.getString(R.string.meters);

        if(autoscale && (meters >= 1000)) {
            result = df.format(meters/1000) + context.getString(R.string.kilometers);
        }

        return result;
    }

    // get battery level
    public static String getBatteryPercentage(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= 21) {
                BatteryManager bm = (BatteryManager) context.getSystemService(BATTERY_SERVICE);
                return String.valueOf(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY));
            } else {
                IntentFilter iFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus = context.registerReceiver(null, iFilter);

                int level = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) : -1;
                int scale = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1) : -1;

                double batteryPct = level / (double) scale;
                return String.valueOf(batteryPct * 100);
            }
        } catch (Exception e) {
            Log.e(TAG, "getBatteryPercentage error: " + e.getMessage());
            return "100";
        }
    }

    //
    public static RequestQueue getRequestQueue(Context appCtx) {
        if (mRequestQueue == null) {
            mRequestQueue = Volley.newRequestQueue(appCtx);
        }
        return mRequestQueue;
    }
}
