package pl.sviete.dom;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

public final class AisNetUtils {
    // OS version
    public static String getOsVersion(){
        return System.getProperty("os.version");
    }
    // API Level
    public static int getApiLevel(){
        return android.os.Build.VERSION.SDK_INT;
    }
    // Device
    public static String getDevice(){
        return android.os.Build.DEVICE;
    }
    // Model
    public static String getModel(){
        return android.os.Build.MODEL;
    }
    // Product
    public static String getProduct(){
        return android.os.Build.PRODUCT;
    }
    // Manufacturer
    public static String getManufacturer(){
        return android.os.Build.MANUFACTURER;
    }
    // NetworkDownSpeed
    public static int getNetworkSpeed(Context context){
        try {
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo != null) {
                return wifiInfo.getLinkSpeed() * 1000; //measured using WifiInfo.LINK_SPEED_UNITS is Mbps * 1000 to have it in Kbps
            }

            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            //should check null because in airplane mode it will be null
            NetworkCapabilities nc = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
                return nc.getLinkDownstreamBandwidthKbps();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    // TODO this should be improved....
    public static String getHostName(){
        String host = "";
        try {
            Process process = Runtime.getRuntime().exec("getprop net.hostname");
            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));

            // Grab the results
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                out.append(line);
            }
            host = out.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (host.equals("")) {
            host = "ais-dom";
        }
        return host;
    }


    /**
     * Returns MAC address of the given interface name.
     *
     * @param interfaceName eth0, wlan0 or NULL=use first interface
     * @return mac address or empty string
     */
    public static String getMACAddress(String interfaceName) {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                if (interfaceName != null) {
                    if (!intf.getName().equalsIgnoreCase(interfaceName)) continue;
                }
                byte[] mac = intf.getHardwareAddress();
                if (mac == null) return "";
                StringBuilder buf = new StringBuilder();
                for (byte aMac : mac) buf.append(String.format("%02X:", aMac));
                if (buf.length() > 0) buf.deleteCharAt(buf.length() - 1);
                return buf.toString();
            }
        } catch (Exception ignored) {
        } // for now eat exceptions
        return "";
    }

    /**
     * Get IP address from first non-localhost interface
     *
     * @param useIPv4 true=return ipv4, false=return ipv6
     * @return address or empty string
     */
    public static String getIPAddress(boolean useIPv4) {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        //boolean isIPv4 = InetAddressUtils.isIPv4Address(sAddr);
                        boolean isIPv4 = sAddr.indexOf(':') < 0;

                        if (useIPv4) {
                            if (isIPv4)
                                return sAddr;
                        } else {
                            if (!isIPv4) {
                                int delim = sAddr.indexOf('%'); // drop ip6 zone suffix
                                return delim < 0 ? sAddr.toUpperCase() : sAddr.substring(0, delim).toUpperCase();
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        } // for now eat exceptions
        return "";
    }


}