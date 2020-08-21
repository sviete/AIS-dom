package pl.sviete.dom;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpPost;
import com.koushikdutta.async.http.AsyncHttpResponse;
import com.koushikdutta.async.http.body.JSONObjectBody;

import java.util.HashMap;
import java.util.Map;

import ai.picovoice.hotword.PorcupineService;
import pl.sviete.dom.data.DomCustomRequest;


import static pl.sviete.dom.AisCoreUtils.BROADCAST_ACTIVITY_SAY_IT;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_SERVICE_SAY_IT;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_SAY_IT_TEXT;
import static pl.sviete.dom.AisCoreUtils.getBatteryPercentage;
import static pl.sviete.dom.AisCoreUtils.isServiceRunning;
import static pl.sviete.dom.DomWebInterface.getDomWsUrl;

public class DomWebInterface {
    final static String TAG = DomWebInterface.class.getName();

    public static String getDomWsUrl(Context context){
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        final String url = sharedPreferences.getString(context.getString(R.string.key_setting_app_launchurl), "");
        if (url.startsWith("dom-")){
            return "http://" + url + ".paczka.pro";
        }
        return pl.sviete.dom.AisCoreUtils.getAisDomUrl().replaceAll("/$", "");
    }

    private static void doPost(JSONObject message, Context context) {
        // do the simple HTTP post
        String url = getDomWsUrl(context) + "/api/webhook/aisdomprocesscommandfromframe";
        AsyncHttpPost post = new AsyncHttpPost(url);
        JSONObjectBody body = new JSONObjectBody(message);
        try {
            message.put("ais_ha_webhook_id", AisCoreUtils.AIS_HA_WEBHOOK_ID);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        post.addHeader("Content-Type", "application/json");
        post.setBody(body);
        AsyncHttpClient.getDefaultInstance().executeJSONObject(post, new AsyncHttpClient.JSONObjectCallback() {

            void say(String text) {
                Intent intent = null;
                if (isServiceRunning(context, AisPanelService.class) || isServiceRunning(context, PorcupineService.class)) {
                    // service is runing
                    intent = new Intent(BROADCAST_SERVICE_SAY_IT);
                } else {
                    //  pl.sviete.dom.BrowserActivityNative
                    intent = new Intent(BROADCAST_ACTIVITY_SAY_IT);
                }
                intent.putExtra(BROADCAST_SAY_IT_TEXT, text);
                LocalBroadcastManager bm = LocalBroadcastManager.getInstance(context);
                bm.sendBroadcast(intent);
            }

            // Callback is invoked with any exceptions/errors, and the result, if available.
            @Override
            public void onCompleted(Exception e, AsyncHttpResponse response, JSONObject result) {
                if (e != null) {
                    // try to discover gate or inform about connection problem
                    Config config = new Config(context.getApplicationContext());
                    String appLaunchUrl = config.getAppLaunchUrl(false, "");
                    if (appLaunchUrl.startsWith("dom-")) {
                        // sprawdzam połączenie
                        // say("Sprawdzam połączenie.");
                        appLaunchUrl = config.getAppLaunchUrl(true, "");
                    } else {
                        // say("Sprawdz połączenie z bramką.");
                    }
                    return;
                }
                // say the response
                if (result.has("say_it")) {
                    try {
                        String text = result.getString("say_it").trim();
                        say(text);
                    } catch (JSONException ex) {
                        ex.printStackTrace();
                    }
                }
                //
                if (result.has("player_status")) {
                    try {
                        JSONObject player_status = result.getJSONObject("player_status");
                        AisPanelService.m_media_title = player_status.getString("media_title");
                        AisPanelService.m_media_source = player_status.getString("media_source");
                        AisPanelService.m_media_album_name = player_status.getString("media_album_name");
                        AisPanelService.m_media_stream_image = player_status.getString("media_stream_image");
                    } catch (JSONException ex) {
                        ex.printStackTrace();
                    }
                }

            }
        });
    }

    public static void publishMessage(String message, String topicPostfix, Context context) {
        // publish via http rest to local instance
        Boolean hotWordOn = false;
        try {
            if (isServiceRunning(context, PorcupineService.class)) {
                hotWordOn = true;
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
        JSONObject json = new JSONObject();
        try {
            json.put("topic", "ais/" + topicPostfix);
            json.put("ais_gate_client_id", AisCoreUtils.AIS_GATE_ID);
            json.put("payload", message);
            json.put("hot_word_on", hotWordOn);
        } catch (JSONException e) {
            Log.e("publishMessage", e.toString());
        }

        doPost(json, context);
    }

    // new
    public static void publishJson(JSONObject message, String topic, Context context) {
        JSONObject json = new JSONObject();
        Boolean hotWordOn = false;
        try {
            if (isServiceRunning(context, PorcupineService.class)) {
                hotWordOn = true;
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
        try {
            json.put("topic", "ais/" + topic);
            json.put("ais_gate_client_id", AisCoreUtils.AIS_GATE_ID);
            json.put("payload", message);
            json.put("hot_word_on", hotWordOn);
        } catch (JSONException e) {
            Log.e("publishJson", e.toString());
        }

        doPost(json, context);
    }

    // -------------------------------------------------------------
    // Message to Cloud
    private static void doCloudPost(JSONObject message, String url) {
        // do the simple HTTP post
        AsyncHttpPost post = new AsyncHttpPost(url);
        JSONObjectBody body = new JSONObjectBody(message);
        post.addHeader("Content-Type", "application/json");
        post.setBody(body);
        AsyncHttpClient.getDefaultInstance().executeJSONObject(post, new AsyncHttpClient.JSONObjectCallback() {

            // Callback is invoked with any exceptions/errors, and the result, if available.
            @Override
            public void onCompleted(Exception e, AsyncHttpResponse response, JSONObject result) {
                if (e != null) {
                    return;
                }
                // TODO say the response

            }
        });
    }

    public static void publishJsonToCloud(JSONObject message, String topic) {
        // message to cloud
        String url = AisCoreUtils.getAisDomCloudWsUrl(true) + topic;
        doCloudPost(message, topic);
    }

    // Sending data home
    // https://developers.home-assistant.io/docs/api/native-app-integration/sending-data
    public static void doPostDomWebHockRequest(String url, JSONObject body, Context appContext){
        DomCustomRequest jsObjRequest = new DomCustomRequest(Request.Method.POST, url, body.toString(), new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {Log.d("AIS auth: ", response.toString());}
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError response) {Log.e("AIS auth: ", response.toString());}
        });
        AisCoreUtils.getRequestQueue(appContext).add(jsObjRequest);
    }


    public static void registerAuthorizationCode(Context context, String code, String clientId) {
        // do the simple HTTP post in async task
        Log.i(TAG, "AIS auth  HTTP post in async task");
        new RetrieveTokenTaskJob(context).execute(code, clientId);
    }

    public static void updateRegistrationPushToken(Context context) {
        // do the simple HTTP post in async task
        new AddUpdateDeviceRegistrationTaskJob(context).execute();
    }

    public static void updateDeviceAddress(Context context, String address) {
        Config config = new Config(context);
        String webhookId = config.getAisHaWebhookId();
        if (!webhookId.equals("")) {
            try {
                // - create url
                String webHookUrl = getDomWsUrl(context) + "/api/webhook/" + webhookId;
                // update address state - create body
                JSONObject jsonUpdate = new JSONObject();
                jsonUpdate.put("type", "update_sensor_states");
                JSONArray sensorsDataArray = new JSONArray();
                JSONObject addressData = new JSONObject();
                addressData.put("icon", "mdi:map");
                addressData.put("state", address);
                addressData.put("type", "sensor");
                addressData.put("unique_id", "geocoded_location");
                sensorsDataArray.put(addressData);
                jsonUpdate.put("data", sensorsDataArray);
                // call
                DomWebInterface.doPostDomWebHockRequest(webHookUrl, jsonUpdate, context.getApplicationContext());
            } catch (Exception e) {
                Log.e(TAG, "updateDeviceAddress error: " + e.getMessage());
            }
        }
    }

    public static void updateDeviceLocation(Context context, Location location) {
        // do the simple HTTP post
        // get ha webhook id from settings
        Config config = new Config(context);
        String webhookId = config.getAisHaWebhookId();
        if (!webhookId.equals("")) {
            try {
                // - create url
                String webHookUrl = getDomWsUrl(context) + "/api/webhook/" + webhookId;
                // create body
                JSONObject json = new JSONObject();
                json.put("type", "update_location");
                JSONObject data = new JSONObject();
                JSONArray gps = new JSONArray();
                gps.put(location.getLatitude());
                gps.put(location.getLongitude());
                data.put("gps", gps);
                data.put("gps_accuracy", location.getAccuracy());
                String batteryState = getBatteryPercentage(context);
                data.put("battery", batteryState);
                data.put("speed", location.getSpeed());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    data.put("course", location.getVerticalAccuracyMeters());
                }
                json.put("data", data);

                // call
                DomWebInterface.doPostDomWebHockRequest(webHookUrl, json, context.getApplicationContext());
                AisCoreUtils.GPS_SERVICE_LOCATIONS_SENT++;

                // update battery state
                // create body
                JSONObject jsonUpdate = new JSONObject();
                jsonUpdate.put("type", "update_sensor_states");
                JSONArray sensorsDataArray = new JSONArray();
                JSONObject batteryData = new JSONObject();
                batteryData.put("icon", "mdi:battery");
                batteryData.put("state", batteryState);
                batteryData.put("type", "sensor");
                batteryData.put("unique_id", "battery");
                sensorsDataArray.put(batteryData);
                jsonUpdate.put("data", sensorsDataArray);
                // call
                DomWebInterface.doPostDomWebHockRequest(webHookUrl, jsonUpdate, context.getApplicationContext());
            } catch (Exception e) {
                Log.e(TAG, "updateDeviceLocation error: " + e.getMessage());
            }
        }
    }
}

class RetrieveTokenTaskJob extends AsyncTask<String, Void, String> {
    final static String TAG = RetrieveTokenTaskJob.class.getName();

    private Context mContext;

    public RetrieveTokenTaskJob (Context context){
        mContext = context;
    }

    @Override
    protected String doInBackground(String[] params) {
        String code = params[0];
        String clientId = params[1];

        try {
            // 1. get token
            String tokenUrl = getDomWsUrl(mContext) + "/auth/token";
            Log.i(TAG, "AIS auth tokenUrl " + tokenUrl);

            // - create body
            StringBuilder body = new StringBuilder();
            body.append("grant_type=authorization_code&");
            body.append("client_id=");
            body.append(clientId);
            body.append("&");
            body.append("code");
            body.append("=");
            body.append(code);
            Log.i(TAG, "AIS auth body " + body);

            // call
            Map<String, String> heders = new HashMap<String, String>();
            heders.put("Content-Type", "application/x-www-form-urlencoded");
            DomCustomRequest jsObjRequest = new DomCustomRequest(Request.Method.POST, tokenUrl, heders, body.toString(), new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    try {
                        Log.i("AIS auth: ", response.toString());
                        // json answer result
                        String ha_access_token = response.getString("access_token");
                        Log.i(TAG, "AIS auth access_token " + ha_access_token);
                        // save ha access_token in settings
                        Config config = new Config(mContext);
                        config.setAisHaAccessToken(ha_access_token);

                        // 2. do the device registration
                        new AddUpdateDeviceRegistrationTaskJob(mContext).execute();

                    } catch (Exception e) {
                        Log.e(TAG, "AIS auth: " + e.getMessage());
                    }
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError response) {
                    Log.e(TAG, "AIS auth: " + response.toString());
                }
            });
            AisCoreUtils.getRequestQueue(mContext.getApplicationContext()).add(jsObjRequest);

        } catch (Exception e) {
            Log.e(TAG, "AIS auth: " + e.getMessage());
            return "";
        }

        return "";
    }


    @Override
    protected void onPostExecute(String  haAccessToken) {
        // just for debug
        if (!haAccessToken.equals("")){
            Log.d(TAG, "haAccessToken: " + haAccessToken);
        }
    }
}


class AddUpdateDeviceRegistrationTaskJob extends AsyncTask<String, Void, String> {
    final static String TAG = AddUpdateDeviceRegistrationTaskJob.class.getName();

    private Context mContext;

    public AddUpdateDeviceRegistrationTaskJob (Context context){
        mContext = context;
    }

    @Override
    protected String doInBackground(String[] params) {
        // get token and save ha access_token in settings
        Config config = new Config(mContext);
        String accessToken = config.getHaAccessToken();
        Log.i(TAG, "AIS auth access_token 2 " + accessToken);

        try {
            // 1. get webhook - create url
            String mobRegistrationUrl = getDomWsUrl(mContext) + "/api/mobile_app/registrations";
            Log.i(TAG, "AIS auth url 2 " + mobRegistrationUrl);
            // create body
            JSONObject webHookJson = new JSONObject();
            webHookJson.put("device_id", AisCoreUtils.AIS_GATE_ID);
            webHookJson.put("app_id", BuildConfig.APPLICATION_ID);
            webHookJson.put("app_name", "AIS dom");
            webHookJson.put("app_version", BuildConfig.VERSION_NAME);
            webHookJson.put("device_name", "mobile_ais_" + AisCoreUtils.AIS_GATE_ID.toLowerCase().replace(" ", "_"));
            webHookJson.put("manufacturer", AisNetUtils.getManufacturer());
            webHookJson.put("model", AisNetUtils.getModel() + " " + AisNetUtils.getDevice() );
            webHookJson.put("os_name", "Android");
            webHookJson.put("os_version", AisNetUtils.getApiLevel() + " " + AisNetUtils.getOsVersion());
            webHookJson.put("supports_encryption", false);
            JSONObject appData = new JSONObject();
            appData.put("push_token", AisCoreUtils.AIS_PUSH_NOTIFICATION_KEY);
            appData.put("push_url", "https://powiedz.co/ords/dom/dom/send_push_data");
            webHookJson.put("app_data", appData);
            // call
            Map<String, String> heders = new HashMap<String, String>();
            heders.put("Content-Type", "application/json; charset=UTF-8");
            heders.put("Authorization", "Bearer " + accessToken);
            DomCustomRequest jsObjRequest = new DomCustomRequest(Request.Method.POST, mobRegistrationUrl, heders, webHookJson.toString(), new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    try {
                        Log.d("AIS auth: ", response.toString());
                        String webhookId = response.getString("webhook_id");
                        Log.i(TAG, "AIS auth webhookId " + webhookId);
                        // save ha Webhook Id in settings
                        config.setAisHaWebhookId(webhookId);

                        // 2. Registering a sensor - battery
                        // https://developers.home-assistant.io/docs/api/native-app-integration/sensors
                        // - create url
                        String webHookUrl = getDomWsUrl(mContext) + "/api/webhook/" + webhookId;
                        // create body
                        JSONObject jsonSensor = new JSONObject();
                        jsonSensor.put("type", "register_sensor");
                        JSONObject jsonSensorData = new JSONObject();
                        jsonSensorData.put("device_class", "battery");
                        jsonSensorData.put("icon", "mdi:battery");
                        jsonSensorData.put("name", "battery");
                        jsonSensorData.put("state", getBatteryPercentage(mContext));
                        jsonSensorData.put("type", "sensor");
                        jsonSensorData.put("unique_id", "battery");
                        jsonSensorData.put("unit_of_measurement", "%");
                        jsonSensor.put("data", jsonSensorData);
                        // call
                        DomWebInterface.doPostDomWebHockRequest(webHookUrl, jsonSensor, mContext.getApplicationContext());

                        // 3. Registering a sensor - geocoded_location
                        JSONObject jsonSensor2 = new JSONObject();
                        jsonSensor2.put("type", "register_sensor");
                        JSONObject jsonSensorData2 = new JSONObject();
                        jsonSensorData2.put("icon", "mdi:map");
                        jsonSensorData2.put("name", "Geocoded Location");
                        jsonSensorData2.put("type", "sensor");
                        jsonSensorData2.put("unique_id", "geocoded_location");
                        jsonSensor2.put("data", jsonSensorData2);
                        // call
                        DomWebInterface.doPostDomWebHockRequest(webHookUrl, jsonSensor2,mContext.getApplicationContext());
                    } catch (Exception e) {
                        Log.e(TAG, "AIS auth: " + e.getMessage());
                    }
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError response) {
                    Log.e(TAG, "AIS auth: " + response.toString());
                }
            });
            AisCoreUtils.getRequestQueue(mContext.getApplicationContext()).add(jsObjRequest);

        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            return e.getMessage();
        }
        return "";
    }

    @Override
    protected void onPostExecute(String webhookId) {
        if (!webhookId.equals("")){
            Log.d(TAG, "webhookId: " + webhookId);

        }
    }
}

