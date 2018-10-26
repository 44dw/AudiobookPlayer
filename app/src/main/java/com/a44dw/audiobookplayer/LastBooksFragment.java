package com.a44dw.audiobookplayer;


import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
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
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.a44dw.audiobookplayer.FileManagerHandler.pathToCurrentDirectory;

public class LastBooksFragment extends Fragment implements LastbooksAdapter.OnItemClickListener,
                                                           MainActivity.OnBackPressedListener {

    public static final String fileNameRegex = "\"fileName\":\"(.*?)\"";

    public static final String publicNameRegex = "(\"publicName\":\")(.*)(\")";

    public static final String doneRegex = "(\"done\":)(true)(,)";
    public static final String bookProgressRegex = "(\"bookProgress\":)([\\d]*)" ;
    public static final String progressRegex = "(\"progress\":)([\\d]*)";
    public static final String lastChapterRegex = "(\"lastPlayedChapterNum\":)(\\d*)";
    public static final String percentRegex = "(\"percent\":)(\\d*)";

    private View view;
    private File innerStorage;
    private File playlistStorage;
    private RecyclerView lastbooksRecyclerView;
    private LastbooksAdapter adapter;
    private ArrayList<String> jsonBooks;
    LinearLayoutManager manager;
    OnIterationWithActivityListener activityListener;
    private View editedView;
    private ArrayList<String> editedJsonBook;
    private ActionMode actionMode;
    private MenuItem launchFileManager;

    public LastBooksFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_last_books, container, false);
        innerStorage = getActivity().getFilesDir();
        playlistStorage = new File(innerStorage.getAbsolutePath() + File.separator + "playlists");
        jsonBooks = getPlaylists();
        editedJsonBook = new ArrayList<>();
        lastbooksRecyclerView = view.findViewById(R.id.lastbooksRecyclerView);

        activityListener = (OnIterationWithActivityListener) getActivity();

        updatePlaylists();

        setHasOptionsMenu(true);
        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        Log.d(MainActivity.TAG, "LastBooksFragment -> onCreateOptionsMenu");
        inflater.inflate(R.menu.menu_lastbooks, menu);
        if(AudiobookViewModel.getPlayerStatus() == PlaybackStateCompat.STATE_PLAYING ||
                AudiobookViewModel.getPlayerStatus() == PlaybackStateCompat.STATE_PAUSED) {
            MenuItem playPause = menu.findItem(R.id.menu_play);
            playPause.setVisible(true);
            playPause.setIcon(
                    AudiobookViewModel.getPlayerStatus() == PlaybackStateCompat.STATE_PLAYING ?
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
        Log.d(MainActivity.TAG, "LastBooksFragment -> onDestroyOptionsMenu");
        launchFileManager.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        super.onDestroyOptionsMenu();

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.d(MainActivity.TAG, "LastBooksFragment -> onOptionsItemSelected()");
        int id = item.getItemId();
        switch (id) {
            case R.id.menu_remove_listened:
                //очищаем массив, потому что диалог вызывается не из контекстного меню
                editedJsonBook.clear();
                showBookRemoveDialog(true);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void updatePlaylists() {
        Log.d(MainActivity.TAG, "LastBooksFragment -> updatePlaylists");
        manager = new LinearLayoutManager(getContext());
        adapter = new LastbooksAdapter(jsonBooks, this);
        lastbooksRecyclerView.setLayoutManager(manager);
        lastbooksRecyclerView.setAdapter(adapter);
        //установка разделителя
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(lastbooksRecyclerView.getContext(),
                manager.getOrientation());
        lastbooksRecyclerView.addItemDecoration(dividerItemDecoration);
    }

    private ArrayList<String> getPlaylists() {
        File[] files = playlistStorage.listFiles();
        ArrayList<String> books = new ArrayList<>();
        for(File f : files) {
            Log.d(MainActivity.TAG, "LastBooksFragment -> getPlaylists(): " + f.toString());
            try {
                BufferedReader br = new BufferedReader(new FileReader(f));
                String jsonPlaylist = br.readLine();
                books.add(jsonPlaylist);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return books;
    }

    @Override
    public void onItemClick(View item) {
        Log.d(MainActivity.TAG, "LastBooksFragment -> onItemClick()");

        String jsonBook = (String)item.getTag();

        GsonBuilder builder = new GsonBuilder();
        Gson gson = builder.create();
        Log.d(MainActivity.TAG, "read book from json " + jsonBook);
        Book loadedBook = gson.fromJson(jsonBook, Book.class);

        if(!loadedBook.equals(AudiobookViewModel.getPlaylist())) {
            Log.d(MainActivity.TAG, "selected Book NOT equals playing Book");
            //достаём последний проигрываемый файл из плейлиста и запускаем
            int lastPlayedChapterNum = loadedBook.getLastPlayedChapterNum();
            Chapter startChapter = loadedBook.getChapters().get(lastPlayedChapterNum);
            AudiobookViewModel.updateNowPlayingFile(startChapter);
        }
        activityListener.showLastBooks(false);
    }

    @Override
    public void onItemLongClick(View item) {
        editedJsonBook.clear();
        editedView = item;
        editedView.setBackgroundColor(getActivity().getResources().getColor(R.color.pbBackgroundIncative));
        editedJsonBook.add((String) item.getTag());
        if(actionMode == null) {
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
            Log.d(MainActivity.TAG, "LastBooksFragment -> actionModeCallbacks -> onActionItemClicked()");
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
                    activityListener.showBookmarks(true, editedJsonBook.get(0));
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
            Log.d(MainActivity.TAG, "LastBooksFragment -> actionModeCallbacks -> onDestroyActionMode()");
            actionMode = null;
            if(editedView != null) editedView.setBackground(null);
        }
    };

    private void showBookPictureDialog() {
        String pathToFile = "";
        Pattern pattern = Pattern.compile(LastbooksAdapter.REGEX_GET_FILE, Pattern.UNICODE_CASE);
        Matcher matcher = pattern.matcher(editedJsonBook.get(0));
        if(matcher.find()) pathToFile = matcher.group(1);
        //создаём файл только чтобы проверить
        File file = new File(pathToFile);
        if(file.exists()) {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(pathToFile);
            byte[] art = retriever.getEmbeddedPicture();

            ImageDialog dialog = new ImageDialog();
            Bundle args = new Bundle();
            args.putSerializable(ImageDialog.BUNDLE_IMAGE, art);
            dialog.setArguments(args);
            dialog.show(getActivity().getSupportFragmentManager().beginTransaction(), ImageDialog.IMAGE_DIALOG_TAG);
        }
    }

    //если false - было вызвано для одной книги, true - для нескольких
    private void showBookRemoveDialog(Boolean multipleChoises) {
        ArrayList<String> names = new ArrayList<>();
        if(!multipleChoises) {
            //извлекаем имя
            String name = getBookName(editedJsonBook.get(0));
            if(name.length() > 0) names.add(name);
        } else {
            for(int i=0; i<lastbooksRecyclerView.getChildCount(); i++) {
                View item = lastbooksRecyclerView.getChildAt(i);
                String jsonBook = (String) item.getTag();

                //извлекаем статус
                if(LastbooksAdapter.LastbookViewHolder.isDone(jsonBook)) {
                    editedJsonBook.add(jsonBook);
                    String name = getBookName(jsonBook);
                    if(name.length() > 0) names.add(name);
                }
            }
        }
        //создаём диалог и добавляем в Bundle имя
        RemoveDialog dialog = new RemoveDialog();
        Bundle args = new Bundle();
        args.putStringArrayList(RemoveDialog.BUNDLE_DEL_FILES, names);
        dialog.setArguments(args);
        dialog.setTargetFragment(this, RemoveDialog.REMOVE_DIALOG_CODE);
        dialog.show(getActivity().getSupportFragmentManager().beginTransaction(), RemoveDialog.REMOVE_DIALOG_TAG);
    }

    private String getBookName(String jsonBook) {
        Pattern pattern = Pattern.compile(LastbooksAdapter.REGEX_GET_NAME);
        Matcher matcher = pattern.matcher(jsonBook);
        String result = "";
        if(matcher.find()) {
            Log.d(MainActivity.TAG, "LastBooksFragment -> getBookName(): name is " + matcher.group(1));
            result = matcher.group(1);
        }
        return result;
    }

    private void showBookRenameDialog() {
        RenameDialog dialog = new RenameDialog();
        dialog.setTargetFragment(this, RenameDialog.RENAME_DIALOG_CODE);
        dialog.show(getActivity().getSupportFragmentManager().beginTransaction(), RenameDialog.RENAME_DIALOG_TAG);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
            case RenameDialog.RENAME_DIALOG_CODE: {
                if (resultCode == Activity.RESULT_OK) {
                    String newName = data.getStringExtra(RenameDialog.EXTRA_NAME);
                    editBookmarkName(newName);
                } else if (resultCode == Activity.RESULT_CANCELED){}

                break;
            }
            case RemoveDialog.REMOVE_DIALOG_CODE: {
                if (resultCode == Activity.RESULT_OK) {
                    Boolean delFilesFromStorage = data.getBooleanExtra(RemoveDialog.EXTRA_DEL_FILES, false);
                    for(String jsonBook : editedJsonBook) {
                        removeBook(jsonBook, delFilesFromStorage);
                    }
                } else if (resultCode == Activity.RESULT_CANCELED){}

                break;
            }
        }
    }

    private void removeBook(String jsonBook, Boolean delFilesFromStorage) {
        //получаем имя файла
        //TODO дублирование кода!
        Pattern pathPattern = Pattern.compile(fileNameRegex);
        Matcher pathMatcher = pathPattern.matcher(jsonBook);
        String delFilename = "";
        if(pathMatcher.find()) delFilename = pathMatcher.group(1);
        Log.d(MainActivity.TAG, "LastBooksFragment -> removeBook() found filename is " + delFilename);

        //если удаляемая книга сейчас играет, очистить
        Book nowPlayingBook = AudiobookViewModel.getPlaylist();
        if(nowPlayingBook != null) {
            if(nowPlayingBook.getFileName().equals(delFilename)) {
                AudiobookViewModel.stopPlayingAndReset();
            }
        }

        //если пользователь захотел, удаляем файлы из хранилища
        if(delFilesFromStorage) {
            if(!removeFiles(jsonBook)) Toast.makeText(getActivity(),
                    getString(R.string.unable_remove_files),
                    Toast.LENGTH_SHORT)
                    .show();
        }

        //удаляем файл Book
        //TODO вынести в отдельный поток
        File file = new File(playlistStorage, delFilename);
        if(file.exists()) file.delete();

        jsonBooks = getPlaylists();
        adapter.changeData(jsonBooks);
    }

    private Boolean removeFiles(String jsonBook) {
        String pathToFileRegex = "\"path\":\"(.*?)\"";

        Boolean returned = true;

        Pattern pattern = Pattern.compile(pathToFileRegex);
        Matcher matcher = pattern.matcher(jsonBook);
        File directory = null;
        //не слишком эффективно
        //TODO попробовать рекурсию отсюда https://stackoverflow.com/questions/4943629/how-to-delete-a-whole-folder-and-content
        while(matcher.find()) {
            File file = new File(matcher.group(1));
            if(directory == null) directory = file.getParentFile();
            if(!file.delete()) returned = false;
            if(directory.listFiles().length == 0) {
                if(!directory.delete()) returned = false;
            }
        }
        return returned;
    }

    private void nullProgress() {
        if(editedJsonBook != null) {
            for(int i=0; i<jsonBooks.size(); i++) {
                String bookFromArray = jsonBooks.get(i);
                if(editedJsonBook.equals(bookFromArray)) {
                    //обнуляем прогресс файла
                    Pattern pattern = Pattern.compile(progressRegex);
                    Matcher matcher = pattern.matcher(bookFromArray);
                    String newProgress = "0";
                    String progressReplacementText = "$1" + newProgress;
                    String formatted = matcher.replaceAll(progressReplacementText);
                    Log.d(MainActivity.TAG, "LastBooksFragment -> nullProgress() 0 progress formatted text is " + formatted);

                    //сбрасываем isDone
                    pattern = Pattern.compile(doneRegex);
                    matcher = pattern.matcher(formatted);
                    String doneReplacementText = "$1" + "false" + "$3";
                    formatted = matcher.replaceAll(doneReplacementText);
                    Log.d(MainActivity.TAG, "LastBooksFragment -> nullProgress() false formatted text is " + formatted);

                    //обнуляем прогресс книги
                    pattern = Pattern.compile(bookProgressRegex);
                    matcher = pattern.matcher(formatted);
                    formatted = matcher.replaceAll(progressReplacementText);
                    Log.d(MainActivity.TAG, "LastBooksFragment -> nullProgress() 0 progress book formatted text is " + formatted);

                    //обнуляем последнюю прослушанную часть
                    pattern = Pattern.compile(lastChapterRegex);
                    matcher = pattern.matcher(formatted);
                    formatted = matcher.replaceFirst(progressReplacementText);
                    Log.d(MainActivity.TAG, "LastBooksFragment -> nullProgress() last chapter book formatted text is " + formatted);

                    //обнуляем процент
                    pattern = Pattern.compile(percentRegex);
                    matcher = pattern.matcher(formatted);
                    formatted = matcher.replaceFirst(progressReplacementText);
                    Log.d(MainActivity.TAG, "LastBooksFragment -> nullProgress() percent book formatted text is " + formatted);

                    //записываем все изменения в лист
                    jsonBooks.set(i, formatted);

                    //получаем имя файла в хранилие
                    String filename = getFilename(bookFromArray);

                    //записываем файл на диск
                    //TODO вынести запись в отдельный поток
                    try {
                        File file = new File(playlistStorage, filename);
                        BufferedWriter bw = new BufferedWriter(new FileWriter(file));
                        bw.write(formatted);
                        bw.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    //если плейлист текущий, обновляем имя файла и там...
                    Book nowBook = AudiobookViewModel.getPlaylist();
                    if(filename.equals(nowBook.getFileName())) {
                        AudiobookViewModel.stopPlayingAndReset();
                    }
                    break;
                }
            }
            adapter.notifyDataSetChanged();
        }
    }

    private void editBookmarkName(String name) {
        if(editedJsonBook != null) {

            for(int i=0; i<jsonBooks.size(); i++) {
                String bookFromArray = jsonBooks.get(i);
                if(editedJsonBook.equals(bookFromArray)) {

                    //меняем имя
                    Pattern namePattern = Pattern.compile(publicNameRegex);
                    Matcher nameMatcher = namePattern.matcher(bookFromArray);
                    String replacementText = "$1" + name + "$3";

                    //получаем форматированную книгу
                    String formattedBook = nameMatcher.replaceFirst(replacementText);
                    Log.d(MainActivity.TAG, "LastBooksFragment -> editBookmarkName() replaced text is " + formattedBook);
                    jsonBooks.set(i, formattedBook);

                    //получаем имя файла в хранилищк
                    String filename = getFilename(bookFromArray);

                    //записываем файл на диск
                    //TODO вынести запись в отдельный поток
                    try {
                        File file = new File(playlistStorage, filename);
                        BufferedWriter bw = new BufferedWriter(new FileWriter(file));
                        bw.write(formattedBook);
                        bw.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    //если плейлист текущий, обновляем имя файла и там...
                    Book nowBook = AudiobookViewModel.getPlaylist();
                    if(filename.equals(nowBook.getFileName())) {
                        nowBook.setName(name);
                    }
                    break;
                }
            }
            adapter.notifyDataSetChanged();
        }
    }

    private String getFilename(String jsonBook) {
        Pattern pathPattern = Pattern.compile(fileNameRegex);
        Matcher pathMatcher = pathPattern.matcher(jsonBook);
        String filename = "";
        if(pathMatcher.find()) filename = pathMatcher.group(1);
        Log.d(MainActivity.TAG, "LastBooksFragment -> getFilename() found filename is " + filename);
        return filename;
    }

    @Override
    public void onBackPressed() {
        activityListener.goBack();
    }
}
