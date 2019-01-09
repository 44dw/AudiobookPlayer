package com.a44dw.audiobookplayer;

import android.arch.lifecycle.ViewModel;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

public class AudiobookViewModel extends ViewModel {

    private int fragmentStatus;
    public boolean screenRotate = false;
    public boolean permissions;

    public DateFormat longDf;
    public DateFormat shortDf;

    public AudiobookViewModel() {
        super();
        longDf = new SimpleDateFormat("HH:mm:ss", Locale.US);
        longDf.setTimeZone(TimeZone.getTimeZone("GMT"));
        shortDf = new SimpleDateFormat("mm:ss", Locale.US);
        shortDf.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    public int getFragmentStatus() {
        return fragmentStatus;
    }

    public void setFragmentStatus(int fragmentStatus) {
        this.fragmentStatus = fragmentStatus;
    }

    FileManagerHandler fileManagerHandler;

    public boolean userIsSeeking = false;
}
