package com.xuchongyang.easyphone.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import com.xuchongyang.easyphone.callback.PhoneCallback;
import com.xuchongyang.easyphone.callback.RegistrationCallback;
import com.xuchongyang.easyphone.linphone.KeepAliveHandler;
import com.xuchongyang.easyphone.linphone.LinphoneManager;

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
import org.linphone.core.CoreListener;
import org.linphone.core.EcCalibratorStatus;
import org.linphone.core.Event;
import org.linphone.core.Factory;
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



public class LinphoneService extends Service implements CoreListener {
    private static final String TAG = "LinphoneService";
    private PendingIntent mKeepAlivePendingIntent;
    private static LinphoneService instance;
    private static PhoneCallback sPhoneCallback;
    private static RegistrationCallback sRegistrationCallback;

    public static boolean isReady() {
        return instance != null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Factory.instance();
        LinphoneManager.createAndStart(LinphoneService.this);
        instance = this;
        Intent intent = new Intent(this, KeepAliveHandler.class);
        mKeepAlivePendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        ((AlarmManager)this.getSystemService(Context.ALARM_SERVICE)).setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 60000, 60000, mKeepAlivePendingIntent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.e(TAG, "LinphoneService onDestroy execute");
        removeAllCallback();
        LinphoneManager.getLc().stop();
        LinphoneManager.destroy();
        ((AlarmManager)this.getSystemService(Context.ALARM_SERVICE)).cancel(mKeepAlivePendingIntent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void addPhoneCallback(PhoneCallback phoneCallback) {
        sPhoneCallback = phoneCallback;
    }

    public static void removePhoneCallback() {
        if (sPhoneCallback != null) {
            sPhoneCallback = null;
        }
    }

    public static void addRegistrationCallback(RegistrationCallback registrationCallback) {
        sRegistrationCallback = registrationCallback;
    }

    public static void removeRegistrationCallback() {
        if (sRegistrationCallback != null) {
            sRegistrationCallback = null;
        }
    }

    public void removeAllCallback() {
        removePhoneCallback();
        removeRegistrationCallback();
    }



    @Override
    public void onRegistrationStateChanged(@androidx.annotation.NonNull Core core,
                                           @androidx.annotation.NonNull ProxyConfig proxyConfig,
                                           RegistrationState state,
                                           @androidx.annotation.NonNull String message) {
        String regState = state.toString();
        if (sRegistrationCallback != null && regState.equals(RegistrationState.None.toString())) {
            sRegistrationCallback.registrationNone();
        } else if (regState.equals(RegistrationState.Progress.toString())) {
            sRegistrationCallback.registrationProgress();
        } else if (regState.equals(RegistrationState.Ok.toString())) {
            sRegistrationCallback.registrationOk();
        } else if (regState.equals(RegistrationState.Cleared.toString())) {
            sRegistrationCallback.registrationCleared();
        } else if (regState.equals(RegistrationState.Failed.toString())) {
            sRegistrationCallback.registrationFailed();
        }

    }


    @Override
    public void onCallStateChanged(@androidx.annotation.NonNull Core core,
                                   @androidx.annotation.NonNull Call call,
                                   Call.State state,
                                   @androidx.annotation.NonNull String message) {
        Log.e(TAG, "callState: " + state.toString());
        if (state == Call.State.IncomingReceived && sPhoneCallback != null) {
            sPhoneCallback.incomingCall(call);
        }

        if (state == Call.State.OutgoingInit && sPhoneCallback != null) {
            sPhoneCallback.outgoingInit();
        }

        if (state == Call.State.Connected && sPhoneCallback != null) {
            sPhoneCallback.callConnected();
        }

        if (state == Call.State.Error && sPhoneCallback != null) {
            sPhoneCallback.error();
        }

        if (state == Call.State.End && sPhoneCallback != null) {
            sPhoneCallback.callEnd();
        }

        if (state == Call.State.Released && sPhoneCallback != null) {
            sPhoneCallback.callReleased();
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
    public void onGlobalStateChanged(@androidx.annotation.NonNull Core core, GlobalState state, @androidx.annotation.NonNull String message) {

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
