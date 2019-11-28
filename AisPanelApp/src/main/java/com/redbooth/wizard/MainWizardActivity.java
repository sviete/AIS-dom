package com.redbooth.wizard;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AppCompatActivity;
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
import pl.sviete.dom.R;


public class MainWizardActivity extends AppCompatActivity {
    private boolean animationReady = false;
    private ValueAnimator backgroundAnimator;
    private RocketAvatarsAnimator rocketAvatarsAnimator;
    private ChatAvatarsAnimator chatAvatarsAnimator;
    private RocketFlightAwayAnimator rocketFlightAwayAnimator;
    private InSyncAnimator inSyncAnimator;
    private InSyncAnimatorPage4 inSyncAnimatorPage4;
    private WelcomeCoordinatorLayout coordinatorLayout;

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
                        break;
                    case 2:
                        if (inSyncAnimator == null) {
                            inSyncAnimator = new InSyncAnimator(coordinatorLayout);
                            inSyncAnimator.play();
                        }
                        break;
                    case 3:
                        if (inSyncAnimatorPage4 == null) {
                            inSyncAnimatorPage4 = new InSyncAnimatorPage4(coordinatorLayout);
                            inSyncAnimatorPage4.play();
                        }
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
        final int colorPage3 = ResourcesCompat.getColor(resources, R.color.page3, getTheme());
        final int colorPage4 = ResourcesCompat.getColor(resources, R.color.page4, getTheme());
        final int colorPage5 = ResourcesCompat.getColor(resources, R.color.page4, getTheme());
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
        int currentPage = coordinatorLayout.getPageSelected();
        Button bSkip = (Button) findViewById(R.id.skip);
        bSkip.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                coordinatorLayout.setCurrentPage(currentPage + 1, true);
            }
        });


        ImageView bAisNext1 = (ImageView) findViewById(R.id.avatar_ais_logo_page1);
        bAisNext1.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                coordinatorLayout.setCurrentPage(1, true);
             }
        });

        ImageView bAisNext2 = (ImageView) findViewById(R.id.avatar_ais_logo_page2);
        bAisNext2.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                coordinatorLayout.setCurrentPage(2, true);
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

}
