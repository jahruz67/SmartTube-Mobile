package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.ServiceManager;
import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.prefs.DeArrowData;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.prefs.common.DataChangeBase.OnDataChange;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.SerialDisposable;

public class DeArrowProcessor implements OnDataChange, BrowseProcessor {
    private static final String TAG = DeArrowProcessor.class.getSimpleName();
    private final OnItemReady mOnItemReady;
    private final MediaItemService mItemService;
    private final DeArrowData mDeArrowData;
    private boolean mIsReplaceTitlesEnabled;
    private boolean mIsReplaceThumbnailsEnabled;
    private final CompositeDisposable mRequests = new CompositeDisposable();
    private final BrowseEnrichmentCache<com.liskovsoft.mediaserviceinterfaces.data.DeArrowData> mCache = new BrowseEnrichmentCache<>();
    private final AtomicInteger mGeneration = new AtomicInteger();
    private final AppPrefs.ProfileChangeListener mProfileChanged = this::dispose;
    private String mLocale;

    public DeArrowProcessor(Context context, OnItemReady onItemReady) {
        mOnItemReady = onItemReady;
        ServiceManager service = YouTubeServiceManager.instance();
        mItemService = service.getMediaItemService();
        mDeArrowData = DeArrowData.instance(context);
        mDeArrowData.setOnChange(this);
        AppPrefs.instance(context).addListener(mProfileChanged);
        initData();
    }

    @Override
    public void onDataChange() {
        dispose();
        initData();
    }

    private void initData() {
        mIsReplaceTitlesEnabled = mDeArrowData.isReplaceTitlesEnabled();
        mIsReplaceThumbnailsEnabled = mDeArrowData.isReplaceThumbnailsEnabled();
    }

    @Override
    public void process(VideoGroup videoGroup) {
        if ((!mIsReplaceTitlesEnabled && !mIsReplaceThumbnailsEnabled) || videoGroup == null || videoGroup.isEmpty()) {
            return;
        }

        String locale = Locale.getDefault().toLanguageTag();
        if (!locale.equals(mLocale)) {
            dispose();
            mLocale = locale;
        }
        int generation = mGeneration.get();
        BrowseVideoIndex videos = new BrowseVideoIndex(videoGroup);
        List<String> videoIds = getVideoIds(videoGroup);
        List<String> missing = new ArrayList<>();
        for (String videoId : videoIds) {
            com.liskovsoft.mediaserviceinterfaces.data.DeArrowData cached = videoId != null ? mCache.get(videoId) : null;
            if (cached != null) {
                apply(videos.get(videoId), cached);
            } else {
                missing.add(videoId);
            }
        }
        if (missing.isEmpty() || generation != mGeneration.get()) {
            return;
        }
        SerialDisposable owner = new SerialDisposable();
        mRequests.add(owner);
        if (generation != mGeneration.get()) {
            mRequests.remove(owner);
            return;
        }
        owner.set(mItemService.getDeArrowDataObserve(missing)
                .doFinally(() -> mRequests.delete(owner))
                .subscribe(deArrowData -> {
                    if (generation != mGeneration.get()) {
                        return;
                    }
                    mCache.put(deArrowData.getVideoId(), deArrowData);
                    apply(videos.get(deArrowData.getVideoId()), deArrowData);
                },
                error -> {
                    Log.d(TAG, "DeArrow cannot process the video");
                }));
    }

    private void apply(Video video, com.liskovsoft.mediaserviceinterfaces.data.DeArrowData data) {
        if (video == null) {
            return;
        }
        if (mIsReplaceTitlesEnabled) {
            video.deArrowTitle = data.getTitle();
        }
        if (mIsReplaceThumbnailsEnabled) {
            video.altCardImageUrl = data.getThumbnailUrl();
        }
        mOnItemReady.onItemReady(video);
    }

    @Override
    public void dispose() {
        mGeneration.incrementAndGet();
        mRequests.clear();
        // Explicit refresh, profile changes and section changes always start with fresh results.
        mCache.clear();
    }

    private List<String> getVideoIds(VideoGroup videoGroup) {
        List<String> result = new ArrayList<>();

        for (Video video : videoGroup.getVideos()) {
            if (video.deArrowProcessed) {
                continue;
            }
            video.deArrowProcessed = true;
            result.add(video.videoId);
        }

        return result;
    }
}
