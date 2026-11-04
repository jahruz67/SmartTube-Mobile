package com.liskovsoft.smartyoutubetv2.mobile.ui.main;

import android.app.Activity;
import android.app.Application;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewTreeObserver;

import com.liskovsoft.smartyoutubetv2.common.app.views.AddDeviceView;
import com.liskovsoft.smartyoutubetv2.common.app.views.AppDialogView;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelUploadsView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelView;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SearchView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SplashView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.app.views.WebBrowserView;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerConstants;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AccountSelectionPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.service.SidebarService;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.GeneralSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.mobile.ui.browse.MobileBrowseActivity;
import com.liskovsoft.smartyoutubetv2.mobile.ui.browse.MobileSettingsActivity;
import com.liskovsoft.smartyoutubetv2.mobile.ui.channel.MobileChannelActivity;
import com.liskovsoft.smartyoutubetv2.mobile.ui.channeluploads.MobileChannelUploadsActivity;
import com.liskovsoft.smartyoutubetv2.mobile.ui.dialogs.MobileAppDialogActivity;
import com.liskovsoft.smartyoutubetv2.mobile.ui.playback.PlaybackBackdrop;
import com.liskovsoft.smartyoutubetv2.mobile.notifications.NotificationPollWorker;
import com.liskovsoft.smartyoutubetv2.mobile.stats.StatsReporter;
import com.liskovsoft.smartyoutubetv2.mobile.ui.playback.MobilePlaybackActivity;
import com.liskovsoft.smartyoutubetv2.mobile.settings.MobileNotificationPrefs;
import com.liskovsoft.smartyoutubetv2.mobile.settings.MobilePlayerStylePrefs;
import com.liskovsoft.smartyoutubetv2.mobile.settings.MobileThemePrefs;
import com.liskovsoft.smartyoutubetv2.mobile.ui.search.MobileSearchActivity;
import com.liskovsoft.smartyoutubetv2.mobile.ui.signin.MobileSignInActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.adddevice.AddDeviceActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.main.MainApplication;
import com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.webbrowser.WebBrowserActivity;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;

/**
 * Application class for the phone (stmobile) flavor.
 *
 * Overrides view routing so the phone build uses its own native portrait screens
 * ({@link MobileBrowseActivity}, {@link MobileSearchActivity}, ...) instead of the TV
 * Leanback ones. The remaining screens are reused from the TV code unchanged (later
 * phases will replace them in turn).
 */
public class MobileApplication extends MainApplication {
    private boolean mFirstFrameDrawn;
    private boolean mNotificationReconciliationQueued;
    // Held strongly because AppPrefs stores weak listeners. SidebarService reloads first.
    private final AppPrefs.ProfileChangeListener mApplyNavigationDefaults = () -> {
        applyNavigationDefaults();
        // Account switched: forget the previous account's seen uploads so the new account
        // seeds fresh (no cross-account false alerts) and re-evaluate the poll schedule.
        MobileNotificationPrefs.clearSeenIds(this);
        if (mFirstFrameDrawn) {
            NotificationPollWorker.schedule(this);
        }
    };

    @Override
    public void onCreate() {
        // Apply the persisted Theme choice (System / Light / Dark) before any activity
        // is created so the first frame uses the correct values/ vs values-night/ tokens
        // - avoids a recreate-flicker on cold start.
        MobileThemePrefs.apply(this);
        // YouTube's notifications inbox endpoint is dead (HTTP 400 -> "Can't load content"), so the
        // Notifications tab shows the uploads the upload-notifications poll has picked up instead.
        // Set before super.onCreate() so it's in place before BrowsePresenter is ever created.
        BrowsePresenter.sNotificationsSource = () -> MobileNotificationPrefs.getHistory(this);
        // Save crashes locally for the opt-in crash reports. Installed BEFORE super.onCreate() so
        // upstream's handler (MainApplication.setupGlobalExceptionHandler) wraps this one and the
        // exceptions it deliberately swallows are never recorded as crashes.
        StatsReporter.installCrashHandler(this);
        super.onCreate();

        // Opt-in anonymous usage heartbeat: at most once a day, whenever any screen starts.
        // No-op unless the user opted in (see StatsReporter).
        registerActivityLifecycleCallbacks(new ActivityStartedCallback() {
            @Override
            public void onActivityStarted(Activity activity) {
                StatsReporter.onForeground(activity);
            }

            @Override
            public void onActivityResumed(Activity activity) {
                reconcileNotificationsAfterFirstFrame(activity);
            }

            @Override
            public void onActivityPaused(Activity activity) {
                PlaybackBackdrop.rememberPage(activity);
                if (!(activity instanceof MobileSettingsActivity) && !(activity instanceof MobileAppDialogActivity)) {
                    return;
                }
                // These stores normally wait 5–10 seconds. Request a save when settings
                // closes or goes into the background, before a quick exit can lose choices.
                GeneralData.instance(MobileApplication.this).persistNow();
                MainUIData.instance(MobileApplication.this).persistNow();
                PlayerData.instance(MobileApplication.this).persistNow();
                PlayerTweaksData.instance(MobileApplication.this).persistNow();
                MediaServiceData.instance().persistNow();
            }
        });

        applyNavigationDefaults();
        AppPrefs.instance(this).addListener(mApplyNavigationDefaults);
        hideShortsOnce();

        // Reconcile both enabled and disabled polling after the first activity has drawn.
        // WorkManager initialization must not delay the first visible frame.

        // Must run before the *Once() defaults below: it tells a fresh install from an existing
        // one by whether those one-time flags have been written yet.
        choosePlayerStyleOnce();
        hideScreenDimmingButtonOnce();
        keepVideoSizeUnderDialogsOnce();
        defaultBackgroundPlaybackOnce();

        // Ticking a context-menu item in Settings > General just enables it (in its default slot)
        // instead of popping a "Position of …" picker after every tick — a TV-remote flow that
        // reads as broken on a touchscreen. Reordering stays under "Context menu sorting".
        GeneralSettingsPresenter.sAskMenuItemPosition = false;

        MediaServiceManager.instance().loadAccounts(accounts -> {
            if (accounts != null && accounts.size() == 1
                    && MediaServiceManager.instance().getSelectedAccount() == null) {
                AccountSelectionPresenter.instance(this).selectAccount(accounts.get(0));
            }
        });
    }

    private void reconcileNotificationsAfterFirstFrame(Activity activity) {
        if (mFirstFrameDrawn || mNotificationReconciliationQueued) {
            return;
        }
        View decor = activity.getWindow().getDecorView();
        decor.getViewTreeObserver().addOnDrawListener(new ViewTreeObserver.OnDrawListener() {
            @Override
            public void onDraw() {
                boolean reconcile = !mNotificationReconciliationQueued;
                mNotificationReconciliationQueued = true;
                // Posting from onDraw runs after this traversal; removing the listener
                // inside onDraw itself is prohibited by ViewTreeObserver.
                decor.post(() -> {
                    if (decor.getViewTreeObserver().isAlive()) {
                        decor.getViewTreeObserver().removeOnDrawListener(this);
                    }
                    if (reconcile) {
                        mFirstFrameDrawn = true;
                        NotificationPollWorker.schedule(MobileApplication.this);
                    }
                });
            }
        });
    }

    private void applyNavigationDefaults() {
        SidebarService.instance(this).applyDefaultSectionsOnce(1,
                MediaGroup.TYPE_NOTIFICATIONS, MediaGroup.TYPE_HOME, MediaGroup.TYPE_CHANNEL_UPLOADS,
                MediaGroup.TYPE_SUBSCRIPTIONS, MediaGroup.TYPE_HISTORY, MediaGroup.TYPE_SETTINGS);
    }

    private void hideShortsOnce() {
        AppPrefs prefs = AppPrefs.instance(this);
        if (prefs.getData("mobile_shorts_defaults_v1") == null) {
            MediaServiceData.instance().setContentHidden(MediaServiceData.CONTENT_SHORTS_ALL, true);
            MediaServiceData.instance().persistNow();
            prefs.setData("mobile_shorts_defaults_v1", "true");
        }
    }

    /**
     * Default phones to background audio playback. Upstream defaults to BACKGROUND_MODE_DEFAULT,
     * which releases the player when the screen turns off — so locking the phone stops playback
     * and no lockscreen media controls appear. Phone users expect audio to keep playing, so we set
     * BACKGROUND_MODE_SOUND once. Done once so a user who deliberately changes it keeps their
     * choice. stmobile-only; shared common code is untouched, keeping the fork upstream-mergeable.
     */
    private void defaultBackgroundPlaybackOnce() {
        SharedPreferences prefs = getSharedPreferences("mobile_player_prefs", MODE_PRIVATE);
        if (!prefs.getBoolean("background_sound_defaulted", false)) {
            PlayerData.instance(this).setBackgroundMode(PlayerConstants.BACKGROUND_MODE_SOUND);
            prefs.edit().putBoolean("background_sound_defaulted", true).apply();
        }
    }

    /**
     * Hide the Screen Dimming player button on phones. It blacks the screen for audio-only /
     * battery saving with a TV remote, but on a phone it reads as a broken control (it just turns
     * the screen black) — and you can simply lock the phone instead. Done once so a user who
     * deliberately re-enables it in player settings keeps it. stmobile-only; shared common code
     * (the button is added by the TV {@code VideoPlayerGlue}) is untouched, keeping the fork
     * upstream-mergeable.
     */
    private void hideScreenDimmingButtonOnce() {
        SharedPreferences prefs = getSharedPreferences("mobile_player_prefs", MODE_PRIVATE);
        if (!prefs.getBoolean("screen_dimming_hidden", false)) {
            PlayerTweaksData.instance(this).setPlayerButtonDisabled(PlayerTweaksData.PLAYER_BUTTON_SCREEN_DIMMING);
            prefs.edit().putBoolean("screen_dimming_hidden", true).apply();
        }
    }

    /**
     * Pick the default phone player style once (#46). Fresh installs get the Modern style (phone-style
     * controls familiar to phone users); existing users keep the Classic SmartTube-style controls
     * they already know. An existing install is recognised by the one-time flags earlier versions
     * wrote to "mobile_player_prefs". The choice is stored, so later launches never change it.
     */
    private void choosePlayerStyleOnce() {
        if (MobilePlayerStylePrefs.isSet(this)) {
            return;
        }
        SharedPreferences prefs = getSharedPreferences("mobile_player_prefs", MODE_PRIVATE);
        boolean existingInstall = prefs.contains("background_sound_defaulted")
                || prefs.contains("screen_dimming_hidden");
        MobilePlayerStylePrefs.setStyle(this, existingInstall
                ? MobilePlayerStylePrefs.Style.CLASSIC
                : MobilePlayerStylePrefs.Style.MODERN);
    }

    /**
     * Stop the player shrinking behind settings dialogs (#29). Upstream's "resize video to fit
     * dialog" zooms the video to ~63% and pins it to the left so a TV side-panel dialog doesn't
     * cover it; phone dialogs are bottom sheets or full screen, so in landscape it just leaves a
     * small video in the corner. Turning on "Don't resize video to fit dialog" skips it. Done once
     * so a user who deliberately turns it back off keeps their choice. stmobile-only; shared common
     * code is untouched, keeping the fork upstream-mergeable.
     */
    private void keepVideoSizeUnderDialogsOnce() {
        SharedPreferences prefs = getSharedPreferences("mobile_player_prefs", MODE_PRIVATE);
        if (!prefs.getBoolean("dialog_resize_disabled", false)) {
            PlayerTweaksData.instance(this).setDontResizeVideoToFitDialogEnabled(true);
            prefs.edit().putBoolean("dialog_resize_disabled", true).apply();
        }
    }

    @Override
    protected void setupViewManager() {
        ViewManager viewManager = ViewManager.instance(this);

        viewManager.setActivityReorderingEnabled(true);
        viewManager.setRoot(MobileBrowseActivity.class);
        viewManager.register(SplashView.class, SplashActivity.class); // root activity, no parent
        viewManager.register(BrowseView.class, MobileBrowseActivity.class); // phone Home
        viewManager.register(PlaybackView.class, MobilePlaybackActivity.class, MobileBrowseActivity.class);
        viewManager.register(AppDialogView.class, MobileAppDialogActivity.class, MobilePlaybackActivity.class);
        viewManager.register(SearchView.class, MobileSearchActivity.class, MobileBrowseActivity.class);
        viewManager.register(SignInView.class, MobileSignInActivity.class, MobileBrowseActivity.class);
        viewManager.register(AddDeviceView.class, AddDeviceActivity.class, MobileBrowseActivity.class);
        viewManager.register(ChannelView.class, MobileChannelActivity.class, MobileBrowseActivity.class);
        viewManager.register(ChannelUploadsView.class, MobileChannelUploadsActivity.class, MobileBrowseActivity.class);
        viewManager.register(WebBrowserView.class, WebBrowserActivity.class, MobileBrowseActivity.class);
    }

    /** Lifecycle callbacks with empty defaults, so callers override only what they need. */
    private abstract static class ActivityStartedCallback implements Application.ActivityLifecycleCallbacks {
        @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
        @Override public void onActivityResumed(Activity activity) {}
        @Override public void onActivityPaused(Activity activity) {}
        @Override public void onActivityStopped(Activity activity) {}
        @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
        @Override public void onActivityDestroyed(Activity activity) {}
    }
}
