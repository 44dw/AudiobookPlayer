package com.a44dw.audiobookplayer;

import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutionException;

public class LastbooksAdapter extends RecyclerView.Adapter {

    private ArrayList<Book> mData;
    private OnItemClickListener listener;


    LastbooksAdapter(ArrayList<Book> data, OnItemClickListener l) {
        mData = data;
        listener = l;
    }

    public interface OnItemClickListener {
        void onItemClick(View item);
        void onItemLongClick(View item);
    }

    public static class LastbookViewHolder extends RecyclerView.ViewHolder {
        public TextView name;
        public TextView percent;
        public TextView duration;
        public TextView progress;
        public ImageView image;
        ProgressBar scale;
        public ConstraintLayout holder;
        public ImageView bookmark;
        DateFormat df;
        LastbookViewHolder(ConstraintLayout layout) {
            super(layout);
            holder = layout;
            name = layout.findViewById(R.id.lastbooksName);
            percent = layout.findViewById(R.id.lastbooksPercent);
            image = layout.findViewById(R.id.lastbooksImage);
            scale = layout.findViewById(R.id.lastbookProgressBar);
            duration = layout.findViewById(R.id.lastbooksDuration);
            progress = layout.findViewById(R.id.lastbooksProgress);
            bookmark = layout.findViewById(R.id.lastbooksBookmark);
            df = new SimpleDateFormat("HH:mm", Locale.US);
            df.setTimeZone(TimeZone.getTimeZone("GMT"));
        }
        private void bind(Book data, final LastbooksAdapter.OnItemClickListener listener) {
            //прикрепляем в качестве тэга Book - может быть тяжело
            holder.setTag(data);
            name.setText(data.publicName);
            long durationValue = data.bookDuration;
            long progressValue = data.bookProgress;
            int percentValue = data.percent;
            progress.setText(df.format(progressValue));
            String s = "/" + df.format(durationValue) + " ч.";
            duration.setText(s);
            s = "(" + percentValue + "%)";
            percent.setText(s);
            if(data.cover != null) image.setImageBitmap(data.cover);
            scale.setMax((int) durationValue);
            scale.setProgress((int) progressValue);

            if(hasBookmarks(data))bookmark.setVisibility(View.VISIBLE);

            holder.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onItemClick(v);
                }
            });
            holder.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    listener.onItemLongClick(v);
                    return true;
                }
            });
        }

        private Boolean hasBookmarks(Book data) {
            BookRepository.BookmarkDaoGetByBookId bookmarkDaoGetByBookId = new BookRepository.BookmarkDaoGetByBookId();
            bookmarkDaoGetByBookId.execute(data.bookId);
            try {
                ArrayList<Bookmark> bookmarks = bookmarkDaoGetByBookId.get();
                if(bookmarks.size() > 0) return true;
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
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
}
