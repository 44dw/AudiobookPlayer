package com.a44dw.audiobookplayer;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;

public class BookinfoArrayAdapter extends ArrayAdapter<Chapter> {

    private final LayoutInflater mInflater;
    private final ArrayList<Chapter> chapterList;
    private final int mRecource;

    BookinfoArrayAdapter(@NonNull Context context, int resource, @NonNull ArrayList<Chapter> list) {
        super(context, resource, 0, list);

        mInflater = LayoutInflater.from(context);
        mRecource = resource;
        chapterList = list;
    }

    @Override
    public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        return createItemView(position, convertView, parent);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        return createItemView(position, convertView, parent);
    }

    private View createItemView(int position, View convertView, ViewGroup parent) {
        final View view = mInflater.inflate(mRecource, parent, false);

        TextView text = view.findViewById(R.id.bookInfoItemText);
        Chapter ch = chapterList.get(position);
        String chapterName = ch.chapter;
        if((chapterName == null)||
                (chapterName.length() == 0))
            text.setText(new File(ch.filepath).getName());
        else text.setText(chapterName);
        return view;
    }
}
