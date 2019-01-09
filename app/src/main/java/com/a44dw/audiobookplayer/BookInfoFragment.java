package com.a44dw.audiobookplayer;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;

public class BookInfoFragment extends Fragment implements CompoundButton.OnCheckedChangeListener {

    TextView bookInfoTitle;
    TextView bookInfoTimeToEnd;
    TextView bookInfoTimeToEndDescr;
    Switch bookInfoBookscaleSwitch;
    Spinner bookInfoChapterSelectSpinner;

    OnIterationWithActivityListener mActivityListener;

    AudiobookViewModel model;

    BookRepository repository;

    int remainTime;
    String remainFlag;

    ChapterPlayBroadReceiver chapterPlayReceiver;

    public static LiveData<Chapter> currentChapter;
    public static LiveData<String> currentBookName;

    @Override
    public void onAttach(Context c) {
        super.onAttach(c);
        if (c instanceof OnIterationWithActivityListener) {
            mActivityListener = (OnIterationWithActivityListener) c;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repository = BookRepository.getInstance();

        currentChapter = repository.getCurrentChapter();
        currentBookName = repository.getBookName();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_book_info, container, false);
        bookInfoTitle = view.findViewById(R.id.bookInfoTitle);
        bookInfoTitle.setText(R.string.load);
        bookInfoTimeToEnd = view.findViewById(R.id.bookInfoTimeToEnd);
        bookInfoTimeToEndDescr = view.findViewById(R.id.bookInfoTimeToEndDescr);
        bookInfoChapterSelectSpinner = view.findViewById(R.id.bookInfoChapterSelectSpinner);
        bookInfoBookscaleSwitch = view.findViewById(R.id.bookInfoBookscaleSwitch);
        bookInfoBookscaleSwitch.setOnCheckedChangeListener(this);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        if(prefs.getBoolean(Preferences.SHOW_BOOKSCALE, true)) {
            bookInfoBookscaleSwitch.setOnCheckedChangeListener(null);
            bookInfoBookscaleSwitch.setChecked(true);
            bookInfoBookscaleSwitch.setOnCheckedChangeListener(this);
        }
        if(model == null)
            model = ViewModelProviders.of(getActivity()).get(AudiobookViewModel.class);

        bookInfoChapterSelectSpinner.setVisibility(View.GONE);
        SpinnerInteractionListener siListener = new SpinnerInteractionListener();
        bookInfoChapterSelectSpinner.setOnItemSelectedListener(siListener);
        bookInfoChapterSelectSpinner.setOnTouchListener(siListener);

        remainFlag = PreferenceManager.getDefaultSharedPreferences(getActivity())
                .getString(Preferences.REMAIN_TIME, "1");
        bookInfoTimeToEndDescr.setText((remainFlag.equals("1") ? getString(R.string.bookinfo_to_end) :
                                                                 getString(R.string.bookinfo_unlistened)));
        currentChapter.observe(this, new Observer<Chapter>() {
            @Override
            public void onChanged(@Nullable Chapter chapter) {
                if(chapter != null) {
                    setInfo(chapter);

                    int num = 0;

                    if(repository.getBook() != null) {
                        num = repository.getNowPlayingChapterNumber();
                        bookInfoChapterSelectSpinner.setSelection(num);
                    }

                    if((currentBookName.getValue() != null)&&(currentBookName.getValue().length() > 0)) {
                        remainFlag = PreferenceManager.getDefaultSharedPreferences(getActivity())
                                .getString(Preferences.REMAIN_TIME, "1");
                        remainTime = getRemainTime(num);
                        setBookRemainTime((int)chapter.progress);
                    }
                }
            }
        });

        currentBookName.observe(this, new Observer<String>() {
            @Override
            public void onChanged(String name) {
                if((name.length() > 0)) {
                    Book book = repository.getBook();
                    ArrayList<Chapter> playlist = book.chapters;

                    if(bookInfoChapterSelectSpinner.getVisibility() == View.GONE)
                        bookInfoChapterSelectSpinner.setVisibility(View.VISIBLE);

                    BookinfoArrayAdapter adapter = new BookinfoArrayAdapter(getContext(), R.layout.book_info_item, playlist);
                    bookInfoChapterSelectSpinner.setAdapter(adapter);

                    int num = repository.getNowPlayingChapterNumber();
                    remainTime = getRemainTime(num);

                    bookInfoChapterSelectSpinner.setSelection(num);

                    if((currentChapter.getValue() != null)&&
                            (currentChapter.getValue().bId == repository.getBook().bookId))
                        setInfo(currentChapter.getValue());
                } else {
                    bookInfoTitle.setText(R.string.load);
                    if(bookInfoChapterSelectSpinner.getVisibility() == View.VISIBLE)
                        bookInfoChapterSelectSpinner.setVisibility(View.GONE);
                }
            }
        });
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();

        chapterPlayReceiver = new ChapterPlayBroadReceiver();
        IntentFilter filter = new IntentFilter(MainActivity.SEEK_BAR_BROADCAST_NAME);
        getContext().registerReceiver(chapterPlayReceiver, filter);
    }


    private void onSwitchCheckedChanged(boolean isChecked) {
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .edit()
                .putBoolean(Preferences.SHOW_BOOKSCALE, isChecked)
                .apply();

        mActivityListener.showBookScale(isChecked);
    }

    void setInfo(Chapter chapter) {
        String title = chapter.title;
        String author = chapter.author;
        String separator = ", ";
        String info;
        boolean noTitle = ((title == null)||(title.length() == 0));
        boolean noAuthor = ((author == null)||(author.length() == 0));

        if(noTitle && noAuthor) info = new File(chapter.filepath).getParentFile().getName();
        else if (noTitle) info = author;
        else if (noAuthor) info = title;
        else info = author + separator + title;

        bookInfoTitle.setText(info);
    }

    //проверяем, не изменились ли настройки
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

            boolean isBookscaleShown = prefs.getBoolean(Preferences.SHOW_BOOKSCALE, true);
            if(isBookscaleShown != bookInfoBookscaleSwitch.isChecked()) {
                bookInfoBookscaleSwitch.setOnCheckedChangeListener(null);
                bookInfoBookscaleSwitch.setChecked(isBookscaleShown);
                bookInfoBookscaleSwitch.setOnCheckedChangeListener(this);
            }

            String newFlag = prefs.getString(Preferences.REMAIN_TIME, "1");
            if(!newFlag.equals(remainFlag)) {
                 int num = repository.getNowPlayingChapterNumber();
                 remainTime = getRemainTime(num);
                 bookInfoTimeToEndDescr.setText((newFlag.equals("1") ? getString(R.string.bookinfo_to_end) :
                         getString(R.string.bookinfo_unlistened)));
                 remainFlag = newFlag;
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        getContext().unregisterReceiver(chapterPlayReceiver);
    }

    private int getRemainTime(int numInPlaylist) {
        String remainFlag = PreferenceManager.getDefaultSharedPreferences(getActivity())
                .getString(Preferences.REMAIN_TIME, "1");
        return (remainFlag.equals("1") ? getTimeToEnd(numInPlaylist) : getTimeUnheard(numInPlaylist));
    }

    private int getTimeToEnd(int numInPlaylist) {
        ArrayList<Chapter> chapters = repository.getBook().chapters;
        int durationToEnd = 0;
        for(int i=(chapters.size() - 1); i > numInPlaylist; i--) {
            Chapter ch = chapters.get(i);
            if(ch.done) continue;
            long duration = ch.duration - ch.progress;
            durationToEnd += duration;
        }
        return durationToEnd;
    }

    public void setLoad() {
        if(bookInfoTitle != null) bookInfoTitle.setText(R.string.load);
    }

    private int getTimeUnheard(int numInPlaylist) {
        ArrayList<Chapter> chapters = repository.getBook().chapters;
        int durationToEnd = 0;
        for(int i=0; i<chapters.size(); i++) {
            Chapter ch = chapters.get(i);
            if((ch.done)||(i == numInPlaylist)) continue;
            long duration = ch.duration - ch.progress;
            durationToEnd += duration;
        }
        return durationToEnd;
    }

    public void setBookRemainTime(int position) {
        Chapter ch = currentChapter.getValue();
        if(ch != null) {
            int toChapterEnd = (int)ch.duration - position;
            int toBookEnd = remainTime + toChapterEnd;

            if(toBookEnd >= 3600 * 1000) bookInfoTimeToEnd.setText(model.longDf.format(toBookEnd));
            else bookInfoTimeToEnd.setText(model.shortDf.format(toBookEnd));
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        onSwitchCheckedChanged(isChecked);
    }

    public class SpinnerInteractionListener implements AdapterView.OnItemSelectedListener, View.OnTouchListener {

        boolean userSelect = false;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            userSelect = true;
            return false;
        }

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
            if (userSelect) {
                Chapter newCh = repository.getBook().chapters.get(pos);
                repository.updateCurrentChapter(newCh);
                userSelect = false;
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {}
    }

    public class ChapterPlayBroadReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int position = intent.getIntExtra(MainActivity.PLAYBACK_STATUS, 0);
            if(!model.userIsSeeking) setBookRemainTime(position);
        }
    }
}
