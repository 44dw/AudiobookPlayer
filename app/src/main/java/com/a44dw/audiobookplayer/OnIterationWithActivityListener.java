package com.a44dw.audiobookplayer;

import android.support.v4.media.session.MediaControllerCompat;

public interface OnIterationWithActivityListener {
    void showFileManager(boolean flag);
    void showBookmarks(boolean flag);
    void showLastBooks(boolean flag);
    MediaControllerCompat getMediaControllerCompat();
}
