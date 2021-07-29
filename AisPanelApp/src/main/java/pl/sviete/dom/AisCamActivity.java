package pl.sviete.dom;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.net.sip.SipAudioCall;
import android.net.sip.SipException;
import android.net.sip.SipProfile;
import android.os.Bundle;
import android.os.Handler;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
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

import static pl.sviete.dom.AisCoreUtils.BROADCAST_CAMERA_COMMAND_URL;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_CAMERA_SIP_CALL;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_SAY_IT_TEXT;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_SERVICE_SAY_IT;
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
            }
        });

        // end call
        Button endCallSipCamButton = (Button) findViewById(R.id.cam_activity_end_call);
        endCallSipCamButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(AisCoreUtils.mAisSipIncomingCall != null) {
                    try {
                        AisCoreUtils.mAisSipIncomingCall.endCall();
                    } catch (SipException se) {
                        Log.d(TAG,"Error ending call.", se);
                    }
                    AisCoreUtils.mAisSipIncomingCall.close();
                    AisCoreUtils.mAisSipIncomingCall = null;
                    Toast.makeText(getBaseContext(),R.string.sip_ending_call_text, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getBaseContext(), R.string.sip_ending_no_call_to_end_text, Toast.LENGTH_SHORT).show();
                }
            }
        });


        // video talk can be a serious pain when the screen keeps turning off.
        // Let's prevent that.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //
        mConfig = new Config(getApplicationContext());
        updateStatus(mAisSipStatus);

        // BROADCAST
        IntentFilter filter = new IntentFilter();
        filter.addAction(AisCoreUtils.BROADCAST_SIP_STATUS);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(this);
        bm.registerReceiver(mBroadcastReceiver, filter);
    }


    private void openGateCamButton() {
            if (mHaCamId != null) {
                try {
                    // send camera button event
                    JSONObject jMessage = new JSONObject();
                    jMessage.put("event_type", "ais_cam_button_pressed");
                    JSONObject jData = new JSONObject();
                    jData.put("button", "open");
                    jData.put("camera_entity_id", mHaCamId);
                    jMessage.put("event_data", jData);
                    DomWebInterface.publishJson(jMessage, "event", getApplicationContext());
                } catch (Exception e) {
                    Log.e("Exception", e.toString());
                }
            }
            Toast.makeText(getBaseContext(),R.string.sip_opening_text, Toast.LENGTH_SHORT).show();
    }

    private void screenshotCamButton() {
        try {
            // send camera button event
            JSONObject jMessage = new JSONObject();
            jMessage.put("event_type", "ais_cam_button_pressed");
            JSONObject jData = new JSONObject();
            jData.put("button", "picture");
            jData.put("camera_entity_id", mHaCamId);
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
                updateStatus(mAisSipStatus);
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

        if (AisCoreUtils.mAisSipIncomingCall != null) {
            AisCoreUtils.mAisSipIncomingCall.close();
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
        if (AisCoreUtils.mAisSipIncomingCall == null) {
            sipCall = false;
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
            // call animation
            final Animation animShake = AnimationUtils.loadAnimation(this, R.anim.shake);
            Button answerSipCamButton = (Button) findViewById(R.id.cam_activity_answer_call);
            answerSipCamButton.startAnimation(animShake);
            Toast.makeText(getBaseContext(), "Call: " + AisCoreUtils.mAisSipIncomingCall.getPeerProfile().getAuthUserName(), Toast.LENGTH_SHORT).show();
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

        updateStatus(mAisSipStatus);
    }

    @Override
    protected void onStop() {
        super.onStop();

        mMediaPlayer.stop();
        mMediaPlayer.detachViews();
    }
    // SIP
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
     * Updates the status box at the top of the UI with a messege of your choice.
     * @param status The String to display in the status box.
     */
    public void updateStatus(final String status) {
        // Be a good citizen.  Make sure UI changes fire on the UI thread.
        this.runOnUiThread(new Runnable() {
            public void run() {
                try {
                    String statusDisp = "No SIP connection, check settings -> ";
                    if (status != null && !status.equals("")){
                        statusDisp = status;
                    }
                    if (statusDisp.equals("Ready")){
                        statusDisp = getString(R.string.sip_video_door_intercom_text) + " " + status;
                    }
                    TextView labelView = (TextView) findViewById(R.id.sipLabel);
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
    public void updateStatus(SipAudioCall call) {
        String useName = call.getPeerProfile().getDisplayName();
        if(useName == null) {
            useName = call.getPeerProfile().getUserName();
        }
        updateStatus(useName + "@" + call.getPeerProfile().getSipDomain());
    }

    /**
     * Make an outgoing call.
     */
    public void initiateCall() {

        updateStatus(sipAddress);

        try {
            SipAudioCall.Listener listener = new SipAudioCall.Listener() {
                // Much of the client's interaction with the SIP Stack will
                // happen via listeners.  Even making an outgoing call, don't
                // forget to set up a listener to set things up once the call is established.
                @Override
                public void onCallEstablished(SipAudioCall call) {
                    call.startAudio();
                    call.setSpeakerMode(true);
                    if(call.isMuted()) {
                        call.toggleMute();
                    }
                    updateStatus(call);
                }

                @Override
                public void onCallEnded(SipAudioCall call) {
                    updateStatus("Ready.");
                }
            };

            AisCoreUtils.mAisSipIncomingCall = AisPanelService.mAisSipManager.makeAudioCall(
                    AisPanelService.mAisSipProfile.getUriString(), sipAddress, listener, 30
            );

        }
        catch (Exception e) {
            Log.i(TAG, "Error when trying to close manager.", e);
        }
    }
    }