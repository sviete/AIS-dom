package pl.sviete.dom;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;
import com.google.android.exoplayer2.ui.StyledPlayerView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;


public class AisMediaPlayerActivity extends AppCompatActivity {
    private static final String TAG = "AisMediaPlayerActivity";
    private final CastDescriptionAdapter mCasttDescriptionAdapter  = new CastDescriptionAdapter();
    Handler mMainThreadHandler = new Handler(Looper.getMainLooper());
    public static PlayerNotificationManager mCastPlayerNotificationManager;


    @Override
    protected void onStart() {
        super.onStart();
    }


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.media_player_view);

        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        mMainThreadHandler.post(
                playMedia()
        );

        mMainThreadHandler.postDelayed(new Runnable() {
            public void run() {
                getNewArtwork();
            }
        }, 4000);

    }

    private Runnable playMedia() {
        int ais_cast_notification_id = 8888888;
        String ais_cast_channel = "ais_cast_channel";
        if (AisPanelService.mCastExoPlayer != null) {
            try {
                AisPanelService.mCastExoPlayer.stop();
                mCastPlayerNotificationManager.setPlayer(null);
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

            mCastPlayerNotificationManager = PlayerNotificationManager.createWithNotificationChannel(
                    getApplicationContext(),
                    ais_cast_channel,
                    R.string.playback_channel_name,
                    R.string.cast_channel_descr,
                    ais_cast_notification_id,
                    mCasttDescriptionAdapter
            );
            mCastPlayerNotificationManager.setPlayer(AisPanelService.mCastExoPlayer);
            AisPanelService.mCastExoPlayer.setPlayWhenReady(true);
            mCastPlayerNotificationManager.setUseNextAction(false);
            mCastPlayerNotificationManager.setUsePreviousAction(false);
            mCastPlayerNotificationManager.setUseStopAction(true);
            mCastPlayerNotificationManager.setRewindIncrementMs(0);
            mCastPlayerNotificationManager.setFastForwardIncrementMs(0);
            mCastPlayerNotificationManager.setSmallIcon(R.drawable.ais_icon_cast);
            Drawable drawable = getDrawable(R.drawable.ic_ais_logo);
            playerView.setDefaultArtwork(drawable);
        } catch (Exception e) {
            Log.e(TAG, "Error playCastMedia: " + e.getMessage());
        }
        return null;
    }

    private Runnable getNewArtwork() {
        if (AisPanelService.m_cast_media_stream_image == null){
            return null;
        }
        new RetrieveArtworkTask().execute();
        return null;
    }

    class RetrieveArtworkTask extends AsyncTask<String, Void, Drawable> {

        protected Drawable doInBackground(String... urls) {
            try {
                Bitmap x;
                HttpURLConnection connection = (HttpURLConnection) new URL(AisPanelService.m_cast_media_stream_image).openConnection();
                connection.connect();
                InputStream input = connection.getInputStream();
                x = BitmapFactory.decodeStream(input);
                Drawable drawable_img = new BitmapDrawable(Resources.getSystem(), x);
                return drawable_img;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return  null;
        }
        protected void onPostExecute (Drawable drawable_img){
            if (drawable_img != null) {
                StyledPlayerView playerView = findViewById(R.id.player_view);
                playerView.setDefaultArtwork(drawable_img);
            }
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
            if (AisPanelService.m_cast_media_stream_image != null) {
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
            }

            return null;
        }

    }


}

