package pl.sviete.dom;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.net.sip.SipAudioCall;
import android.net.sip.SipException;
import android.net.sip.SipProfile;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONObject;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;
import java.util.ArrayList;

import pl.sviete.dom.sip.SipSettings;
import static pl.sviete.dom.AisCoreUtils.mAisSipStatus;


public class AisCamActivity extends AppCompatActivity  {
    private static final boolean USE_TEXTURE_VIEW = false;
    private static final boolean ENABLE_SUBTITLES = false;
    private VLCVideoLayout mVideoLayout = null;

    private LibVLC mLibVLC = null;
    private MediaPlayer mMediaPlayer = null;

    public String mUrl = null;
    public String mHaCamId = null;

    // SIP
    private static final int SIP_CALL_ADDRESS = 1;
    public String sipAddress = null;

    private static Config mConfig;
    private static final String TAG = "AIS SIP";

    private static boolean mRingsActive = false;
    private static  String mCallingUserName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ais_cam);

        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        final ArrayList<String> args = new ArrayList<>();
        mLibVLC = new LibVLC(this, args);
        mMediaPlayer = new MediaPlayer(mLibVLC);

        mVideoLayout = findViewById(R.id.video_layout);

        // exit
        Button exitCamButton = (Button) findViewById(R.id.cam_activity_exit);
        exitCamButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mRingsActive = false;
                finish();
            }
        });

        // picture
        Button screenshotCamButton = (Button) findViewById(R.id.cam_activity_screenshot);
        screenshotCamButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                screenshotCamButton();
            }
        });

        // open
        Button openGateCamButton = (Button) findViewById(R.id.cam_activity_open_gate);
        openGateCamButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openGateCamButton();
            }
        });

        // settings
        Button settingsSipCamButton = (Button) findViewById(R.id.cam_activity_settings);
        settingsSipCamButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateSipPreferences();
            }
        });

        // answer
        Button answerSipCamButton = (Button) findViewById(R.id.cam_activity_answer_call);
        answerSipCamButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mRingsActive = false;

                //
                if (AisCoreUtils.mAisSipIncomingCall != null){
                    try {
                        Toast.makeText(getBaseContext(),R.string.sip_answering_call_text, Toast.LENGTH_SHORT).show();
                        AisCoreUtils.mAisSipIncomingCall.answerCall(30);
                        AisCoreUtils.mAisSipIncomingCall.startAudio();
                        AisCoreUtils.mAisSipIncomingCall.setSpeakerMode(true);
                        if(AisCoreUtils.mAisSipIncomingCall.isMuted()) {
                            AisCoreUtils.mAisSipIncomingCall.toggleMute();
                        }
                    } catch (Exception e) {
                        Log.e("AIS", e.getMessage());
                        if (AisCoreUtils.mAisSipIncomingCall != null) {
                            AisCoreUtils.mAisSipIncomingCall.close();
                        }
                    }
                }
                else {
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

            }
        });

        // end call
        Button endCallSipCamButton = (Button) findViewById(R.id.cam_activity_end_call);
        endCallSipCamButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mRingsActive = false;

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

                if (AisCoreUtils.mAisSipIncomingCall == null && AisCoreUtils.mAisSipOutgoingCall == null) {
                    Toast.makeText(getBaseContext(), R.string.sip_ending_no_call_to_end_text, Toast.LENGTH_SHORT).show();
                } else {

                    // end incoming call
                    if (AisCoreUtils.mAisSipIncomingCall != null) {
                        try {
                            AisCoreUtils.mAisSipIncomingCall.endCall();
                        } catch (SipException se) {
                            Log.d(TAG, "Error ending call.", se);
                        }
                        AisCoreUtils.mAisSipIncomingCall.close();
                        AisCoreUtils.mAisSipIncomingCall = null;
                        Toast.makeText(getBaseContext(), R.string.sip_ending_call_text, Toast.LENGTH_SHORT).show();
                    }

                    // end outgoing call
                    if (AisCoreUtils.mAisSipOutgoingCall != null) {
                        try {
                            AisCoreUtils.mAisSipOutgoingCall.endCall();
                        } catch (SipException se) {
                            Log.d(TAG, "Error ending call.", se);
                        }
                        AisCoreUtils.mAisSipOutgoingCall.close();
                        AisCoreUtils.mAisSipOutgoingCall = null;
                        Toast.makeText(getBaseContext(), R.string.sip_ending_call_text, Toast.LENGTH_SHORT).show();
                    }
                }

            }
        });


        // video talk can be a serious pain when the screen keeps turning off.
        // Let's prevent that.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //
        mConfig = new Config(getApplicationContext());
        updateSipServerStatus(mAisSipStatus);

        // BROADCAST
        IntentFilter filter = new IntentFilter();
        filter.addAction(AisCoreUtils.BROADCAST_SIP_STATUS);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(this);
        bm.registerReceiver(mBroadcastReceiver, filter);
    }


    private void openGateCamButton() {
        try {
            // send camera button event
            JSONObject jMessage = new JSONObject();
            jMessage.put("event_type", "ais_video_ring_button_pressed");
            JSONObject jData = new JSONObject();
            jData.put("button", "open");
            jData.put("camera_entity_id", mHaCamId);
            jData.put("calling_user_name", mCallingUserName);
            jMessage.put("event_data", jData);
            DomWebInterface.publishJson(jMessage, "event", getApplicationContext());
        } catch (Exception e) {
            Log.e("Exception", e.toString());
        }
        Toast.makeText(getBaseContext(),R.string.sip_opening_text, Toast.LENGTH_SHORT).show();
    }

    private void screenshotCamButton() {
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
            }
            else if (action.equals(AisCoreUtils.BROADCAST_ON_END_HOT_WORD_LISTENING)){
                Log.d(TAG, "HWL stopHotWordListening");

            }
        }
    };


    @Override
    protected void onDestroy() {
        super.onDestroy();
        mMediaPlayer.release();
        mLibVLC.release();

        if (AisCoreUtils.mAisSipOutgoingCall != null) {
            AisCoreUtils.mAisSipOutgoingCall.close();
        }

        try {
            LocalBroadcastManager bm = LocalBroadcastManager.getInstance(this);
            bm.unregisterReceiver(mBroadcastReceiver);
        } catch (Exception e){
            Log.e(TAG, "unregisterReceiver " + e.toString());
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        boolean sipCall = false;
        Intent intent = getIntent();
        if (intent.hasExtra(AisCoreUtils.BROADCAST_CAMERA_COMMAND_URL)) {
            mUrl = intent.getStringExtra(AisCoreUtils.BROADCAST_CAMERA_COMMAND_URL);
        }
        if  (intent.hasExtra(AisCoreUtils.BROADCAST_CAMERA_HA_ID)) {
            mHaCamId = intent.getStringExtra(AisCoreUtils.BROADCAST_CAMERA_HA_ID);
        }
        if  (intent.hasExtra(AisCoreUtils.BROADCAST_CAMERA_SIP_CALL)) {
            sipCall = intent.getBooleanExtra(AisCoreUtils.BROADCAST_CAMERA_SIP_CALL, false);
        }
        if (sipCall) {
            try {
                SipAudioCall.Listener listener = new SipAudioCall.Listener() {
                    @Override
                    public void onRinging(SipAudioCall call, SipProfile caller) {
                        try {
                            Log.e("AIS", "SipAudioCall onRinging" + caller.getUserName());
                            updateSipCallStatus(call,"incoming onRinging");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    @Override
                    public void onReadyToCall(SipAudioCall call) {
                        Log.e("AIS", "SipAudioCall onReadyToCall" );
                        updateSipCallStatus(call,"incoming onReadyToCall");
                    }

                    @Override
                    public void onCalling(SipAudioCall call) {
                        Log.e("AIS", "SipAudioCall onCalling" );
                        updateSipCallStatus(call,"incoming onCalling");
                    }

                    @Override
                    public void onRingingBack(SipAudioCall call) {
                        Log.e("AIS", "SipAudioCall onRingingBack" );
                        updateSipCallStatus(call,"incoming onRingingBack");
                        mRingsActive = false;
                    }

                    @Override
                    public void onCallEstablished(SipAudioCall call) {
                        Log.e("AIS", "SipAudioCall onCallEstablished" );
                        updateSipCallStatus(call,"incoming onCallEstablished");
                        mRingsActive = false;
                    }

                    @Override
                    public void onCallEnded(SipAudioCall call) {
                        mRingsActive = false;
                        AisCoreUtils.mAisSipIncomingCall = null;
                        Log.e("AIS", "SipAudioCall onCallEnded" );
                        updateSipCallStatus(call,"incoming onCallEnded");
                    }

                    @Override
                    public void onCallBusy(SipAudioCall call) {
                        Log.e("AIS", "SipAudioCall onCallBusy" );
                        updateSipCallStatus(call,"incoming onCallBusy");
                        mRingsActive = false;
                    }

                    @Override
                    public void onCallHeld(SipAudioCall call) {
                        Log.e("AIS", "SipAudioCall onCallHeld" );
                        updateSipCallStatus(call,"incoming onCallHeld");
                        mRingsActive = false;
                    }

                    @Override
                    public void onError(SipAudioCall call, int errorCode, String errorMessage) {
                        Log.e("AIS", "SipAudioCall SipAudioCall" );
                        updateSipCallStatus(call,"incoming onError");
                        mRingsActive = false;
                    }

                    @Override
                    public void onChanged(SipAudioCall call) {
                        Log.e("AIS", "SipAudioCall onChanged" );
                        updateSipCallStatus(call,"incoming onChanged");
                    }
                };

                AisCoreUtils.mAisSipIncomingCall = AisPanelService.mAisSipManager.takeAudioCall(AisCoreUtils.mAisSipIncomingCallIntent, listener);
            } catch (SipException e) {
                e.printStackTrace();
            }
        }

        mMediaPlayer.attachViews(mVideoLayout, null, ENABLE_SUBTITLES, USE_TEXTURE_VIEW);
        try {
            final Media media = new Media(mLibVLC, Uri.parse(mUrl));
            mMediaPlayer.setMedia(media);
            media.release();
        } catch (Exception e) {
            Toast.makeText(getBaseContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        mMediaPlayer.play();

        //
        if (sipCall) {
            // we have sip call - ring
            updateSipCallStatus(AisCoreUtils.mAisSipIncomingCall, " onStart");
            Intent ttsIntent = new Intent(AisCoreUtils.BROADCAST_SERVICE_SAY_IT);

            mCallingUserName = "dzwonek";
            if (AisCoreUtils.mAisSipIncomingCall.getPeerProfile() != null) {
                if (AisCoreUtils.mAisSipIncomingCall.getPeerProfile().getAuthUserName() != null) {
                    mCallingUserName = AisCoreUtils.mAisSipIncomingCall.getPeerProfile().getAuthUserName();
                } else {
                    mCallingUserName = AisCoreUtils.mAisSipIncomingCall.getPeerProfile().getUserName();
                }
            }
            AisCoreUtils.AIS_DOM_LAST_TTS = "";
            ttsIntent.putExtra(AisCoreUtils.BROADCAST_SAY_IT_TEXT, mCallingUserName);

            LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
            bm.sendBroadcast(ttsIntent);

            // ring
            try {
                android.media.MediaPlayer mediaPlayer = new android.media.MediaPlayer();
                AssetFileDescriptor descriptor = getApplicationContext().getAssets().openFd("find_my_phone.mp3");
                mediaPlayer.setDataSource(descriptor.getFileDescriptor(), descriptor.getStartOffset(), descriptor.getLength());
                descriptor.close();
                mediaPlayer.prepare();
                mediaPlayer.setVolume(1f, 1f);
                mediaPlayer.setLooping(false);
                mRingsActive = true;
                Runnable myRunnable = new Runnable() {
                    @Override
                    public void run() {
                        while (mRingsActive) {
                            try {
                                Thread.sleep(1500); // Waits for 1.5 second (1500 milliseconds)
                                mediaPlayer.start();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        mediaPlayer.stop();

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
                        while (mRingsActive || AisCoreUtils.mAisSipIncomingCall != null) {
                            try {
                                Thread.sleep(3000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        // finish
                        try {
                            Thread.sleep(6000);
                            finish();
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
            Button answerSipCamButton = (Button) findViewById(R.id.cam_activity_answer_call);
            answerSipCamButton.startAnimation(animShake);
            if (AisCoreUtils.mAisSipIncomingCall.getPeerProfile().getAuthUserName() != null) {
                Toast.makeText(getBaseContext(), "Call: " + AisCoreUtils.mAisSipIncomingCall.getPeerProfile().getAuthUserName(), Toast.LENGTH_SHORT).show();
            }
            else {
                Toast.makeText(getBaseContext(), "Call: " + AisCoreUtils.mAisSipIncomingCall.getPeerProfile().getUserName(), Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(getBaseContext(), "CAM url: " + mUrl, Toast.LENGTH_SHORT).show();
            // SIP when we get back from the preference setting Activity, assume
            // settings have changed, and re-login with new auth info.
            if (!mAisSipStatus.equals("Ready")) {
                Intent sipIntent = new Intent(getBaseContext(), AisPanelService.class);
                sipIntent.putExtra(AisCoreUtils.BROADCAST_SIP_COMMAND, "SIP_ON");
                getBaseContext().startService(sipIntent);
            }
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

                    textField.setText("domofon@" + mConfig.getAppLocalGateIp());
                    return new AlertDialog.Builder(this)
                            .setTitle(R.string.sip_call_someone_text)
                            .setView(textBoxView)
                            .setPositiveButton(
                                    android.R.string.ok, new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int whichButton) {
                                            EditText textField = (EditText)
                                                    (textBoxView.findViewById(R.id.calladdress_edit));
                                            sipAddress = textField.getText().toString();
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

        updateSipServerStatus(sipAddress);

        try {
            SipAudioCall.Listener listener = new SipAudioCall.Listener() {
                // Much of the client's interaction with the SIP Stack will
                // happen via listeners.  Even making an outgoing call, don't
                // forget to set up a listener to set things up once the call is established.
                @Override
                public void onCallEstablished(SipAudioCall call) {
                    Log.e("AIS", "SipAudioCall outgoing onCallEstablished" );
                    call.startAudio();
                    call.setSpeakerMode(true);
                    if(call.isMuted()) {
                        call.toggleMute();
                    }
                    updateSipCallStatus(call, "outgoing onCallEstablished");
                }

                @Override
                public void onCallEnded(SipAudioCall call) {
                    Log.e("AIS", "SipAudioCall outgoing onCallEnded ");
                    updateSipCallStatus(call,"outgoing onCallEnded");
                }

                @Override
                public void onRinging(SipAudioCall call, SipProfile caller) {
                    try {
                        Log.e("AIS", "SipAudioCall onRinging" + caller.getUserName());
                        updateSipCallStatus(call,"outgoing onRinging");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                @Override
                public void onReadyToCall(SipAudioCall call) {
                    Log.e("AIS", "SipAudioCall onReadyToCall" );
                    updateSipCallStatus(call,"outgoing onReadyToCall");
                }

                @Override
                public void onCalling(SipAudioCall call) {
                    Log.e("AIS", "SipAudioCall onCalling" );
                    updateSipCallStatus(call,"outgoing onCalling");
                }

                @Override
                public void onRingingBack(SipAudioCall call) {
                    Log.e("AIS", "SipAudioCall onRingingBack" );
                    updateSipCallStatus(call,"outgoing onRingingBack");
                }

                @Override
                public void onCallBusy(SipAudioCall call) {
                    Log.e("AIS", "SipAudioCall onCallBusy" );
                    updateSipCallStatus(call,"outgoing onCallBusy");
                }

                @Override
                public void onCallHeld(SipAudioCall call) {
                    Log.e("AIS", "SipAudioCall onCallHeld" );
                    updateSipCallStatus(call,"outgoing onCallHeld");
                }

                @Override
                public void onError(SipAudioCall call, int errorCode, String errorMessage) {
                    Log.e("AIS", "SipAudioCall SipAudioCall" );
                    updateSipCallStatus(call,"outgoing onError");
                }

                @Override
                public void onChanged(SipAudioCall call) {
                    Log.e("AIS", "SipAudioCall onChanged" );
                    updateSipCallStatus(call,"outgoing onChanged");
                }
            };

            AisCoreUtils.mAisSipOutgoingCall = AisPanelService.mAisSipManager.makeAudioCall(
                    AisPanelService.mAisSipProfile.getUriString(), sipAddress, listener, 30
            );

        }
        catch (Exception e) {
            Log.i(TAG, "Error when trying to close manager.", e);
        }
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
                    TextView labelView = findViewById(R.id.sipServerStatusLabel);
                    labelView.setText(statusDisp);
                } catch (Exception e) {
                    Log.d(TAG, "Error ", e);
                }
            }
        });
    }

    /**
     * Updates the status box with the SIP address of the current call.
     * @param call The current, active call.
     */
    public void updateSipCallStatus(SipAudioCall call, String text) {
        String useName = "";
        int state = 0;
        try {
             useName = call.getPeerProfile().getDisplayName();
            if (useName == null) {
                useName = call.getPeerProfile().getUserName();
            }
            state = AisCoreUtils.mAisSipIncomingCall.getState();
        }  catch (Exception e) {
            Log.d(TAG, "Error ", e);
        }
        String status = useName + " " + text + " state " + state;
        this.runOnUiThread(new Runnable() {
            public void run() {
                try {
                    String statusDisp = "No SIP connection, check settings -> ";
                    if (status != null && !status.equals("")){
                        statusDisp = status;
                    }
                    TextView labelView = findViewById(R.id.sipCallStatusLabel);
                    labelView.setText(statusDisp);
                } catch (Exception e) {
                    Log.d(TAG, "Error ", e);
                }
            }
        });

    }

}