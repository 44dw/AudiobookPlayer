package com.a44dw.audiobookplayer;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Observer;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.File;

public class AudioPlayerFragment extends Fragment implements MainActivity.OnPlaybackStateChangedListener,
                                                             View.OnClickListener {

    static ImageButton playPauseButton;
    SeekBar seekBar;
    TextView progressTextView;
    boolean userIsSeeking = false;
    OnIterationWithActivityListener mActivityListener;
    MediaControllerCompat mediaController;
    public static LiveData<Integer> nowPlayingMediaDuration;
    private SeekbarBroadReceiver seekbarReceiver;

    public AudioPlayerFragment() {
        // Required empty public constructor
    }

    @Override
    public void onAttach(Context c) {
        super.onAttach(c);
        if (c instanceof OnIterationWithActivityListener) {
            mActivityListener = (OnIterationWithActivityListener) c;
        } else {
            throw new RuntimeException(c.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //получаем контроллер для взаимодействия с MediaSession
        mediaController = mActivityListener.getMediaControllerCompat();

        //регистрируем ресивер для получения информации о прогрессе песни
        IntentFilter filter = new IntentFilter(MainActivity.seekBarBroadcastName);
        seekbarReceiver = new SeekbarBroadReceiver();
        getContext().registerReceiver(seekbarReceiver, filter);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.d(MainActivity.TAG, "AudioPlayerFragment -> onCreateView");
        View view = inflater.inflate(R.layout.fragment_audio_player, container, false);
        playPauseButton = view.findViewById(R.id.playPauseButton);
        int playPauseImageRecource = (AudiobookViewModel.getPlayerStatus() == PlaybackStateCompat.STATE_PLAYING
                                     ? R.drawable.ic_pause_black_24dp
                                     : R.drawable.ic_play_arrow_black_24dp);
        playPauseButton.setImageResource(playPauseImageRecource);
        playPauseButton.setOnClickListener(this);
        view.findViewById(R.id.skipToNextButton).setOnClickListener(this);
        view.findViewById(R.id.skipToPreviousButton).setOnClickListener(this);
        view.findViewById(R.id.rewindBackButton).setOnClickListener(this);
        view.findViewById(R.id.rewindForwardButton).setOnClickListener(this);
        seekBar = view.findViewById(R.id.seekBar);
        progressTextView = view.findViewById(R.id.progressTextView);
        progressTextView.setText("test");
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int userSelectedPosition = 0;
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if(fromUser) userSelectedPosition = progress;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                userIsSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                userIsSeeking = false;
                mediaController.getTransportControls().seekTo((long) userSelectedPosition);
            }
        });

        nowPlayingMediaDuration = AudiobookViewModel.getNowPlayingMediaDuration();
        nowPlayingMediaDuration.observe(this, new Observer<Integer>() {
            @Override
            public void onChanged(@Nullable Integer duration) {
                Log.d(MainActivity.TAG, "AudioPlayerFragment -> nowPlayingMediaDuration -> onChanged(): duration: " + duration);
                onDurationChanged(duration);
            }
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onDestroy() {
        getContext().unregisterReceiver(seekbarReceiver);
        super.onDestroy();
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    public void onDurationChanged(int duration) {
        seekBar.setMax(duration);
        seekBar.setProgress(0);
        Log.d(MainActivity.TAG, "AudioPlayerFragment -> onDurationChanged: the max of seekBar is " + seekBar.getMax());
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case (R.id.playPauseButton): {
                Log.d(MainActivity.TAG, "AudioPlayerFragment -> onCreateView -> onClick -> playPauseButton, AudiobookViewModel.getPlayerStatus() == " + AudiobookViewModel.getPlayerStatus());
                if(AudiobookViewModel.getPlayerStatus() == PlaybackStateCompat.STATE_PLAYING) {
                    mediaController.getTransportControls().pause();
                } else {
                    if(AudiobookViewModel.getNowPlayingFile().getValue() != null) {
                        mediaController.getTransportControls().play();
                    } else {
                        mActivityListener.launchFileManagerFragment();
                    }
                }
                break;
            }
            case (R.id.skipToNextButton): {
                Log.d(MainActivity.TAG, "AudioPlayerFragment -> onCreateView -> onClick -> skipToNextButton");
                mediaController.getTransportControls().skipToNext();
                break;
            }
            case (R.id.skipToPreviousButton): {
                Log.d(MainActivity.TAG, "AudioPlayerFragment -> onCreateView -> onClick -> skipToPreviousButton");
                mediaController.getTransportControls().skipToPrevious();
                break;
            }
            case (R.id.rewindBackButton): {
                Log.d(MainActivity.TAG, "AudioPlayerFragment -> onCreateView -> onClick -> rewindBackButton");
                mediaController.getTransportControls().rewind();
                break;
            }
            case (R.id.rewindForwardButton): {
                Log.d(MainActivity.TAG, "AudioPlayerFragment -> onCreateView -> onClick -> rewindForwardButton");
                mediaController.getTransportControls().fastForward();
                break;
            }

        }
    }

    //вызывается из МА при запуске плеера
    @Override
    public void onPlayMedia() {
        playPauseButton.setImageResource(R.drawable.ic_pause_black_24dp);
    }

    @Override
    public void onPauseMedia() {
        playPauseButton.setImageResource(R.drawable.ic_play_arrow_black_24dp);
    }


    public class SeekbarBroadReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(!userIsSeeking) {
                int position = intent.getIntExtra(MainActivity.playbackStatus, 0);
                seekBar.setProgress(position);
                progressTextView.setText("position is " + String.valueOf(position));
            }
        }
    }
}
