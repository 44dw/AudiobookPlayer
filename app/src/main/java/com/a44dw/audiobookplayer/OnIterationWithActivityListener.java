package com.a44dw.audiobookplayer;

import android.support.annotation.Nullable;
import android.support.v4.media.session.MediaControllerCompat;

import java.util.ArrayList;

public interface OnIterationWithActivityListener {
    void showFileManager(boolean flag);
    void showBookmarks(boolean flag, @Nullable String jsonBook);
    void showLastBooks(boolean flag);
    void updateTime(long time);
    MediaControllerCompat getMediaControllerCompat();
    void goBack();
    void onBookmarkInteraction(@Nullable Integer chNum);
}
