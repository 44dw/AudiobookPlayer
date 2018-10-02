package com.a44dw.audiobookplayer;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.io.File;
import java.util.ArrayList;

import static com.a44dw.audiobookplayer.AudiobookViewModel.fileManagerHandler;
import static com.a44dw.audiobookplayer.FileManagerHandler.pathToCurrentDirectory;

public class FileManagerFragment extends Fragment implements FileManagerHandler.OnFileManagerIterationWithFragmentListener,
                                                             MainActivity.OnBackPressedListener{

    Context context;
    View view;
    FileManagerAdapter adapter;

    public FileManagerFragment() {
        // Required empty public constructor
    }

    @Override
    public void onAttach(Context c) {
        super.onAttach(c);
        context = c;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(fileManagerHandler == null) fileManagerHandler = new FileManagerHandler(this);
        if(pathToCurrentDirectory == null) {
            pathToCurrentDirectory = new ArrayList<>();
            pathToCurrentDirectory.add(fileManagerHandler.goToRoot());
        }

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_file_manager, container, false);

        RecyclerView filesList = view.findViewById(R.id.fileManagerRecyclerView);
        LinearLayoutManager manager = new LinearLayoutManager(context);
        adapter = new FileManagerAdapter(pathToCurrentDirectory.get(pathToCurrentDirectory.size()-1), fileManagerHandler);
        filesList.setLayoutManager(manager);
        filesList.setAdapter(adapter);
        //установка разделителя
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(filesList.getContext(),
                manager.getOrientation());
        filesList.addItemDecoration(dividerItemDecoration);

        return view;
    }

    @Override
    public void onChooseDirectory(File f) {
        Log.d(MainActivity.TAG, "FileManagerFragment -> onChooseFile: " + f.toString());
        adapter.changeData(pathToCurrentDirectory.get(pathToCurrentDirectory.size() - 1));
    }

    @Override
    public void onBackPressed() {
        Log.d(MainActivity.TAG, "FileManagerFragment -> onBackPressed");
        pathToCurrentDirectory.remove(pathToCurrentDirectory.size()-1);
        adapter.changeData(pathToCurrentDirectory.get(pathToCurrentDirectory.size()-1));
    }
}
