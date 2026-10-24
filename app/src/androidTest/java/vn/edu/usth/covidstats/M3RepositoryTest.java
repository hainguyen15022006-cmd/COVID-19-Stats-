package vn.edu.usth.covidstats;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.Looper;
import android.os.StrictMode;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.volley.Network;
import com.android.volley.NetworkResponse;
import com.android.volley.NoConnectionError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.toolbox.NoCache;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import vn.edu.usth.covidstats.data.Callback;
import vn.edu.usth.covidstats.data.CovidRepository;
import vn.edu.usth.covidstats.data.JsonParser;
import vn.edu.usth.covidstats.data.NetworkRepository;
import vn.edu.usth.covidstats.model.CountryStats;
import vn.edu.usth.covidstats.model.GlobalStats;
import vn.edu.usth.covidstats.model.HistoricalData;

/** Test xac dinh, khong phu thuoc API song hay dong doi da viet CacheManager. */
@RunWith(AndroidJUnit4.class)
public class M3RepositoryTest {
    private Context context;
    private TestNetwork network;
    private RequestQueue queue;
    private MemoryStore store;
    private AtomicLong clock;
    private NetworkRepository repository;

    @Before public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        network = new TestNetwork();
        network.responses.put("all", raw(R.raw.global_stats));
        network.responses.put("countries?sort=cases", raw(R.raw.countries));
        network.responses.put("countries/VN", raw(R.raw.country_vn));
        network.responses.put("historical/all?lastdays=all", raw(R.raw.history_all));
        network.responses.put("historical/VN?lastdays=all", raw(R.raw.history_vn));
        queue = new RequestQueue(new NoCache(), network, 1);
        queue.start();
        store = new MemoryStore();
        clock = new AtomicLong(1_000_000L);
        repository = new NetworkRepository(context, queue, store, clock::get);
    }

    @After public void tearDown() { queue.stop(); }

    @Test public void allFivePathsReturnTypedDataOnMainThread() throws Exception {
        Result<GlobalStats> global = observe(cb -> repository.getGlobal(false, cb));
        assertNotNull(global.data);
        assertFalse(global.cached);
        Result<List<CountryStats>> countries = observe(cb -> repository.getCountries(false, cb));
        assertEquals(231, countries.data.size());
        Result<CountryStats> country = observe(cb -> repository.getCountry(" vn ", cb));
        assertEquals("VN", country.data.iso2);
        Result<HistoricalData> world = observe(cb -> repository.getHistory("all", -1, cb));
        Result<HistoricalData> vn = observe(cb -> repository.getHistory("VN", -1, cb));
        assertEquals(1143, world.data.dates.size());
        assertEquals("3/9/23", vn.data.lastDataDate);
        assertEquals(5, network.calls.get());
        assertNotEquals(Looper.getMainLooper().getThread(), network.thread);
        assertNotEquals(Looper.getMainLooper().getThread(), store.lastThread);
    }

    @Test public void freshCacheAndForceRefreshHaveDifferentBehavior() throws Exception {
        assertFalse(observeGlobal(false).cached);
        assertTrue(observeGlobal(false).cached);
        assertEquals(1, network.calls.get());
        assertFalse(observeGlobal(true).cached);
        assertEquals(2, network.calls.get());
    }

    @Test public void tenMinuteTtlExpiresWithoutWaitingTenMinutes() throws Exception {
        observeGlobal(false);
        clock.addAndGet(NetworkRepository.CACHE_TTL_MS - 1);
        assertTrue(observeGlobal(false).cached);
        clock.incrementAndGet();
        assertFalse(observeGlobal(false).cached);
        assertEquals(2, network.calls.get());
    }

    @Test public void t02OfflineWithoutCacheReportsErrorInsteadOfFakeData() throws Exception {
        network.offline = true;
        Result<GlobalStats> result = observeGlobal(false);
        assertNull(result.data);
        assertNotNull(result.error);
        assertEquals(1, network.calls.get());
    }

    @Test public void staleCacheSurvivesNetworkFailure() throws Exception {
        observeGlobal(false);
        clock.addAndGet(NetworkRepository.CACHE_TTL_MS);
        network.offline = true;
        Result<GlobalStats> result = observeGlobal(false);
        assertNull(result.error);
        assertTrue(result.cached);
        assertNotNull(result.data);
    }

    @Test public void newRepositoryReadsCacheBridgeOnWorker() throws Exception {
        observeGlobal(false);
        network.offline = true;
        // Cung store test, Repository moi. Day KHONG phai test file hay restart process.
        NetworkRepository next = new NetworkRepository(context, queue, store, clock::get);
        Result<GlobalStats> result = observe(cb -> next.getGlobal(false, cb));
        assertTrue(result.cached);
        assertEquals(1, network.calls.get());
        assertNotEquals(Looper.getMainLooper().getThread(), store.lastThread);
    }

    @Test public void historyWindowsReuseFullJsonAndKeepTheLastSourceDate() throws Exception {
        Result<HistoricalData> month = observe(cb -> repository.getHistory("VN", 30, cb));
        Result<HistoricalData> quarter = observe(cb -> repository.getHistory("VN", 90, cb));
        Result<HistoricalData> year = observe(cb -> repository.getHistory("VN", 365, cb));
        Result<HistoricalData> all = observe(cb -> repository.getHistory("VN", -1, cb));
        assertEquals(30, month.data.dates.size());
        assertEquals(90, quarter.data.dates.size());
        assertEquals(365, year.data.dates.size());
        assertEquals(1143, all.data.dates.size());
        assertEquals("3/9/23", all.data.lastDataDate);
        assertTrue(quarter.cached);
        assertEquals(1, network.calls.get());
    }

    @Test public void t04NullAndLargeValuesDoNotCrash() throws Exception {
        network.responses.put("countries?sort=cases", "[{\"country\":\"X\","
                + "\"countryInfo\":null,\"cases\":3000000000,\"deaths\":null}]");
        Result<List<CountryStats>> result = observe(cb -> repository.getCountries(true, cb));
        assertNull(result.error);
        assertEquals("", result.data.get(0).iso2);
        assertEquals(3_000_000_000L, result.data.get(0).cases);
        assertEquals(0, result.data.get(0).deaths);
    }

    @Test public void unreadableNetworkJsonReportsError() throws Exception {
        network.responses.put("all", "<html>unavailable</html>");
        Result<GlobalStats> result = observeGlobal(true);
        assertNull(result.data);
        assertNotNull(result.error);
    }

    @Test public void timeoutWithoutCacheReportsReadableError() throws Exception {
        network.failure = new TimeoutError();
        Result<GlobalStats> result = observeGlobal(true);
        assertNull(result.data);
        assertNotNull(result.error);
        assertFalse(result.error.trim().isEmpty());
    }

    @Test public void httpServerFailureWithoutCacheReportsStatusCode() throws Exception {
        network.failure = new ServerError(new NetworkResponse(503, new byte[0],
                false, 0, Collections.emptyList()));
        Result<GlobalStats> result = observeGlobal(true);
        assertNull(result.data);
        assertTrue(result.error.contains("503"));
    }

    @Test public void forceRefreshFallsBackToOldCacheWhenServerFails() throws Exception {
        observeGlobal(false);
        network.failure = new ServerError(new NetworkResponse(500, new byte[0],
                false, 0, Collections.emptyList()));
        Result<GlobalStats> result = observeGlobal(true);
        assertNotNull(result.data);
        assertTrue(result.cached);
        assertEquals(2, network.calls.get());
    }

    @Test public void corruptedCacheDoesNotBlockValidNetworkData() throws Exception {
        store.items.put("/all", new NetworkRepository.CacheRecord("not json", clock.get()));
        Result<GlobalStats> result = observeGlobal(false);
        assertNotNull(result.data);
        assertFalse(result.cached);
        assertEquals(1, store.removals);
    }

    @Test public void cacheWriteFailureDoesNotDiscardNetworkSuccess() throws Exception {
        store.failWrite = true;
        Result<GlobalStats> result = observeGlobal(false);
        assertNotNull(result.data);
        assertFalse(result.cached);
    }

    @Test public void invalidArgumentsDoNotSendRequests() throws Exception {
        Result<CountryStats> badIso = observe(cb -> repository.getCountry("vietnam", cb));
        Result<HistoricalData> badDays = observe(cb -> repository.getHistory("VN", 0, cb));
        assertNotNull(badIso.error);
        assertNotNull(badDays.error);
        assertEquals(0, network.calls.get());
    }

    @Test public void staleOfflineCacheReturnsWithoutStartingNetwork() throws Exception {
        observeGlobal(false);
        clock.addAndGet(NetworkRepository.CACHE_TTL_MS);
        network.offline = true;
        NetworkRepository offline = new NetworkRepository(context, queue, store,
                clock::get, () -> false);
        Result<GlobalStats> result = observe(cb -> offline.getGlobal(true, cb));
        assertNotNull(result.data);
        assertTrue(result.cached);
        assertEquals(1, network.calls.get());
    }

    @Test public void duplicateRequestsShareOneInFlightOperation() throws Exception {
        network.block = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicReference<String> error = new AtomicReference<>();
        Callback<GlobalStats> callback = new Callback<GlobalStats>() {
            @Override public void onSuccess(GlobalStats data, boolean fromCache) {
                done.countDown();
            }
            @Override public void onError(String message) { error.set(message); done.countDown(); }
        };
        repository.getGlobal(true, callback);
        repository.getGlobal(true, callback);
        assertTrue(network.entered.await(5, TimeUnit.SECONDS));
        network.block.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertNull(error.get());
        assertEquals(1, network.calls.get());
    }

    @Test @SdkSuppress(minSdkVersion = 28)
    public void t12RepositoryDoesNotPerformMainThreadIo() throws Exception {
        AtomicInteger violations = new AtomicInteger();
        AtomicReference<StrictMode.ThreadPolicy> previous = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            previous.set(StrictMode.getThreadPolicy());
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads().detectDiskWrites().detectNetwork()
                    .penaltyListener(Runnable::run, violation -> violations.incrementAndGet())
                    .build());
        });
        try {
            Result<GlobalStats> result = observe(cb ->
                    InstrumentationRegistry.getInstrumentation().runOnMainSync(
                            () -> repository.getGlobal(true, cb)));
            assertNotNull(result.data);
            assertEquals(0, violations.get());
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(
                    () -> StrictMode.setThreadPolicy(previous.get()));
        }
    }

    private Result<GlobalStats> observeGlobal(boolean force) throws Exception {
        return observe(cb -> repository.getGlobal(force, cb));
    }

    private interface Start<T> { void run(Callback<T> cb); }

    private static final class Result<T> {
        T data;
        boolean cached;
        String error;
        boolean onMain;
    }

    private <T> Result<T> observe(Start<T> start) throws Exception {
        Result<T> result = new Result<>();
        CountDownLatch done = new CountDownLatch(1);
        start.run(new Callback<T>() {
            @Override public void onSuccess(T data, boolean fromCache) {
                result.data = data;
                result.cached = fromCache;
                result.onMain = Looper.myLooper() == Looper.getMainLooper();
                done.countDown();
            }
            @Override public void onError(String message) {
                result.error = message;
                result.onMain = Looper.myLooper() == Looper.getMainLooper();
                done.countDown();
            }
        });
        assertTrue("Callback timeout", done.await(15, TimeUnit.SECONDS));
        assertTrue("Callback must run on main", result.onMain);
        return result;
    }

    private String raw(int id) throws IOException {
        try (InputStream input = context.getResources().openRawResource(id);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static final class TestNetwork implements Network {
        final Map<String, String> responses = new HashMap<>();
        final AtomicInteger calls = new AtomicInteger();
        final CountDownLatch entered = new CountDownLatch(1);
        volatile boolean offline;
        volatile VolleyError failure;
        volatile CountDownLatch block;
        volatile Thread thread;

        @Override public NetworkResponse performRequest(Request<?> request) throws VolleyError {
            thread = Thread.currentThread();
            calls.incrementAndGet();
            entered.countDown();
            if (block != null) {
                try { if (!block.await(5, TimeUnit.SECONDS)) throw new NoConnectionError(); }
                catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new NoConnectionError();
                }
            }
            if (offline) throw new NoConnectionError();
            if (failure != null) throw failure;
            String path = request.getUrl().replace("https://disease.sh/v3/covid-19/", "");
            String body = responses.get(path);
            if (body == null) throw new NoConnectionError();
            return new NetworkResponse(200, body.getBytes(StandardCharsets.UTF_8),
                    false, 0, Collections.emptyList());
        }
    }

    private static final class MemoryStore implements NetworkRepository.CacheStore {
        final Map<String, NetworkRepository.CacheRecord> items = new HashMap<>();
        volatile Thread lastThread;
        boolean failWrite;
        int removals;
        @Override public void remove(String key) {
            removals++;
            items.remove(key);
        }
        @Override public NetworkRepository.CacheRecord read(String key) {
            lastThread = Thread.currentThread();
            return items.get(key);
        }
        @Override public void write(String key, NetworkRepository.CacheRecord record)
                throws IOException {
            lastThread = Thread.currentThread();
            if (failWrite) throw new IOException("Simulated disk full");
            items.put(key, record);
        }
    }
}
