package pl.sviete.dom;

import android.content.Context;
import android.app.Application;
import android.provider.Settings;
import android.util.Log;

public class AisPanel extends Application {
    public static final String TAG = Context.class.getName();

    @Override
    public void onCreate() {
        super.onCreate();

        //
        Log.i(TAG, "-------------------------------------------");
        Log.i(TAG, "-------------------------------------------");
        Log.i(TAG, "-------------------------------------------");
        Log.i(TAG, "---------AisPanel -> onCreate -------------");
        Log.i(TAG, "-------------------------------------------");
        Log.i(TAG, "-------------------------------------------");
        Log.i(TAG, "-------------------------------------------");


        // set gate ID
        Log.i(TAG, "set gate ID");
        AisCoreUtils.AIS_GATE_ID = "dom-" + Settings.Secure.getString(this.getApplicationContext().getContentResolver(), Settings.Secure.ANDROID_ID);
        Log.i(TAG, "AIS_GATE_ID: " + AisCoreUtils.AIS_GATE_ID);
    }

    @Override
    public void onTerminate() {
        // call the superclass method first
        super.onTerminate();
        Log.i(TAG, "mdns.Stop discover gates in local network");
    }
}
