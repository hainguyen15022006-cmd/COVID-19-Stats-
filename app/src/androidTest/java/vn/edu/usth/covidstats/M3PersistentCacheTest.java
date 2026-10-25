package vn.edu.usth.covidstats;

import static org.junit.Assert.*;

import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import vn.edu.usth.covidstats.data.Callback;
import vn.edu.usth.covidstats.data.CovidRepository;
import vn.edu.usth.covidstats.data.NetworkRepository;
import vn.edu.usth.covidstats.model.CountryStats;
import vn.edu.usth.covidstats.model.GlobalStats;
import vn.edu.usth.covidstats.util.NetworkUtil;

/** Chay hai lan rieng: phase seed online, force-stop, phase read offline. */
@RunWith(AndroidJUnit4.class)
public class M3PersistentCacheTest {
    @Test public void sharedApplicationRepositoryUsesDiskAcrossProcesses() throws Exception {
        String phase = InstrumentationRegistry.getArguments().getString("m3CachePhase", "seed");
        assertTrue("Unknown cache phase", phase.equals("seed") || phase.equals("read"));
        CovidApp app = (CovidApp) InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getApplicationContext();
        CovidRepository repository = app.getRepository();
        assertTrue(repository instanceof NetworkRepository);
        boolean seed = phase.equals("seed");
        if (!seed) assertFalse("Read phase must run offline", NetworkUtil.isOnline(app));
        long start = SystemClock.elapsedRealtime();
        GlobalStats global = fetch(cb -> repository.getGlobal(seed, cb), !seed);
        List<CountryStats> countries = fetch(cb -> repository.getCountries(seed, cb), !seed);
        assertTrue(global.cases > 0);
        assertFalse(countries.isEmpty());
        long elapsed = SystemClock.elapsedRealtime() - start;
        Log.i("M3CacheProof", "phase=" + phase + " pid=" + Process.myPid()
                + " elapsedMs=" + elapsed + " countries=" + countries.size());
        if (!seed) assertTrue("Cached data exceeded 2 seconds: " + elapsed, elapsed < 2000);
    }

    private interface Start<T> { void run(Callback<T> callback); }
    private static final class Result<T> { T data; String error; boolean cached; boolean main; }

    private <T> T fetch(Start<T> start, boolean expectedCache) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Result<T> result = new Result<>();
        start.run(new Callback<T>() {
            @Override public void onSuccess(T data, boolean fromCache) {
                result.data = data;
                result.cached = fromCache;
                result.main = Looper.myLooper() == Looper.getMainLooper();
                done.countDown();
            }
            @Override public void onError(String message) { result.error = message; done.countDown(); }
        });
        assertTrue(done.await(25, TimeUnit.SECONDS));
        assertNull(result.error);
        assertTrue(result.main);
        assertEquals(expectedCache, result.cached);
        return result.data;
    }
}
