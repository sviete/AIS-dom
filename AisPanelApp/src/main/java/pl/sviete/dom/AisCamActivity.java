package pl.sviete.dom;

import static pl.sviete.dom.AisCoreUtils.mAisSipStatus;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.format.DateFormat;
import android.text.format.Time;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.PixelCopy;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.xuchongyang.easyphone.EasyLinphone;

import org.json.JSONObject;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.EcCalibratorStatus;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.interfaces.IVLCVout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import pl.sviete.dom.sip.SipSettings;


public class AisCamActivity extends AppCompatActivity implements SurfaceHolder.Callback {
    private SurfaceView mSurface = null;

    private LibVLC mLibVLC = null;
    private MediaPlayer mMediaPlayer = null;

    public String mUrl = null;
    public String mHaCamId = null;

    // SIP
    private static final int SIP_CALL_ADDRESS = 1;

    private static Config mConfig;
    private static final String TAG = "AIS SIP";

    private static boolean mRingsActive = false;
    private static  String mCallingUserName = "";
    private static int mCallingTimeOut = 60;
    Time mRingStartTime = new Time();
    Time mcurrnetTime = new Time();


    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if(android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ais_cam);

        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        final ArrayList<String> args = new ArrayList<>();
        mLibVLC = new LibVLC(this, args);

        mMediaPlayer = new MediaPlayer(mLibVLC);

        mConfig = new Config(getApplicationContext());

        if (mConfig.getSipAspectRatio().contains(":")) {
            mMediaPlayer.setAspectRatio(mConfig.getSipAspectRatio());
        } else {
            mMediaPlayer.setAspectRatio("2:1");
        }


        mSurface = findViewById(R.id.video_layout);

        // get timeout
        mCallingTimeOut = mConfig.getSipTimeout();

        // exit
        Button exitCamButton = findViewById(R.id.cam_activity_exit);
        exitCamButton.setOnClickListener(v -> {
            mRingsActive = false;
            finish();
        });


        // echo on
        Button echoOnButton = findViewById(R.id.cam_activity_eho_on);
        echoOnButton.setOnClickListener(v -> {
            Toast.makeText(getBaseContext(), "Start Echo Test", Toast.LENGTH_SHORT).show();
            Core core = EasyLinphone.getLC();
            if (core != null) {
                core.enableEchoCancellation(false);
                core.addListener(
                        new CoreListenerStub() {
                            @Override
                            public void onEcCalibrationResult(
                                    Core core, EcCalibratorStatus status, int delayMs) {
                                Log.d(TAG, "startEchoCancellerCalibration status " + status + ", delay " + delayMs);
                                if (status == EcCalibratorStatus.InProgress) return;
                                core.removeListener(this);
                                core.enableEchoCancellation(true);
                                Toast.makeText(getBaseContext(), "Start Echo Canceller, delay " + delayMs, Toast.LENGTH_SHORT).show();
                            }
                        });
                int ec = core.startEchoCancellerCalibration();
                Log.i(TAG, "startEchoCancellerCalibration " + ec);
            }
        });


        // picture
        Button screenshotCamButton = findViewById(R.id.cam_activity_screenshot);
        screenshotCamButton.setOnClickListener(v -> screenshotCamButton());

        // open 1
        Button openGateCamButton = findViewById(R.id.cam_activity_open_gate);
        openGateCamButton.setOnClickListener(v -> openGateCamButton());

        // open 2
        Button openGate2CamButton = findViewById(R.id.cam_activity_open_gate2);
        openGate2CamButton.setOnClickListener(v -> openGate2CamButton());

        // settings
        Button settingsSipCamButton = findViewById(R.id.cam_activity_settings);
        settingsSipCamButton.setOnClickListener(v -> updateSipPreferences());

        // answer
        Button answerSipCamButton = findViewById(R.id.cam_activity_answer_call);
        answerSipCamButton.setOnClickListener(v -> {
            mRingsActive = false;

            // check the mic settings
            int permissionMicrophone = ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
            if (permissionMicrophone != PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(
                            new String[] { Manifest.permission.RECORD_AUDIO },
                            AisCoreUtils.REQUEST_RECORD_PERMISSION);

                    return;
                }
            }

            //
            if (AisCoreUtils.mAisSipActiveCall != null){
                try {
                    // Answer the current call
                    EasyLinphone.acceptCall();
                    Toast.makeText(getBaseContext(), R.string.sip_answering_call_text, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e("AIS", e.getMessage());
                }
            }
            else {
                // call to someone
                showDialog(SIP_CALL_ADDRESS);
            }

            // info to ha
            try {
                // send camera button event
                JSONObject jMessage = new JSONObject();
                jMessage.put("event_type", "ais_video_ring_button_pressed");
                JSONObject jData = new JSONObject();
                jData.put("button", "answer");
                jData.put("camera_entity_id", mHaCamId);
                jData.put("calling_user_name", mCallingUserName);
                jMessage.put("event_data", jData);
                DomWebInterface.publishJson(jMessage, "event", getApplicationContext());
            } catch (Exception e) {
                Log.e("Exception", e.toString());
            }

        });

        // end call
        Button endCallSipCamButton = findViewById(R.id.cam_activity_end_call);
        endCallSipCamButton.setOnClickListener(v -> {
            mRingsActive = false;

            // end incoming call
            if (AisCoreUtils.mAisSipActiveCall != null) {
                // info to ha
                try {
                    // send button event
                    JSONObject jMessage = new JSONObject();
                    jMessage.put("event_type", "ais_video_ring_button_pressed");
                    JSONObject jData = new JSONObject();
                    jData.put("button", "answer");
                    jData.put("camera_entity_id", mHaCamId);
                    jData.put("calling_user_name", mCallingUserName);
                    jMessage.put("event_data", jData);
                    DomWebInterface.publishJson(jMessage, "event", getApplicationContext());
                } catch (Exception e) {
                    Log.e("Exception", e.toString());
                }

                try {
                    EasyLinphone.hangUp();
                } catch (Exception se) {
                    Log.d(TAG, "Error ending call.", se);
                }
                AisCoreUtils.mAisSipActiveCall = null;
                Toast.makeText(getBaseContext(), R.string.sip_ending_call_text, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getBaseContext(), R.string.sip_ending_no_call_to_end_text, Toast.LENGTH_SHORT).show();
            }

        });


        // video talk can be a serious pain when the screen keeps turning off.
        // Let's prevent that.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //
        updateSipServerStatus(mAisSipStatus);

        // BROADCAST
        IntentFilter filter = new IntentFilter();
        filter.addAction(AisCoreUtils.BROADCAST_SIP_STATUS);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(this);
        bm.registerReceiver(mBroadcastReceiver, filter);
    }


    private void openGateCamButton() {

        // open gate1
        doGet(mConfig.getSipLocalGate1OpenUrl(), getApplicationContext());

        // info to ha
        try {
            // send camera button event
            JSONObject jMessage = new JSONObject();
            jMessage.put("event_type", "ais_video_ring_button_pressed");
            JSONObject jData = new JSONObject();
            jData.put("button", "open1");
            jData.put("camera_entity_id", mHaCamId);
            jData.put("calling_user_name", mCallingUserName);
            jMessage.put("event_data", jData);
            DomWebInterface.publishJson(jMessage, "event", getApplicationContext());
        } catch (Exception e) {
            Log.e("Exception", e.toString());
        }
    }

    private void openGate2CamButton() {

        // open gate2
        doGet(mConfig.getSipLocalGate2OpenUrl(), getApplicationContext());

        try {
            // send camera button event
            JSONObject jMessage = new JSONObject();
            jMessage.put("event_type", "ais_video_ring_button_pressed");
            JSONObject jData = new JSONObject();
            jData.put("button", "open2");
            jData.put("camera_entity_id", mHaCamId);
            jData.put("calling_user_name", mCallingUserName);
            jMessage.put("event_data", jData);
            DomWebInterface.publishJson(jMessage, "event", getApplicationContext());
        } catch (Exception e) {
            Log.e("Exception", e.toString());
        }
    }

    //Share ScreenShot
    private void shareScreenShot(File imageFile) {

        //Using sub-class of Content provider
        Uri photoURI = FileProvider.getUriForFile(getApplicationContext(), this.getApplicationContext().getPackageName() + ".provider", imageFile);

        //Explicit intent
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.setType("image/*");
        intent.putExtra(android.content.Intent.EXTRA_TEXT, "AIS Doorbell");
        intent.putExtra(Intent.EXTRA_STREAM, photoURI);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        //It will show the application which are available to share Image; else Toast message will throw.
        try {
            this.startActivity(Intent.createChooser(intent, "AIS Share With"));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No App to Share Available", Toast.LENGTH_SHORT).show();
        }
    }

    private void takeScreenShot(View view) {

        //This is used to provide file name with Date a format
        Date date = new Date();
        CharSequence format = DateFormat.format("yyyyMMdd_hhmmss", date);

        //It will make sure to store file to given below Directory and If the file Directory dosen't exist then it will create it.
        try {
            File mainDir = new File(
                    this.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "FilShare");
            if (!mainDir.exists()) {
                boolean mkdir = mainDir.mkdir();
            }

            //Providing file name along with Bitmap to capture screenview
            String path = mainDir + "/" + "AIS" + "-" + format + ".jpeg";
            view.setDrawingCacheEnabled(true);
            Bitmap bitmap = Bitmap.createBitmap(view.getDrawingCache());
            view.setDrawingCacheEnabled(false);

            File imageFile = new File(path);
            FileOutputStream fileOutputStream = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, fileOutputStream);
            fileOutputStream.flush();
            fileOutputStream.close();

            shareScreenShot(imageFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void getBitMapFromSurfaceView(SurfaceView videoView) {
        Bitmap bitmap = Bitmap.createBitmap(
                videoView.getWidth(),
                videoView.getHeight(),
                Bitmap.Config.ARGB_8888
        );
        try {
            // Create a handler thread to offload the processing of the image.
            HandlerThread handlerThread = new HandlerThread("PixelCopier");
            handlerThread.start();

            // Make the request to copy.
            PixelCopy.request(videoView, bitmap, (copyResult) -> {
                if (copyResult == PixelCopy.SUCCESS) {

                    try {
                        // save to file
                        Date date = new Date();
                        CharSequence format = DateFormat.format("yyyyMMdd_hhmmss", date);
                        File mainDir = new File(
                                this.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "FilShare");
                        if (!mainDir.exists()) {
                            boolean mkdir = mainDir.mkdir();
                        }
                        String path = mainDir + "/" + "AIS" + "-" + format + ".jpeg";
                        File imageFile = new File(path);
                        FileOutputStream fileOutputStream = new FileOutputStream(imageFile);
                        bitmap.compress(Bitmap.CompressFormat.PNG, 90, fileOutputStream);
                        fileOutputStream.flush();
                        fileOutputStream.close();

                        shareScreenShot(imageFile);
                    } catch (IOException e) {
                        Log.e(TAG, e.toString());
                        return;
                    }
                } else {
                    Log.e(TAG, "Failed to copyPixels: " + copyResult);
                }
                handlerThread.quitSafely();
            }, new Handler(handlerThread.getLooper()));

        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }


    private void screenshotCamButton() {

        //takeScreenShot(mVideoLayout);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            getBitMapFromSurfaceView(mSurface);
        } else {
            takeScreenShot(mSurface);
        }

        try {
            // send camera button event
            JSONObject jMessage = new JSONObject();
            jMessage.put("event_type", "ais_video_ring_button_pressed");
            JSONObject jData = new JSONObject();
            jData.put("button", "picture");
            jData.put("camera_entity_id", mHaCamId);
            jData.put("calling_user_name", mCallingUserName);
            jMessage.put("event_data", jData);
            DomWebInterface.publishJson(jMessage, "event", getApplicationContext());
        } catch (Exception e) {
            Log.e("Exception", e.toString());
        }
        Toast.makeText(getBaseContext(),R.string.sip_cam_photo_text, Toast.LENGTH_SHORT).show();
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(AisCoreUtils.BROADCAST_SIP_STATUS)) {
                updateSipServerStatus(mAisSipStatus);
                if (mAisSipStatus.equals("callEnd")){
                    // end call
                    AisCoreUtils.mAisSipActiveCall = null;
                    mRingsActive = false;
                } else if (mAisSipStatus.equals("incomingCall")) {
                    // start call
                }
            }
        }
    };


    @Override
    protected void onDestroy() {
        super.onDestroy();
        mMediaPlayer.release();
        mLibVLC.release();

        try {
            LocalBroadcastManager bm = LocalBroadcastManager.getInstance(this);
            bm.unregisterReceiver(mBroadcastReceiver);
        } catch (Exception e){
            Log.e(TAG, "unregisterReceiver " + e.toString());
        }
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Checks the orientation of the screen
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(displayMetrics);
        IVLCVout vout = mMediaPlayer.getVLCVout();
        vout.detachViews();
        vout.setVideoView(mSurface);
        vout.setWindowSize(displayMetrics.widthPixels, displayMetrics.heightPixels);
        vout.attachViews();

    }

    @Override
    protected void onStart() {
        super.onStart();

        //
        if (mConfig.getSipAspectRatio().contains(":")) {
            mMediaPlayer.setAspectRatio(mConfig.getSipAspectRatio());
        } else {
            mMediaPlayer.setAspectRatio("2:1");
        }

        // switch to selected speaker
        try {
            EasyLinphone.switchSpeaker(mConfig.getSipAudioDeviceId());
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }

        // switch to selected microphone
        try {
            EasyLinphone.switchMicrophone(mConfig.getSipMicDeviceId());
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }

        // set mic gain
        try {
            float micGain = Float.parseFloat(mConfig.getSipMicGain());
            EasyLinphone.getLC().setMicGainDb(micGain);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }

        // set speaker gain
        try {
            float speakerGain = Float.parseFloat(mConfig.getSipSpeakerGain());
            EasyLinphone.getLC().setPlaybackGainDb(speakerGain);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }

        // open 2
        Button openGate2CamButton = findViewById(R.id.cam_activity_open_gate2);
        openGate2CamButton.setOnClickListener(v -> openGate2CamButton());
        // hide 2 button if not set
        LinearLayout openGate2LinearLayout=  findViewById(R.id.cam_activity_open_gate2_ll);
        LinearLayout.LayoutParams lParam;
        if (mConfig.getSipLocalGate2OpenUrl().equals("")) {
            openGate2LinearLayout.setVisibility(View.GONE);

        } else {
            openGate2LinearLayout.setVisibility(View.VISIBLE);
        }

        boolean sipCall = false;
        Intent intent = getIntent();

        // get url from settings
        mUrl = mConfig.getSipLocalCamUrl();
        if (mUrl.equals("") && intent.hasExtra(AisCoreUtils.BROADCAST_CAMERA_COMMAND_URL)) {
            mUrl = intent.getStringExtra(AisCoreUtils.BROADCAST_CAMERA_COMMAND_URL);
        }


        if  (intent.hasExtra(AisCoreUtils.BROADCAST_CAMERA_HA_ID)) {
            mHaCamId = intent.getStringExtra(AisCoreUtils.BROADCAST_CAMERA_HA_ID);
        }
        if  (intent.hasExtra(AisCoreUtils.BROADCAST_CAMERA_SIP_CALL)) {
            sipCall = intent.getBooleanExtra(AisCoreUtils.BROADCAST_CAMERA_SIP_CALL, false);
        }

        // cam view
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(displayMetrics);
        IVLCVout vout = mMediaPlayer.getVLCVout();
        vout.detachViews();
        vout.setVideoView(mSurface);
        vout.setWindowSize(displayMetrics.widthPixels, displayMetrics.heightPixels);
        vout.attachViews();

        try {
            final Media media = new Media(mLibVLC, Uri.parse(mUrl));
            mMediaPlayer.setMedia(media);
            media.release();
        } catch (Exception e) {
            Toast.makeText(getBaseContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

        mMediaPlayer.play();

        // sip call
        if (sipCall) {
            // we have sip call - ring
            Intent ttsIntent = new Intent(AisCoreUtils.BROADCAST_SERVICE_SAY_IT);
            mCallingUserName = "dzwonek";
            mCallingUserName = AisCoreUtils.mAisSipActiveCall.getRemoteAddress().getUsername();
            // to allow recall
            AisCoreUtils.mLastCallingSipAddress = mCallingUserName;
            // say the calling name
            AisCoreUtils.AIS_DOM_LAST_TTS = "";
            ttsIntent.putExtra(AisCoreUtils.BROADCAST_SAY_IT_TEXT, mCallingUserName);
            LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
            bm.sendBroadcast(ttsIntent);


            // ring
            try {
                // ring start time
                mRingStartTime.setToNow();
                mRingsActive = true;

                Runnable myRunnable = new Runnable() {
                    @Override
                    public void run() {
                        while (mRingsActive) {
                            try {
                                Thread.sleep(1500); // Waits for 1.5 second (1500 milliseconds)
                                // mediaPlayer.start();
                                // hangup after timeout
                                mcurrnetTime.setToNow();
                                long ringingTime = TimeUnit.MILLISECONDS.toSeconds(mcurrnetTime.toMillis(true) - mRingStartTime.toMillis(true));
                                Log.d(TAG, "ringingTime " + ringingTime);
                                if (ringingTime > mCallingTimeOut ) {
                                    Log.i(TAG, "ringingTime hangUp after " + ringingTime);
                                    try {
                                        EasyLinphone.hangUp();
                                    } catch (Exception se) {
                                        Log.e(TAG, "Error ending call.", se);
                                    }
                                    mRingsActive = false;
                                }
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    };
                };
                Thread myThread = new Thread(myRunnable);
                myThread.start();
            } catch (Exception e) {
                e.printStackTrace();
            }

            // close
            try {
                Runnable myRunnable = new Runnable() {
                    @Override
                    public void run() {
                        while (mRingsActive || AisCoreUtils.mAisSipActiveCall != null) {
                            try {
                                Thread.sleep(3000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        // finish
                        try {
                            Thread.sleep(6000);
                            // go back to app
                            // finish();
                            Intent camActivity = new Intent(getApplicationContext(), BrowserActivityNative.class);
                            camActivity.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                            getApplicationContext().startActivity(camActivity);


                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    };
                };
                Thread myThread = new Thread(myRunnable);
                myThread.start();
            } catch (Exception e) {
                e.printStackTrace();
            }

            // call animation
            final Animation animShake = AnimationUtils.loadAnimation(this, R.anim.shake);
            Button answerSipCamButton = findViewById(R.id.cam_activity_answer_call);
            answerSipCamButton.startAnimation(animShake);
        } else {
            Toast.makeText(getBaseContext(), "CAM url: " + mUrl, Toast.LENGTH_SHORT).show();
            // SIP when we get back from the preference setting Activity, assume
            // settings have changed, and re-login with new auth info.
            Intent sipIntent = new Intent(getBaseContext(), AisPanelService.class);
            sipIntent.putExtra(AisCoreUtils.BROADCAST_SIP_COMMAND, "SIP_ON");
            getBaseContext().startService(sipIntent);
        }

        updateSipServerStatus(mAisSipStatus);
    }

    @Override
    protected void onStop() {
        super.onStop();

        mMediaPlayer.stop();
        mMediaPlayer.detachViews();
    }


        @Override
        protected Dialog onCreateDialog(int id) {
            switch (id) {
                case SIP_CALL_ADDRESS:

                    LayoutInflater factory = LayoutInflater.from(this);
                    final View textBoxView = factory.inflate(R.layout.call_address_dialog, null);
                    EditText textField = (EditText) (textBoxView.findViewById(R.id.calladdress_edit));

                    textField.setText(AisCoreUtils.mLastCallingSipAddress);


                    return new AlertDialog.Builder(this)
                            .setTitle(R.string.sip_call_someone_text)
                            .setView(textBoxView)
                            .setPositiveButton(
                                    android.R.string.ok, new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int whichButton) {
                                            EditText textField = (EditText)
                                                    (textBoxView.findViewById(R.id.calladdress_edit));
                                            AisCoreUtils.mLastCallingSipAddress = textField.getText().toString();
                                            initiateCall();

                                        }
                                    })
                            .setNegativeButton(
                                    android.R.string.cancel, new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int whichButton) {
                                            // Noop.
                                        }
                                    })
                            .create();

            }
            return null;
        }
        public void updateSipPreferences() {
            Intent settingsActivity = new Intent(getBaseContext(),
                    SipSettings.class);
            startActivity(settingsActivity);
        }


    /**
     * Make an outgoing call.
     */
    public void initiateCall() {
        updateSipServerStatus(AisCoreUtils.mLastCallingSipAddress);
        // Make a call
        AisCoreUtils.mAisSipActiveCall = EasyLinphone.callTo(AisCoreUtils.mLastCallingSipAddress, false);
    }

    /**
     * Updates the status box at the top of the UI with a messege of your choice.
     * @param status The String to display in the status box.
     */
    public void updateSipServerStatus(final String status) {
        // Be a good citizen.  Make sure UI changes fire on the UI thread.
        this.runOnUiThread(new Runnable() {
            public void run() {
                try {
                    String statusDisp = "No SIP connection, check settings -> ";
                    if (status != null && !status.equals("")){
                        statusDisp = status;
                    }

                    if (mAisSipStatus.equals("incomingCall")) {
                        statusDisp = "-> " + AisCoreUtils.mAisSipActiveCall.getRemoteAddress().getUsername();
                    }

                    if (mAisSipStatus.equals("Ready")){
                        statusDisp = "AIS " + mAisSipStatus;


                        EasyLinphone.switchSpeaker("");
                    }

                    TextView labelView = findViewById(R.id.sipServerStatusLabel);
                    labelView.setText(statusDisp);
                } catch (Exception e) {
                    Log.d(TAG, "Error ", e);
                }
            }
        });
    }


    private static void doGet(String urlToGet, Context context) {
        // do the simple HTTP get
        String webUrl = mConfig.getSipLocalGate1OpenUrl();
        // Instantiate the RequestQueue.
        RequestQueue queue = Volley.newRequestQueue(context);

        // Request a string response from the provided URL.
        StringRequest stringRequest = new StringRequest(Request.Method.GET, urlToGet,
                response -> {
                    Toast.makeText(context, R.string.sip_opening_text, Toast.LENGTH_SHORT).show();
                }, error -> Toast.makeText(context, "Error!", Toast.LENGTH_SHORT).show());

        // Add the request to the RequestQueue.
        queue.add(stringRequest);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // cam view


    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }
}
