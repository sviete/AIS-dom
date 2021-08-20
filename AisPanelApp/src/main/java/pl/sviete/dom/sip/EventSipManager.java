package pl.sviete.dom.sip;

import java.net.SocketException;

import net.sourceforge.peers.Config;
import net.sourceforge.peers.FileLogger;
import net.sourceforge.peers.Logger;
//import net.sourceforge.peers.javaxsound.JavaxSoundManager;
import net.sourceforge.peers.sip.Utils;
import net.sourceforge.peers.sip.core.useragent.SipListener;
import net.sourceforge.peers.sip.core.useragent.UserAgent;
import net.sourceforge.peers.sip.syntaxencoding.SipUriSyntaxException;
import net.sourceforge.peers.sip.transactionuser.Dialog;
import net.sourceforge.peers.sip.transactionuser.DialogManager;
import net.sourceforge.peers.sip.transport.SipRequest;
import net.sourceforge.peers.sip.transport.SipResponse;
import android.util.Log;


public class EventManager implements SipListener {

    public String TAG = "AIS";
    private UserAgent userAgent;
    private SipRequest sipRequest;


    public EventManager() throws SocketException {
        Config config = new CustomSipConfig();
        Logger logger = new FileLogger(null);
        //JavaxSoundManager javaxSoundManager = new JavaxSoundManager(false, logger, null);
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



    // Action methods

    public void call(final String callee) {
        new Thread() {
            @Override
            public void run() {
                try {
                    sipRequest = userAgent.invite(callee, null);
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
                userAgent.terminate(sipRequest);
            }
        }.start();
    }

    public void accept() {
        new Thread() {
            @Override
            public void run() {
                String callId = Utils.getMessageCallId(sipRequest);
                DialogManager dialogManager = userAgent.getDialogManager();
                Dialog dialog = dialogManager.getDialog(callId);
                userAgent.acceptCall(sipRequest, dialog);
            }
        }.start();
    }

    public void reject() {
        new Thread() {
            @Override
            public void run() {
                userAgent.rejectCall(sipRequest);
            }
        }.start();
    }



    // SipListener methods

    @Override
    public void registering(SipRequest sipRequest) {
        Log.i(TAG, "registering");
    }

    @Override
    public void registerSuccessful(SipResponse sipResponse) {
        Log.i(TAG, "registerSuccessful");
    }

    @Override
    public void registerFailed(SipResponse sipResponse) {
        Log.i(TAG, "registerFailed");
    }

    @Override
    public void incomingCall(SipRequest sipRequest, SipResponse provResponse) {
        Log.i(TAG, "incomingCall");
    }

    @Override
    public void remoteHangup(SipRequest sipRequest) {
        Log.i(TAG, "remoteHangup");
    }

    @Override
    public void ringing(SipResponse sipResponse) {
        Log.i(TAG, "ringing");
    }

    @Override
    public void calleePickup(SipResponse sipResponse) {
        Log.i(TAG, "calleePickup");
    }

    @Override
    public void error(SipResponse sipResponse) {
        Log.i(TAG, "error");
    }

}
