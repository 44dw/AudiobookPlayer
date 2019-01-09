package com.a44dw.audiobookplayer;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static com.a44dw.audiobookplayer.AudiobookService.currentState;
import static com.a44dw.audiobookplayer.MainActivity.exec;

public class BookRepository {

    private static final int MESSAGE_UPDATE_CHAPTER = 1;
    private static final int MESSAGE_ALEREADY_SAVED = 2;

    public static final int GAP = 1000;

    private static final BookRepository instance = new BookRepository();
    private MutableLiveData<Chapter> currentChapter;
    private MutableLiveData<String> currentBookName;
    static BookplayerDatabase database;
    private Book currentBook;
    private int nowPlayingPosition = 0;
    private int nowPlayingChapterNumber;
    private static RepositoryHandler handler;
    private boolean alreadySaved = false;

    public LinkedList<Integer> chapterNumStack;

    public static BookRepository getInstance() {
        return instance;
    }

    private BookRepository() {
        if(chapterNumStack == null) chapterNumStack = new LinkedList<>();
        if(handler == null) handler = new RepositoryHandler(this);
    }

    public BookplayerDatabase getDatabase(Context context) {
        if (database == null) {
            database = BookplayerDatabase.getDatabase(context);
        }

        return database;
    }

    public LiveData<Chapter> getCurrentChapter() {
        if (currentChapter == null) currentChapter = new MutableLiveData<>();
        return currentChapter;
    }

    public LiveData<String> getBookName() {
        if (currentBookName == null) currentBookName = new MutableLiveData<>();
        return currentBookName;
    }

    public void updateCurrentChapter(Chapter newChapter) {
        if (currentChapter == null) currentChapter = new MutableLiveData<>();
        Chapter oldChapter = currentChapter.getValue();
        if ((oldChapter != null)&&(!oldChapter.equals(newChapter))&&(currentBook.bookId == oldChapter.bId)) {
            chapterNumStack.add(nowPlayingChapterNumber);
            updateChapterInBook(oldChapter);
        }
        boolean stateNotPlayed = ((currentState == PlaybackStateCompat.STATE_NONE) ||
                (currentState == (PlaybackStateCompat.STATE_STOPPED)) ||
                (currentState == (PlaybackStateCompat.STATE_BUFFERING)));
        if(!stateNotPlayed) currentState = PlaybackStateCompat.STATE_BUFFERING;
        if((currentBook != null) && (newChapter != null)){
            setNowPlayingChapterNumber(newChapter, currentBook);
        }
        currentChapter.setValue(newChapter);
    }

    private void updateBookName(String newName, boolean fromMainThread) {
        if (currentBookName == null) currentBookName = new MutableLiveData<>();
        if (fromMainThread) currentBookName.setValue(newName);
        else currentBookName.postValue(newName);
    }

    private void updateBook(Book newNowPlayingBook, boolean fromMainThread) {
        currentBook = newNowPlayingBook;
        updateBookName(newNowPlayingBook.filepath, fromMainThread);
    }

    public Book getBook() {
        return currentBook;
    }

    public void addBookmark() {
        if ((currentBook != null)&&
                (currentBook.bookId == currentChapter.getValue().bId)) {
            currentBook.chapters.get(nowPlayingChapterNumber).addBookmark(nowPlayingPosition);
            if(currentChapter.getValue() != null) {
                exec.execute(new Runnable() {
                    @Override
                    public void run() {
                        database.bookmarkDao().insert(new Bookmark(currentChapter.getValue(),
                                currentBook.bookId,
                                nowPlayingPosition));
                    }
                });
            }
        }
    }

    private void updateChapterInBook(final Chapter ch) {
        if ((currentBook != null) && (currentChapter.getValue() != null)) {

            if(ch.chapterId == 0)
                ch.chapterId = currentBook.chapters.get(nowPlayingChapterNumber).chapterId;
            if(ch.bId == 0)
                ch.bId = currentBook.bookId;
            ch.progress = nowPlayingPosition;
            ch.done = nowPlayingPosition > ch.duration - GAP;

            currentBook.updateInPlaylist(ch);
            currentBook.lastPlayedChapterNum = nowPlayingChapterNumber;

            exec.execute(new Runnable() {
                @Override
                public void run() {
                    database.chapterDao().update(ch);
                }
            });
        }
    }

    public int getNowPlayingPosition() {
        return nowPlayingPosition;
    }

    public void updateNowPlayingPosition(int pos) {
        nowPlayingPosition = pos;
    }

    public int getNowPlayingChapterNumber() {
        return nowPlayingChapterNumber;
    }

    private void setNowPlayingChapterNumber(Chapter ch, Book book) {
        int num = book.findInChapters(ch);
        if (num == -1) return;
        this.nowPlayingChapterNumber = num;
    }

    public void reset() {
        currentBook = null;
        currentBookName.setValue("");
        currentChapter.setValue(null);
        nowPlayingChapterNumber = 0;

        currentState = PlaybackStateCompat.STATE_NONE;
    }

    public void skipToNext() {
        int nextNum = nowPlayingChapterNumber + 1;
        Chapter nextCh;

        while (nextNum < currentBook.chapters.size()) {
            nextCh = currentBook.chapters.get(nextNum);
            if(!nextCh.done) {
                updateCurrentChapter(nextCh);
                return;
            }
            else nextNum++;
        }
        updateCurrentChapter(null);
    }

    public void skipToPrevious() {
        int prevNum = nowPlayingChapterNumber - 1;

        while (prevNum >= 0) {
            Chapter prevCh = currentBook.chapters.get(prevNum);
            if(!prevCh.done) {
                updateCurrentChapter(prevCh);
                break;
            } else prevNum--;
        }
    }

    public void openFile(final File openedFile) {

        Chapter nowCh = currentChapter.getValue();

        if((nowCh != null)&&(openedFile.getAbsolutePath().equals(nowCh.filepath))) return;
        saveCurrentBookAndChapter();
        exec.execute(new Runnable() {
            Message msg;
            @Override
            public void run() {
                Chapter openedChapter = database.chapterDao().getByFilepath(openedFile.getAbsolutePath());

                if (openedChapter == null) {
                    openedChapter = new Chapter(openedFile);
                } else {
                    ArrayList<Bookmark> bookmarkList = (ArrayList<Bookmark>) database.bookmarkDao().getAllByChapterId(openedChapter.chapterId);
                    if((bookmarkList != null)&&(bookmarkList.size() > 0)) openedChapter.bookmarks = bookmarkList;
                }
                if (openedChapter.exists()) {
                    msg = handler.obtainMessage(MESSAGE_UPDATE_CHAPTER, openedChapter);
                    handler.sendMessage(msg);
                }
            }
        });
    }

    public void openBookmark(Bookmark b) {
        if(b.bId == currentBook.bookId) {
            int numInPl = currentBook.findInChapters(b.pathToFile);
            Chapter ch = currentBook.chapters.get(numInPl);
            ch.progress = b.time;
            updateCurrentChapter(ch);
        } else {
            ChapterDaoGetById chapterDaoGetById = new ChapterDaoGetById();
            BookDaoGetById bookDaoGetById = new BookDaoGetById();
            chapterDaoGetById.execute(b.chId);
            bookDaoGetById.execute(b.bId);
            try {
                Chapter ch = chapterDaoGetById.get();
                Book book = bookDaoGetById.get();
                if((ch != null)&&(book != null)) {
                    ch.progress = b.time;
                    updateBook(book, true);
                    updateCurrentChapter(ch);
                }
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
    }

    public void openBook(long bookId) {
        saveCurrentBookAndChapter();

        Book loadedBook = loadBook(bookId);

        if(loadedBook == null) return;

        int lastPlayedChapterNum = loadedBook.lastPlayedChapterNum;

        Chapter startChapter;

        if(lastPlayedChapterNum >= loadedBook.chapters.size()) {
            startChapter = loadedBook.chapters.get(0);
            loadedBook.lastPlayedChapterNum = 0;
        } else startChapter = loadedBook.chapters.get(lastPlayedChapterNum);

        updateBook(loadedBook, true);
        updateCurrentChapter(startChapter);
    }

    private void obtainBook(Chapter ch) {
        if((currentBook != null)&&(ch.bId == currentBook.bookId)) return;
        if(getCurrentChapter().getValue() != null) currentBookName.setValue("");
        new CreateNewBook(this).execute(ch);
    }

    static private class CreateNewBook extends AsyncTask<Chapter, Void, Void> {

        private final WeakReference<BookRepository> repoWR;

        CreateNewBook(BookRepository repository) {
            this.repoWR = new WeakReference<>(repository);
        }

        Book saveNewBook(File[] filesInDirectory) {
            ArrayList<Chapter> chapters = new ArrayList<>();
            for(File f : filesInDirectory) {
                Chapter chapter = new Chapter(f);
                if(chapter.exists()) chapters.add(chapter);
            }
            Book newBook = new Book(new File(chapters.get(0).filepath).getParentFile());
            newBook.bookId = database.bookDao().insert(newBook);

            for(Chapter ch : chapters) {
                ch.bId = newBook.bookId;
                ch.chapterId = database.chapterDao().insert(ch);
            }

            newBook.chapters = chapters;
            newBook.bookDuration = newBook.calcDuration();
            return newBook;
        }

        Book loadBook(Long id) {
            Book book = database.bookDao().getById(id);
            if(book != null) {
                book.chapters = (ArrayList<Chapter>) database.chapterDao().getByBookId(id);

                ArrayList<Bookmark> bookmarkList = (ArrayList<Bookmark>) database.bookmarkDao().getAllByBookId(id);
                for(Bookmark b : bookmarkList) {
                    for (Chapter ch : book.chapters) {
                        if(b.chId == ch.chapterId) {
                            if(ch.bookmarks == null) ch.bookmarks = new ArrayList<>();
                            ch.bookmarks.add(b);
                            break;
                        }
                    }
                }
            }
            return book;
        }

        Book loadBook(String filepath) {
            File parentFile = new File(filepath).getParentFile();

            Book book = database.bookDao().getBookByPath(parentFile.getAbsolutePath());
            if(book != null) {
                book.chapters = (ArrayList<Chapter>) database.chapterDao().getByBookId(book.bookId);

                ArrayList<Bookmark> bookmarkList = (ArrayList<Bookmark>) database.bookmarkDao().getAllByBookId(book.bookId);
                for(Bookmark b : bookmarkList) {
                    for (Chapter ch : book.chapters) {
                        if(b.chId == ch.chapterId) {
                            if(ch.bookmarks == null) ch.bookmarks = new ArrayList<>();
                            ch.bookmarks.add(b);
                            break;
                        }
                    }
                }
                if(repoWR.get() != null) {
                    return repoWR.get().validateBook(book);
                } else return book;
            }
            return null;
        }

        @Override
        protected Void doInBackground(Chapter... ch) {
            BookRepository repository = repoWR.get();
            Book obtainedBook;
            boolean inStorage;

            if(ch[0].bId > 0) inStorage = true;
            else {
                File parentFile = new File(ch[0].filepath).getParentFile();
                String path = parentFile.getAbsolutePath();
                Book book = database.bookDao().getBookByPath(path);
                inStorage = book != null;
            }

            if(!inStorage) {
                File parentFile = new File(ch[0].filepath).getParentFile();
                File[] filesInDirectory = parentFile.listFiles();
                obtainedBook = saveNewBook(filesInDirectory);
            } else {
                obtainedBook = ch[0].bId > 0 ? loadBook(ch[0].bId) : loadBook(ch[0].filepath);
            }
            if(obtainedBook != null) {
                Chapter nowCh = repository.currentChapter.getValue();
                if(nowCh == null) return null;
                repository.setNowPlayingChapterNumber(nowCh, obtainedBook);

                repository.updateBook(obtainedBook, false);

                if(nowCh.chapterId == 0) nowCh.chapterId = repository.currentBook.chapters.get(
                        repository.nowPlayingChapterNumber).chapterId;
                if(nowCh.bId == 0) nowCh.bId = repository.currentBook.bookId;
            }
            return null;
        }
    }

    private Book loadBook(final long bookId) {
        BookDaoGetById bookDaoGetById = new BookDaoGetById();
        bookDaoGetById.execute(bookId);
        try {
            Book book = bookDaoGetById.get();
            if(book != null) return validateBook(book);
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return null;
    }

    private Book validateBook(final Book book) {
        File[] filesInFolder = new File(book.filepath).listFiles();
        ArrayList<String> filepathInFolder = new ArrayList<>();
        for(File f : filesInFolder) filepathInFolder.add(f.getAbsolutePath());
        for (int i=0; i<book.chapters.size(); i++) {
            final Chapter ch = book.chapters.get(i);
            if(!ch.exists()) {
                exec.execute(new Runnable() {
                    @Override
                    public void run() {
                    database.chapterDao().delete(ch);
                    }
                });
                book.chapters.remove(i);
                i--;
            } else {
                filepathInFolder.remove(ch.filepath);
            }
        }
        if(filepathInFolder.size() > 0) {
            for(String s : filepathInFolder) {
                final Chapter ch = new Chapter(new File(s));
                if(!ch.exists()) continue;
                ch.bId = book.bookId;
                exec.execute(new Runnable() {
                    @Override
                    public void run() {
                        database.chapterDao().insert(ch);
                    }
                });
                book.chapters.add(ch);
            }
        }
        return book;
    }

    public void saveCurrentBookAndChapter() {

        if((alreadySaved)||(currentBook == null)) return;

        final Chapter chapter = currentChapter.getValue();

        if((chapter != null)&&(chapter.exists())) {
            if(chapter.chapterId == 0)
                chapter.chapterId = currentBook.chapters.get(nowPlayingChapterNumber).chapterId;
            if(chapter.bId == 0)
                chapter.bId = currentBook.bookId;
            chapter.progress = nowPlayingPosition;

            currentBook.lastPlayedChapterNum = nowPlayingChapterNumber;
            currentBook.updateInPlaylist(chapter);

            final Book cbook = currentBook;

            exec.execute(new Runnable() {
                @Override
                public void run() {
                    database.chapterDao().update(chapter);
                    database.bookDao().update(cbook);
                }
            });
        } else {
            final Book cbook = currentBook;

            exec.execute(new Runnable() {
                @Override
                public void run() {
                    database.bookDao().update(cbook);
                }
            });
        }
        alreadySaved = true;
        handler.sendEmptyMessageDelayed(MESSAGE_ALEREADY_SAVED, 3000);
    }

    public boolean hasBooksInStorage() {
        BookDaoGetCount bookDaoGetCount = new BookDaoGetCount();
        bookDaoGetCount.execute();
        try {
            long result = bookDaoGetCount.get();
            if(result > 0) return true;
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static void nullBookProgress(final Book book) {
        exec.execute(new Runnable() {
            @Override
            public void run() {
                database.bookDao().update(book);
                ArrayList<Chapter> chapters = (ArrayList<Chapter>)database.chapterDao().getByBookId(book.bookId);
                for (Chapter ch : chapters) {
                    if(ch.progress > 0) {
                        ch.progress = 0;
                        if(ch.done) ch.done = false;
                    }
                    database.chapterDao().update(ch);
                }
            }
        });
    }

    static class RepositoryHandler extends Handler {
        WeakReference<BookRepository> wrRepo;

        RepositoryHandler(BookRepository repository) {
            wrRepo = new WeakReference<>(repository);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            BookRepository repo = wrRepo.get();
            switch (msg.what) {
                case MESSAGE_UPDATE_CHAPTER: {
                    Chapter ch = (Chapter) msg.obj;
                    repo.updateCurrentChapter(ch);
                    repo.obtainBook(ch);
                    break;
                }
                case MESSAGE_ALEREADY_SAVED: {
                    repo.alreadySaved = false;
                }
            }
        }
    }

    static private class BookDaoGetCount extends AsyncTask<Void, Void, Long> {
        @Override
        protected Long doInBackground(Void... voids) {
            return database.bookDao().getCount();
        }
    }

    static public class BookmarkDaoGetByBookId extends AsyncTask<Long, Void, ArrayList<Bookmark>> {
        @Override
        protected ArrayList<Bookmark> doInBackground(Long... id) {
            return (ArrayList<Bookmark>)database.bookmarkDao().getAllByBookId(id[0]);
        }
    }

    static public class BookDaoGetById extends AsyncTask<Long, Void, Book> {
        @Override
        protected Book doInBackground(Long... bookId) {
            Book book = database.bookDao().getById(bookId[0]);
            if(book != null) {
                book.chapters = (ArrayList<Chapter>) database.chapterDao().getByBookId(bookId[0]);

                ArrayList<Bookmark> bookmarkList = (ArrayList<Bookmark>) database.bookmarkDao().getAllByBookId(bookId[0]);
                for(Bookmark b : bookmarkList) {
                    for (Chapter ch : book.chapters) {
                        if(b.chId == ch.chapterId) {
                            if(ch.bookmarks == null) ch.bookmarks = new ArrayList<>();
                            ch.bookmarks.add(b);
                            break;
                        }
                    }
                }
            }
            return book;
        }
    }

    static public class BookDaoGetListByIds extends AsyncTask<long[], Void, List<Book>> {
        @Override
        protected List<Book> doInBackground(long[]... longs) {
            List<Book> books = new ArrayList<>();
            for(Long bookId : longs[0]) {
                Book book = database.bookDao().getById(bookId);
                if(book != null) {
                    book.chapters = (ArrayList<Chapter>) database.chapterDao().getByBookId(bookId);

                    ArrayList<Bookmark> bookmarkList = (ArrayList<Bookmark>) database.bookmarkDao().getAllByBookId(bookId);
                    for(Bookmark b : bookmarkList) {
                        for (Chapter ch : book.chapters) {
                            if(b.chId == ch.chapterId) {
                                if(ch.bookmarks == null) ch.bookmarks = new ArrayList<>();
                                ch.bookmarks.add(b);
                            }
                        }
                    }
                    books.add(book);
                }
            }
            return books;
        }
    }

    static public class BookmarkDaoGetById extends AsyncTask<Long, Void, Bookmark> {
        @Override
        protected Bookmark doInBackground(Long... id) {
            return database.bookmarkDao().getById(id[0]);
        }
    }

    static public class ChapterDaoGetAllByBookId extends AsyncTask<Long, Void, List<Chapter>> {
        @Override
        protected List<Chapter> doInBackground(Long... id) {
            return database.chapterDao().getByBookId(id[0]);
        }
    }

    static public class ChapterDaoGetFirstByBookId extends AsyncTask<Long, Void, Chapter> {
        @Override
        protected Chapter doInBackground(Long... id) {
            return database.chapterDao().getFirstByBookId(id[0]);
        }
    }

    static public class ChapterDaoGetById extends AsyncTask<Long, Void, Chapter> {
        @Override
        protected Chapter doInBackground(Long... id) {
            return database.chapterDao().getById(id[0]);
        }
    }
}
