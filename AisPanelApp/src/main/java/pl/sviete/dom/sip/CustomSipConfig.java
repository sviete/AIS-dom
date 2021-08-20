package pl.sviete.dom.sip;

import java.net.InetAddress;
import java.net.UnknownHostException;

import net.sourceforge.peers.Config;
import net.sourceforge.peers.media.MediaMode;
import net.sourceforge.peers.sip.syntaxencoding.SipURI;

import pl.sviete.dom.AisNetUtils;

public class CustomSipConfig implements Config {

    private InetAddress publicIpAddress;
    private String sipUserPart;
    private String sipPassword;
    private String sipDomain;


    @Override public String getUserPart() {
        return sipUserPart;
    }
    @Override public String getDomain() {
        return sipDomain;
    }
    @Override public String getPassword() {
        return sipPassword;
    }

    @Override
    public MediaMode getMediaMode() {
        return MediaMode.captureAndPlayback;
    }

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
    @Override public void setUserPart(String userPart) {
        sipUserPart = userPart;
    }
    @Override public void setDomain(String domain) {
        sipDomain = domain;
    }
    @Override public void setPassword(String password) {
        sipPassword = password;
    }
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
