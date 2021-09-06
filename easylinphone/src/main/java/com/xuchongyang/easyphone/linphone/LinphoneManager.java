package com.xuchongyang.easyphone.linphone;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

import com.xuchongyang.easyphone.R;

import org.linphone.core.Account;
import org.linphone.core.AudioDevice;
import org.linphone.core.AuthInfo;
import org.linphone.core.AuthMethod;
import org.linphone.core.Call;
import org.linphone.core.CallLog;
import org.linphone.core.CallStats;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatRoom;
import org.linphone.core.Conference;
import org.linphone.core.ConfiguringState;
import org.linphone.core.Content;
import org.linphone.core.Core;
import org.linphone.core.CoreException;
import org.linphone.core.EcCalibratorStatus;
import org.linphone.core.Factory;
import org.linphone.core.CoreListener;
import org.linphone.core.Event;
import org.linphone.core.Friend;
import org.linphone.core.FriendList;
import org.linphone.core.GlobalState;
import org.linphone.core.InfoMessage;
import org.linphone.core.PresenceModel;
import org.linphone.core.ProxyConfig;
import org.linphone.core.PublishState;
import org.linphone.core.RegistrationState;
import org.linphone.core.SubscriptionState;
import org.linphone.core.VersionUpdateCheckResult;
import org.linphone.mediastream.Log;

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

public class LinphoneManager implements CoreListener {
    private static final String TAG = "LinphoneManager";
    private static LinphoneManager instance;
    private Context mServiceContext;
    private Core mLc;
    private Timer mTimer;
    private static boolean sExited;

    private String mLPConfigXsd = null;
    private String mLinphoneFactoryConfigFile = null;
    public String mLinphoneConfigFile = null;
    private String mLinphoneRootCaFile = null;

    public LinphoneManager(Context serviceContext) {
        mServiceContext = serviceContext;
        Factory.instance().setDebugMode(false, "ais-sip");
        sExited = false;

        String basePath = mServiceContext.getFilesDir().getAbsolutePath();
        mLPConfigXsd = basePath + "/lpconfig.xsd";
        mLinphoneFactoryConfigFile = basePath + "/linphonerc";
        mLinphoneConfigFile = basePath + "/.linphonerc";
        mLinphoneRootCaFile = basePath + "/rootca.pem";
    }

    public synchronized static final LinphoneManager createAndStart(Context context) {
        if (instance != null) {
            throw new RuntimeException("Linphone Manager is already initialized");
        }
        instance = new LinphoneManager(context);
        instance.startLibLinphone(context);
        return instance;
    }

    public static synchronized Core getLcIfManagerNotDestroyOrNull() {
        if (sExited || instance == null) {
            Log.e("Trying to get linphone core while LinphoneManager already destroyed or not created");
            return null;
        }
        return getLc();
    }

    public static final boolean isInstanceiated() {
        return instance != null;
    }

    public static synchronized final Core getLc() {
        return getInstance().mLc;
    }

    public static synchronized final LinphoneManager getInstance() {
        if (instance != null) {
            return instance;
        }
        if (sExited) {
            throw new RuntimeException("Linphone Manager was already destroyed. "
                    + "Better use getLcIfManagerNotDestroyed and check returned value");
        }
        throw new RuntimeException("Linphone Manager should be created before accessed");
    }

    private synchronized void startLibLinphone(Context context) {
        try {
            copyAssetsFromPackage();
            mLc = Factory.instance().createCore(mLinphoneConfigFile, mLinphoneFactoryConfigFile, context);
            mLc.addListener((CoreListener)context);

            try {
                initLibLinphone();
            } catch (Exception e) {
                Log.e(e);
            }

            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            if (mLc != null) {
                                mLc.iterate();
                            }
                        }
                    });
                }
            };
            mTimer = new Timer("Linphone Scheduler");
            mTimer.schedule(task, 0, 20);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "startLibLinphone: cannot start linphone");
        }
    }

    private synchronized void initLibLinphone() throws CoreException {

        //
        setUserAgent();

        int migrationResult = getLc().migrateToMultiTransport();
        Log.d(TAG, "Migration to multi transport result = " + migrationResult);

        mLc.setNetworkReachable(true);

        //
        mLc.enableAdaptiveRateControl(true);

        //audio
        LinphoneUtils.getConfig(mServiceContext).setInt("audio", "codec_bitrate_limit", 36);

        // mLc.setPreferredVideoDefinitionByName("720p");
        mLc.setUploadBandwidth(1536);
        mLc.setDownloadBandwidth(1536);
    }

    private void copyAssetsFromPackage() throws IOException {
        LinphoneUtils.copyIfNotExist(mServiceContext, R.raw.linphonerc_default, mLinphoneConfigFile);
        LinphoneUtils.copyIfNotExist(mServiceContext, R.raw.linphonerc_factory, new File(mLinphoneFactoryConfigFile).getName());
        LinphoneUtils.copyIfNotExist(mServiceContext, R.raw.lpconfig, mLPConfigXsd);
        LinphoneUtils.copyIfNotExist(mServiceContext, R.raw.rootca, mLinphoneRootCaFile);
    }

    private void setUserAgent() {
        try {
            String versionName = mServiceContext.getPackageManager().getPackageInfo(mServiceContext.getPackageName(),
                    0).versionName;
            if (versionName == null) {
                versionName = String.valueOf(mServiceContext.getPackageManager().getPackageInfo(mServiceContext.getPackageName(), 0).versionCode);
            }
            mLc.setUserAgent("Hunayutong", versionName);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static synchronized void destroy() {
        if (instance == null) {
            return;
        }
        sExited = true;
        instance.doDestroy();
    }

    private void doDestroy() {
        try {
            mTimer.cancel();
            mLc.stop();
        } catch (RuntimeException e) {
            e.printStackTrace();
        } finally {
            mLc = null;
            instance = null;
        }
    }



    @Override
    public void onAudioDeviceChanged(@androidx.annotation.NonNull Core core, @androidx.annotation.NonNull AudioDevice audioDevice) {

    }

    @Override
    public void onReferReceived(@androidx.annotation.NonNull Core core, @androidx.annotation.NonNull String referTo) {

    }

    @Override
    public void onCallStatsUpdated(@androidx.annotation.NonNull Core core, @androidx.annotation.NonNull Call call, @androidx.annotation.NonNull CallStats callStats) {

    }

    @Override
    public void onIsComposingReceived(@androidx.annotation.NonNull Core core, @androidx.annotation.NonNull ChatRoom chatRoom) {

    }

    @Override
    public void onFriendListCreated(@androidx.annotation.NonNull Core core, @androidx.annotation.NonNull FriendList friendList) {

    }

    @Override
    public void onQrcodeFound(@androidx.annotation.NonNull Core core, @androidx.annotation.Nullable String result) {

    }

    @Override
    public void onNotifyReceived(@androidx.annotation.NonNull Core core, @androidx.annotation.NonNull Event linphoneEvent, @androidx.annotation.NonNull String notifiedEvent, @androidx.annotation.NonNull Content body) {

    }

    @Override
    public void onSubscribeReceived(@androidx.annotation.NonNull Core core, @androidx.annotation.NonNull Event linphoneEvent, @androidx.annotation.NonNull String subscribeEvent, @androidx.annotation.NonNull Content body) {

    }

    @Override
    public void onSubscriptionStateChanged(@androidx.annotation.NonNull Core core, @androidx.annotation.NonNull Event linphoneEvent, SubscriptionState state) {

    }

    @Override
    public void onEcCalibrationResult(@androidx.annotation.NonNull Core core, EcCalibratorStatus status, int delayMs) {

    }

    @Override
    public void onImeeUserRegistration(@androidx.annotation.NonNull Core core, boolean status, @androidx.annotation.NonNull String userId, @androidx.annotation.NonNull String info) {

    }

    @Override
    public void onCallIdUpdated(@androidx.annotation.NonNull Core core, @androidx.annotation.NonNull String previousCallId, @androidx.annotation.NonNull String currentCallId) {

    }

    @Override
    public void onAudioDevicesListUpdated(@androidx.annotation.NonNull Core core) {

    }

    @Override
    public void onNetworkReachable(@androidx.annotation.NonNull Core core, boolean reachable) {

    }

    @Override
    public void onInfoReceived(@androidx.annotation.NonNull Core core, @androidx.annotation.NonNull Call call, @androidx.annotation.NonNull InfoMessage message) {

    }

    @Override
    public void onMessageSent(@androidx.annotation.NonNull Core core, @androidx.annotation.NonNull ChatRoom chatRoom, @androidx.annotation.NonNull ChatMessage message) {

    }

    @Override
    public void onCallLogUpdated(@androidx.annotation.NonNull Core core, @androidx.annotation.NonNull CallLog callLog) {

    }

    @Override
    public void onNotifyPresenceReceived(@androidx.annotation.NonNull Core core, @androidx.annotation.NonNull Friend linphoneFriend) {

    }

    @Override
    public void onLastCallEnded(@androidx.annotation.NonNull Core core) {

    }

    @Override
    public void onCallCreated(@androidx.annotation.NonNull Core core, @androidx.annotation.NonNull Call call) {

    }

    @Override
    public void onMessageReceivedUnableDecrypt(@androidx.annotation.NonNull Core core, @androidx.annotation.NonNull ChatRoom chatRoom, @androidx.annotation.NonNull ChatMessage message) {

    }

    @Override
    public void onNewSubscriptionRequested(@androidx.annotation.NonNull Core core, @androidx.annotation.NonNull Friend linphoneFriend, @androidx.annotation.NonNull String url) {

    }

    @Override
    public void onConfiguringStatus(@androidx.annotation.NonNull Core core, ConfiguringState status, @androidx.annotation.Nullable String message) {

    }

    @Override
    public void onLogCollectionUploadProgressIndication(@androidx.annotation.NonNull Core core, int offset, int total) {

    }

    @Override
    public void onMessageReceived(@androidx.annotation.NonNull Core core, @androidx.annotation.NonNull ChatRoom chatRoom, @androidx.annotation.NonNull ChatMessage message) {

    }

    @Override
    public void onAuthenticationRequested(@androidx.annotation.NonNull Core core, @androidx.annotation.NonNull AuthInfo authInfo, @androidx.annotation.NonNull AuthMethod method) {

    }

    @Override
    public void onChatRoomSubjectChanged(@androidx.annotation.NonNull Core core, @androidx.annotation.NonNull ChatRoom chatRoom) {

    }

    @Override
    public void onBuddyInfoUpdated(@androidx.annotation.NonNull Core core, @androidx.annotation.NonNull Friend linphoneFriend) {

    }

    @Override
    public void onNotifyPresenceReceivedForUriOrTel(@androidx.annotation.NonNull Core core, @androidx.annotation.NonNull Friend linphoneFriend, @androidx.annotation.NonNull String uriOrTel, @androidx.annotation.NonNull PresenceModel presenceModel) {

    }

    @Override
    public void onEcCalibrationAudioUninit(@androidx.annotation.NonNull Core core) {

    }

    @Override
    public void onCallStateChanged(@androidx.annotation.NonNull Core core, @androidx.annotation.NonNull Call call, Call.State state, @androidx.annotation.NonNull String message) {

    }

    @Override
    public void onGlobalStateChanged(@androidx.annotation.NonNull Core core, GlobalState state, @androidx.annotation.NonNull String message) {

    }

    @Override
    public void onRegistrationStateChanged(@androidx.annotation.NonNull Core core, @androidx.annotation.NonNull ProxyConfig proxyConfig, RegistrationState state, @androidx.annotation.NonNull String message) {

    }

    @Override
    public void onFriendListRemoved(@androidx.annotation.NonNull Core core, @androidx.annotation.NonNull FriendList friendList) {

    }

    @Override
    public void onLogCollectionUploadStateChanged(@androidx.annotation.NonNull Core core, Core.LogCollectionUploadState state, @androidx.annotation.NonNull String info) {

    }

    @Override
    public void onTransferStateChanged(@androidx.annotation.NonNull Core core, @androidx.annotation.NonNull Call transfered, Call.State callState) {

    }

    @Override
    public void onChatRoomStateChanged(@androidx.annotation.NonNull Core core, @androidx.annotation.NonNull ChatRoom chatRoom, ChatRoom.State state) {

    }

    @Override
    public void onChatRoomEphemeralMessageDeleted(@androidx.annotation.NonNull Core core, @androidx.annotation.NonNull ChatRoom chatRoom) {

    }

    @Override
    public void onPublishStateChanged(@androidx.annotation.NonNull Core core, @androidx.annotation.NonNull Event linphoneEvent, PublishState state) {

    }

    @Override
    public void onEcCalibrationAudioInit(@androidx.annotation.NonNull Core core) {

    }

    @Override
    public void onAccountRegistrationStateChanged(@androidx.annotation.NonNull Core core, @androidx.annotation.NonNull Account account, RegistrationState state, @androidx.annotation.NonNull String message) {

    }

    @Override
    public void onVersionUpdateCheckResultReceived(@androidx.annotation.NonNull Core core, @androidx.annotation.NonNull VersionUpdateCheckResult result, String version, @androidx.annotation.Nullable String url) {

    }

    @Override
    public void onDtmfReceived(@androidx.annotation.NonNull Core core, @androidx.annotation.NonNull Call call, int dtmf) {

    }

    @Override
    public void onConferenceStateChanged(@androidx.annotation.NonNull Core core, @androidx.annotation.NonNull Conference conference, Conference.State state) {

    }

    @Override
    public void onCallEncryptionChanged(@androidx.annotation.NonNull Core core, @androidx.annotation.NonNull Call call, boolean mediaEncryptionEnabled, @androidx.annotation.Nullable String authenticationToken) {

    }

    @Override
    public void onChatRoomRead(@androidx.annotation.NonNull Core core, @androidx.annotation.NonNull ChatRoom chatRoom) {

    }

    @Override
    public void onFirstCallStarted(@androidx.annotation.NonNull Core core) {

    }
}
