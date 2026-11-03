package com.liskovsoft.smartyoutubetv2.common.misc;

import androidx.activity.OnBackPressedCallback;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class MotherActivityBackNavigationTest {
    @Test
    public void dispatcherRunsScreenExitOnceWithoutRecursing() throws Exception {
        RoutingActivity activity = activityWithBackCallback();

        activity.getOnBackPressedDispatcher().onBackPressed();

        assertEquals(1, activity.backCalls);
        assertEquals(1, activity.finishCalls);
        assertTrue(activity.getOnBackPressedDispatcher().hasEnabledCallbacks());
    }

    @Test
    public void legacyBackCallDoesNotReenterScreenExit() throws Exception {
        RoutingActivity activity = activityWithBackCallback();

        activity.onBackPressed();

        assertEquals(1, activity.backCalls);
        assertEquals(1, activity.finishCalls);
    }

    private RoutingActivity activityWithBackCallback() throws Exception {
        RoutingActivity activity = Robolectric.buildActivity(RoutingActivity.class).get();
        activity.getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                activity.finish();
            }
        });
        Field field = MotherActivity.class.getDeclaredField("mBackNavigation");
        field.setAccessible(true);
        activity.getOnBackPressedDispatcher().addCallback((OnBackPressedCallback) field.get(activity));
        return activity;
    }

    public static class RoutingActivity extends MotherActivity {
        int backCalls;
        int finishCalls;

        @Override
        public void onBackPressed() {
            backCalls++;
            super.onBackPressed();
        }

        @Override
        public void finish() {
            finishCalls++;
        }
    }
}
