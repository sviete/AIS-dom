package pl.sviete.dom.sip;

import static org.linphone.core.AudioDevice.Type.Microphone;

import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.util.Log;

import com.xuchongyang.easyphone.EasyLinphone;

import org.linphone.core.AudioDevice;

import java.util.ArrayList;
import java.util.List;

import pl.sviete.dom.Config;
import pl.sviete.dom.R;

/**
 * Handles SIP authentication settings for the AIS.
 */
public class SipSettings extends PreferenceActivity {

    private static final String TAG = "AIS SIP PREF";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Note that none of the preferences are actually defined here.
        // They're all in the XML file res/xml/preferences.xml.
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.pref_sip);

        // get speaker list
        for (AudioDevice audioDevice : EasyLinphone.getLC().getExtendedAudioDevices()) {
            Log.i(TAG, "defaultOutputAudioDevice audioDevice " + audioDevice.getId());

        }
        ListPreference audioLp = (ListPreference) findPreference("setting_local_sip_speaker_device");

        List<String> audioDevicesList = new ArrayList<String>();

        for (AudioDevice audioDevice : EasyLinphone.getLC().getExtendedAudioDevices()) {
            Log.i(TAG, "defaultOutputAudioDevice audioDevice " + audioDevice.getId());
            if (audioDevice.getType() != Microphone) {
                audioDevicesList.add(audioDevice.getId());
            }
        }

        String[] audioDevices = new String[ audioDevicesList.size() ];
        audioDevicesList.toArray( audioDevices );

        AudioDevice defaultOutputAudioDevice = EasyLinphone.getLC().getDefaultOutputAudioDevice();

        audioLp.setEntries(audioDevices);
        Config config = new Config(getApplicationContext());


        audioLp.setDefaultValue(defaultOutputAudioDevice.getId());
        if (audioDevicesList.contains(config.getSipAudioDeviceId())) {
            audioLp.setValue(config.getSipAudioDeviceId());
        } else {
            audioLp.setValue(defaultOutputAudioDevice.getId());
        }
        audioLp.setEntryValues(audioDevices);

    }
}
