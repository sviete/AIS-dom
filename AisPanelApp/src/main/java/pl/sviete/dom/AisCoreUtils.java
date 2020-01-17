package pl.sviete.dom;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.speech.SpeechRecognizer;
import android.webkit.WebView;

import ai.picovoice.porcupinemanager.PorcupineManager;

public class AisCoreUtils {

    // AIS_GATE ID
    public static String AIS_GATE_ID = null;
    public static String AIS_GATE_USER = "";
    public static String AIS_GATE_DESC = "";
    public static String AIS_DEVICE_TYPE = "MOB";
    public static int AIS_LAST_KEY_CODE = 0;

    // STT
    public static final String BROADCAST_ON_END_SPEECH_TO_TEXT = "BROADCAST_ON_END_SPEECH_TO_TEXT";
    public static final String BROADCAST_ON_START_SPEECH_TO_TEXT = "BROADCAST_ON_START_SPEECH_TO_TEXT";
    public static final String  BROADCAST_EVENT_ON_SPEECH_PARTIAL_RESULTS = "BROADCAST_EVENT_ON_SPEECH_PARTIAL_RESULTS";
    public static final String  BROADCAST_EVENT_ON_SPEECH_PARTIAL_RESULTS_TEXT = "BROADCAST_EVENT_ON_SPEECH_PARTIAL_RESULTS_TEXT";
    public static final String BROADCAST_EVENT_ON_SPEECH_COMMAND = "BROADCAST_EVENT_ON_SPEECH_COMMAND";
    public static final String BROADCAST_EVENT_ON_SPEECH_COMMAND_TEXT = "BROADCAST_EVENT_ON_SPEECH_COMMAND_TEXT";

    // TTS
    public static final String BROADCAST_ON_END_TEXT_TO_SPEECH = "BROADCAST_ON_END_TEXT_TO_SPEECH";
    public static final String BROADCAST_ON_START_TEXT_TO_SPEECH = "BROADCAST_ON_START_TEXT_TO_SPEECH";
    public static final String TTS_TEXT = "TTS_TEXT";
    public static final String TTS_TEXT_TYPE = "TTS_TEXT_TYPE";
    public static final String TTS_TEXT_TYPE_IN = "TTS_TEXT_TYPE_IN";
    public static final String TTS_TEXT_TYPE_OUT = "TTS_TEXT_TYPE_OUT";
    public static final String TTS_TEXT_TYPE_ERROR = "TTS_TEXT_TYPE_ERROR";

    // USB
    private static String TAG = "AisCoreUtils";
    private static String AIS_DOM_URL = "http://127.0.0.1:8180";
    private static String AIS_DOM_CLOUD_WS_URL = "https://powiedz.co/ords/dom/dom/";
    private static String AIS_DOM_CLOUD_WS_URL_HTTP = "http://powiedz.co/ords/dom/dom/";
    private static String mCurrentRemoteControllerMode = AisCoreUtils.mOnDisplay;

    // HOT WORD
    public static PorcupineManager mPorcupineManager = null;



    public static String getAisDomUrl(){
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

    public static String getRemoteControllerMode(){
        return mCurrentRemoteControllerMode;
    }

    public static void setRemoteControllerMode(String mode){
        mCurrentRemoteControllerMode = mode;
    }

    public static Activity mBrowserActivity = null;
    public static WebView mWebView = null;

    // STT
    public static SpeechRecognizer mSpeech = null;
    public static Intent mRecognizerIntent = null;
    public static boolean mSpeechIsRecording = false;
    //
    // Control mode
    public static String mOnDisplay = "ON_DISPLAY";
    public static String mByGesture = "BY_GESTURE";


    // Audio source
    public static String mAudioSourceSpotify = "Spotify";
    public static String mAudioSourceRadio = "Radio";
    public static String mAudioSourcePodcast = "Podcast";
    public static String mAudioSourceMusic = "Music";
    public static String mAudioSourceAudioBook = "AudioBook";
    public static String mAudioSourceNews = "News";
    public static String mAudioSourceLocal = "Local";
    public static String mAudioSourceAndroid = "Android";

    // Notification
    public static int AIS_DOM_NOTIFICATION_ID = 1234;
    public static final String AIS_DOM_CHANNEL_ID = "AisDomServiceChannel";
    /*
     * Check if we are on gate or phone
     *
     * @return <code>true</code> if we are on Box, otherwise <code>false</code>
     */

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


}

