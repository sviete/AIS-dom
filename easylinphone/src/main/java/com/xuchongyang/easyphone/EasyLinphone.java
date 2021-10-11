package com.xuchongyang.easyphone;

import static java.lang.Thread.sleep;

import android.content.Context;
import android.content.Intent;

import com.xuchongyang.easyphone.callback.PhoneCallback;
import com.xuchongyang.easyphone.callback.RegistrationCallback;
import com.xuchongyang.easyphone.linphone.LinphoneManager;
import com.xuchongyang.easyphone.linphone.LinphoneUtils;
import com.xuchongyang.easyphone.linphone.PhoneBean;
import com.xuchongyang.easyphone.service.LinphoneService;

import org.linphone.core.Call;
import org.linphone.core.Core;


public class EasyLinphone {
    private static ServiceWaitThread mServiceWaitThread;
    private static String mUsername, mPassword, mServerIP;


    public static void startService(Context context) {
        if (!LinphoneService.isReady()) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClass(context, LinphoneService.class);
            context.startService(intent);
        }
    }

    public static void setAccount(String username, String password, String serverIP) {
        mUsername = username;
        mPassword = password;
        mServerIP = serverIP;
    }


    public static void addCallback(RegistrationCallback registrationCallback,
                                   PhoneCallback phoneCallback) {
        if (LinphoneService.isReady()) {
            LinphoneService.addRegistrationCallback(registrationCallback);
            LinphoneService.addPhoneCallback(phoneCallback);
        } else {
            mServiceWaitThread = new ServiceWaitThread(registrationCallback, phoneCallback);
            mServiceWaitThread.start();
        }
    }

    /**
     * 登录到 SIP 服务器
     */
    public static void login() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (!LinphoneService.isReady()) {
                    try {
                        sleep(80);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                loginToServer();
            }
        }).start();
    }


    public static Call callTo(String num, boolean isVideoCall) {
        Call call = null;
        if (!LinphoneService.isReady() || !LinphoneManager.isInstanceiated()) {
            return call;
        }
        if (!num.equals("")) {
            PhoneBean phone = new PhoneBean();
            phone.setUserName(num);
            phone.setHost(mServerIP);
             call = LinphoneUtils.getInstance().startSingleCallingTo(phone, isVideoCall);
        }
        return call;
    }

    /**
     * 接听来电
     */
    public static void acceptCall() {
        try {
            LinphoneManager.getLc().getCurrentCall().accept();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 挂断当前通话
     */
    public static void hangUp() {
        LinphoneUtils.getInstance().hangUp();
    }


    public static void switchMicrophone(String microphoneId) {
        LinphoneUtils.getInstance().switchMicrophone(microphoneId);
    }

    public static void switchSpeaker(String speakerId) {
        LinphoneUtils.getInstance().switchSpeaker(speakerId);
    }

    private static class ServiceWaitThread extends Thread {
        private PhoneCallback mPhoneCallback;
        private RegistrationCallback mRegistrationCallback;

        ServiceWaitThread(RegistrationCallback registrationCallback, PhoneCallback phoneCallback) {
            mRegistrationCallback = registrationCallback;
            mPhoneCallback = phoneCallback;
        }

        @Override
        public void run() {
            super.run();
            while (!LinphoneService.isReady()) {
                try {
                    sleep(80);
                } catch (InterruptedException e) {
                    throw new RuntimeException("waiting thread sleep() has been interrupted");
                }
            }
            LinphoneService.addPhoneCallback(mPhoneCallback);
            LinphoneService.addRegistrationCallback(mRegistrationCallback);
            mServiceWaitThread = null;
        }
    }

    /**
     * 登录 SIP 服务器
     */
    private static void loginToServer() {
        try {
            if (mUsername == null || mPassword == null || mServerIP == null) {
                throw new RuntimeException("The sip account is not configured.");
            }
            LinphoneUtils.getInstance().registerUserAuth(mUsername, mPassword, mServerIP);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * onResume
     */
    public static void onResume() { }

    /**
     * onPause
     */
    public static void onPause() { }

    /**
     * onDestroy
     */
    public static void onDestroy() { }

    /**
     * 获取 LinphoneCore
     * @return LinphoneCore
     */
    public static Core getLC() {
        return LinphoneManager.getLc();
    }
}
