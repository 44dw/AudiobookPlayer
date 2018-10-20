package com.a44dw.audiobookplayer;


import android.arch.lifecycle.LiveData;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class MiniPlayerFragment extends Fragment implements View.OnClickListener  {

    ImageButton playPauseButton;
    TextView timeToEndOfChapter;

    private TimeBroadReceiver timeReceiver;
    MediaControllerCompat mediaController;
    OnIterationWithActivityListener mActivityListener;

    public MiniPlayerFragment() {}

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

        //регистрируем ресивер для получения информации о прогрессе песни
        IntentFilter filter = new IntentFilter(MainActivity.seekBarBroadcastName);
        timeReceiver = new TimeBroadReceiver();
        getContext().registerReceiver(timeReceiver, filter);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_player_mini, container, false);
        playPauseButton = view.findViewById(R.id.playPauseButton);
        playPauseButton.setOnClickListener(this);
        timeToEndOfChapter = view.findViewById(R.id.timeToEndOfChapter);
        view.findViewById(R.id.skipToNextButton).setOnClickListener(this);
        view.findViewById(R.id.skipToPreviousButton).setOnClickListener(this);
        view.findViewById(R.id.rewindBackButton).setOnClickListener(this);
        view.findViewById(R.id.rewindForwardButton).setOnClickListener(this);
        return view;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getContext().unregisterReceiver(timeReceiver);
    }

    @Override
    public void onClick(View v) {
        //получаем контроллер для взаимодействия с MediaSession
        if(mediaController == null) mediaController = mActivityListener.getMediaControllerCompat();
        switch (v.getId()) {
            case (R.id.playPauseButton): {
                if(AudiobookViewModel.getPlayerStatus() == PlaybackStateCompat.STATE_PLAYING) {
                    mediaController.getTransportControls().pause();
                } else {
                    mediaController.getTransportControls().play();
                }
                break;
            }
            case (R.id.skipToNextButton): {
                mediaController.getTransportControls().skipToNext();
                break;
            }
            case (R.id.skipToPreviousButton): {
                mediaController.getTransportControls().skipToPrevious();
                break;
            }
            case (R.id.rewindBackButton): {
                mediaController.getTransportControls().rewind();
                break;
            }
            case (R.id.rewindForwardButton): {
                mediaController.getTransportControls().fastForward();
                break;
            }
        }
    }

    public class TimeBroadReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int position = intent.getIntExtra(MainActivity.playbackStatus, 0);
            int remainingTime = (int)AudiobookViewModel.getNowPlayingFile().getValue().getDuration() - position;
            DateFormat df = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            timeToEndOfChapter.setText(df.format(remainingTime));
        }
    }
}
