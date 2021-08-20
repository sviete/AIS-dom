package pl.sviete.dom.sip;


import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

import net.sourceforge.peers.Config;
import net.sourceforge.peers.media.MediaMode;
import net.sourceforge.peers.sip.syntaxencoding.SipURI;

import pl.sviete.dom.AisNetUtils;

public class CustomConfig implements Config {

    private InetAddress publicIpAddress;

    @Override public String getUserPart() {
        return "u3";
    }
    @Override public String getDomain() {
        return "10.10.10.10";
    }
    @Override public String getPassword() {
        return "p3";
    }
    
    @Override
    public MediaMode getMediaMode() { return MediaMode.captureAndPlayback; }

    @Override public String getAuthorizationUsername() { return getUserPart(); }

    @Override
    public void setPublicInetAddress(InetAddress inetAddress) {
        publicIpAddress = inetAddress;
    }

    @Override public SipURI getOutboundProxy() { return null; }
    @Override public int getSipPort() { return 0; }
    @Override public boolean isMediaDebug() { return false; }
    @Override public String getMediaFile() { return null; }
    @Override public int getRtpPort() { return 0; }
    @Override public void setLocalInetAddress(InetAddress inetAddress) { }
    @Override public void setUserPart(String userPart) { }
    @Override public void setDomain(String domain) { }
    @Override public void setPassword(String password) { }
    @Override public void setOutboundProxy(SipURI outboundProxy) { }
    @Override public void setSipPort(int sipPort) { }
    @Override public void setMediaMode(MediaMode mediaMode) { }
    @Override public void setMediaDebug(boolean mediaDebug) { }
    @Override public void setMediaFile(String mediaFile) { }
    @Override public void setRtpPort(int rtpPort) { }
    @Override public void save() { }
    @Override public void setAuthorizationUsername(String authorizationUsername) { }

    @Override
    public InetAddress getPublicInetAddress() {
        return publicIpAddress;
    }

    @Override
    public InetAddress getLocalInetAddress() {
        InetAddress inetAddress;
        try {
            inetAddress = InetAddress.getByName(getMyIp());
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return null;
        }
        return inetAddress;
    }

    private static String getMyIp() {
        return AisNetUtils.getIPAddress(true);
    }

}
