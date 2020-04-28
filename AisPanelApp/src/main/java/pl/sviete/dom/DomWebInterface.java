package pl.sviete.dom;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONException;
import org.json.JSONObject;

import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpPost;
import com.koushikdutta.async.http.AsyncHttpResponse;
import com.koushikdutta.async.http.body.JSONObjectBody;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
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

    public static void registerAuthorizationCode(String code, String clientId) {
        // do the simple HTTP post in async task
        new RetrieveTokenTaskJob().execute(code, clientId);
    }

}

class RetrieveTokenTaskJob extends AsyncTask<String, Void, String> {

    @Override
    protected String doInBackground(String[] params) {
        URL obj = null;
        String code = params[0];
        String clientId = params[1];
        try {
            obj = new URL(AisCoreUtils.getAisDomUrl() + "/auth/token");
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
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
            System.out.println("POST Response Code :: " + responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) { //success
                BufferedReader in = new BufferedReader(new InputStreamReader(
                        con.getInputStream()));
                String inputLine;
                StringBuffer response = new StringBuffer();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                // print result
                System.out.println(response.toString());
                //
            } else {
                System.out.println("POST request not worked");
            }
        } catch (Exception e) {
            return null;
        }

        return "token";
    }


    @Override
    protected void onPostExecute(String message) {
        //process message with url to go
        if (!message.equals("")){
            // call the browser url change
//            Intent intent = new Intent(BrowserActivity.BROADCAST_ACTION_LOAD_URL);
//            intent.putExtra(BrowserActivity.BROADCAST_ACTION_LOAD_URL, message);
//            LocalBroadcastManager bm = LocalBroadcastManager.getInstance(myContext);
//            bm.sendBroadcast(intent);
//            AisCoreUtils.setAisDomUrl(message);
        }
    }
}
