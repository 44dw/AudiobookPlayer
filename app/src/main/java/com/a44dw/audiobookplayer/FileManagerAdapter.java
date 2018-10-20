package com.a44dw.audiobookplayer;

import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.net.URLConnection;
import java.util.ArrayList;

public class FileManagerAdapter extends RecyclerView.Adapter {

    private ArrayList<File> mData;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(View item);
    }

    private static class FileManagerViewHolder extends RecyclerView.ViewHolder {
        private TextView textView;
        private ImageView imageView;
        private ConstraintLayout holder;
        private FileManagerViewHolder(ConstraintLayout layout) {
            super(layout);
            holder = layout;
            textView = layout.findViewById(R.id.fileManagerListItemText);
            imageView = layout.findViewById(R.id.fileManagerListItemImage);
        }
        private void bind(File data, final OnItemClickListener listener) {
                holder.setTag(R.string.key_path, data.getAbsolutePath());
                textView.setText(data.getName());
                if(data.isDirectory()) {
                    if(FileManagerHandler.containsMedia(data)) {
                        imageView.setImageResource(R.drawable.ic_folder_black_24dp);
                        holder.setTag(R.string.key_is_media, FileManagerHandler.HAS_MEDIA);
                    } else {
                        imageView.setImageResource(R.drawable.ic_folder_open_black_24dp);
                        holder.setTag(R.string.key_is_media, FileManagerHandler.NO_MEDIA);
                    }
                }
                else {
                    imageView.setImageResource(R.drawable.ic_music_note_black_24dp);
                    holder.setTag(R.string.key_is_media, FileManagerHandler.MEDIA_ITSELF);
                }
                holder.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        listener.onItemClick(v);
                    }
            });
        }
    }

    public FileManagerAdapter(ArrayList<File> data, OnItemClickListener l) {
        mData = data;
        listener = l;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        ConstraintLayout listItem = (ConstraintLayout)LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.file_manager_list_item_view, viewGroup, false);
        return new FileManagerViewHolder(listItem);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int i) {
        ((FileManagerViewHolder)viewHolder).bind(mData.get(i), listener);
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }

    public void changeData(ArrayList<File> newData) {
        Log.d(MainActivity.TAG, "FileManagerAdapter -> changeData " + newData.toString());
        mData = newData;
        notifyDataSetChanged();
    }
}
