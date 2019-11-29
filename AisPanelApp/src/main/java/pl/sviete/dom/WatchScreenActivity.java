package pl.sviete.dom;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.BounceInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.util.Locale;

public class WatchScreenActivity extends AppCompatActivity {

    final String TAG = WatchScreenActivity.class.getName();
    private LocalBroadcastManager mlocalBroadcastManager;
    private TextView mSttTextView;
    private final int REQUEST_RECORD_PERMISSION = 100;
    private ImageView mConfImageView;
    private Config mConfig = null;
    private ToggleButton mBtnSpeak;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_ais_dom_watch_screen);

        //
        createSpeechEngine();

        //
        mBtnSpeak = (ToggleButton) findViewById(R.id.btnSpeak);

        mBtnSpeak.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                                         boolean isChecked) {
                if (isChecked) {
                    // volume down
                    Intent intent = new Intent(AisCoreUtils.BROADCAST_ON_START_SPEECH_TO_TEXT);
                    LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
                    bm.sendBroadcast(intent);

                    int permissionMic = ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
                    if (permissionMic != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions
                                (WatchScreenActivity.this,
                                        new String[]{Manifest.permission.RECORD_AUDIO},
                                        REQUEST_RECORD_PERMISSION);
                    } else {
                        startTheSpeechToText();
                    }
                } else {
                    stopTheSpeechToText();
                }
            }
        });

        //
        mlocalBroadcastManager = LocalBroadcastManager.getInstance(this);

        //
        mSttTextView = (TextView) findViewById(R.id.sttTextView);


        mConfImageView = (ImageView) findViewById(R.id.go_to_config);
        mConfImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // go to settings
                Log.d(TAG, "startSettingsActivity Called");
                try{
                    Intent intent = new Intent(Intent.ACTION_MAIN);
                    intent.setComponent(new ComponentName("pl.sviete.dom","pl.sviete.dom.WelcomeActivity"));
                    intent.putExtra(WelcomeActivity.BROADCAST_STAY_ON_SETTNGS_ACTIVITY_VALUE, true);
                    startActivity(intent);
                    finish();
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        });

        mSttTextView.setText("");
        mSttTextView.setTextColor(ContextCompat.getColor(this, R.color.color3));
        mSttTextView.setTextSize(30);
        mSttTextView.setGravity(Gravity.CENTER_HORIZONTAL);
    }

    private void startTheSpeechToText(){

        // just in case
        createSpeechEngine();

        if (AisCoreUtils.mSpeechIsRecording) {
                stopTheSpeechToText();
                Log.e(TAG, "StopTheSpeechToText !!!");
        }

        Log.e(TAG, "startListening");
        AisCoreUtils.mSpeech.startListening(AisCoreUtils.mRecognizerIntent);

        ScaleAnimation scaleAnimation = new ScaleAnimation(0.7f, 1.0f, 0.7f, 1.0f,
                Animation.RELATIVE_TO_SELF, 0.7f, Animation.RELATIVE_TO_SELF, 0.7f);
        scaleAnimation.setDuration(500);
        BounceInterpolator bounceInterpolator = new BounceInterpolator();
        scaleAnimation.setInterpolator(bounceInterpolator);
        mBtnSpeak.startAnimation(scaleAnimation);
    }

    private void stopTheSpeechToText(){
        Log.d(TAG, "stopTheSpeechToText");
        AisCoreUtils.mSpeech.stopListening();
        ScaleAnimation scaleAnimation = new ScaleAnimation(0.7f, 1.0f, 0.7f, 1.0f,
                Animation.RELATIVE_TO_SELF, 0.7f, Animation.RELATIVE_TO_SELF, 0.7f);
        scaleAnimation.setDuration(500);
        BounceInterpolator bounceInterpolator = new BounceInterpolator();
        scaleAnimation.setInterpolator(bounceInterpolator);
        mBtnSpeak.startAnimation(scaleAnimation);
    }

    @Override
    protected void onStart() {
        // to check the url from settings
        mConfig = new Config(getApplicationContext());
        // get app url with discovery
        Log.i(TAG, mConfig.getAppLaunchUrl(true));

        IntentFilter filter = new IntentFilter();
        filter.addAction(AisCoreUtils.BROADCAST_ON_END_SPEECH_TO_TEXT);
        filter.addAction(AisCoreUtils.BROADCAST_ON_START_SPEECH_TO_TEXT);
        filter.addAction(AisCoreUtils.BROADCAST_EVENT_ON_SPEECH_PARTIAL_RESULTS);
        filter.addAction(AisCoreUtils.BROADCAST_EVENT_ON_SPEECH_COMMAND);
        mlocalBroadcastManager.registerReceiver(mBroadcastReceiver, filter);

        //
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
            }
        }
    };

    private void onStartSpeechToText() {
        Log.d(TAG, "onStartSpeechToText -> play animation");

        mSttTextView.setText("Słucham Cię...");
        mSttTextView.setGravity(Gravity.CENTER_HORIZONTAL);
    }

    private void onEndSpeechToText(){
        Log.d(TAG, "onEndSpeechToText -> stop");

        if (mSttTextView.getText().equals("Słucham Cię...")){
            mSttTextView.setText("...");
        }

        mBtnSpeak.setChecked(false);
    }

    private void onSttFullResult(String text) {
        Log.e(TAG, "!!! onSttFullResult " + text);
        mSttTextView.setText(text);
        mSttTextView.setTextColor(ContextCompat.getColor(this, R.color.color3));
        mSttTextView.setTextSize(34);
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
        mSttTextView.setTextSize(32);
        mSttTextView.setGravity(Gravity.CENTER_HORIZONTAL);
    }


    private void createSpeechEngine(){

        //if (AisCoreUtils.mSpeech == null) {
            Log.i(TAG, "createSpeechEngine -> createSpeechRecognizer");
            AisCoreUtils.mSpeech = SpeechRecognizer.createSpeechRecognizer(this);
            AisRecognitionListener listener = new AisRecognitionListener(this, AisCoreUtils.mSpeech);
            AisCoreUtils.mSpeech.setRecognitionListener(listener);
        //}

        if (AisCoreUtils.mRecognizerIntent == null) {
            Log.i(TAG, "createSpeechEngine -> starting STT initialization");
            AisCoreUtils.mRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            AisCoreUtils.mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString());
            AisCoreUtils.mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            AisCoreUtils.mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, new Long(5000));
            AisCoreUtils.mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, "pl.sviete.dom");
            AisCoreUtils.mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            AisCoreUtils.mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        if (AisCoreUtils.mSpeech != null) {
            AisCoreUtils.mSpeech.destroy();
        }
    }

}
