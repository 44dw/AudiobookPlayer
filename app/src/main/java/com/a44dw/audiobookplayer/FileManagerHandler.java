package com.a44dw.audiobookplayer;

import android.content.SharedPreferences;
import android.os.Environment;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.net.URLConnection;
import java.util.ArrayList;

import static com.a44dw.audiobookplayer.AudiobookService.currentState;

public class FileManagerHandler implements FileManagerAdapter.OnItemClickListener {

    private OnFileManagerIterationWithFragmentListener fragmentListener;
    static ArrayList<File> pathToCurrentDirectory;
    private SharedPreferences prefs;

    BookRepository repository;

    public static final int MEDIA_ITSELF = 2;
    public static final int HAS_MEDIA = 1;
    public static final int NO_MEDIA = 0;

    FileManagerHandler(OnFileManagerIterationWithFragmentListener listener) {
        fragmentListener = listener;
        prefs = PreferenceManager.getDefaultSharedPreferences(((FileManagerFragment)fragmentListener).getActivity());
        String rootPath = prefs.getString(Preferences.KEY_ROOT, "");
        if(rootPath.equals("")) {
            prefs.edit().putString(Preferences.KEY_ROOT, Environment.getExternalStorageDirectory().getAbsolutePath()).apply();
        }
        repository = BookRepository.getInstance();
    }

    public void changeFragmentListener(OnFileManagerIterationWithFragmentListener listener) {
        fragmentListener = listener;
    }

    public void goToRoot() {
        File current = new File(prefs.getString(Preferences.KEY_ROOT, ""));

        if(current.exists()) {
            ArrayList<File> pathToRoot = new ArrayList<>();
            while (current != null) {
                if(current.toString().equals("/")) break;
                pathToRoot.add(0, current);
                current = current.getParentFile();
            }
            pathToCurrentDirectory = pathToRoot;
        } else {
            goToExternal();
            changeRootDirectory();
        }
    }

    //проверка на доступ
    public boolean checkExternal() {
        String state = Environment.getExternalStorageState();
        return state.equals(Environment.MEDIA_MOUNTED) ||
                state.equals(Environment.MEDIA_MOUNTED_READ_ONLY);
    }

    public void goToExternal() {
        File external = Environment.getExternalStorageDirectory();
        pathToCurrentDirectory.clear();
        pathToCurrentDirectory.add(external);
    }

    public void changeRootDirectory() {
        prefs.edit().putString(Preferences.KEY_ROOT,
                pathToCurrentDirectory.get(pathToCurrentDirectory.size()-1).getAbsolutePath()).apply();
    }

    private void openPath(String openedFilepath) {
        File openedFile = new File(openedFilepath);
        if(openedFile.exists()) {
            if (openedFile.isDirectory()) {
                pathToCurrentDirectory.add(openedFile);
                fragmentListener.onChooseDirectory(openedFile);
            } else {
                currentState = PlaybackStateCompat.STATE_BUFFERING;
                fragmentListener.closeSelf();
                repository.openFile(openedFile);
            }
        } else showFileNotExistToast();

    }

    private void showFileNotExistToast() {
        Toast.makeText(((FileManagerFragment)fragmentListener).getActivity(),
                R.string.false_directory,
                Toast.LENGTH_SHORT)
                .show();
    }

    private static Boolean isAudio(File file) {
        try {
            String result = URLConnection.guessContentTypeFromName(file.toString());
            return result == "audio/mpeg";
        } catch (StringIndexOutOfBoundsException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static ArrayList<File> filterData(File directory) {
        ArrayList<File> filteredList = new ArrayList<>();
        File[] files = directory.listFiles();
        if(files != null) {
            for(File f : files) if(f.isDirectory()||isAudio(f)) filteredList.add(f);
        } else {
            filteredList.add(Environment.getExternalStorageDirectory());
        }
        return filteredList;
    }

    public static Boolean containsMedia(File directory) {
        File[] files = directory.listFiles();
        if(files != null) {
            for(File f : files) {
                if(!f.isDirectory()) {
                    if(isAudio(f)) return true;
                } else {
                    if(containsMedia(f)) return true;
                }
            }
        } else return directory.getName().equals("emulated");
        return false;
    }

    @Override
    public void onItemClick(View item) {
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
