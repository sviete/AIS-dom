package pl.sviete.dom;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class AisAdminReceiver extends DeviceAdminReceiver
{
    private static final String TAG = "AisAdminReceiver";

    @Override
    public void onEnabled(Context context, Intent intent){
        Log.d(TAG, "AdminContextManager onEnabled");
        super.onEnabled(context, intent);
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        Log.d(TAG, "AdminContextManager onDisabled");
        super.onDisabled(context, intent);
    }
}
