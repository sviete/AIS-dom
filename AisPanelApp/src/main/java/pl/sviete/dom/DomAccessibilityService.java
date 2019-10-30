package pl.sviete.dom;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import org.json.JSONException;
import org.json.JSONObject;

import static pl.sviete.dom.AisPanelService.BROADCAST_ON_END_SPEECH_TO_TEXT_KEY_PRES;
import static pl.sviete.dom.AisPanelService.BROADCAST_ON_START_SPEECH_TO_TEXT_KEY_PRES;

public class DomAccessibilityService extends AccessibilityService {
    public static final String TAG = "DomAccessibilityService";
    @Override
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) { }

    @Override
    public void onInterrupt() {
        Log.i(TAG, "onInterrupt");
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        Log.d(TAG, "onKeyEvent " + event.toString());

        // remember last key
        AisCoreUtils.AIS_LAST_KEY_CODE = event.getKeyCode();


        // 1. change the mode
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            //
            if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_1 || event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE) {
                // change the mode
                // block possibility to change mode for 2 seconds
                if ((System.currentTimeMillis() - AisCoreUtils.mLastSwitchControlModeTime) < 2000) {

                    Intent intent = new Intent(AisPanelService.BROADCAST_EVENT_CHANGE_CONTROLLER_MODE);
                    intent.putExtra(AisPanelService.EVENT_CHANGE_CONTROLLER_MODE_VALUE, "");
                    LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
                    bm.sendBroadcast(intent);
                    AisCoreUtils.mLastSwitchControlModeTime = System.currentTimeMillis();
                }

            }
        }


        // 2. microphone
        if (event.getKeyCode() == KeyEvent.KEYCODE_LEFT_BRACKET && event.getAction() == KeyEvent.ACTION_DOWN) {
            // KEYCODE_LEFT_BRACKET -> MIC DOWN
            Intent intent = new Intent(BROADCAST_ON_START_SPEECH_TO_TEXT_KEY_PRES);
            LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
            bm.sendBroadcast(intent);
        } else if(event.getKeyCode() == KeyEvent.KEYCODE_RIGHT_BRACKET) {
            // KEYCODE_RIGHT_BRACKET -> MIC UP
            Intent intent = new Intent(BROADCAST_ON_END_SPEECH_TO_TEXT_KEY_PRES);
            LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
            bm.sendBroadcast(intent);
        }



        // 3. only in offDispaly mode
        if (AisCoreUtils.getRemoteControllerMode().equals(AisCoreUtils.mOffDisplay)){
            // keycode back heve to be hendled on SplashScreenActivity because it is not send when the mouse if on
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK){
                return false;
            }
            // 3.1 Brodcast the key press to AIS-dom
            if (event.getAction() == KeyEvent.ACTION_UP) {
                if (event.getKeyCode() != KeyEvent.KEYCODE_LEFT_BRACKET && event.getKeyCode() != KeyEvent.KEYCODE_CTRL_LEFT &&
                        event.getKeyCode() != KeyEvent.KEYCODE_RIGHT_BRACKET && event.getKeyCode() != AisCoreUtils.mLastLongPressKeyCode &&
                        event.getKeyCode() != KeyEvent.KEYCODE_BUTTON_1 && event.getKeyCode() != KeyEvent.KEYCODE_ESCAPE) {

                        Intent intent = new Intent(AisPanelService.BROADCAST_EVENT_KEY_BUTTON_PRESSED);
                        JSONObject k_event = new JSONObject();
                        try {
                            k_event.put("KeyCode", event.getKeyCode());
                            if (event.getKeyCode() == 23) {
                                k_event.put("Action", 0);
                            } else {
                                k_event.put("Action", 1);
                            }
                            k_event.put("DownTime", event.getDownTime());
                            k_event.put("RepeatCount", 0);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        intent.putExtra(AisPanelService.EVENT_KEY_BUTTON_PRESSED_VALUE, String.valueOf(k_event));
                        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
                        bm.sendBroadcast(intent);
                    }

                // 3.2 Volume
                if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    try {
                        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
                        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0);
                        int audioVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                        int systemVol =  Math.max(Math.round(audioVol / 4), 1);
                        audioManager.setStreamMute(AudioManager.STREAM_SYSTEM, false);
                        audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, systemVol, 0);
                        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, systemVol, 0);
                    } catch (Exception e) {
                        Log.e(TAG, e.toString());
                    }
                } else if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP) {
                    try {
                        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
                        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0);
                        int audioVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                        int systemVol = Math.max(Math.round(audioVol / 4), 1);
                        audioManager.setStreamMute(AudioManager.STREAM_SYSTEM, false);
                        audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, systemVol, 0);
                        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, systemVol, 0);
                    } catch (Exception e) {
                        Log.e(TAG, e.toString());
                    }
                }
            }
        }

    // Important!!! do not return true!!! do not forget the problems with lot of KeyEvents
    return false;

    }

}
