package com.a44dw.audiobookplayer;

import android.arch.lifecycle.Observer;
import android.content.Context;
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

import static com.a44dw.audiobookplayer.AudiobookViewModel.playerHandler;

public class AudioPlayerFragment extends Fragment implements AudioPlayerHandler.OnAudioPlayerIterationWithFragmentListener,
                                                             View.OnClickListener {

    static ImageButton playPauseButton;
    SeekBar seekBar;
    TextView progressTextView;
    boolean userIsSeeking = false;
    OnIterationWithActivityListener mActivityListener;


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
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.d(MainActivity.TAG, "AudioPlayerFragment -> onCreateView");
        View view = inflater.inflate(R.layout.fragment_audio_player, container, false);
        playPauseButton = view.findViewById(R.id.playPauseButton);
        int playPauseImageRecource = R.drawable.ic_play_arrow_black_24dp;
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
                //playerHandler.seekTo(userSelectedPosition);
            }
        });
        //onDurationChanged(0);
        onDurationChanged(AudiobookViewModel.nowPlayingMediaDuration);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    @Override
    public void onLaunchPlayer() {
        Log.d(MainActivity.TAG, "AudioPlayerFragment -> onLaunchPlayer");
        playPauseButton.setImageResource(R.drawable.ic_pause_black_24dp);
    }

    @Override
    public void onStopPlayer() {
        Log.d(MainActivity.TAG, "AudioPlayerFragment -> onStopPlayer");
        playPauseButton.setImageResource(R.drawable.ic_play_arrow_black_24dp);
    }

    @Override
    public void onDurationChanged(int duration) {
        Log.d(MainActivity.TAG, "AudioPlayerFragment -> onDurationChanged: set max to " + duration);
        seekBar.setMax(duration);
        seekBar.setProgress(0);
        Log.d(MainActivity.TAG, "AudioPlayerFragment -> onDurationChanged: the max of seekBar is " + seekBar.getMax());
    }

    @Override
    public void onPositionChanged(int position) {
        if(!userIsSeeking) {
            Log.d(MainActivity.TAG, "AudioPlayerFragment -> onPositionChanged: set position to " + position);
            //if(seekBar.getMax() == 0) seekBar.setMax(AudiobookViewModel.nowPlayingMediaDuration);
            Log.d(MainActivity.TAG, "AudioPlayerFragment -> onPositionChanged: getMax: " + seekBar.getMax());
            seekBar.setProgress(position);
            progressTextView.setText("position is " + String.valueOf(position));
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case (R.id.playPauseButton): {
                Log.d(MainActivity.TAG, "AudioPlayerFragment -> onCreateView -> onClick -> playPauseButton");
                if(MainActivity.nowPlayingFile.getValue() != null) {
                    mActivityListener.play();
                } else {
                    mActivityListener.launchFileManagerFragment();
                }
                break;
            }
            case (R.id.skipToNextButton): {
                Log.d(MainActivity.TAG, "AudioPlayerFragment -> onCreateView -> onClick -> skipToNextButton");
                //playerHandler.skipToNext();
                break;
            }
            case (R.id.skipToPreviousButton): {
                Log.d(MainActivity.TAG, "AudioPlayerFragment -> onCreateView -> onClick -> skipToPreviousButton");
                //playerHandler.skipToPrevious();
                break;
            }
            case (R.id.rewindBackButton): {
                Log.d(MainActivity.TAG, "AudioPlayerFragment -> onCreateView -> onClick -> rewindBackButton");
                //playerHandler.rewindBack();
                break;
            }
            case (R.id.rewindForwardButton): {
                Log.d(MainActivity.TAG, "AudioPlayerFragment -> onCreateView -> onClick -> rewindForwardButton");
                //playerHandler.rewindForward();
                break;
            }

        }
    }
}
