package com.a44dw.audiobookplayer;

import android.support.v4.media.session.MediaControllerCompat;

public interface OnIterationWithActivityListener {
    void launchFileManagerFragment();
    MediaControllerCompat getMediaControllerCompat();
}
