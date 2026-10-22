package vn.edu.usth.covidstats;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.Looper;
import android.os.StrictMode;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONArray;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import vn.edu.usth.covidstats.data.Callback;
import vn.edu.usth.covidstats.data.CovidRepository;
import vn.edu.usth.covidstats.data.FakeRepository;
import vn.edu.usth.covidstats.data.JsonParser;
import vn.edu.usth.covidstats.model.CountryStats;
import vn.edu.usth.covidstats.model.GlobalStats;
import vn.edu.usth.covidstats.model.HistoricalData;

/** Kiem tra hop dong M1 tren Android that, bao gom org.json va main Looper. */
@RunWith(AndroidJUnit4.class)
public class M1ContractTest {
    private Context context;
    private CovidRepository repository;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        repository = ((CovidApp) context.getApplicationContext()).getRepository();
    }

    @Test
    public void applicationSharesOneRepositoryAndOneQueue() {
        CovidApp app = (CovidApp) context.getApplicationContext();
        assertTrue(repository instanceof FakeRepository);
        assertSame(repository, app.getRepository());
        assertNotNull(app.getRequestQueue());
        assertSame(app.getRequestQueue(), app.getRequestQueue());
    }

    @Test
    public void globalSampleArrivesAsynchronouslyOnMainThread() throws Exception {
        Result<GlobalStats> result = observe(cb -> repository.getGlobal(false, cb));
        assertNull(result.error);
        assertNotNull(result.data);
        assertTrue(result.data.cases > Integer.MAX_VALUE / 4L);
        assertTrue(result.data.tests > Integer.MAX_VALUE);
        assertFalse(result.fromCache);
    }

    @Test
    public void countriesKeepRowsWithMissingIsoCodes() throws Exception {
        Result<List<CountryStats>> result = observe(cb -> repository.getCountries(true, cb));
        assertNull(result.error);
        assertEquals(231, result.data.size());
        int missing = 0;
        for (CountryStats country : result.data) {
            assertNotNull(country.country);
            assertNotNull(country.iso2);
            if (country.iso2.isEmpty()) missing++;
        }
        assertEquals(2, missing);
    }

    @Test
    public void countryLookupNormalizesIsoAndUsesTheCorrectCountry() throws Exception {
        Result<CountryStats> vietnam = observe(cb -> repository.getCountry(" vn ", cb));
        assertNull(vietnam.error);
        assertEquals("VN", vietnam.data.iso2);
        assertEquals("Vietnam", vietnam.data.country);
        Result<CountryStats> usa = observe(cb -> repository.getCountry("us", cb));
        assertNull(usa.error);
        assertEquals("US", usa.data.iso2);
    }

    @Test
    public void invalidAndUnknownCountriesReportErrors() throws Exception {
        Result<CountryStats> invalid = observe(cb -> repository.getCountry(null, cb));
        assertNotNull(invalid.error);
        assertNull(invalid.data);
        Result<CountryStats> unknown = observe(cb -> repository.getCountry("ZZ", cb));
        assertNotNull(unknown.error);
    }

    @Test
    public void globalAndVietnamHistoryReadTheirDifferentShapes() throws Exception {
        Result<HistoricalData> global = observe(cb -> repository.getHistory(
                "all", CovidRepository.ALL_DAYS, cb));
        Result<HistoricalData> vietnam = observe(cb -> repository.getHistory(
                "VN", CovidRepository.ALL_DAYS, cb));
        assertNull(global.error);
        assertNull(vietnam.error);
        assertEquals(1143, global.data.dates.size());
        assertEquals(1143, vietnam.data.dates.size());
        assertEquals("3/9/23", global.data.lastDataDate);
        assertEquals("Vietnam", vietnam.data.country);
        assertEquals("VN", vietnam.data.iso2OrAll);
    }

    @Test
    public void allChartWindowsEndAtTheSourceDate() throws Exception {
        for (int days : new int[]{30, 90, 365}) {
            Result<HistoricalData> result = observe(cb -> repository.getHistory("all", days, cb));
            assertNull(result.error);
            assertEquals(days, result.data.dates.size());
            assertEquals("3/9/23", result.data.lastDataDate);
        }
    }

    @Test
    public void missingHistoryDoesNotSubstituteVietnamData() throws Exception {
        Result<HistoricalData> missing = observe(cb -> repository.getHistory("US", 30, cb));
        assertNotNull(missing.error);
        assertNull(missing.data);
        Result<HistoricalData> invalid = observe(cb -> repository.getHistory("all", 0, cb));
        assertNotNull(invalid.error);
    }

    @Test
    public void parserToleratesMissingFieldsNullsAndLargeNumbers() throws Exception {
        GlobalStats stats = JsonParser.parseGlobal("{\"cases\":3000000000,\"deaths\":null}");
        assertEquals(3000000000L, stats.cases);
        assertEquals(0, stats.deaths);
        CountryStats country = JsonParser.parseCountry(
                "{\"country\":\"Test\",\"countryInfo\":null,\"continent\":null}");
        assertEquals("", country.iso2);
        assertEquals("", country.continent);
        assertEquals(0, country.population);
    }

    @Test
    public void historySortsCalendarDatesAndSkipsMissingPoints() throws Exception {
        HistoricalData data = JsonParser.parseHistory("all", CovidRepository.ALL_DAYS,
                "{\"cases\":{\"1/1/21\":20,\"12/31/20\":10,\"1/2/21\":null},"
                        + "\"deaths\":{\"1/2/21\":2,\"12/31/20\":1}}");
        assertEquals(Arrays.asList("12/31/20", "1/1/21", "1/2/21"), data.dates);
        assertFalse(data.cases.containsKey("1/2/21"));
        assertEquals(Long.valueOf(20), data.cases.get("1/1/21"));
        assertTrue(data.recovered.isEmpty());
    }

    @Test
    public void historyWindowCountsCalendarDaysRatherThanAvailableRows() throws Exception {
        HistoricalData data = JsonParser.parseHistory("all", 2,
                "{\"cases\":{\"1/1/21\":10,\"2/1/21\":20,\"2/2/21\":30},\"deaths\":{}}");
        assertEquals(Arrays.asList("2/1/21", "2/2/21"), data.dates);
    }

    @Test(expected = JSONException.class)
    public void unsupportedProvinceResponseIsAParseError() throws Exception {
        JsonParser.parseHistory("CN", CovidRepository.ALL_DAYS, "[{\"timeline\":{}}]");
    }

    @Test(expected = JSONException.class)
    public void invalidCalendarDateIsRejected() throws Exception {
        JsonParser.parseHistory("all", 30,
                "{\"cases\":{\"2/30/23\":1},\"deaths\":{}}");
    }

    @Test
    public void sixthSampleContainsTheSixContinents() throws Exception {
        try (InputStream input = context.getResources().openRawResource(R.raw.continents);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            assertEquals(6, new JSONArray(output.toString(StandardCharsets.UTF_8.name())).length());
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 28)
    public void loadingSamplesDoesNotDoDiskOrNetworkWorkOnMainThread() throws Exception {
        AtomicInteger violations = new AtomicInteger();
        AtomicReference<StrictMode.ThreadPolicy> original = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            original.set(StrictMode.getThreadPolicy());
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads().detectDiskWrites().detectNetwork()
                    .penaltyListener(Runnable::run, violation -> violations.incrementAndGet())
                    .build());
        });
        try {
            Result<List<CountryStats>> countries = observe(cb -> repository.getCountries(true, cb));
            Result<HistoricalData> history = observe(cb -> repository.getHistory("all", 365, cb));
            assertNull(countries.error);
            assertNull(history.error);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertEquals(0, violations.get());
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(
                    () -> StrictMode.setThreadPolicy(original.get()));
        }
    }

    private <T> Result<T> observe(Invocation<T> invocation) throws Exception {
        Result<T> result = new Result<>();
        CountDownLatch done = new CountDownLatch(1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            invocation.call(new Callback<T>() {
                @Override
                public void onSuccess(T data, boolean fromCache) {
                    result.data = data;
                    result.fromCache = fromCache;
                    finish();
                }

                @Override
                public void onError(String message) {
                    result.error = message;
                    finish();
                }

                private void finish() {
                    result.onMainThread = Looper.myLooper() == Looper.getMainLooper();
                    result.async = result.callReturned;
                    done.countDown();
                }
            });
            result.callReturned = true;
        });
        assertTrue("Repository callback timed out", done.await(15, TimeUnit.SECONDS));
        assertTrue("Callback must run on main thread", result.onMainThread);
        assertTrue("Callback must not run inline", result.async);
        return result;
    }

    private interface Invocation<T> {
        void call(Callback<T> cb);
    }

    private static final class Result<T> {
        T data;
        String error;
        boolean fromCache;
        boolean onMainThread;
        boolean async;
        boolean callReturned;
    }
}
