package com.naman14.timber;

import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.lang.ref.WeakReference;

final class MusicPlayerHandler extends Handler {
    private final WeakReference<MusicService> mServiceReference;
    private float mCurrentVolume = 1.0f;

    public MusicPlayerHandler(final MusicService service, final Looper looper) {
        super(looper);
        mServiceReference = new WeakReference<>(service);
    }

    @Override
    public void handleMessage(final Message msg) {
        final MusicService service = mServiceReference.get();
        if (service == null) {
            return;
        }

        synchronized (service) {
            switch (msg.what) {
                case MusicService.FADEDOWN:
                    mCurrentVolume -= .05f;
                    if (mCurrentVolume > .2f) {
                        sendEmptyMessageDelayed(MusicService.FADEDOWN, 10);
                    } else {
                        mCurrentVolume = .2f;
                    }
                    service.mPlayer.setVolume(mCurrentVolume);
                    break;
                case MusicService.FADEUP:
                    mCurrentVolume += .01f;
                    if (mCurrentVolume < 1.0f) {
                        sendEmptyMessageDelayed(MusicService.FADEUP, 10);
                    } else {
                        mCurrentVolume = 1.0f;
                    }
                    service.mPlayer.setVolume(mCurrentVolume);
                    break;
                case MusicService.SERVER_DIED:
                    if (service.isPlaying()) {
                        final TrackErrorInfo info = (TrackErrorInfo) msg.obj;
                        service.sendErrorMessage(info.mTrackName);


                        service.removeTrack(info.mId);
                    } else {
                        service.openCurrentAndNext();
                    }
                    break;
                case MusicService.TRACK_WENT_TO_NEXT:
                    mServiceReference.get().scrobble();
                    service.setAndRecordPlayPos(service.mNextPlayPos);
                    service.setNextTrack();
                    if (service.mCursor != null) {
                        service.mCursor.close();
                        service.mCursor = null;
                    }
                    service.updateCursor(service.playlist.getTrackId(service.mPlayPos));
                    service.notifyChange(MusicService.META_CHANGED);
                    service.updateNotification();
                    break;
                case MusicService.TRACK_ENDED:
                    if (service.mRepeatMode == MusicService.REPEAT_CURRENT) {
                        service.seek(0);
                        service.play();
                    } else {
                        service.gotoNext(false);
                    }
                    break;
                case MusicService.RELEASE_WAKELOCK:
                    service.mWakeLock.release();
                    break;
                case MusicService.FOCUSCHANGE:
                    if (MusicService.D)
                        Log.d(MusicService.TAG, "Received audio focus change event " + msg.arg1);
                    switch (msg.arg1) {
                        case AudioManager.AUDIOFOCUS_LOSS:
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                            if (service.isPlaying()) {
                                service.mPausedByTransientLossOfFocus =
                                        msg.arg1 == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT;
                            }
                            service.pause();
                            break;
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                            removeMessages(MusicService.FADEUP);
                            sendEmptyMessage(MusicService.FADEDOWN);
                            break;
                        case AudioManager.AUDIOFOCUS_GAIN:
                            if (!service.isPlaying()
                                    && service.mPausedByTransientLossOfFocus) {
                                service.mPausedByTransientLossOfFocus = false;
                                mCurrentVolume = 0f;
                                service.mPlayer.setVolume(mCurrentVolume);
                                service.play();
                            } else {
                                removeMessages(MusicService.FADEDOWN);
                                sendEmptyMessage(MusicService.FADEUP);
                            }
                            break;
                        default:
                    }
                    break;
                default:
                    break;
            }
        }
    }
}
