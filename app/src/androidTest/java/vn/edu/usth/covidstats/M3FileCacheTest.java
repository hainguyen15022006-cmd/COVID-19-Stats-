package vn.edu.usth.covidstats;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.ContextWrapper;
import android.os.Looper;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.volley.Network;
import com.android.volley.NetworkResponse;
import com.android.volley.NoConnectionError;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.NoCache;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import vn.edu.usth.covidstats.data.CacheManager;
import vn.edu.usth.covidstats.data.Callback;
import vn.edu.usth.covidstats.data.NetworkRepository;
import vn.edu.usth.covidstats.model.GlobalStats;

/** Ghép Repository voi CacheManager that; file rieng, khong sua cache app cua nguoi dung. */
@RunWith(AndroidJUnit4.class)
public class M3FileCacheTest {
    private Context context;
    private File directory;
    private RequestQueue queue;
    private AtomicInteger requests;

    @Before public void setUp() {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        directory = new File(target.getCacheDir(), "m3-integration-" + UUID.randomUUID());
        context = new ContextWrapper(target) {
            @Override public Context getApplicationContext() { return this; }
            @Override public File getFilesDir() { return directory; }
        };
        requests = new AtomicInteger();
        Network network = request -> {
            requests.incrementAndGet();
            return new NetworkResponse(200, "{\"cases\":3000000000,\"deaths\":12}"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    false, 0, Collections.emptyList());
        };
        queue = new RequestQueue(new NoCache(), network, 1);
        queue.start();
    }

    @After public void tearDown() {
        queue.stop();
        delete(directory);
    }

    @Test public void newRepositoryAndCacheManagerRecoverSameSavedJson() throws Exception {
        NetworkRepository first = new NetworkRepository(context, queue, new CacheManager(context));
        Result original = fetch(first, true);
        assertFalse(original.fromCache);
        CacheManager.Entry saved = new CacheManager(context).read("/all");
        assertNotNull(saved);
        long timestamp = saved.savedAtMillis;
        RequestQueue offlineQueue = new RequestQueue(new NoCache(), request -> {
            requests.incrementAndGet();
            throw new NoConnectionError();
        }, 1);
        offlineQueue.start();
        try {
            NetworkRepository second = new NetworkRepository(context, offlineQueue,
                    new CacheManager(context));
            Result restored = fetch(second, false);
            assertTrue(restored.fromCache);
            assertEquals(3_000_000_000L, restored.data.cases);
            assertEquals(timestamp, new CacheManager(context).read("/all").savedAtMillis);
            assertEquals(1, requests.get());
        } finally { offlineQueue.stop(); }
    }

    @Test public void invalidPayloadIsReplacedOnlyAfterValidNetworkResponse() throws Exception {
        CacheManager cache = new CacheManager(context);
        cache.write("/all", "not JSON");
        Result result = fetch(new NetworkRepository(context, queue, cache), false);
        assertFalse(result.fromCache);
        assertEquals(3_000_000_000L, result.data.cases);
        assertTrue(cache.read("/all").json.startsWith("{"));
    }

    private Result fetch(NetworkRepository repository, boolean force) throws Exception {
        Result result = new Result();
        CountDownLatch done = new CountDownLatch(1);
        repository.getGlobal(force, new Callback<GlobalStats>() {
            @Override public void onSuccess(GlobalStats data, boolean fromCache) {
                result.data = data;
                result.fromCache = fromCache;
                result.onMain = Looper.myLooper() == Looper.getMainLooper();
                done.countDown();
            }
            @Override public void onError(String message) { result.error = message; done.countDown(); }
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertNull(result.error);
        assertTrue(result.onMain);
        return result;
    }

    private static final class Result {
        GlobalStats data;
        boolean fromCache;
        boolean onMain;
        String error;
    }

    private static void delete(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        if (file.exists() && !file.delete()) throw new AssertionError("Cannot delete test file");
    }
}
