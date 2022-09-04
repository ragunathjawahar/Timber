/*
 * Copyright (C) 2012 Andrew Neal
 * Copyright (C) 2014 The CyanogenMod Project
 * Copyright (C) 2015 Naman Dwivedi
 *
 * Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.naman14.timber;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaMetadataEditor;
import android.media.MediaMetadataRetriever;
import android.media.RemoteControlClient;
import android.media.audiofx.AudioEffect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio.AlbumColumns;
import android.provider.MediaStore.Audio.AudioColumns;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.Log;

import com.naman14.timber.helpers.MediaButtonIntentReceiver;
import com.naman14.timber.helpers.MusicPlaybackTrack;
import com.naman14.timber.lastfmapi.LastFmClient;
import com.naman14.timber.lastfmapi.models.LastfmUserSession;
import com.naman14.timber.lastfmapi.models.ScrobbleQuery;
import com.naman14.timber.permissions.Nammu;
import com.naman14.timber.provider.MusicPlaybackState;
import com.naman14.timber.provider.RecentStore;
import com.naman14.timber.provider.SongPlayCount;
import com.naman14.timber.utils.PreferencesUtility;
import com.naman14.timber.utils.TimberUtils;
import com.naman14.timber.utils.TimberUtils.IdType;
import com.nostra13.universalimageloader.core.ImageLoader;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.ListIterator;

import de.Maxr1998.trackselectorlib.NotificationHelper;

@SuppressLint("NewApi")
public class MusicService extends Service {
    public static final String PLAYSTATE_CHANGED = "com.naman14.timber.playstatechanged";
    public static final String POSITION_CHANGED = "com.naman14.timber.positionchanged";
    public static final String META_CHANGED = "com.naman14.timber.metachanged";
    public static final String QUEUE_CHANGED = "com.naman14.timber.queuechanged";
    public static final String PLAYLIST_CHANGED = "com.naman14.timber.playlistchanged";
    public static final String REPEATMODE_CHANGED = "com.naman14.timber.repeatmodechanged";
    public static final String SHUFFLEMODE_CHANGED = "com.naman14.timber.shufflemodechanged";
    public static final String TRACK_ERROR = "com.naman14.timber.trackerror";
    public static final String TIMBER_PACKAGE_NAME = "com.naman14.timber";
    public static final String MUSIC_PACKAGE_NAME = "com.android.music";
    public static final String SERVICECMD = "com.naman14.timber.musicservicecommand";
    public static final String TOGGLEPAUSE_ACTION = "com.naman14.timber.togglepause";
    public static final String PAUSE_ACTION = "com.naman14.timber.pause";
    public static final String STOP_ACTION = "com.naman14.timber.stop";
    public static final String PREVIOUS_ACTION = "com.naman14.timber.previous";
    public static final String PREVIOUS_FORCE_ACTION = "com.naman14.timber.previous.force";
    public static final String NEXT_ACTION = "fcom.naman14.timber.next";
    public static final String REPEAT_ACTION = "com.naman14.timber.repeat";
    public static final String SHUFFLE_ACTION = "com.naman14.timber.shuffle";
    public static final String FROM_MEDIA_BUTTON = "frommediabutton";
    public static final String REFRESH = "com.naman14.timber.refresh";
    public static final String CMDNAME = "command";
    public static final String CMDTOGGLEPAUSE = "togglepause";
    public static final String CMDSTOP = "stop";
    public static final String CMDPAUSE = "pause";
    public static final String CMDPLAY = "play";
    public static final String CMDPREVIOUS = "previous";
    public static final String CMDNEXT = "next";
    public static final String UPDATE_PREFERENCES = "updatepreferences";
    public static final int NEXT = 2;
    public static final int LAST = 3;
    public static final int SHUFFLE_NONE = 0;
    public static final int SHUFFLE_NORMAL = 1;
    public static final int SHUFFLE_AUTO = 2;
    public static final int REPEAT_NONE = 0;
    public static final int REPEAT_CURRENT = 1;
    public static final int REPEAT_ALL = 2;
    public static final int MAX_HISTORY_SIZE = 1000;
    static final String TAG = "MusicPlaybackService";
    static final boolean D = false;
    private static final String SHUTDOWN = "com.naman14.timber.shutdown";
    private static final int IDCOLIDX = 0;
    static final int TRACK_ENDED = 1;
    static final int TRACK_WENT_TO_NEXT = 2;
    static final int RELEASE_WAKELOCK = 3;
    static final int SERVER_DIED = 4;
    static final int FOCUSCHANGE = 5;
    static final int FADEDOWN = 6;
    static final int FADEUP = 7;
    static final int IDLE_DELAY = 5 * 60 * 1000;
    private static final long REWIND_INSTEAD_PREVIOUS_THRESHOLD = 3000;
    private static final String[] PROJECTION = new String[]{
            "audio._id AS _id", MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ARTIST_ID
    };
    private static final String[] ALBUM_PROJECTION = new String[]{
            MediaStore.Audio.Albums.ALBUM, MediaStore.Audio.Albums.ARTIST,
            MediaStore.Audio.Albums.LAST_YEAR
    };
    private static final Shuffler sShuffler = new Shuffler();
    private static final String[] PROJECTION_MATRIX = new String[]{
            "_id", MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ARTIST_ID
    };
    private static LinkedList<Integer> sHistory = new LinkedList<>();
    private final IBinder mBinder = new ServiceStub(this);
    MultiPlayer mPlayer;
    private String mFileToPlay;
    WakeLock mWakeLock;
    private AlarmManager mAlarmManager;
    private PendingIntent mShutdownIntent;
    private boolean mShutdownScheduled;
    Cursor mCursor;
    private Cursor mAlbumCursor;
    private AudioManager mAudioManager;
    private SharedPreferences mPreferences;
    private boolean mServiceInUse = false;
    private boolean mIsSupposedToBePlaying = false;
    private long mLastPlayedTime;
    private boolean mQueueIsSaveable = true;
    boolean mPausedByTransientLossOfFocus = false;

    private MediaSessionCompat mSession;
    private RemoteControlClient mRemoteControlClient;

    private ComponentName mMediaButtonReceiverComponent;

    private int mCardId;

    int mPlayPos = -1;

    int mNextPlayPos = -1;

    private int mOpenFailedCounter = 0;

    private int mMediaMountedCount = 0;

    private int mShuffleMode = SHUFFLE_NONE;

    int mRepeatMode = REPEAT_NONE;

    private int mServiceStartId = -1;

    final Playlist playlist = new Playlist();

    private long[] mAutoShuffleList = null;

    private MusicPlayerHandler mPlayerHandler;

    private final OnAudioFocusChangeListener mAudioFocusListener = new OnAudioFocusChangeListener() {

        @Override
        public void onAudioFocusChange(final int focusChange) {
            mPlayerHandler.obtainMessage(FOCUSCHANGE, focusChange, 0).sendToTarget();
        }
    };
    private HandlerThread mHandlerThread;
    private BroadcastReceiver mUnmountReceiver = null;
    private MusicPlaybackState mPlaybackStateStore;
    private boolean mShowAlbumArtOnLockscreen;
    private boolean mActivateXTrackSelector;
    private SongPlayCount mSongPlayCount;
    private RecentStore mRecentStore;
    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(final Context context, final Intent intent) {
            intent.getStringExtra(CMDNAME);
            handleCommandIntent(intent);
        }
    };
    private ContentObserver mMediaStoreObserver;
    @Override
    public IBinder onBind(final Intent intent) {
        if (D) Log.d(TAG, "Service bound, intent = " + intent);
        cancelShutdown();
        mServiceInUse = true;
        return mBinder;
    }

    @Override
    public boolean onUnbind(final Intent intent) {
        if (D) Log.d(TAG, "Service unbound");
        mServiceInUse = false;
        saveQueue(true);

        if (mIsSupposedToBePlaying || mPausedByTransientLossOfFocus) {
            return true;
        } else if (playlist.isNotEmpty() || mPlayerHandler.hasMessages(TRACK_ENDED)) {
            scheduleDelayedShutdown();
            return true;
        }
        stopSelf(mServiceStartId);

        return true;
    }

    @Override
    public void onRebind(final Intent intent) {
        cancelShutdown();
        mServiceInUse = true;
    }

    private NotificationHandler mNotificationHandler;

    @Override
    public void onCreate() {
        if (D) Log.d(TAG, "Creating service");
        super.onCreate();
        mNotificationHandler = new NotificationHandler(this);

        mNotificationHandler.createNotificationChannel();

        // gets a pointer to the playback state store
        mPlaybackStateStore = MusicPlaybackState.getInstance(this);
        mSongPlayCount = SongPlayCount.getInstance(this);
        mRecentStore = RecentStore.getInstance(this);


        mHandlerThread = new HandlerThread("MusicPlayerHandler",
                android.os.Process.THREAD_PRIORITY_BACKGROUND);
        mHandlerThread.start();


        mPlayerHandler = new MusicPlayerHandler(this, mHandlerThread.getLooper());


        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mMediaButtonReceiverComponent = new ComponentName(getPackageName(),
                MediaButtonIntentReceiver.class.getName());
        mAudioManager.registerMediaButtonEventReceiver(mMediaButtonReceiverComponent);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            setUpMediaSession();
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
            setUpRemoteControlClient();

        mPreferences = getSharedPreferences("Service", 0);
        mCardId = getCardId();

        registerExternalStorageListener();

        mPlayer = new MultiPlayer(this);
        mPlayer.setHandler(mPlayerHandler);

        // Initialize the intent filter and each action
        final IntentFilter filter = new IntentFilter();
        filter.addAction(SERVICECMD);
        filter.addAction(TOGGLEPAUSE_ACTION);
        filter.addAction(PAUSE_ACTION);
        filter.addAction(STOP_ACTION);
        filter.addAction(NEXT_ACTION);
        filter.addAction(PREVIOUS_ACTION);
        filter.addAction(PREVIOUS_FORCE_ACTION);
        filter.addAction(REPEAT_ACTION);
        filter.addAction(SHUFFLE_ACTION);
        filter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        // Attach the broadcast listener
        registerReceiver(mIntentReceiver, filter);

        mMediaStoreObserver = new MediaStoreObserver(this, mPlayerHandler);
        getContentResolver().registerContentObserver(
                MediaStore.Audio.Media.INTERNAL_CONTENT_URI, true, mMediaStoreObserver);
        getContentResolver().registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, mMediaStoreObserver);

        // Initialize the wake lock
        final PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
        mWakeLock.setReferenceCounted(false);


        final Intent shutdownIntent = new Intent(this, MusicService.class);
        shutdownIntent.setAction(SHUTDOWN);

        mAlarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        mShutdownIntent = PendingIntent.getService(this, 0, shutdownIntent, 0);

        scheduleDelayedShutdown();

        reloadQueueAfterPermissionCheck();
        notifyChange(QUEUE_CHANGED);
        notifyChange(META_CHANGED);
        //Try to push LastFMCache
        if (LastfmUserSession.getSession(this) != null) {
            LastFmClient.getInstance(this).Scrobble(null);
        }
        PreferencesUtility pref = PreferencesUtility.getInstance(this);
        mShowAlbumArtOnLockscreen = pref.getSetAlbumartLockscreen();
        mActivateXTrackSelector = pref.getXPosedTrackselectorEnabled();
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private void setUpRemoteControlClient() {
        //Legacy for ICS
        if (mRemoteControlClient == null) {
            Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
            mediaButtonIntent.setComponent(mMediaButtonReceiverComponent);
            PendingIntent mediaPendingIntent = PendingIntent.getBroadcast(this, 0, mediaButtonIntent, 0);

            // create and register the remote control client
            mRemoteControlClient = new RemoteControlClient(mediaPendingIntent);
            mAudioManager.registerRemoteControlClient(mRemoteControlClient);
        }

        mRemoteControlClient.setTransportControlFlags(
                RemoteControlClient.FLAG_KEY_MEDIA_PLAY |
                        RemoteControlClient.FLAG_KEY_MEDIA_PAUSE |
                        RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS |
                        RemoteControlClient.FLAG_KEY_MEDIA_NEXT |
                        RemoteControlClient.FLAG_KEY_MEDIA_STOP);
    }

    private void setUpMediaSession() {
        mSession = new MediaSessionCompat(this, "Timber");
        mSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPause() {
                pause();
                mPausedByTransientLossOfFocus = false;
            }

            @Override
            public void onPlay() {
                play();
            }

            @Override
            public void onSeekTo(long pos) {
                seek(pos);
            }

            @Override
            public void onSkipToNext() {
                gotoNext(true);
            }

            @Override
            public void onSkipToPrevious() {
                prev(false);
            }

            @Override
            public void onStop() {
                pause();
                mPausedByTransientLossOfFocus = false;
                seek(0);
                releaseServiceUiAndStop();
            }
        });
        mSession.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
                          | MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS);
    }

    @Override
    public void onDestroy() {
        if (D) Log.d(TAG, "Destroying service");
        super.onDestroy();
        //Try to push LastFMCache
        if (LastfmUserSession.getSession(this).isLogedin()) {
            LastFmClient.getInstance(this).Scrobble(null);
        }
        // Remove any sound effects
        final Intent audioEffectsIntent = new Intent(
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        audioEffectsIntent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, getAudioSessionId());
        audioEffectsIntent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName());
        sendBroadcast(audioEffectsIntent);


        mAlarmManager.cancel(mShutdownIntent);

        mPlayerHandler.removeCallbacksAndMessages(null);

        if (TimberUtils.isJellyBeanMR2())
            mHandlerThread.quitSafely();
        else mHandlerThread.quit();

        mPlayer.release();
        mPlayer = null;

        mAudioManager.abandonAudioFocus(mAudioFocusListener);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            mSession.release();

        getContentResolver().unregisterContentObserver(mMediaStoreObserver);

        closeCursor();

        unregisterReceiver(mIntentReceiver);
        if (mUnmountReceiver != null) {
            unregisterReceiver(mUnmountReceiver);
            mUnmountReceiver = null;
        }

        mWakeLock.release();
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        if (D) Log.d(TAG, "Got new intent " + intent + ", startId = " + startId);
        mServiceStartId = startId;

        if (intent != null) {
            final String action = intent.getAction();

            if (SHUTDOWN.equals(action)) {
                mShutdownScheduled = false;
                releaseServiceUiAndStop();
                return START_NOT_STICKY;
            }

            handleCommandIntent(intent);
        }

        scheduleDelayedShutdown();

        if (intent != null && intent.getBooleanExtra(FROM_MEDIA_BUTTON, false)) {
            MediaButtonIntentReceiver.completeWakefulIntent(intent);
        }

        return START_NOT_STICKY; //no sense to use START_STICKY with using startForeground
    }

    void scrobble() {
        if (LastfmUserSession.getSession(this).isLogedin()) {
            Log.d("Scrobble", "to LastFM");
            String trackname = getTrackName();
            if (trackname != null)
                LastFmClient.getInstance(this).Scrobble(new ScrobbleQuery(getArtistName(), trackname, System.currentTimeMillis() / 1000));
        }
    }

    private void releaseServiceUiAndStop() {
        if (isPlaying()
                || mPausedByTransientLossOfFocus
                || mPlayerHandler.hasMessages(TRACK_ENDED)) {
            return;
        }

        if (D) Log.d(TAG, "Nothing is playing anymore, releasing notification");
        mNotificationHandler.cancelNotification();
        mAudioManager.abandonAudioFocus(mAudioFocusListener);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            mSession.setActive(false);

        if (!mServiceInUse) {
            saveQueue(true);
            stopSelf(mServiceStartId);
        }
    }

    private void handleCommandIntent(Intent intent) {
        final String action = intent.getAction();
        final String command = SERVICECMD.equals(action) ? intent.getStringExtra(CMDNAME) : null;

        if (D) Log.d(TAG, "handleCommandIntent: action = " + action + ", command = " + command);

        if (NotificationHelper.checkIntent(intent)) {
            goToPosition(mPlayPos + NotificationHelper.getPosition(intent));
            return;
        }

        if (CMDNEXT.equals(command) || NEXT_ACTION.equals(action)) {
            gotoNext(true);
        } else if (CMDPREVIOUS.equals(command) || PREVIOUS_ACTION.equals(action)
                || PREVIOUS_FORCE_ACTION.equals(action)) {
            prev(PREVIOUS_FORCE_ACTION.equals(action));
        } else if (CMDTOGGLEPAUSE.equals(command) || TOGGLEPAUSE_ACTION.equals(action)) {
            if (isPlaying()) {
                pause();
                mPausedByTransientLossOfFocus = false;
            } else {
                play();
            }
        } else if (CMDPAUSE.equals(command) || PAUSE_ACTION.equals(action)) {
            pause();
            mPausedByTransientLossOfFocus = false;
        } else if (CMDPLAY.equals(command)) {
            play();
        } else if (CMDSTOP.equals(command) || STOP_ACTION.equals(action)) {
            pause();
            mPausedByTransientLossOfFocus = false;
            seek(0);
            releaseServiceUiAndStop();
        } else if (REPEAT_ACTION.equals(action)) {
            cycleRepeat();
        } else if (SHUFFLE_ACTION.equals(action)) {
            cycleShuffle();
        } else if (UPDATE_PREFERENCES.equals(action)) {
            onPreferencesUpdate(intent.getExtras());
        }
        else if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(action)) {
            if (PreferencesUtility.getInstance(getApplicationContext()).pauseEnabledOnDetach()) {
                pause();
            }
        }
    }

    private void onPreferencesUpdate(Bundle extras) {
        mShowAlbumArtOnLockscreen = extras.getBoolean("lockscreen", mShowAlbumArtOnLockscreen);
        mActivateXTrackSelector = extras.getBoolean("xtrack",mActivateXTrackSelector);
        LastfmUserSession session = LastfmUserSession.getSession(this);
        session.mToken = extras.getString("lf_token", session.mToken);
        session.mUsername = extras.getString("lf_user", session.mUsername);
        if ("logout".equals(session.mToken)) {
            session.mToken = null;
            session.mUsername = null;
        }
        notifyChange(META_CHANGED);

    }

    private int getCardId() {
        if (TimberUtils.isMarshmallow()) {
            if (Nammu.checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                return getmCardId();
            } else return 0;
        } else {
            return getmCardId();
        }
    }

    private int getmCardId() {
        final ContentResolver resolver = getContentResolver();
        Cursor cursor = resolver.query(Uri.parse("content://media/external/fs_id"), null, null,
                null, null);
        int mCardId = -1;
        if (cursor != null && cursor.moveToFirst()) {
            mCardId = cursor.getInt(0);
            cursor.close();
            cursor = null;
        }
        return mCardId;
    }

    public void closeExternalStorageFiles(final String storagePath) {
        stop(true);
        notifyChange(QUEUE_CHANGED);
        notifyChange(META_CHANGED);
    }

    public void registerExternalStorageListener() {
        if (mUnmountReceiver == null) {
            mUnmountReceiver = new BroadcastReceiver() {


                @Override
                public void onReceive(final Context context, final Intent intent) {
                    final String action = intent.getAction();
                    if (Intent.ACTION_MEDIA_EJECT.equals(action)) {
                        saveQueue(true);
                        mQueueIsSaveable = false;
                        closeExternalStorageFiles(intent.getData().getPath());
                    } else if (Intent.ACTION_MEDIA_MOUNTED.equals(action)) {
                        mMediaMountedCount++;
                        mCardId = getCardId();
                        reloadQueueAfterPermissionCheck();
                        mQueueIsSaveable = true;
                        notifyChange(QUEUE_CHANGED);
                        notifyChange(META_CHANGED);
                    }
                }
            };
            final IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_MEDIA_EJECT);
            filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
            filter.addDataScheme("file");
            registerReceiver(mUnmountReceiver, filter);
        }
    }

    private void scheduleDelayedShutdown() {
        if (D) Log.v(TAG, "Scheduling shutdown in " + IDLE_DELAY + " ms");
        mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + IDLE_DELAY, mShutdownIntent);
        mShutdownScheduled = true;
    }

    private void cancelShutdown() {
        if (D) Log.d(TAG, "Cancelling delayed shutdown, scheduled = " + mShutdownScheduled);
        if (mShutdownScheduled) {
            mAlarmManager.cancel(mShutdownIntent);
            mShutdownScheduled = false;
        }
    }

    private void stop(final boolean goToIdle) {
        if (D) Log.d(TAG, "Stopping playback, goToIdle = " + goToIdle);
        long duration = this.duration();
        long position = this.position();
        if (duration > 30000 && (position >= duration / 2) || position > 240000) {
            scrobble();
        }

        if (mPlayer.isInitialized()) {
            mPlayer.stop();
        }
        mFileToPlay = null;
        closeCursor();
        if (goToIdle) {
            setIsSupposedToBePlaying(false, false);
        } else {
            stopForeground(!TimberUtils.isLollipop());
        }
    }

    private int removeTracksInternal(int first, int last) {
        synchronized (this) {
            if (last < first) {
                return 0;
            } else if (first < 0) {
                first = 0;
            } else if (last >= playlist.size()) {
                last = playlist.size() - 1;
            }

            boolean gotonext = false;
            if (first <= mPlayPos && mPlayPos <= last) {
                mPlayPos = first;
                gotonext = true;
            } else if (mPlayPos > last) {
                mPlayPos -= last - first + 1;
            }
            final int numToRemove = last - first + 1;

            if (first == 0 && last == playlist.size() - 1) {
                mPlayPos = -1;
                mNextPlayPos = -1;
                playlist.clear();
                sHistory.clear();
            } else {
                for (int i = 0; i < numToRemove; i++) {
                    playlist.getTracks().remove(first);
                }

                ListIterator<Integer> positionIterator = sHistory.listIterator();
                while (positionIterator.hasNext()) {
                    int pos = positionIterator.next();
                    if (pos >= first && pos <= last) {
                        positionIterator.remove();
                    } else if (pos > last) {
                        positionIterator.set(pos - numToRemove);
                    }
                }
            }
            if (gotonext) {
                if (playlist.isEmpty()) {
                    stop(true);
                    mPlayPos = -1;
                    closeCursor();
                } else {
                    if (mShuffleMode != SHUFFLE_NONE) {
                        mPlayPos = getNextPosition(true);
                    } else if (mPlayPos >= playlist.size()) {
                        mPlayPos = 0;
                    }
                    final boolean wasPlaying = isPlaying();
                    stop(false);
                    openCurrentAndNext();
                    if (wasPlaying) {
                        play();
                    }
                }
                notifyChange(META_CHANGED);
            }
            return last - first + 1;
        }
    }

    private void addToPlayList(final long[] list, int position, long sourceId, TimberUtils.IdType sourceType) {
        final int addlen = list.length;
        if (position < 0) {
            playlist.clear();
            position = 0;
        }

        playlist.getTracks().ensureCapacity(playlist.size() + addlen);
        if (position > playlist.size()) {
            position = playlist.size();
        }

        final ArrayList<MusicPlaybackTrack> arrayList = new ArrayList<>(addlen);
        for (int i = 0; i < list.length; i++) {
            arrayList.add(new MusicPlaybackTrack(list[i], sourceId, sourceType, i));
        }

        playlist.getTracks().addAll(position, arrayList);

        if (playlist.isEmpty()) {
            closeCursor();
            notifyChange(META_CHANGED);
        }
    }

    void updateCursor(final long trackId) {
        updateCursor("_id=" + trackId, null);
    }

    private void updateCursor(final String selection, final String[] selectionArgs) {
        synchronized (this) {
            closeCursor();
            mCursor = openCursorAndGoToFirst(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    PROJECTION, selection, selectionArgs);
        }
        updateAlbumCursor();
    }

    private void updateCursor(final Uri uri) {
        synchronized (this) {
            closeCursor();
            mCursor = openCursorAndGoToFirst(uri, PROJECTION, null, null);
        }
        updateAlbumCursor();
    }

    private void updateAlbumCursor() {
        long albumId = getAlbumId();
        if (albumId >= 0) {
            mAlbumCursor = openCursorAndGoToFirst(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                    ALBUM_PROJECTION, "_id=" + albumId, null);
        } else {
            mAlbumCursor = null;
        }
    }

    private Cursor openCursorAndGoToFirst(Uri uri, String[] projection,
                                          String selection, String[] selectionArgs) {
        Cursor c = getContentResolver().query(uri, projection,
                selection, selectionArgs, null);
        if (c == null) {
            return null;
        }
        if (!c.moveToFirst()) {
            c.close();
            return null;
        }
        return c;
    }

    private synchronized void closeCursor() {
        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
        }
        if (mAlbumCursor != null) {
            mAlbumCursor.close();
            mAlbumCursor = null;
        }
    }

    void openCurrentAndNext() {
        openCurrentAndMaybeNext(true);
    }

    private void openCurrentAndMaybeNext(final boolean openNext) {
        synchronized (this) {
            closeCursor();

            if (playlist.isEmpty()) {
                return;
            }
            stop(false);

            boolean shutdown = false;

            updateCursor(playlist.getTrackId(mPlayPos));
            while (true) {
                if (mCursor != null
                        && openFile(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI + "/"
                        + mCursor.getLong(IDCOLIDX))) {
                    break;
                }

                closeCursor();
                if (mOpenFailedCounter++ < 10 && playlist.size() > 1) {
                    final int pos = getNextPosition(false);
                    if (pos < 0) {
                        shutdown = true;
                        break;
                    }
                    mPlayPos = pos;
                    stop(false);
                    mPlayPos = pos;
                    updateCursor(playlist.getTrackId(mPlayPos));
                } else {
                    mOpenFailedCounter = 0;
                    Log.w(TAG, "Failed to open file for playback");
                    shutdown = true;
                    break;
                }
            }

            if (shutdown) {
                scheduleDelayedShutdown();
                if (mIsSupposedToBePlaying) {
                    mIsSupposedToBePlaying = false;
                    notifyChange(PLAYSTATE_CHANGED);
                }
            } else if (openNext) {
                setNextTrack();
            }
        }
    }

    void sendErrorMessage(final String trackName) {
        final Intent i = new Intent(TRACK_ERROR);
        i.putExtra(TrackErrorExtra.TRACK_NAME, trackName);
        sendBroadcast(i);
    }

    private int getNextPosition(final boolean force) {
        if (playlist.isEmpty()) {
            return -1;
        }
        if (!force && mRepeatMode == REPEAT_CURRENT) {
            return Math.max(mPlayPos, 0);
        } else if (mShuffleMode == SHUFFLE_NORMAL) {
            final int numTracks = playlist.size();


            final int[] trackNumPlays = new int[numTracks];
            for (int i = 0; i < numTracks; i++) {
                trackNumPlays[i] = 0;
            }


            final int numHistory = sHistory.size();
            for (int i = 0; i < numHistory; i++) {
                final int idx = sHistory.get(i);
                if (idx >= 0 && idx < numTracks) {
                    trackNumPlays[idx]++;
                }
            }

            if (mPlayPos >= 0 && mPlayPos < numTracks) {
                trackNumPlays[mPlayPos]++;
            }

            int minNumPlays = Integer.MAX_VALUE;
            int numTracksWithMinNumPlays = 0;
            for (int trackNumPlay : trackNumPlays) {
                if (trackNumPlay < minNumPlays) {
                    minNumPlays = trackNumPlay;
                    numTracksWithMinNumPlays = 1;
                } else if (trackNumPlay == minNumPlays) {
                    numTracksWithMinNumPlays++;
                }
            }


            if (minNumPlays > 0 && numTracksWithMinNumPlays == numTracks
                    && mRepeatMode != REPEAT_ALL && !force) {
                return -1;
            }


            int skip = sShuffler.nextInt(numTracksWithMinNumPlays);
            for (int i = 0; i < trackNumPlays.length; i++) {
                if (trackNumPlays[i] == minNumPlays) {
                    if (skip == 0) {
                        return i;
                    } else {
                        skip--;
                    }
                }
            }

            if (D)
                Log.e(TAG, "Getting the next position resulted did not get a result when it should have");
            return -1;
        } else if (mShuffleMode == SHUFFLE_AUTO) {
            doAutoShuffleUpdate();
            return mPlayPos + 1;
        } else {
            if (mPlayPos >= playlist.size() - 1) {
                if (mRepeatMode == REPEAT_NONE && !force) {
                    return -1;
                } else if (mRepeatMode == REPEAT_ALL || force) {
                    return 0;
                }
                return -1;
            } else {
                return mPlayPos + 1;
            }
        }
    }

    void setNextTrack() {
        setNextTrack(getNextPosition(false));
    }

    private void setNextTrack(int position) {
        mNextPlayPos = position;
        if (D) Log.d(TAG, "setNextTrack: next play position = " + mNextPlayPos);
        if (mNextPlayPos >= 0 && playlist.isNotEmpty() && mNextPlayPos < playlist.size()) {
            final long id = playlist.getTrackId(mNextPlayPos);
            mPlayer.setNextDataSource(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI + "/" + id);
        } else {
            mPlayer.setNextDataSource(null);
        }
    }

    private boolean makeAutoShuffleList() {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[]{
                            MediaStore.Audio.Media._ID
                    }, MediaStore.Audio.Media.IS_MUSIC + "=1", null, null);
            if (cursor == null || cursor.getCount() == 0) {
                return false;
            }
            final int len = cursor.getCount();
            final long[] list = new long[len];
            for (int i = 0; i < len; i++) {
                cursor.moveToNext();
                list[i] = cursor.getLong(0);
            }
            mAutoShuffleList = list;
            return true;
        } catch (final RuntimeException ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }
        }
        return false;
    }

    private void doAutoShuffleUpdate() {
        boolean notify = false;
        if (mPlayPos > 10) {
            removeTracks(0, mPlayPos - 9);
            notify = true;
        }
        final int toAdd = 7 - (playlist.size() - (mPlayPos < 0 ? -1 : mPlayPos));
        for (int i = 0; i < toAdd; i++) {
            int lookback = sHistory.size();
            int idx;
            while (true) {
                idx = sShuffler.nextInt(mAutoShuffleList.length);
                if (!wasRecentlyUsed(idx, lookback)) {
                    break;
                }
                lookback /= 2;
            }
            sHistory.add(idx);
            if (sHistory.size() > MAX_HISTORY_SIZE) {
                sHistory.remove(0);
            }
            MusicPlaybackTrack track = new MusicPlaybackTrack(mAutoShuffleList[idx], -1, IdType.NA, -1);
            playlist.getTracks().add(track);
            notify = true;
        }
        if (notify) {
            notifyChange(QUEUE_CHANGED);
        }
    }

    private boolean wasRecentlyUsed(final int idx, int lookbacksize) {
        if (lookbacksize == 0) {
            return false;
        }
        final int histsize = sHistory.size();
        if (histsize < lookbacksize) {
            lookbacksize = histsize;
        }
        final int maxidx = histsize - 1;
        for (int i = 0; i < lookbacksize; i++) {
            final long entry = sHistory.get(maxidx - i);
            if (entry == idx) {
                return true;
            }
        }
        return false;
    }

    void notifyChange(final String what) {
        if (D) Log.d(TAG, "notifyChange: what = " + what);

        // Update the lockscreen controls
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            updateMediaSession(what);
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
            updateRemoteControlClient(what);

        if (what.equals(POSITION_CHANGED)) {
            return;
        }

        final Intent intent = new Intent(what);
        intent.putExtra("id", getAudioId());
        intent.putExtra("artist", getArtistName());
        intent.putExtra("album", getAlbumName());
        intent.putExtra("albumid", getAlbumId());
        intent.putExtra("track", getTrackName());
        intent.putExtra("playing", isPlaying());

        sendStickyBroadcast(intent);

        final Intent musicIntent = new Intent(intent);
        musicIntent.setAction(what.replace(TIMBER_PACKAGE_NAME, MUSIC_PACKAGE_NAME));
        sendStickyBroadcast(musicIntent);

        if (what.equals(META_CHANGED)) {

            mRecentStore.addSongId(getAudioId());
            mSongPlayCount.bumpSongCount(getAudioId());

        } else if (what.equals(QUEUE_CHANGED)) {
            saveQueue(true);
            if (isPlaying()) {

                if (mNextPlayPos >= 0 && mNextPlayPos < playlist.size()
                        && getShuffleMode() != SHUFFLE_NONE) {
                    setNextTrack(mNextPlayPos);
                } else {
                    setNextTrack();
                }
            }
        } else {
            saveQueue(false);
        }

        if (what.equals(PLAYSTATE_CHANGED)) {
            mNotificationHandler.updateNotification();
        }
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private void updateRemoteControlClient(final String what) {
        //Legacy for ICS
        if (mRemoteControlClient != null) {
            int playState = mIsSupposedToBePlaying
                    ? RemoteControlClient.PLAYSTATE_PLAYING
                    : RemoteControlClient.PLAYSTATE_PAUSED;
            if (what.equals(META_CHANGED) || what.equals(QUEUE_CHANGED)) {
                Bitmap albumArt = null;
                if (mShowAlbumArtOnLockscreen) {
                    albumArt = ImageLoader.getInstance().loadImageSync(TimberUtils.getAlbumArtUri(getAlbumId()).toString());
                    if (albumArt != null) {

                        Bitmap.Config config = albumArt.getConfig();
                        if (config == null) {
                            config = Bitmap.Config.ARGB_8888;
                        }
                        albumArt = albumArt.copy(config, false);
                    }
                }

                RemoteControlClient.MetadataEditor editor = mRemoteControlClient.editMetadata(true);
                editor.putString(MediaMetadataRetriever.METADATA_KEY_ALBUM, getAlbumName());
                editor.putString(MediaMetadataRetriever.METADATA_KEY_ARTIST, getArtistName());
                editor.putString(MediaMetadataRetriever.METADATA_KEY_TITLE, getTrackName());
                editor.putLong(MediaMetadataRetriever.METADATA_KEY_DURATION, duration());
                editor.putBitmap(MediaMetadataEditor.BITMAP_KEY_ARTWORK, albumArt);
                editor.apply();
            }
            mRemoteControlClient.setPlaybackState(playState);
        }
    }


    private void updateMediaSession(final String what) {
        int playState = mIsSupposedToBePlaying
                ? PlaybackStateCompat.STATE_PLAYING
                : PlaybackStateCompat.STATE_PAUSED;

        if (what.equals(PLAYSTATE_CHANGED) || what.equals(POSITION_CHANGED)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mSession.setPlaybackState(new PlaybackStateCompat.Builder()
                        .setState(playState, position(), 1.0f)
                        .setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE | PlaybackStateCompat.ACTION_PLAY_PAUSE |
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
                        .build());
            }
        } else if (what.equals(META_CHANGED) || what.equals(QUEUE_CHANGED)) {
            Bitmap albumArt = null;
            if (mShowAlbumArtOnLockscreen) {
                albumArt = ImageLoader.getInstance().loadImageSync(TimberUtils.getAlbumArtUri(getAlbumId()).toString());
                if (albumArt != null) {

                    Bitmap.Config config = albumArt.getConfig();
                    if (config == null) {
                        config = Bitmap.Config.ARGB_8888;
                    }
                    albumArt = albumArt.copy(config, false);
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mSession.setMetadata(new MediaMetadataCompat.Builder()
                        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, getArtistName())
                        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, getAlbumArtistName())
                        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, getAlbumName())
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, getTrackName())
                        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration())
                        .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, getQueuePosition() + 1)
                        .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, getQueue().length)
                        .putString(MediaMetadataCompat.METADATA_KEY_GENRE, getGenreName())
                        .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)
                        .build());

                mSession.setPlaybackState(new PlaybackStateCompat.Builder()
                        .setState(playState, position(), 1.0f)
                        .setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE | PlaybackStateCompat.ACTION_PLAY_PAUSE |
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
                        .build());
            }
        }
    }

    private void saveQueue(final boolean full) {
        if (!mQueueIsSaveable) {
            return;
        }

        final SharedPreferences.Editor editor = mPreferences.edit();
        if (full) {
            mPlaybackStateStore.saveState(playlist.getTracks(),
                    mShuffleMode != SHUFFLE_NONE ? sHistory : null);
            editor.putInt("cardid", mCardId);
        }
        editor.putInt("curpos", mPlayPos);
        if (mPlayer.isInitialized()) {
            editor.putLong("seekpos", mPlayer.position());
        }
        editor.putInt("repeatmode", mRepeatMode);
        editor.putInt("shufflemode", mShuffleMode);
        editor.apply();
    }

    private void reloadQueueAfterPermissionCheck() {
        if (TimberUtils.isMarshmallow()) {
            if (Nammu.checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                reloadQueue();
            }
        } else {
            reloadQueue();
        }
    }

    private void reloadQueue() {
        int id = mCardId;
        if (mPreferences.contains("cardid")) {
            id = mPreferences.getInt("cardid", ~mCardId);
        }
        if (id == mCardId) {
            playlist.setTracks(mPlaybackStateStore.getQueue());
        }
        if (playlist.isNotEmpty()) {
            final int pos = mPreferences.getInt("curpos", 0);
            if (pos < 0 || pos >= playlist.size()) {
                playlist.clear();
                return;
            }
            mPlayPos = pos;
            updateCursor(playlist.getTrackId(mPlayPos));
            if (mCursor == null) {
                SystemClock.sleep(3000);
                updateCursor(playlist.getTrackId(mPlayPos));
            }
            synchronized (this) {
                closeCursor();
                mOpenFailedCounter = 20;
                openCurrentAndNext();
            }
            if (!mPlayer.isInitialized()) {
                playlist.clear();
                return;
            }

            final long seekpos = mPreferences.getLong("seekpos", 0);
            seek(seekpos >= 0 && seekpos < duration() ? seekpos : 0);

            if (D) {
                Log.d(TAG, "restored queue, currently at position "
                        + position() + "/" + duration()
                        + " (requested " + seekpos + ")");
            }

            int repmode = mPreferences.getInt("repeatmode", REPEAT_NONE);
            if (repmode != REPEAT_ALL && repmode != REPEAT_CURRENT) {
                repmode = REPEAT_NONE;
            }
            mRepeatMode = repmode;

            int shufmode = mPreferences.getInt("shufflemode", SHUFFLE_NONE);
            if (shufmode != SHUFFLE_AUTO && shufmode != SHUFFLE_NORMAL) {
                shufmode = SHUFFLE_NONE;
            }
            if (shufmode != SHUFFLE_NONE) {
                sHistory = mPlaybackStateStore.getHistory(playlist.size());
            }
            if (shufmode == SHUFFLE_AUTO) {
                if (!makeAutoShuffleList()) {
                    shufmode = SHUFFLE_NONE;
                }
            }
            mShuffleMode = shufmode;
        }
    }

    public boolean openFile(final String path) {
        if (D) Log.d(TAG, "openFile: path = " + path);
        synchronized (this) {
            if (path == null) {
                return false;
            }

            if (mCursor == null) {
                Uri uri = Uri.parse(path);
                boolean shouldAddToPlaylist = true;
                long id = -1;
                try {
                    id = Long.parseLong(uri.getLastPathSegment());
                } catch (NumberFormatException ex) {
                    // Ignore
                }

                if (id != -1 && path.startsWith(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.toString())) {
                    updateCursor(uri);

                } else if (id != -1 && path.startsWith(
                        MediaStore.Files.getContentUri("external").toString())) {
                    updateCursor(id);

                } else if (path.startsWith("content://downloads/")) {
                    String mpUri = getValueForDownloadedFile(this, uri, "mediaprovider_uri");
                    if (D) Log.i(TAG, "Downloaded file's MP uri : " + mpUri);
                    if (!TextUtils.isEmpty(mpUri)) {
                        if (openFile(mpUri)) {
                            notifyChange(META_CHANGED);
                            return true;
                        } else {
                            return false;
                        }
                    } else {
                        updateCursorForDownloadedFile(uri);
                        shouldAddToPlaylist = false;
                    }

                } else {
                    String where = MediaStore.Audio.Media.DATA + "=?";
                    String[] selectionArgs = new String[]{path};
                    updateCursor(where, selectionArgs);
                }
                try {
                    if (mCursor != null && shouldAddToPlaylist) {
                        playlist.clear();
                        MusicPlaybackTrack track = new MusicPlaybackTrack(
                                mCursor.getLong(IDCOLIDX), -1, IdType.NA, -1);
                        playlist.getTracks().add(track);
                        notifyChange(QUEUE_CHANGED);
                        mPlayPos = 0;
                        sHistory.clear();
                    }
                } catch (final UnsupportedOperationException ex) {
                    // Ignore
                }
            }

            mFileToPlay = path;
            mPlayer.setDataSource(mFileToPlay);
            if (mPlayer.isInitialized()) {
                mOpenFailedCounter = 0;
                return true;
            }

            String trackName = getTrackName();
            if (TextUtils.isEmpty(trackName)) {
                trackName = path;
            }
            sendErrorMessage(trackName);

            stop(true);
            return false;
        }
    }

    private void updateCursorForDownloadedFile(Uri uri) {
        synchronized (this) {
            closeCursor();
            MatrixCursor cursor = new MatrixCursor(PROJECTION_MATRIX);
            String title = getValueForDownloadedFile(this, uri, "title");
            cursor.addRow(new Object[]{
                    null,
                    null,
                    null,
                    title,
                    null,
                    null,
                    null,
                    null
            });
            mCursor = cursor;
            mCursor.moveToFirst();
        }
    }

    private String getValueForDownloadedFile(Context context, Uri uri, String column) {
        Cursor cursor = null;
        final String[] projection = {
                column
        };

        try {
            cursor = context.getContentResolver().query(uri, projection, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    public int getAudioSessionId() {
        synchronized (this) {
            return mPlayer.getAudioSessionId();
        }
    }

    public int getMediaMountedCount() {
        return mMediaMountedCount;
    }

    public int getShuffleMode() {
        return mShuffleMode;
    }

    public void setShuffleMode(final int shufflemode) {
        synchronized (this) {
            if (mShuffleMode == shufflemode && playlist.isNotEmpty()) {
                return;
            }

            mShuffleMode = shufflemode;
            if (mShuffleMode == SHUFFLE_AUTO) {
                if (makeAutoShuffleList()) {
                    playlist.clear();
                    doAutoShuffleUpdate();
                    mPlayPos = 0;
                    openCurrentAndNext();
                    play();
                    notifyChange(META_CHANGED);
                    return;
                } else {
                    mShuffleMode = SHUFFLE_NONE;
                }
            } else {
                setNextTrack();
            }
            saveQueue(false);
            notifyChange(SHUFFLEMODE_CHANGED);
        }
    }

    public int getRepeatMode() {
        return mRepeatMode;
    }

    public void setRepeatMode(final int repeatmode) {
        synchronized (this) {
            mRepeatMode = repeatmode;
            setNextTrack();
            saveQueue(false);
            notifyChange(REPEATMODE_CHANGED);
        }
    }

    public int removeTrack(final long id) {
        int numremoved = 0;
        synchronized (this) {
            for (int i = 0; i < playlist.size(); i++) {
                if (playlist.getTrackId(i) == id) {
                    numremoved += removeTracksInternal(i, i);
                    i--;
                }
            }
        }
        if (numremoved > 0) {
            notifyChange(QUEUE_CHANGED);
        }
        return numremoved;
    }

    public boolean removeTrackAtPosition(final long id, final int position) {
        synchronized (this) {
            if (position >= 0 &&
                    position < playlist.size() &&
                    playlist.getTrackId(position) == id) {

                return removeTracks(position, position) > 0;
            }
        }
        return false;
    }

    public int removeTracks(final int first, final int last) {
        final int numremoved = removeTracksInternal(first, last);
        if (numremoved > 0) {
            notifyChange(QUEUE_CHANGED);
        }
        return numremoved;
    }

    public int getQueuePosition() {
        synchronized (this) {
            return mPlayPos;
        }
    }

    public void setQueuePosition(final int index) {
        synchronized (this) {
            stop(false);
            mPlayPos = index;
            openCurrentAndNext();
            play();
            notifyChange(META_CHANGED);
            if (mShuffleMode == SHUFFLE_AUTO) {
                doAutoShuffleUpdate();
            }
        }
    }

    public int getQueueHistorySize() {
        synchronized (this) {
            return sHistory.size();
        }
    }

    public int getQueueHistoryPosition(int position) {
        synchronized (this) {
            if (position >= 0 && position < sHistory.size()) {
                return sHistory.get(position);
            }
        }

        return -1;
    }

    public int[] getQueueHistoryList() {
        synchronized (this) {
            int[] history = new int[sHistory.size()];
            for (int i = 0; i < sHistory.size(); i++) {
                history[i] = sHistory.get(i);
            }

            return history;
        }
    }

    public String getPath() {
        synchronized (this) {
            if (mCursor == null) {
                return null;
            }
            return mCursor.getString(mCursor.getColumnIndexOrThrow(AudioColumns.DATA));
        }
    }

    public String getAlbumName() {
        synchronized (this) {
            if (mCursor == null) {
                return null;
            }
            return mCursor.getString(mCursor.getColumnIndexOrThrow(AudioColumns.ALBUM));
        }
    }

    public String getTrackName() {
        synchronized (this) {
            if (mCursor == null) {
                return null;
            }
            return mCursor.getString(mCursor.getColumnIndexOrThrow(AudioColumns.TITLE));
        }
    }

    public String getGenreName() {
        synchronized (this) {
            if (mCursor == null || mPlayPos < 0 || mPlayPos >= playlist.size()) {
                return null;
            }
            String[] genreProjection = {MediaStore.Audio.Genres.NAME};
            Uri genreUri = MediaStore.Audio.Genres.getContentUriForAudioId("external",
                    (int) playlist.getTrackId(mPlayPos));
            Cursor genreCursor = getContentResolver().query(genreUri, genreProjection,
                    null, null, null);
            if (genreCursor != null) {
                try {
                    if (genreCursor.moveToFirst()) {
                        return genreCursor.getString(
                                genreCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME));
                    }
                } finally {
                    genreCursor.close();
                }
            }
            return null;
        }
    }

    public String getArtistName() {
        synchronized (this) {
            if (mCursor == null) {
                return null;
            }
            return mCursor.getString(mCursor.getColumnIndexOrThrow(AudioColumns.ARTIST));
        }
    }

    public String getAlbumArtistName() {
        synchronized (this) {
            if (mAlbumCursor == null) {
                return null;
            }
            return mAlbumCursor.getString(mAlbumCursor.getColumnIndexOrThrow(AlbumColumns.ARTIST));
        }
    }

    public long getAlbumId() {
        synchronized (this) {
            if (mCursor == null) {
                return -1;
            }
            return mCursor.getLong(mCursor.getColumnIndexOrThrow(AudioColumns.ALBUM_ID));
        }
    }

    public long getArtistId() {
        synchronized (this) {
            if (mCursor == null) {
                return -1;
            }
            return mCursor.getLong(mCursor.getColumnIndexOrThrow(AudioColumns.ARTIST_ID));
        }
    }

    public long getAudioId() {
        MusicPlaybackTrack track = getCurrentTrack();
        if (track != null) {
            return track.mId;
        }

        return -1;
    }

    public MusicPlaybackTrack getCurrentTrack() {
        return getTrack(mPlayPos);
    }

    public synchronized MusicPlaybackTrack getTrack(int index) {
        if (index >= 0 && index < playlist.size() && mPlayer.isInitialized()) {
            return playlist.getTracks().get(index);
        }

        return null;
    }

    public long getNextAudioId() {
        synchronized (this) {
            if (mNextPlayPos >= 0 && mNextPlayPos < playlist.size() && mPlayer.isInitialized()) {
                return playlist.getTrackId(mNextPlayPos);
            }
        }
        return -1;
    }

    public long getPreviousAudioId() {
        synchronized (this) {
            if (mPlayer.isInitialized()) {
                int pos = getPreviousPlayPosition(false);
                if (pos >= 0 && pos < playlist.size()) {
                    return playlist.getTrackId(pos);
                }
            }
        }
        return -1;
    }

    public long seek(long position) {
        if (mPlayer.isInitialized()) {
            if (position < 0) {
                position = 0;
            } else if (position > mPlayer.duration()) {
                position = mPlayer.duration();
            }
            long result = mPlayer.seek(position);
            notifyChange(POSITION_CHANGED);
            return result;
        }
        return -1;
    }

    public void seekRelative(long deltaInMs) {
        synchronized (this) {
            if (mPlayer.isInitialized()) {
                final long newPos = position() + deltaInMs;
                final long duration = duration();
                if (newPos < 0) {
                    prev(true);
                    // seek to the new duration + the leftover position
                    seek(duration() + newPos);
                } else if (newPos >= duration) {
                    gotoNext(true);
                    // seek to the leftover duration
                    seek(newPos - duration);
                } else {
                    seek(newPos);
                }
            }
        }
    }

    public long position() {
        if (mPlayer.isInitialized()) {
            return mPlayer.position();
        }
        return -1;
    }

    public long duration() {
        if (mPlayer.isInitialized()) {
            return mPlayer.duration();
        }
        return -1;
    }

    public long[] getQueue() {
        synchronized (this) {
            final int len = playlist.size();
            final long[] list = new long[len];
            for (int i = 0; i < len; i++) {
                list[i] = playlist.getTrackId(i);
            }
            return list;
        }
    }

    public long getQueueItemAtPosition(int position) {
        synchronized (this) {
            if (position >= 0 && position < playlist.size()) {
                return playlist.getTrackId(position);
            }
        }

        return -1;
    }

    public int getQueueSize() {
        synchronized (this) {
            return playlist.size();
        }
    }

    public boolean isPlaying() {
        return mIsSupposedToBePlaying;
    }

    private void setIsSupposedToBePlaying(boolean value, boolean notify) {
        if (mIsSupposedToBePlaying != value) {
            mIsSupposedToBePlaying = value;


            if (!mIsSupposedToBePlaying) {
                scheduleDelayedShutdown();
                mLastPlayedTime = System.currentTimeMillis();
            }

            if (notify) {
                notifyChange(PLAYSTATE_CHANGED);
            }
        }
    }

    public void open(final long[] list, final int position, long sourceId, TimberUtils.IdType sourceType) {
        synchronized (this) {
            if (mShuffleMode == SHUFFLE_AUTO) {
                mShuffleMode = SHUFFLE_NORMAL;
            }
            final long oldId = getAudioId();
            final int listlength = list.length;
            boolean newlist = true;
            if (playlist.size() == listlength) {
                newlist = false;
                for (int i = 0; i < listlength; i++) {
                    if (list[i] != playlist.getTrackId(i)) {
                        newlist = true;
                        break;
                    }
                }
            }
            if (newlist) {
                addToPlayList(list, -1, sourceId, sourceType);
                notifyChange(QUEUE_CHANGED);
            }
            if (position >= 0) {
                mPlayPos = position;
            } else {
                mPlayPos = sShuffler.nextInt(playlist.size());
            }
            sHistory.clear();
            openCurrentAndNext();
            if (oldId != getAudioId()) {
                notifyChange(META_CHANGED);
            }
        }
    }

    public void stop() {
        stop(true);
    }

    public void play() {
        play(true);
    }

    public void play(boolean createNewNextTrack) {
        int status = mAudioManager.requestAudioFocus(mAudioFocusListener,
                AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

        if (D) Log.d(TAG, "Starting playback: audio focus request status = " + status);

        if (status != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            return;
        }

        final Intent intent = new Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
        intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, getAudioSessionId());
        intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName());
        sendBroadcast(intent);

        mAudioManager.registerMediaButtonEventReceiver(new ComponentName(getPackageName(),
                MediaButtonIntentReceiver.class.getName()));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            mSession.setActive(true);

        if (createNewNextTrack) {
            setNextTrack();
        } else {
            setNextTrack(mNextPlayPos);
        }

        if (mPlayer.isInitialized()) {
            final long duration = mPlayer.duration();
            if (mRepeatMode != REPEAT_CURRENT && duration > 2000
                    && mPlayer.position() >= duration - 2000) {
                gotoNext(true);
            }

            mPlayer.start();
            mPlayerHandler.removeMessages(FADEDOWN);
            mPlayerHandler.sendEmptyMessage(FADEUP);

            setIsSupposedToBePlaying(true, true);

            cancelShutdown();
            mNotificationHandler.updateNotification();
            notifyChange(META_CHANGED);
        } else if (playlist.size() <= 0) {
            setShuffleMode(SHUFFLE_AUTO);
        }
    }

    public void pause() {
        if (D) Log.d(TAG, "Pausing playback");
        synchronized (this) {
            mPlayerHandler.removeMessages(FADEUP);
            if (mIsSupposedToBePlaying) {
                final Intent intent = new Intent(
                        AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
                intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, getAudioSessionId());
                intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName());
                sendBroadcast(intent);

                mPlayer.pause();
                notifyChange(META_CHANGED);
                setIsSupposedToBePlaying(false, true);
            }
        }
    }

    public void gotoNext(final boolean force) {
        if (D) Log.d(TAG, "Going to next track");
        synchronized (this) {
            if (playlist.size() <= 0) {
                if (D) Log.d(TAG, "No play queue");
                scheduleDelayedShutdown();
                return;
            }
            int pos = mNextPlayPos;
            if (pos < 0) {
                pos = getNextPosition(force);
            }

            if (pos < 0) {
                setIsSupposedToBePlaying(false, true);
                return;
            }

            stop(false);
            setAndRecordPlayPos(pos);
            openCurrentAndNext();
            play();
            notifyChange(META_CHANGED);
        }
    }

    public void goToPosition(int pos) {
        synchronized (this) {
            if (playlist.size() <= 0) {
                if (D) Log.d(TAG, "No play queue");
                scheduleDelayedShutdown();
                return;
            }
            if (pos < 0) {
                return;
            }
            if (pos == mPlayPos) {
                if (!isPlaying()) {
                    play();
                }
                return;
            }
            stop(false);
            setAndRecordPlayPos(pos);
            openCurrentAndNext();
            play();
            notifyChange(META_CHANGED);
        }
    }

    public void setAndRecordPlayPos(int nextPos) {
        synchronized (this) {
            if (mShuffleMode != SHUFFLE_NONE) {
                sHistory.add(mPlayPos);
                if (sHistory.size() > MAX_HISTORY_SIZE) {
                    sHistory.remove(0);
                }
            }

            mPlayPos = nextPos;
        }
    }

    public void prev(boolean forcePrevious) {
        synchronized (this) {
            boolean goPrevious = getRepeatMode() != REPEAT_CURRENT &&
                    (position() < REWIND_INSTEAD_PREVIOUS_THRESHOLD || forcePrevious);

            if (goPrevious) {
                if (D) Log.d(TAG, "Going to previous track");
                int pos = getPreviousPlayPosition(true);

                if (pos < 0) {
                    return;
                }
                mNextPlayPos = mPlayPos;
                mPlayPos = pos;
                stop(false);
                openCurrent();
                play(false);
                notifyChange(META_CHANGED);
            } else {
                if (D) Log.d(TAG, "Going to beginning of track");
                seek(0);
                play(false);
            }
        }
    }

    public int getPreviousPlayPosition(boolean removeFromHistory) {
        synchronized (this) {
            if (mShuffleMode == SHUFFLE_NORMAL) {

                final int histsize = sHistory.size();
                if (histsize == 0) {
                    return -1;
                }
                final Integer pos = sHistory.get(histsize - 1);
                if (removeFromHistory) {
                    sHistory.remove(histsize - 1);
                }
                return pos;
            } else {
                if (mPlayPos > 0) {
                    return mPlayPos - 1;
                } else {
                    return playlist.size() - 1;
                }
            }
        }
    }

    private void openCurrent() {
        openCurrentAndMaybeNext(false);
    }

    public void moveQueueItem(int index1, int index2) {
        synchronized (this) {
            if (index1 >= playlist.size()) {
                index1 = playlist.size() - 1;
            }
            if (index2 >= playlist.size()) {
                index2 = playlist.size() - 1;
            }

            if (index1 == index2) {
                return;
            }

            final MusicPlaybackTrack track = playlist.getTracks().remove(index1);
            if (index1 < index2) {
                playlist.getTracks().add(index2, track);
                if (mPlayPos == index1) {
                    mPlayPos = index2;
                } else if (mPlayPos >= index1 && mPlayPos <= index2) {
                    mPlayPos--;
                }
            } else if (index2 < index1) {
                playlist.getTracks().add(index2, track);
                if (mPlayPos == index1) {
                    mPlayPos = index2;
                } else if (mPlayPos >= index2 && mPlayPos <= index1) {
                    mPlayPos++;
                }
            }
            notifyChange(QUEUE_CHANGED);
        }
    }

    public void enqueue(final long[] list, final int action, long sourceId, IdType sourceType) {
        synchronized (this) {
            if (action == NEXT && mPlayPos + 1 < playlist.size()) {
                addToPlayList(list, mPlayPos + 1, sourceId, sourceType);
                mNextPlayPos = mPlayPos + 1;
            } else {
                addToPlayList(list, Integer.MAX_VALUE, sourceId, sourceType);
            }
            notifyChange(QUEUE_CHANGED);

            if (mPlayPos < 0) {
                mPlayPos = 0;
                openCurrentAndNext();
                play();
                notifyChange(META_CHANGED);
            }
        }
    }

    private void cycleRepeat() {
        if (mRepeatMode == REPEAT_NONE) {
            setRepeatMode(REPEAT_CURRENT);
            if (mShuffleMode != SHUFFLE_NONE) {
                setShuffleMode(SHUFFLE_NONE);
            }
        } else {
            setRepeatMode(REPEAT_NONE);
        }
    }

    private void cycleShuffle() {
        if (mShuffleMode == SHUFFLE_NONE) {
            setShuffleMode(SHUFFLE_NORMAL);
        } else if (mShuffleMode == SHUFFLE_NORMAL || mShuffleMode == SHUFFLE_AUTO) {
            setShuffleMode(SHUFFLE_NONE);
        }
    }

    public void refresh() {
        notifyChange(REFRESH);
    }

    public void playlistChanged() {
        notifyChange(PLAYLIST_CHANGED);
    }

    public long getLastPlayedTime() {
        return mLastPlayedTime;
    }

    MediaSessionCompat getSession() {
        return mSession;
    }

    public boolean isActivateXTrackSelector() {
        return mActivateXTrackSelector;
    }

    public void updateNotification() {
        mNotificationHandler.updateNotification();
    }
}
