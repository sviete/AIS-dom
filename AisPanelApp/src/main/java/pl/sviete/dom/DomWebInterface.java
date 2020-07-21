package pl.sviete.dom;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.AsyncTask;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpPost;
import com.koushikdutta.async.http.AsyncHttpResponse;
import com.koushikdutta.async.http.body.JSONObjectBody;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.net.HttpURLConnection;
import java.net.URL;

import ai.picovoice.hotword.PorcupineService;

import static pl.sviete.dom.AisCoreUtils.BROADCAST_ACTIVITY_SAY_IT;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_SERVICE_SAY_IT;
import static pl.sviete.dom.AisCoreUtils.BROADCAST_SAY_IT_TEXT;
import static pl.sviete.dom.AisCoreUtils.isServiceRunning;

public class DomWebInterface {
    final static String TAG = DomWebInterface.class.getName();

    private static void doPost(JSONObject message, Context context) {
        // do the simple HTTP post
        String url = pl.sviete.dom.AisCoreUtils.getAisDomUrl() + "/api/webhook/aisdomprocesscommandfromframe";
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
                    String appLaunchUrl = config.getAppLaunchUrl(false);
                    if (appLaunchUrl.startsWith("dom-")) {
                        // sprawdzam połączenie
                        // say("Sprawdzam połączenie.");
                        appLaunchUrl = config.getAppLaunchUrl(true);
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

    public static void registerAuthorizationCode(Context context, String code, String clientId) {
        // do the simple HTTP post in async task
        new RetrieveTokenTaskJob(context).execute(code, clientId);
    }

    public static void updateRegistrationPushToken(Context context) {
        // do the simple HTTP post in async task
        new AddUpdateDeviceRegistrationTaskJob(context).execute();
    }


    // Sending data home TODO
    // https://developers.home-assistant.io/docs/api/native-app-integration/sending-data
    // 1. Update device location
    // 2. Sensors

    public static void updateDeviceLocation(Context context, Location location) {
        // do the simple HTTP post in async task
        new AddUpdateDeviceLocationTaskJob(context, location).execute();
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
        URL url = null;
        String code = params[0];
        String clientId = params[1];
        String ha_access_token = "";
        try {
            // 1. get token
            url = new URL(AisCoreUtils.getAisDomUrl() + "/auth/token");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            con.setRequestProperty("charset", "utf-8");

            // For POST only - START
            con.setDoOutput(true);
            StringBuilder result = new StringBuilder();
            result.append("grant_type=authorization_code&");
            result.append("client_id=");
            result.append(clientId);
            result.append("&");
            result.append("code");
            result.append("=");
            result.append(code);

            OutputStream os = con.getOutputStream();
            os.write(result.toString().getBytes());
            os.flush();
            os.close();

            int responseCode = con.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(
                        con.getInputStream()));
                String inputLine;
                StringBuffer response = new StringBuffer();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                // json answer result
                JSONObject jsonObj = new JSONObject(response.toString());

                ha_access_token = jsonObj.getString("access_token");

                // save ha access_token in settings
                Config config = new Config(mContext);
                config.setAisHaAccessToken(ha_access_token);

                // device registration
                new AddUpdateDeviceRegistrationTaskJob(mContext).execute();

            } else {
                System.out.println("request not worked");
            }
        } catch (Exception e) {
            return ha_access_token;
        }
        return ha_access_token;
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
        URL url = null;
        String webhookId = "";
        // get token
        // save ha access_token in settings
        Config config = new Config(mContext);
        String accessToken = config.getHaAccessToken();

        try {
                // json
                JSONObject json = new JSONObject();
                json.put("device_id", AisCoreUtils.AIS_GATE_ID);
                json.put("app_id", BuildConfig.APPLICATION_ID);
                json.put("app_name", "AIS dom");
                json.put("app_version", BuildConfig.VERSION_NAME);
                json.put("device_name", "mobile_ais_" + AisCoreUtils.AIS_GATE_ID.toLowerCase().replace(" ", "_"));
                json.put("manufacturer", AisNetUtils.getManufacturer());
                json.put("model", AisNetUtils.getModel() + " " + AisNetUtils.getDevice() );
                json.put("os_name", "Android");
                json.put("os_version", AisNetUtils.getApiLevel() + " " + AisNetUtils.getOsVersion());
                json.put("supports_encryption", false);
                JSONObject appData = new JSONObject();
                appData.put("push_token", AisCoreUtils.AIS_PUSH_NOTIFICATION_KEY);
                appData.put("push_url", "https://powiedz.co/ords/dom/dom/send_push_data");
                json.put("app_data", appData);

                //
                // JSONObjectBody body = new JSONObjectBody(json);

                 url = new URL(AisCoreUtils.getAisDomUrl() + "/api/mobile_app/registrations");
                 HttpURLConnection con = (HttpURLConnection) url.openConnection();
                 con.setRequestMethod("POST");
                 con.setRequestProperty("Content-Type", "application/json");
                 con.setRequestProperty("charset", "utf-8");
                 con.setRequestProperty("Authorization", "Bearer " + accessToken);

                 // For POST only - START
                 con.setDoOutput(true);
                 OutputStream os = con.getOutputStream();
                 os.write(json.toString().getBytes("UTF-8"));
                 os.flush();
                 os.close();

                 int responseCode = con.getResponseCode();
                 if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                     BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                     StringBuffer response = new StringBuffer();
                     String inputLine;
                     while ((inputLine = in.readLine()) != null) {
                            response.append(inputLine);
                     }
                     in.close();
                     // json answer result
                     JSONObject jsonObj = new JSONObject();
                     jsonObj = new JSONObject(response.toString());
                     webhookId = jsonObj.getString("webhook_id");

                     // save ha Webhook Id in settings
                     config.setAisHaWebhookId(webhookId);

                     return webhookId;
                 }

        } catch (Exception e) {
            return webhookId;
        }
        return webhookId;
    }


    @Override
    protected void onPostExecute(String webhookId) {
        // register mobile in home assistant /api/mobile_app/registrations
        if (!webhookId.equals("")){
            // TODO save webhookId
            Log.d(TAG, "webhookId: " + webhookId);

        }
    }
}


class AddUpdateDeviceLocationTaskJob extends AsyncTask<String, Void, String> {
    final static String TAG = AddUpdateDeviceRegistrationTaskJob.class.getName();

    private Context mContext;
    private Location mLocation;

    public AddUpdateDeviceLocationTaskJob (Context context, Location location){
        mContext = context;
        mLocation = location;
    }

    @Override
    protected String doInBackground(String[] params) {
        // save ha access_token in settings
        Config config = new Config(mContext);
        String accessToken = config.getHaAccessToken();
        String webhookId = config.getAisHaWebhookId();

        if (!accessToken.equals("") && !webhookId.equals("")) {
            try {
                // json
                JSONObject json = new JSONObject();
                json.put("type", "update_location");
                JSONObject data = new JSONObject();
                JSONArray gps = new JSONArray();
                gps.put(mLocation.getLatitude());
                gps.put(mLocation.getLongitude());
                data.put("gps", gps);
                data.put("gps_accuracy", mLocation.getAccuracy());
                json.put("data", data);

                //
                // JSONObjectBody body = new JSONObjectBody(json);

                URL url = new URL(AisCoreUtils.getAisDomUrl() + "/api/webhook/" + webhookId);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("POST");
                con.setRequestProperty("Content-Type", "application/json");
                con.setRequestProperty("charset", "utf-8");
                con.setRequestProperty("Authorization", "Bearer " + accessToken);

                // For POST only - START
                con.setDoOutput(true);
                OutputStream os = con.getOutputStream();
                os.write(json.toString().getBytes("UTF-8"));
                os.flush();
                os.close();

                int responseCode = con.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                    StringBuffer response = new StringBuffer();
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();


                    // TODO answer result
                    return "";
                }
            } catch (Exception e) {
                return e.getMessage();
            }
        }
        return "no access token";
    }


    @Override
    protected void onPostExecute(String gateAnswer) {
         Log.d(TAG, "gateAnswer: " + gateAnswer);
    }
}
