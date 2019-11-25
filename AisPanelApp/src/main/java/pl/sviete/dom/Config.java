package pl.sviete.dom;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpGet;
import com.koushikdutta.async.http.AsyncHttpResponse;
import com.koushikdutta.async.http.body.JSONObjectBody;
import com.koushikdutta.async.http.callback.HttpConnectCallback;

import org.json.JSONException;
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
            String wsUrl = AisCoreUtils.getAisDomCloudWsUrl(true) + "gate_ip_full_info?id=" + gateId;
            // url is the URL to download.
            AsyncHttpClient client = AsyncHttpClient.getDefaultInstance();
            Future<AsyncHttpResponse> rF = client.execute(wsUrl, new HttpConnectCallback() {
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
                            String ip = "";
                            try {
                                JSONObject jsonAnswer = new JSONObject(bb);
                                ip = jsonAnswer.getString("ip");
                                Log.d(TAG, "We have local IP" + bb);

                            } catch (Exception el) {
                                Log.e(TAG, el.toString());
                            }
                        }
                    });
                    Log.d(TAG, "I got a response in callback: ");
                }
            });
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    public String getAppLaunchUrl() {
        String url;

        url = getStringPref(R.string.key_setting_app_launchurl, R.string.default_setting_app_launchurl);
            // if the url is gate id
            if (url.startsWith("dom-")){
                getGateIpFromCloud(url);
                // async get
                url = "https://" + url + ".paczka.pro";
            }


        //
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


    public void setAppLaunchUrl(String url, String host) {
        SharedPreferences.Editor ed = sharedPreferences.edit();
        ed.putString(myContext.getString(R.string.key_setting_app_launchurl), url);
        ed.apply();
        //
        pl.sviete.dom.AisCoreUtils.setAisDomUrl(url);

        //
        // save in file
        //
        try {
            JSONObject mNewConn = new JSONObject();
            mNewConn.put("name", url);
            mNewConn.put("url", url);
            if (host != null) {
                mNewConn.put("host", host);
            }
            AisConnectionHistJSON.addConnection(myContext, mNewConn.toString());
        }
        catch (Exception e) {
            Log.e(TAG, e.toString());
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
