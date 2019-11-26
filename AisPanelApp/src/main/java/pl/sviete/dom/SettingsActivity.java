package pl.sviete.dom;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.Log;




public class SettingsActivity extends AppCompatPreferenceActivity {

    static final String TAG = SettingsActivity.class.getName();
    static private int mClickNo = 0;

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

//    protected boolean isValidFragment(String fragmentName) {
//        return PreferenceFragment.class.getName().equals(fragmentName)
//                || GeneralPreferenceFragment.class.getName().equals(fragmentName);
//    }

    @Override
    public void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
    }


    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class GeneralPreferenceFragment extends PreferenceFragment {

        @Override
        public void onResume() {
            super.onResume();
        }


        @Override
        public void onStart() {
            super.onStart();

        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.pref_general);

            // get version info
            String versionName = BuildConfig.VERSION_NAME;

            // hide some options on watch / phone
            PreferenceScreen preferenceScreen = (PreferenceScreen) findPreference("ais_dom_main_pref_screen");
            PreferenceCategory prefCategoryAbout = (PreferenceCategory) findPreference("pref_category_about");
            PreferenceCategory prefCategorySettings = (PreferenceCategory) findPreference("pref_category_app_settings");
            PreferenceCategory prefCategoryConnHistory = (PreferenceCategory) findPreference("ais_dom_list_history");
            PreferenceCategory prefCategoryConnUrl = (PreferenceCategory) findPreference("ais_dom_con_url");
            //
            Preference preferenceVersion = findPreference("pref_ais_dom_version");
            Preference preferenceRemote = findPreference("setting_app_remotemode");
            Preference preferenceAppDisco = findPreference("setting_app_discovery");
            Preference preferenceAppTtsVoice = findPreference("setting_app_tts_voice");
            Preference preferenceAppZoomLevel = findPreference("setting_test_zoomlevel");
            Preference preferenceGesture = findPreference("pref_ais_dom_list_gesture");


            prefCategorySettings.removePreference(preferenceRemote);
            // set info in version
            preferenceVersion.setSummary(versionName + " (client app)");
            //
            bindPreferenceSummaryToValue(findPreference(getString(R.string.key_setting_app_launchurl)));

            // on watch
            if (AisCoreUtils.onWatch()) {
                // remove select tts voice
                prefCategorySettings.removePreference(preferenceAppTtsVoice);
                // remove discovery option
                prefCategorySettings.removePreference(preferenceAppDisco);
                // remove gesture list
                prefCategorySettings.removePreference(preferenceGesture);
                // remove zoom in app
                prefCategorySettings.removePreference(preferenceAppZoomLevel);

            }


            Preference.OnPreferenceChangeListener preferenceChangeListener = new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    String summary = newValue + " seconds";
                    preference.setSummary(summary);
                    return true;
                }
            };


            // scanner

            PreferenceScreen prefScan = (PreferenceScreen) findPreference("button_scan_gate_id");
            prefScan.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Log.i(TAG, "test");
                    Intent intent = new Intent(Intent.ACTION_MAIN);
                    intent.setComponent(new ComponentName("pl.sviete.dom","pl.sviete.dom.ScannerActivity"));
                    startActivity(intent);
                    return false;
                }
            });

        }

    }


}

