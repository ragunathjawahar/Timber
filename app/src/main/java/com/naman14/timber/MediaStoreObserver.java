package com.naman14.timber;

import android.database.ContentObserver;
import android.os.Handler;
import android.util.Log;

class MediaStoreObserver extends ContentObserver implements Runnable {
    private static final long REFRESH_DELAY = 500;
    private final MusicService musicService;
    private final Handler mHandler;

    public MediaStoreObserver(MusicService musicService, Handler handler) {
        super(handler);
        this.musicService = musicService;
        mHandler = handler;
    }

    @Override
    public void onChange(boolean selfChange) {
        mHandler.removeCallbacks(this);
        mHandler.postDelayed(this, REFRESH_DELAY);
    }

    @Override
    public void run() {
        Log.e("ELEVEN", "calling refresh!");
        musicService.refresh();
    }
}
