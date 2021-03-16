package pl.sviete.dom;
import android.app.UiModeManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.util.Log;
import android.view.View;

import com.redbooth.wizard.MainWizardActivity;

import ai.picovoice.hotword.PorcupineService;


public class WelcomeActivity extends AppCompatActivity {

    private final String TAG = WelcomeActivity.class.getName();
    private static boolean startup = true;
    public static final String BROADCAST_STAY_ON_SETTNGS_ACTIVITY_VALUE = "BROADCAST_STAY_ON_SETTNGS_ACTIVITY_VALUE";

    // connection config
    public void scanGateOnButtonClick(View view) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(BuildConfig.APPLICATION_ID, "pl.sviete.dom.ScannerActivity");
        startActivity(intent);
    }
    public void nfcGateOnButtonClick(View view) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(BuildConfig.APPLICATION_ID, "pl.sviete.dom.AisNfcActivity");
        intent.putExtra("INFO_TEXT", getString(R.string.scan_nfc_gate_id_from_settings_info_text));
        startActivity(intent);
    }
    public void wizardGateOnButtonClick(View view) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(BuildConfig.APPLICATION_ID, "com.redbooth.wizard.MainWizardActivity");
        startActivity(intent);
    }



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
                // Starting from API 26 you have to to use Context.startForegroundService(Intent) instead of the former startService(Intent)
                // The service must then call startForeground(int, Notification) within first 5 seconds after it has started,
                // otherwise system will stop the service.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    this.getApplicationContext().startForegroundService(porcupineServiceIntent);
                } else {
                    this.getApplicationContext().startService(porcupineServiceIntent);
                }
            }

            // run location service on start
            if (config.getReportLocationMode()) {
                Intent reportLocationServiceIntent = new Intent(this.getApplicationContext(), AisFuseLocationService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    this.getApplicationContext().startForegroundService(reportLocationServiceIntent);
                } else {
                    this.getApplicationContext().startService(reportLocationServiceIntent);
                }
            }

            // run exo player service on start - this need to be done on the end
            if (config.getAppDiscoveryMode()) {
                Intent serviceIntent = new Intent(this.getApplicationContext(), AisPanelService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    this.getApplicationContext().startForegroundService(serviceIntent);
                } else {
                    this.getApplicationContext().startService(serviceIntent);
                }
            }

            // get request Queue
            AisCoreUtils.getRequestQueue(this.getApplicationContext());
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
        if (AisCoreUtils.onBox()){
            startBrowserActivity();
        } else {
            Config config = new Config(this.getApplicationContext());
            if (config.getAppWizardDone()) {
                startBrowserActivity();
            } else {
                // no settings wizard on TV
                if (AisCoreUtils.AIS_DEVICE_TYPE.equals("TV")) {
                    // stay in settings
                    return;
                }
                startWizardActivity();
            }
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


    @SuppressWarnings("UnusedParameters")
    public void onLaunchDashboard(MenuItem mi) {
        Log.d(TAG, "onLaunchDashboard Called");
        redirectToActivity();
    }

}
