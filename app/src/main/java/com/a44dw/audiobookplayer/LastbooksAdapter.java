package com.a44dw.audiobookplayer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LastbooksAdapter extends RecyclerView.Adapter {

    private ArrayList<String> mData;
    private OnItemClickListener listener;
    private static final String REGEX_GET_NAME = "\"publicName\":\"([\\w\\d\\s]*)";
    private static final String REGEX_GET_PERCENT = "\"percent\":(\\d*)";
    private static final String REGEX_GET_FILE = "\"path\":\"([\\w\\d\\s\\/\\.]*)";
    private static final String REGEX_GET_DURATION = "\"bookDuration\":([\\d]*)";
    private static final String REGEX_GET_PROGRESS = "\"bookProgress\":([\\d]*)";
    private static final String REGEX_GET_DONE = "\"done\":false";
    private static final String REGEX_GET_BOOKMARK = "\"bookmarks\":\\[(.*?)\\]";

    public LastbooksAdapter(ArrayList<String> data, OnItemClickListener l) {
        mData = data;
        listener = l;
    }

    public interface OnItemClickListener {
        void onItemClick(View item);
    }

    public static class LastbookViewHolder extends RecyclerView.ViewHolder {
        public TextView name;
        public TextView percent;
        public TextView duration;
        public TextView progress;
        public ImageView image;
        public ProgressBar scale;
        public ConstraintLayout holder;
        public ImageView bookmark;
        DateFormat df = new SimpleDateFormat("HH:mm", Locale.getDefault());
        public LastbookViewHolder(ConstraintLayout layout) {
            super(layout);
            holder = layout;
            name = layout.findViewById(R.id.lastbooksName);
            percent = layout.findViewById(R.id.lastbooksPercent);
            image = layout.findViewById(R.id.lastbooksImage);
            scale = layout.findViewById(R.id.lastbookProgressBar);
            duration = layout.findViewById(R.id.lastbooksDuration);
            progress = layout.findViewById(R.id.lastbooksProgress);
            bookmark = layout.findViewById(R.id.lastbooksBookmark);
        }
        public void bind(String data, final LastbooksAdapter.OnItemClickListener listener) {
            holder.setTag(data);
            name.setText(getName(data));
            long durationValue = getDuration(data);
            long progressValue = getProgress(data);
            int percentValue = getPercent(data);
            Bitmap img = getDrawable(data);
            progress.setText(df.format(progressValue));
            duration.setText("/" + df.format(durationValue) + " ч.");
            if (isDone(data)) holder.setBackgroundColor(ContextCompat.getColor(((LastBooksFragment)listener).getActivity(), R.color.mocassin));
            percent.setText("(" + percentValue + "%)");
            if(img != null) image.setImageBitmap(img);
            scale.setMax((int) durationValue);
            scale.setProgress((int) progressValue);
            bookmark.setVisibility(View.VISIBLE);

            holder.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onItemClick(v);
                }
            });
        }

        private int getPercent(String data) {
            String result = "";
            Pattern pattern = Pattern.compile(REGEX_GET_PERCENT);
            Matcher matcher = pattern.matcher(data);
            if(matcher.find()) result = matcher.group(1);
            Log.d(MainActivity.TAG, "LastbooksAdapter -> getPercent() result is: " + result);
            return Integer.parseInt(result);
        }

        private String getName(String data) {
            String result = "";
            Pattern pattern = Pattern.compile(REGEX_GET_NAME);
            Matcher matcher = pattern.matcher(data);
            if(matcher.find()) result = matcher.group(1);
            Log.d(MainActivity.TAG, "LastbooksAdapter -> getName() result is: " + result);
            return result;
        }

        private long getProgress(String data) {
            String result = "";
            Pattern pattern = Pattern.compile(REGEX_GET_PROGRESS);
            Matcher matcher = pattern.matcher(data);
            if(matcher.find()) result = matcher.group(1);
            Log.d(MainActivity.TAG, "LastbooksAdapter -> getProgress() result is: " + result);
            return Long.parseLong(result);
        }

        private long getDuration(String data) {
            String result = "";
            Pattern pattern = Pattern.compile(REGEX_GET_DURATION);
            Matcher matcher = pattern.matcher(data);
            if(matcher.find()) result = matcher.group(1);
            Log.d(MainActivity.TAG, "LastbooksAdapter -> getDuration() result is: " + result);
            return Long.parseLong(result);
        }

        private Bitmap getDrawable(String data) {
            String pathToFile = "";
            Bitmap result = null;
            Pattern pattern = Pattern.compile(REGEX_GET_FILE, Pattern.UNICODE_CASE);
            Matcher matcher = pattern.matcher(data);
            if(matcher.find()) pathToFile = matcher.group(1);
            Log.d(MainActivity.TAG, "LastbooksAdapter -> getDrawable() pathToFile is: " + pathToFile);
            //создаём файл только чтобы проверить
            File file = new File(pathToFile);
            if(file.exists()) {
                Log.d(MainActivity.TAG, "LastbooksAdapter -> getDrawable() file is exists!");
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                retriever.setDataSource(pathToFile);
                byte[] art = retriever.getEmbeddedPicture();
                if(art != null){
                    result = BitmapFactory.decodeByteArray(art, 0, art.length);
                }
            }
            return result;
        }

        private Boolean isDone(String data) {
            Pattern pattern = Pattern.compile(REGEX_GET_DONE);
            Matcher matcher = pattern.matcher(data);
            if(matcher.find()) return false;
            return true;
        }
        private Boolean hasBookmarks(String data) {
            Pattern pattern = Pattern.compile(REGEX_GET_BOOKMARK);
            Matcher matcher = pattern.matcher(data);
            if(matcher.find()) {
                if(matcher.group(1).length() > 0) return true;
            }
            return false;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        ConstraintLayout listItem = (ConstraintLayout) LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.last_books_list_item_view, viewGroup, false);
        return new LastbooksAdapter.LastbookViewHolder(listItem);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int i) {
        ((LastbooksAdapter.LastbookViewHolder)viewHolder).bind(mData.get(i), listener);
    }

    @Override
    public int getItemCount() {return mData.size();}

    public void changeData(ArrayList<String> newData) {
        mData = newData;
        notifyDataSetChanged();
    }
}
