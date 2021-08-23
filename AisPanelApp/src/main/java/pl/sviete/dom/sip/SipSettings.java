package pl.sviete.dom.sip;

import android.os.Bundle;
import android.preference.PreferenceActivity;

import pl.sviete.dom.R;

/**
 * Handles SIP authentication settings for the AIS.
 */
public class SipSettings extends PreferenceActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Note that none of the preferences are actually defined here.
        // They're all in the XML file res/xml/preferences.xml.
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.pref_sip);
    }
}
