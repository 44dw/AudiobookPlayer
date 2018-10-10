package com.a44dw.audiobookplayer;

import android.content.ContentResolver;
import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Environment;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

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

    public void openPath(File openedFile) {
        if (openedFile.isDirectory()) {
            pathToCurrentDirectory.add(openedFile);
            fragmentListener.onChooseDirectory(openedFile);
        } else {
            if(isAudio(openedFile)) {
                AudiobookViewModel.updateNowPlayingFile(new Chapter(openedFile));
                AudiobookViewModel.updatePlaylist(getPlaylist());
            }
            else {
                Context context = ((FileManagerFragment)fragmentListener).getContext();
                Toast.makeText(context,
                        context.getString(R.string.wrong_file),
                        Toast.LENGTH_SHORT)
                        .show();
            }
        }
    }

    public ArrayList<Chapter> getPlaylist() {
        ArrayList<Chapter> playlist = new ArrayList<>();
        File directory = pathToCurrentDirectory.get(pathToCurrentDirectory.size() - 1);
        File[] filesInDirectory = directory.listFiles();
        for(File f : filesInDirectory) {
            if(isAudio(f)) {
                playlist.add(new Chapter(f));
            }
        }
        return playlist;
    }

    private Boolean isAudio(File file) {
        return URLConnection.guessContentTypeFromName(file.toString()).equals("audio/mpeg");
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
