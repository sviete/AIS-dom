package com.redbooth.wizard;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.redbooth.WelcomeCoordinatorLayout;
import com.redbooth.wizard.animators.ChatAvatarsAnimator;
import com.redbooth.wizard.animators.InSyncAnimator;
import com.redbooth.wizard.animators.InSyncAnimatorPage4;
import com.redbooth.wizard.animators.RocketAvatarsAnimator;
import com.redbooth.wizard.animators.RocketFlightAwayAnimator;

import pl.sviete.dom.BrowserActivityNative;
import pl.sviete.dom.Config;
import pl.sviete.dom.R;

import static pl.sviete.dom.ScannerActivity.BACK_TO_WIZARD;


public class MainWizardActivity extends AppCompatActivity {
    private boolean animationReady = false;
    private ValueAnimator backgroundAnimator;
    private RocketAvatarsAnimator rocketAvatarsAnimator;
    private ChatAvatarsAnimator chatAvatarsAnimator;
    private RocketFlightAwayAnimator rocketFlightAwayAnimator;
    private InSyncAnimator inSyncAnimator;
    private InSyncAnimatorPage4 inSyncAnimatorPage4;
    private WelcomeCoordinatorLayout coordinatorLayout;

    private final int REQUEST_RECORD_PERMISSION = 100;
    private final int REQUEST_CAMERA_PERMISSION = 110;
    private final String TAG = "MainWizardActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wizard_main);
        coordinatorLayout = (WelcomeCoordinatorLayout)findViewById(R.id.coordinator);
        initializeListeners();
        initializePages();
        initializeBackgroundTransitions();
        initializeButtons();
    }

    private void initializePages() {
        coordinatorLayout.addPage(
                R.layout.welcome_page_1,
                R.layout.welcome_page_2,
                R.layout.welcome_page_3,
                R.layout.welcome_page_4,
                R.layout.welcome_page_5);
    }

    private void initializeListeners() {
        coordinatorLayout.setOnPageScrollListener(new WelcomeCoordinatorLayout.OnPageScrollListener() {
            @Override
            public void onScrollPage(View v, float progress, float maximum) {
                if (!animationReady) {
                    animationReady = true;
                    backgroundAnimator.setDuration((long) maximum);
                }
                backgroundAnimator.setCurrentPlayTime((long) progress);
            }

            @Override
            public void onPageSelected(View v, int pageSelected) {
                Log.d(TAG, "onPageSelected " + pageSelected);
                switch (pageSelected) {
                    case 0:
                        if (rocketAvatarsAnimator == null) {
                            rocketAvatarsAnimator = new RocketAvatarsAnimator(coordinatorLayout);
                            rocketAvatarsAnimator.play();
                        }
                        break;
                    case 1:
                        if (chatAvatarsAnimator == null) {
                            chatAvatarsAnimator = new ChatAvatarsAnimator(coordinatorLayout);
                            chatAvatarsAnimator.play();
                        }
                        // check and set the mic access
                        checkMicAccess();
                        break;
                    case 2:
                        if (inSyncAnimator == null) {
                            inSyncAnimator = new InSyncAnimator(coordinatorLayout);
                            inSyncAnimator.play();
                        }
                        // check and set camera access
                        checkCamAccess();
                        break;
                    case 3:
                        if (inSyncAnimatorPage4 == null) {
                            inSyncAnimatorPage4 = new InSyncAnimatorPage4(coordinatorLayout);
                            inSyncAnimatorPage4.play();
                        }
                        // check if gate was scanned
                        checkGateExists();
                        break;
                    case 4:
                        if (rocketFlightAwayAnimator == null) {
                            rocketFlightAwayAnimator = new RocketFlightAwayAnimator(coordinatorLayout);
                            rocketFlightAwayAnimator.play();
                        }
                        break;
                }
            }
        });
    }

    private void initializeBackgroundTransitions() {
        final Resources resources = getResources();
        final int colorPage1 = ResourcesCompat.getColor(resources, R.color.page1, getTheme());
        final int colorPage2 = ResourcesCompat.getColor(resources, R.color.page2, getTheme());
        final int colorPage3 = ResourcesCompat.getColor(resources, R.color.page2, getTheme());
        final int colorPage4 = ResourcesCompat.getColor(resources, R.color.page3, getTheme());
        final int colorPage5 = ResourcesCompat.getColor(resources, R.color.page3, getTheme());
        backgroundAnimator = ValueAnimator
                .ofObject(new ArgbEvaluator(), colorPage1, colorPage2, colorPage3, colorPage4, colorPage5);

        backgroundAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                coordinatorLayout.setBackgroundColor((int) animation.getAnimatedValue());
            }
        }

        );
    }


    private void initializeButtons() {
        Button bSkip = (Button) findViewById(R.id.skip);
        bSkip.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int page = coordinatorLayout.getPageSelected();
                if (page == 4) {
                    startActivity(new Intent(getApplicationContext(), BrowserActivityNative.class));
                } else {
                    coordinatorLayout.setCurrentPage(page + 1, true);
                }
            }
        });


        ImageView bAisNext1 = (ImageView) findViewById(R.id.avatar_ais_logo_page1);
        bAisNext1.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                coordinatorLayout.setCurrentPage(1, true);
             }
        });

        ImageView bAisNext3 = (ImageView) findViewById(R.id.avatar_ais_logo_page3);
        bAisNext3.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                coordinatorLayout.setCurrentPage(3, true);
            }
        });

        ImageView bAisNext4 = (ImageView) findViewById(R.id.avatar_ais_logo_page4);
        bAisNext4.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                coordinatorLayout.setCurrentPage(4, true);
            }
        });

        ImageView bAisNext5 = (ImageView) findViewById(R.id.avatar_ais_logo_page5);
        bAisNext5.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(getApplicationContext(), BrowserActivityNative.class));
            }
        });


    }


    // MIC ON PAGE 2 //
    private void checkMicAccess(){
        if (isMicOn()){
            setMicIsON();

        } else {
            setMicIsOFF();
        }
    }

    private boolean isMicOn(){
        int permissionMic = ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
        if (permissionMic != PackageManager.PERMISSION_GRANTED) {
            return false;
        } else {
            return true;
        }
    }

    private void setMicIsON(){
        ImageView infoButton = findViewById(R.id.avatar_ais_logo_page2);
        TextView infoText = findViewById(R.id.wizard_info_page2);
        infoButton.setImageResource(R.drawable.wizzard_mic_on);
        infoText.setText(getString(R.string.wizzard_mic_on_info));
        infoButton.setOnClickListener(v -> coordinatorLayout.setCurrentPage(2, true));
    }

    private void setMicIsOFF(){
        ImageView infoButton = findViewById(R.id.avatar_ais_logo_page2);
        TextView infoText = findViewById(R.id.wizard_info_page2);
        infoButton.setImageResource(R.drawable.wizzard_mic_off);
        infoText.setText(getString(R.string.wizzard_mic_off_info));
        infoButton.setOnClickListener(v -> askForMicOn());
    }

    private void askForMicOn(){
        ActivityCompat.requestPermissions(MainWizardActivity.this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        REQUEST_RECORD_PERMISSION);
    }


    // CAM ON PAGE 3 //


    private void checkCamAccess(){
        int permissionCam = ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA);
        if (permissionCam != PackageManager.PERMISSION_GRANTED) {
            setCamIsOFF();
        } else {
            setCamIsON();
        }
    }

    private void setCamIsON(){
        ImageView infoButton = findViewById(R.id.avatar_ais_logo_page3);
        TextView infoText = findViewById(R.id.wizard_info_page3);
        infoButton.setImageResource(R.drawable.wizzard_cam_on);
        infoText.setText(getString(R.string.wizzard_cam_on_info));
        infoButton.setOnClickListener(v -> coordinatorLayout.setCurrentPage(3, true));
    }

    private void setCamIsOFF(){
        ImageView infoButton = findViewById(R.id.avatar_ais_logo_page3);
        TextView infoText = findViewById(R.id.wizard_info_page3);
        infoButton.setImageResource(R.drawable.wizzard_cam_off);
        infoText.setText(getString(R.string.wizzard_cam_off_info));
        infoButton.setOnClickListener(v -> askForCamOn());
    }

    private void askForCamOn(){
        ActivityCompat.requestPermissions(MainWizardActivity.this,
                new String[]{Manifest.permission.CAMERA},
                REQUEST_CAMERA_PERMISSION);
    }


    // GATE ON PAGE 4 //

    private void checkGateExists(){
        Config config = new Config(this.getApplicationContext());
        String appLaunchUrl = config.getAppLaunchUrl();
        if (appLaunchUrl.startsWith("dom-")){
            setGateIsON(appLaunchUrl);
        } else {
            setGateIsOFF(appLaunchUrl);
        }
    }

    private void setGateIsON(String appLaunchUrl){
        ImageView infoButton = findViewById(R.id.avatar_ais_logo_page4);
        TextView infoText = findViewById(R.id.wizard_info_page4);
        infoButton.setImageResource(R.drawable.wizzard_qr_on);
        infoText.setText(getString(R.string.wizzard_qr_on_info));
        infoButton.setOnClickListener(v -> skanGateId());

        TextView qrText = findViewById(R.id.gate_id_from_qr_code);
        qrText.setText(appLaunchUrl);
    }

    private void setGateIsOFF(String appLaunchUrl){
        ImageView infoButton = findViewById(R.id.avatar_ais_logo_page4);
        TextView infoText = findViewById(R.id.wizard_info_page4);
        infoButton.setImageResource(R.drawable.wizzard_qr_off);
        infoText.setText(getString(R.string.wizzard_qr_off_info));
        infoButton.setOnClickListener(v -> skanGateId());

        TextView qrText = findViewById(R.id.gate_id_from_qr_code);
        qrText.setText(appLaunchUrl);
    }

    private void skanGateId(){
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setComponent(new ComponentName("pl.sviete.dom","pl.sviete.dom.ScannerActivity"));
        intent.putExtra(BACK_TO_WIZARD, true);
        startActivity(intent);
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.i(TAG, "onRequestPermissionsResult");
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_RECORD_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    setMicIsON();
                } else {
                    setMicIsOFF();
                }
                break;
            case REQUEST_CAMERA_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    setCamIsON();
                } else {
                    setCamIsOFF();
                }
        }
    }


}
