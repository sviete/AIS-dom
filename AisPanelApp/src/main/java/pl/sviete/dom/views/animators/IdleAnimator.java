package pl.sviete.dom.views.animators;

/**
 * Created by andrzej on 29.01.18.
 */

import pl.sviete.dom.views.RecognitionBar;

import java.util.List;

public class IdleAnimator implements pl.sviete.dom.views.animators.BarParamsAnimator {

    private boolean isPlaying;

    private final int floatingAmplitude;
    private final List<RecognitionBar> bars;

    public IdleAnimator(List<RecognitionBar> bars, int floatingAmplitude) {
        this.floatingAmplitude = floatingAmplitude;
        this.bars = bars;
    }

    @Override
    public void start() {
        isPlaying = true;
    }

    @Override
    public void stop() {
        isPlaying = false;
    }

    @Override
    public void animate() {
        // no animation on idle
    }

}
