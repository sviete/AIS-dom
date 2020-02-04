package pl.sviete.dom;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.gesture.Gesture;
import android.gesture.GestureLibraries;
import android.gesture.GestureLibrary;
import android.gesture.GestureOverlayView;
import android.gesture.Prediction;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.BounceInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.CompoundButton;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.widget.Button;

import com.github.zagum.switchicon.SwitchIconView;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Locale;

import ai.picovoice.hotword.PorcupineService;
import ai.picovoice.porcupinemanager.PorcupineManagerException;
import pl.sviete.dom.views.RecognitionProgressView;

import static pl.sviete.dom.AisCoreUtils.BROADCAST_ACTIVITY_SAY_IT;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_ON_END_TEXT_TO_SPEECH;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_ON_START_TEXT_TO_SPEECH;
import static pl.sviete.dom.AisCoreUtils.isServiceRunning;


abstract class BrowserActivity extends AppCompatActivity  implements GestureOverlayView.OnGesturePerformedListener, TextToSpeech.OnInitListener {
    public static final String BROADCAST_ACTION_LOAD_URL = "BROADCAST_ACTION_LOAD_URL";
    public static final String BROADCAST_ACTION_JS_EXEC = "BROADCAST_ACTION_JS_EXEC";
    public static final String BROADCAST_ACTION_CLEAR_BROWSER_CACHE = "BROADCAST_ACTION_CLEAR_BROWSER_CACHE";
    public static final String BROADCAST_ACTION_RELOAD_PAGE = "BROADCAST_ACTION_RELOAD_PAGE";


    final String TAG = BrowserActivity.class.getName();
    private View decorView;
    float zoomLevel = 1.0f;
    private ToggleButton btnSpeak;
    private Button btnGoToSettings;
    private final int REQUEST_RECORD_PERMISSION = 100;
    private final int REQUEST_HOT_WORD_MIC_PERMISSION = 200;
    private LocalBroadcastManager localBroadcastManager;
    private Config mConfig = null;
    public RecognitionProgressView recognitionProgressView = null;
    //
    public static GestureLibrary gestureLibrary;
    private String gesture_sentence;

    public static GestureOverlayView gestureOverlayView;
    //
    public static final int CODE_DRAW_OVER_OTHER_APP_PERMISSION = 2084;
    public String appLaunchUrl = "";

    //
    private SwitchIconView mSwitchIconModeGesture;
    private View mButtonModeGesture;
    private View mButtonModeConnection;

    // browser speech
    private TextToSpeech mBrowserTts;
    private static boolean isBrowserActivityVisible;


    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // we need to finish this activity if we are going to work off display
        AisCoreUtils.mBrowserActivity = this;

        super.onCreate(savedInstanceState);
        // fix for the problem reported on Xiaomi MI 5
        // previously the localBroadcastManager had value assigned in the constructor
        // "this" wasn't fully initialised in your constructor and a Context wasn't obtained,
        // which it why it is null and user get app crash
        localBroadcastManager = LocalBroadcastManager.getInstance(this);
        // if this will not work here, I'm going to move this further
        //
        btnSpeak = findViewById(R.id.btnSpeak);


        //
        mSwitchIconModeGesture = findViewById(R.id.switchControlModeGesture);
        mButtonModeGesture = findViewById(R.id.btnControlModeGesture);
        mButtonModeGesture.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                    gestureOverlayView = (GestureOverlayView) findViewById(R.id.gesturesOverlay);

                    if (mSwitchIconModeGesture.isIconEnabled()) {
                        gestureOverlayView.setVisibility(View.INVISIBLE);
                        mSwitchIconModeGesture.setIconEnabled(false);

                        // gesty wyłączone
                        speakOutFromBrowser("Sterowanie gestami wyłączone.", "app");

                    } else {
                        gestureOverlayView.setVisibility(View.VISIBLE);
                        mSwitchIconModeGesture.setIconEnabled(true);

                        // gesty włączone
                        speakOutFromBrowser("Sterowanie gestami włączone.", "app");


                    }

                return true;
            }
        });

        mButtonModeGesture.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Toast.makeText(BrowserActivity.this, getString(R.string.long_click_to_execute_gesture), Toast.LENGTH_SHORT).show();
            }
        });


        // buton check connection
        mButtonModeConnection = findViewById(R.id.btnControlModeConnection);
        mButtonModeConnection.setOnLongClickListener(v -> {
            appLaunchUrl = mConfig.getAppLaunchUrl(false);
            if (appLaunchUrl.startsWith("dom-")) {
                // sprawdzam połączenie
                speakOutFromBrowser("Sprawdzam połączenie.", "app");
                mButtonModeConnection.setBackgroundResource(R.drawable.ic_connection_sync_icon);
                appLaunchUrl = mConfig.getAppLaunchUrl(true);
            } else {
                speakOutFromBrowser("Podaj w konfiguracji identyfikator bramki, żeby można było sprawdzać połączenie.", "app");
            }
            return true;
        });

        mButtonModeConnection.setOnClickListener(v -> Toast.makeText(BrowserActivity.this, getString(R.string.long_click_to_execute_connection), Toast.LENGTH_SHORT).show());


        // this is done onResume
        // createRecognitionView();

        // tts in browse
        mBrowserTts = new TextToSpeech(this, this);


        btnSpeak.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

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
                                (BrowserActivity.this,
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

        // BTN MIC
        btnSpeak.setOnLongClickListener(v -> {
            if (mConfig.getHotWordMode()) {
                // hot word off
                speakOutFromBrowser("Nasłuchiwanie wyłączone.", "app");
                mConfig.setHotWordMode(false);
                btnSpeak.setBackgroundResource(R.drawable.ic_floating_mic_button_toggle_bg);

            } else {
                int permissionMic = ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
                if (permissionMic != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions
                            (BrowserActivity.this,
                                    new String[]{Manifest.permission.RECORD_AUDIO},
                                    REQUEST_HOT_WORD_MIC_PERMISSION);
                } else {
                    mConfig.setHotWordMode(true);
                    speakOutFromBrowser("Nasłuchiwanie włączone.", "app");
                    btnSpeak.setBackgroundResource(R.drawable.ic_floating_mic_button_toggle_bg_recording);
                }
            }

            return true;
        });

        //
        mConfig = new Config(this.getApplicationContext());

        //
        btnGoToSettings = findViewById(R.id.btnGoToSettings);
        btnGoToSettings.setOnClickListener(v -> {
            Intent intent = new Intent(BrowserActivity.this, WelcomeActivity.class);
            intent.putExtra(WelcomeActivity.BROADCAST_STAY_ON_SETTNGS_ACTIVITY_VALUE, true);
            startActivity(intent);
        });


        //
        zoomLevel = mConfig.getTestZoomLevel();


        decorView = getWindow().getDecorView();
        // Hide both the navigation bar and the status bar.
        // SYSTEM_UI_FLAG_FULLSCREEN is only available on Android 4.1 and higher, but as
        // a general rule, you should design your app to hide the status bar whenever you
        // hide the navigation bar.
        int uiOptions = 0;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN;
        }

        decorView.setSystemUiVisibility(uiOptions);
        // get app url with discovery
        appLaunchUrl = mConfig.getAppLaunchUrl(true);
        if (appLaunchUrl.startsWith("dom-")) {
            loadUrl(appLaunchUrl, true);
        } else {
            loadUrl(appLaunchUrl, false);
        }


        // set the remote control mode on start
        gestureOverlayView = findViewById(R.id.gesturesOverlay);
        gestureOverlayView.setVisibility(View.INVISIBLE);
        mSwitchIconModeGesture.setIconEnabled(false);


        //
        try {
            copyResourceFile(R.raw.params, "porcupine_params.pv");
            copyResourceFile(R.raw.alexa, "alexa.ppn");
            copyResourceFile(R.raw.americano, "americano.ppn");
            copyResourceFile(R.raw.avocado, "avocado.ppn");
            copyResourceFile(R.raw.blueberry, "blueberry.ppn");
            copyResourceFile(R.raw.caterpillar, "caterpillar.ppn");
            copyResourceFile(R.raw.christina, "christina.ppn");
            copyResourceFile(R.raw.dragonfly, "dragonfly.ppn");
            copyResourceFile(R.raw.flamingo, "flamingo.ppn");
            copyResourceFile(R.raw.francesca, "francesca.ppn");
            copyResourceFile(R.raw.iguana, "iguana.ppn");
            copyResourceFile(R.raw.raspberry, "raspberry.ppn");
            copyResourceFile(R.raw.terminator, "terminator.ppn");
        } catch (IOException e) {
            Toast.makeText(this, "Failed to copy resource files.", Toast.LENGTH_SHORT).show();
        }
    }

    // copy porcupine files
    private void copyResourceFile(int resourceID, String filename) throws IOException {
        Resources resources = getResources();
        try (InputStream is = new BufferedInputStream(resources.openRawResource(resourceID), 256); OutputStream os = new BufferedOutputStream(openFileOutput(filename, Context.MODE_PRIVATE), 256)) {
            int r;
            while ((r = is.read()) != -1) {
                os.write(r);
            }
            os.flush();
        }
    }

    void speakOutFromBrowser(String text, String source) {
        // dont say if the browser activity is not visible
        if (!isBrowserActivityVisible){
            return;
        }
        if (!AisCoreUtils.shouldIsayThis(text, source)){
            return;
        }
        //
        if (mBrowserTts != null) {
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
            mBrowserTts.setVoice(voiceobj);
            mBrowserTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "444");
            Intent intent = new Intent(BROADCAST_ON_START_TEXT_TO_SPEECH);
            intent.putExtra(AisCoreUtils.TTS_TEXT, text);
            LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
            bm.sendBroadcast(intent);
        }
    }

    @Override
    public void onInit(int status) {
        Log.v(TAG, "BrowserActivity onInit");
        if (status == TextToSpeech.SUCCESS) {
                int result = mBrowserTts.setLanguage(new Locale("pl_PL"));
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Language is not available.");
                } else {
                    Log.d(TAG, "TTS from broser is ready");
                    mBrowserTts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
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

                        }
                    });
                }
            } else {
                Log.d(TAG, "TTS from broser is not possible");
            }

        Intent intent = getIntent();
        if (intent != null) {
              if (intent.getAction() != null) {
                  if (intent.getAction().equals("exit_mic_service")) {
                      mConfig.setHotWordMode(false);
                    }
                }
        }

        // Hot Word - set on start
        if (mConfig.getHotWordMode()){
            btnSpeak.setBackgroundResource(R.drawable.ic_floating_mic_button_toggle_bg_recording);
        } else {
            btnSpeak.setBackgroundResource(R.drawable.ic_floating_mic_button_toggle_bg);
        }
    }


    public static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while((read = in.read(buffer)) != -1){
            out.write(buffer, 0, read);
        }
    }

    //
    public void reloadGestureLib(){
        // gesture
        Log.d(TAG, "reloadGestureLib...");
        File file = new File(getExternalFilesDir(null) + "/" + "gesture.txt");
        if(file.exists()){
            Log.d(TAG, "biblioteka gestów istnieje");
        } else {
            Log.d(TAG, "tworzę nową bibliotekę gestów");
            try {
                InputStream in = null;
                OutputStream out = null;
                in = getResources().openRawResource(R.raw.abc);
                File outFile = new File(getExternalFilesDir(null) + "/" + "gesture.txt");
                out = new FileOutputStream(outFile);
                copyFile(in, out);
            } catch(IOException e) {
                Log.e("tag", "Failed to copy asset file: ", e);
            }
        }

        gestureLibrary = GestureLibraries.fromFile(getExternalFilesDir(null) + "/" + "gesture.txt");
        if (!gestureLibrary.load()) {
            Log.e(TAG, "Nie mogę załadować biblioteki...");
        }
        // remove all to add only one
        Log.d(TAG, "removeAllOnGestureListeners");
        gestureOverlayView = (GestureOverlayView) findViewById(R.id.gesturesOverlay);
        gestureOverlayView.removeAllOnGesturePerformedListeners();
        gestureOverlayView.addOnGesturePerformedListener(this);
        gestureOverlayView.setGestureStrokeAngleThreshold(90.0f);
        Log.d(TAG, "addOnGesturePerformedListener ");
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadGestureLib();
        isBrowserActivityVisible = true;


//        if (AisCoreUtils.mSpeech != null) {
//            try {
//                AisCoreUtils.mSpeech.destroy();
//                AisCoreUtils.mSpeech = null;
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }

        //
        createRecognitionView();

        //
    }

    @Override
    public void onGesturePerformed(GestureOverlayView gestureOverlayView, Gesture gesture) {
        ArrayList<Prediction> predictions = gestureLibrary.recognize(gesture);
        Log.e(TAG, "rozpoznaje gest...");
        String message = "...";
        // one prediction needed
        if (predictions.size() > 0) {
            Prediction prediction = predictions.get(0);
            // checking prediction
            if (prediction.score > 1.0) {
                gesture_sentence = prediction.name;
                message = "Gest: " + gesture_sentence;
                DomWebInterface.publishMessage(gesture_sentence, "speech_command", getApplicationContext());
            } else{
                // and action
                NumberFormat formatter = new DecimalFormat("#0.00");
                message = "Nie rozumiem tego gestu... (dopasowanie: " + formatter.format(prediction.score) + ")";
            }
        }
        try {
            Toast.makeText(BrowserActivity.this, message, Toast.LENGTH_SHORT).show();
        } catch (Exception e){
            Log.e(TAG, e.toString());
        }
    }

    private void createRecognitionView(){
        int[] colors = {
                ContextCompat.getColor(this, R.color.color1),
                ContextCompat.getColor(this, R.color.color2),
                ContextCompat.getColor(this, R.color.color3)
        };

        int[] heights = { 80, 55, 20 };
        recognitionProgressView = findViewById(R.id.ais_recognition_view);


        if (AisCoreUtils.mSpeech == null) {
            AisCoreUtils.mSpeech = SpeechRecognizer.createSpeechRecognizer(this);
        }

        recognitionProgressView.setSpeechRecognizer(AisCoreUtils.mSpeech);
        recognitionProgressView.setRecognitionListener(new AisRecognitionListener(this, AisCoreUtils.mSpeech));
        recognitionProgressView.setColors(colors);
        recognitionProgressView.setBarMaxHeightsInDp(heights);
        recognitionProgressView.setCircleRadiusInDp(5);
        recognitionProgressView.setSpacingInDp(5);
        recognitionProgressView.setIdleStateAmplitudeInDp(5);
        recognitionProgressView.setRotationRadiusInDp(20);


        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                //start animation after 2sec
                recognitionProgressView.rotate();
            }
        }, 2000);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                //stop animation after 1.5sec
                recognitionProgressView.stop();
            }
        }, 3500);


        Log.d(TAG, "starting STT initialization");
            if (AisCoreUtils.mRecognizerIntent == null) {
            AisCoreUtils.mRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            AisCoreUtils.mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString());
            AisCoreUtils.mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            AisCoreUtils.mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, new Long(1000));
            AisCoreUtils.mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, "pl.sviete.dom");
            AisCoreUtils.mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            AisCoreUtils.mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        }

        // start
        if (AisCoreUtils.isServiceRunning(this.getApplicationContext(), PorcupineService.class)) {
            try {
                AisCoreUtils.mPorcupineManager.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void startTheSpeechToText(){

        if (AisCoreUtils.mSpeech == null){
            AisCoreUtils.mSpeech = SpeechRecognizer.createSpeechRecognizer(this);
            AisRecognitionListener listener = new AisRecognitionListener(this, AisCoreUtils.mSpeech);
            AisCoreUtils.mSpeech.setRecognitionListener(listener);
        }
        AisCoreUtils.mSpeech.startListening(AisCoreUtils.mRecognizerIntent);



        ScaleAnimation scaleAnimation = new ScaleAnimation(0.7f, 1.0f, 0.7f, 1.0f,
                Animation.RELATIVE_TO_SELF, 0.7f, Animation.RELATIVE_TO_SELF, 0.7f);
        scaleAnimation.setDuration(500);
        BounceInterpolator bounceInterpolator = new BounceInterpolator();
        scaleAnimation.setInterpolator(bounceInterpolator);
        btnSpeak.startAnimation(scaleAnimation);

        // to stop the TTS
        Intent intent = new Intent(AisPanelService.BROADCAST_EVENT_DO_STOP_TTS);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
        bm.sendBroadcast(intent);
    }

    private void stopTheSpeechToText(){
        Log.d(TAG, "stopTheSpeechToText");
        ScaleAnimation scaleAnimation = new ScaleAnimation(0.7f, 1.0f, 0.7f, 1.0f,
                Animation.RELATIVE_TO_SELF, 0.7f, Animation.RELATIVE_TO_SELF, 0.7f);
        scaleAnimation.setDuration(500);
        BounceInterpolator bounceInterpolator = new BounceInterpolator();
        scaleAnimation.setInterpolator(bounceInterpolator);
        btnSpeak.startAnimation(scaleAnimation);
        AisCoreUtils.mSpeech.stopListening();
        recognitionProgressView.stop();

    }

    @Override
    protected void onStart() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BROADCAST_ACTION_LOAD_URL);
        filter.addAction(BROADCAST_ACTION_JS_EXEC);
        filter.addAction(BROADCAST_ACTION_CLEAR_BROWSER_CACHE);
        filter.addAction(BROADCAST_ACTION_RELOAD_PAGE);
        filter.addAction(BROADCAST_ACTIVITY_SAY_IT);
        filter.addAction(AisCoreUtils.BROADCAST_ON_END_SPEECH_TO_TEXT);
        filter.addAction(AisCoreUtils.BROADCAST_ON_START_SPEECH_TO_TEXT);
        filter.addAction(AisCoreUtils.BROADCAST_ON_END_TEXT_TO_SPEECH);
        filter.addAction(AisCoreUtils.BROADCAST_ON_START_TEXT_TO_SPEECH);

        localBroadcastManager.registerReceiver(mBroadcastReceiver, filter);

        super.onStart();
    }


    @Override
    protected void onStop() {
        localBroadcastManager.unregisterReceiver(mBroadcastReceiver);
        super.onStop();
    }


    @Override
    protected void onPause() {
        super.onPause();
        if (AisCoreUtils.mSpeech != null) {
            try {
                AisCoreUtils.mSpeech.destroy();
                AisCoreUtils.mSpeech = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        isBrowserActivityVisible = false;
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        if (AisCoreUtils.mSpeech != null) {
                AisCoreUtils.mSpeech.destroy();
        }

        // allow the user to close the WebWiew activity without message
        //Intent intent = new Intent(getApplicationContext(), AisPanelService.class);
        //getApplicationContext().stopService(intent);

        // stop mBrowserTts
        if (mBrowserTts != null){
            mBrowserTts.shutdown();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult");
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_RECORD_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startTheSpeechToText();
                }
            case REQUEST_HOT_WORD_MIC_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mConfig.setHotWordMode(true);
                }
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            int visibility;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                visibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            } else {
                visibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN;
            }
            decorView.setSystemUiVisibility(visibility);
        }
    }



    // handler for received data from service
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(BROADCAST_ACTION_LOAD_URL)) {
                final String url = intent.getStringExtra(BROADCAST_ACTION_LOAD_URL);
                Log.d(TAG, "Browsing to " + url);
                loadUrl(url, false);
            }else if (intent.getAction().equals(BROADCAST_ACTION_JS_EXEC)) {
                final String js = intent.getStringExtra(BROADCAST_ACTION_JS_EXEC);
                Log.d(TAG, "Executing javascript in current browser: " +js);
                evaluateJavascript(js);
            }else if (intent.getAction().equals(BROADCAST_ACTION_CLEAR_BROWSER_CACHE)) {
                Log.d(TAG, "Clearing browser cache");
                clearCache();
            }else if (intent.getAction().equals(BROADCAST_ACTION_RELOAD_PAGE)) {
                Log.d(TAG, "Browser page reloading.");
                reload();
            }else if (intent.getAction().equals(AisCoreUtils.BROADCAST_ON_END_SPEECH_TO_TEXT)) {
                Log.d(TAG, AisCoreUtils.BROADCAST_ON_END_SPEECH_TO_TEXT + " btn speak set checked false.");
                if (btnSpeak.isChecked()) {
                    btnSpeak.setChecked(false);
                }
                onEndSpeechToText();
            }else if (intent.getAction().equals(AisCoreUtils.BROADCAST_ON_START_TEXT_TO_SPEECH)) {
                Log.d(TAG, AisCoreUtils.BROADCAST_ON_START_TEXT_TO_SPEECH + " onStartTextToSpeech.");
                final String text = intent.getStringExtra(AisCoreUtils.TTS_TEXT);
                onStartTextToSpeech(text);
            }else if (intent.getAction().equals(AisCoreUtils.BROADCAST_ON_END_TEXT_TO_SPEECH)) {
                Log.d(TAG, AisCoreUtils.BROADCAST_ON_END_TEXT_TO_SPEECH + " btn speak set checked false.");
                onEndTextToSpeech();
            }else if (intent.getAction().equals(AisCoreUtils.BROADCAST_ON_START_SPEECH_TO_TEXT)){
                Log.d(TAG, AisCoreUtils.BROADCAST_ON_START_SPEECH_TO_TEXT + " btn speak set checked true.");
                if (!btnSpeak.isChecked()) {
                    btnSpeak.setChecked(true);
                }
                onStartSpeechToText();
            } else if (intent.getAction().equals(BROADCAST_ACTIVITY_SAY_IT)){
                final String text = intent.getStringExtra(AisCoreUtils.BROADCAST_SAY_IT_TEXT);
                speakOutFromBrowser(text, "service");
            }
            }
        };

    private void onStartTextToSpeech(String text) {
        Log.d(TAG, "onStartTextToSpeech -> transform");
        // TODO
        // recognitionProgressView.transform();
    }

    private void onEndTextToSpeech() {
        Log.d(TAG, "onEndTextToSpeech -> play");
        recognitionProgressView.stop();
    }

    private void onStartSpeechToText() {
        Log.d(TAG, "onStartSpeechToText ");
        recognitionProgressView.play();
        if (mBrowserTts != null) {
            mBrowserTts.stop();
        }
    }

    private void onEndSpeechToText(){
        Log.d(TAG, "onEndSpeechToText -> rotate");
        btnSpeak.setChecked(false);
        recognitionProgressView.stop();
    }

    protected abstract void loadUrl(final String url, Boolean syncIcon);
    protected abstract void evaluateJavascript(final String js);
    protected abstract void clearCache();
    protected abstract void reload();


}
