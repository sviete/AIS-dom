package pl.sviete.dom;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Locale;
import java.util.Map;

import ai.picovoice.hotword.PorcupineService;

import static pl.sviete.dom.AisCoreUtils.GO_TO_HA_APP_VIEW_INTENT_EXTRA;
import static pl.sviete.dom.AisCoreUtils.isServiceRunning;

/**
 * NOTE: There can only be one service in each app that receives FCM messages. If multiple
 * are declared in the Manifest then the first one will be chosen.
 *
 * In order to make this Java sample functional, you must remove the following from the Kotlin messaging
 * service in the AndroidManifest.xml:
 *
 * <intent-filter>
 *   <action android:name="com.google.firebase.MESSAGING_EVENT" />
 * </intent-filter>
 */
public class MyFirebaseMessagingService extends FirebaseMessagingService implements TextToSpeech.OnInitListener {

    private static final String TAG = "MyFirebaseMsgService";
    private TextToSpeech mTts;
    private String textToSpeak;
    private Handler mHandler;
    private Context mContext;

    @Override
    public void onCreate(){
        super.onCreate();
        mHandler = new Handler(); // this is attached to the main thread and the main looper
        mContext = this.getApplicationContext();
    }

    /**
     * Called when message is received.
     *
     * @param remoteMessage Object representing the message received from Firebase Cloud Messaging.
     */
    // [START receive_message]
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        // [START_EXCLUDE]
        // There are two types of messages data messages and notification messages. Data messages
        // are handled
        // here in onMessageReceived whether the app is in the foreground or background. Data
        // messages are the type
        // traditionally used with GCM. Notification messages are only received here in
        // onMessageReceived when the app
        // is in the foreground. When the app is in the background an automatically generated
        // notification is displayed.
        // When the user taps on the notification they are returned to the app. Messages
        // containing both notification
        // and data payloads are treated as notification messages. The Firebase console always
        // sends notification
        // messages. For more see: https://firebase.google.com/docs/cloud-messaging/concept-options
        // [END_EXCLUDE]

        // Handle FCM messages here.
        // Not getting messages here? See why this may be: https://goo.gl/39bRNJ

        // We only accept data payload - to have one way of handling messages
        Log.d(TAG, "Message ");
        Map<String, String> data = remoteMessage.getData();
        if (data.size() > 0) {
            // Handle message within 10 seconds
            handleMessageNow(remoteMessage);
        }
    }
    // [END receive_message]


    // [START on_new_token]

    /**
     * Called if InstanceID token is updated. This may occur if the security of
     * the previous token had been compromised. Note that this is called when the InstanceID token
     * is initially generated so this is where you would retrieve the token.
     */
    @Override
    public void onNewToken(String token) {
        Log.d(TAG, "Refreshed token: " + token);
        AisCoreUtils.AIS_PUSH_NOTIFICATION_KEY = token;
        DomWebInterface.updateRegistrationPushToken(getApplicationContext());
    }
    // [END on_new_token]

    /**
     * Handle time allotted to BroadcastReceivers.
     */
    private void handleMessageNow(RemoteMessage remoteMessage) {
        Log.d(TAG, "Handle message .");
        this.textToSpeak = "";
        Map<String, String> data = remoteMessage.getData();
        String say = "";
        if (data.size() > 0) {
            Log.d(TAG, "Message data payload: " + data);
            String body = data.get("body");

            // Notification
            if (body != null) {
                say = data.get("say");
                String title = data.get("title");
                // Say this
                if (say != null && say.equals("true")) {
                    // try to say
                    processTTS(title + " " + body);
                }

                String clickAction = data.get("click_action");
                sendNotification(title, body, data.get("image"), data.get("notification_id"), clickAction);
            }

            //
            String request = data.get("request");
            if (request != null) {
                sendRequest(request, data);
            }
        }
    }

    private void brodcastToPanelService(String request, String extraUrl){
        // 1. check if service is runing
        if (!isServiceRunning(this.getApplicationContext(), AisPanelService.class)) {
            // service is NOT running - start it!
            Intent serviceIntent = new Intent(this.getApplicationContext(), AisPanelService.class);
            serviceIntent.putExtra("aisRequest", request);
            this.getApplicationContext().startService(serviceIntent);
            // 2. execute after 2 seconds
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Intent requestIntent = new Intent(AisPanelService.BROADCAST_ON_AIS_REQUEST);
                    requestIntent.putExtra("aisRequest", request);
                    requestIntent.putExtra("url", extraUrl);
                    LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
                    bm.sendBroadcast(requestIntent);
                }
            }, 2000);
        } else {
            Intent requestIntent = new Intent(AisPanelService.BROADCAST_ON_AIS_REQUEST);
            requestIntent.putExtra("aisRequest", request);
            requestIntent.putExtra("url", extraUrl);
            LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
            bm.sendBroadcast(requestIntent);
        }
    }

    /**
     * Turn on microphone etc
     *
     */
    private void sendRequest(String request, Map<String, String> data) {
        Config config = new Config(getApplicationContext());

        if (request.equals("micOn")) {
            brodcastToPanelService("micOn", null);
        } else if (request.equals("locationServiceOn")) {
            Intent reportLocationServiceIntent = new Intent(this.getApplicationContext(), AisFuseLocationService.class);
            this.getApplicationContext().startService(reportLocationServiceIntent);
        } else if (request.equals("locationServiceOff")) {
            Intent reportLocationServiceIntent = new Intent(this.getApplicationContext(), AisFuseLocationService.class);
            this.getApplicationContext().stopService(reportLocationServiceIntent);
        } else if (request.equals("locationUpdate")) {
            boolean reDisable = true;
            if (config.getReportLocationMode()) {
                reDisable = false;
            }
            Intent reportLocationServiceIntent = new Intent(this.getApplicationContext(), AisFuseLocationService.class);
            this.getApplicationContext().startService(reportLocationServiceIntent);
            if (reDisable){
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mContext.stopService(reportLocationServiceIntent);
                    }
                }, 30000);
            }
        } else if (request.equals("hotWordServiceOn")) {
            Intent porcupineServiceIntent = new Intent(this.getApplicationContext(), PorcupineService.class);
            this.getApplicationContext().startService(porcupineServiceIntent);
        } else if (request.equals("hotWordServiceOff")) {
            Intent porcupineServiceIntent = new Intent(this.getApplicationContext(), PorcupineService.class);
            this.getApplicationContext().stopService(porcupineServiceIntent);
        } else if (request.equals("audioServiceOn")) {
            Intent serviceIntent = new Intent(this.getApplicationContext(), AisPanelService.class);
            this.getApplicationContext().startService(serviceIntent);
        } else if (request.equals("audioServiceOff")) {
            Intent serviceIntent = new Intent(this.getApplicationContext(), AisPanelService.class);
            this.getApplicationContext().stopService(serviceIntent);
        } else if (request.equals("sayIt")) {
            // try to say
            if (data.size() > 0) {
                String text = data.get("text");
                if (text != null) {
                    processTTS(text);
                }
            }
        } else if (request.equals("playAudio")) {
            // try to play
            if (data.size() > 0) {
                String url = data.get("url");
                if (url != null) {
                    brodcastToPanelService("playAudio", url);
                }
            }
        } else if (request.equals("stopAudio")) {
            // try to stop audio
            brodcastToPanelService("stopAudio", null);
        } else if (request.equals("findPhone")) {
            // try to say
            processTTS("Hej. Tu jestem!");
            // try to play
            brodcastToPanelService("findPhone", null);
        }
    }


    /**
     * Create and show a notification containing the received FCM message.
     *
     */
    private void sendNotification(String title, String body, String imageUrl, String notification_id, String clickAction) {
        // Go to frame
        Intent goToAppView = new Intent(getApplicationContext(), BrowserActivityNative.class);
        int iUniqueId = (int) (System.currentTimeMillis() & 0xfffffff);
        if (clickAction != null && clickAction != "") {
            final URI actionLink;
            boolean isAbsoluteUri = false;
            try {
                actionLink = new URI(clickAction);
                isAbsoluteUri = actionLink.isAbsolute();
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
            if (isAbsoluteUri) {
                // open in broser
                goToAppView = new Intent(Intent.ACTION_VIEW);
                goToAppView.setData(Uri.parse(clickAction));
            } else {
                // go to view in app
                if (!clickAction.startsWith("/")) {
                    clickAction = "/" + clickAction;
                }
                goToAppView.putExtra(GO_TO_HA_APP_VIEW_INTENT_EXTRA, clickAction);
            }
        }
        goToAppView.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), iUniqueId, goToAppView, PendingIntent.FLAG_UPDATE_CURRENT);

        String channelId = getString(R.string.default_notification_channel_id);
        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(R.drawable.ic_ais_logo)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setAutoCancel(true)
                        .setSound(defaultSoundUri)
                        .setContentIntent(pendingIntent)
                        .setColor(getApplicationContext().getResources().getColor(R.color.color1));
        if (imageUrl != null) {
            Bitmap bitmap = getBitmapfromUrl(imageUrl);
            notificationBuilder.setStyle(new NotificationCompat.BigPictureStyle().bigPicture(bitmap));
            notificationBuilder.setLargeIcon(bitmap);
        }
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Since android Oreo notification channel is needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId,
                    "AIS Notification Channel",
                    NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }
        int l_notification_id = 0;
        try {
            l_notification_id = Integer.parseInt(notification_id);
        } catch (Exception e) {
            e.printStackTrace();
        }
        notificationManager.notify(l_notification_id, notificationBuilder.build());
    }

    /*
     *To get a Bitmap image from the URL received
     * */
    public Bitmap getBitmapfromUrl(String imageUrl) {
        try {
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            return bitmap;

        } catch (Exception e) {
            e.printStackTrace();
            return null;

        }
    }

    private void processTTS(String text) {
        Log.d(TAG, "processTTS Called: " + text);
        this.textToSpeak = text;

        if (mTts != null) {
            mTts.shutdown();
            mTts.stop();
            mTts = null;
        }

        if (mTts == null) {
            // currently can\'t change Locale until speech ends
            try {
                // Initialize text-to-speech. This is an asynchronous operation.
                // The OnInitListener (second argument) is called after
                // initialization completes.
                mTts = new TextToSpeech(getApplicationContext(), this);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onInit(int initStatus) {
        // fix - java.lang.NullPointerException
        try {
            if (initStatus == TextToSpeech.SUCCESS) {
                mTts.setSpeechRate(1.0f);
                Config mConfig = new Config(getApplicationContext());
                String ttsVoice = mConfig.getAppTtsVoice();
                Voice voiceobj = new Voice(
                        ttsVoice, new Locale("pl_PL"),
                        Voice.QUALITY_HIGH,
                        Voice.LATENCY_NORMAL,
                        false,
                        null);
                mTts.setVoice(voiceobj);
                if(this.textToSpeak.length() >= 4000) {
                    this.textToSpeak = this.textToSpeak.substring(0, 3999);
                }
                mTts.speak(this.textToSpeak, TextToSpeech.QUEUE_FLUSH, null,"123456");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
