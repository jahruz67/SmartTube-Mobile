package com.liskovsoft.smartyoutubetv2.mobile.notifications;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.MediaDescription;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Looper;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaSession;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.shadows.ShadowMediaController;
import org.robolectric.shadows.ShadowTransportControls;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@UnstableApi
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, application = Application.class,
        shadows = MediaNotificationServiceTest.BridgeControllerShadow.class)
public class MediaNotificationServiceTest {
    private ServiceController<MediaNotificationService> mService;
    private MediaSessionCompat mSource;

    @After
    public void releaseSessions() {
        if (mService != null) {
            mService.destroy();
        }
        if (mSource != null) {
            mSource.release();
        }
        BridgeControllerShadow.controllers.clear();
    }

    @Test
    public void media3ControlsReachPlatformBridgeAndDisconnectCleanly() throws InterruptedException {
        Application context = RuntimeEnvironment.getApplication();
        mSource = new MediaSessionCompat(context, "sabr-test");
        mSource.setCallback(new MediaSessionCompat.Callback() {});
        MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, "video")
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Video title")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, 10_000)
                .build();
        PlaybackStateCompat state = new PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, 1_000, 1f)
                .setActiveQueueItemId(0)
                .setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE
                        | PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_SEEK_TO)
                .build();
        mSource.setMetadata(metadata);
        mSource.setPlaybackState(state);
        BridgeControllerShadow.sourceMetadata = (MediaMetadata) metadata.getMediaMetadata();
        BridgeControllerShadow.sourceState = (PlaybackState) state.getPlaybackState();
        mService = Robolectric.buildService(MediaNotificationService.class).create();
        MediaNotificationService.start(context, mSource.getSessionToken());
        Intent request = Shadows.shadowOf(context).getNextStartedService();
        mService.get().onStartCommand(request, 0, 1);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        MediaSession session;
        do {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10));
            Thread.sleep(10);
            session = mService.get().onGetSession(null);
        } while (session == null && System.nanoTime() < deadline);
        assertNotNull(ShadowLog.getLogsForTag("MediaNotificationService").toString(), session);
        assertEquals("Video title", session.getPlayer().getMediaMetadata().title);
        session.getPlayer().pause();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        MediaController sourceController = null;
        for (MediaController candidate : BridgeControllerShadow.controllers) {
            if (Shadows.shadowOf(candidate.getTransportControls()).getLastPerformedAction()
                    == PlaybackState.ACTION_PAUSE) {
                sourceController = candidate;
                break;
            }
        }
        assertNotNull("Pause must reach the platform transport", sourceController);
        ShadowTransportControls controls = Shadows.shadowOf(sourceController.getTransportControls());
        session.getPlayer().seekTo(4_321);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(4_321, controls.getSeekToPositionMs());

        Shadows.shadowOf(sourceController).executeOnSessionDestroyed();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertNull(mService.get().onGetSession(null));
    }

    // Robolectric does not propagate MediaSession state to separately constructed platform controllers.
    @Implements(MediaController.class)
    public static class BridgeControllerShadow extends ShadowMediaController {
        static MediaMetadata sourceMetadata;
        static PlaybackState sourceState;
        static final List<MediaController> controllers = new ArrayList<>();
        @RealObject private MediaController mRealController;

        @Implementation
        protected void __constructor__(Context context, android.media.session.MediaSession.Token token) {
            super.__constructor__(context, token);
            controllers.add(mRealController);
        }

        @Implementation
        protected MediaMetadata getMetadata() {
            return sourceMetadata;
        }

        @Implementation
        protected PlaybackState getPlaybackState() {
            return sourceState;
        }

        @Implementation
        protected List<android.media.session.MediaSession.QueueItem> getQueue() {
            return Collections.singletonList(new android.media.session.MediaSession.QueueItem(
                    new MediaDescription.Builder().setMediaId("video").setTitle("Video title").build(), 0));
        }
    }
}
