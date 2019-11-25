package pl.sviete.dom;
import android.app.UiModeManager;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.util.Log;


public class WelcomeActivity extends AppCompatActivity {

    private final String TAG = WelcomeActivity.class.getName();
    private static boolean startup = true;
    public static final String BROADCAST_STAY_ON_SETTNGS_ACTIVITY_VALUE = "BROADCAST_STAY_ON_SETTNGS_ACTIVITY_VALUE";

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction().add(android.R.id.content,
                    new SettingsActivity.GeneralPreferenceFragment()).commit();
        }


        if (startup){

            // set remote control mode on start
            Config config = new Config(this.getApplicationContext());
            AisCoreUtils.setRemoteControllerMode(config.getAppRemoteControllerMode());
            Log.i(TAG, "config.getAppRemoteControllerMode() " + config.getAppRemoteControllerMode());

            // check the device type on start
            UiModeManager uiModeManager = (UiModeManager) getSystemService(UI_MODE_SERVICE);
            if (uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_WATCH) {
                AisCoreUtils.AIS_DEVICE_TYPE = "WATCH";
            } else if (uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION){
                AisCoreUtils.AIS_DEVICE_TYPE = "TV";
            } else {
                AisCoreUtils.AIS_DEVICE_TYPE = "MOB";
            }


            // go to correct activity
            Log.i(TAG, "Redirect to correct screen");
            redirectToActivity();

        }


        setTitle(getString(R.string.ais_dom_app_name_client));


        //
        startup = false;
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_welcome, menu);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean stayOnSettings = false;
        try {
            Intent startIntent = getIntent();
            stayOnSettings = startIntent.getBooleanExtra(BROADCAST_STAY_ON_SETTNGS_ACTIVITY_VALUE, false);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }

        if (!stayOnSettings) {
            redirectToActivity();
        }

    }

    private void redirectToActivity(){
            // on client
            if (AisCoreUtils.onWatch()) {
                Log.i(TAG, "On client. Go to watch on Startup");
                startWatchActivity();
            } else {
                Log.i(TAG, "On client. Go to browser on Startup");
                startBrowserActivity();
            }
    }

    private void startBrowserActivity() {
        Log.d(TAG, "startBrowserActivity Called");
        startActivity(new Intent(getApplicationContext(), BrowserActivityNative.class));
    }

    private void startSplashScreenActivity() {
        Log.d(TAG, "startSplashScreenActivity Called");
        startActivity(new Intent(getApplicationContext(), SplashScreenActivity.class));
    }



    private void startWatchActivity() {
        Log.d(TAG, "startWatchActivity Called");
        startActivity(new Intent(getApplicationContext(), WatchScreenActivity.class));
    }


    public void startMic(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(getApplicationContext())) {
            //If the draw over permission is not available open the settings screen
            //to grant the permission.
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, BrowserActivity.CODE_DRAW_OVER_OTHER_APP_PERMISSION);
        } else {
            Log.i(TAG, "start FloatingViewService");
            startService(new Intent(WelcomeActivity.this, FloatingViewService.class));
            // go back to home
            appExit();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
        if (requestCode == BrowserActivity.CODE_DRAW_OVER_OTHER_APP_PERMISSION) {
            if (Settings.canDrawOverlays(getApplicationContext())) {
                // You have permission, exit
                startMic();
            }
          }
        }
    }

    public void appExit() {
        Log.d(TAG, "onExit Called");
        // going to home screen programmatically
        Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);
        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(startMain);
    }


    @SuppressWarnings("UnusedParameters")
    public void onLaunchDashboard(MenuItem mi) {
        Log.d(TAG, "onLaunchDashboard Called");
        redirectToActivity();
    }

}
