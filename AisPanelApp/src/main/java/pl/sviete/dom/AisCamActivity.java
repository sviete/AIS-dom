package pl.sviete.dom;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.io.File;
import java.io.FileOutputStream;
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
        }

    private void screenshotCamButton() {
            // TODO
        return;
//        View view = (View) findViewById(R.id.video_layout);
//        View screenView = view.getRootView();
//        screenView.setDrawingCacheEnabled(true);
//        Bitmap bitmap = Bitmap.createBitmap(screenView.getDrawingCache());
//        screenView.setDrawingCacheEnabled(false);
//
//        String dirPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Screenshots";
//        File dir = new File(dirPath);
//        if(!dir.exists())
//            dir.mkdirs();
//        File file = new File(dirPath, "test.png");
//        try {
//            FileOutputStream fOut = new FileOutputStream(file);
//            bitmap.compress(Bitmap.CompressFormat.PNG, 85, fOut);
//            fOut.flush();
//            fOut.close();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//
//        Uri uri = Uri.fromFile(file);
//        Intent intent = new Intent();
//        intent.setAction(Intent.ACTION_SEND);
//        intent.setType("image/*");
//
//        intent.putExtra(android.content.Intent.EXTRA_SUBJECT, "");
//        intent.putExtra(android.content.Intent.EXTRA_TEXT, "");
//        intent.putExtra(Intent.EXTRA_STREAM, uri);
//        try {
//            startActivity(Intent.createChooser(intent, "Share Screenshot"));
//        } catch (ActivityNotFoundException e) {
//            Toast.makeText(getApplicationContext(), "No App Available", Toast.LENGTH_SHORT).show();
//        }
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
                Toast.makeText(getApplicationContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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