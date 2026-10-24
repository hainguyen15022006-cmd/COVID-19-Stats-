package vn.edu.usth.covidstats;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.ContextWrapper;
import android.os.Looper;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.volley.NetworkResponse;
import com.android.volley.NoConnectionError;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.NoCache;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import vn.edu.usth.covidstats.data.CacheManager;
import vn.edu.usth.covidstats.data.Callback;
import vn.edu.usth.covidstats.data.NetworkRepository;
import vn.edu.usth.covidstats.model.GlobalStats;
import vn.edu.usth.covidstats.model.HistoricalData;

/** M4: file that, production constructor, offline da xac dinh, khong sua data nguoi dung. */
@RunWith(AndroidJUnit4.class)
public class M4OfflineFileCacheTest {
    private static final String GLOBAL = "{\"cases\":3000000000,\"deaths\":12}";
    private Context context;
    private File directory;
    private CacheManager cache;
    private RequestQueue queue;
    private final AtomicInteger networkCalls = new AtomicInteger();
    private boolean failNetwork;

    @Before public void setUp() {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        directory = new File(target.getCacheDir(), "m4-offline-" + UUID.randomUUID());
        context = new ContextWrapper(target) {
            @Override public Context getApplicationContext() { return this; }
            @Override public File getFilesDir() { return directory; }
            @Override public Object getSystemService(String name) {
                // NetworkUtil coi ConnectivityManager=null la offline.
                return Context.CONNECTIVITY_SERVICE.equals(name) ? null : super.getSystemService(name);
            }
        };
        cache = new CacheManager(context);
        failNetwork = true;
        queue = new RequestQueue(new NoCache(), request -> {
            networkCalls.incrementAndGet();
            if (failNetwork) throw new NoConnectionError();
            return new NetworkResponse(200, GLOBAL.getBytes(StandardCharsets.UTF_8),
                    false, 0, Collections.emptyList());
        }, 1);
        queue.start();
    }

    @After public void tearDown() {
        queue.stop();
        delete(directory);
    }

    @Test public void expiredDiskCacheStillWorksOfflineAndKeepsItsTimestamp() throws Exception {
        cache.write("/all", GLOBAL);
        File[] entries = new File(directory, "covid_cache").listFiles((dir, name) -> name.endsWith(".json"));
        assertNotNull(entries);
        assertEquals(1, entries.length);
        // Chi thay timestamp cua file test de mo phong cache cu, khong sua production API.
        JSONObject envelope = new JSONObject(read(entries[0]));
        long old = System.currentTimeMillis() - CacheManager.MAX_AGE_MILLIS - 1000;
        envelope.put("savedAtMillis", old);
        try (FileOutputStream output = new FileOutputStream(entries[0])) {
            output.write(envelope.toString().getBytes(StandardCharsets.UTF_8));
        }
        NetworkRepository repository = new NetworkRepository(context, queue, new CacheManager(context));
        Result<GlobalStats> result = observe(cb -> repository.getGlobal(false, cb));
        assertNull(result.error);
        assertTrue(result.cached);
        assertEquals(3_000_000_000L, result.data.cases);
        assertEquals(old, new CacheManager(context).read("/all").savedAtMillis);
        assertEquals(0, networkCalls.get());
    }

    @Test public void historyWindowDoesNotOverwriteFullDiskHistory() throws Exception {
        String history = "{\"cases\":{\"1/1/20\":1,\"3/8/23\":2,\"3/9/23\":3},"
                + "\"deaths\":{\"1/1/20\":0,\"3/8/23\":1,\"3/9/23\":1}}";
        String key = "/historical/all?lastdays=all";
        cache.write(key, history);
        NetworkRepository first = new NetworkRepository(context, queue, cache);
        Result<HistoricalData> shortWindow = observe(cb -> first.getHistory("all", 2, cb));
        assertEquals(2, shortWindow.data.dates.size());
        NetworkRepository next = new NetworkRepository(context, queue, new CacheManager(context));
        Result<HistoricalData> full = observe(cb -> next.getHistory("all", -1, cb));
        assertEquals(3, full.data.dates.size());
        assertEquals("3/9/23", full.data.lastDataDate);
        assertEquals(history, cache.read(key).json);
        assertEquals(0, networkCalls.get());
    }

    @Test public void corruptDiskPayloadOfflineReportsErrorAndIsRemoved() throws Exception {
        cache.write("/all", "invalid payload");
        NetworkRepository repository = new NetworkRepository(context, queue, cache);
        Result<GlobalStats> result = observe(cb -> repository.getGlobal(false, cb));
        assertNull(result.data);
        assertNotNull(result.error);
        assertFalse(result.error.trim().isEmpty());
        assertNull(cache.read("/all"));
        assertEquals(1, networkCalls.get());
    }

    @Test public void unwritableCacheDirectoryDoesNotDiscardValidNetworkResponse() throws Exception {
        assertTrue(directory.mkdirs());
        assertTrue(new File(directory, "covid_cache").createNewFile());
        failNetwork = false;
        NetworkRepository repository = new NetworkRepository(context, queue, cache);
        Result<GlobalStats> result = observe(cb -> repository.getGlobal(true, cb));
        assertNull(result.error);
        assertNotNull(result.data);
        assertFalse(result.cached);
        assertEquals(3_000_000_000L, result.data.cases);
        assertEquals(1, networkCalls.get());
    }

    private interface Start<T> { void run(Callback<T> cb); }
    private static final class Result<T> { T data; boolean cached; String error; boolean main; }
    private <T> Result<T> observe(Start<T> start) throws Exception {
        Result<T> result = new Result<>();
        CountDownLatch done = new CountDownLatch(1);
        start.run(new Callback<T>() {
            @Override public void onSuccess(T data, boolean fromCache) {
                result.data = data; result.cached = fromCache;
                result.main = Looper.myLooper() == Looper.getMainLooper(); done.countDown();
            }
            @Override public void onError(String message) {
                result.error = message;
                result.main = Looper.myLooper() == Looper.getMainLooper(); done.countDown();
            }
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertTrue("Callback must be on main", result.main);
        return result;
    }
    private static String read(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[2048]; int n;
            while ((n = input.read(buffer)) != -1) output.write(buffer, 0, n);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
    private static void delete(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        if (file.exists() && !file.delete()) throw new AssertionError("Cannot delete test file");
    }
}
