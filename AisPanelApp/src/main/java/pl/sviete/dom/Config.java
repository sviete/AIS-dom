package pl.sviete.dom;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpResponse;
import com.koushikdutta.async.http.callback.HttpConnectCallback;
import org.json.JSONObject;

import pl.sviete.dom.connhist.AisConnectionHistJSON;

public class Config {
    public final Context myContext;
    private final SharedPreferences sharedPreferences;
    private SharedPreferences.OnSharedPreferenceChangeListener prefsChangedListener;
    public static final String TAG = Context.class.getName();

    public Config(Context appContext)
    {
        myContext = appContext;
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext);
    }

    public void startListeningForConfigChanges(SharedPreferences.OnSharedPreferenceChangeListener prefsChangedListener) {
        this.prefsChangedListener = prefsChangedListener;
        sharedPreferences.registerOnSharedPreferenceChangeListener(prefsChangedListener);
    }

    public void stopListeningForConfigChanges() {
        sharedPreferences.registerOnSharedPreferenceChangeListener(prefsChangedListener);
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

    // APP
    public void checkTheLocalIp(String localIP, String gateId){
        // we need to check if the gate is live on this IP address
        AsyncHttpClient client = AsyncHttpClient.getDefaultInstance();
        Future<AsyncHttpResponse> rF = client.execute("http://" + localIP + ":8122", new HttpConnectCallback() {
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
                            String localGateId = jsonAnswer.getString("gate_id");
                            if (localGateId.equals(gateId)){
                                // local connection is possible

                            }
                            Log.d(TAG, "We have local IP" + bb);
                        } catch (Exception el) {
                            Log.e(TAG, el.toString());
                        }

                        // we need to check if the gate is live on this IP address


                    }
                });
                Log.d(TAG, "I got a response in callback: ");
            }
        });
    }

    public void getGateIpFromCloud(String gateId){
        try {
            // ask cloud for local IP
            // https://powiedz.co/ords/dom/dom/gate_ip_info?id=dom-abaa0803afcda623
            String wsUrl = AisCoreUtils.getAisDomCloudWsUrl(true) + "gate_ip_info?id=" + gateId;
            AsyncHttpClient client = AsyncHttpClient.getDefaultInstance();
            Future<AsyncHttpResponse> rF = client.execute(wsUrl, new HttpConnectCallback() {
                // Callback is invoked with any exceptions/errors, and the result, if available.
                public void onConnectCompleted(Exception e, AsyncHttpResponse resp) {
                    if (e != null) {
                        tryCloudConnection(gateId);
                        e.printStackTrace();
                        return;
                    }

                    resp.setDataCallback(new DataCallback() {
                        public void onDataAvailable(DataEmitter dataEmitter, ByteBufferList byteBufferList) {
                            String localIP = byteBufferList.readString();
                            // note that this data has been read
                            byteBufferList.recycle();

                            // save and redirect if not on watch
                            localIP = localIP.replace("###ais###", "");
                            localIP = localIP.replace("###dom###", "");
                            localIP = localIP.trim();
                            if (localIP.equals("ais-dom")) {
                                // wrong gate id
                                tryCloudConnection(gateId);
                            }
                            tryLocalConnection(localIP, gateId);
                        }
                    });
                }
            });
        } catch (Exception e)  {
            tryCloudConnection(gateId);
            Log.e("getGateIpFromCloud e: ", e.toString());
        }
    }

    public void tryCloudConnection(String gateId){
        String url = "https://" +gateId + ".powiedz.co";
        pl.sviete.dom.AisCoreUtils.setAisDomUrl(url);
        if (!pl.sviete.dom.AisCoreUtils.onWatch()){
            // redirect browser...
            Intent intent = new Intent(BrowserActivity.BROADCAST_ACTION_LOAD_URL);
            intent.putExtra(BrowserActivity.BROADCAST_ACTION_LOAD_URL, url);
            myContext.startActivity(intent);
        }
    }

    public void tryLocalConnection(String gateIP, String gateID){
        try {
            // check local IP
            String wsUrl = "http://" + gateIP + ":8122";
            AsyncHttpClient client = AsyncHttpClient.getDefaultInstance();
            Future<AsyncHttpResponse> rF = client.execute(wsUrl, new HttpConnectCallback() {
                // Callback is invoked with any exceptions/errors, and the result, if available.
                public void onConnectCompleted(Exception e, AsyncHttpResponse resp) {
                    if (e != null) {
                        tryCloudConnection(gateID);
                        e.printStackTrace();
                        return;
                    }

                    resp.setDataCallback(new DataCallback() {
                        public void onDataAvailable(DataEmitter dataEmitter, ByteBufferList byteBufferList) {
                            String localIP = byteBufferList.readString();
                            // TODO compare the gate ID with JSON answer from gate "gate_id":"dom-c5fbba736429c4fc"
                            // note that this data has been read
                            byteBufferList.recycle();
                            String url = "http://" +localIP + ":8180";
                            pl.sviete.dom.AisCoreUtils.setAisDomUrl(url);
                            if (!pl.sviete.dom.AisCoreUtils.onWatch()){
                                // redirect browser...
                                Intent intent = new Intent(BrowserActivity.BROADCAST_ACTION_LOAD_URL);
                                intent.putExtra(BrowserActivity.BROADCAST_ACTION_LOAD_URL, url);
                                myContext.startActivity(intent);
                            }

                        }
                    });
                }
            });
        } catch (Exception e)  {
            tryCloudConnection(gateID);
            Log.e("getGateIpFromCloud e: ", e.toString());
        }
    }

    public String getAppLaunchUrl() {
        String url;

        url = getStringPref(R.string.key_setting_app_launchurl, R.string.default_setting_app_launchurl);
            // if the url is gate id
            if (url.startsWith("dom-")){
                // TODO sync get then redirect...
                // getGateIpFromCloud(url);
            }
        pl.sviete.dom.AisCoreUtils.setAisDomUrl(url);
        return url;
    }

    public String getAppRemoteControllerMode() {
        return getStringPref(R.string.key_setting_app_remotecontrollermode,
                    R.string.default_setting_app_remotecontrollermode);
    }

    public Boolean getAppDiscoveryMode() {
        return getBoolPref(R.string.key_setting_app_discovery, R.string.default_setting_app_discovery);
    }


    public void setAppLaunchUrl(String url, String gate, String source) {
        Log.w(TAG, "---------------------------------");
        Log.w(TAG, "---------------------------------");
        Log.w(TAG, "source " + source);
        Log.w(TAG, "---------------------------------");
        Log.w(TAG, "---------------------------------");
        String launchurl = url;
        if (source.equals("scan")) {
            launchurl = gate;
        }  else if (source.equals("history")){
            launchurl = gate;
        }

        if (!source.equals("browser")) {
            // when the info is from scanner or history we just need to save it in settings
            SharedPreferences.Editor ed = sharedPreferences.edit();
            ed.putString(myContext.getString(R.string.key_setting_app_launchurl), launchurl);
            ed.apply();
        } else {
            // if the connection info is from browser we are going to save it in history too
            try {
                JSONObject mNewConn = new JSONObject();
                mNewConn.put("name", url);
                mNewConn.put("url", url);
                mNewConn.put("gate", gate);
                AisConnectionHistJSON.addConnection(myContext, mNewConn.toString());
            } catch (Exception e) {
                Log.e(TAG, e.toString());
            }
        }
    }

    public void setAppRemoteControllerMode(String mode) {
        SharedPreferences.Editor ed = sharedPreferences.edit();
        ed.putString(myContext.getString(R.string.key_setting_app_remotecontrollermode), mode);
        ed.apply();
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
