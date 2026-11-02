package com.liskovsoft.smartyoutubetv2.mobile.ui.playback;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.AccelerateInterpolator;

/** Holds a possible downward drag so taps and other gestures can be replayed unchanged. */
final class SwipeToDismiss {
    private final View mContent;
    private final Runnable mDismiss;
    private final float mSlop;
    private final float mDistance;
    private MotionEvent mDown;
    private boolean mDragging;
    private boolean mSettling;
    private boolean mDiscardUntilUp;

    SwipeToDismiss(View content, Runnable dismiss) {
        mContent = content;
        mDismiss = dismiss;
        mSlop = ViewConfiguration.get(content.getContext()).getScaledTouchSlop() * 2;
        mDistance = 64 * content.getResources().getDisplayMetrics().density;
    }

    boolean isActive() {
        return mDown != null || mSettling || mDiscardUntilUp;
    }

    boolean start(MotionEvent event) {
        mDown = MotionEvent.obtain(event);
        return true;
    }

    boolean handle(MotionEvent event, SwipeGestures.Router router) {
        if (mDiscardUntilUp) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                mDiscardUntilUp = false;
            }
            return true;
        }
        if (mSettling) return true;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                float dy = event.getRawY() - mDown.getRawY();
                float dx = Math.abs(event.getRawX() - mDown.getRawX());
                if (!mDragging) {
                    if (dy > mSlop && dy > 2 * dx) {
                        mDragging = true;
                    } else if (dx > mSlop || dy < -mSlop) {
                        return replay(event, router);
                    }
                }
                if (mDragging) mContent.setTranslationY(Math.max(0, dy));
                return true;
            case MotionEvent.ACTION_UP:
                if (!mDragging) return replay(event, router);
                boolean dismiss = event.getRawY() - mDown.getRawY() >= mDistance;
                releaseDown();
                settle(dismiss);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (!mDragging) return replay(event, router);
                releaseDown();
                mDiscardUntilUp = true;
                settle(false);
                return true;
            case MotionEvent.ACTION_CANCEL:
                releaseDown();
                settle(false);
                return true;
            default:
                return true;
        }
    }

    private boolean replay(MotionEvent event, SwipeGestures.Router router) {
        MotionEvent down = mDown;
        mDown = null;
        try {
            router.route(down);
            return router.route(event);
        } finally {
            down.recycle();
        }
    }

    private void settle(boolean dismiss) {
        mSettling = true;
        mContent.animate().translationY(dismiss ? mContent.getHeight() : 0)
                .setDuration(dismiss ? 180 : 140)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    mSettling = false;
                    if (dismiss) mDismiss.run();
                    mContent.setTranslationY(0);
                }).start();
    }

    void cancel() {
        mContent.animate().withEndAction(null).cancel();
        mContent.setTranslationY(0);
        mSettling = false;
        mDiscardUntilUp = false;
        releaseDown();
    }

    private void releaseDown() {
        if (mDown != null) {
            mDown.recycle();
            mDown = null;
        }
        mDragging = false;
    }
}
