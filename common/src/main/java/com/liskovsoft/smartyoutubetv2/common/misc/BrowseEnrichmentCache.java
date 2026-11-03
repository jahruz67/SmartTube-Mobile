package com.liskovsoft.smartyoutubetv2.common.misc;

import android.os.SystemClock;
import android.util.LruCache;

/** Short-lived successful results; never limits the number of items that can be requested. */
final class BrowseEnrichmentCache<T> {
    private static final long MAX_AGE_MS = 5 * 60 * 1_000L;
    private final LruCache<String, Entry<T>> mEntries = new LruCache<>(512);

    synchronized T get(String videoId) {
        Entry<T> entry = mEntries.get(videoId);
        if (entry == null) {
            return null;
        }
        if (SystemClock.elapsedRealtime() - entry.createdAt >= MAX_AGE_MS) {
            mEntries.remove(videoId);
            return null;
        }
        return entry.value;
    }

    synchronized void put(String videoId, T value) {
        if (videoId != null && value != null) {
            mEntries.put(videoId, new Entry<>(value));
        }
    }

    synchronized void clear() {
        mEntries.evictAll();
    }

    private static final class Entry<T> {
        final T value;
        final long createdAt = SystemClock.elapsedRealtime();

        Entry(T value) {
            this.value = value;
        }
    }
}
