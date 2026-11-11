package com.liskovsoft.smartyoutubetv2.common.app.models.search;

import android.text.TextUtils;

import com.liskovsoft.mediaserviceinterfaces.ContentService;
import com.liskovsoft.mediaserviceinterfaces.ServiceManager;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Tag;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import io.reactivex.disposables.Disposable;

import java.util.Collections;
import java.util.List;

public class MediaServiceSearchTagProvider implements SearchTagsProvider {
    private static final String TAG = MediaServiceSearchTagProvider.class.getSimpleName();
    private final ContentService mContentService;
    private final boolean mIgnoreEmptyQuery;
    private Disposable mTagsAction;

    public MediaServiceSearchTagProvider(boolean ignoreEmptyQuery) {
        mIgnoreEmptyQuery = ignoreEmptyQuery;
        ServiceManager service = YouTubeServiceManager.instance();
        mContentService = service.getContentService();
    }

    @Override
    public void search(String query, ResultsCallback callback) {
        cancel();

        if (mIgnoreEmptyQuery && TextUtils.isEmpty(query)) {
            callback.onResults(null);
            return;
        }

        // Keep autocomplete independent of the shared browse worker queue.
        mTagsAction = SearchRequest.<List<Tag>>suggestions(emitter -> {
                    List<String> suggestions = mContentService.getSearchTags(query);
                    if (emitter.isDisposed()) return;
                    if (suggestions == null) {
                        RxHelper.onError(emitter, "Search suggestions returned no data");
                        return;
                    }
                    List<Tag> tags = Tag.from(suggestions);
                    emitter.onNext(tags != null ? tags : Collections.emptyList());
                    emitter.onComplete();
                })
                .subscribe(
                        callback::onResults,
                        error -> {
                            Log.e(TAG, "Unable to load search suggestions: %s", error.getMessage());
                            callback.onResults(null);
                        }
                );
    }

    public void cancel() {
        RxHelper.disposeActions(mTagsAction);
        mTagsAction = null;
    }
}
