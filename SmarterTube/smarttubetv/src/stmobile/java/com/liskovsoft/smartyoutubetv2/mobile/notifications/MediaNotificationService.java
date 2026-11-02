package com.liskovsoft.smartyoutubetv2.mobile.notifications;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.support.v4.media.session.MediaSessionCompat;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.IntentCompat;
import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.DefaultMediaNotificationProvider;
import androidx.media3.session.MediaController;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.mobile.ui.playback.MobilePlaybackActivity;
import com.liskovsoft.smartyoutubetv2.tv.R;

/** Media3 owns the phone's media controls; its legacy controller preserves the custom SABR player. */
@UnstableApi
public class MediaNotificationService extends MediaSessionService {
    private static final String TAG = MediaNotificationService.class.getSimpleName();
    private static final int NOTIFICATION_ID = MediaNotificationService.class.hashCode();
    private static final String CHANNEL_ID = "com.codesculptor.smartertube_media";
    private static final String EXTRA_SESSION_TOKEN = "media_session_token";
    private static final String ACTION_UPDATE = "smartertube.media.UPDATE";
    private MediaSessionCompat.Token mSourceToken;
    private ListenableFuture<MediaController> mControllerFuture;
    private MediaController mController;
    private ForwardingPlayer mPlayer;
    private MediaSession mSession;
    private int mStartId;

    public static void start(Context context, MediaSessionCompat.Token token) {
        Intent intent = new Intent(context, MediaNotificationService.class)
                .setAction(ACTION_UPDATE)
                .putExtra(EXTRA_SESSION_TOKEN, token);
        try {
            ContextCompat.startForegroundService(context, intent);
        } catch (Exception e) {
            Log.e(TAG, "Can't start media notification: %s", e.getMessage());
        }
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, MediaNotificationService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        setMediaNotificationProvider(new DefaultMediaNotificationProvider.Builder(this)
                .setNotificationId(NOTIFICATION_ID)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.app_name)
                .build());
    }

    @Nullable
    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mSession;
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        mStartId = startId;
        if (intent == null || !ACTION_UPDATE.equals(intent.getAction())) {
            return super.onStartCommand(intent, flags, startId);
        }
        MediaSessionCompat.Token token =
                IntentCompat.getParcelableExtra(intent, EXTRA_SESSION_TOKEN, MediaSessionCompat.Token.class);
        if (token == null) {
            stopSelfCompletely();
            return START_NOT_STICKY;
        }
        if (token.equals(mSourceToken)) {
            return START_NOT_STICKY;
        }
        try {
            releaseConnection();
            // Promote immediately while the asynchronous controller connection is being established.
            ServiceCompat.startForeground(this, NOTIFICATION_ID, connectingNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            connect(token);
        } catch (Exception e) {
            Log.e(TAG, "Can't connect media session: %s", e.getMessage());
            stopSelfCompletely();
        }
        return START_NOT_STICKY;
    }

    private void connect(MediaSessionCompat.Token token) {
        mSourceToken = token;
        mControllerFuture = Futures.transformAsync(
                SessionToken.createSessionToken(this, token),
                sessionToken -> {
                    if (!getPackageName().equals(sessionToken.getPackageName())) {
                        throw new SecurityException("The media source must belong to this app");
                    }
                    return new MediaController.Builder(this, sessionToken)
                        .setListener(new MediaController.Listener() {
                            @Override
                            public void onDisconnected(MediaController controller) {
                                if (controller == mController) {
                                    stopSelfCompletely();
                                }
                            }
                        })
                        .buildAsync();
                },
                ContextCompat.getMainExecutor(this));
        Futures.addCallback(mControllerFuture, new FutureCallback<MediaController>() {
            @Override
            public void onSuccess(MediaController controller) {
                if (!token.equals(mSourceToken)) {
                    controller.release();
                    return;
                }
                try {
                    mController = controller;
                    // This service owns the public session; the source session is private and inactive.
                    mPlayer = new ForwardingPlayer(controller) {
                        @Override
                        public boolean canAdvertiseSession() {
                            return true;
                        }
                    };
                    mSession = new MediaSession.Builder(MediaNotificationService.this, mPlayer)
                            .setId("mobile")
                            .setSessionActivity(createActivityIntent(MediaNotificationService.this))
                            .build();
                    addSession(mSession);
                } catch (Exception e) {
                    android.util.Log.e(TAG, "Can't publish media session", e);
                    stopSelfCompletely();
                }
            }

            @Override
            public void onFailure(Throwable error) {
                if (token.equals(mSourceToken)) {
                    Log.e(TAG, "Media controller connection failed: %s", error.getMessage());
                    stopSelfCompletely();
                }
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private Notification connectingNotification() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(getApplicationInfo().icon)
                .setContentTitle(getString(R.string.app_name))
                .setContentIntent(createActivityIntent(this))
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setOngoing(true)
                .build();
    }

    private void releaseConnection() {
        mSourceToken = null;
        mController = null;
        if (mSession != null) {
            removeSession(mSession);
            mSession.release();
            mSession = null;
        }
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
            mControllerFuture = null;
        } else if (mControllerFuture != null) {
            MediaController.releaseFuture(mControllerFuture);
            mControllerFuture = null;
        }
    }

    private void stopSelfCompletely() {
        releaseConnection();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf(mStartId);
    }

    @Override
    public void onDestroy() {
        releaseConnection();
        stopForeground(STOP_FOREGROUND_REMOVE);
        getSystemService(NotificationManager.class).cancel(NOTIFICATION_ID);
        super.onDestroy();
    }

    public static PendingIntent createActivityIntent(Context context) {
        Intent intent = new Intent(context, MobilePlaybackActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
