package com.liskovsoft.smartyoutubetv2.mobile.ui.base;

import android.app.Activity;
import android.app.Application;
import android.view.View;
import android.widget.FrameLayout;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, manifest = Config.NONE, application = Application.class)
public class SystemBarInsetsTest {
    @Test
    public void keyboardReplacesNavigationInsetAndDoesNotAccumulatePadding() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setContentView(new FrameLayout(activity));
        View content = activity.findViewById(android.R.id.content);
        content.setPadding(2, 3, 4, 5);
        SystemBarInsets.apply(activity);

        ViewCompat.dispatchApplyWindowInsets(content, insets(300));
        assertEquals(305, content.getPaddingBottom());
        assertEquals(27, content.getPaddingTop());

        ViewCompat.dispatchApplyWindowInsets(content, insets(0));
        assertEquals(53, content.getPaddingBottom());
        assertEquals(2, content.getPaddingLeft());
        assertEquals(4, content.getPaddingRight());
    }

    private WindowInsetsCompat insets(int keyboardHeight) {
        return new WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 24, 0, 48))
                .setVisible(WindowInsetsCompat.Type.systemBars(), true)
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, keyboardHeight))
                .setVisible(WindowInsetsCompat.Type.ime(), keyboardHeight > 0)
                .build();
    }
}
