package pl.sviete.dom;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.ToneGenerator;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

import pl.sviete.dom.views.RecognitionProgressView;

public class SplashScreenActivity extends AppCompatActivity {

    final String TAG = SplashScreenActivity.class.getName();
    public static RecognitionProgressView mRecognitionProgressView = null;
    private LocalBroadcastManager mlocalBroadcastManager;
    private TextView mSttTextView;
    private TextView mTtsTextView;
    private TextView mIpTextView;
    private ImageView mIpImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // we need to finish this activity if we are going to work off display
        AisCoreUtils.mSplashScreenActivity = this;
        setContentView(R.layout.activity_ais_dom_splash_screen);

        //
        createRecognitionView();

        //
        mlocalBroadcastManager = LocalBroadcastManager.getInstance(this);

        //
        mSttTextView = (TextView) findViewById(R.id.sttTextView);

        //
        mIpTextView = (TextView) findViewById(R.id.ipTextView);
        mIpImageView = (ImageView) findViewById(R.id.ipImageIconView);

        mSttTextView.setText("");
        mSttTextView.setTextColor(ContextCompat.getColor(this, R.color.color3));
        mTtsTextView = (TextView) findViewById(R.id.ttsTextView);
        mTtsTextView.setText("...");
        mTtsTextView.setTextSize(40);
        mSttTextView.setTextSize(40);
        mSttTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        mTtsTextView.setGravity(Gravity.CENTER_HORIZONTAL);

    }

    @Override
    protected void onStart() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(AisCoreUtils.BROADCAST_ON_END_SPEECH_TO_TEXT);
        filter.addAction(AisCoreUtils.BROADCAST_ON_START_SPEECH_TO_TEXT);
        filter.addAction(AisCoreUtils.BROADCAST_ON_END_TEXT_TO_SPEECH);
        filter.addAction(AisCoreUtils.BROADCAST_ON_START_TEXT_TO_SPEECH);
        filter.addAction(AisCoreUtils.BROADCAST_EVENT_ON_SPEECH_PARTIAL_RESULTS);
        filter.addAction(AisCoreUtils.BROADCAST_EVENT_ON_SPEECH_COMMAND);
        filter.addAction(AisPanelService.BROADCAST_EVENT_KEY_BUTTON_PRESSED);
        filter.addAction(AisCoreUtils.BROADCAST_ON_IP_CHANGE);
        mlocalBroadcastManager.registerReceiver(mBroadcastReceiver, filter);

        super.onStart();
    }


    @Override
    protected void onStop() {
        mlocalBroadcastManager.unregisterReceiver(mBroadcastReceiver);
        super.onStop();
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(AisCoreUtils.BROADCAST_ON_END_SPEECH_TO_TEXT)) {
                Log.i(TAG, AisCoreUtils.BROADCAST_ON_END_SPEECH_TO_TEXT + "onEndSpeechToText");
                onEndSpeechToText();
            } else if (intent.getAction().equals(AisCoreUtils.BROADCAST_ON_END_TEXT_TO_SPEECH)) {
                Log.i(TAG, AisCoreUtils.BROADCAST_ON_END_TEXT_TO_SPEECH + " onEndTextToSpeech.");
                onEndTextToSpeech();
            } else if (intent.getAction().equals(AisCoreUtils.BROADCAST_ON_START_TEXT_TO_SPEECH)) {
                Log.i(TAG, AisCoreUtils.BROADCAST_ON_START_TEXT_TO_SPEECH + " onStartTextToSpeech.");
                final String text = intent.getStringExtra(AisCoreUtils.TTS_TEXT);
                final String type = intent.getStringExtra(AisCoreUtils.TTS_TEXT_TYPE);
                onStartTextToSpeech(text, type);
            } else if (intent.getAction().equals(AisCoreUtils.BROADCAST_ON_START_SPEECH_TO_TEXT)) {
                Log.i(TAG, AisCoreUtils.BROADCAST_ON_START_SPEECH_TO_TEXT + " onStartSpeechToText.");
                onStartSpeechToText();
            } else if (intent.getAction().equals(AisCoreUtils.BROADCAST_EVENT_ON_SPEECH_PARTIAL_RESULTS)) {
                Log.i(TAG, AisCoreUtils.BROADCAST_EVENT_ON_SPEECH_PARTIAL_RESULTS + " display text.");
                final String partialText = intent.getStringExtra(AisCoreUtils.BROADCAST_EVENT_ON_SPEECH_PARTIAL_RESULTS_TEXT);
                onSttParialResults(partialText);
            } else if (intent.getAction().equals(AisCoreUtils.BROADCAST_EVENT_ON_SPEECH_COMMAND)){
                Log.i(TAG, AisCoreUtils.BROADCAST_EVENT_ON_SPEECH_COMMAND + " display text.");
                final String command = intent.getStringExtra(AisCoreUtils.BROADCAST_EVENT_ON_SPEECH_COMMAND_TEXT);
                onSttFullResult(command);
            } else if (intent.getAction().equals(AisPanelService.BROADCAST_EVENT_KEY_BUTTON_PRESSED)) {
                Log.d(TAG, AisPanelService.BROADCAST_EVENT_KEY_BUTTON_PRESSED + " onClick");
                onClick();
            } else if (intent.getAction().equals(AisCoreUtils.BROADCAST_ON_IP_CHANGE)) {
                Log.d(TAG, AisCoreUtils.BROADCAST_ON_IP_CHANGE + " dsplay new IP ");
                final String newIP = intent.getStringExtra(AisCoreUtils.BROADCAST_ON_IP_CHANGE_NEW_IP);
                final String newIcon = intent.getStringExtra(AisCoreUtils.BROADCAST_ON_IP_CHANGE_NEW_ICON);
                onIPchange(newIP, newIcon);
            }
        }
    };

    private void onStartTextToSpeech(String text, String type) {
        Log.d(TAG, "onStartTextToSpeech -> rotate animation");
        mRecognitionProgressView.transform();

        mTtsTextView.setText(text);
        mTtsTextView.setTextSize(40);
        mTtsTextView.setGravity(Gravity.CENTER_HORIZONTAL);
    }

    private void onEndTextToSpeech() {
        Log.d(TAG, "onEndTextToSpeech -> stop animation");
        mRecognitionProgressView.stop();

        mTtsTextView.setTextSize(36);
        mTtsTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        String text = mTtsTextView.getText().toString();
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                //remove text after 1.5sec
                String currText = mTtsTextView.getText().toString();
                if (currText.equals(text)) {
                    mTtsTextView.setText("");
                }
            }
        }, 1500);

    }

    private void onStartSpeechToText() {
        Log.d(TAG, "onStartSpeechToText -> play animation");
        mRecognitionProgressView.play();

        mSttTextView.setText("Słucham Cię...");
        mSttTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        mTtsTextView.setText("");
    }

    private void onEndSpeechToText(){
        Log.d(TAG, "onEndSpeechToText -> stop");
        mRecognitionProgressView.stop();
    }

    private void onSttFullResult(String text) {
        Log.d(TAG, "onSttFullResult " + text);
        mSttTextView.setText(text);
        mSttTextView.setTextColor(ContextCompat.getColor(this, R.color.color3));
        mSttTextView.setTextSize(36);
        mSttTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                //remove text after 1.5sec
                String currText = mSttTextView.getText().toString();
                if (currText.equals(text)) {
                    mSttTextView.setText("");
                }
            }
        }, 2500);
    }

    private void onSttParialResults(String text) {
        Log.d(TAG, "onSttParialResults " + text);
        mSttTextView.setText(text);
        mSttTextView.setTextColor(ContextCompat.getColor(this, R.color.color1));
        mSttTextView.setTextSize(40);
        mSttTextView.setGravity(Gravity.CENTER_HORIZONTAL);
    }

    private void onClick() {
        Log.d(TAG, "onClick ");
        mSttTextView.setText("");
    }

    private void onIPchange(String newIp, String newIcon) {
        Log.d(TAG, "onIPchange ");
        if (newIp.equals(" ")){
            mIpTextView.setText("...");
        } else {
            mIpTextView.setText("http://" + newIp + ":8180");
        }
        if (newIcon == "conn_icon_bluetooth") {
            mIpImageView.setImageResource(R.drawable.conn_icon_bluetooth);
        } else if (newIcon == "conn_icon_ethernet") {
            mIpImageView.setImageResource(R.drawable.conn_icon_ethernet);
        } else if (newIcon == "conn_icon_disconnect"){
            mIpImageView.setImageResource(R.drawable.conn_icon_disconnect);
        } else {
            mIpImageView.setImageResource(R.drawable.conn_icon_wifi);
        }
    }

    private void createRecognitionView(){
        int[] colors = {
                ContextCompat.getColor(this, R.color.color1),
                ContextCompat.getColor(this, R.color.color2),
                ContextCompat.getColor(this, R.color.color3)
        };

        int[] heights = { 80, 55, 20 };
        mRecognitionProgressView = (RecognitionProgressView) findViewById(R.id.ais_recognition_view);


        if (AisCoreUtils.mSpeech == null) {
            AisCoreUtils.mSpeech = SpeechRecognizer.createSpeechRecognizer(this);
        }

        mRecognitionProgressView.setSpeechRecognizer(AisCoreUtils.mSpeech);
        mRecognitionProgressView.setRecognitionListener(new AisRecognitionListener(this, AisCoreUtils.mSpeech));
        mRecognitionProgressView.setColors(colors);
        mRecognitionProgressView.setBarMaxHeightsInDp(heights);
        mRecognitionProgressView.setCircleRadiusInDp(5);
        mRecognitionProgressView.setSpacingInDp(5);
        mRecognitionProgressView.setIdleStateAmplitudeInDp(5);
        mRecognitionProgressView.setRotationRadiusInDp(20);

        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                //reset animation after 2sec
                mRecognitionProgressView.rotate();
            }
        }, 2000);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                //stop animation after 1.5sec
                mRecognitionProgressView.stop();
            }
        }, 3500);


        Log.i(TAG, "starting STT initialization");
        if (AisCoreUtils.mRecognizerIntent == null) {
            AisCoreUtils.mRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            AisCoreUtils.mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString());
            AisCoreUtils.mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            AisCoreUtils.mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, new Long(5000));
            AisCoreUtils.mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, "pl.sviete.dom");
            AisCoreUtils.mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            AisCoreUtils.mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        }
    }

    private String getKeyEvent(KeyEvent event) {
        Log.d(TAG, "getKeyEvent Called");
        JSONObject k_event = new JSONObject();
        int RepeatCount = 0;
        try{
            RepeatCount = event.getRepeatCount();
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }

        // only long press is here
        if (RepeatCount == 0){ return ""; }

        //
        if (RepeatCount % 10 == 0) {
            try {
                k_event.put("KeyCode", event.getKeyCode());
                k_event.put("Action", event.getAction());
                k_event.put("DownTime", event.getDownTime());
                k_event.put("RepeatCount", RepeatCount);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            return String.valueOf(k_event);
        }
        return "";
    }


    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        Log.d(TAG, "dispatchKeyEvent: " + event.toString());

        // checking if user is trying to change the mode but don't have access to AccessibilityService
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_1 || event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE) {
                // change the mode
                if (!AisCoreUtils.isAccessibilityEnabled(getApplicationContext())){
                    // no access to AccessibilityService - try to add
                    AisCoreUtils.enableAccessibility();
                    Intent txtIntent = new Intent(AisPanelService.BROADCAST_READ_THIS_TXT_NOW);
                    txtIntent.putExtra(AisPanelService.READ_THIS_TXT_MESSAGE_VALUE, "Brak dostępu do klawiatury, sprawdzam uprawnienia aplikacji.");
                    LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
                    bm.sendBroadcast(txtIntent);
                }
            }
        }

        // allow to mute
        if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return false;
        }


        if (event.getAction() == KeyEvent.ACTION_UP) {
            AisCoreUtils.mLastLongPressKeyCode = -1;

            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK || event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE) {
                if (AisCoreUtils.getRemoteControllerMode().equals(AisCoreUtils.mOffDisplay)) {
                    Log.i(TAG, "Back/Esc button pressed in off display");
                    Intent intent = new Intent(AisPanelService.BROADCAST_EVENT_KEY_BUTTON_PRESSED);
                    intent.putExtra(AisPanelService.EVENT_KEY_BUTTON_PRESSED_VALUE, "{\"KeyCode\":4,\"Action\":1,\"DownTime\":0,\"RepeatCount\":0}");
                    LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
                    bm.sendBroadcast(intent);
                }

                if (!AisCoreUtils.isAccessibilityEnabled(getApplicationContext())){
                    // no access to AccessibilityService - try to exit
                    Log.i(TAG, "Back/Esc button pressed without access to Accessibility service!");
                    return false;
                }


            }
        } else if (event.getAction() == KeyEvent.ACTION_DOWN){
            String kEvent = getKeyEvent(event);
            if (!kEvent.equals("")){
                // we have long press
                AisCoreUtils.mLastLongPressKeyCode = event.getKeyCode();
                Intent intent = new Intent(AisPanelService.BROADCAST_EVENT_KEY_BUTTON_PRESSED);
                intent.putExtra(AisPanelService.EVENT_KEY_BUTTON_PRESSED_VALUE, kEvent);
                LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
                bm.sendBroadcast(intent);
            }
        }

        //return true to disable remote in app
        return true;

    }


    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (AisCoreUtils.getRemoteControllerMode().equals(AisCoreUtils.mOffDisplay)) {
            if (event.getToolType(0) != MotionEvent.TOOL_TYPE_FINGER) {
                // click not by finger / on remote controller
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    Intent intent = new Intent(AisPanelService.BROADCAST_EVENT_KEY_BUTTON_PRESSED);
                    intent.putExtra(AisPanelService.EVENT_KEY_BUTTON_PRESSED_VALUE, "{\"KeyCode\":23,\"Action\":0,\"DownTime\":0,\"RepeatCount\":0}");
                    LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
                    bm.sendBroadcast(intent);
                }
            }
        }
        // to disable remote in app
        Log.d(TAG, "dispatchTouchEvent, disabled: " + event);
        return true;
    }



    @Override
    public void onDestroy() {
        super.onDestroy();
        if (AisCoreUtils.mSpeech != null) {
            AisCoreUtils.mSpeech.destroy();
        }

    }

}
