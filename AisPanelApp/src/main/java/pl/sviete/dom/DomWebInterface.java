package pl.sviete.dom;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpPost;
import com.koushikdutta.async.http.body.JSONObjectBody;
public class DomWebInterface {

    public static void publishMessage(String message, String topicPostfix){
        Log.d("publishMessage", "publishMessage Called, topic: " + topicPostfix + " message: " + message);
        // publish via http rest to local instance
        // url is the URL to home assistant
        JSONObject json = new JSONObject();
        try {
            json.put("topic", "ais/" + topicPostfix);
            json.put("payload", message);
        } catch (JSONException e) {
            Log.e("publishMessage", e.toString());
        }
        String url =  pl.sviete.dom.AisCoreUtils.getAisDomUrl() + "/api/webhook/aisdomprocesscommandfromframe";
        // do the simple HTTP post
        AsyncHttpPost post = new AsyncHttpPost(url);
        JSONObjectBody body = new JSONObjectBody(json);
        post.addHeader("Content-Type", "application/json");
        post.setBody(body);
        AsyncHttpClient.getDefaultInstance().executeJSONObject(post, null);


    }
}
