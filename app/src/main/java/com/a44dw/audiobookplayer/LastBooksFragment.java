package com.a44dw.audiobookplayer;


import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.AsyncTask;
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
import android.widget.ProgressBar;
import android.widget.Toast;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutionException;

import static com.a44dw.audiobookplayer.AudiobookService.currentState;
import static com.a44dw.audiobookplayer.MainActivity.exec;

public class LastBooksFragment extends Fragment implements LastbooksAdapter.OnItemClickListener,
                                                           MainActivity.OnBackPressedListener {

    public static final String editedItemKey = "editedItemKey";

    private RecyclerView lastbooksRecyclerView;
    private LastbooksAdapter adapter;
    private ArrayList<Book> books;
    LinearLayoutManager manager;
    OnIterationWithActivityListener activityListener;
    private View editedView;
    private ArrayList<Book> editedBook;
    private ActionMode actionMode;
    private MenuItem launchFileManager;
    ProgressBar progressBar;

    BookRepository repository;
    static BookplayerDatabase database;

    public LastBooksFragment() {}

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repository = BookRepository.getInstance();
        database = repository.getDatabase(getContext());
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View holder = inflater.inflate(R.layout.fragment_last_books, container, false);
        editedBook = new ArrayList<>();
        lastbooksRecyclerView = holder.findViewById(R.id.lastbooksRecyclerView);
        progressBar = holder.findViewById(R.id.progressBar);
        activityListener = (OnIterationWithActivityListener) getActivity();
        if(savedInstanceState != null) {
            long[] editedBookId = savedInstanceState.getLongArray(editedItemKey);
            if(editedBookId != null) {
                if(editedBookId.length > 1) {
                    BookRepository.BookDaoGetListByIds bookDaoGetListByIds =
                            new BookRepository.BookDaoGetListByIds();
                    bookDaoGetListByIds.execute(editedBookId);
                    try {
                        ArrayList<Book> books = (ArrayList<Book>) bookDaoGetListByIds.get();
                        if(books != null) editedBook.addAll(books);
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }
                } else {
                    BookRepository.BookDaoGetById bookDaoGetById = new BookRepository.BookDaoGetById();
                    bookDaoGetById.execute(editedBookId[0]);
                    try {
                        Book book = bookDaoGetById.get();
                        if(book != null) editedBook.add(book);
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        setHasOptionsMenu(true);

        return holder;
    }

    @Override
    public void onStart() {
        books = getBooks();
        if(books != null) updateBooks();

        new SetDrawable(this).execute(books);

        if(progressBar.getVisibility() == View.VISIBLE)
            progressBar.setVisibility(View.GONE);

        super.onStart();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if((editedBook != null)&&(editedBook.size() > 0)) {
            long[] editedBookId = new long[editedBook.size()];
            for(int i=0; i<editedBook.size(); i++) {
                editedBookId[i] = editedBook.get(i).bookId;
            }
            outState.putLongArray(editedItemKey, editedBookId);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_lastbooks, menu);
        if(currentState == PlaybackStateCompat.STATE_PLAYING ||
                currentState == PlaybackStateCompat.STATE_PAUSED) {
            MenuItem playPause = menu.findItem(R.id.menu_play);
            playPause.setVisible(true);
            playPause.setIcon(
                    currentState == PlaybackStateCompat.STATE_PLAYING ?
                            R.drawable.ic_pause_white_24dp :
                            R.drawable.ic_play_arrow_white_24dp
            );
        }
        launchFileManager = menu.findItem(R.id.menu_file_manager);
        launchFileManager.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onDestroyOptionsMenu() {
        launchFileManager.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        super.onDestroyOptionsMenu();

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.menu_remove_listened:
                editedBook.clear();
                showBookRemoveDialog(true);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void updateBooks() {
        manager = new LinearLayoutManager(getContext());
        adapter = new LastbooksAdapter(books, this);
        lastbooksRecyclerView.setLayoutManager(manager);
        lastbooksRecyclerView.setAdapter(adapter);
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(lastbooksRecyclerView.getContext(),
                manager.getOrientation());
        lastbooksRecyclerView.addItemDecoration(dividerItemDecoration);
    }

    private ArrayList<Book> getBooks() {
        BookDaoGetAll bookDaoGetAll = new BookDaoGetAll();
        bookDaoGetAll.execute();
        try {
            ArrayList<Book> bookList = bookDaoGetAll.get();
            if(bookList != null) {
                bookList = checkBooks(bookList);
                return bookList;
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return null;
    }

    private ArrayList<Book> checkBooks(final ArrayList<Book> bookList) {
        for(int i=0; i<bookList.size(); i++) {
            final Book book = bookList.get(i);
            if(!(new File(book.filepath).exists())) {
                bookList.remove(i);
                exec.execute(new Runnable() {
                    @Override
                    public void run() {
                        database.bookDao().delete(book);
                    }
                });
                i--;
            }
        }
        return bookList;
    }

    @Override
    public void onItemClick(View item) {

        if(actionMode != null) return;

        Book loadedBook = (Book)item.getTag();

        if(!loadedBook.exists()) {
            removeBook(loadedBook, false);
            return;
        }

        if(!loadedBook.equals(repository.getBook())) {
            currentState = PlaybackStateCompat.STATE_BUFFERING;
            repository.openBook(loadedBook.bookId);
        }
        activityListener.showLastBooks(false);
    }

    @Override
    public void onItemLongClick(View item) {
        if(actionMode == null) {
            editedBook.clear();
            editedView = item;
            editedView.setBackgroundColor(getActivity().getResources().getColor(R.color.mocassin));
            editedBook.add((Book) item.getTag());
            actionMode = ((AppCompatActivity)getContext()).startSupportActionMode(actionModeCallbacks);
        } else actionMode.finish();
    }

    private ActionMode.Callback actionModeCallbacks = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.contextmenu_lastbooks, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case (R.id.contextmenu_lastbooks_change): {
                    showBookRenameDialog();
                    break;
                }
                case (R.id.contextmenu_lastbooks_delete): {
                    showBookRemoveDialog(false);
                    break;
                }
                case (R.id.contextmenu_lastbooks_bookmarks): {
                    activityListener.showBookmarks(true, editedBook.get(0).bookId);
                    break;
                }
                case (R.id.contextmenu_lastbooks_picture): {
                    showBookPictureDialog();
                    break;
                }
                case (R.id.contextmenu_lastbooks_null_progress): {
                    nullProgress();
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

    private void showBookPictureDialog() {
        Book book = editedBook.get(0);

        BookRepository.ChapterDaoGetFirstByBookId chapterDaoGetFirstByBookId =
                new BookRepository.ChapterDaoGetFirstByBookId();
        chapterDaoGetFirstByBookId.execute(book.bookId);

        try {
            Chapter firstCh = chapterDaoGetFirstByBookId.get();
            if(firstCh.exists()) {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                retriever.setDataSource(firstCh.filepath);
                byte[] art = retriever.getEmbeddedPicture();

                if(art != null) {
                    ImageDialog dialog = new ImageDialog();
                    Bundle args = new Bundle();
                    args.putSerializable(ImageDialog.BUNDLE_IMAGE, art);
                    dialog.setArguments(args);
                    dialog.show(getActivity().getSupportFragmentManager(), ImageDialog.IMAGE_DIALOG_TAG);
                } else {
                    Toast.makeText(getActivity(),
                            getString(R.string.no_cover),
                            Toast.LENGTH_SHORT)
                            .show();
                }
                retriever.release();
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    private void showBookRemoveDialog(Boolean multipleChoises) {
        ArrayList<String> names = new ArrayList<>();
        if(!multipleChoises) {
            //извлекаем имя
            String name = editedBook.get(0).publicName;
            if(name.length() > 0) names.add(name);
        } else {
            for(int i=0; i<lastbooksRecyclerView.getChildCount(); i++) {
                View item = lastbooksRecyclerView.getChildAt(i);
                Book book = (Book) item.getTag();

                //извлекаем статус
                if(book.percent == 100) {
                    editedBook.add(book);
                    String name = book.publicName;
                    if(name.length() > 0) names.add(name);
                }
            }
        }
        RemoveDialog dialog = new RemoveDialog();
        Bundle args = new Bundle();
        args.putStringArrayList(RemoveDialog.BUNDLE_DEL_FILES, names);
        dialog.setArguments(args);
        dialog.setTargetFragment(this, RemoveDialog.REMOVE_DIALOG_CODE);
        dialog.show(getActivity().getSupportFragmentManager(), RemoveDialog.REMOVE_DIALOG_TAG);
    }

    private void showBookRenameDialog() {
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
                    editBookName(newName);
                }

                break;
            }
            case RemoveDialog.REMOVE_DIALOG_CODE: {
                if (resultCode == Activity.RESULT_OK) {
                    Boolean delFilesFromStorage = data.getBooleanExtra(RemoveDialog.EXTRA_DEL_FILES, false);
                    for(Book book : editedBook) {
                        removeBook(book, delFilesFromStorage);
                    }
                }

                break;
            }
        }
    }

    private void removeBook(final Book book, Boolean delFilesFromStorage) {
        String bookPath = book.filepath;

        //если удаляемая книга сейчас играет, очистить
        Book nowPlayingBook = repository.getBook();
        if(nowPlayingBook != null) {
            if(nowPlayingBook.filepath.equals(bookPath)) {
                repository.reset();
            }
        }

        //если пользователь захотел, удаляем файлы из хранилища
        if(delFilesFromStorage) {
            if(!removeFiles(book)) Toast.makeText(getActivity(),
                    getString(R.string.unable_remove_files),
                    Toast.LENGTH_SHORT)
                    .show();
        }

        //удаляем из базы
        exec.execute(new Runnable() {
            @Override
            public void run() {
                database.bookDao().delete(book);
            }
        });

        books.remove(book);
        adapter.notifyDataSetChanged();
    }

    private Boolean removeFiles(Book removedBook) {
        Boolean result = true;
        File directory = new File(removedBook.filepath);

        BookRepository.ChapterDaoGetAllByBookId chapterDaoGetAllByBookId =
                new BookRepository.ChapterDaoGetAllByBookId();
        chapterDaoGetAllByBookId.execute(removedBook.bookId);

        try {
            ArrayList<Chapter> chapters = (ArrayList<Chapter>)chapterDaoGetAllByBookId.get();
            for (Chapter ch : chapters) {
                File f = new File(ch.filepath);
                if(f.exists())
                    if(!f.delete()) result = false;
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        if (directory.listFiles().length == 0) directory.delete();

        return result;
    }

    private void nullProgress() {
        if((editedBook != null)&&(editedBook.size() > 0)) {

            final Book book = editedBook.get(0);

            book.nullProgress();

            BookRepository.nullBookProgress(book);

            Book nowBook = repository.getBook();
            if((nowBook != null)&&(nowBook.equals(book))) repository.reset();

            adapter.notifyDataSetChanged();
        }
    }

    private void editBookName(String name) {
        if((editedBook != null)&&(editedBook.size() > 0)) {
            final Book book = editedBook.get(0);
            book.publicName = name;

            exec.execute(new Runnable() {
                @Override
                public void run() {
                    database.bookDao().update(book);
                }
            });

            Book nowBook = repository.getBook();
            if(book.equals(nowBook)) nowBook.publicName = name;
        }
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onBackPressed() {
        activityListener.goBack();
    }

    static private class BookDaoGetAll extends AsyncTask<Void, Void, ArrayList<Book>> {
        @Override
        protected ArrayList<Book> doInBackground(Void... voids) {
            return (ArrayList<Book>) database.bookDao().getAll();
        }
    }

    static private class SetDrawable extends AsyncTask<ArrayList<Book>, Void, LinkedHashMap<String, Bitmap>> {

        private final WeakReference<LastBooksFragment> contextWR;

        SetDrawable(LastBooksFragment fragment) {
            contextWR = new WeakReference<>(fragment);
        }

        @Override
        protected LinkedHashMap<String, Bitmap> doInBackground(ArrayList<Book>... bookList) {
            LinkedHashMap<String, Bitmap> drawableMap = new LinkedHashMap<>();
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            for (Book b : bookList[0]) {
                File dir = new File(b.filepath);
                try {
                    retriever.setDataSource(dir.listFiles()[0].getAbsolutePath());
                    byte[] art = retriever.getEmbeddedPicture();
                    if(art != null){
                        drawableMap.put(b.filepath, BitmapFactory.decodeByteArray(art, 0, art.length));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            retriever.release();
            return drawableMap;
        }

        @Override
        protected void onPostExecute(LinkedHashMap<String, Bitmap> bitmaps) {
            if(contextWR != null) {
                LastBooksFragment fragment = contextWR.get();
                for(int i=0; i<fragment.books.size(); i++) {
                    String key = fragment.books.get(i).filepath;
                    fragment.books.get(i).cover = bitmaps.get(key);
                }
                fragment.adapter.notifyDataSetChanged();
            }
        }
    }
}
