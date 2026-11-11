package com.liskovsoft.smartyoutubetv2.common.app.models.search;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Scheduler;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

/** Keeps interactive search work independent of background browsing and enrichment. */
public final class SearchRequest {
    private static final Scheduler RESULTS = newScheduler(2);
    private static final Scheduler SUGGESTIONS = newScheduler(1);

    private SearchRequest() {
    }

    public static <T> Observable<T> results(ObservableOnSubscribe<T> source) {
        return create(source, RESULTS);
    }

    public static <T> Observable<T> suggestions(ObservableOnSubscribe<T> source) {
        return create(source, SUGGESTIONS);
    }

    private static Scheduler newScheduler(int workerCount) {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(workerCount);
        // Canceled keystrokes must not leave queued requests or an unbounded number of workers.
        executor.setRemoveOnCancelPolicy(true);
        executor.setKeepAliveTime(60, TimeUnit.SECONDS);
        executor.allowCoreThreadTimeOut(true);
        return Schedulers.from(executor, true);
    }

    private static <T> Observable<T> create(ObservableOnSubscribe<T> source, Scheduler scheduler) {
        return Observable.<T>create(emitter -> {
                    if (emitter.isDisposed()) return;
                    try {
                        source.subscribe(emitter);
                    } catch (Exception error) {
                        // Interrupted requests can fail after cancellation; do not deliver stale errors.
                        emitter.tryOnError(error);
                    }
                })
                .subscribeOn(scheduler)
                .observeOn(AndroidSchedulers.mainThread());
    }
}
