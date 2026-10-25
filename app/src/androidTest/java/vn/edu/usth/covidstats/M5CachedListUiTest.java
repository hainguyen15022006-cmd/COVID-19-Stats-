package vn.edu.usth.covidstats;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import android.os.SystemClock;
import android.util.Log;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import vn.edu.usth.covidstats.data.CacheManager;

/** T12 UI: cache fixture co 231 dong, giu nguyen cac file cache ban dau sau test. */
@RunWith(AndroidJUnit4.class)
public class M5CachedListUiTest {
    @Test @SdkSuppress(minSdkVersion = 28)
    public void cachedListLoadsAndScrollsWithoutMainIoOrBlockedHeartbeat() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File cacheDir = new File(context.getFilesDir(), "covid_cache");
        Map<String, byte[]> original = new HashMap<>();
        File[] files = cacheDir.listFiles();
        if (files != null) for (File file : files) {
            if (file.isFile()) original.put(file.getName(), Files.readAllBytes(file.toPath()));
        }
        List<String> violations = new CopyOnWriteArrayList<>();
        AtomicReference<StrictMode.ThreadPolicy> previous = new AtomicReference<>();
        try {
            String json;
            try (InputStream input = context.getResources().openRawResource(R.raw.countries);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int n;
                while ((n = input.read(buffer)) != -1) output.write(buffer, 0, n);
                json = new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
            new CacheManager(context).write("/countries?sort=cases", json);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                previous.set(StrictMode.getThreadPolicy());
                StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                        .detectDiskReads().detectDiskWrites().detectNetwork()
                        .penaltyListener(Runnable::run, v -> violations.add(v.toString())).build());
            });
            long start = SystemClock.elapsedRealtime();
            long maxHeartbeat = 0;
            try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class)) {
                waitForRows(screen, 231);
                long loaded = SystemClock.elapsedRealtime() - start;
                for (int position : new int[]{230, 0, 120, 0}) {
                    screen.onActivity(activity -> ((RecyclerView) activity.findViewById(R.id.country_list))
                            .scrollToPosition(position));
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                    CountDownLatch heartbeat = new CountDownLatch(1);
                    long posted = SystemClock.elapsedRealtime();
                    new Handler(Looper.getMainLooper()).post(heartbeat::countDown);
                    assertTrue("Main did not respond", heartbeat.await(2, TimeUnit.SECONDS));
                    maxHeartbeat = Math.max(maxHeartbeat, SystemClock.elapsedRealtime() - posted);
                }
                assertTrue("StrictMode violations: " + violations, violations.isEmpty());
                Log.i("M5UiProof", "cachedFixtureRows=231 activityLoadMs=" + loaded
                        + " maxMainHeartbeatMs=" + maxHeartbeat + " strictModeViolations=" + violations.size());
            }
        } finally {
            if (previous.get() != null) InstrumentationRegistry.getInstrumentation().runOnMainSync(
                    () -> StrictMode.setThreadPolicy(previous.get()));
            File[] current = cacheDir.listFiles();
            if (current != null) for (File file : current) {
                if (file.isFile() && !original.containsKey(file.getName())) {
                    assertTrue("Cannot remove test cache", file.delete());
                }
            }
            for (Map.Entry<String, byte[]> entry : original.entrySet()) {
                Files.write(new File(cacheDir, entry.getKey()).toPath(), entry.getValue());
            }
        }
    }

    private void waitForRows(ActivityScenario<MainActivity> screen, int count) throws Exception {
        long end = SystemClock.elapsedRealtime() + 10_000;
        AtomicBoolean ready = new AtomicBoolean();
        while (SystemClock.elapsedRealtime() < end) {
            screen.onActivity(activity -> {
                RecyclerView list = activity.findViewById(R.id.country_list);
                ready.set(list.getAdapter() != null && list.getAdapter().getItemCount() == count
                        && list.getChildCount() > 0);
            });
            if (ready.get()) return;
            Thread.sleep(25);
        }
        fail("Cached list never displayed 231 rows");
    }
}
