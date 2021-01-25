package pl.sviete.dom;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;
import java.util.ArrayList;

import static pl.sviete.dom.AisPanelService.mCamStreamUrl;


public class AisCamActivity extends AppCompatActivity  {
        private static final boolean USE_TEXTURE_VIEW = false;
        private static final boolean ENABLE_SUBTITLES = false;
        private VLCVideoLayout mVideoLayout = null;

        private LibVLC mLibVLC = null;
        private MediaPlayer mMediaPlayer = null;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            setContentView(R.layout.activity_ais_cam);

            final ArrayList<String> args = new ArrayList<>();
            args.add("-vvv");
            mLibVLC = new LibVLC(this, args);
            mMediaPlayer = new MediaPlayer(mLibVLC);

            mVideoLayout = findViewById(R.id.video_layout);
        }

        @Override
        protected void onDestroy() {
            super.onDestroy();
            mMediaPlayer.release();
            mLibVLC.release();
        }

        @Override
        protected void onStart() {
            super.onStart();
            mMediaPlayer.attachViews(mVideoLayout, null, ENABLE_SUBTITLES, USE_TEXTURE_VIEW);
            try {
                final Media media = new Media(mLibVLC, Uri.parse(mCamStreamUrl));
                mMediaPlayer.setMedia(media);
                media.release();
            } catch (Exception e) {
                throw new RuntimeException("Invalid asset folder");
            }
            mMediaPlayer.play();
        }

        @Override
        protected void onStop() {
            super.onStop();

            mMediaPlayer.stop();
            mMediaPlayer.detachViews();
        }
    }