package com.a44dw.audiobookplayer;

import android.app.Activity;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.v4.app.Fragment;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import static com.a44dw.audiobookplayer.AudiobookService.currentState;

public class AudioPlayerFragment extends Fragment implements MainActivity.OnPlaybackStateChangedListener,
                                                             View.OnClickListener,
                                                             View.OnLongClickListener {
    public static final String SPEED_CHANGE_TAG = "speedTag";
    private static final String KEY_EDITED_REWIND = "keyEditedRewind";

    private ImageView playPauseButton;
    private ConstraintLayout rewindBackLayout;
    private ConstraintLayout rewindForwardLayout;
    private ImageView skipToNextButton;
    private ImageView skipToPreviousButton;
    private SeekBar seekBar;
    private TextView timeToEndOfChapter;
    private TextView rewindBackText;
    private TextView rewindForwardText;

    private SeekbarBroadReceiver seekbarReceiver;

    OnIterationWithActivityListener mActivityListener;

    MediaControllerCompat mediaController;

    AudiobookViewModel model;
    BookRepository repository;

    public LiveData<Chapter> currentChapter;

    private String rewindBack;
    private String rewindForward;
    private Integer editedRewind;

    //класс меню
    Menu mPlayerMenu;

    @Override
    public void onAttach(Context c) {
        super.onAttach(c);
        if (c instanceof OnIterationWithActivityListener) {
            mActivityListener = (OnIterationWithActivityListener) c;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        model = ViewModelProviders.of(getActivity()).get(AudiobookViewModel.class);
        repository = BookRepository.getInstance();
        currentChapter = repository.getCurrentChapter();

        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View holder = inflater.inflate(R.layout.fragment_audio_player, container, false);
        playPauseButton = holder.findViewById(R.id.playPauseButton);
        rewindBackLayout = holder.findViewById(R.id.rewindBackLayout);
        rewindForwardLayout = holder.findViewById(R.id.rewindForwardLayout);
        rewindBackText = rewindBackLayout.findViewById(R.id.rewindBackText);
        rewindForwardText = rewindForwardLayout.findViewById(R.id.rewindForwardText);
        skipToNextButton = holder.findViewById(R.id.skipToNextButton);
        skipToPreviousButton = holder.findViewById(R.id.skipToPreviousButton);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        rewindBack = prefs.getString(Preferences.KEY_REWIND_LEFT, "10");
        rewindForward = prefs.getString(Preferences.KEY_REWIND_RIGHT, "10");

        int playPauseImageRecource = (currentState == PlaybackStateCompat.STATE_PLAYING
                                     ? R.drawable.ic_pause_white_24dp
                                     : R.drawable.ic_play_arrow_white_24dp);
        playPauseButton.setImageResource(playPauseImageRecource);
        playPauseButton.setOnClickListener(this);
        skipToNextButton.setOnClickListener(this);
        skipToPreviousButton.setOnClickListener(this);
        rewindBackLayout.setOnClickListener(this);
        rewindBackLayout.setOnLongClickListener(this);
        rewindForwardLayout.setOnClickListener(this);
        rewindForwardLayout.setOnLongClickListener(this);
        rewindBackText.setText(rewindBack);
        rewindForwardText.setText(rewindForward);
        seekBar = holder.findViewById(R.id.seekBar);

        if(savedInstanceState != null) {
            editedRewind = savedInstanceState.getInt(KEY_EDITED_REWIND);
        }

        Drawable progressDrawable = seekBar.getProgressDrawable().mutate();
        progressDrawable.setColorFilter(getActivity()
                .getResources()
                .getColor(R.color.paletteOne), android.graphics.PorterDuff.Mode.SRC_IN);
        seekBar.setProgressDrawable(progressDrawable);
        seekBar.getThumb().setColorFilter(getActivity()
                .getResources()
                .getColor(R.color.paletteOne), android.graphics.PorterDuff.Mode.SRC_IN);

        timeToEndOfChapter = holder.findViewById(R.id.timeToEndOfChapter);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int userSelectedPosition = 0;
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if((fromUser)&&(currentChapter.getValue() != null)) {
                    if(currentChapter.getValue().exists()) {
                        userSelectedPosition = progress;
                        setRemainTime(progress);
                        mActivityListener.onUserSeeking(progress);
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                model.userIsSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                model.userIsSeeking = false;
                if(currentChapter.getValue() != null) {
                    if(currentChapter.getValue().exists()) {
                        if(mediaController == null) mediaController = mActivityListener.getMediaControllerCompat();
                        mediaController.getTransportControls().seekTo((long) userSelectedPosition);
                    }
                }
            }
        });

        currentChapter.observe(this, new Observer<Chapter>() {
            @Override
            public void onChanged(@Nullable Chapter chapter) {
                if(chapter != null) {
                    int progress = (repository.getNowPlayingPosition() == 0 ? (int)chapter.progress
                            : repository.getNowPlayingPosition());
                    onProgressChanged(progress, (int)chapter.duration);
                    setRemainTime(progress);
                    setInterfaceEnabled(true);
                }
            }
        });

        return holder;
    }

    @Override
    public void onStart() {
        super.onStart();

        setInterfaceEnabled(!(currentState == PlaybackStateCompat.STATE_NONE));

        seekbarReceiver = new SeekbarBroadReceiver();
        IntentFilter filter = new IntentFilter(MainActivity.SEEK_BAR_BROADCAST_NAME);
        getContext().registerReceiver(seekbarReceiver, filter);

        if(currentChapter.getValue() != null) {
            seekBar.setProgress(repository.getNowPlayingPosition());
            setRemainTime(repository.getNowPlayingPosition());
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        if(editedRewind != null) savedInstanceState.putInt(KEY_EDITED_REWIND, editedRewind);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_player, menu);

        mPlayerMenu = menu;

        setMenuEnabled(!(currentState == PlaybackStateCompat.STATE_NONE));

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.menu_speed:
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    showSpeedChangeDialog();
                } else {
                    Toast.makeText((MainActivity)mActivityListener,
                            getString(R.string.old_version),
                            Toast.LENGTH_SHORT)
                            .show();
                }
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
        args.putFloat(SpeedDialog.EXTRA_SPEED, AudiobookService.nowSpeed);
        dialog.setArguments(args);
        dialog.setTargetFragment(this, SpeedDialog.SPEED_DIALOG_CODE);
        dialog.show(getActivity().getSupportFragmentManager(), SpeedDialog.SPEED_DIALOG_TAG);
    }

    private void showRewindChangeDialog() {
        RewindDialog dialog = new RewindDialog();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String sec = (editedRewind == 1 ? prefs.getString(Preferences.KEY_REWIND_RIGHT, "")
                                        : prefs.getString(Preferences.KEY_REWIND_LEFT, ""));
        Bundle args = new Bundle();
        args.putString(RewindDialog.EXTRA_SEC, sec);
        dialog.setArguments(args);
        dialog.setTargetFragment(this, RewindDialog.REWIND_DIALOG_CODE);
        dialog.show(getActivity().getSupportFragmentManager(), RewindDialog.REWIND_DIALOG_TAG);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
            case SpeedDialog.SPEED_DIALOG_CODE: {
                if (resultCode == Activity.RESULT_OK) {
                    float speed = data.getFloatExtra(SpeedDialog.EXTRA_SPEED, 1);
                    setSpeed(speed);
                }
                break;
            }
            case RewindDialog.REWIND_DIALOG_CODE: {
                if(resultCode == Activity.RESULT_OK) {
                    String sec = data.getStringExtra(RewindDialog.EXTRA_SEC);
                    setRewind(sec);
                    if(editedRewind == 1) {
                        rewindForward = String.valueOf(sec);
                        rewindForwardText.setText(rewindForward);
                    }
                    else{
                        rewindBack = String.valueOf(sec);
                        rewindBackText.setText(rewindBack);
                    }
                    editedRewind = null;
                    break;
                }
                break;
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
        Bundle bundle = new Bundle();
        bundle.putFloat(SPEED_CHANGE_TAG, speed);
        if(mediaController == null) mediaController = mActivityListener.getMediaControllerCompat();
        mediaController.getTransportControls().sendCustomAction(SPEED_CHANGE_TAG, bundle);
    }

    private void addBookmark() {
        mActivityListener.onBookmarkInteraction(null);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mediaController != null) mediaController = null;
        getContext().unregisterReceiver(seekbarReceiver);
    }

    public void onProgressChanged(int progress, int duration) {
        seekBar.setMax(duration);
        seekBar.setProgress(progress);
    }

    @Override
    public void onClick(View v) {
        if(mediaController == null) mediaController = mActivityListener.getMediaControllerCompat();
        switch (v.getId()) {
            case (R.id.playPauseButton): {
                if(currentState == PlaybackStateCompat.STATE_PLAYING) {
                    mediaController.getTransportControls().pause();
                } else {
                    Chapter ch = repository.getCurrentChapter().getValue();
                    if((ch != null)&&(ch.exists())) {
                        mediaController.getTransportControls().play();
                    }
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
            case (R.id.rewindBackLayout): {
                if(AudiobookService.currentState == PlaybackStateCompat.STATE_PAUSED) {
                    int newPosition = seekBar.getProgress() - Integer.parseInt(rewindBack) * 1000;
                    setRemainTime(newPosition);
                    seekBar.setProgress(newPosition);
                }
                mediaController.getTransportControls().rewind();
                break;
            }
            case (R.id.rewindForwardLayout): {
                if(AudiobookService.currentState == PlaybackStateCompat.STATE_PAUSED) {
                    int newPosition = seekBar.getProgress() + Integer.parseInt(rewindForward) * 1000;
                    setRemainTime(newPosition);
                    seekBar.setProgress(newPosition);
                }
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

    @Override
    public void onPlayMedia() {
        if(playPauseButton != null) playPauseButton.setImageResource(R.drawable.ic_pause_white_24dp);
    }

    @Override
    public void onPauseMedia() {
        if(playPauseButton != null) playPauseButton.setImageResource(R.drawable.ic_play_arrow_white_24dp);
    }

    public void setInterfaceEnabled(boolean flag) {
        if(playPauseButton.isEnabled() != flag) {
            seekBar.setEnabled(flag);
            skipToPreviousButton.setEnabled(flag);
            skipToNextButton.setEnabled(flag);
            rewindBackLayout.setEnabled(flag);
            rewindForwardLayout.setEnabled(flag);
            playPauseButton.setEnabled(flag);

            if(mPlayerMenu != null) setMenuEnabled(true);
        }

        if(!flag) timeToEndOfChapter.setText("");
    }

    private void setMenuEnabled(boolean flag) {
        MenuItem speed = mPlayerMenu.findItem(R.id.menu_speed);
        MenuItem bookmark = mPlayerMenu.findItem(R.id.menu_bookmark);
        if((speed != null)&&(bookmark != null)) {
            if(speed.isEnabled() != flag) speed.setEnabled(flag);
            if(bookmark.isEnabled() != flag) bookmark.setEnabled(flag);
        }
    }

    public class SeekbarBroadReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(!model.userIsSeeking) {
                int position = intent.getIntExtra(MainActivity.PLAYBACK_STATUS, 0);
                seekBar.setProgress(position);
                if(currentChapter != null) {
                    setRemainTime(position);
                }
            }
        }
    }

    private void setRemainTime(int position) {
        Chapter ch = currentChapter.getValue();
        int remainingTime = 0;
        if(ch != null) remainingTime = (int)ch.duration - position;
        if(remainingTime < 0) remainingTime = 0;
        String time;

        if(remainingTime >= 3600*1000) time = "- " + model.longDf.format(remainingTime);
        else time = "- " + model.shortDf.format(remainingTime);

        timeToEndOfChapter.setText(time);
    }
}
