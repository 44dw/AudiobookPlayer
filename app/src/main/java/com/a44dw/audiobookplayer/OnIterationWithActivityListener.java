package com.a44dw.audiobookplayer;

import android.support.annotation.Nullable;
import android.support.v4.media.session.MediaControllerCompat;

public interface OnIterationWithActivityListener {
    void showFileManager(boolean flag);
    void showBookmarks(boolean flag, @Nullable Long bookId);
    void showLastBooks(boolean flag);
    void showBookScale(boolean flag);
    void showMediaPlayerFragments(boolean flag);
    void goBack();
    void onBookmarkInteraction(@Nullable Integer chNum);
    void onUserSeeking(int progress);
    boolean hasBooksInStorage();
    MediaControllerCompat getMediaControllerCompat();

    int[] getDisplayMetrics();
}
