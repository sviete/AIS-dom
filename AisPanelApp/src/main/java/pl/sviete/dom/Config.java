package pl.sviete.dom;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.util.Log;
import android.webkit.WebStorage;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import ai.picovoice.hotword.PorcupineService;

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
        //Log.d(TAG, "PREF: " + myContext.getString(resId) + " default " + myContext.getString(defId));
        //Log.d(TAG, "PREF: " + myContext.getString(resId) + " -> " + sharedPreferences.getBoolean(myContext.getString(resId), Boolean.valueOf(myContext.getString(defId))));
        return sharedPreferences.getBoolean(
                myContext.getString(resId),
                Boolean.valueOf(myContext.getString(defId))
        );
    }

    // GET the answer from server
    // TODO - switch to valley
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

    private void redirectToNewGateUrl(String localUrlToGo, Boolean force){
        AisCoreUtils.setAisDomUrl(localUrlToGo);
        // fix java.lang.NullPointerException
        if (!force && AisCoreUtils.mWebView != null){
            // check if the url was changed
            if (AisCoreUtils.mWebView.getUrl().startsWith(localUrlToGo)){
                return;
            }
        }
        Intent intent = new Intent(BrowserActivity.BROADCAST_ACTION_LOAD_URL);
        intent.putExtra(BrowserActivity.BROADCAST_ACTION_LOAD_URL, localUrlToGo);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(myContext);
        bm.sendBroadcast(intent);
    }

    public void getTheLocalIpFromCloud(String gateID, String goToHaView, Boolean forceRefreshWeb) {
        // 1. Get the new local IP from the Cloud
        // https://powiedz.co/ords/dom/dom/gate_ip_full_info
        String url = AisCoreUtils.getAisDomCloudWsUrl(true) + "gate_ip_full_info";
        final String[] localGateIpFromCloud = {"ais-dom"};
        JSONObject deviceJsonInfo = new JSONObject();
        try{
            deviceJsonInfo.put("gate_id", gateID);
            deviceJsonInfo.put("client_id", AisCoreUtils.AIS_GATE_ID);
            deviceJsonInfo.put("app_version", BuildConfig.VERSION_NAME);
            deviceJsonInfo.put("client_ip", AisNetUtils.getIPAddress(true));
            deviceJsonInfo.put("os_version", AisNetUtils.getApiLevel() + " " + AisNetUtils.getOsVersion());
            deviceJsonInfo.put("device_type", AisCoreUtils.AIS_DEVICE_TYPE);
        } catch (Exception e) {
            Log.e("Exception", e.toString());
        }
        // call
        Map<String, String> heders = new HashMap<String, String>();
        heders.put("Content-Type", "application/json; charset=UTF-8");
        heders.put("Authorization", "Bearer ais");

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest
                (Request.Method.POST, url, deviceJsonInfo, new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            localGateIpFromCloud[0] = response.getString("gate_local_ip");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        // save sip settings
//                        try {
//                            setSipLocalClientName(response.getString("sip_local_client_name"));
//                            setSipLocalClientPassword(response.getString("sip_local_client_password"));
//                            setSipLocalCamUrl(response.getString("sip_local_cam_url"));
//
//                        } catch (Exception e) {
//                            e.printStackTrace();
//                        }

                        //
                        if (!localGateIpFromCloud[0].equals("ais-dom")) {
                            // 2. check if new local IP from cloud is now OK
                            String localUrl = "http://" + localGateIpFromCloud[0]+ ":8122";
                            JsonObjectRequest jsonObjectRequest = new JsonObjectRequest
                                    (Request.Method.GET, localUrl, null, new Response.Listener<JSONObject>() {
                                        @Override
                                        public void onResponse(JSONObject response) {
                                            String gateIdInNetwork = "";
                                            try {
                                                gateIdInNetwork = response.getString("gate_id");
                                                // TODO we can be more restrictive in the future
                                                //  and check if (gateID.equals(gateIdInNetwork)) {
                                                if (gateID.startsWith("dom-")) {
                                                    // The new local gate is OK to connect - do this connection locally
                                                    String localUrlToGo = "http://" + localGateIpFromCloud[0] + goToHaView;
                                                    // SUCCESS
                                                    setAppLocalGateIp(localGateIpFromCloud[0]);
                                                    redirectToNewGateUrl(localUrlToGo, forceRefreshWeb);
                                                    return;
                                                }
                                            } catch (JSONException e) {
                                                e.printStackTrace();
                                            }
                                            // this is not our gate - we should try the tunnel
                                            // PLAN D
                                            String urlToGo = "https://" + gateID + ".paczka.pro";
                                            redirectToNewGateUrl(urlToGo, forceRefreshWeb);
                                        }
                                    }, new Response.ErrorListener() {
                                        @Override
                                        public void onErrorResponse(VolleyError error) {
                                            Log.e(TAG, error.toString());
                                            // no connection with gate -  try tunnel
                                            // PLAN C
                                            String urlToGo = "https://" + gateID + ".paczka.pro";
                                            redirectToNewGateUrl(urlToGo, forceRefreshWeb);
                                        }
                                    });

                            RequestQueue requestQueue = Volley.newRequestQueue(myContext);
                            requestQueue.add(jsonObjectRequest);

                        } else {
                            // try tunnel
                            // PLAN C
                            String urlToGo = "https://" + gateID + ".paczka.pro";
                            redirectToNewGateUrl(urlToGo, forceRefreshWeb);
                        }
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e(TAG, error.toString());
                        // try tunnel
                        // PLAN C
                        String urlToGo = "https://" + gateID + ".paczka.pro";
                        redirectToNewGateUrl(urlToGo, forceRefreshWeb);
                    }
                });

        RequestQueue requestQueue = Volley.newRequestQueue(myContext);
        requestQueue.add(jsonObjectRequest);
    }

    private void checkTheConnectionWithGate(String gateID, String localIpHist, String goToHaView, Boolean forceRefreshWeb) {
        // Check if the local IP from history is still OK
        String localUrl = "http://" + localIpHist + ":8122";
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest
                (Request.Method.GET, localUrl, null, new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        String gateIdInNetwork = "";
                        try {
                            gateIdInNetwork = response.getString("gate_id");
                            if (gateID.equals(gateIdInNetwork)) {
                                // The local gate is OK to connect - do this connection locally
                                String localUrlToGo = "http://" + localIpHist + goToHaView;
                                // SUCCESS
                                setAppLocalGateIp(localIpHist);
                                redirectToNewGateUrl(localUrlToGo, forceRefreshWeb);
                                return;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        // this is not our gate - we should try to ask cloud about new local ip
                        // PLAN B
                        getTheLocalIpFromCloud(gateID, goToHaView, forceRefreshWeb);
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e(TAG, error.toString());
                        // no connection with gate - we should try to ask cloud about new local ip
                        // PLAN B
                        getTheLocalIpFromCloud(gateID, goToHaView, forceRefreshWeb);
                    }
                });

        RequestQueue requestQueue = Volley.newRequestQueue(myContext);
        requestQueue.add(jsonObjectRequest);
    }


    public String getAppLaunchUrl(boolean disco, boolean force, String goToHaView) {
        String url;

        if (AisCoreUtils.onBox()){
            return AisCoreUtils.getAisDomUrl();
        }

        url = getStringPref(R.string.key_setting_app_launchurl, R.string.default_setting_app_launchurl);

        if (url.startsWith("dom-") && disco) {
            String gateID = url;
            String lastLocalGateIp = getAppLocalGateIp();
            checkTheConnectionWithGate(gateID, lastLocalGateIp, goToHaView, force);
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

    public String getSelectedHotWordName() {
        String[] splited =  getStringPref(R.string.key_setting_app_hot_word, R.string.default_setting_app_hot_word).split("_");
        String returnString = "";
        for (String s: splited) {
            returnString = returnString + s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase() + " ";
        }
        return  returnString.trim();
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

    //
    public void setDoorbellMode(Boolean mode) {
        SharedPreferences.Editor ed = sharedPreferences.edit();
        ed.putBoolean(myContext.getString(R.string.key_pref_ais_dom_doorbell), mode);
        ed.apply();
    }

    public Boolean getDoorbellMode() {
        return getBoolPref(R.string.key_pref_ais_dom_doorbell, R.string.default_setting_doorbell);
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

    public String getAppLaunchUrl() {
        return getStringPref(R.string.key_setting_app_launchurl, R.string.default_setting_app_launchurl);
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

    // local gate ip
    public String getAppLocalGateIp() {
        return getStringPref(R.string.key_setting_local_gate_ip,
                R.string.default_setting_local_gate_ip);
    }

    public void setAppLocalGateIp(String gateIp) {
        SharedPreferences.Editor ed = sharedPreferences.edit();
        ed.putString(myContext.getString(R.string.key_setting_local_gate_ip), gateIp);
        ed.apply();
    }

    //
    // -- SIP --
    public void setSipLocalClientName(String sipLocalClientName) {
        SharedPreferences.Editor ed = sharedPreferences.edit();
        ed.putString(myContext.getString(R.string.key_setting_local_sip_client_name), sipLocalClientName);
        ed.apply();
    }

    public String getSipLocalClientName() {
        return getStringPref(R.string.key_setting_local_sip_client_name,
                R.string.default_setting_local_sip_client_name);
    }

    public void setSipLocalClientPassword(String sipLocalClientPass) {
        SharedPreferences.Editor ed = sharedPreferences.edit();
        ed.putString(myContext.getString(R.string.key_setting_local_sip_client_password), sipLocalClientPass);
        ed.apply();
    }

    public String getSipLocalClientPassword() {
        return getStringPref(R.string.key_setting_local_sip_client_password,
                R.string.default_setting_local_sip_client_password);
    }

    public void setSipLocalCamUrl(String sipLocalCamUr) {
        SharedPreferences.Editor ed = sharedPreferences.edit();
        ed.putString(myContext.getString(R.string.key_setting_local_sip_cam_url), sipLocalCamUr);
        ed.apply();
    }

    public String getSipLocalCamUrl() {
        return getStringPref(R.string.key_setting_local_sip_cam_url, R.string.default_setting_local_sip_cam_url);
    }

    public String getSipLocalGate1OpenUrl() {
        return getStringPref(R.string.key_setting_local_sip_gate_1_open_url, R.string.default_setting_local_sip_gate_1_open_url);
    }

    public String getSipLocalGate2OpenUrl() {
        return getStringPref(R.string.key_setting_local_sip_gate_2_open_url, R.string.default_setting_local_sip_gate_2_open_url);
    }

    public int getSipTimeout() {
        String timeOut = getStringPref(R.string.key_setting_local_sip_pick_up_time, R.string.default_setting_local_sip_pick_up_tim);
        return Integer.parseInt(timeOut);
    }

    public String getSipAspectRatio() {
        String aspectRatio = getStringPref(R.string.key_setting_local_sip_cam_aspect_ratio, R.string.default_setting_local_sip_cam_aspect_ratio);
        return aspectRatio;
    }

    public String getSipAudioDeviceId() {
        String audioDeviceId = getStringPref(R.string.key_setting_local_sip_speaker_device, R.string.default_setting_local_sip_speaker_device);
        return audioDeviceId;
    }

    public String getSipMicDeviceId() {
        String micDeviceId = getStringPref(R.string.key_setting_local_sip_mic_device, R.string.default_setting_local_sip_mic_device);
        return micDeviceId;
    }

    public String getSipMicGain() {
        String micGain = getStringPref(R.string.key_setting_local_sip_mic_gain, R.string.default_setting_local_sip_mic_gain);
        return micGain;
    }

    public String getSipSpeakerGain() {
        String speakerGain = getStringPref(R.string.key_setting_local_sip_speaker_gain, R.string.default_setting_local_sip_speaker_gain);
        return speakerGain;
    }

}
