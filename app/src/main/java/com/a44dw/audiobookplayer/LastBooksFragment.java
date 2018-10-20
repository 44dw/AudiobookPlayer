package com.a44dw.audiobookplayer;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LastBooksFragment extends Fragment implements LastbooksAdapter.OnItemClickListener {

    private View view;
    private File innerStorage;
    private File playlistStorage;
    private RecyclerView lastbooksRecyclerView;
    private LastbooksAdapter adapter;
    private ArrayList<String> jsonBooks;
    LinearLayoutManager manager;
    OnIterationWithActivityListener activityListener;

    public LastBooksFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_last_books, container, false);
        innerStorage = getActivity().getFilesDir();
        playlistStorage = new File(innerStorage.getAbsolutePath() + File.separator + "playlists");
        jsonBooks = getPlaylists();
        lastbooksRecyclerView = view.findViewById(R.id.lastbooksRecyclerView);

        activityListener = (OnIterationWithActivityListener) getActivity();

        updatePlaylists();

        return view;
    }

    private void updatePlaylists() {
        Log.d(MainActivity.TAG, "LastBooksFragment -> updatePlaylists");
        manager = new LinearLayoutManager(getContext());
        adapter = new LastbooksAdapter(jsonBooks, this);
        lastbooksRecyclerView.setLayoutManager(manager);
        lastbooksRecyclerView.setAdapter(adapter);
        //установка разделителя
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(lastbooksRecyclerView.getContext(),
                manager.getOrientation());
        lastbooksRecyclerView.addItemDecoration(dividerItemDecoration);
    }

    private ArrayList<String> getPlaylists() {
        File[] files = playlistStorage.listFiles();
        ArrayList<String> books = new ArrayList<>();
        for(File f : files) {
            Log.d(MainActivity.TAG, "LastBooksFragment -> getPlaylists(): " + f.toString());
            try {
                BufferedReader br = new BufferedReader(new FileReader(f));
                String jsonPlaylist = br.readLine();
                books.add(jsonPlaylist);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return books;
    }

    @Override
    public void onItemClick(View item) {
        Log.d(MainActivity.TAG, "LastBooksFragment -> onItemClick()");

        String jsonBook = (String)item.getTag();

        GsonBuilder builder = new GsonBuilder();
        Gson gson = builder.create();
        Log.d(MainActivity.TAG, "InnerStorageHandler -> loadPlaylistFromStorage(): read book from json " + jsonBook);
        Book loadedBook = gson.fromJson(jsonBook, Book.class);

        if(!loadedBook.equals(AudiobookViewModel.getPlaylist())) {
            Log.d(MainActivity.TAG, "InnerStorageHandler -> loadPlaylistFromStorage(): selected Book NOT equals playing Book");
            //достаём последний проигрываемый файл из плейлиста и запускаем
            int lastPlayedChapterNum = loadedBook.getLastPlayedChapterNum();
            Chapter startChapter = loadedBook.getChapters().get(lastPlayedChapterNum);
            AudiobookViewModel.updateNowPlayingFile(startChapter);
        }
        activityListener.showLastBooks(false);
    }
}
