package vn.edu.usth.covidstats;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.Looper;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.NoCache;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import vn.edu.usth.covidstats.data.Callback;
import vn.edu.usth.covidstats.data.NetworkRepository;
import vn.edu.usth.covidstats.model.CountryStats;
import vn.edu.usth.covidstats.model.GlobalStats;
import vn.edu.usth.covidstats.model.HistoricalData;

/** Test mang THAT: chi chay rieng khi co Internet; ket qua phu thuoc dich vu ngoai. */
@RunWith(AndroidJUnit4.class)
public class M3LiveApiTest {
    @Test public void fiveRealEndpointsRespond() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        RequestQueue queue = new RequestQueue(new NoCache(), new BasicNetwork(new HurlStack()));
        queue.start();
        try {
            NetworkRepository repo = new NetworkRepository(context, queue,
                    NetworkRepository.NO_DISK_CACHE);
            GlobalStats global = fetch(cb -> repo.getGlobal(true, cb));
            List<CountryStats> countries = fetch(cb -> repo.getCountries(true, cb));
            CountryStats vn = fetch(cb -> repo.getCountry("VN", cb));
            HistoricalData world = fetch(cb -> repo.getHistory("all", -1, cb));
            HistoricalData history = fetch(cb -> repo.getHistory("VN", 30, cb));
            assertTrue(global.cases > 0);
            assertFalse(countries.isEmpty());
            assertEquals("VN", vn.iso2);
            assertFalse(world.lastDataDate.isEmpty());
            assertTrue(history.dates.size() <= 30);
            assertFalse(history.dates.isEmpty());
        } finally {
            queue.stop();
        }
    }

    private interface Start<T> { void run(Callback<T> callback); }
    private static final class Result<T> { T data; String error; boolean onMain; }

    private <T> T fetch(Start<T> start) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Result<T> result = new Result<>();
        start.run(new Callback<T>() {
            @Override public void onSuccess(T data, boolean fromCache) {
                result.data = data;
                result.onMain = Looper.myLooper() == Looper.getMainLooper();
                done.countDown();
            }
            @Override public void onError(String message) {
                result.error = message;
                result.onMain = Looper.myLooper() == Looper.getMainLooper();
                done.countDown();
            }
        });
        assertTrue("Live callback timeout", done.await(25, TimeUnit.SECONDS));
        assertNull("Live API failed: " + result.error, result.error);
        assertTrue(result.onMain);
        return result.data;
    }
}
