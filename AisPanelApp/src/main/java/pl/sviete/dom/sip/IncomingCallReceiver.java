package pl.sviete.dom.sip;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.sip.*;
import android.util.Log;

import pl.sviete.dom.AisCamActivity;
import pl.sviete.dom.AisCoreUtils;
import pl.sviete.dom.AisPanelService;
import pl.sviete.dom.Config;

import static pl.sviete.dom.AisCoreUtils.BROADCAST_CAMERA_COMMAND_URL;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_CAMERA_HA_ID;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_CAMERA_SIP_CALL;

/**
 * Listens for incoming SIP calls, intercepts and hands them off to AIS Camera Activity.
 */
public class IncomingCallReceiver extends BroadcastReceiver {
    /**
     * Processes the incoming call, answers it, and hands it over to the Activity
     * @param context The context under which the receiver is running.
     * @param intent The intent being received.
     */
    @Override
    public void onReceive(Context context, Intent intent) {

        try {

            SipAudioCall.Listener listener = new SipAudioCall.Listener() {
                @Override
                public void onRinging(SipAudioCall call, SipProfile caller) {
                    try {
                        Log.e("AIS", "onRinging" + caller.getUserName());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };

            AisCoreUtils.mAisSipIncomingCall = AisPanelService.mAisSipManager.takeAudioCall(intent, listener);

            Intent camActivity = new Intent(context, AisCamActivity.class);
            camActivity.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            //camActivity.addFlags(Intent.FLAG_ACTIVITY_);
            Config config = new Config(context);
            camActivity.putExtra(BROADCAST_CAMERA_COMMAND_URL, config.getSipLocalCamUrl());
            camActivity.putExtra(BROADCAST_CAMERA_SIP_CALL, true);
            context.startActivity(camActivity);

//            Intent camActivity = new Intent(Intent.ACTION_MAIN);
//            camActivity.setComponent(new ComponentName("pl.sviete.dom.client","pl.sviete.dom.AisPanelService"));
//            Config config = new Config(context);
//            camActivity.putExtra(BROADCAST_CAMERA_COMMAND_URL, config.getSipLocalCamUrl());
//            camActivity.putExtra(BROADCAST_CAMERA_SIP_CALL, true);
//            camActivity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//            context.startActivity(camActivity);

        } catch (Exception e) {
            Log.e("AIS", e.getMessage());
        }
    }

}
