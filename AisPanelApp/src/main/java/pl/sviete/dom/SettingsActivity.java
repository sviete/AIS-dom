package pl.sviete.dom;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceManager;
import androidx.core.app.ActivityCompat;

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

    public static class GeneralPreferenceFragment extends PreferenceFragment {

        @Override
        public void onResume() {
            super.onResume();
        }


        @Override
        public void onStart() {
            super.onStart();

        }


        public void updateServices(Context context, boolean enableAisAudioService, boolean enableAisHotWordService){
            // to update notification
            Intent aisAudioService = new Intent(context, AisPanelService.class);
            Intent aisHotWordService = new Intent(context, PorcupineService.class);
            // 1. stop all
            context.stopService(aisAudioService);
            context.stopService(aisHotWordService);

            // 2.start enabled
            if (enableAisHotWordService){
                context.startService(aisHotWordService);
            }
            if (enableAisAudioService){
                context.startService(aisAudioService);
            }
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

                // pref_ais_dom_list_gesture  abd remove select tts voice
                preferenceMainScreen.removePreference(prefCategorySettings);
                //
                PreferenceCategory prefCategoryExperimental = (PreferenceCategory) findPreference("pref_category_app_experimental");
                Preference preferenceAudioDisco = findPreference("setting_app_discovery");
                prefCategoryExperimental.removePreference(preferenceAudioDisco);

                //
                Preference preferenceReportLocation = findPreference("setting_report_location");
                prefCategoryExperimental.removePreference(preferenceReportLocation);

                //

            } else {
                preferenceVersion.setSummary(versionName + " \n(mob client id: " + AisCoreUtils.AIS_GATE_ID + ")" );
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
                    Config config = new Config(preference.getContext());
                    boolean enableAisAudioService = false;
                    boolean enableAisHotWordService = false;
                    if (config.getHotWordMode()) {
                        enableAisHotWordService = true;
                    }
                    if (config.getAppDiscoveryMode()) {
                        enableAisAudioService = true;
                    }

                    if (prefKey.equals("setting_app_discovery")) {
                        if ((boolean) newValue) {
                            enableAisAudioService = true;
                        } else {
                            enableAisAudioService = false;
                        }
                        updateServices(preference.getContext(),enableAisAudioService, enableAisHotWordService);
                    } else if (prefKey.equals("setting_hot_word_mode")) {
                        if ((boolean) newValue) {
                            // ask for permission
                            int permissionMicrophone = ActivityCompat.checkSelfPermission(preference.getContext(), Manifest.permission.RECORD_AUDIO);
                            if (permissionMicrophone != PackageManager.PERMISSION_GRANTED) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    requestPermissions(
                                            new String[] { Manifest.permission.RECORD_AUDIO },
                                            AisCoreUtils.REQUEST_RECORD_PERMISSION);
                                }
                            }
                            enableAisHotWordService = true;

                        } else {
                            enableAisHotWordService = false;
                        }
                        updateServices(preference.getContext(),enableAisAudioService, enableAisHotWordService);
                    } else if (prefKey.equals("setting_report_location")) {
                        //
                        Intent aisLocationService = new Intent(preference.getContext(), AisFuseLocationService.class);
                        if ((boolean) newValue) {
                            // ask for permission
                            int permissionLocation = ActivityCompat.checkSelfPermission(preference.getContext(), Manifest.permission.ACCESS_FINE_LOCATION);
                            if (permissionLocation != PackageManager.PERMISSION_GRANTED) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    requestPermissions(
                                            new String[] { Manifest.permission.ACCESS_FINE_LOCATION },
                                            AisCoreUtils.REQUEST_LOCATION_PERMISSION);
                                }
                            }
                            preference.getContext().startService(aisLocationService);
                        } else {
                            preference.getContext().stopService(aisLocationService);
                        }
                    } else if (prefKey.equals("setting_app_hot_word_sensitivity") || prefKey.equals("setting_app_hot_word")) {
                        if (config.getHotWordMode()) {
                            // restart hot word service
                            enableAisHotWordService = true;
                            updateServices(preference.getContext(),enableAisAudioService, enableAisHotWordService);
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
                Preference preferenceReportLocationMode = findPreference("setting_report_location");
                preferenceReportLocationMode.setOnPreferenceChangeListener(preferenceChangeListener);

                //
                Preference preferenceMediaPlayer = findPreference("setting_app_discovery");
                preferenceMediaPlayer.setOnPreferenceChangeListener(preferenceChangeListener);


                // pref_ais_dom_list_gesture
                PreferenceScreen preGesture = (PreferenceScreen) findPreference("pref_ais_dom_list_gesture");
                preGesture.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        Intent intent = new Intent(Intent.ACTION_MAIN);
                        intent.setClassName(BuildConfig.APPLICATION_ID, "pl.sviete.dom.gesture.GestureListActivity");
                        startActivity(intent);
                        return false;
                    }
                });
            }
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            //super.onCreate(savedInstanceState);
        }

    }


}

