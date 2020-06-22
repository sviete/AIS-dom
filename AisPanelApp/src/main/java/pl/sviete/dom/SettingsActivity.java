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

import androidx.core.content.ContextCompat;

import ai.picovoice.hotword.PorcupineService;

public class SettingsActivity extends AppCompatPreferenceActivity {

    static final String TAG = SettingsActivity.class.getName();
    static private int mClickNo = 0;

    private static final Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = (preference, value) -> {
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

            // get version info - set info about version
            String versionName = BuildConfig.VERSION_NAME;
            PreferenceCategory prefCategorySettings = (PreferenceCategory) findPreference("pref_category_app_settings");
            Preference preferenceVersion = findPreference("pref_ais_dom_version");
            if(AisCoreUtils.onBox()) {
                preferenceVersion.setSummary(versionName + " (client app on iot gate)");
                // hide some options on gate / phone
                // remove connection url
                PreferenceScreen preferenceMainScreen = (PreferenceScreen) findPreference("ais_dom_main_pref_screen");
                PreferenceCategory prefCategoryConnUrl = (PreferenceCategory) findPreference("ais_dom_con_url");
                preferenceMainScreen.removePreference(prefCategoryConnUrl);
                // remove wizard
                PreferenceCategory prefCategoryWizard = (PreferenceCategory) findPreference("ais_dom_wizard");
                preferenceMainScreen.removePreference(prefCategoryWizard);
                // pref_ais_dom_list_gesture  abd remove select tts voice
                preferenceMainScreen.removePreference(prefCategorySettings);
                //
                PreferenceCategory prefCategoryExperimental = (PreferenceCategory) findPreference("pref_category_app_experimental");
                Preference preferenceAudioDisco = findPreference("setting_app_discovery");
                prefCategoryExperimental.removePreference(preferenceAudioDisco);

                //

            } else {
                preferenceVersion.setSummary(versionName + " (client app)");
            }

            // set on exit
            Preference preferenceExitApp = findPreference("pref_ais_dom_exit");
            preferenceExitApp.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent startMain = new Intent(Intent.ACTION_MAIN);
                    startMain.addCategory(Intent.CATEGORY_HOME);
                    startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(startMain);
                    return true;
                }
            });

            // set info about HotWordSensitivity
            Preference preferenceHotWordSensitivity = findPreference("setting_app_hot_word_sensitivity");
            Config config = new Config(preferenceHotWordSensitivity.getContext());
            int sensitivity = config.getSelectedHotWordSensitivity();
            preferenceHotWordSensitivity.setTitle(getString(R.string.title_setting_app_hot_word_sensitivity) + String.format(" : %d", sensitivity));

            // set hot word info
            Preference preferenceHotWord = findPreference("setting_app_hot_word");
            final String hotWord = config.getSelectedHotWord();
            preferenceHotWord.setTitle(getString(R.string.title_setting_app_hot_word) + ": " + hotWord);

            //
            if (!AisCoreUtils.onBox()) {
                bindPreferenceSummaryToValue(findPreference(getString(R.string.key_setting_app_launchurl)));
            }



            Preference.OnPreferenceChangeListener preferenceChangeListener = new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    String prefKey = preference.getKey();
                    if (prefKey.equals("setting_app_discovery")) {
                        Intent serviceIntent = new Intent(preference.getContext(), AisPanelService.class);
                        if ((boolean) newValue) {
                            // start service
                            preference.getContext().startService(serviceIntent);

                        } else {
                            // stop service
                            preference.getContext().stopService(serviceIntent);
                        }
                    }

                    if (prefKey.equals("setting_hot_word_mode")) {
                        Intent serviceHotWordIntent = new Intent(preference.getContext(), PorcupineService.class);

                        if ((boolean) newValue) {
                            // start service
                            preference.getContext().startService(serviceHotWordIntent);

                        } else {
                            // stop service
                            preference.getContext().stopService(serviceHotWordIntent);
                        }
                    }

                    if (prefKey.equals("setting_app_hot_word_sensitivity") || prefKey.equals("setting_app_hot_word")) {
                        // restart hot word service
                        Config config = new Config(preference.getContext());
                        if (config.getHotWordMode()) {
                            Intent serviceHotWordIntent = new Intent(preference.getContext(), PorcupineService.class);
                            preference.getContext().stopService(serviceHotWordIntent);
                            preference.getContext().startService(serviceHotWordIntent);
                        }

                        if (prefKey.equals("setting_app_hot_word_sensitivity")) {
                            final int progress = Integer.valueOf(String.valueOf(newValue));
                            preference.setTitle(getString(R.string.title_setting_app_hot_word_sensitivity) + String.format(" : %d", progress));
                        }

                        if (prefKey.equals("setting_app_hot_word")) {
                            final String hotWord = String.valueOf(newValue);
                            preference.setTitle(getString(R.string.title_setting_app_hot_word) + ": " + hotWord);
                        }
                    }


                    return true;
                }
            };


            Preference preferenceHotWordMode = findPreference("setting_hot_word_mode");
            preferenceHotWordMode.setOnPreferenceChangeListener(preferenceChangeListener);

            preferenceHotWord.setOnPreferenceChangeListener(preferenceChangeListener);
            preferenceHotWordSensitivity.setOnPreferenceChangeListener(preferenceChangeListener);

            //
            if (!AisCoreUtils.onBox()) {
                //
                Preference preferenceMediaPlayer = findPreference("setting_app_discovery");
                preferenceMediaPlayer.setOnPreferenceChangeListener(preferenceChangeListener);

                // scanner
                PreferenceScreen prefScan = (PreferenceScreen) findPreference("button_scan_gate_id");
                prefScan.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        Intent intent = new Intent(Intent.ACTION_MAIN);
                        intent.setComponent(new ComponentName("pl.sviete.dom", "pl.sviete.dom.ScannerActivity"));
                        startActivity(intent);
                        return false;
                    }
                });


                PreferenceScreen prefWizard = (PreferenceScreen) findPreference("button_run_ais_dom_wizard");
                prefWizard.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        Intent intent = new Intent(Intent.ACTION_MAIN);
                        intent.setComponent(new ComponentName("pl.sviete.dom", "com.redbooth.wizard.MainWizardActivity"));
                        startActivity(intent);
                        return false;
                    }
                });

            }
        }

    }


}

