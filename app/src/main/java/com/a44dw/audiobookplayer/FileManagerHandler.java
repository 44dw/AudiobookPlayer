package com.a44dw.audiobookplayer;

import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.net.URLConnection;
import java.util.ArrayList;

public class FileManagerHandler implements FileManagerAdapter.OnItemClickListener {

    private OnFileManagerIterationWithFragmentListener fragmentListener;
    public static ArrayList<File> pathToCurrentDirectory;
    private static File rootDirectory = Environment.getExternalStorageDirectory();
    public static final int MEDIA_ITSELF = 2;
    public static final int HAS_MEDIA = 1;
    public static final int NO_MEDIA = 0;

    public FileManagerHandler(OnFileManagerIterationWithFragmentListener listener) {
        fragmentListener = listener;
    }

    public void goToRoot() {
        File current = rootDirectory;
        Log.d(MainActivity.TAG, "FileManagerHandler -> goToRoot(): rootDirectory: " + current.getAbsolutePath());
        ArrayList<File> pathToRoot = new ArrayList<>();
        while (current != null) {
            Log.d(MainActivity.TAG, "FileManagerHandler -> goToRoot: current: " + current);
            pathToRoot.add(0, current);
            current = current.getParentFile();
        }
        pathToCurrentDirectory = pathToRoot;
    }

    public static void changeRootDirectory() {
        FileManagerHandler.rootDirectory = pathToCurrentDirectory.get(pathToCurrentDirectory.size()-1);
    }

    public static File getRootDirectory() {
        return rootDirectory;
    }

    public void openPath(String openedFilepath) {
        File openedFile = new File(openedFilepath);
        if (openedFile.isDirectory()) {
            pathToCurrentDirectory.add(openedFile);
            fragmentListener.onChooseDirectory(openedFile);
        } else {
            AudiobookViewModel.updateNowPlayingFile(new Chapter(openedFile));
            fragmentListener.closeSelf();
        }
    }

    private static Boolean isAudio(File file) {
        //Log.d(MainActivity.TAG, "FileManagerHandler -> testing on Audio " + file.toString());
        String result = URLConnection.guessContentTypeFromName(file.toString());
        //сравнивается "==" потому что result может быть null и по .equals крашится
        return result == "audio/mpeg";
    }

    public static ArrayList<File> filterData(File directory) {
        Log.d(MainActivity.TAG, "FileManagerHandler -> filterData: directory is " + directory.toString());
        //если идти выше Environment.getExternalStorageDirectory(), крашится: files == null
        //TODO проверить на реальном устройстве
        File[] files = directory.listFiles();
        ArrayList<File> filteredList = new ArrayList<>();
        for(File f : files) if(f.isDirectory()||isAudio(f)) filteredList.add(f);
        return filteredList;
    }

    public static Boolean containsMedia(File directory) {
        File[] files = directory.listFiles();
        for(File f : files) {
            if(!f.isDirectory()) {
                if(isAudio(f)) return true;
            } else {
                if(containsMedia(f)) return true;
            }
        }
        return false;
    }

    @Override
    public void onItemClick(View item) {
        Log.d(MainActivity.TAG, "FileManagerHandler -> onItemClick " + item.getTag(R.string.key_path).toString() + " is media: " + item.getTag(R.string.key_is_media));
        if(item.getTag(R.string.key_is_media).equals(NO_MEDIA)) Toast.makeText(((FileManagerFragment)fragmentListener).getContext(),
                                ((FileManagerFragment)fragmentListener).getActivity().getText(R.string.no_media),
                                Toast.LENGTH_SHORT)
                                .show();
        else openPath((String)item.getTag(R.string.key_path));
    }

    public interface OnFileManagerIterationWithFragmentListener {
        void onChooseDirectory(File f);
        void closeSelf();
    }
}
