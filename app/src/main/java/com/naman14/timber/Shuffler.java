package com.naman14.timber;

import java.util.LinkedList;
import java.util.Random;
import java.util.TreeSet;

final class Shuffler {
    private final LinkedList<Integer> mHistoryOfNumbers = new LinkedList<>();

    private final TreeSet<Integer> mPreviousNumbers = new TreeSet<>();

    private final Random mRandom = new Random();

    private int mPrevious;

    public Shuffler() {
        super();
    }

    public int nextInt(final int interval) {
        int next;
        do {
            next = mRandom.nextInt(interval);
        } while (next == mPrevious && interval > 1
                && !mPreviousNumbers.contains(next));
        mPrevious = next;
        mHistoryOfNumbers.add(mPrevious);
        mPreviousNumbers.add(mPrevious);
        cleanUpHistory();
        return next;
    }

    private void cleanUpHistory() {
        if (!mHistoryOfNumbers.isEmpty() && mHistoryOfNumbers.size() >= MusicService.MAX_HISTORY_SIZE) {
            for (int i = 0; i < Math.max(1, MusicService.MAX_HISTORY_SIZE / 2); i++) {
                mPreviousNumbers.remove(mHistoryOfNumbers.removeFirst());
            }
        }
    }
}
