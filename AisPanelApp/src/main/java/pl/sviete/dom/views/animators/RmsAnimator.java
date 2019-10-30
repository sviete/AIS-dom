package pl.sviete.dom.views.animators;

/**
 * Created by andrzej on 29.01.18.
 */

import pl.sviete.dom.views.RecognitionBar;

import java.util.ArrayList;
import java.util.List;

public class RmsAnimator implements pl.sviete.dom.views.animators.BarParamsAnimator {
    final private List<pl.sviete.dom.views.animators.BarRmsAnimator> barAnimators;

    public RmsAnimator(List<RecognitionBar> recognitionBars) {
        this.barAnimators = new ArrayList<>();
        for (RecognitionBar bar : recognitionBars) {
            barAnimators.add(new pl.sviete.dom.views.animators.BarRmsAnimator(bar));
        }
    }

    @Override
    public void start() {
        for (pl.sviete.dom.views.animators.BarRmsAnimator barAnimator : barAnimators) {
            barAnimator.start();
        }
    }

    @Override
    public void stop() {
        for (pl.sviete.dom.views.animators.BarRmsAnimator barAnimator : barAnimators) {
            barAnimator.stop();
        }
    }

    @Override
    public void animate() {
        for (pl.sviete.dom.views.animators.BarRmsAnimator barAnimator : barAnimators) {
            barAnimator.animate();
        }
    }

    public void onRmsChanged(float rmsDB) {
        for (BarRmsAnimator barAnimator : barAnimators) {
            barAnimator.onRmsChanged(rmsDB);
        }
    }
}