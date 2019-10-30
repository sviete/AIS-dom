package pl.sviete.dom;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import pl.sviete.dom.mdns.NsdController;

public class SettingsActivity extends AppCompatPreferenceActivity {

    static final String TAG = SettingsActivity.class.getName();
    static private int mClickNo = 0;
    private ProgressDialog mProgressDialogResetApp;
    static String RESET_APP_DOWNLOAD_TASK = "RESET_APP_DOWNLOAD_TASK";
    static String RESET_APP_DELETE_TASK = "RESET_APP_DELETE_TASK";

    private static final Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();

            if(preference instanceof SwitchPreference){
                //((SwitchPreference) preference).setChecked((boolean)value);
                //preference.setSummary(value.toString());
                return true;
            }

            if (preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);

                // Set the summary to reflect the new value.
                preference.setSummary(
                        index >= 0
                                ? listPreference.getEntries()[index]
                                : null);

            } else {
                // For all other preferences, set the summary to the value's
                // simple string representation.

                preference.setSummary(stringValue);
            }
            return true;
        }
    };

    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of text below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     *
     * @see #sBindPreferenceSummaryToValueListener
     */
    private static void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        if (preference instanceof SwitchPreference) {
            sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                    PreferenceManager
                            .getDefaultSharedPreferences(preference.getContext())
                            .getBoolean(preference.getKey(), false));
        } else {
            sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                    PreferenceManager
                            .getDefaultSharedPreferences(preference.getContext())
                            .getString(preference.getKey(), ""));
        }

    }

    protected boolean isValidFragment(String fragmentName) {
        return PreferenceFragment.class.getName().equals(fragmentName)
                || GeneralPreferenceFragment.class.getName().equals(fragmentName);
    }

    @Override
    public void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        // reset progress dialog
        Intent startIntent = getIntent();
        boolean doResetDownload = startIntent.getBooleanExtra(RESET_APP_DOWNLOAD_TASK, false);
        boolean doResetDelete = startIntent.getBooleanExtra(RESET_APP_DELETE_TASK, false);

        if (doResetDownload){
            Log.e(TAG, "RESET_APP_DOWNLOAD_TASK");
            mProgressDialogResetApp = new ProgressDialog(SettingsActivity.this);
            mProgressDialogResetApp.setMessage(getString(R.string.pref_ais_dom_reset_download_info));
            mProgressDialogResetApp.setIndeterminate(true);
            mProgressDialogResetApp.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialogResetApp.setCancelable(false);

            final ResetAppTaskDownload resetAppTask = new ResetAppTaskDownload(this);
            resetAppTask.execute("https://powiedz.co/ota/bootstrap/files.tar.7z");

            mProgressDialogResetApp.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    resetAppTask.cancel(false); //do not allow to cancel the task
                }
            });
        }

        if (doResetDelete){
            Log.e(TAG, "RESET_APP_DELETE_TASK");
            mProgressDialogResetApp = new ProgressDialog(SettingsActivity.this);
            mProgressDialogResetApp.setMessage(getString(R.string.pref_ais_dom_reset_delete_info));
            mProgressDialogResetApp.setIndeterminate(true);
            mProgressDialogResetApp.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mProgressDialogResetApp.setCancelable(false);

            final ResetAppTaskDelete resetAppTask = new ResetAppTaskDelete(this);
            resetAppTask.execute("");

            mProgressDialogResetApp.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    resetAppTask.cancel(false); //do not allow to cancel the task
                }
            });
        }
    }


    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class GeneralPreferenceFragment extends PreferenceFragment {


        @Override
        public void onResume() {
            super.onResume();
        }

        private void prepareGateList(){
            Log.i(TAG, "prepareGateList");
            try {
                PreferenceCategory category_ais_dom_list = (PreferenceCategory) findPreference("ais_dom_list");
                category_ais_dom_list.removeAll();
                Log.i(TAG, "load gates instances from collection ");
                JSONArray ja = NsdController.get_gates();
                for (int i = 0; i < ja.length(); i++) {
                    JSONObject obj = null;
                    try {
                        obj = ja.getJSONObject(i);
                        String N = obj.getString("name");
                        String G = obj.getString("host");
                        String P = obj.getString("port");
                        String A = obj.getString("address");
                        Log.i(TAG, "adding gate to preferences: " + N + " " + G + " " + P + " " + A);
                        Preference pref = new Preference(getActivity());
                        pref.setKey(A);
                        pref.setTitle(N);
                        pref.setSummary(A + " (" + G + ")");
                        pref.setSelectable(true);
                        pref.setIcon(R.drawable.ic_ais_logo);

                        pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                            public boolean onPreferenceClick(Preference preference) {
                                Config config = new Config(preference.getContext());
                                String mUrl = "http://" + preference.getKey() + ":8180";
                                config.setAppLaunchUrl(mUrl, G);
                                // go to app
                                if (AisCoreUtils.onWatch()){
                                    startActivity(new Intent(preference.getContext(), WatchScreenActivity.class));
                                    return true;
                                } else {
                                    startActivity(new Intent(preference.getContext(), BrowserActivityNative.class));
                                    return true;
                                }

                            }
                        });
                        category_ais_dom_list.addPreference(pref);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
            catch (Exception e){
                e.printStackTrace();
            }
        }


        @Override
        public void onStart() {
            super.onStart();

            prepareGateList();
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.pref_general);

            // get version info
            String versionName = BuildConfig.VERSION_NAME;

            // hide some options on gate / phone
            PreferenceScreen preferenceScreen = (PreferenceScreen) findPreference("ais_dom_main_pref_screen");
            PreferenceCategory prefCategoryAbout = (PreferenceCategory) findPreference("pref_category_about");
            PreferenceCategory prefCategorySettings = (PreferenceCategory) findPreference("pref_category_app_settings");
            PreferenceCategory prefCategoryConnHistory = (PreferenceCategory) findPreference("ais_dom_list_history");
            PreferenceCategory prefCategoryConnList = (PreferenceCategory) findPreference("ais_dom_list");
            PreferenceCategory prefCategoryConnUrl = (PreferenceCategory) findPreference("ais_dom_con_url");
            //
            Preference preferenceVersion = findPreference("pref_ais_dom_version");
            Preference preferenceAppReset = findPreference("pref_ais_dom_app_reset");
            Preference preferenceGoToSpotify = findPreference("pref_ais_dom_spotify");
            Preference preferenceExitApp = findPreference("pref_ais_dom_exit");
            Preference preferenceRemote = findPreference("setting_app_remotemode");
            Preference preferenceAppDisco = findPreference("setting_app_discovery");
            Preference preferenceAppBeepOnClick = findPreference("setting_app_beep_on_click");
            Preference preferenceAppTtsVoice = findPreference("setting_app_tts_voice");
            Preference preferenceAppZoomLevel = findPreference("setting_test_zoomlevel");
            Preference preferenceGesture = findPreference("pref_ais_dom_list_gesture");


                 prefCategorySettings.removePreference(preferenceRemote);
                 // remove beep on click
                 prefCategorySettings.removePreference(preferenceAppBeepOnClick);
                 // remove reset preference
                 prefCategoryAbout.removePreference(preferenceAppReset);
                 // set info in version
                 preferenceVersion.setSummary(versionName + " (client app)");
                 // remove exit preference
                 prefCategoryAbout.removePreference(preferenceExitApp);
                 //
                 prepareGateList();
                 //
                 bindPreferenceSummaryToValue(findPreference(getString(R.string.key_setting_app_launchurl)));

                 // on watch
                if (AisCoreUtils.onWatch()){
                    // remove select tts voice
                    prefCategorySettings.removePreference(preferenceAppTtsVoice);
                    // remove discovery option
                    prefCategorySettings.removePreference(preferenceAppDisco);
                    // remove gesture list
                    prefCategorySettings.removePreference(preferenceGesture);
                    // remove zoom in app
                    prefCategorySettings.removePreference(preferenceAppZoomLevel);

                }




            Preference.OnPreferenceChangeListener preferenceChangeListener = new Preference.OnPreferenceChangeListener(){
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    String summary = newValue+" seconds";
                    preference.setSummary(summary);
                    return true;
                }
            };


        }

    }

    //  subclasses of AsyncTask are declared inside the activity class.
    // that way, we can easily modify the UI thread from here
    class ResetAppTaskDownload extends AsyncTask<String, Integer, String> {

        private Context context;
        private PowerManager.WakeLock mWakeLock;

        public ResetAppTaskDownload(Context context) {
            this.context = context;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            // take CPU lock to prevent CPU from going off if the user
            // presses the power button during download
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
            mWakeLock.acquire();
            mProgressDialogResetApp.show();
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            super.onProgressUpdate(progress);
            // if we get here, length is known, now set indeterminate to false
            mProgressDialogResetApp.setIndeterminate(false);
            mProgressDialogResetApp.setMax(100);
            mProgressDialogResetApp.setProgress(progress[0]);
        }

        @Override
        protected void onPostExecute(String result) {
            mWakeLock.release();
            mProgressDialogResetApp.dismiss();
            if (result != null) {
                Toast.makeText(context, getString(R.string.pref_ais_dom_reset_download_error) + result, Toast.LENGTH_LONG).show();
            }
            else {
                Toast.makeText(context, getString(R.string.pref_ais_dom_reset_download_ok), Toast.LENGTH_SHORT).show();
                Log.i(TAG, "pref_ais_dom_app_reset -> PositiveButton");
                //Intent intent = new Intent(context, SettingsActivity.class);
                //intent.putExtra(RESET_APP_DELETE_TASK, true);
                //startActivity(intent);

                mProgressDialogResetApp = new ProgressDialog(SettingsActivity.this);
                mProgressDialogResetApp.setMessage(getString(R.string.pref_ais_dom_reset_delete_info));
                mProgressDialogResetApp.setIndeterminate(true);
                mProgressDialogResetApp.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                mProgressDialogResetApp.setCancelable(false);

                final ResetAppTaskDelete resetAppTask = new ResetAppTaskDelete(context);
                resetAppTask.execute("");
            }
        }

        @Override
        protected String doInBackground(String... sUrl) {
            InputStream input = null;
            OutputStream output = null;
            HttpURLConnection connection = null;
                try {
                    URL url = new URL(sUrl[0]);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.connect();

                    // expect HTTP 200 OK, so we don't mistakenly save error report
                    // instead of the file
                    if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                        return getString(R.string.pref_ais_dom_reset_download_server_answer) + connection.getResponseCode() + " " + connection.getResponseMessage();
                    }

                    // this will be useful to display download percentage
                    // might be -1: server did not report the length
                    int fileLength = connection.getContentLength();

                    // download the file
                    input = connection.getInputStream();
                    output = new FileOutputStream("/sdcard/files.tar.7z");

                    byte data[] = new byte[4096];
                    long total = 0;
                    int count;
                    while ((count = input.read(data)) != -1) {
                        // allow canceling with back button
                        if (isCancelled()) {
                            input.close();
                            return null;
                        }
                        total += count;
                        // publishing the progress....
                        if (fileLength > 0) // only if total length is known
                            publishProgress((int) (total * 100 / fileLength));
                        output.write(data, 0, count);
                    }
                } catch (Exception e) {
                    return e.toString();
                } finally {
                    try {
                        if (output != null)
                            output.close();
                        if (input != null)
                            input.close();
                    } catch (IOException ignored) {
                    }

                    if (connection != null)
                        connection.disconnect();
                }

            return null;
        }

    }

    //  subclasses of AsyncTask are declared inside the activity class.
    // that way, we can easily modify the UI thread from here
    class ResetAppTaskDelete extends AsyncTask<String, Integer, String> {

        private Context context;
        private PowerManager.WakeLock mWakeLock;

        public ResetAppTaskDelete(Context context) {
            this.context = context;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            // take CPU lock to prevent CPU from going off if the user
            // presses the power button during the delete
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
            mWakeLock.acquire();
            mProgressDialogResetApp.show();
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            super.onProgressUpdate(progress);
            // if we get here, length is known, now set indeterminate to false
            mProgressDialogResetApp.setIndeterminate(false);
            mProgressDialogResetApp.setMax(100);
            mProgressDialogResetApp.setProgress(progress[0]);
        }

        @Override
        protected void onPostExecute(String result) {
            mWakeLock.release();
            mProgressDialogResetApp.dismiss();

            // restart
            Log.i(TAG, "Restart");
            try {
                Runtime.getRuntime().exec(
                        new String[]{"su", "-c", "reboot now"}
                );
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }

        }

        @Override
        protected String doInBackground(String... sParams) {

            try {
                String s = "";
                Process p = Runtime.getRuntime().exec("rm -rf /data/data/pl.sviete.dom/files");
                BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
                BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));

                // read the output from the command
                Log.i(TAG, "read the output from the command");
                while ((s = stdInput.readLine()) != null) {
                    Log.i(TAG, s);
                }

                // read any errors from the attempted command
                while ((s = stdError.readLine()) != null) {
                    Log.e(TAG, s);
                }
            }
            catch (IOException e) {
                Log.e(TAG, "Exception: " + e.getMessage() + e.getStackTrace());
            }

            //
            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException e) {
                Log.e(TAG, "Exception: " + e.getMessage() + e.getStackTrace());
            }

            // try to delete again - problem on BT-BOX
            try {
                String s = "";
                Process p = Runtime.getRuntime().exec("rm -rf /data/data/pl.sviete.dom/files");
                BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
                BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));

                // read the output from the command
                Log.i(TAG, "read the output from the command");
                while ((s = stdInput.readLine()) != null) {
                    Log.i(TAG, s);
                }

                // read any errors from the attempted command
                while ((s = stdError.readLine()) != null) {
                    Log.e(TAG, s);
                }
            }
            catch (IOException e) {
                Log.e(TAG, "Exception: " + e.getMessage() + e.getStackTrace());
            }

            //
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                Log.e(TAG, "Exception: " + e.getMessage() + e.getStackTrace());
            }


            return null;
        }

    }
}

