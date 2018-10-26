package com.a44dw.audiobookplayer;


import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Observer;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;

public class BookScaleFragment extends Fragment implements View.OnClickListener,
                                                           View.OnLongClickListener{

    LinearLayout barLayout;
    ArrayList<ConstraintLayout> barList;
    LiveData<Chapter> nowPlayingFile;
    LiveData<String> playlistName;
    static TextView informChapterTitle;
    ProgressbarFabric progressbarFabric;
    ConstraintLayout currentBar;
    ProgressbarBroadReceiver progressbarReceiver;
    Handler handler;

    private static final int TEXT_DISAPPEAR_DELAY = 2000;

    public BookScaleFragment() {}

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_bookscale, container, false);
        barLayout = view.findViewById(R.id.progressBarLayout);

        informChapterTitle = view.findViewById(R.id.informChapterTitle);

        IntentFilter filter = new IntentFilter(MainActivity.seekBarBroadcastName);
        progressbarReceiver = new ProgressbarBroadReceiver();
        getContext().registerReceiver(progressbarReceiver, filter);

        //TODO посомтреть ещё раз, как это работает
        if(nowPlayingFile == null) {
            nowPlayingFile = AudiobookViewModel.getNowPlayingFile();
            nowPlayingFile.observe(getActivity(), new Observer<Chapter>() {
                @Override
                public void onChanged(@Nullable Chapter chapter) {
                    if(barList != null) {
                        Log.d(MainActivity.TAG, "BookScaleFragment -> nowPlayingFile: onChanged " + chapter);
                        currentBar = getCurrentBar();
                    }
                }
            });
        }

        if(playlistName == null) {
            playlistName = AudiobookViewModel.getPlaylistName();
            playlistName.observe(getActivity(), new Observer<String>() {
                @Override
                public void onChanged(@Nullable String s) {
                    Log.d(MainActivity.TAG, "BookScaleFragment -> playlistName: onChanged " + s);
                    drawProgressBars();
                    //после обновления плейлиста получаем currentBar, потому что в onChanged nowPlayingFile
                    //он не сработал
                    currentBar = getCurrentBar();
                }
            });
        }

        handler = new DelHandler(this);

        return view;
    }

    @Override
    public void onDestroy() {
        if(handler != null)
            handler.removeCallbacksAndMessages(null);
        getContext().unregisterReceiver(progressbarReceiver);
        super.onDestroy();
    }

    private void drawProgressBars() {
        if(progressbarFabric == null) {
            progressbarFabric = new ProgressbarFabric(getActivity());
        }
        //очищаем layout, если там что-то было нарисовано
        if(barLayout.getChildCount() > 0) barLayout.removeAllViews();
        progressbarFabric.setInterval(getDurationInterval());
        barList = getChapterScales();
        ArrayList<Integer> chaptersWithBookmarks = getChaptersWithBookmarks();
        int iterator = 1;
        for(ConstraintLayout bar : barList) {
            TextView num = bar.findViewById(R.id.progressbarText);
            num.setText(String.valueOf(iterator++));
            barLayout.addView(bar);
        }
        if(chaptersWithBookmarks.size() > 0) {
            for(int i : chaptersWithBookmarks) {
                ConstraintLayout bar = (ConstraintLayout) barLayout.getChildAt(i);
                ImageView bookmark = bar.findViewById(R.id.progressbarBookmark);
                bookmark.setVisibility(View.VISIBLE);
            }
        }
    }

    private ArrayList<Integer> getChaptersWithBookmarks() {
        ArrayList<Integer> result = new ArrayList<>();
        ArrayList<Chapter> pl = AudiobookViewModel.getPlaylist().getChapters();
        for(int i=0; i<pl.size(); i++) {
            ArrayList<Bookmark> bookmarkList = pl.get(i).getBookmarks();
            if(bookmarkList != null) {
                if(bookmarkList.size() > 0) result.add(i);
            }
        }
        return result;
    }

    private ArrayList<ConstraintLayout> getChapterScales() {
        Log.d(MainActivity.TAG, "BookScaleFragment -> getChapterScales");
        ArrayList<ConstraintLayout> barList = new ArrayList<>();
        ArrayList<Chapter> pl = AudiobookViewModel.getPlaylist().getChapters();
        for(int i=0; i<pl.size(); i++) {
            ConstraintLayout bar = progressbarFabric.getBar(pl.get(i));
            //вешаем в качестве тэга номер в playlist
            bar.setTag(i);
            bar.setOnClickListener(this);
            bar.setOnLongClickListener(this);
            barList.add(bar);
        }
        return barList;
    }

    private Long[] getDurationInterval() {
        ArrayList<Long> durationList = new ArrayList<>();

        ArrayList<Chapter> pl = AudiobookViewModel.getPlaylist().getChapters();
        Log.d(MainActivity.TAG, "BookScaleFragment -> getDurationInterval: playlist length is " + pl.size());

        for(Chapter chapter : pl) {
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
            if(oldBar.getProgress() > oldBar.getMax() - AudiobookViewModel.GAP) oldBar.setProgressDrawable(ContextCompat.getDrawable(getContext(), R.drawable.progress_drawable_done));
            else oldBar.setProgressDrawable(ContextCompat.getDrawable(getContext(), R.drawable.progress_drawable));
        }
        int num = AudiobookViewModel.getNowPlayingFileNumber();
        ConstraintLayout b = barList.get(num);
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
        Chapter ch = AudiobookViewModel.getPlaylist().getChapters().get((int)bar.getTag());
        playChapter(ch);
    }

    @Override
    public boolean onLongClick(View v) {
        Log.d(MainActivity.TAG, "BookScaleFragment -> onLongClick on bar");
        Chapter ch = AudiobookViewModel.getPlaylist().getChapters().get((int)v.getTag());
        informChapterTitle.setText(ch.getChapter());

        handler.removeMessages(0);
        handler.sendEmptyMessageDelayed(0, TEXT_DISAPPEAR_DELAY);

        return true;
    }

    public void onAddBookmark() {
        if(currentBar != null) {
            ImageView bookmark = currentBar.findViewById(R.id.progressbarBookmark);
            if (bookmark.getVisibility() == View.GONE) bookmark.setVisibility(View.VISIBLE);
        }
    }

    public void onDeleteBookmark(Integer chNum) {
        barList.get(chNum).findViewById(R.id.progressbarBookmark).setVisibility(View.GONE);
    }

    public class ProgressbarBroadReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int position = intent.getIntExtra(MainActivity.playbackStatus, 0);
            if(currentBar != null) {
                ProgressBar pbar = currentBar.findViewById(R.id.progressbar);
                pbar.setProgress(position);
            }
        }
    }

    private void clearInformChapterTitle() {
        informChapterTitle.setText("");
        handler.sendEmptyMessageDelayed(0, TEXT_DISAPPEAR_DELAY);
    }

    static class DelHandler extends Handler {
        WeakReference<BookScaleFragment> wrFragment;

        public DelHandler(BookScaleFragment fragment) {
            wrFragment = new WeakReference<>(fragment);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            BookScaleFragment fragment = wrFragment.get();
            if (fragment != null)
                fragment.clearInformChapterTitle();
        }
    }
}
