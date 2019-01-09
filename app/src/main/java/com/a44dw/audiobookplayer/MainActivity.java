package com.a44dw.audiobookplayer;

import android.Manifest;
import android.app.Activity;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.NonNull;
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
import android.support.v7.preference.PreferenceManager;
import android.transition.TransitionManager;
import android.view.Display;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.a44dw.audiobookplayer.AudiobookService.currentState;

public class MainActivity extends AppCompatActivity implements OnIterationWithActivityListener {

    public static final String TAG_PLAYER_FRAGMENT = "playerFragment";
    public static final String TAG_INFO_FRAGMENT = "infoFragment";
    public static final String TAG_SCALE_FRAGMENT = "scaleFragment";
    public static final String TAG_FILEMANAGER_FRAGMENT = "filemanagerFragment";
    public static final String TAG_LASTBOOKS_FRAGMENT = "lastbooksFragment";
    public static final String TAG_BOOKMARKS_FRAGMENT = "bookmarksFragment";
    public static final String TAG_PREFERENCE_FRAGMENT = "preferenceFragment";
    public static final String TAG_COVER_FRAGMENT = "coverFragment";
    public static final String TAG_USERMENU_FRAGMENT = "userMenuFragment";
    public static final String TAG_HELP_FRAGMENT = "helpFragment";

    public static final String KEY_SAVED_CHAPTER = "savedChapter";

    public static final String SEEK_BAR_BROADCAST_NAME = "SEEK_BAR_BROADCAST_NAME";
    public static final String PLAYBACK_STATUS = "PLAYBACK_STATUS";

    public static final int SHOW_PLAYER_FRAGMENTS = 1;
    public static final int SHOW_FILE_MANAGER = 2;
    public static final int SHOW_BOOKMARKS = 3;
    public static final int SHOW_LAST_BOOKS = 4;
    public static final int SHOW_PREFERENCES = 5;
    public static final int SHOW_HELP = 6;

    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    ServiceConnection serviceConnection;
    MediaControllerCompat mediaController;
    MediaControllerCompat.Callback callback;
    AudiobookService.AudiobookServiceBinder audiobookServiceBinder;

    FragmentTransaction fragmentTransaction;
    FragmentManager fragmentManager;
    AudioPlayerFragment audioPlayerFragment;
    BookScaleFragment bookScaleFragment;
    BookInfoFragment bookInfoFragment;
    FileManagerFragment fileManagerFragment;
    BookmarkFragment bookmarkFragment;
    LastBooksFragment lastBooksFragment;
    CoverFragment coverFragment;
    UserMenuFragment userMenuFragment;
    Preferences preferencesFragment;
    HelpCreditsFragment helpCreditsFragment;
    OnPlaybackStateChangedListener playbackStateChangedListener;

    public static ExecutorService exec;

    private ConstraintLayout constraintLayout;

    AudiobookViewModel model;

    BookRepository repository;

    public LiveData<Chapter> currentChapter;

    Menu menu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        getSupportActionBar().setElevation(0);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        model = ViewModelProviders.of(this).get(AudiobookViewModel.class);

        verifyPermissions(this);

        constraintLayout = findViewById(R.id.constraintLayout);

        exec = Executors.newCachedThreadPool();

        callback = new MediaControllerCompat.Callback() {
            @Override
            public void onPlaybackStateChanged(PlaybackStateCompat state) {
                if(state != null) {
                    int nowState = state.getState();
                    MenuItem item = null;
                    if(menu != null) item = menu.findItem(R.id.menu_play);
                    if(playbackStateChangedListener == null) initPlayerFragment();
                    switch (nowState) {
                        case (PlaybackStateCompat.STATE_PLAYING): {
                            if(model.getFragmentStatus() == SHOW_PLAYER_FRAGMENTS) playbackStateChangedListener.onPlayMedia();
                            if(item != null) item.setIcon(R.drawable.ic_pause_white_24dp);
                            break;
                        }
                        case (PlaybackStateCompat.STATE_NONE):
                        case (PlaybackStateCompat.STATE_STOPPED):
                        case (PlaybackStateCompat.STATE_PAUSED): {
                            if(model.getFragmentStatus() == SHOW_PLAYER_FRAGMENTS) playbackStateChangedListener.onPauseMedia();
                            if(item != null) item.setIcon(R.drawable.ic_play_arrow_white_24dp);
                            break;
                        }
                    }
                }
            }
        };

        serviceConnection = new BookplayerSconn();

        bindService(new Intent(this, AudiobookService.class), serviceConnection, BIND_AUTO_CREATE);

        fragmentManager = getSupportFragmentManager();

        repository = BookRepository.getInstance();

        repository.getDatabase(this);

        currentChapter = repository.getCurrentChapter();
        currentChapter.observe(this, new Observer<Chapter>() {
            @Override
            public void onChanged(@Nullable Chapter chapter) {}
        });

        if(!model.screenRotate) {
            if(!model.permissions) return;
            SharedPreferences preferences = getPreferences(Activity.MODE_PRIVATE);
            if(preferences.contains(KEY_SAVED_CHAPTER)) {
                String chapterPath = preferences.getString(KEY_SAVED_CHAPTER, "");
                File openedFile = new File(chapterPath);
                if(openedFile.exists()) {
                    repository.openFile(openedFile);
                    showMediaPlayerFragments(true);
                } else showMediaPlayerFragments(false);
            } else showMediaPlayerFragments(false);
        } else {
            model.screenRotate = false;
            if(model.getFragmentStatus() == SHOW_PLAYER_FRAGMENTS) goToPlayerFragments();
            else prepareConstraintSet();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Chapter nowCh = currentChapter.getValue();
        if((nowCh != null)&&(!nowCh.exists())) {
            if (model.getFragmentStatus() == SHOW_PLAYER_FRAGMENTS) {
                showMediaPlayerFragments(false);
                repository.reset();
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        Chapter ch = repository.getCurrentChapter().getValue();
        SharedPreferences preferences = getPreferences(Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        if(ch == null) editor.remove(KEY_SAVED_CHAPTER);
        else {
            editor.putString(KEY_SAVED_CHAPTER, ch.filepath);
        }
        editor.apply();
    }

    public void setFragmentStatus(int now) {
        switch (now) {
            case(SHOW_PLAYER_FRAGMENTS): {
                goToPlayerFragments();
                break;
            }
            case(SHOW_FILE_MANAGER):
            case(SHOW_BOOKMARKS):
            case(SHOW_LAST_BOOKS):
            case(SHOW_PREFERENCES):
            case(SHOW_HELP): {
                prepareConstraintSet();
                break;
            }
        }
        model.setFragmentStatus(now);
    }

    private void initPlayerFragment() {
        audioPlayerFragment = (AudioPlayerFragment) fragmentManager.findFragmentByTag(TAG_PLAYER_FRAGMENT);
        if(audioPlayerFragment == null) audioPlayerFragment = new AudioPlayerFragment();
        playbackStateChangedListener = audioPlayerFragment;
    }

    private void initBookinfoFragment() {
        bookInfoFragment = (BookInfoFragment) fragmentManager.findFragmentByTag(TAG_INFO_FRAGMENT);
        if(bookInfoFragment == null) bookInfoFragment = new BookInfoFragment();
    }

    private void initBookscaleFragment() {
        bookScaleFragment = (BookScaleFragment) fragmentManager.findFragmentByTag(TAG_SCALE_FRAGMENT);
        if(bookScaleFragment == null) bookScaleFragment = new BookScaleFragment();
    }

    private void initFileManagerFragment() {
        fileManagerFragment = (FileManagerFragment) fragmentManager.findFragmentByTag(TAG_FILEMANAGER_FRAGMENT);
        if(fileManagerFragment == null) fileManagerFragment = new FileManagerFragment();
    }

    private void initLastbooksFragment() {
        lastBooksFragment = (LastBooksFragment) fragmentManager.findFragmentByTag(TAG_LASTBOOKS_FRAGMENT);
        if(lastBooksFragment == null) lastBooksFragment = new LastBooksFragment();
    }

    private void initBookmarksFragment() {
        bookmarkFragment = (BookmarkFragment) fragmentManager.findFragmentByTag(TAG_BOOKMARKS_FRAGMENT);
        if(bookmarkFragment == null) bookmarkFragment = new BookmarkFragment();
    }

    private void initPreferencesFragment() {
        preferencesFragment = (Preferences) fragmentManager.findFragmentByTag(TAG_PREFERENCE_FRAGMENT);
        if(preferencesFragment == null) preferencesFragment = new Preferences();
    }

    private void initCoverFragment() {
        coverFragment = (CoverFragment) fragmentManager.findFragmentByTag(TAG_COVER_FRAGMENT);
        if(coverFragment == null) coverFragment = new CoverFragment();
    }

    private void initUsermenuFragment() {
        userMenuFragment = (UserMenuFragment) fragmentManager.findFragmentByTag(TAG_USERMENU_FRAGMENT);
        if(userMenuFragment == null) userMenuFragment = new UserMenuFragment();
    }

    private void initHelpFragment() {
        helpCreditsFragment = (HelpCreditsFragment) fragmentManager.findFragmentByTag(TAG_HELP_FRAGMENT);
        if(helpCreditsFragment == null) helpCreditsFragment = new HelpCreditsFragment();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if((grantResults[0] == -1)||(grantResults[1] == -1)) model.permissions = false;
        else {
            model.permissions = true;
            showMediaPlayerFragments(false);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        repository.saveCurrentBookAndChapter();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        audiobookServiceBinder = null;
        repository.saveCurrentBookAndChapter();
        if(!isChangingConfigurations()) {
            if(mediaController != null) {
                mediaController.getTransportControls().stop();
                mediaController.unregisterCallback(callback);
                mediaController = null;
            }
        } else {
            model.screenRotate = true;
            if(mediaController != null) mediaController.unregisterCallback(callback);
        }
        unbindService(serviceConnection);
        exec.shutdown();
    }

    public void verifyPermissions(Activity activity) {
        int permissionStorage = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permissionStorage != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        } else {
            model.permissions = true;
        }
    }

    @Override
    public void onBackPressed() {
        OnBackPressedListener backPressedListener = null;
        for (Fragment fragment : fragmentManager.getFragments()) {
            if (fragment instanceof OnBackPressedListener) {
                backPressedListener = (OnBackPressedListener) fragment;
                break;
            }
        }
        if (backPressedListener != null) {
            backPressedListener.onBackPressed();
        } else {
            moveTaskToBack(true);
        }
    }

    @Override
    public MediaControllerCompat getMediaControllerCompat() {
        if (serviceConnection == null) {
            serviceConnection = new BookplayerSconn();
        }
        return mediaController;
    }

    @Override
    public void goBack() {
        if(currentState == PlaybackStateCompat.STATE_NONE) showMediaPlayerFragments(false);
        else showMediaPlayerFragments(true);
    }

    @Override
    public void onBookmarkInteraction(Integer chNum) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Boolean isBookscaleShown = prefs.getBoolean(Preferences.SHOW_BOOKSCALE, true);
        if(bookScaleFragment == null) initBookscaleFragment();

        if (chNum == null) {
            if((repository.getBook() == null)||
                    (repository.getCurrentChapter().getValue().bId != repository.getBook().bookId)) {
                Toast.makeText(this,
                        getString(R.string.err_bookmark),
                        Toast.LENGTH_SHORT)
                        .show();
                return;
            }
            repository.addBookmark();
            if(isBookscaleShown) bookScaleFragment.onAddBookmark();
            Toast.makeText(this,
                    getString(R.string.add_bookmark),
                    Toast.LENGTH_SHORT)
                    .show();
        } else if(isBookscaleShown) bookScaleFragment.onDeleteBookmark(chNum);
    }

    @Override
    public void onUserSeeking(int progress) {
        if(bookScaleFragment == null) initBookscaleFragment();
        bookScaleFragment.setCurrentProgress(progress);
        if(bookInfoFragment == null) initBookinfoFragment();
        bookInfoFragment.setBookRemainTime(progress);
    }

    @Override
    public boolean hasBooksInStorage() {
        return repository.hasBooksInStorage();
    }

    private void goToPlayerFragments() {

        constraintLayout.setBackgroundColor(getResources().getColor(R.color.paletteFive));

        ConstraintSet set = new ConstraintSet();
        set.clone(constraintLayout);
        set.connect(R.id.playerLayout, ConstraintSet.TOP, R.id.guidelineMiddle, ConstraintSet.BOTTOM);

        if(getResources().getConfiguration().orientation == 2)
            set.connect(R.id.playerLayout, ConstraintSet.END, R.id.guidelineVertical, ConstraintSet.START);

        set.applyTo(constraintLayout);
    }

    private void prepareConstraintSet() {

        ConstraintSet set = new ConstraintSet();
        set.clone(constraintLayout);
        set.connect(R.id.playerLayout, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);

        if(getResources().getConfiguration().orientation == 2)
            set.connect(R.id.playerLayout, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);

        TransitionManager.beginDelayedTransition(constraintLayout);

        set.applyTo(constraintLayout);
        constraintLayout.setBackgroundColor(getResources().getColor(R.color.paletteOne));
    }

    @Override
    public void showMediaPlayerFragments(boolean flag) {
        if(audioPlayerFragment == null) initPlayerFragment();

        if(model.getFragmentStatus() != SHOW_PLAYER_FRAGMENTS) {
            getSupportActionBar().setTitle("");
            fragmentManager.beginTransaction()
                .replace(R.id.playerLayout, audioPlayerFragment, TAG_PLAYER_FRAGMENT)
                .commitAllowingStateLoss();
        }

        if(flag) {
            showBookInfo();
            boolean needBookscaleShown = PreferenceManager.getDefaultSharedPreferences(this)
                    .getBoolean(Preferences.SHOW_BOOKSCALE, true);

            showBookScale(needBookscaleShown);

        } else {
            if(userMenuFragment == null) initUsermenuFragment();

            Fragment f = fragmentManager.findFragmentByTag(TAG_SCALE_FRAGMENT);
            if(f != null) {
                fragmentManager.beginTransaction().remove(f).commitAllowingStateLoss();
            }
            if(fragmentManager.findFragmentByTag(TAG_INFO_FRAGMENT) != null) {
                fragmentManager.beginTransaction().replace(R.id.bookInfoLayout, userMenuFragment, TAG_USERMENU_FRAGMENT).commitAllowingStateLoss();
            } else {
                if(fragmentManager.findFragmentByTag(TAG_USERMENU_FRAGMENT) != null)
                    fragmentManager.beginTransaction().show(userMenuFragment).commit();
                else fragmentManager.beginTransaction().add(R.id.bookInfoLayout, userMenuFragment, TAG_USERMENU_FRAGMENT).commitAllowingStateLoss();
            }
        }
        setFragmentStatus(SHOW_PLAYER_FRAGMENTS);
    }

    @Override
    public void showBookScale(boolean flag) {
        if(bookScaleFragment == null) initBookscaleFragment();
        if(coverFragment == null) initCoverFragment();

        fragmentTransaction = fragmentManager.beginTransaction();
        if(flag) {
            if(fragmentManager.findFragmentById(R.id.bookScaleLayout) == null) {
                fragmentTransaction.add(R.id.bookScaleLayout, bookScaleFragment, TAG_SCALE_FRAGMENT);
            } else if(fragmentManager.findFragmentById(R.id.bookScaleLayout) == coverFragment) {
                fragmentTransaction.remove(coverFragment)
                        .add(R.id.bookScaleLayout, bookScaleFragment, TAG_SCALE_FRAGMENT);
            }
            if(bookScaleFragment.isHidden()) fragmentTransaction.show(bookScaleFragment);
            fragmentTransaction.commit();
        } else {
            if(coverFragment.isAdded()) {
                if(coverFragment.isHidden()) fragmentTransaction.show(coverFragment);
            } else {
                fragmentTransaction.remove(bookScaleFragment)
                        .add(R.id.bookScaleLayout, coverFragment, TAG_COVER_FRAGMENT);
            }
            fragmentTransaction.commit();
        }
    }

    private void showBookInfo() {
        if(bookInfoFragment == null) initBookinfoFragment();
        if(userMenuFragment == null) initUsermenuFragment();

        fragmentTransaction = fragmentManager.beginTransaction();
        if(fragmentManager.findFragmentById(R.id.bookInfoLayout) == null) {
            fragmentTransaction.add(R.id.bookInfoLayout, bookInfoFragment, TAG_INFO_FRAGMENT);
        } else if(fragmentManager.findFragmentById(R.id.bookInfoLayout) == userMenuFragment) {
            fragmentTransaction.replace(R.id.bookInfoLayout, bookInfoFragment, TAG_INFO_FRAGMENT);
        } else {
            fragmentTransaction.show(bookInfoFragment);
        }
        fragmentTransaction.commit();
    }

    @Override
    public void showFileManager(boolean flag) {
        if(!model.permissions) {
            showDenyPermissionsToast();
            return;
        }
        if(fileManagerFragment == null) initFileManagerFragment();
        if(flag) {
            getSupportActionBar().setTitle(R.string.title_file_manager);

            fragmentTransaction = fragmentManager.beginTransaction();
            fragmentTransaction.replace(R.id.playerLayout, fileManagerFragment, TAG_FILEMANAGER_FRAGMENT);
            hidePlayerFragments();
            fragmentTransaction.commit();

            setFragmentStatus(SHOW_FILE_MANAGER);
        } else {
            showMediaPlayerFragments(true);
            //bookInfoFragment.setLoad();
        }
    }

    @Override
    public void showBookmarks(boolean flag, Long bookId) {
        if(!model.permissions) {
            showDenyPermissionsToast();
            return;
        }
        if(bookmarkFragment == null) initBookmarksFragment();

        if(flag) {
            getSupportActionBar().setTitle(R.string.title_bookmarks);

            if((bookId != null)&&(bookId != repository.getBook().bookId)) {
                Bundle args = new Bundle();
                args.putLong(BookmarkFragment.BOOKMARK_BUNDLE_KEY, bookId);
                bookmarkFragment.setArguments(args);
            }

            fragmentTransaction = fragmentManager.beginTransaction()
                    .replace(R.id.playerLayout, bookmarkFragment, TAG_BOOKMARKS_FRAGMENT);
            hidePlayerFragments();
            fragmentTransaction.commit();
            setFragmentStatus(SHOW_BOOKMARKS);
        } else {
            showMediaPlayerFragments(true);
        }
    }

    @Override
    public void showLastBooks(boolean flag) {
        if(!model.permissions) {
            showDenyPermissionsToast();
            return;
        }
        if(lastBooksFragment == null) initLastbooksFragment();

        if(flag) {
            getSupportActionBar().setTitle(R.string.title_lastbooks);

            if((repository.getBook() != null)&&(currentChapter != null)) {
                repository.saveCurrentBookAndChapter();
            }
            fragmentTransaction = fragmentManager.beginTransaction()
                    .replace(R.id.playerLayout, lastBooksFragment, TAG_LASTBOOKS_FRAGMENT);
            hidePlayerFragments();
            fragmentTransaction.commit();
            setFragmentStatus(SHOW_LAST_BOOKS);
        } else {
            showMediaPlayerFragments(true);
        }
    }

    private void showPreferences() {

        if(preferencesFragment == null) initPreferencesFragment();

        getSupportActionBar().setTitle(R.string.title_preferences);

        fragmentTransaction = fragmentManager.beginTransaction()
                .replace(R.id.playerLayout, preferencesFragment, TAG_PREFERENCE_FRAGMENT);
        hidePlayerFragments();
        fragmentTransaction.commit();
        setFragmentStatus(SHOW_PREFERENCES);
    }

    private void showHelp() {

        if(helpCreditsFragment == null) initHelpFragment();

        //меняем Title...
        getSupportActionBar().setTitle(R.string.title_credits);

        fragmentTransaction = fragmentManager.beginTransaction()
                .replace(R.id.playerLayout, helpCreditsFragment, TAG_HELP_FRAGMENT);
        hidePlayerFragments();
        fragmentTransaction.commit();
        setFragmentStatus(SHOW_HELP);
    }

    private void hidePlayerFragments() {

        Fragment fragmentInBookinfo = fragmentManager.findFragmentById(R.id.bookInfoLayout);
        Fragment fragmentInBookscale = fragmentManager.findFragmentById(R.id.bookScaleLayout);

        if((fragmentInBookinfo != null)&&(fragmentInBookinfo.isVisible())) {
            fragmentTransaction.hide(fragmentInBookinfo);
        }

        if((fragmentInBookscale != null)&&(fragmentInBookscale.isVisible())) {
            fragmentTransaction.hide(fragmentInBookscale);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        this.menu = menu;
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case R.id.menu_about:
                showHelp();
                return true;
            case R.id.menu_bookmarks:
                showBookmarks(true, null);
                return true;
            case R.id.menu_library:
                showLastBooks(true);
                return true;
            case R.id.menu_file_manager:
                showFileManager(true);
                return true;
            case R.id.menu_settings:
                showPreferences();
                return true;
            case R.id.menu_play: {
                switch (currentState) {
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

    @Override
    public int[] getDisplayMetrics() {
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int width = size.x;
        int height = size.y;

        return new int[] {width, height};
    }

    private void showDenyPermissionsToast() {
        Toast.makeText(this,
                getString(R.string.deny_permissions),
                Toast.LENGTH_SHORT)
                .show();
    }

    public interface OnBackPressedListener {
        void onBackPressed();
    }

    public interface OnPlaybackStateChangedListener {
        void onPlayMedia();
        void onPauseMedia();
    }

    class BookplayerSconn implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
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
            audiobookServiceBinder = null;
            if(mediaController != null) {
                mediaController.unregisterCallback(callback);
                mediaController = null;
            }
        }
    }
}
