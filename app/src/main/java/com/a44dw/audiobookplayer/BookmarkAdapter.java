package com.a44dw.audiobookplayer;

import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;

public class BookmarkAdapter extends RecyclerView.Adapter  {

    private ArrayList<Bookmark> mData;
    private OnItemClickListener listener;

    public BookmarkAdapter(ArrayList<Bookmark> data, OnItemClickListener l) {
        mData = data;
        listener = l;
    }

    public interface OnItemClickListener {
        void onItemClick(View item);
        void onItemLongClick(View item);
    }

    public static class BookmarkViewHolder extends RecyclerView.ViewHolder {
        public TextView name;
        public TextView path;
        public TextView time;
        public ConstraintLayout holder;
        DateFormat df = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        public BookmarkViewHolder(ConstraintLayout layout) {
            super(layout);
            holder = layout;
            name = layout.findViewById(R.id.bookmarkListItemText);
            path = layout.findViewById(R.id.bookmarkListItemPath);
            time = layout.findViewById(R.id.bookmarkTime);
        }
        public void bind(Bookmark data, final BookmarkAdapter.OnItemClickListener listener) {
            String filename = new File(data.getPathToFile()).getName();
            holder.setTag(data);
            name.setText(data.getName());
            path.setText(filename);
            time.setText(df.format(data.getTime()));
            View.OnClickListener oklistener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onItemClick(v);
                }
            };
            holder.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    listener.onItemLongClick(v);
                    return true;
                }
            });
            holder.setOnClickListener(oklistener);
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        ConstraintLayout listItem = (ConstraintLayout) LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.bookmark_list_item_view, viewGroup, false);
        return new BookmarkViewHolder(listItem);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int i) {
        ((BookmarkViewHolder)viewHolder).bind(mData.get(i), listener);
    }

    @Override
    public int getItemCount() {return mData.size();}

    public void changeData(ArrayList<Bookmark> newData) {
        mData = newData;
        notifyDataSetChanged();
    }
}
