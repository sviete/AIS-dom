package pl.sviete.dom.sip;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.sip.*;
import android.util.Log;

import pl.sviete.dom.AisCoreUtils;
import pl.sviete.dom.AisMediaPlayerActivity;
import pl.sviete.dom.AisPanelService;

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
        SipAudioCall incomingCall = null;
        try {

            SipAudioCall.Listener listener = new SipAudioCall.Listener() {
                @Override
                public void onRinging(SipAudioCall call, SipProfile caller) {
                    try {
                        call.answerCall(30);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };

            incomingCall = AisPanelService.mAisSipManager.takeAudioCall(intent, listener);
            incomingCall.answerCall(30);
            incomingCall.startAudio();
            incomingCall.setSpeakerMode(true);
            if(incomingCall.isMuted()) {
                incomingCall.toggleMute();
            }

            AisPanelService.mAisSipAudioCall = incomingCall;

            Intent camActivity = new Intent(context, WalkieTalkieActivity.class);
            camActivity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            camActivity.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(camActivity);

        } catch (Exception e) {
            Log.e("AIS", e.getMessage());
            if (incomingCall != null) {
                incomingCall.close();
            }
        }
    }

}
