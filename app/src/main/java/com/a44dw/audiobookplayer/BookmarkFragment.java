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
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

import static com.a44dw.audiobookplayer.AudiobookService.currentState;
import static com.a44dw.audiobookplayer.BookRepository.database;
import static com.a44dw.audiobookplayer.MainActivity.exec;

public class BookmarkFragment extends Fragment implements BookmarkAdapter.OnItemClickListener,
                                                          MainActivity.OnBackPressedListener {

    public static final String BOOKMARK_BUNDLE_KEY = "bookmarkBundleKey";
    public static final String BOOKMARK_TIME_TAG = "bookmarkTime";

    private View editedView;
    private RecyclerView bookmarkRecyclerView;

    private BookmarkAdapter adapter;
    LinearLayoutManager manager;
    OnIterationWithActivityListener activityListener;
    private ActionMode actionMode;

    private ArrayList<Bookmark> bookmarks;
    private Bookmark editedBookmark;
    private BookRepository repository;

    public BookmarkFragment() {}

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repository = BookRepository.getInstance();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View holder = inflater.inflate(R.layout.fragment_bookmark, container, false);
        bookmarkRecyclerView = holder.findViewById(R.id.bookmarkRecyclerView);
        activityListener = (OnIterationWithActivityListener) getActivity();
        Bundle args = getArguments();
        bookmarks = (args == null ? getBookmarks(repository.getBook().bookId) : getBookmarks(args.getLong(BOOKMARK_BUNDLE_KEY)));
        updateBookmarks();

        if(savedInstanceState != null) {
            Long bookmarkId = savedInstanceState.getLong(BOOKMARK_BUNDLE_KEY);
            BookRepository.BookmarkDaoGetById bookmarkDaoGetById = new BookRepository.BookmarkDaoGetById();
            bookmarkDaoGetById.execute(bookmarkId);
            try {
                editedBookmark = bookmarkDaoGetById.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
        setHasOptionsMenu(true);
        return holder;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_bookmarks, menu);
        if(currentState == PlaybackStateCompat.STATE_PLAYING ||
                currentState == PlaybackStateCompat.STATE_PAUSED) {
            MenuItem item = menu.findItem(R.id.menu_play);
            item.setVisible(true);
            item.setIcon(
                    currentState == PlaybackStateCompat.STATE_PLAYING ?
                            R.drawable.ic_pause_white_24dp :
                            R.drawable.ic_play_arrow_white_24dp
            );
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if(editedBookmark != null) {
            long bookmarkId = editedBookmark.bookmarkId;
            outState.putLong(BOOKMARK_BUNDLE_KEY, bookmarkId);
        }
        super.onSaveInstanceState(outState);
    }

    public void updateBookmarks() {
        manager = new LinearLayoutManager(getContext());
        adapter = new BookmarkAdapter(bookmarks, this);
        bookmarkRecyclerView.setLayoutManager(manager);
        bookmarkRecyclerView.setAdapter(adapter);
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(bookmarkRecyclerView.getContext(),
                manager.getOrientation());
        bookmarkRecyclerView.addItemDecoration(dividerItemDecoration);
    }

    private ArrayList<Bookmark> getBookmarks(Long bookId) {

        ArrayList<Bookmark> bookmarkList = new ArrayList<>();

        BookRepository.BookmarkDaoGetByBookId bookmarkDaoGetByBookId = new BookRepository.BookmarkDaoGetByBookId();
        bookmarkDaoGetByBookId.execute(bookId);

        try {
            bookmarkList = bookmarkDaoGetByBookId.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        return bookmarkList;
    }

    @Override
    public void onItemClick(View item) {
        if(actionMode != null) return;

        Bookmark bookmark = (Bookmark)item.getTag();
        repository.openBookmark(bookmark);
        activityListener.showBookmarks(false, null);
    }

    @Override
    public void onItemLongClick(View item) {
        if(actionMode == null) {
            editedView = item;
            editedView.setBackgroundColor(getActivity().getResources().getColor(R.color.mocassin));
            editedBookmark = (Bookmark)item.getTag();
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
            actionMode = null;
            if(editedView != null) editedView.setBackground(null);
        }
    };

    private void showBookmarkRenameDialog() {
        RenameDialog dialog = new RenameDialog();
        dialog.setTargetFragment(this, RenameDialog.RENAME_DIALOG_CODE);
        dialog.show(getActivity().getSupportFragmentManager(), RenameDialog.RENAME_DIALOG_TAG);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
            case RenameDialog.RENAME_DIALOG_CODE: {
                if (resultCode == Activity.RESULT_OK) {
                    String newName = data.getStringExtra(RenameDialog.EXTRA_NAME);
                    editBookmarkName(newName);
                }

                break;
            }
        }
    }

    private void editBookmarkName(String name) {
        if(editedBookmark != null) {
            editedBookmark.name = name;
            exec.execute(new Runnable() {
                @Override
                public void run() {
                    if(editedBookmark.bookmarkId == 0) {
                        database.bookmarkDao().updateNameByTime(editedBookmark.name, editedBookmark.time, editedBookmark.chId);
                    } else database.bookmarkDao().update(editedBookmark);
                }
            });
            adapter.notifyDataSetChanged();
        }
    }

    private void deleteBookmark() {
        if(editedBookmark != null) {
            int numInPl = repository.getBook().findInChapters(editedBookmark.pathToFile);
            if(numInPl != -1) {
                Chapter ch = repository.getBook().chapters.get(numInPl);
                exec.execute(new Runnable() {
                    @Override
                    public void run() {
                        if(editedBookmark.bookmarkId == 0) {
                            database.bookmarkDao().deleteByTime(editedBookmark.time, editedBookmark.chId);
                        } else database.bookmarkDao().delete(editedBookmark);
                    }
                });
                ch.deleteBookmark(editedBookmark);
                bookmarks.remove(editedBookmark);
                if((ch.bookmarks != null)&&(ch.bookmarks.size() == 0))
                    activityListener.onBookmarkInteraction(numInPl);
                adapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    public void onBackPressed() {
        activityListener.goBack();
    }
}
