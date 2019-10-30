package pl.sviete.dom.connhist;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import android.util.Log;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpResponse;
import com.koushikdutta.async.http.callback.HttpConnectCallback;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


public class AisConnectionHistJSON {
    static String fileName = "myConnHist.json";
    static String TAG = "AisConnectionHistJSON";


    public static void getTheGateId(JSONObject mConn, Context context){
        AsyncHttpClient client = AsyncHttpClient.getDefaultInstance();
        String WsUrl = null;
        try {
            WsUrl = mConn.getString("url").replace("https://", "http://");
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            return;
        }
        WsUrl = WsUrl.replace(":8180", ":8122");
        WsUrl = WsUrl.replace(":8123", ":8122");
        Future<AsyncHttpResponse> rF = client.execute(WsUrl, new HttpConnectCallback() {
            // Callback is invoked with any exceptions/errors, and the result, if available.
            public void onConnectCompleted(Exception e, AsyncHttpResponse resp) {
                if (e != null) {
                    Log.e(TAG, e.toString());
                    return;
                }
                resp.setDataCallback(new DataCallback() {
                    public void onDataAvailable(DataEmitter dataEmitter, ByteBufferList byteBufferList) {
                        String bb = byteBufferList.readString();
                        Log.d(TAG, "I got some answer " + bb);
                        // note that this data has been read
                        byteBufferList.recycle();
                        // get IP from cloud
                        try {
                            JSONObject jsonAnswer = new JSONObject(bb);
                            String localGateId = mConn.getString("url");
                            if (jsonAnswer.has("gate_id")) {
                                localGateId = jsonAnswer.getString("gate_id");
                                Log.d(TAG, "We have localGateId" + bb);
                            }
                            // add
                            String timeStamp = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(Calendar.getInstance().getTime());
                            mConn.put("time", timeStamp);
                            mConn.put("host", localGateId);
                            //
                            JSONArray mConnArray = new JSONArray(AisConnectionHistJSON.getData(context));
                            mConnArray.put(mConn);
                            saveData(context, mConnArray.toString());

                        } catch (Exception el) {
                            Log.e(TAG, el.toString());
                        }
                    }
                });
                Log.d(TAG, "I got a response in callback: ");
            }
        });
    }


    public static void addConnection(Context context, String mJsonConnection){
        try{
            JSONObject mConn = new JSONObject(mJsonConnection);

            // get connection url
            String url = mConn.getString("url");

            // delete connection if exists
            delConnection(context, url);

            // get gate id
            String gateId = "";
            if (mConn.has("host")) {
                gateId = mConn.getString("host");

            } else{
                // try to get gate id from url
                if (url.contains("://dom-")){
                    gateId = url.replace("https://", "");
                    gateId = gateId.replace(".paczka.pro", "");
                } else {
                    getTheGateId(mConn, context);
                    return;
                }

            }

            // add
            String timeStamp = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(Calendar.getInstance().getTime());
            mConn.put("time", timeStamp);
            mConn.put("host", gateId);
            //
            JSONArray mConnArray = new JSONArray(AisConnectionHistJSON.getData(context));
            mConnArray.put(mConn);
            saveData(context, mConnArray.toString());


        }catch (Exception e) {
            Log.e(TAG, "Error in Reading: " + e.toString());
        }
    }

    public static void delConnection(Context context, String mConUrl){
        try{
            JSONArray mConnArray = new JSONArray(AisConnectionHistJSON.getData(context));
            for (int i = 0; i < mConnArray.length(); i++) {
                JSONObject mConObj = mConnArray.getJSONObject(i);
                if (mConObj.getString("url").equals(mConUrl)){
                    mConnArray.remove(i);
                }
            }
            saveData(context, mConnArray.toString());


        }catch (Exception e) {
            Log.e(TAG, "Error in Reading: " + e.toString());
        }
    }

    public static void saveData(Context context, String mJsonResponse) {
        try {
            FileWriter file = new FileWriter(context.getFilesDir().getPath() + "/" + fileName);
            file.write(mJsonResponse);
            file.flush();
            file.close();
        } catch (Exception e) {
            Log.e(TAG, "Error in Writing: " + e.toString());
        }
    }

    public static String getData(Context context) {
        String jsonArray = "[]";
        try {
            File f = new File(context.getFilesDir().getPath() + "/" + fileName);
            //check whether file exists
            FileInputStream is = new FileInputStream(f);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            jsonArray = new String(buffer, "UTF-8");
        } catch (Exception e) {
            Log.e(TAG, "Error in Reading: " + e.toString());
        }
        return jsonArray;
    }
}