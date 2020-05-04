package pl.sviete.dom;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.webkit.WebView;

import ai.picovoice.porcupinemanager.PorcupineManager;

public class AisCoreUtils {

    // AIS_GATE ID
    public static String AIS_GATE_ID = null;
    public static String AIS_GATE_USER = "";
    public static String AIS_GATE_DESC = "";
    public static String AIS_DEVICE_TYPE = "MOB";
    public static String AIS_PUSH_NOTIFICATION_KEY = "";

    // STT
    public static final String BROADCAST_ON_END_SPEECH_TO_TEXT = "BROADCAST_ON_END_SPEECH_TO_TEXT";
    public static final String BROADCAST_ON_START_SPEECH_TO_TEXT = "BROADCAST_ON_START_SPEECH_TO_TEXT";
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
    private static String TAG = "AisCoreUtils";
    private static String AIS_DOM_URL = "http://127.0.0.1:8180";
    private static String AIS_DOM_CLOUD_WS_URL = "https://powiedz.co/ords/dom/dom/";
    private static String AIS_DOM_CLOUD_WS_URL_HTTP = "http://powiedz.co/ords/dom/dom/";

    // HOT WORD
    public static PorcupineManager mPorcupineManager = null;
    public static final String BROADCAST_ON_END_HOT_WORD_LISTENING = "BROADCAST_ON_END_HOT_WORD_LISTENING";
    public static final String BROADCAST_ON_START_HOT_WORD_LISTENING = "BROADCAST_ON_START_HOT_WORD_LISTENING";


    // EXO PLAYER
    public static final String BROADCAST_EXO_PLAYER_COMMAND = "BROADCAST_EXO_PLAYER_COMMAND";
    public static final String BROADCAST_EXO_PLAYER_COMMAND_TEXT = "BROADCAST_EXO_PLAYER_COMMAND_TEXT";



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
    public static final String AIS_DOM_CHANNEL_ID = "AisServiceChannelId";
    public static String AIS_DOM_LAST_TTS = "";
    public static String AIS_DOM_LAST_TTS_SOURCE = "";

    // HA
    public static String HA_CLIENT_ID = "";
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


}

