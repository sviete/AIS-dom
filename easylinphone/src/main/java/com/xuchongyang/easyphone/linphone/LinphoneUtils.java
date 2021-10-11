package com.xuchongyang.easyphone.linphone;

import android.content.Context;
import android.util.Log;

import org.linphone.core.Account;
import org.linphone.core.AccountParams;
import org.linphone.core.Address;
import org.linphone.core.AudioDevice;
import org.linphone.core.AuthInfo;
import org.linphone.core.Call;
import org.linphone.core.CallParams;
import org.linphone.core.Config;
import org.linphone.core.Core;
import org.linphone.core.CoreException;
import org.linphone.core.Factory;
import org.linphone.core.MediaEncryption;
import org.linphone.core.TransportType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;


public class LinphoneUtils {
    private static final String TAG = "LinphoneUtils";
    private static volatile LinphoneUtils sLinphoneUtils;
    private Core mLinphoneCore = null;

    public static LinphoneUtils getInstance() {
        if (sLinphoneUtils == null) {
            synchronized (LinphoneUtils.class) {
                if (sLinphoneUtils == null) {
                    sLinphoneUtils = new LinphoneUtils();
                }
            }
        }
        return sLinphoneUtils;
    }

    private LinphoneUtils() {
        mLinphoneCore = LinphoneManager.getLc();
        if (!mLinphoneCore.hasBuiltinEchoCanceller()) {
            Log.i(TAG, "hasBuiltinEchoCanceller FALSE");
        } else {
            Log.i(TAG, "hasBuiltinEchoCanceller TRUE");
        }

        // mLinphoneCore.enableEchoCancellation(true);
        // mLinphoneCore.enableEchoLimiter(true);
    }

    public void registerUserAuth(String name, String password, String host) throws CoreException {
        Log.e(TAG, "registerUserAuth name = " + name);
        Log.e(TAG, "registerUserAuth pw = " + password);
        Log.e(TAG, "registerUserAuth host = " + host);


        // The auth info can be created from the Factory as it's only a data class
        // userID is set to null as it's the same as the username in our case
        // ha1 is set to null as we are using the clear text password. Upon first register, the hash will be computed automatically.
        // The realm will be determined automatically from the first register, as well as the algorithm
        AuthInfo authInfo = Factory.instance().createAuthInfo(name, null, password, null, null, host, null);

        // Account object replaces deprecated ProxyConfig object
        // Account object is configured through an AccountParams object that we can obtain from the Core
        AccountParams accountParams = mLinphoneCore.createAccountParams();

        // A SIP account is identified by an identity address that we can construct from the username and domain
        String identify = "sip:" + name + "@" + host;
        Address identifyAddr = Factory.instance().createAddress(identify);
        accountParams.setIdentityAddress(identifyAddr);

        // We also need to configure where the proxy server is located
        String proxy = "sip:" + host;
        Address address = Factory.instance().createAddress(proxy);

        // We use the Address object to easily set the transport protocol
        address.setTransport(TransportType.Udp);
        accountParams.setServerAddress(address);
        // And we ensure the account will start the registration process
        accountParams.setRegisterEnabled(true);


        // Now that our AccountParams is configured, we can create the Account object
        Account account = mLinphoneCore.createAccount(accountParams);

        // Now let's add our objects to the Core
        mLinphoneCore.addAuthInfo(authInfo);
        mLinphoneCore.addAccount(account);

        // Also set the newly added account as default
        mLinphoneCore.setDefaultAccount(account);

        // Finally we need the Core to be started for the registration to happen (it could have been started before)
        mLinphoneCore.start();
    }

    public Call startSingleCallingTo(PhoneBean bean, boolean isVideoCall) {
        Address address;
        Call call = null;

        // As for everything we need to get the SIP URI of the remote and convert it to an Address
        try {
            address = Factory.instance().createAddress("sip:" + bean.getUserName() + "@" + bean.getHost());
            address.setDisplayName(bean.getDisplayName());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        // Create call params expects a Call object for incoming calls, but for outgoing we must use null safely
        CallParams params = mLinphoneCore.createCallParams(null);
        params.setMediaEncryption(MediaEncryption.None);

        try {
            call = mLinphoneCore.inviteAddressWithParams(address, params);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return call;
    }


    public void hangUp() {
        if (mLinphoneCore.getCallsNb() == 0) {
            return;
        }
        Call currentCall = mLinphoneCore.getCurrentCall();
        if (currentCall != null) {
            currentCall.terminate();
        } else {
            mLinphoneCore.terminateAllCalls();
        }
    }


    public void switchMicrophone(String microphoneId) {
        AudioDevice defaultInputAudioDevice = mLinphoneCore.getDefaultInputAudioDevice();
        Log.i(TAG, "defaultInputAudioDevice on start " + defaultInputAudioDevice.getId() );

        // We can get a list of all available audio devices using
        // Note that on tablets for example, there may be no Earpiece device
        for (AudioDevice audioDevice : mLinphoneCore.getExtendedAudioDevices()) {
            Log.i(TAG, "defaultInputAudioDevice audioDevice " + audioDevice.getId());
            if (audioDevice.getId().equals(microphoneId) ) {
                mLinphoneCore.setDefaultInputAudioDevice(audioDevice);
            }
        }

        //
        defaultInputAudioDevice = mLinphoneCore.getDefaultInputAudioDevice();
        Log.i(TAG, "defaultInputAudioDevice on end " + defaultInputAudioDevice.getId());
    }

     public void switchSpeaker(String speakerId) {
         AudioDevice defaultOutputAudioDevice = mLinphoneCore.getDefaultOutputAudioDevice();
         Log.i(TAG, "defaultOutputAudioDevice on start " + defaultOutputAudioDevice.getId() );

         // We can get a list of all available audio devices using
         // Note that on tablets for example, there may be no Earpiece device
         for (AudioDevice audioDevice : mLinphoneCore.getExtendedAudioDevices()) {
             Log.i(TAG, "defaultOutputAudioDevice audioDevice " + audioDevice.getId());
              if (audioDevice.getId().equals(speakerId) ) {
                 mLinphoneCore.setDefaultOutputAudioDevice(audioDevice);
             }
         }

         //
         defaultOutputAudioDevice = mLinphoneCore.getDefaultOutputAudioDevice();
         Log.i(TAG, "defaultOutputAudioDevice on end " + defaultOutputAudioDevice.getId());

     }

    public static void copyIfNotExist(Context context, int resourceId, String target) throws IOException {
        File fileToCopy = new File(target);
        if (!fileToCopy.exists()) {
            copyFromPackage(context, resourceId, fileToCopy.getName());
        }
    }

    public static void copyFromPackage(Context context, int resourceId, String target) throws IOException {
        FileOutputStream outputStream = context.openFileOutput(target, 0);
        InputStream inputStream = context.getResources().openRawResource(resourceId);
        int readByte;
        byte[] buff = new byte[8048];
        while ((readByte = inputStream.read(buff)) != -1) {
            outputStream.write(buff, 0, readByte);
        }
        outputStream.flush();
        outputStream.close();
        inputStream.close();
    }

    public static Config getConfig(Context context) {
        Core lc = getLc();
        if (lc != null) {
            return lc.getConfig();
        }

        if (LinphoneManager.isInstanceiated()) {
            org.linphone.mediastream.Log.w("LinphoneManager not instanciated yet...");
            return Factory.instance().createConfig(context.getFilesDir().getAbsolutePath() + "/.linphonerc");
        }

        return Factory.instance().createConfig(LinphoneManager.getInstance().mLinphoneConfigFile);
    }

    public static void sleep(int time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static Core getLc() {
        if (!LinphoneManager.isInstanceiated()) {
            return null;
        }
        return LinphoneManager.getLcIfManagerNotDestroyOrNull();
    }
}
