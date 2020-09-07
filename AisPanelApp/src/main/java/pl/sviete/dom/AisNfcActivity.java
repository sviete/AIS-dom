package pl.sviete.dom;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.Animatable;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.NfcA;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;

public class AisNfcActivity extends AppCompatActivity {
    private static final String TAG = "AisNfcActivity";

    @Override
    protected void onStart() {
        super.onStart();
    }
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ais_nfc);

        // animation
        ImageView aisAnimationNfcLogo = findViewById(R.id.ais_logo_nfc_animation);
        AnimatedVectorDrawableCompat animatedVectorDrawableCompat = AnimatedVectorDrawableCompat.create(this, R.drawable.nfc_anim);
        aisAnimationNfcLogo.setImageDrawable(animatedVectorDrawableCompat);
        Animatable animatable = (Animatable) ((ImageView) aisAnimationNfcLogo).getDrawable();
        animatable.start();

        //
        TextView nfcText = findViewById(R.id.nfcText);

        // NFC
        Intent intent = getIntent();
        String action = intent.getAction();
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);

            String[][] mTechLists = new String[][] {
                    new String[] {
                            NfcA.class.getName() ,
                            MifareUltralight.class.getName(),
                            MifareClassic.class.getName()
                    }
            };

            NdefMessage[] msgs = null;
            if (rawMsgs != null) {
                msgs = new NdefMessage[rawMsgs.length];
                for (int i = 0; i < rawMsgs.length; i++) {
                    msgs[i] = (NdefMessage) rawMsgs[i];
                }
            }
            if (msgs == null || msgs.length == 0) {
                //  no message - try to get tag id
                Bundle bundle = intent.getExtras();
                if (bundle != null) {
                    for (String key : bundle.keySet()) {
                        Log.e("TEST", key + " : " + (bundle.get(key) != null ? bundle.get(key) : "NULL"));
                    }
                }
                byte[] extraID = intent.getByteArrayExtra(NfcAdapter.EXTRA_ID);
                StringBuilder sb = new StringBuilder();
                for (byte b : extraID) {
                    sb.append(String.format("%02X", b));
                }
                try {
                    // Get the nfc id
                    String nfcTagSerialNum = sb.toString();
                    nfcText.setText(nfcTagSerialNum);
                    JSONObject jMessage = new JSONObject();
                    try {
                        jMessage.put("event_type", "tag_scanned");
                        JSONObject jData = new JSONObject();
                        jData.put("tag_id", nfcTagSerialNum);
                        jMessage.put("event_data", jData);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    DomWebInterface.publishJson(jMessage, "event", getApplicationContext());

                    final Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            finish();
                        }
                    }, 3000);

                } catch (Exception e) {
                    Log.e("Exception", e.toString());
                }

            }
            else {
                // try to get message
                String text = "";
                byte[] payload = msgs[0].getRecords()[0].getPayload();
                String textEncoding = ((payload[0] & 128) == 0) ? "UTF-8" : "UTF-16"; // Get the Text Encoding
                int languageCodeLength = payload[0] & 0063; // Get the Language Code, e.g. "en"

                try {
                    // Get the Text
                    text = new String(payload, languageCodeLength + 1, payload.length - languageCodeLength - 1, textEncoding);
                    nfcText.setText(text);
                    DomWebInterface.publishMessage(text, "speech_command", getApplicationContext());

                    final Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            finish();
                        }
                    }, 3000);

                } catch (Exception e) {
                    Log.e("Exception", e.toString());
                }
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        // do not resend command to gate
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onStop() {
        super.onStop();
    }
    @Override
    protected void onResume() {
        super.onResume();
    }
    @Override
    protected void onPause() {
        super.onPause();
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    public boolean onOptionsItemSelected(MenuItem item){
        Intent myIntent = new Intent(getApplicationContext(), AisNfcActivity.class);
        startActivityForResult(myIntent, 0);
        return true;
    }
}

