package com.a44dw.audiobookplayer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BookmarkFragment extends Fragment implements BookmarkAdapter.OnItemClickListener,
                                                          MainActivity.OnBackPressedListener {

    public static final String BOOKMARK_BUNDLE_KEY = "bookmarkBundleKey";
    public static final String BOOKMARK_TIME_TAG = "bookmarkTime";

    private View view;
    private RecyclerView bookmarkRecyclerView;
    private BookmarkAdapter adapter;
    LinearLayoutManager manager;
    OnIterationWithActivityListener activityListener;
    private ActionMode actionMode;
    private ArrayList<Bookmark> bookmarks;
    private Bookmark editedBookmark;
    private View editedView;

    public BookmarkFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_bookmark, container, false);
        bookmarkRecyclerView = view.findViewById(R.id.bookmarkRecyclerView);
        Log.d(MainActivity.TAG, "BookmarkFragment -> onCreateView");
        activityListener = (OnIterationWithActivityListener) getActivity();

        Bundle args = getArguments();
        bookmarks = (args == null ? getBookmarks() : getBookmarks(args.getString(BOOKMARK_BUNDLE_KEY)));
        updateBookmarks();
        setHasOptionsMenu(true);
        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        Log.d(MainActivity.TAG, "BookmarkFragment -> onCreateOptionsMenu");
        inflater.inflate(R.menu.menu_bookmarks, menu);
        if(AudiobookViewModel.getPlayerStatus() == PlaybackStateCompat.STATE_PLAYING ||
                AudiobookViewModel.getPlayerStatus() == PlaybackStateCompat.STATE_PAUSED) {
            MenuItem item = menu.findItem(R.id.menu_play);
            item.setVisible(true);
            item.setIcon(
                    AudiobookViewModel.getPlayerStatus() == PlaybackStateCompat.STATE_PLAYING ?
                            R.drawable.ic_pause_white_24dp :
                            R.drawable.ic_play_arrow_white_24dp
            );
        }
        super.onCreateOptionsMenu(menu, inflater);
    }


    public void updateBookmarks() {
        Log.d(MainActivity.TAG, "BookmarkFragment -> updateBookmarks");
        manager = new LinearLayoutManager(getContext());
        adapter = new BookmarkAdapter(bookmarks, this);
        bookmarkRecyclerView.setLayoutManager(manager);
        bookmarkRecyclerView.setAdapter(adapter);
        //установка разделителя
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(bookmarkRecyclerView.getContext(),
                manager.getOrientation());
        bookmarkRecyclerView.addItemDecoration(dividerItemDecoration);
    }

    private ArrayList<Bookmark> getBookmarks() {
        Book book = AudiobookViewModel.getPlaylist();
        ArrayList<Chapter> chapters;
        chapters = ((book != null) ? book.getChapters() : new ArrayList<Chapter>());
        ArrayList<Bookmark> bookmarkList = new ArrayList<>();

        for(Chapter ch : chapters) {
            if(ch.getBookmarks() != null) {
                if(ch.getBookmarks().size() != 0) {
                    bookmarkList.addAll(ch.getBookmarks());
                }
            }
        }
        return bookmarkList;
    }

    private ArrayList<Bookmark> getBookmarks(String jsonBook) {
        ArrayList<Bookmark> bookmarkList = new ArrayList<>();

        GsonBuilder builder = new GsonBuilder();
        Gson gson = builder.create();
        Pattern pattern = Pattern.compile(LastbooksAdapter.REGEX_GET_BOOKMARK);
        Matcher matcher = pattern.matcher(jsonBook);
        if(matcher.find()) bookmarkList.add(gson.fromJson(matcher.group(1), Bookmark.class));

        return bookmarkList;
    }

    private void openBookmark(Bookmark b) {
        int numInPl = AudiobookViewModel.getPlaylist().findInChapters(b.getPathToFile());
        if(numInPl == -1) {
            File file = new File(b.getPathToFile());
            AudiobookViewModel.updateNowPlayingFile(new Chapter(file));
            activityListener.updateTime(b.getTime());
        } else {
            Chapter ch = AudiobookViewModel.getPlaylist().getChapters().get(numInPl);
            ch.setProgress(b.getTime());
            AudiobookViewModel.updateNowPlayingFile(ch);
        }
        activityListener.showBookmarks(false, null);
    }

    @Override
    public void onItemClick(View item) {
        Bookmark bookmark = (Bookmark)item.getTag();
        openBookmark(bookmark);
    }

    @Override
    public void onItemLongClick(View item) {
        editedView = item;
        editedView.setBackgroundColor(getActivity().getResources().getColor(R.color.pbBackgroundIncative));
        editedBookmark = (Bookmark)item.getTag();
        if(actionMode == null) {
            actionMode = ((AppCompatActivity)getContext()).startSupportActionMode(actionModeCallbacks);
        } else actionMode.finish();
    }

    private ActionMode.Callback actionModeCallbacks = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.contextmenu_bookmark, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            Log.d(MainActivity.TAG, "BookmarkFragment -> actionModeCallbacks -> onActionItemClicked()");
            switch (item.getItemId()) {
                case (R.id.contextmenu_bookmarks_change): {
                    showBookmarkRenameDialog();
                    break;
                }
                case (R.id.contextmenu_bookmarks_delete): {
                    deleteBookmark();
                    break;
                }
            }
            mode.finish();
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            Log.d(MainActivity.TAG, "BookmarkFragment -> actionModeCallbacks -> onDestroyActionMode()");
            actionMode = null;
            if(editedView != null) editedView.setBackground(null);
        }
    };

    private void showBookmarkRenameDialog() {
        RenameDialog dialog = new RenameDialog();
        dialog.setTargetFragment(this, RenameDialog.RENAME_DIALOG_CODE);
        dialog.show(getActivity().getSupportFragmentManager().beginTransaction(), RenameDialog.RENAME_DIALOG_TAG);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
            case RenameDialog.RENAME_DIALOG_CODE: {
                if (resultCode == Activity.RESULT_OK) {
                    Log.d(MainActivity.TAG, "BookmarkFragment -> onActivityResult -> RESULT_OK");
                    String newName = data.getStringExtra(RenameDialog.EXTRA_NAME);
                    editBookmarkName(newName);
                } else if (resultCode == Activity.RESULT_CANCELED){}

                break;
            }
        }
    }

    private void editBookmarkName(String name) {
        if(editedBookmark != null) {
            editedBookmark.setName(name);
            adapter.notifyDataSetChanged();
        }
    }
    private void deleteBookmark() {
        if(editedBookmark != null) {
            Log.d(MainActivity.TAG, "BookmarkFragment -> deleteBookmark(): " + editedBookmark.getName() + ", " + editedBookmark.getTime());
            int numInPl = AudiobookViewModel.getPlaylist().findInChapters(editedBookmark.getPathToFile());
            if(numInPl != -1) {
                Chapter ch = AudiobookViewModel.getPlaylist().getChapters().get(numInPl);
                //приходится удалять из двух мест, потому что иначе notifyDataSetChanged() не работает
                ch.deleteBookmark(editedBookmark);
                bookmarks.remove(editedBookmark);
                if(ch.getBookmarks().size() == 0) activityListener.onBookmarkInteraction(numInPl);
                adapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    public void onBackPressed() {
        activityListener.goBack();
    }
}
