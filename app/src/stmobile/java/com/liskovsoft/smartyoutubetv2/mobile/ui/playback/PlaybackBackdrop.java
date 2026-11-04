package com.liskovsoft.smartyoutubetv2.mobile.ui.playback;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import com.liskovsoft.smartyoutubetv2.mobile.ui.base.MobileActivity;
import com.liskovsoft.smartyoutubetv2.mobile.ui.browse.MobileSettingsActivity;
import com.liskovsoft.smartyoutubetv2.mobile.ui.dialogs.MobileAppDialogActivity;

import java.lang.ref.WeakReference;

/** Draws the retained caller page behind the player without changing activity visibility. */
public final class PlaybackBackdrop extends View {
    private static WeakReference<View> sLastPage = new WeakReference<>(null);
    private WeakReference<View> mPage = new WeakReference<>(null);

    PlaybackBackdrop(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public static void rememberPage(Activity activity) {
        if (!(activity instanceof MobileActivity) || activity instanceof MobileSettingsActivity
                || activity instanceof MobileAppDialogActivity || activity.isFinishing()) {
            return;
        }
        sLastPage = new WeakReference<>(activity.findViewById(android.R.id.content));
    }

    void refresh() {
        mPage = sLastPage;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        View page = mPage.get();
        if (page == null || page.getWidth() == 0 || page.getHeight() == 0) {
            return;
        }
        int saveCount = canvas.save();
        canvas.scale((float) getWidth() / page.getWidth(), (float) getHeight() / page.getHeight());
        page.draw(canvas);
        canvas.restoreToCount(saveCount);
    }
}
