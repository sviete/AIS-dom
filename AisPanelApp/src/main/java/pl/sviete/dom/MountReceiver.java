package pl.sviete.dom;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;


public class MountReceiver extends BroadcastReceiver {
    public static final String TAG = "MountReceiver";

    @Override
    public void onReceive(final Context context, Intent intent) {
        Log.i(TAG, intent.getAction());
    }


    private void startAisActivity(Context context) {
        Log.d(TAG, "startAisActivity Called");

        String className = "pl.sviete.dom.BrowserActivityNative";
        if (AisCoreUtils.getRemoteControllerMode().equals(AisCoreUtils.mOffDisplay)){
            className = "pl.sviete.dom.SplashScreenActivity";
        }

        try {
            Intent i = new Intent();
            i.setClassName("pl.sviete.dom", className);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
