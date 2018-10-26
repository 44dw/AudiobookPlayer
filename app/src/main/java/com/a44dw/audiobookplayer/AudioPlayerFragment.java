package com.a44dw.audiobookplayer;

import android.app.Activity;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Observer;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class AudioPlayerFragment extends Fragment implements MainActivity.OnPlaybackStateChangedListener,
                                                             View.OnClickListener,
                                                             View.OnLongClickListener {
    public static final String SPEED_CHANGE_TAG = "speedTag";

    //View'ы
    ImageButton playPauseButton;
    SeekBar seekBar;
    TextView chapterTitle;
    TextView timeToEndOfChapter;
    TextView rewindBackText;
    TextView rewindForwardText;

    boolean userIsSeeking = false;
    private SeekbarBroadReceiver seekbarReceiver;
    OnIterationWithActivityListener mActivityListener;
    MediaControllerCompat mediaController;

    //LiveData objects
    public static LiveData<Chapter> nowPlayingFile;
    private static float nowSpeed = 1.0f;
    private int editedRewind;

    public AudioPlayerFragment() {}

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
        seekbarReceiver = new SeekbarBroadReceiver();
        getContext().registerReceiver(seekbarReceiver, filter);

        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.d(MainActivity.TAG, "AudioPlayerFragment -> onCreateView");
        View view = inflater.inflate(R.layout.fragment_audio_player, container, false);
        playPauseButton = view.findViewById(R.id.playPauseButton);
        LinearLayout rewindBackLayout = view.findViewById(R.id.rewindBackLayout);
        LinearLayout rewindForwardLayout = view.findViewById(R.id.rewindForwardLayout);
        rewindBackText = rewindBackLayout.findViewById(R.id.rewindBackText);
        rewindForwardText = rewindForwardLayout.findViewById(R.id.rewindForwardText);

        //получение значений дальности перемотки
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String rewindBack = prefs.getString(Preferences.KEY_REWIND_LEFT, "");
        String rewindForward = prefs.getString(Preferences.KEY_REWIND_RIGHT, "");

        int playPauseImageRecource = (AudiobookViewModel.getPlayerStatus() == PlaybackStateCompat.STATE_PLAYING
                                     ? R.drawable.ic_pause_black_24dp
                                     : R.drawable.ic_play_arrow_black_24dp);
        playPauseButton.setImageResource(playPauseImageRecource);
        playPauseButton.setOnClickListener(this);
        view.findViewById(R.id.skipToNextButton).setOnClickListener(this);
        view.findViewById(R.id.skipToPreviousButton).setOnClickListener(this);
        rewindBackLayout.setOnClickListener(this);
        rewindBackLayout.setOnLongClickListener(this);
        rewindForwardLayout.setOnClickListener(this);
        rewindForwardLayout.setOnLongClickListener(this);
        rewindBackText.setText(rewindBack);
        rewindForwardText.setText(rewindForward);
        seekBar = view.findViewById(R.id.seekBar);
        timeToEndOfChapter = view.findViewById(R.id.timeToEndOfChapter);
        chapterTitle = view.findViewById(R.id.chapterTitle);
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
                //получаем контроллер для взаимодействия с MediaSession
                if(mediaController == null) mediaController = mActivityListener.getMediaControllerCompat();
                mediaController.getTransportControls().seekTo((long) userSelectedPosition);
            }
        });

        nowPlayingFile = AudiobookViewModel.getNowPlayingFile();
        nowPlayingFile.observe(this, new Observer<Chapter>() {
            @Override
            public void onChanged(@Nullable Chapter chapter) {
                onDurationChanged((int)chapter.getDuration());
                //Вешает название трека в chapterTitle - возможно не понадобится
                //String chapterName = chapter.getChapter();
                //if((chapterName != null)&&(chapterTitle != null)) chapterTitle.setText(chapterName);
            }
        });

        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        Log.d(MainActivity.TAG, "AudioPlayerFragment -> onCreateOptionsMenu()");
        inflater.inflate(R.menu.menu_player, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.d(MainActivity.TAG, "AudioPlayerFragment -> onOptionsItemSelected()");
        int id = item.getItemId();
        switch (id) {
            case R.id.menu_speed:
                showSpeedChangeDialog();
                return true;
            case R.id.menu_volume:
                return true;
            case R.id.menu_bookmark:
                addBookmark();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void showSpeedChangeDialog() {
        SpeedDialog dialog = new SpeedDialog();

        Bundle args = new Bundle();
        args.putFloat(SpeedDialog.EXTRA_SPEED, nowSpeed);
        dialog.setArguments(args);
        dialog.setTargetFragment(this, SpeedDialog.SPEED_DIALOG_CODE);
        dialog.show(getActivity().getSupportFragmentManager().beginTransaction(), SpeedDialog.SPEED_DIALOG_TAG);
    }

    private void showRewindChangeDialog() {
        RewindDialog dialog = new RewindDialog();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String sec = (editedRewind == 1 ? prefs.getString(Preferences.KEY_REWIND_RIGHT, "")
                                        : prefs.getString(Preferences.KEY_REWIND_LEFT, ""));
        Bundle args = new Bundle();
        Log.d(MainActivity.TAG, "AudioPlayerFragment -> showRewindChangeDialog(): sec is " + sec);
        args.putString(RewindDialog.EXTRA_SEC, sec);
        dialog.setArguments(args);
        dialog.setTargetFragment(this, RewindDialog.REWIND_DIALOG_CODE);
        dialog.show(getActivity().getSupportFragmentManager().beginTransaction(), RewindDialog.REWIND_DIALOG_TAG);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
            case SpeedDialog.SPEED_DIALOG_CODE: {
                if (resultCode == Activity.RESULT_OK) {
                    float speed = data.getFloatExtra(SpeedDialog.EXTRA_SPEED, 1);
                    setSpeed(speed);
                } else if (resultCode == Activity.RESULT_CANCELED){}
                break;
            }
            case RewindDialog.REWIND_DIALOG_CODE: {
                if(resultCode == Activity.RESULT_OK) {
                    String sec = data.getStringExtra(RewindDialog.EXTRA_SEC);
                    Log.d(MainActivity.TAG, "AudioPlayerFragment -> onActivityResult() -> rewind dialog: sec is " + sec);
                    setRewind(sec);
                    if(editedRewind == 1) rewindForwardText.setText(String.valueOf(sec));
                    else rewindBackText.setText(String.valueOf(sec));
                    break;
                } else if (resultCode == Activity.RESULT_CANCELED){}
            }
        }
    }

    private void setRewind(String sec) {
        String key = (editedRewind == 1 ? Preferences.KEY_REWIND_RIGHT : Preferences.KEY_REWIND_LEFT);

        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .edit()
                .putString(key, sec)
                .apply();
    }

    private void setSpeed(float speed) {
        nowSpeed = speed;
        Bundle bundle = new Bundle();
        bundle.putFloat(SPEED_CHANGE_TAG, speed);
        if(mediaController == null) mediaController = mActivityListener.getMediaControllerCompat();
        mediaController.getTransportControls().sendCustomAction(SPEED_CHANGE_TAG, bundle);
    }

    private void addBookmark() {
        Log.d(MainActivity.TAG, "added bookmark");
        mActivityListener.onBookmarkInteraction(null);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getContext().unregisterReceiver(seekbarReceiver);
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    public void onDurationChanged(int duration) {
        seekBar.setMax(duration);
        //TODO из-за этого скорее всего прыгает сикбар
        seekBar.setProgress(0);
        Log.d(MainActivity.TAG, "AudioPlayerFragment -> onDurationChanged: the max of seekBar is " + seekBar.getMax());
    }

    @Override
    public void onClick(View v) {
        //получаем контроллер для взаимодействия с MediaSession
        if(mediaController == null) mediaController = mActivityListener.getMediaControllerCompat();
        switch (v.getId()) {
            case (R.id.playPauseButton): {
                Log.d(MainActivity.TAG, "AudioPlayerFragment -> onCreateView -> onClick -> playPauseButton, AudiobookViewModel.getPlayerStatus() == " + AudiobookViewModel.getPlayerStatus());
                if(AudiobookViewModel.getPlayerStatus() == PlaybackStateCompat.STATE_PLAYING) {
                    Log.d(MainActivity.TAG, "AudioPlayerFragment -> onCreateView -> onClick -> playPauseButton, mediaController is " + mediaController);
                    mediaController.getTransportControls().pause();
                } else {
                    if(AudiobookViewModel.getNowPlayingFile().getValue() != null) {
                        mediaController.getTransportControls().play();
                    } else {
                        mActivityListener.showFileManager(true);
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
            case (R.id.rewindBackLayout): {
                Log.d(MainActivity.TAG, "AudioPlayerFragment -> onCreateView -> onClick -> rewindBackLayout");
                mediaController.getTransportControls().rewind();
                break;
            }
            case (R.id.rewindForwardLayout): {
                Log.d(MainActivity.TAG, "AudioPlayerFragment -> onCreateView -> onClick -> rewindForwardLayout");
                mediaController.getTransportControls().fastForward();
                break;
            }
        }
    }

    @Override
    public boolean onLongClick(View v) {
        editedRewind = (v.getId() == R.id.rewindForwardLayout ? 1 : 0);
        showRewindChangeDialog();
        return true;
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
                int remainingTime = (int)nowPlayingFile.getValue().getDuration() - position;
                DateFormat df = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                seekBar.setProgress(position);
                timeToEndOfChapter.setText(df.format(remainingTime));
            }
        }
    }
}
