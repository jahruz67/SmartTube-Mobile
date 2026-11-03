package com.liskovsoft.smartyoutubetv2.mobile.ui.playback;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.mobile.ui.base.MobileActivity;
import com.liskovsoft.smartyoutubetv2.tv.R;

/** Keeps the active video visible in a small in-app bar while navigating phone screens. */
public final class MobileMiniPlayer {
    private static MobilePlaybackActivity sPlaybackActivity;
    private static ViewGroup sHost;
    private static View sOverlay;
    private static boolean sMinimized;

    private MobileMiniPlayer() {}

    static void minimize(MobilePlaybackActivity activity) {
        sPlaybackActivity = activity;
        sMinimized = true;
        activity.getMobileFragment().blockEngine(true);
        activity.getMobileFragment().detachVideoSurface();
        activity.showParentForMiniPlayer();
    }

    static boolean isPlaybackActivity(MobilePlaybackActivity activity) {
        return sPlaybackActivity == activity;
    }

    static void restore(MobilePlaybackActivity activity) {
        if (sPlaybackActivity != activity) return;
        sMinimized = false;
        removeOverlay();
    }

    static void onPlaybackFinished(MobilePlaybackActivity activity) {
        if (sPlaybackActivity != activity) return;
        sMinimized = false;
        removeOverlay();
        sPlaybackActivity = null;
    }

    public static void show(MobileActivity activity) {
        if (!sMinimized || sPlaybackActivity == null) return;
        ViewGroup host = activity.findViewById(android.R.id.content);
        if (host == null) return;
        if (sHost == host && sOverlay != null) return;

        removeOverlay();
        MobilePlaybackFragment fragment = sPlaybackActivity.getMobileFragment();
        if (fragment == null) return;

        FrameLayout overlay = new FrameLayout(activity);
        FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        host.addView(overlay, overlayParams);

        LinearLayout bar = new LinearLayout(activity);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Color.rgb(28, 28, 28));
        int height = dp(activity, 90);
        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, height, Gravity.BOTTOM);
        barParams.leftMargin = dp(activity, 8);
        barParams.rightMargin = dp(activity, 8);
        barParams.bottomMargin = dp(activity, 72);
        overlay.addView(bar, barParams);

        FrameLayout video = new FrameLayout(activity);
        bar.addView(video, new LinearLayout.LayoutParams(dp(activity, 160), height));
        fragment.moveVideoSurfaceTo(video);
        video.setOnClickListener(v -> expand(activity));

        Video current = PlaybackPresenter.instance(activity).getVideo();
        TextView title = new TextView(activity);
        title.setText(current != null && current.getTitle() != null ? current.getTitle() : "Now playing");
        title.setTextColor(Color.WHITE);
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setMaxLines(2);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(activity, 12), 0, dp(activity, 8), 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, height, 1));
        title.setOnClickListener(v -> expand(activity));

        TextView playPause = button(activity, "Ⅱ", activity.getString(R.string.mobile_player_play_pause));
        playPause.setOnClickListener(v -> {
            fragment.setPlayWhenReady(!fragment.getPlayWhenReady());
            playPause.setText(fragment.getPlayWhenReady() ? "Ⅱ" : "▶");
        });
        bar.addView(playPause, new LinearLayout.LayoutParams(dp(activity, 44), height));

        TextView close = button(activity, "×", activity.getString(R.string.mobile_player_collapse));
        close.setOnClickListener(v -> close());
        bar.addView(close, new LinearLayout.LayoutParams(dp(activity, 44), height));

        sHost = host;
        sOverlay = overlay;
    }

    private static TextView button(MobileActivity activity, String label, String description) {
        TextView button = new TextView(activity);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(24);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(description);
        button.setFocusable(true);
        return button;
    }

    private static void expand(MobileActivity activity) {
        if (sPlaybackActivity == null) return;
        removeOverlay();
        activity.startActivity(new Intent(activity, MobilePlaybackActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
    }

    private static void close() {
        MobilePlaybackActivity playback = sPlaybackActivity;
        if (playback == null) return;
        sMinimized = false;
        removeOverlay();
        MobilePlaybackFragment fragment = playback.getMobileFragment();
        if (fragment != null) {
            fragment.restoreVideoSurface();
            fragment.blockEngine(false);
        }
        sPlaybackActivity = null;
        playback.finishReally();
    }

    private static void removeOverlay() {
        if (sOverlay != null && sOverlay.getParent() instanceof ViewGroup) {
            ((ViewGroup) sOverlay.getParent()).removeView(sOverlay);
        }
        sOverlay = null;
        sHost = null;
    }

    private static int dp(MobileActivity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
