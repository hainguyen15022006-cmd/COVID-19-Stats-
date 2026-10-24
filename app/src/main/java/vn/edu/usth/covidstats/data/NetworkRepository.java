package vn.edu.usth.covidstats.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.android.volley.NoConnectionError;
import com.android.volley.ParseError;
import com.android.volley.RequestQueue;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.LongSupplier;
import java.util.function.BooleanSupplier;

import vn.edu.usth.covidstats.R;
import vn.edu.usth.covidstats.model.CountryStats;
import vn.edu.usth.covidstats.model.GlobalStats;
import vn.edu.usth.covidstats.model.HistoricalData;
import vn.edu.usth.covidstats.util.NetworkUtil;

/** Giu nguyen CovidRepository M1; tach mang, parser, cache va UI. */
public final class NetworkRepository implements CovidRepository {
    public static final long CACHE_TTL_MS = 10 * 60 * 1000L;

    /** Cau noi de ghep CacheManager CUA CONG DUONG, khong phai cache file thu hai. */
    public interface CacheStore {
        CacheRecord read(String key) throws IOException;
        void write(String key, CacheRecord record) throws IOException;
        default void remove(String key) throws IOException { }
    }

    public static final class CacheRecord {
        public final String json;
        public final long fetchedAt;

        public CacheRecord(String json, long fetchedAt) {
            this.json = json;
            this.fetchedAt = fetchedAt;
        }
    }

    /** Chi cho test mang doc lap; production dung constructor nhan CacheManager. */
    public static final CacheStore NO_DISK_CACHE = new CacheStore() {
        @Override public CacheRecord read(String key) { return null; }
        @Override public void write(String key, CacheRecord record) { }
    };

    private final Context context;
    private final CovidApi api;
    private final CacheStore store;
    private final LongSupplier clock;
    private final BooleanSupplier online;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            task -> new Thread(task, "covid-repository"));
    // Hai map nay CHI duoc doc/ghi tren worker o tren.
    private final Map<String, CacheRecord> memory = new HashMap<>();
    private final Map<String, Flight<?>> flights = new HashMap<>();

    /** Cau hinh production: dung nguyen CacheManager cua Cong Duong. */
    public NetworkRepository(Context context, RequestQueue queue, CacheManager cacheManager) {
        this(context, queue, new CacheStore() {
            @Override public CacheRecord read(String key) throws IOException {
                CacheManager.Entry entry = cacheManager.read(key);
                return entry == null ? null : new CacheRecord(entry.json, entry.savedAtMillis);
            }
            @Override public void write(String key, CacheRecord record) throws IOException {
                // CacheManager tu ghi thoi gian tai; khong lam moi timestamp khi doc.
                cacheManager.write(key, record.json);
            }
            @Override public void remove(String key) { cacheManager.remove(key); }
        }, System::currentTimeMillis,
                () -> NetworkUtil.isOnline(context.getApplicationContext()));
    }

    public NetworkRepository(Context context, RequestQueue queue, CacheStore store) {
        this(context, queue, store, System::currentTimeMillis);
    }

    // Clock co the thay trong test de khong can cho 10 phut.
    public NetworkRepository(Context context, RequestQueue queue, CacheStore store,
                             LongSupplier clock) {
        this(context, queue, store, clock, () -> true);
    }

    /** Cho test dieu khien ca dong ho va ket noi, khong phu thuoc mang cua emulator. */
    public NetworkRepository(Context context, RequestQueue queue, CacheStore store,
                             LongSupplier clock, BooleanSupplier online) {
        this.context = context.getApplicationContext();
        this.api = new CovidApi(queue);
        this.store = store;
        this.clock = clock;
        this.online = online;
    }

    @Override public void getGlobal(boolean forceRefresh, Callback<GlobalStats> cb) {
        load("/all", "global", "all", forceRefresh, JsonParser::parseGlobal, cb);
    }

    @Override public void getCountries(boolean forceRefresh, Callback<List<CountryStats>> cb) {
        load("/countries?sort=cases", "countries", "countries?sort=cases", forceRefresh,
                JsonParser::parseCountries, cb);
    }

    @Override public void getCountry(String iso2, Callback<CountryStats> cb) {
        String iso = normalize(iso2);
        if (iso == null) {
            main.post(() -> cb.onError(context.getString(R.string.m3_invalid_country)));
            return;
        }
        load("/countries/" + iso, "country_" + iso, "countries/" + iso, false,
                JsonParser::parseCountry, cb);
    }

    @Override public void getHistory(String iso2OrAll, int days, Callback<HistoricalData> cb) {
        if (days != ALL_DAYS && days <= 0) {
            main.post(() -> cb.onError(context.getString(R.string.m3_invalid_days)));
            return;
        }
        String iso = iso2OrAll != null && "all".equalsIgnoreCase(iso2OrAll.trim())
                ? "all" : normalize(iso2OrAll);
        if (iso == null) {
            main.post(() -> cb.onError(context.getString(R.string.m3_invalid_country)));
            return;
        }
        String key = "/historical/" + iso + "?lastdays=all";
        // Cache lich su DAY DU; doi 30/90/365/all chi loc tren worker.
        load(key, "history_" + iso + "_" + days, "historical/" + iso + "?lastdays=all", false,
                json -> JsonParser.parseHistory(iso, days, json), cb);
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String iso = value.trim().toUpperCase(Locale.ROOT);
        return iso.matches("[A-Z]{2}") ? iso : null;
    }

    private static final class Flight<T> {
        final List<Callback<T>> callbacks = new ArrayList<>();
        final CovidApi.Parser<T> parser;
        CacheRecord fallback;
        Flight(CovidApi.Parser<T> parser) { this.parser = parser; }
    }

    // jobKey do chinh cac ham tren tao; moi key chi co MOT kieu T va mot bo loc ngay.
    @SuppressWarnings("unchecked")
    private <T> void load(String key, String jobKey, String path, boolean force,
                          CovidApi.Parser<T> parser, Callback<T> cb) {
        worker.execute(() -> {
            Flight<T> running = (Flight<T>) flights.get(jobKey);
            if (running != null) {
                running.callbacks.add(cb);
                return;
            }
            Flight<T> flight = new Flight<>(parser);
            flight.callbacks.add(cb);
            flights.put(jobKey, flight);
            CacheRecord cached = memory.get(key);
            if (cached == null) {
                try { cached = store.read(key); }
                catch (IOException | RuntimeException error) {
                    Log.w("M3Data", "CACHE_READ_FAILED " + key);
                }
            }
            T cachedData = null;
            if (cached != null) {
                try {
                    cachedData = parser.parse(cached.json);
                    flight.fallback = cached;
                    memory.put(key, cached);
                } catch (Exception error) {
                    memory.remove(key);
                    try { store.remove(key); }
                    catch (IOException | RuntimeException removeError) {
                        Log.w("M3Data", "CACHE_REMOVE_FAILED " + key);
                    }
                    Log.w("M3Data", "CACHE_INVALID " + key);
                }
            }
            long age = cached == null ? -1 : clock.getAsLong() - cached.fetchedAt;
            if (cachedData != null && ((!force && age >= 0 && age < CACHE_TTL_MS)
                    || !online.getAsBoolean())) {
                complete(jobKey, flight, cachedData, true);
                return;
            }
            api.get(path, parser, result -> worker.execute(() -> {
                CacheRecord entry = new CacheRecord(result.json, clock.getAsLong());
                memory.put(key, entry);
                try { store.write(key, entry); }
                catch (IOException | RuntimeException error) {
                    // Mang thanh cong van tra du lieu, nhung phai kiem tra loi luu cache.
                    Log.w("M3Data", "CACHE_WRITE_FAILED " + key);
                }
                complete(jobKey, flight, result.data, false);
            }), error -> worker.execute(() -> fail(key, jobKey, flight, error)));
        });
    }

    private <T> void fail(String key, String jobKey, Flight<T> flight, VolleyError error) {
        // Instance khac co the da ghi cache trong khi request dang chay.
        if (flight.fallback == null) {
            try { flight.fallback = store.read(key); }
            catch (IOException | RuntimeException readError) {
                Log.w("M3Data", "CACHE_READ_FAILED " + key);
            }
        }
        if (flight.fallback != null) {
            try {
                T data = flight.parser.parse(flight.fallback.json);
                complete(jobKey, flight, data, true);
                return;
            } catch (Exception ignored) { /* Khong dua cache hong len UI. */ }
        }
        flights.remove(jobKey);
        final String message = errorMessage(error);
        Log.d("M3Data", "ERROR " + jobKey + " " + error.getClass().getSimpleName());
        main.post(() -> {
            for (Callback<T> cb : flight.callbacks) cb.onError(message);
        });
    }

    private <T> void complete(String jobKey, Flight<T> flight, T data, boolean cached) {
        flights.remove(jobKey);
        Log.d("M3Data", (cached ? "CACHE " : "NETWORK ") + jobKey);
        main.post(() -> {
            for (Callback<T> cb : flight.callbacks) cb.onSuccess(data, cached);
        });
    }

    private String errorMessage(VolleyError error) {
        if (error instanceof TimeoutError) return context.getString(R.string.m3_timeout);
        if (error instanceof ParseError) return context.getString(R.string.m3_bad_data);
        if (error.networkResponse != null) {
            return context.getString(R.string.m3_server_error, error.networkResponse.statusCode);
        }
        if (error instanceof NoConnectionError) return context.getString(R.string.m3_no_network);
        return context.getString(R.string.m3_request_failed);
    }
}
