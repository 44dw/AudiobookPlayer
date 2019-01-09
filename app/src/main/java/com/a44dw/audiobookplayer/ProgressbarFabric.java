package com.a44dw.audiobookplayer;

import android.content.Context;
import android.support.constraint.ConstraintLayout;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

public class ProgressbarFabric {

    private Context context;
    private Long[] durationInterval;
    private long intervalDiffer;
    private float heightMin;
    private float heightDiffer;
    private int width;

    ProgressbarFabric(Context c, int[] metrics) {
        context = c;
        float heightMax = metrics[1];
        heightMin = heightMax - (heightMax /100*25);
        heightDiffer = heightMax - heightMin;
        width = metrics[0];
    }

    public void setInterval(Long[] interval) {
        durationInterval = interval;
        long differ = durationInterval[1] - durationInterval[0];
        intervalDiffer = (differ == 0 ? 1 : differ);
    }

    public ConstraintLayout getBar(Chapter chapter) {
        long duration = chapter.duration;

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        ConstraintLayout bar = (ConstraintLayout) inflater.inflate(R.layout.updated_progressbar, null);

        long durationDiffer = duration - durationInterval[0];
        int percent = Math.round((durationDiffer * 100)/intervalDiffer);

        int height = (int)(heightDiffer*(percent/100.0f) + heightMin);
        if(height < heightMin) height = (int) heightMin;

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                new ViewGroup.LayoutParams(width, height));
        params.setMargins(10, 0, 10, 0);
        bar.setLayoutParams(params);
        UpdatedProgressBar scale = bar.findViewById(R.id.progressbar);
        scale.setMax((int)chapter.duration);
        scale.setProgress((int)chapter.progress);
        if(chapter.done)
            scale.setProgressDrawable(context.getResources().getDrawable(R.drawable.progress_drawable_done));

        return bar;
    }
}
