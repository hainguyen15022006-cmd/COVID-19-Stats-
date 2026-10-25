package vn.edu.usth.covidstats;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;
import android.app.LocaleManager;
import android.graphics.Bitmap;
import android.os.LocaleList;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.filters.SdkSuppress;
import androidx.viewpager.widget.ViewPager;

import org.json.JSONArray;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.android.volley.RequestQueue;

import vn.edu.usth.covidstats.data.Callback;
import vn.edu.usth.covidstats.data.WatchlistStore;
import vn.edu.usth.covidstats.ui.CountryDetailActivity;
import vn.edu.usth.covidstats.ui.MainPagerAdapter;
import vn.edu.usth.covidstats.util.NetworkUtil;

/** Run seedOnline, force-stop + disable network, readOfflineAndSwipe, then restoreOriginal.
 * Each phase is a separate instrumentation invocation, so no in-memory store survives.
 * Uses production MainActivity, detail button, repository, cache and watchlist tab.
 */
@RunWith(AndroidJUnit4.class)
public class WatchlistIntegrationTest {
    @Rule public final TestName testName = new TestName();
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final WatchlistStore store = WatchlistStore.getInstance(context);

    @Before public void requireExplicitPhase() {
        // This multi-process scenario is opt-in; ordinary suite runs must not
        // require airplane mode or leave temporary VN/US stars behind.
        Assume.assumeTrue(testName.getMethodName().equals(
                InstrumentationRegistry.getArguments().getString("storagePhase")));
    }

    @Test public void seedOnline() throws Exception {
        assertTrue("Seed requires validated Internet", NetworkUtil.isOnline(context));
        File backup = backup();
        // Keep the first backup if a failed run is retried.
        if (!backup.exists()) {
            Files.write(backup.toPath(), new JSONArray(await(store::getCodes)).toString()
                    .getBytes(StandardCharsets.UTF_8));
        }
        replaceCodes(new HashSet<>());
        try (ActivityScenario<MainActivity> main = ActivityScenario.launch(MainActivity.class)) {
            selectWatchlist(main);
            waitRows(main, 0);
            main.onActivity(activity -> assertEquals(activity.getString(R.string.watchlist_empty),
                    ((TextView) activity.findViewById(R.id.watchlist_status)).getText().toString()));
            screenshot("01-empty");
        }
        starFromDetail("VN");
        try (ActivityScenario<MainActivity> main = ActivityScenario.launch(MainActivity.class)) {
            selectWatchlist(main);
            waitRows(main, 1);
        }
        starFromDetail("US");
        try (ActivityScenario<MainActivity> main = ActivityScenario.launch(MainActivity.class)) {
            selectWatchlist(main);
            waitRows(main, 2);
            screenshot("02-detail-stars-in-real-tab");
            main.recreate();
            waitRows(main, 2);
            main.onActivity(activity -> {
                assertEquals(MainPagerAdapter.PAGE_WATCHLIST,
                        ((ViewPager) activity.findViewById(R.id.main_pager)).getCurrentItem());
            });
            refreshFromToolbar(main);
            waitRows(main, 2);
        }
        Log.i("StorageEvidence", "SEED pid=" + Process.myPid() + " codes=" + await(store::getCodes));
    }

    @Test public void readOfflineAndSwipe() throws Exception {
        assertTrue("Run seedOnline first", backup().exists());
        assertFalse("Disable Wi-Fi/data before this phase", NetworkUtil.isOnline(context));
        Set<String> persisted = await(store::getCodes);
        assertTrue(persisted.contains("VN"));
        assertTrue(persisted.contains("US"));
        long started = SystemClock.elapsedRealtime();
        try (ActivityScenario<MainActivity> main = ActivityScenario.launch(MainActivity.class)) {
            selectWatchlist(main);
            waitRows(main, 2);
            long elapsed = SystemClock.elapsedRealtime() - started;
            main.onActivity(activity -> assertEquals(View.VISIBLE,
                    activity.findViewById(R.id.watchlist_offline).getVisibility()));
            screenshot("03-after-force-stop-offline");
            main.recreate();
            waitRows(main, 2);
            swipeVietnam(main);
            waitRows(main, 1);
            Set<String> remaining = await(store::getCodes);
            assertFalse(remaining.contains("VN"));
            assertTrue(remaining.contains("US"));
            refreshFromToolbar(main);
            waitRows(main, 1);
            screenshot("04-swiped-vn-refreshed-offline");
            Log.i("StorageEvidence", "OFFLINE pid=" + Process.myPid()
                    + " visibleAfterLaunchMs=" + elapsed + " remaining=" + remaining);
        }
    }

    @Test public void restoreOriginal() throws Exception {
        if (!backup().exists()) return;
        JSONArray saved = new JSONArray(new String(Files.readAllBytes(backup().toPath()), StandardCharsets.UTF_8));
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < saved.length(); i++) codes.add(saved.getString(i));
        replaceCodes(codes);
        assertEquals(codes, await(store::getCodes));
        assertTrue(backup().delete());
        Log.i("StorageEvidence", "RESTORED original=" + codes);
    }

    @Test public void recoverOnline() throws Exception {
        assertTrue("Enable Internet before recovery", NetworkUtil.isOnline(context));
        try (ActivityScenario<MainActivity> main = ActivityScenario.launch(MainActivity.class)) {
            selectWatchlist(main);
            waitRows(main, 1);
            refreshFromToolbar(main);
            waitRows(main, 1);
            main.onActivity(activity -> assertEquals(View.GONE,
                    activity.findViewById(R.id.watchlist_offline).getVisibility()));
            screenshot("05-back-online");
        }
        Log.i("StorageEvidence", "RECOVERED pid=" + Process.myPid() + " codes=" + await(store::getCodes));
    }

    @Test @SdkSuppress(minSdkVersion = 33)
    public void pullRefreshAndVietnamese() throws Exception {
        assertTrue(NetworkUtil.isOnline(context));
        int count = await(store::getCodes).size();
        assertTrue("Keep at least one followed country for the gesture check", count > 0);
        RequestQueue queue = ((CovidApp) context.getApplicationContext()).getRequestQueue();
        CountDownLatch requested = new CountDownLatch(1);
        RequestQueue.RequestFinishedListener<Object> listener = request -> {
            if (request.getUrl().endsWith("/countries?sort=cases")) requested.countDown();
        };
        try (ActivityScenario<MainActivity> main = ActivityScenario.launch(MainActivity.class)) {
            selectWatchlist(main);
            waitRows(main, count);
            queue.addRequestFinishedListener(listener);
            try {
                float[] points = new float[3];
                main.onActivity(activity -> {
                    RecyclerView list = activity.findViewById(R.id.watchlist_list);
                    View row = list.getChildAt(0);
                    int[] location = new int[2];
                    row.getLocationOnScreen(location);
                    points[0] = location[0] + row.getWidth() * .5f;
                    points[1] = location[1] + row.getHeight() * .5f;
                    points[2] = points[1] + list.getHeight() * .45f;
                });
                long down = SystemClock.uptimeMillis();
                inject(down, MotionEvent.ACTION_DOWN, points[0], points[1]);
                for (int step = 1; step <= 20; step++) {
                    SystemClock.sleep(20);
                    inject(down, MotionEvent.ACTION_MOVE, points[0],
                            points[1] + (points[2] - points[1]) * step / 20f);
                }
                inject(down, MotionEvent.ACTION_UP, points[0], points[2]);
                assertTrue("Vertical pull must send a countries request", requested.await(20, TimeUnit.SECONDS));
                waitRows(main, count);
            } finally { queue.removeRequestFinishedListener(listener); }
        }
        LocaleManager locales = context.getSystemService(LocaleManager.class);
        LocaleList original = locales.getApplicationLocales();
        try {
            locales.setApplicationLocales(LocaleList.forLanguageTags("vi"));
            try (ActivityScenario<MainActivity> main = ActivityScenario.launch(MainActivity.class)) {
                selectWatchlist(main);
                waitRows(main, count);
                main.onActivity(activity -> {
                    assertEquals("Theo dõi", activity.getString(R.string.watchlist_title));
                    assertEquals("Vuốt quốc gia sang trái hoặc phải để bỏ theo dõi.",
                            ((TextView) activity.findViewById(R.id.watchlist_status)).getText().toString());
                });
                screenshot("06-watchlist-vi");
            }
        } finally { locales.setApplicationLocales(original); }
        Log.i("StorageEvidence", "PULL_REFRESH_AND_VI passed; locale restored=" + original.toLanguageTags());
    }

    private void starFromDetail(String code) throws Exception {
        Intent intent = new Intent(context, CountryDetailActivity.class)
                .putExtra(CountryDetailActivity.EXTRA_COUNTRY_ISO2, code);
        try (ActivityScenario<CountryDetailActivity> detail = ActivityScenario.launch(intent)) {
            AtomicBoolean ready = new AtomicBoolean();
            long deadline = SystemClock.elapsedRealtime() + 30000;
            while (SystemClock.elapsedRealtime() < deadline) {
                detail.onActivity(activity -> ready.set(activity.findViewById(R.id.detail_watchlist_button).isEnabled()));
                if (ready.get()) break;
                Thread.sleep(50);
            }
            assertTrue("Detail star button must become enabled", ready.get());
            detail.onActivity(activity -> activity.findViewById(R.id.detail_watchlist_button).performClick());
            assertTrue("Detail click must persist " + code, await(store::getCodes).contains(code));
        }
    }

    private void selectWatchlist(ActivityScenario<MainActivity> main) {
        main.onActivity(activity -> ((ViewPager) activity.findViewById(R.id.main_pager))
                .setCurrentItem(MainPagerAdapter.PAGE_WATCHLIST, false));
    }

    private void refreshFromToolbar(ActivityScenario<MainActivity> main) {
        main.onActivity(activity -> {
            androidx.appcompat.widget.Toolbar toolbar = activity.findViewById(R.id.main_toolbar);
            assertTrue("Refresh must be visible on Watchlist", toolbar.getMenu().findItem(R.id.action_refresh).isVisible());
            assertTrue(toolbar.getMenu().performIdentifierAction(R.id.action_refresh, 0));
        });
    }

    private void waitRows(ActivityScenario<MainActivity> main, int expected) throws Exception {
        AtomicBoolean ready = new AtomicBoolean();
        AtomicReference<String> observed = new AtomicReference<>();
        long deadline = SystemClock.elapsedRealtime() + 45000;
        while (SystemClock.elapsedRealtime() < deadline) {
            main.onActivity(activity -> {
                RecyclerView list = activity.findViewById(R.id.watchlist_list);
                SwipeRefreshLayout refresh = activity.findViewById(R.id.watchlist_refresh);
                ready.set(list != null && list.getAdapter() != null
                        && list.getAdapter().getItemCount() == expected && !refresh.isRefreshing()
                        && (expected == 0 || list.getChildCount() == expected));
                observed.set("page=" + ((ViewPager) activity.findViewById(R.id.main_pager)).getCurrentItem()
                        + " rows=" + (list == null || list.getAdapter() == null ? -1 : list.getAdapter().getItemCount())
                        + " children=" + (list == null ? -1 : list.getChildCount())
                        + " loading=" + (refresh != null && refresh.isRefreshing())
                        + " status=" + ((TextView) activity.findViewById(R.id.watchlist_status)).getText());
            });
            if (ready.get()) return;
            Thread.sleep(50);
        }
        screenshot("failure");
        fail("Real watchlist tab did not settle at " + expected + " rows: " + observed.get());
    }

    private void swipeVietnam(ActivityScenario<MainActivity> main) {
        float[] points = new float[3];
        main.onActivity(activity -> {
            RecyclerView list = activity.findViewById(R.id.watchlist_list);
            for (int i = 0; i < list.getChildCount(); i++) {
                View row = list.getChildAt(i);
                TextView name = row.findViewById(R.id.country_name);
                if (name != null && "Vietnam".contentEquals(name.getText())) {
                    int[] location = new int[2];
                    row.getLocationOnScreen(location);
                    points[0] = location[0] + row.getWidth() * .9f;
                    points[1] = location[0] + row.getWidth() * .1f;
                    points[2] = location[1] + row.getHeight() * .5f;
                }
            }
        });
        assertTrue("Vietnam must be visible", points[0] > points[1]);
        long down = SystemClock.uptimeMillis();
        inject(down, MotionEvent.ACTION_DOWN, points[0], points[2]);
        for (int step = 1; step <= 12; step++) {
            SystemClock.sleep(15);
            inject(down, MotionEvent.ACTION_MOVE, points[0] + (points[1] - points[0]) * step / 12f, points[2]);
        }
        inject(down, MotionEvent.ACTION_UP, points[1], points[2]);
    }

    private void inject(long down, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0);
        try { InstrumentationRegistry.getInstrumentation().sendPointerSync(event); }
        finally { event.recycle(); }
    }

    private void screenshot(String name) throws Exception {
        File directory = new File(context.getExternalFilesDir(null), "storage-evidence");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally { bitmap.recycle(); }
    }

    private File backup() { return new File(context.getFilesDir(), "watchlist-integration-original.json"); }
    private void replaceCodes(Set<String> desired) throws Exception {
        for (String code : await(store::getCodes)) await(cb -> store.setStarred(code, false, cb));
        for (String code : desired) await(cb -> store.setStarred(code, true, cb));
    }
    private interface Invocation { void call(Callback<Set<String>> cb); }
    private Set<String> await(Invocation invocation) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Set<String>> result = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();
        invocation.call(new Callback<Set<String>>() {
            @Override public void onSuccess(Set<String> data, boolean cached) { result.set(data); done.countDown(); }
            @Override public void onError(String message) { error.set(message); done.countDown(); }
        });
        assertTrue("Store callback timed out", done.await(15, TimeUnit.SECONDS));
        assertNull(error.get());
        return result.get();
    }
}
