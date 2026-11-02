package com.liskovsoft.smartyoutubetv2.mobile.ui.playback;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.AccelerateInterpolator;

/** Watches normal touches, cancelling the player's gesture only when a downward drag takes over. */
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

    void start(MotionEvent event) {
        releaseDown();
        mDown = MotionEvent.obtain(event);
    }

    boolean handle(MotionEvent event, SwipeGestures.Router cancelPlayerTouch) {
        if (mDiscardUntilUp) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                mDiscardUntilUp = false;
            }
            return true;
        }
        if (mSettling) {
            // A new finger can land before snap-back finishes. Consume its whole sequence.
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) mDiscardUntilUp = true;
            return true;
        }
        if (mDown == null) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                float dy = event.getRawY() - mDown.getRawY();
                float dx = Math.abs(event.getRawX() - mDown.getRawX());
                if (!mDragging) {
                    if (dy > mSlop && dy > 2 * dx) {
                        cancelPlayerTouch.route(event);
                        mDragging = true;
                    } else if (dx > mSlop || Math.abs(dy) > mSlop) {
                        releaseDown();
                        return false;
                    }
                }
                if (mDragging) mContent.setTranslationY(Math.max(0, dy));
                return mDragging;
            case MotionEvent.ACTION_UP:
                float releaseY = event.getRawY() - mDown.getRawY();
                float releaseX = Math.abs(event.getRawX() - mDown.getRawX());
                // Fast swipes may reach UP without an intermediate MOVE being delivered.
                boolean dismiss = releaseY >= mDistance && releaseY > 2 * releaseX;
                if (!mDragging && !dismiss) {
                    releaseDown();
                    return false;
                }
                if (!mDragging) cancelPlayerTouch.route(event);
                mContent.setTranslationY(Math.max(0, releaseY));
                releaseDown();
                settle(dismiss);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (!mDragging) {
                    releaseDown();
                    return false;
                }
                releaseDown();
                mDiscardUntilUp = true;
                settle(false);
                return true;
            case MotionEvent.ACTION_CANCEL:
                boolean wasDragging = mDragging;
                releaseDown();
                if (wasDragging) settle(false);
                return wasDragging;
            default:
                return mDragging;
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
