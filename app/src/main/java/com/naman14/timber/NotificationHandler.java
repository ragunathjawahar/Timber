package com.naman14.timber;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;

import androidx.core.app.NotificationManagerCompat;
import androidx.media.app.NotificationCompat;
import androidx.palette.graphics.Palette;

import com.naman14.timber.utils.NavigationUtils;
import com.naman14.timber.utils.TimberUtils;
import com.nostra13.universalimageloader.core.ImageLoader;

import java.util.ArrayList;

import de.Maxr1998.trackselectorlib.ModNotInstalledException;
import de.Maxr1998.trackselectorlib.NotificationHelper;
import de.Maxr1998.trackselectorlib.TrackItem;

class NotificationHandler {
    private int mNotifyMode = NOTIFY_MODE_NONE;
    private long mNotificationPostTime = 0;
    private NotificationManagerCompat mNotificationManager;
    private static final int NOTIFY_MODE_NONE = 0;
    private static final int NOTIFY_MODE_FOREGROUND = 1;
    private static final int NOTIFY_MODE_BACKGROUND = 2;

    public static final String CHANNEL_ID = "timber_channel_01";

    private static final String[] NOTIFICATION_PROJECTION = new String[]{
            "audio._id AS _id", MediaStore.Audio.AudioColumns.ALBUM_ID, MediaStore.Audio.AudioColumns.TITLE,
            MediaStore.Audio.AudioColumns.ARTIST, MediaStore.Audio.AudioColumns.DURATION
    };

    private final MusicService mService;

    NotificationHandler(MusicService service) {
        mNotificationManager = NotificationManagerCompat.from(service);
        mService = service;
    }

    void createNotificationChannel() {
        if (TimberUtils.isOreo()) {
            CharSequence name = "Timber";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationManager manager = (NotificationManager) mService.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel mChannel = new NotificationChannel(CHANNEL_ID, name, importance);
            manager.createNotificationChannel(mChannel);
        }
    }

    Notification buildNotification() {
        final String albumName = mService.getAlbumName();
        final String artistName = mService.getArtistName();
        final boolean isPlaying = mService.isPlaying();
        String text = TextUtils.isEmpty(albumName)
                ? artistName : artistName + " - " + albumName;

        int playButtonResId = isPlaying
                ? R.drawable.ic_pause_white_36dp : R.drawable.ic_play_white_36dp;

        Intent nowPlayingIntent = NavigationUtils.getNowPlayingIntent(mService);
        PendingIntent clickIntent = PendingIntent.getActivity(mService, 0, nowPlayingIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        Bitmap artwork;
        artwork = ImageLoader.getInstance().loadImageSync(TimberUtils.getAlbumArtUri(mService.getAlbumId()).toString());

        if (artwork == null) {
            artwork = ImageLoader.getInstance().loadImageSync("drawable://" + R.drawable.ic_empty_music2);
        }

        if (mNotificationPostTime == 0) {
            mNotificationPostTime = System.currentTimeMillis();
        }

        androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(mService, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(artwork)
                .setContentIntent(clickIntent)
                .setContentTitle(mService.getTrackName())
                .setContentText(text)
                .setWhen(mNotificationPostTime)
                .addAction(R.drawable.ic_skip_previous_white_36dp,
                        "",
                        retrievePlaybackAction(MusicService.PREVIOUS_ACTION))
                .addAction(playButtonResId, "",
                        retrievePlaybackAction(MusicService.TOGGLEPAUSE_ACTION))
                .addAction(R.drawable.ic_skip_next_white_36dp,
                        "",
                        retrievePlaybackAction(MusicService.NEXT_ACTION));

        if (TimberUtils.isJellyBeanMR1()) {
            builder.setShowWhen(false);
        }

        if (TimberUtils.isLollipop()) {
            builder.setVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationCompat.MediaStyle style = new NotificationCompat.MediaStyle()
                    .setMediaSession(mService.getSession().getSessionToken())
                    .setShowActionsInCompactView(0, 1, 2, 3);
            builder.setStyle(style);
        }
        if (artwork != null && TimberUtils.isLollipop()) {
            builder.setColor(Palette.from(artwork).generate().getVibrantColor(Color.parseColor("#403f4d")));
        }

        if (TimberUtils.isOreo()) {
            builder.setColorized(true);
        }

        Notification n = builder.build();

        if (mService.isActivateXTrackSelector()) {
            addXTrackSelector(n);
        }

        return n;
    }

    void updateNotification() {
        final int newNotifyMode;
        if (mService.isPlaying()) {
            newNotifyMode = NOTIFY_MODE_FOREGROUND;
        } else if (recentlyPlayed()) {
            newNotifyMode = NOTIFY_MODE_BACKGROUND;
        } else {
            newNotifyMode = NOTIFY_MODE_NONE;
        }

        int notificationId = hashCode();
        if (mNotifyMode != newNotifyMode) {
            if (mNotifyMode == NOTIFY_MODE_FOREGROUND) {
                if (TimberUtils.isLollipop())
                    mService.stopForeground(newNotifyMode == NOTIFY_MODE_NONE);
                else
                    mService.stopForeground(newNotifyMode == NOTIFY_MODE_NONE || newNotifyMode == NOTIFY_MODE_BACKGROUND);
            } else if (newNotifyMode == NOTIFY_MODE_NONE) {
                mNotificationManager.cancel(notificationId);
                mNotificationPostTime = 0;
            }
        }

        if (newNotifyMode == NOTIFY_MODE_FOREGROUND) {
            mService.startForeground(notificationId, buildNotification());
        } else if (newNotifyMode == NOTIFY_MODE_BACKGROUND) {
            mNotificationManager.notify(notificationId, buildNotification());
        }

        mNotifyMode = newNotifyMode;
    }

    void cancelNotification() {
        mService.stopForeground(true);
        mNotificationManager.cancel(hashCode());
        mNotificationPostTime = 0;
        mNotifyMode = NOTIFY_MODE_NONE;
    }

    private void addXTrackSelector(Notification n) {
        if (NotificationHelper.isSupported(n)) {
            StringBuilder selection = new StringBuilder();
            StringBuilder order = new StringBuilder().append("CASE _id \n");
            for (int i = 0; i < mService.playlist.size(); i++) {
                selection.append("_id=").append(mService.playlist.getTrackId(i)).append(" OR ");
                order.append("WHEN ").append(mService.playlist.getTrackId(i)).append(" THEN ").append(i).append("\n");
            }
            order.append("END");
            Cursor c = mService.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, NOTIFICATION_PROJECTION, selection.substring(0, selection.length() - 3), null, order.toString());
            if (c != null && c.getCount() != 0) {
                c.moveToFirst();
                ArrayList<Bundle> list = new ArrayList<>();
                do {
                    TrackItem t = new TrackItem()
                            .setArt(TimberUtils.getAlbumArtUri(c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ALBUM_ID))))
                            .setTitle(c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.TITLE)))
                            .setArtist(c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ARTIST)))
                            .setDuration(TimberUtils.makeShortTimeString(mService, c.getInt(c.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.DURATION)) / 1000));
                    list.add(t.get());
                } while (c.moveToNext());
                try {
                    NotificationHelper.insertToNotification(n, list, mService, mService.getQueuePosition());
                } catch (ModNotInstalledException e) {
                    e.printStackTrace();
                }
                c.close();
            }
        }
    }

    private boolean recentlyPlayed() {
        return mService.isPlaying() || System.currentTimeMillis() - mService.getLastPlayedTime() < MusicService.IDLE_DELAY;
    }

    private PendingIntent retrievePlaybackAction(final String action) {
        final ComponentName serviceName = new ComponentName(mService, MusicService.class);
        Intent intent = new Intent(action);
        intent.setComponent(serviceName);

        return PendingIntent.getService(mService, 0, intent, 0);
    }
}
