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

        //
        Config config = new Config(getApplicationContext());

        ListPreference audioLp = (ListPreference) findPreference("setting_local_sip_speaker_device");
        ListPreference micLp = (ListPreference) findPreference("setting_local_sip_mic_device");

        List<String> audioDevicesList = new ArrayList<String>();
        List<String> micDevicesList = new ArrayList<String>();

        for (AudioDevice audioDevice : EasyLinphone.getLC().getExtendedAudioDevices()) {
            Log.i(TAG, "defaultOutputAudioDevice audioDevice " + audioDevice.getId());
            if (audioDevice.getType() != Microphone) {
                audioDevicesList.add(audioDevice.getId());
            } else {
                micDevicesList.add(audioDevice.getId());
            }
        }

        String[] audioDevices = new String[ audioDevicesList.size() ];
        String[] micDevices = new String[ micDevicesList.size() ];
        audioDevicesList.toArray( audioDevices );
        micDevicesList.toArray( micDevices );

        AudioDevice defaultOutputAudioDevice = EasyLinphone.getLC().getDefaultOutputAudioDevice();
        AudioDevice defaultInputAudioDevice = EasyLinphone.getLC().getDefaultInputAudioDevice();

        audioLp.setEntries(audioDevices);
        micLp.setEntries(micDevices);

        //
        if (audioDevicesList.contains(config.getSipAudioDeviceId())) {
            audioLp.setValue(config.getSipAudioDeviceId());
        } else {
            audioLp.setValue(defaultOutputAudioDevice.getId());
        }

        if (micDevicesList.contains(config.getSipMicDeviceId())) {
            micLp.setValue(config.getSipMicDeviceId());
        } else {
            micLp.setValue(defaultInputAudioDevice.getId());
        }

        audioLp.setEntryValues(audioDevices);
        micLp.setEntryValues(micDevices);

    }
}
