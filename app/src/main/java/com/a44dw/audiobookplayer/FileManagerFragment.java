package com.a44dw.audiobookplayer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;

import static com.a44dw.audiobookplayer.AudiobookViewModel.fileManagerHandler;
import static com.a44dw.audiobookplayer.FileManagerHandler.pathToCurrentDirectory;

public class FileManagerFragment extends Fragment implements FileManagerHandler.OnFileManagerIterationWithFragmentListener,
                                                             MainActivity.OnBackPressedListener,
                                                             View.OnClickListener{

    Context context;
    View view;
    LinearLayout fileManagerPathLayout;
    TextView fileManagerPathText;
    FileManagerAdapter adapter;
    OnIterationWithActivityListener activityListener;

    public FileManagerFragment() {}

    @Override
    public void onAttach(Context c) {
        super.onAttach(c);
        context = c;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(fileManagerHandler == null) fileManagerHandler = new FileManagerHandler(this);

        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_file_manager, container, false);

        RecyclerView filesList = view.findViewById(R.id.fileManagerRecyclerView);
        fileManagerPathLayout = view.findViewById(R.id.fileManagerPathLayout);
        fileManagerPathLayout.setOnClickListener(this);
        fileManagerPathText = view.findViewById(R.id.fileManagerPathToDirectory);
        fileManagerHandler.goToRoot();
        fileManagerPathText.setText(getStringPath(pathToCurrentDirectory));
        LinearLayoutManager manager = new LinearLayoutManager(context);
        adapter = new FileManagerAdapter(
                FileManagerHandler.filterData(FileManagerHandler.getRootDirectory()),
                fileManagerHandler);
        filesList.setLayoutManager(manager);
        filesList.setAdapter(adapter);
        //установка разделителя
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(filesList.getContext(),
                manager.getOrientation());
        filesList.addItemDecoration(dividerItemDecoration);
        activityListener = (OnIterationWithActivityListener) getActivity();

        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        Log.d(MainActivity.TAG, "FileManagerFragment -> onCreateOptionsMenu()");
        inflater.inflate(R.menu.menu_filemanager, menu);
        //проверяем условие, показывать ли иконку play/pause
        if(AudiobookViewModel.getPlayerStatus() == PlaybackStateCompat.STATE_PLAYING ||
           AudiobookViewModel.getPlayerStatus() == PlaybackStateCompat.STATE_PAUSED) {
            MenuItem item = menu.findItem(R.id.menu_play);
            item.setVisible(true);
            item.setIcon(
                    AudiobookViewModel.getPlayerStatus() == PlaybackStateCompat.STATE_PLAYING ?
                            R.drawable.ic_pause_white_24dp :
                            R.drawable.ic_play_arrow_white_24dp
            );
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.d(MainActivity.TAG, "FileManagerFragment -> onOptionsItemSelected()");
        int id = item.getItemId();
        switch (id) {
            case R.id.menu_filemanager_refresh:
                adapter.changeData(FileManagerHandler.filterData(pathToCurrentDirectory.get(pathToCurrentDirectory.size()-1)));
                return true;
            case R.id.menu_filemanager_special:
                FileManagerHandler.changeRootDirectory();
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
        Log.d(MainActivity.TAG, "FileManagerFragment -> goToParentDirectory()");
        pathToCurrentDirectory.remove(pathToCurrentDirectory.size()-1);
        adapter.changeData(FileManagerHandler.filterData(pathToCurrentDirectory.get(pathToCurrentDirectory.size()-1)));
        fileManagerPathText.setText(getStringPath(pathToCurrentDirectory));
    }

    @Override
    public void onChooseDirectory(File f) {
        Log.d(MainActivity.TAG, "FileManagerFragment -> onChooseFile: " + f.toString());
        fileManagerPathText.setText(getStringPath(pathToCurrentDirectory));
        Log.d(MainActivity.TAG, "FileManagerFragment -> onChooseFile: current directory is " + pathToCurrentDirectory.toString());
        adapter.changeData(FileManagerHandler.filterData(pathToCurrentDirectory.get(pathToCurrentDirectory.size()-1)));
    }

    @Override
    public void closeSelf() {
        activityListener.showFileManager(false);
    }

    @Override
    public void onBackPressed() {
        String currentPath = pathToCurrentDirectory.get(pathToCurrentDirectory.size() - 1).getAbsolutePath();
        String rootPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        Log.d(MainActivity.TAG, "FileManagerFragment -> onBackPressed: currentPath: " + currentPath + "\nrootpath: " + rootPath);
        if(currentPath.equals(rootPath)) {
            activityListener.goBack();
        } else goToParentDirectory();
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
