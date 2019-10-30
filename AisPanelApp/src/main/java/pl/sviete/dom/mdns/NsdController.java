package pl.sviete.dom.mdns;

import android.content.Context;
import android.content.Intent;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.InetAddress;
import java.util.Map;

public class NsdController {
    private String TAG = "NsdController";
    private String SERVICE_TYPE = "_ais-dom._tcp.";
    public static final String BROADCAST_EVENT_NSD_DOM_SERVICE_FOUND = "BROADCAST_EVENT_NSD_DOM_SERVICE_FOUND";
    NsdManager mNsdManager;
    NsdManager.DiscoveryListener mDiscoveryListener;
    private static JSONArray mNsdList = new JSONArray();
    private Context mContext = null;

    public void initializeDiscoveryListener(Context c) {

        mNsdManager = (NsdManager)c.getSystemService(Context.NSD_SERVICE);

        // Instantiate a new DiscoveryListener
        mDiscoveryListener  = new NsdManager.DiscoveryListener() {

            // Called as soon as service discovery begins.
            @Override
            public void onDiscoveryStarted(String regType) {
                Log.d(TAG, "Service discovery started");
            }

            @Override
            public void onServiceFound(NsdServiceInfo service) {
                // A service was found! Do something with it.
                Log.d(TAG, "Service discovery success" + service);
                mNsdManager.resolveService(service, new MyResolveListener());
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
                String name = serviceInfo.getServiceName();
                Log.e(TAG, "service lost: " + serviceInfo);
                for (int i = 0 ; i < mNsdList.length(); i++) {
                    try {
                        if (mNsdList.getJSONObject(i).getString("name").equals(name)){
                            mNsdList.remove(i);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.i(TAG, "Discovery stopped: " + serviceType);
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery failed: Error code:" + errorCode);
                mNsdManager.stopServiceDiscovery(this);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery failed: Error code:" + errorCode);
                mNsdManager.stopServiceDiscovery(this);
            }
        };
    }

    public static JSONArray get_gates(){
        return mNsdList;
    }

    private void add_gate_info(NsdServiceInfo serviceInfo){
        String name = serviceInfo.getServiceName();
        int port = serviceInfo.getPort();
        InetAddress inetAddress = serviceInfo.getHost();
        String host = inetAddress.getHostName();
        String address = inetAddress.getHostAddress();
        Map<String, byte[]> map = serviceInfo.getAttributes();
        if (map.containsKey("gate_id")) {
            host = new String(map.get("gate_id"));
        }


        for (int i = 0 ; i < mNsdList.length(); i++) {
            try {
                if (mNsdList.getJSONObject(i).getString("name").equals(name)){
                    // remove to update
                    mNsdList.remove(i);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        JSONObject jo = new JSONObject();
        try {
            jo.put("name", name);
            jo.put("host", host);
            jo.put("port", port);
            jo.put("address", address);
            mNsdList.put(jo);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private class MyResolveListener implements NsdManager.ResolveListener {
        @Override
        public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
            // Called when the resolve fails. Use the error code to debug.
            Log.e(TAG, "Resolve failed: " + errorCode);
        }

        @Override
        public void onServiceResolved(NsdServiceInfo serviceInfo) {
            Log.i(TAG, "Resolve Succeeded. " + serviceInfo);
            add_gate_info(serviceInfo);

            // send info to preferences
//            try {
//                Intent intent = new Intent(BROADCAST_EVENT_NSD_DOM_SERVICE_FOUND);
//                LocalBroadcastManager bm = LocalBroadcastManager.getInstance(mContext);
//                bm.sendBroadcast(intent);
//            } catch (Exception e) {
//                e.printStackTrace();
//            }

        }
    }

    public void Start(Context c) {
        initializeDiscoveryListener(c);
        mContext = c;
        mNsdManager.discoverServices(
                SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
    }

    public void Stop() {
        mNsdManager.stopServiceDiscovery(mDiscoveryListener);
    }
}
