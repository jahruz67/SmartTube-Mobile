package dev.smarttubemobile.tests;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.liskovsoft.smartyoutubetv2.tv.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Resource smoke test for the mobile flavor; does not depend on live YouTube services. */
@RunWith(AndroidJUnit4.class)
public class MobileBottomNavigationInstrumentationTest {
    @Test
    public void fiveTabsExistAndHaveTouchAndAccessibilityTargets() {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        View root = LayoutInflater.from(target).inflate(R.layout.mobile_browse_fragment, null);
        LinearLayout bar = root.findViewById(R.id.mobile_bottom_navigation);
        assertNotNull("Navigation bar must exist", bar);
        assertEquals("Five navigation destinations", 5, bar.getChildCount());
        int[] ids = {R.id.bottom_home, R.id.bottom_shorts, R.id.bottom_subscriptions,
                     R.id.bottom_library, R.id.bottom_search};
        for (int id : ids) {
            View tab = root.findViewById(id);
            assertNotNull(tab);
            assertTrue("Tab must be clickable", tab.isClickable());
            assertTrue("Tab must be focusable", tab.isFocusable());
            assertNotNull("Accessible label", tab.getContentDescription());
            assertTrue("Accessible label must not be empty", tab.getContentDescription().length() > 0);
            ViewGroup.LayoutParams params = tab.getLayoutParams();
            assertEquals("Tab fills height", ViewGroup.LayoutParams.MATCH_PARENT, params.height);
        }
    }
}
