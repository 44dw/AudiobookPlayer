package com.a44dw.audiobookplayer;

import android.Manifest;
import android.app.Activity;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.pm.PackageManager;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import java.io.File;

public class MainActivity extends AppCompatActivity implements OnIterationWithActivityListener {

    static final String TAG = "myDebugTag";
    public static final int SHOW_MEDIA_PLAYER = 1;
    public static final int SHOW_FILE_MANAGER = 2;
    public static int nowFragmentStatus;
    static AudiobookViewModel model;
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    FragmentTransaction fragmentTransaction;
    AudioPlayerFragment audioPlayerFragment;
    FileManagerFragment fileManagerFragment;

    public LiveData<File> nowPlayingFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        verifyPermissions(this);

        model = ViewModelProviders.of(this).get(AudiobookViewModel.class);
        model.initializeListener(this);

        fileManagerFragment = new FileManagerFragment();
        audioPlayerFragment = new AudioPlayerFragment();
        launchPlayerFragment();

        nowPlayingFile = model.getNowPlayingFile();
        nowPlayingFile.observe(this, new Observer<File>() {
            @Override
            public void onChanged(@Nullable File file) {
                int returnedStatus = AudiobookViewModel.playerHandler.loadMediaAndStartPlayer(file);
                switch (returnedStatus) {
                    case (AudiobookViewModel.STATUS_SKIP_TO_NEXT): {
                        Log.d(MainActivity.TAG, "MA -> nowPlayingFile.observe: onChanged -> STATUS_SKIP_TO_NEXT");
                        AudiobookViewModel.playerHandler.skipToNext();
                        break;
                    }
                    case (AudiobookViewModel.STATUS_SKIP_TO_PREVIOUS): {
                        Log.d(MainActivity.TAG, "MA -> nowPlayingFile.observe: onChanged -> STATUS_SKIP_TO_PREVIOUS");
                        AudiobookViewModel.playerHandler.skipToPrevious();
                        break;
                    }
                    case (AudiobookViewModel.STATUS_PLAY): {
                        Log.d(MainActivity.TAG, "MA -> nowPlayingFile.observe: onChanged -> STATUS_PLAY");
                        if(nowFragmentStatus == SHOW_FILE_MANAGER) launchPlayerFragment();
                        break;
                    }
                    default: {
                        Log.d(MainActivity.TAG, "MA -> nowPlayingFile.observe: onChanged -> making toast");
                        String message = (returnedStatus == AudiobookViewModel.STATUS_END_OF_DIR ? "достигнут конец каталога"
                                                                                                 : "неверный формат файла");
                        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
    }

    public static void verifyPermissions(Activity activity) {
        int permissionStorage = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permissionStorage != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }

    @Override
    public void onBackPressed() {
        FragmentManager fm = getSupportFragmentManager();
        OnBackPressedListener backPressedListener = null;
        for (Fragment fragment: fm.getFragments()) {
            if (fragment instanceof OnBackPressedListener) {
                backPressedListener = (OnBackPressedListener) fragment;
                break;
            }
        }
        if (backPressedListener != null) {
            backPressedListener.onBackPressed();
        } else {
            super.onBackPressed();
        }
    }

    public void launchFileManagerFragment() {
        Log.d(MainActivity.TAG, "MA -> launchFileManagerFragment()");
        fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.playerLayout, fileManagerFragment);
        fragmentTransaction.commit();
        nowFragmentStatus = SHOW_FILE_MANAGER;
    }

    public void launchPlayerFragment() {
        Log.d(MainActivity.TAG, "MA -> launchPlayerFragment()");
        fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.playerLayout, audioPlayerFragment);
        fragmentTransaction.commit();
        nowFragmentStatus = SHOW_MEDIA_PLAYER;
        if(audioPlayerFragment.seekBar != null) audioPlayerFragment.onDurationChanged(AudiobookViewModel.nowPlayingMediaDuration);
    }

    public interface OnBackPressedListener {
        public void onBackPressed();
    }
}