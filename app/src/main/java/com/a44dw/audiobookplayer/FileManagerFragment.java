package com.a44dw.audiobookplayer;

import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.v4.app.Fragment;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;

import static com.a44dw.audiobookplayer.AudiobookService.currentState;
import static com.a44dw.audiobookplayer.FileManagerHandler.pathToCurrentDirectory;

public class FileManagerFragment extends Fragment implements FileManagerHandler.OnFileManagerIterationWithFragmentListener,
                                                             MainActivity.OnBackPressedListener,
                                                             View.OnClickListener{

    ConstraintLayout fileManagerPathLayout;
    LinearLayoutManager filesListManager;
    TextView fileManagerPathText;
    FileManagerAdapter adapter;
    RecyclerView filesList;
    OnIterationWithActivityListener activityListener;

    AudiobookViewModel model;
    BookRepository repository;

    public FileManagerFragment() {}

    @Override
    public void onAttach(Context c) {
        super.onAttach(c);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        model = ViewModelProviders.of(this).get(AudiobookViewModel.class);
        repository = BookRepository.getInstance();

        if(model.fileManagerHandler == null) {
            model.fileManagerHandler = new FileManagerHandler(this);
            if(model.fileManagerHandler.checkExternal()) model.fileManagerHandler.goToRoot();
        } else model.fileManagerHandler.changeFragmentListener(this);

        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_file_manager, container, false);

        filesList = view.findViewById(R.id.fileManagerRecyclerView);
        fileManagerPathLayout = view.findViewById(R.id.fileManagerPathLayout);
        fileManagerPathLayout.setOnClickListener(this);
        fileManagerPathText = view.findViewById(R.id.fileManagerPathToDirectory);
        fileManagerPathText.setText(getStringPath(pathToCurrentDirectory));
        filesListManager = new LinearLayoutManager(getActivity());
        activityListener = (OnIterationWithActivityListener) getActivity();

        if(model.fileManagerHandler.checkExternal()) {
            File cdirectory = pathToCurrentDirectory.get(pathToCurrentDirectory.size()-1);
            if(!cdirectory.exists()) model.fileManagerHandler.goToRoot();
            adapter = new FileManagerAdapter(
                    FileManagerHandler.filterData(pathToCurrentDirectory.get(pathToCurrentDirectory.size()-1)),
                    model.fileManagerHandler);

            filesList.setLayoutManager(filesListManager);
            filesList.setAdapter(adapter);
            DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(filesList.getContext(),
                    filesListManager.getOrientation());
            filesList.addItemDecoration(dividerItemDecoration);
        } else {
            Toast.makeText(getContext(),
                    R.string.root_directory_not_available,
                    Toast.LENGTH_SHORT)
                    .show();
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        adapter.changeData(FileManagerHandler.filterData(pathToCurrentDirectory.get(pathToCurrentDirectory.size()-1)));
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_filemanager, menu);
        if(currentState == PlaybackStateCompat.STATE_PLAYING ||
                currentState == PlaybackStateCompat.STATE_PAUSED) {
            MenuItem item = menu.findItem(R.id.menu_play);
            item.setVisible(true);
            item.setIcon(
                    currentState == PlaybackStateCompat.STATE_PLAYING ?
                            R.drawable.ic_pause_white_24dp :
                            R.drawable.ic_play_arrow_white_24dp
            );
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.menu_filemanager_refresh:
                adapter.changeData(FileManagerHandler.filterData(pathToCurrentDirectory.get(pathToCurrentDirectory.size()-1)));
                return true;
            case R.id.menu_filemanager_special:
                model.fileManagerHandler.changeRootDirectory();
                Toast.makeText(getContext(),
                        R.string.change_root_directory,
                        Toast.LENGTH_SHORT)
                        .show();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private String getStringPath(ArrayList<File> pathToCurrentDirectory) {

        StringBuilder sb = new StringBuilder();
        for (int i=0; i<pathToCurrentDirectory.size(); i++) {
            File f = pathToCurrentDirectory.get(i);
            String name = (i == 0) ? f.getAbsolutePath() : f.getName();
            sb.append(name);
            if(i < pathToCurrentDirectory.size()-1)sb.append("/");
        }
        return sb.toString();
    }

    private void goToParentDirectory() {
        if(pathToCurrentDirectory.size() == 1) return;
        pathToCurrentDirectory.remove(pathToCurrentDirectory.size()-1);
        File openedDirectory = pathToCurrentDirectory.get(pathToCurrentDirectory.size()-1);
        if (openedDirectory.exists()) {
            adapter.changeData(FileManagerHandler.filterData(openedDirectory));
        } else {
            Toast.makeText(getActivity(),
                    R.string.false_directory,
                    Toast.LENGTH_SHORT)
                    .show();
            model.fileManagerHandler.goToExternal();
            adapter.changeData(FileManagerHandler.filterData(pathToCurrentDirectory.get(0)));
        }
        fileManagerPathText.setText(getStringPath(pathToCurrentDirectory));
    }

    @Override
    public void onChooseDirectory(File f) {
        fileManagerPathText.setText(getStringPath(pathToCurrentDirectory));
        adapter.changeData(FileManagerHandler.filterData(pathToCurrentDirectory.get(pathToCurrentDirectory.size()-1)));
    }

    @Override
    public void closeSelf() {
        activityListener.showFileManager(false);
    }

    @Override
    public void onBackPressed() {
        activityListener.goBack();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case (R.id.fileManagerPathLayout): {
                goToParentDirectory();
                break;
            }
        }
    }
}