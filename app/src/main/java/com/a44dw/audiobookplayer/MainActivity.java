package com.a44dw.audiobookplayer;

import android.Manifest;
import android.app.Activity;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.constraint.ConstraintSet;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.transition.TransitionManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URLConnection;
import java.util.ArrayList;

import static android.view.View.VISIBLE;

public class MainActivity extends AppCompatActivity implements OnIterationWithActivityListener {

    static final String TAG = "myDebugTag";

    //разрешения и всё, что с ними связано
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    //классы сервиса/медиа-контроллера
    ServiceConnection serviceConnection;
    MediaControllerCompat mediaController;
    MediaControllerCompat.Callback callback;
    AudiobookService.AudiobookServiceBinder audiobookServiceBinder;

    //фрагменты и всё, что с ними связано
    FragmentTransaction fragmentTransaction;
    AudioPlayerFragment audioPlayerFragment;
    MiniPlayerFragment miniPlayerFragment;
    BookScaleFragment bookScaleFragment;
    BookInfoFragment bookInfoFragment;
    FileManagerFragment fileManagerFragment;
    BookmarkFragment bookmarkFragment;
    LastBooksFragment lastBooksFragment;
    OnPlaybackStateChangedListener playbackStateChangedListener;
    public static final String seekBarBroadcastName = "seekBarBroadcastName";
    public static final String playbackStatus = "playbackStatus";

    static private class ActivityStatus {
        public static status nowIs;
        private enum status {
            SHOW_PLAYER_FRAGMENTS,
            SHOW_FILE_MANAGER,
            SHOW_BOOKMARKS,
            SHOW_LAST_BOOKS
        }

        public static boolean showBookscale = true;
    }

    //View'ы
    private ConstraintLayout constraintLayout;
    private FrameLayout playerLayout;
    private FrameLayout bookscaleLayout;
    private FrameLayout bookinfoLayout;

    //ViewModel и всё, что с ней связано
    static AudiobookViewModel model;

    //LiveData
    public static LiveData<Chapter> nowPlayingFile;

    //Класс для работы с внутренним хранилищем
    InnerStorageHandler storageHandler;

    //Класс меню
    Menu menu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(MainActivity.TAG, "MA -> onCreate()");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //проверяем разрешения
        verifyPermissions(this);

        constraintLayout = findViewById(R.id.constraintLayout);
        playerLayout = findViewById(R.id.playerLayout);
        bookscaleLayout = findViewById(R.id.bookScaleLayout);
        bookinfoLayout = findViewById(R.id.bookInfoLayout);

        //прикрепляем сервис и настраиваем медиа-контроллер
        callback = new MediaControllerCompat.Callback() {
            @Override
            public void onPlaybackStateChanged(PlaybackStateCompat state) {
                if(state != null) {
                    int nowState = state.getState();
                    AudiobookViewModel.setPlayerStatus(nowState);
                    MenuItem item = null;
                    if(menu != null) item = menu.findItem(R.id.menu_play);
                    Log.d(MainActivity.TAG, "MA -> onPlaybackStateChanged: status is " + nowState);
                    switch (nowState) {
                        case (PlaybackStateCompat.STATE_PLAYING): {
                            Log.d(MainActivity.TAG, "MA -> onPlaybackStateChanged: STATE_PLAYING");
                            //обращаемся к PlauerFragment с просьбой поменять картинку на кнопке
                            playbackStateChangedListener.onPlayMedia();
                            if(item != null) item.setIcon(R.drawable.ic_pause_white_24dp);
                            break;
                        }
                        case (PlaybackStateCompat.STATE_STOPPED):
                        case (PlaybackStateCompat.STATE_PAUSED): {
                            Log.d(MainActivity.TAG, "MA -> onPlaybackStateChanged: STATE_PAUSED/STATE_STOPPED");
                            //обращаемся к PlauerFragment с просьбой поменять картинку на кнопке
                            playbackStateChangedListener.onPauseMedia();
                            if(item != null) item.setIcon(R.drawable.ic_play_arrow_white_24dp);
                            break;
                        }
                    }
                }
            }
        };

        serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.d(MainActivity.TAG, "MA -> onServiceConnected");
                audiobookServiceBinder = (AudiobookService.AudiobookServiceBinder) service;
                try {
                    mediaController = new MediaControllerCompat(MainActivity.this, audiobookServiceBinder.getMediaSessionToken());
                    mediaController.registerCallback(callback);
                    callback.onPlaybackStateChanged(mediaController.getPlaybackState());
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.d(MainActivity.TAG, "MA -> onServiceDisconnected");
                audiobookServiceBinder = null;
                if(mediaController != null) {
                    mediaController.unregisterCallback(callback);
                    mediaController = null;
                }
            }
        };

        bindService(new Intent(this, AudiobookService.class), serviceConnection, BIND_AUTO_CREATE);

        //инициализация фрагментов
        fileManagerFragment = new FileManagerFragment();
        audioPlayerFragment = new AudioPlayerFragment();
        miniPlayerFragment = new MiniPlayerFragment();
        bookScaleFragment = new BookScaleFragment();
        bookInfoFragment = new BookInfoFragment();
        bookmarkFragment = new BookmarkFragment();
        lastBooksFragment = new LastBooksFragment();
        playbackStateChangedListener = audioPlayerFragment;

        //настраиваем модель
        model = ViewModelProviders.of(this).get(AudiobookViewModel.class);

        storageHandler = new InnerStorageHandler();

        nowPlayingFile = AudiobookViewModel.getNowPlayingFile();
        nowPlayingFile.observe(this, new Observer<Chapter>() {
            @Override
            public void onChanged(@Nullable Chapter chapter) {
                Log.d(MainActivity.TAG, "MA -> nowPlayingFile: onChanged()");
                if((AudiobookViewModel.getPlaylist() == null)||(AudiobookViewModel.getPlaylist().findInChapters(chapter) == -1)) {
                    File rootDirectory = chapter.getFile().getParentFile();
                    Log.d(MainActivity.TAG, "MA -> nowPlayingFile: onChanged(): playlist is empty or another, rootDirectory: " + rootDirectory);
                    if(AudiobookViewModel.getPlaylist() != null) {
                        //сохранение старого плейлиста
                        storageHandler.savePlaylistInStorage(AudiobookViewModel.getPlaylist());
                    }
                    //загрузка нового плейлиста
                    Book newPlaylist = storageHandler.getPlaylist(rootDirectory);
                    AudiobookViewModel.updatePlaylist(newPlaylist);
                    AudiobookViewModel.setNowPlayingFileNumber(chapter);
                    AudiobookViewModel.updatePlaylistName(storageHandler.plName);
                    //сохраняем плейлист при создании.
                    storageHandler.savePlaylistInStorage(newPlaylist);
                } else AudiobookViewModel.setNowPlayingFileNumber(chapter);
            }
        });

        if(storageHandler.hasSavedFile()) {
            Log.d(MainActivity.TAG, "MA -> onCreate(): has saved file");
            showMediaPlayerFragments(true);
            Chapter nowCh = storageHandler.loadPlayingFileFromStorage();
            AudiobookViewModel.updateNowPlayingFile(nowCh);
        } else {
            Log.d(MainActivity.TAG, "MA -> onCreate(): has not saved file");
            showMediaPlayerFragments(false);
        }
    }

    @Override
    protected void onStop() {
        //здесь сохраняется плейлист и текущий файл - отдельно
        Book playlist = AudiobookViewModel.getPlaylist();
        if(nowPlayingFile.getValue() != null) storageHandler.savePlayingFileInStorage();
        if(playlist != null) storageHandler.savePlaylistInStorage(AudiobookViewModel.getPlaylist());
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        audiobookServiceBinder = null;
        if (mediaController != null) {
            mediaController.unregisterCallback(callback);
            mediaController = null;
        }
        unbindService(serviceConnection);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    public static void verifyPermissions(Activity activity) {
        int permissionStorage = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permissionStorage != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }

    @Override
    public void onBackPressed() {
        FragmentManager fm = getSupportFragmentManager();
        OnBackPressedListener backPressedListener = null;
        for (Fragment fragment: fm.getFragments()) {
            if (fragment instanceof OnBackPressedListener) {
                backPressedListener = (OnBackPressedListener) fragment;
                break;
            }
        }
        if (backPressedListener != null) {
            backPressedListener.onBackPressed();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public MediaControllerCompat getMediaControllerCompat() {
        return mediaController;
    }

    //возможно ли перенести launch... методы в ActivityStatus?
    public void showMediaPlayerFragments(boolean flag) {
        Log.d(MainActivity.TAG, "MA -> launchPlayerFragment()");

        if(ActivityStatus.nowIs != ActivityStatus.status.SHOW_PLAYER_FRAGMENTS) {
            fragmentTransaction = getSupportFragmentManager().beginTransaction();
            fragmentTransaction.replace(R.id.playerLayout, audioPlayerFragment);
            fragmentTransaction.commit();
        }

        if(flag) {
            ConstraintSet set = new ConstraintSet();
            set.clone(constraintLayout);

            set.clear(R.id.bookInfoLayout, ConstraintSet.TOP);
            set.connect(R.id.bookScaleLayout, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
            set.connect(R.id.bookScaleLayout, ConstraintSet.BOTTOM, R.id.bookInfoLayout, ConstraintSet.TOP);
            set.connect(R.id.bookInfoLayout, ConstraintSet.BOTTOM, R.id.playerLayout, ConstraintSet.TOP);
            set.constrainHeight(R.id.bookInfoLayout, ConstraintSet.WRAP_CONTENT);
            set.connect(R.id.playerLayout, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);

            TransitionManager.beginDelayedTransition(constraintLayout);

            set.applyTo(constraintLayout);

            showBookInfo();
            showBookScale();
        }
        ActivityStatus.nowIs = ActivityStatus.status.SHOW_PLAYER_FRAGMENTS;
    }

    private void showBookScale() {
        Log.d(MainActivity.TAG, "MA -> launchPlayerFragment()");
        if(ActivityStatus.showBookscale) {
            if(bookscaleLayout.getVisibility() == View.GONE) bookscaleLayout.setVisibility(VISIBLE);
            fragmentTransaction = getSupportFragmentManager().beginTransaction();
            fragmentTransaction.replace(R.id.bookScaleLayout, bookScaleFragment);
            fragmentTransaction.commit();
        }
    }

    private void showBookInfo() {
        Log.d(MainActivity.TAG, "MA -> launchBookInfoFragment()");
        fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.bookInfoLayout, bookInfoFragment);
        fragmentTransaction.commit();
    }

    @Override
    public void showFileManager(boolean flag) {
        Log.d(MainActivity.TAG, "MA -> showFileManager()");
        if(flag) {
            ConstraintSet set = new ConstraintSet();
            set.clone(constraintLayout);

            //отвязываем верх playerLayout от parent, коннектим к его верху bookInfoLayout
            set.clear(R.id.playerLayout, ConstraintSet.TOP);
            set.connect(R.id.bookInfoLayout, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
            set.connect(R.id.bookInfoLayout, ConstraintSet.BOTTOM, R.id.playerLayout, ConstraintSet.TOP);
            set.constrainHeight(R.id.bookInfoLayout, ConstraintSet.MATCH_CONSTRAINT);

            //анимация
            TransitionManager.beginDelayedTransition(constraintLayout);

            set.applyTo(constraintLayout);

            bookscaleLayout.setVisibility(View.GONE);

            fragmentTransaction = getSupportFragmentManager().beginTransaction();
            fragmentTransaction.replace(R.id.bookInfoLayout, fileManagerFragment);
            fragmentTransaction.remove(audioPlayerFragment);
            //fragmentTransaction.replace(R.id.playerLayout, miniPlayerFragment);
            fragmentTransaction.commit();

            ActivityStatus.nowIs = ActivityStatus.status.SHOW_FILE_MANAGER;
        } else {
            showMediaPlayerFragments(true);
        }
    }

    @Override
    public void showBookmarks(boolean flag) {
        if(flag) {
            Log.d(MainActivity.TAG, "MA -> showBookmarks()");

            ConstraintSet set = new ConstraintSet();
            set.clone(constraintLayout);

            //отвязываем верх playerLayout от parent, коннектим к его верху bookInfoLayout
            set.clear(R.id.playerLayout, ConstraintSet.TOP);
            set.connect(R.id.bookInfoLayout, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
            set.connect(R.id.bookInfoLayout, ConstraintSet.BOTTOM, R.id.playerLayout, ConstraintSet.TOP);
            set.constrainHeight(R.id.bookInfoLayout, ConstraintSet.MATCH_CONSTRAINT);

            //анимация
            TransitionManager.beginDelayedTransition(constraintLayout);

            set.applyTo(constraintLayout);

            bookscaleLayout.setVisibility(View.GONE);

            fragmentTransaction = getSupportFragmentManager().beginTransaction();
            fragmentTransaction.replace(R.id.bookInfoLayout, bookmarkFragment);
            fragmentTransaction.remove(audioPlayerFragment);
            //fragmentTransaction.replace(R.id.playerLayout, miniPlayerFragment);
            fragmentTransaction.commit();
            ActivityStatus.nowIs = ActivityStatus.status.SHOW_BOOKMARKS;
        } else {
            showMediaPlayerFragments(true);
        }
    }

    @Override
    public void showLastBooks(boolean flag) {
        if(flag) {
            Log.d(MainActivity.TAG, "MA -> showLastBooks()");

            //обновляем файл в плейлисте
            AudiobookViewModel.updateNowPlayingChapterInPlaylist();
            //сохраняем плейлист
            storageHandler.savePlaylistInStorage(AudiobookViewModel.getPlaylist());

            ConstraintSet set = new ConstraintSet();
            set.clone(constraintLayout);

            //отвязываем верх playerLayout от parent, коннектим к его верху bookInfoLayout
            set.clear(R.id.playerLayout, ConstraintSet.TOP);
            set.connect(R.id.bookInfoLayout, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
            set.connect(R.id.bookInfoLayout, ConstraintSet.BOTTOM, R.id.playerLayout, ConstraintSet.TOP);
            set.constrainHeight(R.id.bookInfoLayout, ConstraintSet.MATCH_CONSTRAINT);

            //анимация
            TransitionManager.beginDelayedTransition(constraintLayout);

            set.applyTo(constraintLayout);

            bookscaleLayout.setVisibility(View.GONE);
            fragmentTransaction = getSupportFragmentManager().beginTransaction();
            fragmentTransaction.replace(R.id.bookInfoLayout, lastBooksFragment);
            fragmentTransaction.remove(audioPlayerFragment);
            //fragmentTransaction.replace(R.id.playerLayout, miniPlayerFragment);
            fragmentTransaction.commit();
            ActivityStatus.nowIs = ActivityStatus.status.SHOW_LAST_BOOKS;
        } else {
            showMediaPlayerFragments(true);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.d(MainActivity.TAG, "MA -> onCreateOptionsMenu()");
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        this.menu = menu;
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.d(MainActivity.TAG, "MA -> onOptionsItemSelected()");
        int id = item.getItemId();

        switch (id) {
            case R.id.menu_about:
                return true;
            case R.id.menu_bookmarks:
                showBookmarks(true);
                return true;
            case R.id.menu_library:
                showLastBooks(true);
                return true;
            case R.id.menu_file_manager:
                showFileManager(true);
                return true;
            case R.id.menu_theme:
                return true;
            case R.id.menu_settings:
                return true;
            case R.id.menu_play: {
                switch (AudiobookViewModel.getPlayerStatus()) {
                    //TODO проверить при статусе STOP
                    case (PlaybackStateCompat.STATE_PAUSED): {
                        mediaController.getTransportControls().play();
                        break;
                    }
                    case (PlaybackStateCompat.STATE_PLAYING): {
                        mediaController.getTransportControls().pause();
                        break;
                        }
                    }
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    class InnerStorageHandler {

        private File innerStorage;
        private File playlistStorage;
        final String NOW_PLAYING_CHAPTER_NAME = "nowPlayingChapter";
        final String PLAYLIST_STORAGE = "playlists";
        public String plName;

        public InnerStorageHandler() {
            innerStorage = getFilesDir();
            String dirPath = getFilesDir().getAbsolutePath() + File.separator + PLAYLIST_STORAGE;
            playlistStorage = new File(dirPath);
            if (!playlistStorage.exists())
                playlistStorage.mkdirs();
        }

        public Book getPlaylist(File rootDirectory) {
            plName = Book.generateFileName(rootDirectory);
            Book playlist;
            Log.d(MainActivity.TAG, "InnerStorageHandler -> getPlaylist() name of new playlist " + plName);
            if(inStorage(plName)) {
                Log.d(MainActivity.TAG, "InnerStorageHandler -> getPlaylist(): inStorage == true");
                playlist = loadPlaylistFromStorage(plName);
            } else {
                File[] filesInDirectory = rootDirectory.listFiles();
                ArrayList<Chapter> chapters = new ArrayList<>();
                for(File f : filesInDirectory) {
                    if(isAudio(f)) {
                        chapters.add(new Chapter(f));
                    }
                }
                playlist = new Book(chapters);
            }
            return playlist;
        }

        private Chapter loadPlayingFileFromStorage() {
            Context context = getApplicationContext();
            try {
                Log.d(MainActivity.TAG, "InnerStorageHandler -> loadPlayingFileFromStorage()");
                BufferedReader br = new BufferedReader(new InputStreamReader(
                        context.openFileInput(NOW_PLAYING_CHAPTER_NAME)));

                GsonBuilder builder = new GsonBuilder();
                Gson gson = builder.create();

                String line = br.readLine();
                Log.d(MainActivity.TAG, "InnerStorageHandler -> loadPlayingFileFromStorage(): read line " + line);
                Chapter ch = gson.fromJson(line, Chapter.class);
                Log.d(MainActivity.TAG, "InnerStorageHandler -> loadPlayingFileFromStorage(): converted from json " + ch.getFile().getName());

                return ch;

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        private Book loadPlaylistFromStorage(String name) {

            Book playlist;

            try {
                Log.d(MainActivity.TAG, "InnerStorageHandler -> loadPlaylistFromStorage() -> book name is " + name);

                File currentPl = new File(playlistStorage.getAbsolutePath() + File.separator + name);

                BufferedReader br = new BufferedReader(new FileReader(currentPl));

                GsonBuilder builder = new GsonBuilder();
                Gson gson = builder.create();

                String jsonPlaylist = br.readLine();
                Log.d(MainActivity.TAG, "InnerStorageHandler -> loadPlaylistFromStorage(): read book from json " + jsonPlaylist);
                playlist = gson.fromJson(jsonPlaylist, Book.class);

                return playlist;
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        public void savePlayingFileInStorage() {
            //собираем данные играющего файла
            Chapter nowPlayingChapter = AudiobookViewModel.getNowPlayingFile().getValue();
            nowPlayingChapter.setProgress(AudiobookViewModel.getNowPlayingPosition());

            Log.d(MainActivity.TAG, "InnerStorageHandler -> savePlayingFileInStorage(): " + nowPlayingChapter.getChapter());

            GsonBuilder builder = new GsonBuilder();
            Gson gson = builder.create();
            String jsonChapter = gson.toJson(nowPlayingChapter);

            try {
                BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
                        getApplicationContext().openFileOutput(NOW_PLAYING_CHAPTER_NAME, MODE_PRIVATE)
                ));

                bw.write(jsonChapter);
                bw.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void savePlaylistInStorage(Book pl) {
            if(innerStorage != null) {
                try {
                    File currentPl = new File(playlistStorage, pl.getFileName());
                    if(!currentPl.exists()) currentPl.createNewFile();

                    //записываем в Book path
                    //if(pl.getPath() == null) pl.setPath(currentPl.getAbsolutePath());
                    //записываем номер последнего трека
                    pl.setLastPlayedChapterNum(AudiobookViewModel.getNowPlayingFileNumber());

                    //сериализуем Book
                    //TODO разработать алгоритм, при котором сохранятся будут только Chapter, в которые внесены изменения
                    GsonBuilder builder = new GsonBuilder();
                    Gson gson = builder.create();
                    String jsonPl = gson.toJson(pl);
                    Log.d(MainActivity.TAG, "InnerStorageHandler -> savePlaylistInStorage(): converting to json " + pl.getName() + " result is " + jsonPl);

                    BufferedWriter bw = new BufferedWriter(new FileWriter(currentPl));

                    Log.d(MainActivity.TAG, "InnerStorageHandler -> savePlaylistInStorage(): write to file " + jsonPl);
                    bw.write(jsonPl);
                    bw.close();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private Boolean inStorage(String name) {
            //получаем файлы из внутр.хранилища
            File[] files = playlistStorage.listFiles();
            if(files != null) {
                for(int i=0; i<files.length; i++) {
                    File f = files[i];
                    if(f.isDirectory()) continue;
                    if(f.getName().equals(name)) return true;
                }
            }
            return false;
        }

        public Boolean isAudio(File file) {
            return URLConnection.guessContentTypeFromName(file.toString()).equals("audio/mpeg");
        }

        public boolean hasSavedFile() {
            File savedFile = new File(innerStorage + File.separator + NOW_PLAYING_CHAPTER_NAME);
            return savedFile.exists();
        }
    }

    public interface OnBackPressedListener {
        void onBackPressed();
    }

    public interface OnPlaybackStateChangedListener {
        void onPlayMedia();
        void onPauseMedia();
    }
}
