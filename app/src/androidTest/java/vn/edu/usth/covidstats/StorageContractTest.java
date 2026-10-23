package vn.edu.usth.covidstats;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.Looper;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import vn.edu.usth.covidstats.data.CacheManager;
import vn.edu.usth.covidstats.data.Callback;
import vn.edu.usth.covidstats.data.WatchlistStore;

/** Chay tren emulator/thiet bi: khong mock SharedPreferences, AtomicFile hay main Looper. */
@RunWith(AndroidJUnit4.class)
public class StorageContractTest {
    private Context context;
    private CacheManager cache;
    private WatchlistStore store;
    private String key;
    private Set<String> original;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        cache = new CacheManager(context);
        store = WatchlistStore.getInstance(context);
        key = "test/" + UUID.randomUUID();
        original = await(cb -> store.getCodes(cb)).data;
    }

    @After
    public void tearDown() throws Exception {
        cache.remove(key);
        cache.remove(key + "?days=30");
        // Chi cac ma test nay bi sua; phuc hoi trang thai ban dau.
        await(cb -> store.setStarred("ZZ", original.contains("ZZ"), cb));
        await(cb -> store.setStarred("ZY", original.contains("ZY"), cb));
    }

    @Test
    public void missingCacheIsNullAndUnicodeSurvivesNewInstance() throws Exception {
        assertNull(cache.read(key));
        String json = "{\"country\":\"Việt Nam\",\"cases\":3000000000}";
        cache.write(key, json);
        CacheManager.Entry entry = new CacheManager(context).read(key);
        assertNotNull(entry);
        assertEquals(json, entry.json);
        assertTrue(entry.isFresh());
    }

    @Test
    public void tenMinuteBoundaryAndClockRollbackDoNotCountAsFresh() throws Exception {
        cache.write(key, "{}");
        CacheManager.Entry entry = cache.read(key);
        assertTrue(entry.isFresh(entry.savedAtMillis + CacheManager.MAX_AGE_MILLIS - 1));
        assertFalse(entry.isFresh(entry.savedAtMillis + CacheManager.MAX_AGE_MILLIS));
        assertFalse(entry.isFresh(entry.savedAtMillis - 1));
        // Het han khong xoa du lieu fallback.
        assertNotNull(cache.read(key));
    }

    @Test
    public void differentQueriesDoNotOverwriteAndRemoveIsScoped() throws Exception {
        cache.write(key, "{\"all\":true}");
        cache.write(key + "?days=30", "{\"days\":30}");
        cache.remove(key);
        assertNull(cache.read(key));
        assertEquals("{\"days\":30}", cache.read(key + "?days=30").json);
    }

    @Test
    public void corruptFileBecomesMissThenCanBeReplaced() throws Exception {
        cache.write(key, "{}");
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
        StringBuilder name = new StringBuilder();
        for (byte b : digest) name.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        File file = new File(new File(context.getFilesDir(), "covid_cache"), name + ".json");
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write("broken JSON".getBytes(StandardCharsets.UTF_8));
        }
        assertNull(cache.read(key));
        cache.write(key, "{\"ok\":true}");
        assertEquals("{\"ok\":true}", cache.read(key).json);
    }

    @Test
    public void fileIoOnMainThreadIsRejected() {
        AtomicReference<Throwable> error = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try { cache.read(key); } catch (Throwable e) { error.set(e); }
        });
        assertTrue(error.get() instanceof IllegalStateException);
    }

    @Test
    public void starNormalizesCodePersistsAndReturnsDefensiveSnapshot() throws Exception {
        Result added = await(cb -> store.setStarred(" zz ", true, cb));
        assertNull(added.error);
        assertTrue(added.data.contains("ZZ"));
        Set<String> persisted = context.getSharedPreferences("covid_watchlist", Context.MODE_PRIVATE)
                .getStringSet("iso2_codes", new HashSet<>());
        assertTrue(persisted.contains("ZZ"));
        try {
            added.data.clear();
            fail("Snapshot must be immutable");
        } catch (UnsupportedOperationException expected) { }
        await(cb -> store.setStarred("ZZ", false, cb));
        assertFalse(await(cb -> store.getCodes(cb)).data.contains("ZZ"));
        assertTrue(added.data.contains("ZZ"));
    }

    @Test
    public void invalidCodesDoNotChangeStars() throws Exception {
        Set<String> before = await(cb -> store.getCodes(cb)).data;
        for (String invalid : new String[]{null, "", "Vietnam", "../", "123"}) {
            Result result = await(cb -> store.setStarred(invalid, true, cb));
            assertNotNull(result.error);
        }
        assertEquals(before, await(cb -> store.getCodes(cb)).data);
    }

    @Test
    public void queuedTogglesAreSerializedWithoutLostUpdates() throws Exception {
        await(cb -> store.setStarred("ZY", false, cb));
        CountDownLatch done = new CountDownLatch(20);
        AtomicReference<String> error = new AtomicReference<>();
        for (int i = 0; i < 20; i++) {
            store.toggle("ZY", new Callback<Set<String>>() {
                @Override public void onSuccess(Set<String> data, boolean fromCache) { done.countDown(); }
                @Override public void onError(String message) { error.set(message); done.countDown(); }
            });
        }
        assertTrue(done.await(15, TimeUnit.SECONDS));
        assertNull(error.get());
        assertFalse(await(cb -> store.getCodes(cb)).data.contains("ZY"));
    }

    @Test
    public void observersReceiveCommittedChangesOnMainThread() throws Exception {
        await(cb -> store.setStarred("ZZ", false, cb));
        CountDownLatch event = new CountDownLatch(1);
        AtomicReference<Set<String>> observed = new AtomicReference<>();
        AtomicReference<Boolean> onMain = new AtomicReference<>();
        WatchlistStore.Listener listener = codes -> {
            observed.set(codes);
            onMain.set(Looper.myLooper() == Looper.getMainLooper());
            event.countDown();
        };
        store.addListener(listener);
        try {
            await(cb -> store.setStarred("ZZ", true, cb));
            assertTrue(event.await(5, TimeUnit.SECONDS));
            assertTrue(onMain.get());
            assertTrue(observed.get().contains("ZZ"));
        } finally {
            store.removeListener(listener);
        }
    }

    private Result await(Invocation action) throws Exception {
        Result result = new Result();
        CountDownLatch done = new CountDownLatch(1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> action.call(new Callback<Set<String>>() {
            @Override public void onSuccess(Set<String> data, boolean fromCache) {
                result.data = data;
                finish();
            }
            @Override public void onError(String message) {
                result.error = message;
                finish();
            }
            private void finish() {
                result.onMain = Looper.myLooper() == Looper.getMainLooper();
                done.countDown();
            }
        }));
        assertTrue("Callback timed out", done.await(15, TimeUnit.SECONDS));
        assertTrue("Callback must be on main thread", result.onMain);
        return result;
    }

    private interface Invocation { void call(Callback<Set<String>> callback); }
    private static final class Result {
        Set<String> data;
        String error;
        boolean onMain;
    }
}
