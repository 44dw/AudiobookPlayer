package com.a44dw.audiobookplayer;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.support.constraint.ConstraintLayout;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import java.io.File;
import java.util.Map;

public class ProgressbarFabric {

    private Context context;
    private Long[] durationInterval;
    private long intervalDiffer;
    private float heightMin;
    private float heightMax;
    private float heightDiffer;

    public ProgressbarFabric(Context c, Long[] interval) {
        Log.d(MainActivity.TAG, "ProgressbarFabric -> constructor");
        context = c;
        heightMin = getPixFromDp(70);
        heightMax = getPixFromDp(100);
        heightDiffer = heightMax - heightMin;
        durationInterval = interval;
        intervalDiffer = durationInterval[1] - durationInterval[0];
        Log.d(MainActivity.TAG, "ProgressbarFabric -> constructor -> durationDiffer: " + intervalDiffer);
    }

    public ConstraintLayout getBar(Chapter chapter) {
        long duration = getFileDuration(chapter.getFile());
        Log.d(MainActivity.TAG, "ProgressbarFabric -> getBar -> getFileDuration: " + duration);

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        ConstraintLayout bar = (ConstraintLayout) inflater.inflate(R.layout.updated_progressbar, null, false);

        long durationDiffer = duration - durationInterval[0];
        int percent = Math.round((durationDiffer * 100)/intervalDiffer);
        Log.d(MainActivity.TAG, "ProgressbarFabric -> getBar -> percent: " + percent);

        int height = (int)(heightDiffer*(percent/100.0f) + heightMin);
        Log.d(MainActivity.TAG, "ProgressbarFabric -> getBar -> height: " + height);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                new ViewGroup.LayoutParams(60, height));
        params.setMargins(10, 0, 10, 0);
        bar.setLayoutParams(params);
        ProgressBar scale = bar.findViewById(R.id.progressbar);
        scale.setMax((int)chapter.getDuration());

        bar.setTag(chapter);

        return bar;
    }

    public static Long getFileDuration(File media) {
        MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
        metadataRetriever.setDataSource(media.toString());
        return Long.parseLong(metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
    }

    private float getPixFromDp(float dp) {
        Resources r = context.getResources();
        float pix = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                r.getDisplayMetrics()
        );
        Log.d(MainActivity.TAG, "ProgressbarFabric -> getPixFromDp: px = " + pix);
        return pix;
    }
}
