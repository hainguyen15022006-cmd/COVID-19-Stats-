package vn.edu.usth.covidstats;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.view.MenuItem;

import androidx.appcompat.widget.Toolbar;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.viewpager.widget.ViewPager;

import org.junit.Test;
import org.junit.runner.RunWith;

import vn.edu.usth.covidstats.ui.MainPagerAdapter;

/** Verifies F05: the app-bar Refresh action serves whichever main tab is visible. */
@RunWith(AndroidJUnit4.class)
public final class MainToolbarRefreshTest {
    @Test
    public void refreshIsVisibleAndHandledOnAllMainTabs() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            for (int page = MainPagerAdapter.PAGE_GLOBAL;
                 page <= MainPagerAdapter.PAGE_WATCHLIST; page++) {
                int selectedPage = page;
                scenario.onActivity(activity -> {
                    ViewPager pager = activity.findViewById(R.id.main_pager);
                    pager.setCurrentItem(selectedPage, false);
                    activity.invalidateOptionsMenu();
                });
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                scenario.onActivity(activity -> {
                    Toolbar toolbar = activity.findViewById(R.id.main_toolbar);
                    MenuItem refresh = toolbar.getMenu().findItem(R.id.action_refresh);
                    assertNotNull(refresh);
                    assertTrue("Refresh must be visible on page " + selectedPage,
                            refresh.isVisible());
                    assertTrue("Refresh must be handled on page " + selectedPage,
                            toolbar.getMenu().performIdentifierAction(R.id.action_refresh, 0));
                });
            }
        }
    }
}
