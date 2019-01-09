package com.a44dw.audiobookplayer;


import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;

import static com.a44dw.audiobookplayer.MainActivity.exec;

public class BookScaleFragment extends Fragment implements View.OnClickListener,
                                                           View.OnLongClickListener{

    LinearLayout barLayout;
    ProgressBar infiniteBar;
    TextView informChapterTitle;
    HorizontalScrollView barScrollView;
    ConstraintLayout currentBar;

    ArrayList<ConstraintLayout> barList;
    static ArrayList<Integer> chaptersWithBookmarks;
    LiveData<Chapter> currentChapter;
    LiveData<String> currentBookName;
    ProgressbarFabric progressbarFabric;
    ProgressbarBroadReceiver progressbarReceiver;
    OnIterationWithActivityListener mActivityListener;
    Handler bookscaleHandler;

    AudiobookViewModel model;
    BookRepository repository;

    private static final int TEXT_DISAPPEAR_DELAY = 2000;
    private static final int SCROLL_TO_CURRENT_DELAY = 5000;
    private static final int BAR_VERTICAL_HEIGHT_CONSTANT = 14;
    private static final int BAR_HORIZONTAL_HEIGHT_CONSTANT = 24;
    private static final int MESSAGE_BARS_LOADED = 1;
    private static final int MESSAGE_TEXT_DISAPPEAR = 2;
    private static final int MESSAGE_SCROLL_TO_CURRENT = 3;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        repository = BookRepository.getInstance();

        currentChapter = repository.getCurrentChapter();
        currentBookName = repository.getBookName();
    }

    @Override
    public void onAttach(Context c) {
        super.onAttach(c);
        if (c instanceof OnIterationWithActivityListener) {
            mActivityListener = (OnIterationWithActivityListener) c;
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_bookscale, container, false);
        barLayout = view.findViewById(R.id.progressBarLayout);
        infiniteBar = view.findViewById(R.id.infiniteBar);
        barScrollView = view.findViewById(R.id.barScrollView);
        informChapterTitle = view.findViewById(R.id.informChapterTitle);

        bookscaleHandler = new BookscaleHandler(this);
        if(model == null)
            model = ViewModelProviders.of(getActivity()).get(AudiobookViewModel.class);

        barScrollView.getViewTreeObserver().addOnScrollChangedListener(new ViewTreeObserver.OnScrollChangedListener() {
            @Override
            public void onScrollChanged() {
                bookscaleHandler.removeMessages(MESSAGE_SCROLL_TO_CURRENT);
                bookscaleHandler.sendEmptyMessageDelayed(MESSAGE_SCROLL_TO_CURRENT, SCROLL_TO_CURRENT_DELAY);
            }
        });

        currentChapter.observe(this, new Observer<Chapter>() {
            @Override
            public void onChanged(@Nullable Chapter chapter) {
                if((barList != null)&&(chapter != null)) {
                    if(chapter.exists()) {
                        currentBar = getCurrentBar();
                        scrollToCurrent();
                    }
                }
                if(repository.chapterNumStack.size() > 0) repository.chapterNumStack.clear();
            }
        });

        currentBookName.observe(this, new Observer<String>() {
            @Override
            public void onChanged(String s) {
                if(s.length() > 0) drawProgressBars();
                else {
                    barLayout.removeAllViews();
                    currentBar = null;
                    barList = null;
                    if(infiniteBar.getVisibility() == View.GONE) infiniteBar.setVisibility(View.VISIBLE);
                }
            }
        });

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();

        IntentFilter filter = new IntentFilter(MainActivity.SEEK_BAR_BROADCAST_NAME);
        progressbarReceiver = new ProgressbarBroadReceiver();
        getContext().registerReceiver(progressbarReceiver, filter);

        if(repository.chapterNumStack.size() > 0) {
            Book book = repository.getBook();
            for(int n : repository.chapterNumStack) {
                ProgressBar pb = barList.get(n).findViewById(R.id.progressbar);
                pb.setProgress((int)book.chapters.get(n).progress);
                if(book.chapters.get(n).done)
                    if(isAdded()) pb.setProgressDrawable(getResources().getDrawable(R.drawable.progress_drawable_done));
                else
                    if(isAdded()) pb.setProgressDrawable(getResources().getDrawable(R.drawable.progress_drawable));
            }
        }
        setCurrentProgress(repository.getNowPlayingPosition());
    }

    @Override
    public void onStop() {
        super.onStop();
        if(bookscaleHandler != null)
            bookscaleHandler.removeCallbacksAndMessages(null);
        getContext().unregisterReceiver(progressbarReceiver);
    }

    private void drawProgressBars() {
        if(infiniteBar.getVisibility() == View.GONE) infiniteBar.setVisibility(View.VISIBLE);
        if(progressbarFabric == null) progressbarFabric = new ProgressbarFabric(getActivity(), getBarMetrics());
        //очищаем layout, если там что-то было нарисовано
        if(barLayout.getChildCount() > 0) barLayout.removeAllViews();
        //очищаем barList, чтобы не сработал nowPlayingFile onChanged()
        if(barList != null) barList = null;
        progressbarFabric.setInterval(getDurationInterval());
        exec.execute(new Runnable() {
            @Override
            public void run() {
                barList = getChapterScales();
                chaptersWithBookmarks = getChaptersWithBookmarks();
                bookscaleHandler.sendEmptyMessage(MESSAGE_BARS_LOADED);
            }
        });
    }

    private void addBookmark(int num) {
        ConstraintLayout bar = (ConstraintLayout) barLayout.getChildAt(num);
        ImageView bookmark = bar.findViewById(R.id.progressbarBookmark);
        bookmark.setVisibility(View.VISIBLE);
    }

    private int[] getBarMetrics() {
        int[] displayMetrics = mActivityListener.getDisplayMetrics();

        int orientation = getResources().getConfiguration().orientation;
        int[] result;
        switch (orientation) {
            case 1: {
                result = new int[] {displayMetrics[0]/10, ((displayMetrics[1]/100) * BAR_VERTICAL_HEIGHT_CONSTANT)};
                break;
            }
            default: {
                result = new int[] {displayMetrics[0]/15, ((displayMetrics[1]/100) * BAR_HORIZONTAL_HEIGHT_CONSTANT)};
                break;
            }
        }
        return result;
    }

    private ArrayList<Integer> getChaptersWithBookmarks() {
        ArrayList<Integer> result = new ArrayList<>();
        ArrayList<Chapter> pl = repository.getBook().chapters;
        for(int i=0; i<pl.size(); i++) {
            ArrayList<Bookmark> bookmarkList = pl.get(i).bookmarks;
            if(bookmarkList != null) {
                if(bookmarkList.size() > 0) result.add(i);
            }
        }
        return result;
    }

    private ArrayList<ConstraintLayout> getChapterScales() {
        ArrayList<ConstraintLayout> barList = new ArrayList<>();
        ArrayList<Chapter> pl = repository.getBook().chapters;
        for(int i=0; i<pl.size(); i++) {
            ConstraintLayout bar = progressbarFabric.getBar(pl.get(i));
            bar.setTag(i);
            bar.setOnClickListener(this);
            bar.setOnLongClickListener(this);
            barList.add(bar);
        }
        return barList;
    }

    private Long[] getDurationInterval() {
        ArrayList<Long> durationList = new ArrayList<>();

        ArrayList<Chapter> pl = repository.getBook().chapters;

        for(Chapter chapter : pl) {
            durationList.add(chapter.duration);
        }

        return new Long[] {Collections.min(durationList), Collections.max(durationList)};
    }

    public ConstraintLayout getCurrentBar() {
        if(currentBar != null) {
            ProgressBar oldBar = currentBar.findViewById(R.id.progressbar);
            Book nowBook = repository.getBook();
            int oldChapterProgress = (int)(nowBook.chapters.get(nowBook.lastPlayedChapterNum).progress);
            if(oldBar.getProgress() < oldChapterProgress) oldBar.setProgress(oldChapterProgress);
            if(oldBar.getProgress() > oldBar.getMax() - BookRepository.GAP) {
                if(isAdded()) oldBar.setProgressDrawable(getResources().getDrawable(R.drawable.progress_drawable_done));
            }
            else {
                if(isAdded()) oldBar.setProgressDrawable(getResources().getDrawable(R.drawable.progress_drawable));
            }
        }
        int num = repository.getNowPlayingChapterNumber();
        ConstraintLayout b = barList.get(num);
        ProgressBar pbar = b.findViewById(R.id.progressbar);
        if(isAdded()) pbar.setProgressDrawable(getResources().getDrawable(R.drawable.progress_drawable_active));
        return b;
    }

    public void playChapter(Chapter ch) {
        repository.updateCurrentChapter(ch);
    }

    @Override
    public void onClick(View v) {
        ConstraintLayout bar = (ConstraintLayout) v;
        Chapter ch = repository.getBook().chapters.get((int)bar.getTag());
        playChapter(ch);
    }

    @Override
    public boolean onLongClick(View v) {
        Chapter ch = repository.getBook().chapters.get((int)v.getTag());
        informChapterTitle.setText(ch.chapter == null ? new File(ch.filepath).getName() : ch.chapter);

        bookscaleHandler.removeMessages(MESSAGE_TEXT_DISAPPEAR);
        bookscaleHandler.sendEmptyMessageDelayed(MESSAGE_TEXT_DISAPPEAR, TEXT_DISAPPEAR_DELAY);

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
            int position = intent.getIntExtra(MainActivity.PLAYBACK_STATUS, 0);
            if(currentBar != null) {
                if(!model.userIsSeeking) setCurrentProgress(position);
            }
        }
    }

    public void setCurrentProgress(int position) {
        if(currentBar != null) {
            ProgressBar pbar = currentBar.findViewById(R.id.progressbar);
            pbar.setProgress(position);
        }
    }

    private void clearInformChapterTitle() {
        informChapterTitle.setText("");
    }

    private void scrollToCurrent() {
        barScrollView.post(new Runnable() {
            @Override
            public void run() {
                int displayWidth = mActivityListener.getDisplayMetrics()[0];
                if(currentBar != null) {
                    int position = currentBar.getLeft() - displayWidth/2;
                    barScrollView.smoothScrollTo(position, 0);
                }
            }
        });
    }

    static class BookscaleHandler extends Handler {
        WeakReference<BookScaleFragment> wrFragment;

        BookscaleHandler(BookScaleFragment fragment) {
            wrFragment = new WeakReference<>(fragment);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            BookScaleFragment fragment = wrFragment.get();
            switch (msg.what) {
                case MESSAGE_TEXT_DISAPPEAR: {
                    if (fragment != null) fragment.clearInformChapterTitle();
                    break;
                }
                case MESSAGE_SCROLL_TO_CURRENT: {
                    if (fragment != null) fragment.scrollToCurrent();
                    break;
                }
                case MESSAGE_BARS_LOADED: {
                    if(fragment != null) {
                        fragment.infiniteBar.setVisibility(View.GONE);
                        int iterator = 1;
                        for(ConstraintLayout bar : fragment.barList) {
                            UpdatedProgressBar scale = bar.findViewById(R.id.progressbar);
                            scale.setNumber(iterator++);
                            fragment.barLayout.addView(bar);
                        }
                        if(chaptersWithBookmarks.size() > 0) {
                            for(int i : chaptersWithBookmarks) {
                                fragment.addBookmark(i);
                            }
                        }
                        fragment.currentBar = fragment.getCurrentBar();
                        fragment.scrollToCurrent();
                    }
                    break;
                }
            }
        }
    }
}
