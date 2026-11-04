package com.liskovsoft.smartyoutubetv2.tv.ui.playback.mod.surface;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.TextureView.SurfaceTextureListener;
import android.view.View;
import android.view.ViewGroup;
import com.liskovsoft.smartyoutubetv2.tv.R;

public class TextureViewWrapper implements SurfaceWrapper {
    private final TextureView mVideoSurface;
    private final boolean mRetainSurfaceOnDetach;
    private SurfaceHolder.Callback mMediaPlaybackCallback;
    private SurfaceTexture mSurfaceTexture;
    private TextureViewSurfaceHolder mSurfaceHolder;
    private boolean mReleased;

    public TextureViewWrapper(Context context, ViewGroup root) {
        this(context, root, false);
    }

    @SuppressLint("WrongConstant")
    public TextureViewWrapper(Context context, ViewGroup root, boolean retainSurfaceOnDetach) {
        mRetainSurfaceOnDetach = retainSurfaceOnDetach;
        mVideoSurface = (TextureView) LayoutInflater.from(context).inflate(
                R.layout.lb_video_texture, root, false);
        mVideoSurface.setSurfaceTextureListener(new SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                if (mReleased) {
                    return;
                }
                if (mSurfaceTexture != null) {
                    // Reuse the decoder's output when this view moves between activity windows.
                    if (surface != mSurfaceTexture) {
                        mVideoSurface.setSurfaceTexture(mSurfaceTexture);
                    }
                    return;
                }
                mSurfaceTexture = surface;
                mSurfaceHolder = new TextureViewSurfaceHolder(new Surface(surface));
                if (mMediaPlaybackCallback != null) {
                    mMediaPlaybackCallback.surfaceCreated(mSurfaceHolder);
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                if (mMediaPlaybackCallback != null && mSurfaceHolder != null) {
                    mMediaPlaybackCallback.surfaceChanged(mSurfaceHolder, 4, width, height);
                }
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                if (mRetainSurfaceOnDetach && !mReleased) {
                    return false;
                }
                releaseSurface();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });
    }

    /**
     * Adds {@link SurfaceHolder.Callback} to {@link SurfaceView}.
     */
    public void setSurfaceHolderCallback(SurfaceHolder.Callback callback) {
        mMediaPlaybackCallback = callback;

        if (callback != null && mSurfaceHolder != null) {
            callback.surfaceCreated(mSurfaceHolder);
        }
    }

    /** Releases a retained output when its owning fragment's view is destroyed. */
    public void release() {
        mReleased = true;
        SurfaceTexture texture = mSurfaceTexture;
        releaseSurface();
        mMediaPlaybackCallback = null;
        // Attached textures are still owned by TextureView until its destroy callback.
        if (texture != null && !mVideoSurface.isAvailable()) {
            texture.release();
        }
    }

    private void releaseSurface() {
        if (mSurfaceHolder != null) {
            if (mMediaPlaybackCallback != null) {
                mMediaPlaybackCallback.surfaceDestroyed(mSurfaceHolder);
            }
            mSurfaceHolder.getSurface().release();
            mSurfaceHolder = null;
        }
        mSurfaceTexture = null;
    }

    @Override
    public View getSurfaceView() {
        return mVideoSurface;
    }
}
