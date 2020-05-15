package pl.sviete.dom;
import android.app.UiModeManager;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import androidx.appcompat.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.util.Log;

import com.redbooth.wizard.MainWizardActivity;

import ai.picovoice.hotword.PorcupineService;


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

            // check the device type on start
            UiModeManager uiModeManager = (UiModeManager) getSystemService(UI_MODE_SERVICE);
            if (uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_WATCH) {
                AisCoreUtils.AIS_DEVICE_TYPE = "WATCH";
            } else if (uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION){
                AisCoreUtils.AIS_DEVICE_TYPE = "TV";
            } else {
                AisCoreUtils.AIS_DEVICE_TYPE = "MOB";
            }

            // remember the ais ha webhook id
            AisCoreUtils.AIS_HA_WEBHOOK_ID = config.getAisHaWebhookId();

            // run hot word service on start
            if (config.getHotWordMode()) {
                Intent porcupineServiceIntent = new Intent(this.getApplicationContext(), PorcupineService.class);
                this.getApplicationContext().startService(porcupineServiceIntent);
            }

            // run exo player service on start
            if (config.getAppDiscoveryMode()) {
                Intent serviceIntent = new Intent(this.getApplicationContext(), AisPanelService.class);
                this.getApplicationContext().startService(serviceIntent);
            }
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
        Log.i(TAG, "On client. Go to browser on Startup");
        Config config = new Config(this.getApplicationContext());
        if (config.getAppWizardDone()){
            startBrowserActivity();
        } else {
            startWizardActivity();
        }
    }

    private void startBrowserActivity() {
        Log.d(TAG, "startBrowserActivity Called");
        startActivity(new Intent(getApplicationContext(), BrowserActivityNative.class));
    }

    private void startWizardActivity() {
        Log.d(TAG, "startWizardActivity Called");
        startActivity(new Intent(getApplicationContext(), MainWizardActivity.class));
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
