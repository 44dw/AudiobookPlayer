package com.a44dw.audiobookplayer;


import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Observer;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

public class BookScaleFragment extends Fragment implements View.OnClickListener{

    LiveData<ArrayList<Chapter>> playlist;
    ArrayList<ConstraintLayout> barList;
    LiveData<Chapter> nowPlayingFile;
    TextView informChapterTitle;
    ProgressbarFabric progressbarFabric;
    ConstraintLayout currentBar;
    ProgressbarBroadReceiver progressbarReceiver;

    public BookScaleFragment() {}

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_bookscale, container, false);
        final LinearLayout barLayout = view.findViewById(R.id.progressBarLayout);

        informChapterTitle = view.findViewById(R.id.informChapterTitle);

        IntentFilter filter = new IntentFilter(MainActivity.seekBarBroadcastName);
        progressbarReceiver = new ProgressbarBroadReceiver();
        getContext().registerReceiver(progressbarReceiver, filter);

        if(playlist == null) {
            playlist = AudiobookViewModel.getPlaylist();
            playlist.observe(getActivity(), new Observer<ArrayList<Chapter>>() {
                @Override
                public void onChanged(@Nullable ArrayList<Chapter> files) {
                    if(progressbarFabric == null) progressbarFabric = new ProgressbarFabric(getActivity(), getDurationInterval());
                    barList = getChapterScales();
                    int iterator = 1;
                    for(ConstraintLayout bar : barList) {
                        TextView num = bar.findViewById(R.id.progressbarText);
                        Log.d(MainActivity.TAG, "BookScaleFragment -> playlist: onChanged: TextView " + num);
                        num.setText(String.valueOf(iterator++));
                        barLayout.addView(bar);
                    }
                }
            });
        }
        if(nowPlayingFile == null) {
            nowPlayingFile = AudiobookViewModel.getNowPlayingFile();
            nowPlayingFile.observe(getActivity(), new Observer<Chapter>() {
                @Override
                public void onChanged(@Nullable Chapter chapter) {
                    Log.d(MainActivity.TAG, "BookScaleFragment -> nowPlayingFile: onChanged" + chapter);
                    currentBar = getCurrentBar();
                }
            });
        }
        return view;
    }

    private ArrayList<ConstraintLayout> getChapterScales() {
        Log.d(MainActivity.TAG, "BookScaleFragment -> getChapterScales");
        ArrayList<ConstraintLayout> barList = new ArrayList<>();
        for(Chapter c : playlist.getValue()) {
            ConstraintLayout bar = progressbarFabric.getBar(c);
            bar.setOnClickListener(this);
            bar.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Log.d(MainActivity.TAG, "BookScaleFragment -> onLongClick on bar");
                    Chapter ch = (Chapter) v.getTag();
                    informChapterTitle.setText(ch.getTitle());
                    return true;
                }
            });
            barList.add(bar);
        }
        return barList;
    }

    private Long[] getDurationInterval() {
        ArrayList<Long> durationList = new ArrayList<>();

        for(Chapter chapter : playlist.getValue()) {
            durationList.add(chapter.getDuration());
        }
        Log.d(MainActivity.TAG, "BookScaleFragment -> getDurationInterval: " + Collections.min(durationList) + " to " + Collections.max(durationList));

        return new Long[] {Collections.min(durationList), Collections.max(durationList)};
    }

    public ConstraintLayout getCurrentBar() {
        Log.d(MainActivity.TAG, "BookScaleFragment -> getCurrentBar");
        if(currentBar != null) {
            ProgressBar oldBar = currentBar.findViewById(R.id.progressbar);
            Log.d(MainActivity.TAG, "BookScaleFragment -> getCurrentBar: oldBar.getProgress() " + oldBar.getProgress());
            Log.d(MainActivity.TAG, "BookScaleFragment -> getCurrentBar: oldBar.getMax() " + oldBar.getMax());
            if(oldBar.getProgress() == oldBar.getMax()) oldBar.setProgressDrawable(ContextCompat.getDrawable(getContext(), R.drawable.progress_drawable_done));
            else oldBar.setProgressDrawable(ContextCompat.getDrawable(getContext(), R.drawable.progress_drawable));
        }
        ConstraintLayout b = null;
        for(ConstraintLayout bar : barList) {
            File f = ((Chapter)bar.getTag()).getFile();
            if(f.equals(nowPlayingFile.getValue().getFile())) b = bar;
        }
        ProgressBar pbar = b.findViewById(R.id.progressbar);
        pbar.setProgressDrawable(ContextCompat.getDrawable(getContext(), R.drawable.progress_drawable_active));
        return b;
    }

    public void playChapter(Chapter ch) {
        Log.d(MainActivity.TAG, "BookScaleFragment -> playChapter: " + ch.getFile().toString());
        AudiobookViewModel.updateNowPlayingFile(ch);
    }

    @Override
    public void onClick(View v) {
        Log.d(MainActivity.TAG, "BookScaleFragment -> onClick");
        ConstraintLayout bar = (ConstraintLayout) v;
        ProgressBar scale = bar.findViewById(R.id.progressbar);
        Chapter ch = (Chapter) bar.getTag();
        ch.setProgress(scale.getProgress());
        playChapter(ch);
    }

    public class ProgressbarBroadReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int position = intent.getIntExtra(MainActivity.playbackStatus, 0);
            Log.d(MainActivity.TAG, "BookScaleFragment -> onReceive: currentBar is " + currentBar);
            if(currentBar != null) {
                ProgressBar pbar = currentBar.findViewById(R.id.progressbar);
                pbar.setProgress(position);
            }
        }
    }
}
