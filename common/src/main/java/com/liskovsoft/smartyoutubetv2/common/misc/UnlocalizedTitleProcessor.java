package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.util.Pair;

import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.ServiceManager;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.prefs.common.DataChangeBase.OnDataChange;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.SerialDisposable;

public class UnlocalizedTitleProcessor implements OnDataChange, BrowseProcessor {
    private static final String TAG = UnlocalizedTitleProcessor.class.getSimpleName();
    private final OnItemReady mOnItemReady;
    private final MediaItemService mItemService;
    private final MainUIData mMainUIData;
    private boolean mIsUnlocalizedTitlesEnabled;
    private final CompositeDisposable mRequests = new CompositeDisposable();
    private final BrowseEnrichmentCache<String> mCache = new BrowseEnrichmentCache<>();
    private final AtomicInteger mGeneration = new AtomicInteger();
    private final AppPrefs.ProfileChangeListener mProfileChanged = this::dispose;
    private String mLocale;

    public UnlocalizedTitleProcessor(Context context, OnItemReady onItemReady) {
        mOnItemReady = onItemReady;
        ServiceManager service = YouTubeServiceManager.instance();
        mItemService = service.getMediaItemService();
        mMainUIData = MainUIData.instance(context);
        mMainUIData.setOnChange(this);
        AppPrefs.instance(context).addListener(mProfileChanged);
        initData();
    }

    @Override
    public void onDataChange() {
        dispose();
        initData();
    }

    private void initData() {
        mIsUnlocalizedTitlesEnabled = mMainUIData.isUnlocalizedTitlesEnabled();
    }

    @Override
    public void process(VideoGroup videoGroup) {
        if (!mIsUnlocalizedTitlesEnabled || videoGroup == null || videoGroup.isEmpty()) {
            return;
        }

        String locale = Locale.getDefault().toLanguageTag();
        if (!locale.equals(mLocale)) {
            dispose();
            mLocale = locale;
        }
        int generation = mGeneration.get();
        BrowseVideoIndex videos = new BrowseVideoIndex(videoGroup);
        List<String> missing = new ArrayList<>();
        for (String videoId : getVideoIds(videoGroup)) {
            String cached = videoId != null ? mCache.get(videoId) : null;
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
        owner.set(Observable.fromIterable(missing)
                .flatMap(videoId -> mItemService.getUnlocalizedTitleObserve(videoId)
                        .map(newTitle -> new Pair<>(videoId, newTitle)))
                .doFinally(() -> mRequests.delete(owner))
                .subscribe(title -> {
                    if (generation != mGeneration.get()) {
                        return;
                    }
                    mCache.put(title.first, title.second);
                    apply(videos.get(title.first), title.second);
                },
                error -> {
                    Log.d(TAG, "Unlocalized title: Cannot process the video");
                }));
    }

    private void apply(Video video, String title) {
        if (video == null) {
            return;
        }
        video.deArrowTitle = title;
        if (!Helpers.equals(video.title, video.deArrowTitle)) {
            mOnItemReady.onItemReady(video);
        }
    }

    @Override
    public void dispose() {
        mGeneration.incrementAndGet();
        mRequests.clear();
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
