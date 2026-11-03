package com.liskovsoft.smartyoutubetv2.common.misc;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;

import java.util.HashMap;
import java.util.Map;

/** Request-local first-match lookup, rebuilt when the group's membership changes. */
final class BrowseVideoIndex {
    private final VideoGroup mGroup;
    private final Map<String, Video> mVideos = new HashMap<>();
    private long mRevision = -1;
    private int mSize = -2;

    BrowseVideoIndex(VideoGroup group) {
        mGroup = group;
    }

    Video get(String videoId) {
        if (mRevision != mGroup.getRevision() || mSize != mGroup.getSize()) {
            mVideos.clear();
            if (mGroup.getSize() >= 0) {
                for (Video video : mGroup.getVideos()) {
                    if (!mVideos.containsKey(video.videoId)) {
                        mVideos.put(video.videoId, video);
                    }
                }
            }
            mRevision = mGroup.getRevision();
            mSize = mGroup.getSize();
        }
        return mVideos.get(videoId);
    }
}
