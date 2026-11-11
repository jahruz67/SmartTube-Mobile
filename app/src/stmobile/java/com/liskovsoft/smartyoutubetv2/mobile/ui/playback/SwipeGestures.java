package com.liskovsoft.smartyoutubetv2.mobile.ui.playback;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Swipe gestures in the landscape player (#48): a vertical swipe on the right half changes the
 * media volume, on the left half this window's brightness, with a small level indicator.
 *
 * The normal touch path sees events immediately so double-tap timers stay accurate. A clear
 * vertical drag cancels that path before taking over volume or brightness. Other gestures
 * continue normally, without replaying delayed events or sending a second DOWN.
 */
final class SwipeGestures {
    /** Dispatches an event through the player's normal (non-swipe) touch handling. */
    interface Router {
        boolean route(MotionEvent event);
    }

    private static final int IDLE = 0;
    private static final int PENDING = 1;
    private static final int SWIPING = 2;
    private static final float FULL_RANGE_FRACTION = 0.8f; // swipe this share of the height for 0 → 100 %
    private static final float MIN_BRIGHTNESS = 0.01f;
    private static final int INDICATOR_HIDE_MS = 800;

    private final Activity mActivity;
    private final AudioManager mAudio;
    private final View mIndicator;
    private final ImageView mIndicatorIcon;
    private final ProgressBar mIndicatorLevel;
    private final int mSlop;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mHideIndicator;

    private int mState = IDLE;
    private MotionEvent mDown;
    private boolean mVolume;
    private float mStartLevel;
    private float mStartY;
    private int mAreaHeight;
    private int mMaxVolume;
    private int mVolumeIndex;
    private boolean mVolumeFixed;
    private final int[] mAreaLocation = new int[2];

    /** Returns null if the indicator isn't in the player layout. */
    static SwipeGestures create(Activity activity) {
        View indicator = activity.findViewById(R.id.mobile_swipe_indicator);
        return indicator != null ? new SwipeGestures(activity, indicator) : null;
    }

    private SwipeGestures(Activity activity, View indicator) {
        mActivity = activity;
        mAudio = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
        mIndicator = indicator;
        mIndicatorIcon = indicator.findViewById(R.id.mobile_swipe_indicator_icon);
        mIndicatorLevel = indicator.findViewById(R.id.mobile_swipe_indicator_level);
        mSlop = ViewConfiguration.get(activity).getScaledTouchSlop() * 2;
        mHideIndicator = () -> mIndicator.setVisibility(View.GONE);
    }

    /** Whether a touch sequence is being watched or swiped. */
    boolean isActive() {
        return mState != IDLE;
    }

    /** Watch a DOWN in the swipe area while letting taps start on time. */
    boolean start(MotionEvent down, View area, Router router) {
        reset();
        mDown = MotionEvent.obtain(down);
        area.getLocationOnScreen(mAreaLocation);
        mVolume = down.getRawX() >= mAreaLocation[0] + area.getWidth() / 2f;
        mAreaHeight = Math.max(1, area.getHeight());
        mState = PENDING;
        return router.route(down);
    }

    /** The rest of a watched or swiping gesture. */
    boolean handle(MotionEvent event, Router router) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE: {
                if (mState == SWIPING) {
                    applyLevel(mStartLevel + (mStartY - event.getY()) / (mAreaHeight * FULL_RANGE_FRACTION));
                    return true;
                }
                float dx = Math.abs(event.getX() - mDown.getX());
                float dy = Math.abs(event.getY() - mDown.getY());
                if (dy > mSlop && dy > dx) {
                    beginSwipe(event, router);
                    return true;
                }
                if (dx > mSlop) {
                    return forward(event, router); // a sideways drag: not ours
                }
                return router.route(event);
            }
            case MotionEvent.ACTION_UP: {
                if (mState == PENDING) {
                    float dx = Math.abs(event.getX() - mDown.getX());
                    float dy = Math.abs(event.getY() - mDown.getY());
                    // A fast release can arrive without a MOVE. It must not become a tap.
                    if (dy > mSlop && dy > dx) {
                        beginSwipe(event, router);
                    } else if (dx > mSlop) {
                        reset();
                        return cancelRoute(event, router);
                    }
                }
                if (mState == SWIPING) {
                    applyLevel(mStartLevel + (mStartY - event.getY()) / (mAreaHeight * FULL_RANGE_FRACTION));
                    reset();
                    mHandler.postDelayed(mHideIndicator, INDICATOR_HIDE_MS);
                    return true;
                }
                return forward(event, router);
            }
            case MotionEvent.ACTION_POINTER_DOWN:
                if (mState == PENDING) {
                    return forward(event, router); // multi-touch isn't a swipe
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                boolean pending = mState == PENDING;
                cancel();
                return !pending || router.route(event);
            default:
                return true;
        }
    }

    private void beginSwipe(MotionEvent event, Router router) {
        cancelRoute(event, router);
        mState = SWIPING;
        mStartLevel = currentLevel();
        mStartY = mDown.getY();
        mIndicatorIcon.setImageResource(mVolume ? R.drawable.ic_swipe_volume : R.drawable.ic_swipe_brightness);
        applyLevel(mStartLevel + (mStartY - event.getY()) / (mAreaHeight * FULL_RANGE_FRACTION));
    }

    private boolean cancelRoute(MotionEvent event, Router router) {
        MotionEvent cancel = MotionEvent.obtain(event);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        try {
            return router.route(cancel);
        } finally {
            cancel.recycle();
        }
    }

    /** Stop watching or swiping (the caller cancels the normal path when another gesture takes over). */
    void cancel() {
        boolean swiping = mState == SWIPING;
        reset();
        if (swiping) {
            mHandler.postDelayed(mHideIndicator, INDICATOR_HIDE_MS);
        }
    }

    void destroy() {
        reset();
        mHandler.removeCallbacks(mHideIndicator);
        mIndicator.setVisibility(View.GONE);
    }

    private boolean forward(MotionEvent event, Router router) {
        reset();
        return router.route(event);
    }

    private void reset() {
        mState = IDLE;
        if (mDown != null) {
            mDown.recycle();
            mDown = null;
        }
    }

    private float currentLevel() {
        if (mVolume) {
            mVolumeFixed = mAudio == null || mAudio.isVolumeFixed();
            mMaxVolume = mAudio != null ? mAudio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) : 0;
            mVolumeIndex = mAudio != null ? mAudio.getStreamVolume(AudioManager.STREAM_MUSIC) : 0;
            return mMaxVolume > 0 ? (float) mVolumeIndex / mMaxVolume : 0f;
        }
        float window = mActivity.getWindow().getAttributes().screenBrightness;
        if (window >= 0) {
            return window;
        }
        // Following the system setting so far: start from it.
        int system = Settings.System.getInt(mActivity.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128);
        return system / 255f;
    }

    private void applyLevel(float level) {
        level = Math.max(0f, Math.min(1f, level));
        if (mVolume) {
            if (!mVolumeFixed && mAudio != null) {
                int index = Math.round(level * mMaxVolume);
                if (index != mVolumeIndex) {
                    mAudio.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0); // our own indicator, no system UI
                    mVolumeIndex = index;
                }
            }
            level = mMaxVolume > 0 ? (float) mVolumeIndex / mMaxVolume : 0f;
        } else {
            WindowManager.LayoutParams attrs = mActivity.getWindow().getAttributes();
            level = Math.max(MIN_BRIGHTNESS, level);
            if (attrs.screenBrightness != level) {
                attrs.screenBrightness = level;
                mActivity.getWindow().setAttributes(attrs);
            }
        }
        showIndicator(level);
    }

    private void showIndicator(float level) {
        mHandler.removeCallbacks(mHideIndicator);
        mIndicatorLevel.setProgress(Math.round(level * 100));
        if (mIndicator.getVisibility() != View.VISIBLE) {
            mIndicator.setVisibility(View.VISIBLE);
            mIndicator.bringToFront();
        }
    }
}
