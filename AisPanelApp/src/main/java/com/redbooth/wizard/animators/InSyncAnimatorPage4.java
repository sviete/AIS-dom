/*
 * Copyright Txus Ballesteros 2016 (@txusballesteros)
 *
 * This file is part of some open source application.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 * Contact: Txus Ballesteros <txus.ballesteros@gmail.com>
 */
package com.redbooth.wizard.animators;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

import pl.sviete.dom.R;

public class InSyncAnimatorPage4 {
    private AnimatorSet animator;
    private final View rootView;

    public InSyncAnimatorPage4(View rootView) {
        this.rootView = rootView;
        initializeAnimator();
    }

    private void initializeAnimator() {
        final View avatarView = rootView.findViewById(R.id.avatar5);
        final View arrowChartMaskView = rootView.findViewById(R.id.arrow_chart_mask);
        final ObjectAnimator scaleXAnimator = ObjectAnimator
                .ofFloat(avatarView, View.SCALE_X, 0f, 1f);
        scaleXAnimator.setDuration(300);
        scaleXAnimator.setInterpolator(new OvershootInterpolator());
        final ObjectAnimator scaleYAnimator = ObjectAnimator
                .ofFloat(avatarView, View.SCALE_Y, 0f, 1f);
        scaleYAnimator.setDuration(300);
        scaleYAnimator.setInterpolator(new OvershootInterpolator());
        final ObjectAnimator maskScaleXAnimator = ObjectAnimator
                .ofFloat(arrowChartMaskView, View.SCALE_X, 1f, 0f);
        maskScaleXAnimator.setDuration(500);
        maskScaleXAnimator.setInterpolator(new LinearInterpolator());

        final View aisLogoView = rootView.findViewById(R.id.avatar_ais_logo_page4);
        Animator aisLogoAnimator = getAnimator(aisLogoView);

        animator = new AnimatorSet();
        animator.play(aisLogoAnimator).after(maskScaleXAnimator);
        animator.play(scaleXAnimator).with(scaleYAnimator).before(maskScaleXAnimator);
    }

    public void play() {
        animator.start();
    }


    private Animator getAnimator(View targetView) {
        AnimatorSet animator = new AnimatorSet();
        animator.setDuration(300);
        animator.setInterpolator(new OvershootInterpolator());
        ObjectAnimator scaleXAnimator = ObjectAnimator.ofFloat(targetView, View.SCALE_X, 1f);
        ObjectAnimator scaleYAnimator = ObjectAnimator.ofFloat(targetView, View.SCALE_Y, 1f);
        animator.playTogether(scaleXAnimator, scaleYAnimator);
        return animator;
    }
}
