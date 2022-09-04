package com.naman14.timber;

import com.naman14.timber.helpers.MusicPlaybackTrack;

import java.util.ArrayList;

class Playlist {
    private ArrayList<MusicPlaybackTrack> tracks = new ArrayList<>(100);

    public ArrayList<MusicPlaybackTrack> getTracks() {
        return tracks;
    }

    public void setTracks(ArrayList<MusicPlaybackTrack> tracks) {
        this.tracks = tracks;
    }

    public boolean isEmpty() {
        return tracks == null || tracks.size() == 0;
    }

    public boolean isNotEmpty() {
        return tracks != null && tracks.size() > 0;
    }

    public void clear() {
        tracks.clear();
    }

    public int size() {
        return tracks.size();
    }

    public long getTrackId(int index) {
        return tracks.get(index).mId;
    }
}
