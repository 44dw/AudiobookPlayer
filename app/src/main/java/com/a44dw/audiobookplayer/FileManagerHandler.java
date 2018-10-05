package com.a44dw.audiobookplayer;

import android.os.Environment;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.View;

import java.io.File;
import java.util.ArrayList;

import static com.a44dw.audiobookplayer.MainActivity.model;

public class FileManagerHandler implements FileManagerAdapter.OnItemClickListener {

    private OnFileManagerIterationWithFragmentListener fragmentListener;
    public static ArrayList<File> pathToCurrentDirectory;

    public FileManagerHandler(OnFileManagerIterationWithFragmentListener listener) {
        fragmentListener = listener;
    }

    public File goToRoot() {
        Log.d(MainActivity.TAG, "FileManagerHandler -> goToRoot");
        return Environment.getExternalStorageDirectory();
    }

    public File getNextOrPrevFile() {
        File nowPlayingFile = AudiobookViewModel.getNowPlayingFile().getValue();
        File directory = pathToCurrentDirectory.get(pathToCurrentDirectory.size() - 1);
        File[] filesInDirectory = directory.listFiles();
        boolean find = false;
        switch (AudiobookViewModel.getPlayerStatus()) {
            case PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS: {
                Log.d(MainActivity.TAG, "FileManagerHandler -> getNextOrPrevFile(): search for prev");
                for(int i = filesInDirectory.length-1; i>=0; i--) {
                    if(find) {
                        if(filesInDirectory[i].isDirectory()) continue;
                        return filesInDirectory[i];
                    }
                    if(filesInDirectory[i].equals(nowPlayingFile)) find = true;
                }
                break;
            }
            case PlaybackStateCompat.STATE_SKIPPING_TO_NEXT: {
                Log.d(MainActivity.TAG, "FileManagerHandler -> getNextOrPrevFile(): search for next");
                for(File f : filesInDirectory) {
                    if(find) {
                        if(f.isDirectory()) continue;
                        return f;
                    }
                    if(f.equals(nowPlayingFile)) find = true;
                }
            }
            break;
            default: Log.d(MainActivity.TAG, "FileManagerHandler -> getNextOrPrevFile(): default: playerStatus " + AudiobookViewModel.getPlayerStatus());
        }
        return null;
    }

    public void openPath(File openedFile) {
        if (openedFile.isDirectory()) {
            pathToCurrentDirectory.add(openedFile);
            fragmentListener.onChooseDirectory(openedFile);
        } else model.updateNowPlayingFile(openedFile);
    }

    @Override
    public void onItemClick(View item) {
        Log.d(MainActivity.TAG, "FileManagerHandler -> onItemClick " + item.getTag().toString());
        openPath((File)item.getTag());
    }

    public interface OnFileManagerIterationWithFragmentListener {
        void onChooseDirectory(File f);
    }
}
