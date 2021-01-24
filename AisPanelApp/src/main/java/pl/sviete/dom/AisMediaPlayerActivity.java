package pl.sviete.dom;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;
import com.google.android.exoplayer2.ui.StyledPlayerView;

import java.net.URL;


public class AisMediaPlayerActivity extends AppCompatActivity {
    private static final String TAG = "AisMediaPlayerActivity";
    private final CastDescriptionAdapter mCasttDescriptionAdapter  = new CastDescriptionAdapter();


    @Override
    protected void onStart() {
        super.onStart();
    }


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String ais_cast_channel = "ais_cast_channel";
        setContentView(R.layout.media_player_view);

        //
        int ais_cast_notification_id = 8888888;
        if (AisPanelService.mCastExoPlayer != null) {
            try {
                AisPanelService.mCastExoPlayer.stop();
                AisPanelService.mCastPlayerNotificationManager.setPlayer(null);
                AisPanelService.mCastExoPlayer.release();
                AisPanelService.mCastExoPlayer = null;
            } catch (Exception e) {
                Log.e(TAG, "Error stop playCastMedia: " + e.getMessage());
            }
        }
        try {
            AisPanelService.mCastExoPlayer = new SimpleExoPlayer.Builder(getApplicationContext()).build();
            //
            StyledPlayerView playerView = findViewById(R.id.player_view);
            playerView.setPlayer(AisPanelService.mCastExoPlayer);
            //
            MediaItem mediaItem = MediaItem.fromUri(AisPanelService.mCastStreamUrl);
            AisPanelService.mCastExoPlayer.setMediaItem(mediaItem);
            AisPanelService.mCastExoPlayer.prepare();
            AisPanelService. mCastPlayerNotificationManager = PlayerNotificationManager.createWithNotificationChannel(
                    getApplicationContext(),
                    ais_cast_channel,
                    R.string.playback_channel_name,
                    R.string.cast_channel_descr,
                    ais_cast_notification_id,
                    mCasttDescriptionAdapter
            );

            AisPanelService.mCastPlayerNotificationManager.setPlayer(AisPanelService.mCastExoPlayer);
            AisPanelService.mCastExoPlayer.setPlayWhenReady(true);
            AisPanelService.mCastPlayerNotificationManager.setUseNextAction(false);
            AisPanelService.mCastPlayerNotificationManager.setUsePreviousAction(false);
            AisPanelService.mCastPlayerNotificationManager.setUseStopAction(true);
            AisPanelService.mCastPlayerNotificationManager.setRewindIncrementMs(0);
            AisPanelService.mCastPlayerNotificationManager.setFastForwardIncrementMs(0);
            AisPanelService. mCastPlayerNotificationManager.setSmallIcon(R.drawable.ais_icon_cast);

            //
//            Thread thread = new Thread(() -> {
//                try {
//                    URL url = new URL(AisPanelService.m_cast_media_stream_image);
//                    Bitmap bitmap = BitmapFactory.decodeStream(url.openConnection().getInputStream());
//                    playerView.setBackground();
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            });
//            thread.start();


        } catch (Exception e) {
            Log.e(TAG, "Error playCastMedia: " + e.getMessage());
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
    }
    @Override
    protected void onResume() {
        super.onResume();
    }
    @Override
    protected void onPause() {
        super.onPause();
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }


    // cast notification
    private class CastDescriptionAdapter implements
            PlayerNotificationManager.MediaDescriptionAdapter {

        @Override
        public String getCurrentContentTitle(Player player) {
            return AisPanelService.m_cast_media_title;
        }

        @Nullable
        @Override
        public PendingIntent createCurrentContentIntent(Player player) {
            // Go to video player app by clicking on notification
            Intent goToAppView = new Intent(AisMediaPlayerActivity.this, AisMediaPlayerActivity.class);
            int iUniqueId = (int) (System.currentTimeMillis() & 0xfffffff);
            goToAppView.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent contentPendingIntent = PendingIntent.getActivity(AisMediaPlayerActivity.this, iUniqueId, goToAppView,PendingIntent.FLAG_UPDATE_CURRENT);

            return contentPendingIntent;
        }

        @Nullable
        @Override
        public String getCurrentContentText(Player player) {
            int window = player.getCurrentWindowIndex();
            return "Cast " +  AisPanelService.m_cast_media_source;
        }

        @Nullable
        @Override
        public CharSequence getCurrentSubText(Player player) {
            return null;
        }

        @Nullable
        @Override
        public Bitmap getCurrentLargeIcon(Player player,
                                          PlayerNotificationManager.BitmapCallback callback) {

            Thread thread = new Thread(() -> {
                try {
                    URL url = new URL(AisPanelService.m_cast_media_stream_image);
                    Bitmap bitmap = BitmapFactory.decodeStream(url.openConnection().getInputStream());
                    callback.onBitmap(bitmap);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            thread.start();

            return null;
        }

    }


}

