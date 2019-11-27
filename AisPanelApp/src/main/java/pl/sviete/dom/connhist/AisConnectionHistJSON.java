package pl.sviete.dom.connhist;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;


public class AisConnectionHistJSON {
    static String fileName = "myConnHist_v1.json";
    static String TAG = "AisConnectionHistJSON";


    public static void addConnection(Context context, String mJsonConnection){
        try{
            JSONObject mConn = new JSONObject(mJsonConnection);

            // get connection url
            String url = mConn.getString("url");

            // delete connection if exists
            delConnection(context, url);

            // add
            String timeStamp = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(Calendar.getInstance().getTime());
            mConn.put("time", timeStamp);
            JSONArray mConnArray = new JSONArray(AisConnectionHistJSON.getHistoryConnectionsData(context));
            mConnArray.put(mConn);
            saveHistoryConnectionsData(context, mConnArray.toString());


        }catch (Exception e) {
            Log.e(TAG, "Error in Reading: " + e.toString());
        }
    }

    public static void delConnection(Context context, String mConUrl){
        try{
            JSONArray mConnArray = new JSONArray(AisConnectionHistJSON.getHistoryConnectionsData(context));
            for (int i = 0; i < mConnArray.length(); i++) {
                JSONObject mConObj = mConnArray.getJSONObject(i);
                if (mConObj.getString("url").equals(mConUrl)){
                    mConnArray.remove(i);
                }
            }
            saveHistoryConnectionsData(context, mConnArray.toString());


        }catch (Exception e) {
            Log.e(TAG, "Error in Reading: " + e.toString());
        }
    }

    public static String getLocalIpForGate(Context context, String gateId){
        try{
            JSONArray mConnArray = new JSONArray(AisConnectionHistJSON.getHistoryConnectionsData(context));
            for (int i = 0; i < mConnArray.length(); i++) {
                JSONObject mConObj = mConnArray.getJSONObject(i);
                if (mConObj.getString("gate").equals(gateId)){
                    return mConObj.getString("ip");
                }
            }

        }catch (Exception e) {
            Log.e(TAG, "Error in Reading: " + e.toString());
        }
        return "";
    }

    public static void saveHistoryConnectionsData(Context context, String mJsonResponse) {
        try {
            FileWriter file = new FileWriter(context.getFilesDir().getPath() + "/" + fileName);
            file.write(mJsonResponse);
            file.flush();
            file.close();
        } catch (Exception e) {
            Log.e(TAG, "Error in Writing: " + e.toString());
        }
    }

    public static String getHistoryConnectionsData(Context context) {
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