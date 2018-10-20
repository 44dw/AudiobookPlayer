package com.a44dw.audiobookplayer;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Observer;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;

public class BookInfoFragment extends Fragment {

    //View`s
    TextView bookInfoTitle;
    TextView bookInfoAuthor;
    TextView bookInfoTimeToEnd;
    Spinner bookInfoChapterSelectSpinner;

    //счётчик времени до конца книги
    int timeToEnd;

    //объекты ресивера
    IntentFilter filter = new IntentFilter(MainActivity.seekBarBroadcastName);
    ChapterPlayBroadReceiver chapterPlayReceiver;

    //объекты LiveData
    public static LiveData<Chapter> nowPlayingFile;
    public static LiveData<String> playlistName;

    public ArrayList<Chapter> playlist;

    public BookInfoFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_book_info, container, false);
        bookInfoTitle = view.findViewById(R.id.bookInfoTitle);
        bookInfoAuthor = view.findViewById(R.id.bookInfoAuthor);
        bookInfoTimeToEnd = view.findViewById(R.id.bookInfoTimeToEnd);
        bookInfoChapterSelectSpinner = view.findViewById(R.id.bookInfoChapterSelectSpinner);
        //на случай, если фрагмент создаётся при пустом плейлисте
        bookInfoChapterSelectSpinner.setVisibility(View.GONE);

        bookInfoChapterSelectSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Log.d(MainActivity.TAG, "BookInfoFragment -> onItemSelected() position is " + position);
                Chapter newCh = AudiobookViewModel.getPlaylist().getChapters().get(position);
                Log.d(MainActivity.TAG, "BookInfoFragment -> onItemSelected() " + newCh.getChapter());
                AudiobookViewModel.updateNowPlayingFile(newCh);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        chapterPlayReceiver = new ChapterPlayBroadReceiver();
        getContext().registerReceiver(chapterPlayReceiver, filter);

        if(nowPlayingFile == null) {
            nowPlayingFile = AudiobookViewModel.getNowPlayingFile();
            nowPlayingFile.observe(this, new Observer<Chapter>() {
                @Override
                public void onChanged(@Nullable Chapter chapter) {
                    Log.d(MainActivity.TAG, "BookInfoFragment -> onChanged nowPlayingFile: setting title and author");
                    String title = chapter.getTitle();
                    String author = chapter.getAuthor();
                    if(title != null) bookInfoTitle.setText(title);
                    if(author != null) bookInfoAuthor.setText(author);

                    int num = AudiobookViewModel.getNowPlayingFileNumber();

                    if(bookInfoChapterSelectSpinner.getSelectedItemPosition() != num)
                        bookInfoChapterSelectSpinner.setSelection(num);

                    //вычисляем время до конца книги при переключении между файлами в рамках одного плейлиста
                    //если вызвать этот метод сразу после обновления плейлиста, получим NPExeption.
                    //TODO попробовать перенести timeToEnd в Book
                    if(playlistName.getValue() != null) {
                        timeToEnd = getTimeToEnd(num);
                    }
                }
            });
        }

        if(playlistName == null) {
            playlistName = AudiobookViewModel.getPlaylistName();
            playlistName.observe(this, new Observer<String>() {
                @Override
                public void onChanged(@Nullable String name) {
                    Book book = AudiobookViewModel.getPlaylist();
                    playlist = book.getChapters();

                    //показываем и заполняем спиннер
                    if(bookInfoChapterSelectSpinner.getVisibility() == View.GONE)
                        bookInfoChapterSelectSpinner.setVisibility(View.VISIBLE);

                    BookinfoArrayAdapter adapter = new BookinfoArrayAdapter(getContext(), R.layout.book_info_item, playlist);
                    bookInfoChapterSelectSpinner.setAdapter(adapter);

                    int num = AudiobookViewModel.getNowPlayingFileNumber();
                    //вычисляем время до конца книги сразу после обновления плейлиста.
                    timeToEnd = getTimeToEnd(num);

                    Log.d(MainActivity.TAG, "BookInfoFragment -> onChanged playlistName: prepare bookInfoChapterSelectSpinner: setting selection to " + num);

                    if(bookInfoChapterSelectSpinner.getSelectedItemPosition() != num)
                        bookInfoChapterSelectSpinner.setSelection(num);
                }
            });
        }

        return view;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getContext().unregisterReceiver(chapterPlayReceiver);
    }

    private int getTimeToEnd(int numInPlaylist) {
        ArrayList<Chapter> chapters = AudiobookViewModel.getPlaylist().getChapters();
        int durationToEnd = 0;
        for(int i=(chapters.size() - 1); i > numInPlaylist; i--) {
            Chapter ch = chapters.get(i);
            if(ch.isDone()) continue;
            long duration = ch.getDuration() - ch.getProgress();
            durationToEnd += duration;
        }
        Log.d(MainActivity.TAG, "BookInfoFragment -> getTimeToEnd(): " + durationToEnd);
        return durationToEnd;
    }

    public class ChapterPlayBroadReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int position = intent.getIntExtra(MainActivity.playbackStatus, 0);
            int toChapterEnd = (int)nowPlayingFile.getValue().getDuration() - position;
            int toBookEnd = timeToEnd + toChapterEnd;
            DateFormat df = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            bookInfoTimeToEnd.setText(df.format(toBookEnd));
        }
    }
}
