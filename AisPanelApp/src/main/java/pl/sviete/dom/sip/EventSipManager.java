package pl.sviete.dom.sip;

import static pl.sviete.dom.AisCoreUtils.BROADCAST_CAMERA_COMMAND_URL;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_CAMERA_SIP_CALL;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_SIP_STATUS;
import static pl.sviete.dom.AisCoreUtils.mAisSipStatus;

import java.net.SocketException;

import net.sourceforge.peers.Config;
import net.sourceforge.peers.FileLogger;
import net.sourceforge.peers.Logger;
import net.sourceforge.peers.sip.Utils;
import net.sourceforge.peers.sip.core.useragent.SipListener;
import net.sourceforge.peers.sip.core.useragent.UserAgent;
import net.sourceforge.peers.sip.syntaxencoding.SipUriSyntaxException;
import net.sourceforge.peers.sip.transactionuser.Dialog;
import net.sourceforge.peers.sip.transactionuser.DialogManager;
import net.sourceforge.peers.sip.transport.SipRequest;
import net.sourceforge.peers.sip.transport.SipResponse;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import pl.sviete.dom.AisCamActivity;
import pl.sviete.dom.AisCoreUtils;


public class EventSipManager implements SipListener {

    public String TAG = "AIS SIP";
    private UserAgent userAgent;
    private Context sipContext;


    public EventSipManager(Context context) throws SocketException {
        Config config = new CustomSipConfig();

        sipContext = context;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String username = prefs.getString("setting_local_sip_client_name", "u1");
        String domain = prefs.getString("setting_local_gate_ip", "10.10.10.10");
        String password = prefs.getString("setting_local_sip_client_password", "p1");
        if (domain.equals("ais_auto")) {
            domain = prefs.getString("setting_local_gate_ip", "10.10.10.10");
        }

        config.setDomain(domain);
        config.setUserPart(username);
        config.setPassword(password);

        Logger logger = new FileLogger(null);
        AndroidSoundManager javaxSoundManager = new AndroidSoundManager();
        userAgent = new UserAgent(this, config, logger, javaxSoundManager);
        new Thread() {
            public void run() {
                try {
                    userAgent.register();
                } catch (SipUriSyntaxException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }


    //
    /**
     * Updates the status box at the top of the UI with a message of your choice.
     * @param status The String to display in the status box.
     */
    public void updateAisSipStatus(final String status) {
        mAisSipStatus = status;
        Intent intent = new Intent(BROADCAST_SIP_STATUS);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(sipContext);
        bm.sendBroadcast(intent);
    }


    // Action methods

    public void call(final String callee) {
        new Thread() {
            @Override
            public void run() {
                try {
                    AisCoreUtils.mAisSipRequest = userAgent.invite(callee, null);
                } catch (SipUriSyntaxException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    public void hangup() {
        new Thread() {
            @Override
            public void run() {
                userAgent.terminate(AisCoreUtils.mAisSipRequest);
            }
        }.start();
    }

    public void accept() {
        new Thread() {
            @Override
            public void run() {
                String callId = Utils.getMessageCallId(AisCoreUtils.mAisSipRequest);
                DialogManager dialogManager = userAgent.getDialogManager();

                Dialog dialog = dialogManager.getDialog(callId);
                userAgent.acceptCall(AisCoreUtils.mAisSipRequest, dialog);
            }
        }.start();
    }

    public void reject() {
        new Thread() {
            @Override
            public void run() {
                userAgent.rejectCall(AisCoreUtils.mAisSipRequest);
            }
        }.start();
    }



    // SipListener methods

    @Override
    public void registering(SipRequest sipRequest) {
        updateAisSipStatus("registering");
        Log.i(TAG, "registering");
    }

    @Override
    public void registerSuccessful(SipResponse sipResponse) {
        updateAisSipStatus("registerSuccessful");
        Log.i(TAG, "registerSuccessful");
    }

    @Override
    public void registerFailed(SipResponse sipResponse) {
        updateAisSipStatus("registerFailed");
        Log.i(TAG, "registerFailed");
    }

    @Override
    public void incomingCall(SipRequest sipRequest, SipResponse provResponse) {
        updateAisSipStatus("incomingCall");
        AisCoreUtils.mAisSipRequest = sipRequest;
        AisCoreUtils.mAisSipResponse = provResponse;

        Log.i(TAG, "incomingCall");
        try {
            Intent camActivity = new Intent(sipContext, AisCamActivity.class);
            camActivity.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            pl.sviete.dom.Config config = new pl.sviete.dom.Config(sipContext);
            camActivity.putExtra(BROADCAST_CAMERA_COMMAND_URL, config.getSipLocalCamUrl());
            camActivity.putExtra(BROADCAST_CAMERA_SIP_CALL, true);
            sipContext.startActivity(camActivity);

        } catch (Exception e) {
            Log.e("AIS", e.getMessage());
        }
    }

    @Override
    public void remoteHangup(SipRequest sipRequest) {
        updateAisSipStatus("remoteHangup");
        Log.i(TAG, "remoteHangup");
    }

    @Override
    public void ringing(SipResponse sipResponse) {
        updateAisSipStatus("ringing");
        Log.i(TAG, "ringing");
    }

    @Override
    public void calleePickup(SipResponse sipResponse) {
        updateAisSipStatus("calleePickup");
        Log.i(TAG, "calleePickup");
    }

    @Override
    public void error(SipResponse sipResponse) {
        updateAisSipStatus("error");
        Log.i(TAG, "error");
    }

}
