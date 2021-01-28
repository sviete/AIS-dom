package pl.sviete.dom;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;
import java.util.ArrayList;


public class AisCamActivity extends AppCompatActivity  {
        private static final boolean USE_TEXTURE_VIEW = false;
        private static final boolean ENABLE_SUBTITLES = false;
        private VLCVideoLayout mVideoLayout = null;

        private LibVLC mLibVLC = null;
        private MediaPlayer mMediaPlayer = null;

        public String mUrl = null;
        public String mHaCamId = null;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_ais_cam);

            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

            final ArrayList<String> args = new ArrayList<>();
            mLibVLC = new LibVLC(this, args);
            mMediaPlayer = new MediaPlayer(mLibVLC);

            mVideoLayout = findViewById(R.id.video_layout);

            // exit
            Button exitCamButton = (Button) findViewById(R.id.cam_activity_exit);
            exitCamButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });

            // picture
            Button screenshotCamButton = (Button) findViewById(R.id.cam_activity_screenshot);
            screenshotCamButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    screenshotCamButton();
                }
            });

            // open
            Button openGateCamButton = (Button) findViewById(R.id.cam_activity_open_gate);
            openGateCamButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openGateCamButton();
                }
            });
        }

    private void openGateCamButton() {
            if (mHaCamId != null) {
                try {
                    // send camera button event
                    JSONObject jMessage = new JSONObject();
                    jMessage.put("event_type", "ais_cam_button_pressed");
                    JSONObject jData = new JSONObject();
                    jData.put("button", "open");
                    jData.put("camera_entity_id", mHaCamId);
                    jMessage.put("event_data", jData);
                    DomWebInterface.publishJson(jMessage, "event", getApplicationContext());
                } catch (Exception e) {
                    Log.e("Exception", e.toString());
                }
            }
            Toast.makeText(getBaseContext(),"Otwieram.", Toast.LENGTH_SHORT).show();
    }

    private void screenshotCamButton() {
        try {
            // send camera button event
            JSONObject jMessage = new JSONObject();
            jMessage.put("event_type", "ais_cam_button_pressed");
            JSONObject jData = new JSONObject();
            jData.put("button", "picture");
            jData.put("camera_entity_id", mHaCamId);
            jMessage.put("event_data", jData);
            DomWebInterface.publishJson(jMessage, "event", getApplicationContext());
        } catch (Exception e) {
            Log.e("Exception", e.toString());
        }
        Toast.makeText(getBaseContext(),"Zdjęcie z kamery.", Toast.LENGTH_SHORT).show();
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

            Intent intent = getIntent();
            if (intent.hasExtra(AisCoreUtils.BROADCAST_CAMERA_COMMAND_URL)) {
                mUrl = intent.getStringExtra(AisCoreUtils.BROADCAST_CAMERA_COMMAND_URL);
            }
            if  (intent.hasExtra(AisCoreUtils.BROADCAST_CAMERA_HA_ID)) {
                mHaCamId = intent.getStringExtra(AisCoreUtils.BROADCAST_CAMERA_HA_ID);
            }

            Toast.makeText(getBaseContext(), "CAM url: " + mUrl, Toast.LENGTH_SHORT).show();

            mMediaPlayer.attachViews(mVideoLayout, null, ENABLE_SUBTITLES, USE_TEXTURE_VIEW);
            try {
                final Media media = new Media(mLibVLC, Uri.parse(mUrl));
                mMediaPlayer.setMedia(media);
                media.release();
            } catch (Exception e) {
                Toast.makeText(getBaseContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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