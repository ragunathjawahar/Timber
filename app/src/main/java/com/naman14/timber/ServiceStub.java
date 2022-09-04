package com.naman14.timber;

import android.os.RemoteException;

import com.naman14.timber.helpers.MusicPlaybackTrack;
import com.naman14.timber.utils.TimberUtils;

import java.lang.ref.WeakReference;

final class ServiceStub extends ITimberService.Stub {
    private final WeakReference<MusicService> mService;

    ServiceStub(final MusicService service) {
        mService = new WeakReference<>(service);
    }

    @Override
    public void openFile(final String path) {
        mService.get().openFile(path);
    }

    @Override
    public void open(final long[] list, final int position, long sourceId, int sourceType)
            throws RemoteException {
        mService.get().open(list, position, sourceId, TimberUtils.IdType.getTypeById(sourceType));
    }

    @Override
    public void stop() {
        mService.get().stop();
    }

    @Override
    public void pause() throws RemoteException {
        mService.get().pause();
    }

    @Override
    public void play() throws RemoteException {
        mService.get().play();
    }

    @Override
    public void prev(boolean forcePrevious) {
        mService.get().prev(forcePrevious);
    }

    @Override
    public void next() throws RemoteException {
        mService.get().gotoNext(true);
    }

    @Override
    public void enqueue(final long[] list, final int action, long sourceId, int sourceType) {
        mService.get().enqueue(list, action, sourceId, TimberUtils.IdType.getTypeById(sourceType));
    }

    @Override
    public void moveQueueItem(final int from, final int to) {
        mService.get().moveQueueItem(from, to);
    }

    @Override
    public void refresh() {
        mService.get().refresh();
    }

    @Override
    public void playlistChanged() {
        mService.get().playlistChanged();
    }

    @Override
    public boolean isPlaying() {
        return mService.get().isPlaying();
    }

    @Override
    public long[] getQueue() {
        return mService.get().getQueue();
    }

    @Override
    public long getQueueItemAtPosition(int position) {
        return mService.get().getQueueItemAtPosition(position);
    }

    @Override
    public int getQueueSize() {
        return mService.get().getQueueSize();
    }

    @Override
    public int getQueueHistoryPosition(int position) {
        return mService.get().getQueueHistoryPosition(position);
    }

    @Override
    public int getQueueHistorySize() {
        return mService.get().getQueueHistorySize();
    }

    @Override
    public int[] getQueueHistoryList() {
        return mService.get().getQueueHistoryList();
    }

    @Override
    public long duration() throws RemoteException {
        return mService.get().duration();
    }

    @Override
    public long position() throws RemoteException {
        return mService.get().position();
    }

    @Override
    public long seek(final long position) {
        return mService.get().seek(position);
    }

    @Override
    public void seekRelative(final long deltaInMs) {
        mService.get().seekRelative(deltaInMs);
    }

    @Override
    public long getAudioId() {
        return mService.get().getAudioId();
    }

    @Override
    public MusicPlaybackTrack getCurrentTrack() {
        return mService.get().getCurrentTrack();
    }

    @Override
    public MusicPlaybackTrack getTrack(int index) {
        return mService.get().getTrack(index);
    }

    @Override
    public long getNextAudioId() {
        return mService.get().getNextAudioId();
    }

    @Override
    public long getPreviousAudioId() {
        return mService.get().getPreviousAudioId();
    }

    @Override
    public long getArtistId() {
        return mService.get().getArtistId();
    }

    @Override
    public long getAlbumId() {
        return mService.get().getAlbumId();
    }

    @Override
    public String getArtistName() {
        return mService.get().getArtistName();
    }

    @Override
    public String getTrackName() {
        return mService.get().getTrackName();
    }

    @Override
    public String getAlbumName() {
        return mService.get().getAlbumName();
    }

    @Override
    public String getPath() {
        return mService.get().getPath();
    }

    @Override
    public int getQueuePosition() throws RemoteException {
        return mService.get().getQueuePosition();
    }

    @Override
    public void setQueuePosition(final int index) throws RemoteException {
        mService.get().setQueuePosition(index);
    }

    @Override
    public int getShuffleMode() {
        return mService.get().getShuffleMode();
    }

    @Override
    public void setShuffleMode(final int shufflemode) {
        mService.get().setShuffleMode(shufflemode);
    }

    @Override
    public int getRepeatMode() {
        return mService.get().getRepeatMode();
    }

    @Override
    public void setRepeatMode(final int repeatmode) {
        mService.get().setRepeatMode(repeatmode);
    }

    @Override
    public int removeTracks(final int first, final int last) {
        return mService.get().removeTracks(first, last);
    }

    @Override
    public int removeTrack(final long id) {
        return mService.get().removeTrack(id);
    }

    @Override
    public boolean removeTrackAtPosition(final long id, final int position) {
        return mService.get().removeTrackAtPosition(id, position);
    }

    @Override
    public int getMediaMountedCount() {
        return mService.get().getMediaMountedCount();
    }

    @Override
    public int getAudioSessionId() {
        return mService.get().getAudioSessionId();
    }
}
