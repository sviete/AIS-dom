package pl.sviete.dom;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;
import android.webkit.WebStorage;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import ai.picovoice.hotword.PorcupineService;
import pl.sviete.dom.connhist.AisConnectionHistJSON;

public class Config {
    public final Context myContext;
    private final SharedPreferences sharedPreferences;
    public static final String TAG = Context.class.getName();

    public Config(Context appContext) {
        myContext = appContext;
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext);
    }

    private String getStringPref(int resId, int defId) {
        final String def = myContext.getString(defId);
        final String pref = sharedPreferences.getString(myContext.getString(resId), "");
        return pref.length() == 0 ? def : pref;
    }

    private boolean getBoolPref(int resId, int defId) {
        return sharedPreferences.getBoolean(
                myContext.getString(resId),
                Boolean.valueOf(myContext.getString(defId))
        );
    }

    // GET the answer from server
    public String getResponseFromServer(String url, int timeout) {
        HttpURLConnection c = null;
        try {
            URL u = new URL(url);
            c = (HttpURLConnection) u.openConnection();
            c.setRequestMethod("GET");
            c.setRequestProperty("Content-length", "0");
            c.setUseCaches(false);
            c.setAllowUserInteraction(false);
            c.setConnectTimeout(timeout);
            c.setReadTimeout(timeout);
            c.connect();
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().permitAll().build());

            int status = c.getResponseCode();

            switch (status) {
                case 200:
                case 201:
                    BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line + "\n");
                    }
                    br.close();
                    return sb.toString();
            }

        } catch (Exception ex) {
            Log.e(TAG, ex.toString());
        } finally {
            if (c != null) {
                try {
                    c.disconnect();
                } catch (Exception ex) {
                    Log.e(TAG, ex.toString());
                }
            }
        }
        return "";
    }



    public boolean canUseLocalConnection(String localIP, String gateId) {
        // check local IP
        String url = "http://" + localIP + ":8122";
        String severAnswer = getResponseFromServer(url, 3000);
        if (!severAnswer.equals("")) {
            try {
                JSONObject jsonAnswer = new JSONObject(severAnswer);
                String localGateID = jsonAnswer.getString("gate_id");
                if (gateId.equals(localGateID)) {
                    return true;
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        return false;
    }

    public String getLocalIpFromCloud(String gateId) {
        // ask cloud for local IP
        // https://powiedz.co/ords/dom/dom/gate_ip_full_info?id=dom-aba
        AisCoreUtils.AIS_GATE_USER = "";
        AisCoreUtils.AIS_GATE_DESC = "";
        String url = AisCoreUtils.getAisDomCloudWsUrl(true) + "gate_ip_full_info?id=" + gateId;
        String severAnswer = getResponseFromServer(url, 10000);
        if (!severAnswer.equals("")) {
            try {
                JSONObject jsonAnswer = new JSONObject(severAnswer);
                String localGateIP = jsonAnswer.getString("ip");
                AisCoreUtils.AIS_GATE_USER = jsonAnswer.getString("user");
                AisCoreUtils.AIS_GATE_DESC = jsonAnswer.getString("desc");
                if (!gateId.equals("ais-dom")) {
                    return localGateIP;
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        return "";
    }



    private class checkConnectionUrlJob extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String[] params) {
            String gateID = params[0];
            String localIpHist = params[1];
            String userHist = params[2];
            String descHist = params[3];
            String goToHaView = params[4];
            String urlToGo = "";

            // Check if the local IP from history is still OK

            if (!localIpHist.equals("") && canUseLocalConnection(localIpHist, gateID)){
                    urlToGo = "http://" + localIpHist + ":8180";
                    saveConnToHistory(localIpHist, urlToGo, gateID, userHist, descHist);
                    return urlToGo  + goToHaView;
            } else {
                    // Get the new local IP from the Cloud
                    String localIpFromCloud = getLocalIpFromCloud(gateID);
                    if (!localIpFromCloud.equals("")) {
                        // check if new local IP from cloud is now OK
                        if (canUseLocalConnection(localIpFromCloud, gateID)){
                            urlToGo = "http://" + localIpFromCloud + ":8180";
                            saveConnToHistory(localIpFromCloud, urlToGo, gateID, AisCoreUtils.AIS_GATE_USER, AisCoreUtils.AIS_GATE_DESC);
                            return urlToGo + goToHaView;
                        } else {
                            // try the tunnel connection
                            urlToGo = "https://" + gateID + ".paczka.pro";
                            saveConnToHistory(localIpFromCloud, urlToGo, gateID, AisCoreUtils.AIS_GATE_USER, AisCoreUtils.AIS_GATE_DESC);
                            return urlToGo + goToHaView;
                        }
                    } else {
                        // try tunnel
                        urlToGo = "https://" + gateID + ".paczka.pro";
                        saveConnToHistory(localIpHist, urlToGo, gateID, AisCoreUtils.AIS_GATE_USER, AisCoreUtils.AIS_GATE_DESC);
                        return urlToGo + goToHaView;
                    }
            }
        }

        @Override
        protected void onPostExecute(String message) {
            //process message with url to go
            if (!message.equals("")){
                // call the browser url change
                Log.w(TAG, "message " + message);
                Intent intent = new Intent(BrowserActivity.BROADCAST_ACTION_LOAD_URL);
                intent.putExtra(BrowserActivity.BROADCAST_ACTION_LOAD_URL, message);
                LocalBroadcastManager bm = LocalBroadcastManager.getInstance(myContext);
                bm.sendBroadcast(intent);
                AisCoreUtils.setAisDomUrl(message);
            }
        }
    }


    public void saveConnToHistory(String localIP, String url, String gate, String user, String desc) {
        try {
            JSONObject mNewConn = new JSONObject();
            mNewConn.put("gate", gate);
            mNewConn.put("url", url);
            mNewConn.put("ip", localIP);
            mNewConn.put("user", user);
            mNewConn.put("desc", desc);
            AisConnectionHistJSON.addConnection(myContext, mNewConn.toString());
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    public String getAppLaunchUrl(boolean disco, String goToHaView) {
        String url;

        if (AisCoreUtils.onBox()){
            return AisCoreUtils.getAisDomUrl();
        }

        url = getStringPref(R.string.key_setting_app_launchurl, R.string.default_setting_app_launchurl);

        if (url.startsWith("dom-") && disco) {
            String gateID = url;
            String localIpHist = AisConnectionHistJSON.getLocalIpForGate(myContext, gateID);
            String userHist = AisConnectionHistJSON.getUserForGate(myContext, gateID);
            String descHist = AisConnectionHistJSON.getDescForGate(myContext, gateID);
            checkConnectionUrlJob checkConnectionUrlJob = new checkConnectionUrlJob();
            checkConnectionUrlJob.execute(gateID, localIpHist, userHist, descHist, goToHaView);
        } else {
            // save it for interface communication with gate
            if (url.startsWith("dom-")) {
                AisCoreUtils.setAisDomUrl("https://" + url + ".paczka.pro" + goToHaView);
            } else {
                // the url is set by hand,
                AisCoreUtils.setAisDomUrl(url);
            }
        }
        return url;
    }

    public String getSelectedHotWord() {
        return getStringPref(R.string.key_setting_app_hot_word,
                R.string.default_setting_app_hot_word);
    }

    public int getSelectedHotWordSensitivity(){
        int howWordSensitivity = sharedPreferences.getInt("setting_app_hot_word_sensitivity", 50);
        return howWordSensitivity;
    }

    public Boolean getAppDiscoveryMode() {
        return getBoolPref(R.string.key_setting_app_discovery, R.string.default_setting_app_discovery);
    }

    //
    public void setAppDiscoveryMode(Boolean mode) {
        SharedPreferences.Editor ed = sharedPreferences.edit();
        ed.putBoolean(myContext.getString(R.string.key_setting_app_discovery), mode);
        ed.apply();
    }


    public Boolean getHotWordMode() {
        return getBoolPref(R.string.key_setting_hot_word_mode, R.string.default_setting_hot_word_mode);
    }

    //
    public void setHotWordMode(Boolean mode) {
        SharedPreferences.Editor ed = sharedPreferences.edit();
        ed.putBoolean(myContext.getString(R.string.key_setting_hot_word_mode), mode);
        ed.apply();

        Intent serviceHotWordIntent = new Intent(myContext, PorcupineService.class);
        if (mode) {
            // start service
            myContext.startService(serviceHotWordIntent);

        } else {
            // stop service
            myContext.stopService(serviceHotWordIntent);
        }
    }

    public Boolean getReportLocationMode() {
        return getBoolPref(R.string.key_setting_report_location, R.string.default_setting_report_location);
    }

    //
    public void setReportLocationMode(Boolean mode) {
        SharedPreferences.Editor ed = sharedPreferences.edit();
        ed.putBoolean(myContext.getString(R.string.key_setting_report_location), mode);
        ed.apply();

        Intent serviceReportLocationIntent = new Intent(myContext, AisFuseLocationService.class);
        if (mode) {
            // start service
            myContext.startService(serviceReportLocationIntent);

        } else {
            // stop service
            myContext.stopService(serviceReportLocationIntent);
        }
    }


    public Boolean getAppWizardDone() {
        return getBoolPref(R.string.default_setting_app_wizard_done, R.string.default_setting_app_wizard_done);
    }

    public void setAppWizardDone(Boolean done) {
        SharedPreferences.Editor ed = sharedPreferences.edit();
        ed.putBoolean(myContext.getString(R.string.default_setting_app_wizard_done), done);
        ed.apply();
    }

    public void setAisHaAccessToken(String token){
        AisCoreUtils.AIS_HA_ACCESS_TOKEN = token;
        SharedPreferences.Editor ed = sharedPreferences.edit();
        ed.putString("ais_ha_access_token", token);
        ed.apply();
    }

    public String getHaAccessToken(){
        return getStringPref(R.string.key_setting_ais_ha_access_token, R.string.default_setting_ais_ha_access_token);
    }

    public void setAisHaWebhookId(String webhookId){
        AisCoreUtils.AIS_HA_WEBHOOK_ID = webhookId;
        SharedPreferences.Editor ed = sharedPreferences.edit();
        ed.putString("ais_ha_webhook_id", webhookId);
        ed.apply();
    }

    public String getAisHaWebhookId(){
        return getStringPref(R.string.key_setting_ais_ha_webhook_id, R.string.default_setting_ais_ha_webhook_id);
    }

    public void setAppLaunchUrl(String gate) {
        // this is executed from QR code scan or Gate history only
        SharedPreferences.Editor ed = sharedPreferences.edit();
        ed.putString(myContext.getString(R.string.key_setting_app_launchurl), gate);
        ed.apply();

        // deleteAllData to logout from browser on change
        WebStorage.getInstance().deleteAllData();
    }


    public float getTestZoomLevel() {
        return Float.valueOf(getStringPref(R.string.key_setting_test_zoomlevel, R.string.default_setting_test_zoomlevel));
    }


    public String getAppTtsVoice() {
        return getStringPref(R.string.key_setting_app_tts_voice,
                R.string.default_setting_app_tts_voice);
    }

    public void setAppTtsVoice(String voice) {
        SharedPreferences.Editor ed = sharedPreferences.edit();
        ed.putString(myContext.getString(R.string.key_setting_app_tts_voice), voice);
        ed.apply();
    }

}
