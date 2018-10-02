package com.a44dw.audiobookplayer;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;

public class FileManagerAdapter extends RecyclerView.Adapter {

    private File[] mData;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(View item);
    }

    public static class FileManagerViewHolder extends RecyclerView.ViewHolder {
        public TextView view;
        public LinearLayout holder;
        public FileManagerViewHolder(LinearLayout layout) {
            super(layout);
            holder = layout;
            view = layout.findViewById(R.id.fileManagerListItemText);
        }
        public void bind(File data, final OnItemClickListener listener) {
            holder.setTag(data);
            view.setText(data.toString());
            holder.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onItemClick(v);
                }
            });
        }
    }

    public FileManagerAdapter(File data, OnItemClickListener l) {
        mData = data.listFiles();
        listener = l;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        LinearLayout listItem = (LinearLayout)LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.file_manager_list_item_view, viewGroup, false);
        return new FileManagerViewHolder(listItem);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int i) {
        ((FileManagerViewHolder)viewHolder).bind(mData[i], listener);
    }

    @Override
    public int getItemCount() {
        return mData.length;
    }

    public void changeData(File newData) {
        mData = newData.listFiles();
        notifyDataSetChanged();
    }
}
