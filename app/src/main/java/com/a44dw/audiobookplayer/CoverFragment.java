package com.a44dw.audiobookplayer;


import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Observer;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

public class CoverFragment extends Fragment {

    ImageView cover;
    public static LiveData<Chapter> currentChapter;
    BookRepository repository;

    public CoverFragment() {}

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        repository = BookRepository.getInstance();

        currentChapter = repository.getCurrentChapter();
        currentChapter.observe(this, new Observer<Chapter>() {
            @Override
            public void onChanged(@Nullable Chapter chapter) {
                if((chapter!= null)&&(chapter.exists())) {
                    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                    retriever.setDataSource(chapter.filepath);
                    byte[] art = retriever.getEmbeddedPicture();
                    if (art != null) {
                        Bitmap bitmap = BitmapFactory.decodeByteArray(art, 0, art.length);
                        cover.setImageBitmap(bitmap);
                    } else {
                        cover.setImageResource(android.R.color.transparent);
                    }
                    retriever.release();
                }
            }
        });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        cover = (ImageView) inflater.inflate(R.layout.fragment_cover, container, false);

        if(currentChapter != null) {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();

            if((currentChapter.getValue() != null)&&(currentChapter.getValue().exists())) {
                retriever.setDataSource(currentChapter.getValue().filepath);
                byte[] art = retriever.getEmbeddedPicture();
                if(art != null) {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(art, 0, art.length);
                    cover.setImageBitmap(bitmap);
                } else {
                    cover.setImageResource(android.R.color.transparent);
                }
            }
        }

        return cover;
    }
}
