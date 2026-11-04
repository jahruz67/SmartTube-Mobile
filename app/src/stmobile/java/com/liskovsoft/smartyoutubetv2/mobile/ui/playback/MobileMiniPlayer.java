package com.liskovsoft.smartyoutubetv2.mobile.ui.playback;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.tv.R;

/** Keeps the active video visible in a small in-app bar while navigating phone screens. */
public final class MobileMiniPlayer {
    private static MobilePlaybackActivity sPlaybackActivity;
    private static ViewGroup sHost;
    private static View sOverlay;
    private static TextView sTitleView;
    private static TextView sPlayPauseView;
    private static boolean sMinimized;
    private static boolean sExpanding;

    private MobileMiniPlayer() {}

    static void minimize(MobilePlaybackActivity activity) {
        if (isMinimizedPlayback(activity)) return;
        sPlaybackActivity = activity;
        sMinimized = true;
        sExpanding = false;
        activity.getMobileFragment().prepareVideoSurfaceTransfer();
        activity.getMobileFragment().blockEngine(true);
        activity.showParentForMiniPlayer();
    }

    static boolean isPlaybackActivity(MobilePlaybackActivity activity) {
        return sPlaybackActivity == activity;
    }

    static boolean isMinimizedPlayback(MobilePlaybackActivity activity) {
        return sMinimized && sPlaybackActivity == activity;
    }

    static void restore(MobilePlaybackActivity activity) {
        if (sPlaybackActivity != activity) return;
        MobilePlaybackFragment fragment = activity.getMobileFragment();
        if (fragment != null) {
            fragment.restoreVideoSurface();
        }
        sMinimized = false;
        sExpanding = false;
        removeOverlay();
        sPlaybackActivity = null;
    }

    static void onPlaybackFinished(MobilePlaybackActivity activity) {
        if (sPlaybackActivity != activity) return;
        MobilePlaybackFragment fragment = activity.getMobileFragment();
        if (fragment != null) {
            fragment.setPlayWhenReady(false);
            fragment.blockEngine(false);
        }
        restore(activity);
    }

    public static void show(Activity activity) {
        if (!sMinimized || sPlaybackActivity == null || activity == sPlaybackActivity
                || activity.isFinishing() || activity.isDestroyed()) return;
        ViewGroup host = activity.findViewById(android.R.id.content);
        if (host == null) return;
        if (sHost == host && sOverlay != null) return;

        MobilePlaybackFragment fragment = sPlaybackActivity.getMobileFragment();
        if (fragment == null || fragment.getView() == null || !fragment.isEngineInitialized()) return;
        removeOverlay();

        FrameLayout overlay = new FrameLayout(activity);
        FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        host.addView(overlay, overlayParams);

        LinearLayout bar = new LinearLayout(activity);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Color.rgb(28, 28, 28));
        bar.setOnClickListener(v -> expand(activity));
        int height = dp(activity, 64);
        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, height, Gravity.BOTTOM);
        barParams.leftMargin = dp(activity, 8);
        barParams.rightMargin = dp(activity, 8);
        barParams.bottomMargin = dp(activity, 72);
        overlay.addView(bar, barParams);

        FrameLayout video = new FrameLayout(activity);
        bar.addView(video, new LinearLayout.LayoutParams(dp(activity, 112), height));
        video.setOnClickListener(v -> expand(activity));

        Video current = PlaybackPresenter.instance(activity).getVideo();
        TextView title = new TextView(activity);
        title.setText(current != null && current.getTitle() != null ? current.getTitle() : "Now playing");
        title.setTextColor(Color.WHITE);
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(activity, 12), 0, dp(activity, 8), 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, height, 1));
        title.setOnClickListener(v -> expand(activity));
        sTitleView = title;

        TextView playPause = button(activity, "Ⅱ", activity.getString(R.string.mobile_player_play_pause));
        playPause.setOnClickListener(v -> fragment.toggleMiniPlayerPlayback());
        bar.addView(playPause, new LinearLayout.LayoutParams(dp(activity, 44), height));
        sPlayPauseView = playPause;

        TextView close = button(activity, "×", activity.getString(R.string.mobile_player_collapse));
        close.setOnClickListener(v -> close());
        bar.addView(close, new LinearLayout.LayoutParams(dp(activity, 44), height));

        sHost = host;
        sOverlay = overlay;
        fragment.moveVideoSurfaceTo(video);
        updatePlaybackState(fragment.getMiniPlayerPlayWhenReady());
    }

    public static void onHostDestroyed(Activity activity) {
        if (sOverlay != null && sOverlay.getContext() == activity) {
            MobilePlaybackFragment fragment = sPlaybackActivity != null ? sPlaybackActivity.getMobileFragment() : null;
            if (fragment != null) {
                fragment.restoreVideoSurface();
            }
            removeOverlay();
        }
    }

    static void updateVideo(Video video) {
        if (sTitleView != null) {
            sTitleView.setText(video != null && video.getTitle() != null
                    ? video.getTitle() : "Now playing");
        }
    }

    static void updatePlaybackState(boolean playing) {
        if (sOverlay != null) {
            sOverlay.setKeepScreenOn(playing);
        }
        if (sPlayPauseView != null) {
            sPlayPauseView.setText(playing ? "Ⅱ" : "▶");
        }
    }

    private static TextView button(Activity activity, String label, String description) {
        TextView button = new TextView(activity);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(24);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(description);
        button.setFocusable(true);
        return button;
    }

    private static void expand(Activity activity) {
        if (sPlaybackActivity == null || sExpanding) return;
        sExpanding = true;
        activity.startActivity(new Intent(activity, MobilePlaybackActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION));
    }

    private static void close() {
        MobilePlaybackActivity playback = sPlaybackActivity;
        if (playback == null) return;
        playback.finishMiniPlayer();
    }

    private static void removeOverlay() {
        if (sOverlay != null && sOverlay.getParent() instanceof ViewGroup) {
            ((ViewGroup) sOverlay.getParent()).removeView(sOverlay);
        }
        sOverlay = null;
        sHost = null;
        sTitleView = null;
        sPlayPauseView = null;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
