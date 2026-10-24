package vn.edu.usth.covidstats;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.widget.TextView;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import vn.edu.usth.covidstats.data.Callback;
import vn.edu.usth.covidstats.data.WatchlistStore;
import vn.edu.usth.covidstats.debug.StoragePreviewActivity;

/** Kiem tra Fragment that, khong thay no bang giao dien gia trong host debug. */
@RunWith(AndroidJUnit4.class)
public class WatchlistPreviewTest {
    @Test
    public void emptyAddRecreateSwipeAndRefresh() throws Exception {
        WatchlistStore store = WatchlistStore.getInstance(
                InstrumentationRegistry.getInstrumentation().getTargetContext());
        Set<String> original = await(store::getCodes);
        try {
            for (String code : original) await(cb -> store.setStarred(code, false, cb));
            try (ActivityScenario<StoragePreviewActivity> screen = ActivityScenario.launch(StoragePreviewActivity.class)) {
                waitFor(screen, 0, true);
                click(screen, R.id.storage_preview_vn);
                waitFor(screen, 1, false);
                click(screen, R.id.storage_preview_us);
                waitFor(screen, 2, false);
                screen.recreate();
                waitFor(screen, 2, false);
                swipeVietnam(screen);
                waitFor(screen, 1, false);
                Set<String> codes = await(store::getCodes);
                assertTrue(codes.contains("US"));
                assertTrue(!codes.contains("VN"));
                click(screen, R.id.storage_preview_refresh);
                waitFor(screen, 1, false);
            }
        } finally {
            for (String code : await(store::getCodes)) await(cb -> store.setStarred(code, false, cb));
            for (String code : original) await(cb -> store.setStarred(code, true, cb));
        }
    }

    private void click(ActivityScenario<StoragePreviewActivity> screen, int id) {
        screen.onActivity(activity -> activity.findViewById(id).performClick());
    }

    private void swipeVietnam(ActivityScenario<StoragePreviewActivity> screen) {
        float[] points = new float[3];
        screen.onActivity(activity -> {
            RecyclerView list = activity.findViewById(R.id.watchlist_list);
            for (int i = 0; i < list.getChildCount(); i++) {
                View row = list.getChildAt(i);
                TextView name = row.findViewById(R.id.country_name);
                if (name != null && "Vietnam".contentEquals(name.getText())) {
                    int[] location = new int[2];
                    row.getLocationOnScreen(location);
                    points[0] = location[0] + row.getWidth() * 0.9f;
                    points[1] = location[0] + row.getWidth() * 0.1f;
                    points[2] = location[1] + row.getHeight() * 0.5f;
                }
            }
        });
        assertTrue("Vietnam row must be laid out", points[0] > points[1]);
        long down = SystemClock.uptimeMillis();
        inject(down, MotionEvent.ACTION_DOWN, points[0], points[2]);
        for (int step = 1; step <= 12; step++) {
            SystemClock.sleep(15);
            inject(down, MotionEvent.ACTION_MOVE,
                    points[0] + (points[1] - points[0]) * step / 12f, points[2]);
        }
        inject(down, MotionEvent.ACTION_UP, points[1], points[2]);
    }

    private void inject(long down, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0);
        try {
            InstrumentationRegistry.getInstrumentation().sendPointerSync(event);
        } finally {
            event.recycle();
        }
    }

    private void waitFor(ActivityScenario<StoragePreviewActivity> screen, int count, boolean empty)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        AtomicBoolean ready = new AtomicBoolean();
        while (System.nanoTime() < deadline) {
            screen.onActivity(activity -> {
                RecyclerView list = activity.findViewById(R.id.watchlist_list);
                TextView status = activity.findViewById(R.id.watchlist_status);
                boolean hasEmptyMessage = status.getText().toString()
                        .equals(activity.getString(R.string.watchlist_empty));
                ready.set(list.getAdapter().getItemCount() == count
                        && (count == 0 || list.getChildCount() == count)
                        && (!empty || hasEmptyMessage));
            });
            if (ready.get()) return;
            Thread.sleep(50);
        }
        assertTrue("Watchlist did not reach expected state, rows=" + count, ready.get());
    }

    private Set<String> await(Invocation invocation) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Set<String>> result = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();
        invocation.call(new Callback<Set<String>>() {
            @Override public void onSuccess(Set<String> data, boolean fromCache) {
                result.set(data);
                done.countDown();
            }
            @Override public void onError(String message) {
                error.set(message);
                done.countDown();
            }
        });
        assertTrue("Store callback timed out", done.await(15, TimeUnit.SECONDS));
        assertNull(error.get());
        return result.get();
    }

    private interface Invocation { void call(Callback<Set<String>> cb); }
}
