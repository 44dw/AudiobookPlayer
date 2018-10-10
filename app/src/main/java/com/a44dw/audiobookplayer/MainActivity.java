package com.a44dw.audiobookplayer;

import android.Manifest;
import android.app.Activity;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.ViewModelProviders;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

public class MainActivity extends AppCompatActivity implements OnIterationWithActivityListener {

    static final String TAG = "myDebugTag";

    //разрешения и всё, что с ними связано
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    //классы сервиса/медиа-контроллера
    ServiceConnection serviceConnection;
    MediaControllerCompat mediaController;
    MediaControllerCompat.Callback callback;
    AudiobookService.AudiobookServiceBinder audiobookServiceBinder;

    //фрагменты и всё, что с ними связано
    FragmentTransaction fragmentTransaction;
    FileManagerFragment fileManagerFragment;
    AudioPlayerFragment audioPlayerFragment;
    BookScaleFragment bookScaleFragment;
    OnPlaybackStateChangedListener playbackStateChangedListener;
    public static int nowFragmentStatus;
    public static boolean isBookscaleShown = false;
    public static final int SHOW_MEDIA_PLAYER = 1;
    public static final int SHOW_FILE_MANAGER = 2;
    public static final String seekBarBroadcastName = "seekBarBroadcastName";
    public static final String playbackStatus = "playbackStatus";

    //ViewModel и всё, что с ней связано
    static AudiobookViewModel model;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(MainActivity.TAG, "MA -> onCreate()");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //проверяем разрешения
        verifyPermissions(this);

        //прикрепляем сервис и настраиваем медиа-контроллер
        callback = new MediaControllerCompat.Callback() {
            @Override
            public void onPlaybackStateChanged(PlaybackStateCompat state) {
                if(state != null) {
                    int nowState = state.getState();
                    AudiobookViewModel.setPlayerStatus(nowState);
                    switch (nowState) {
                        case (PlaybackStateCompat.STATE_PLAYING): {
                            Log.d(MainActivity.TAG, "MA -> onPlaybackStateChanged: STATE_PLAYING");
                            if(nowFragmentStatus == SHOW_FILE_MANAGER) launchPlayerFragment();
                            if(!isBookscaleShown) {
                                launchBookScaleFragment();
                                isBookscaleShown = true;
                            }
                            playbackStateChangedListener.onPlayMedia();
                            break;
                        }
                        case (PlaybackStateCompat.STATE_STOPPED):
                        case (PlaybackStateCompat.STATE_PAUSED): {
                            Log.d(MainActivity.TAG, "MA -> onPlaybackStateChanged: STATE_PAUSED/STATE_STOPPED");
                            playbackStateChangedListener.onPauseMedia();
                            break;
                        }
                    }
                }
            }
        };

        serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.d(MainActivity.TAG, "MA -> onServiceConnected");
                audiobookServiceBinder = (AudiobookService.AudiobookServiceBinder) service;
                try {
                    mediaController = new MediaControllerCompat(MainActivity.this, audiobookServiceBinder.getMediaSessionToken());
                    mediaController.registerCallback(callback);
                    callback.onPlaybackStateChanged(mediaController.getPlaybackState());
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.d(MainActivity.TAG, "MA -> onServiceDisconnected");
                audiobookServiceBinder = null;
                if(mediaController != null) {
                    mediaController.unregisterCallback(callback);
                    mediaController = null;
                }
            }
        };

        bindService(new Intent(this, AudiobookService.class), serviceConnection, BIND_AUTO_CREATE);

        //инициализация фрагментов
        fileManagerFragment = new FileManagerFragment();
        audioPlayerFragment = new AudioPlayerFragment();
        bookScaleFragment = new BookScaleFragment();
        playbackStateChangedListener = audioPlayerFragment;
        launchPlayerFragment();

        //настраиваем модель
        model = ViewModelProviders.of(this).get(AudiobookViewModel.class);
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

    @Override
    public void launchFileManagerFragment() {
        Log.d(MainActivity.TAG, "MA -> launchFileManagerFragment()");
        fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.playerLayout, fileManagerFragment);
        fragmentTransaction.commit();
        nowFragmentStatus = SHOW_FILE_MANAGER;
    }

    @Override
    public MediaControllerCompat getMediaControllerCompat() {
        return mediaController;
    }

    public void launchPlayerFragment() {
        Log.d(MainActivity.TAG, "MA -> launchPlayerFragment()");
        fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.playerLayout, audioPlayerFragment);
        fragmentTransaction.commit();
        nowFragmentStatus = SHOW_MEDIA_PLAYER;
    }

    public void launchBookScaleFragment() {
        Log.d(MainActivity.TAG, "MA -> launchPlayerFragment()");
        fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.bookScaleLayout, bookScaleFragment);
        fragmentTransaction.commit();
    }

    public interface OnBackPressedListener {
        void onBackPressed();
    }

    public interface OnPlaybackStateChangedListener {
        void onPlayMedia();
        void onPauseMedia();
    }
}
