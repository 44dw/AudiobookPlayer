package com.a44dw.audiobookplayer;

import android.util.Log;

class Bookmark {

    //private String pathToFile;
    private String pathToFile;
    private String name;
    private long time;

    Bookmark(Chapter ch, long t) {
        name = "Закладка";
        pathToFile = ch.getFile().getAbsolutePath();
        time = t;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getTime() {
        return time;
    }

    public String getPathToFile() {return pathToFile;}
}