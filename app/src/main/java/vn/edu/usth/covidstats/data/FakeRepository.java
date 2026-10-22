package vn.edu.usth.covidstats.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import vn.edu.usth.covidstats.R;
import vn.edu.usth.covidstats.model.CountryStats;
import vn.edu.usth.covidstats.model.GlobalStats;
import vn.edu.usth.covidstats.model.HistoricalData;

/** Nguon du lieu mau M1: doc file va parse o worker, callback ve main thread. */
public final class FakeRepository implements CovidRepository {
    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            task -> new Thread(task, "covid-samples"));

    public FakeRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public void getGlobal(boolean forceRefresh, Callback<GlobalStats> cb) {
        submit(() -> JsonParser.parseGlobal(read(R.raw.global_stats)), cb);
    }

    @Override
    public void getCountries(boolean forceRefresh, Callback<List<CountryStats>> cb) {
        submit(() -> JsonParser.parseCountries(read(R.raw.countries)), cb);
    }

    @Override
    public void getCountry(String iso2, Callback<CountryStats> cb) {
        submit(() -> {
            String code = countryCode(iso2);
            if ("VN".equals(code)) {
                return JsonParser.parseCountry(read(R.raw.country_vn));
            }
            for (CountryStats country : JsonParser.parseCountries(read(R.raw.countries))) {
                if (code.equals(country.iso2)) {
                    return country;
                }
            }
            throw new SampleException(context.getString(R.string.m1_country_not_found, code));
        }, cb);
    }

    @Override
    public void getHistory(String iso2OrAll, int days, Callback<HistoricalData> cb) {
        submit(() -> {
            if (days != ALL_DAYS && days <= 0) {
                throw new SampleException(context.getString(R.string.m1_invalid_history_days));
            }
            String input = iso2OrAll == null ? "" : iso2OrAll.trim();
            if ("all".equalsIgnoreCase(input)) {
                return JsonParser.parseHistory("all", days, read(R.raw.history_all));
            }
            String code = countryCode(input);
            if (!"VN".equals(code)) {
                // Chi co mau lich su VN: tra loi ro rang, khong gan du lieu VN cho nuoc khac.
                throw new SampleException(context.getString(R.string.m1_history_unavailable, code));
            }
            return JsonParser.parseHistory(code, days, read(R.raw.history_vn));
        }, cb);
    }

    private String countryCode(String input) throws SampleException {
        String code = input == null ? "" : input.trim().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z]{2}")) {
            throw new SampleException(context.getString(R.string.m1_invalid_country_code));
        }
        return code;
    }

    private String read(int resourceId) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getResources().openRawResource(resourceId), StandardCharsets.UTF_8))) {
            StringBuilder json = new StringBuilder();
            char[] buffer = new char[8192];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                json.append(buffer, 0, count);
            }
            return json.toString();
        }
    }

    private <T> void submit(SampleTask<T> task, Callback<T> cb) {
        Objects.requireNonNull(cb, "callback");
        worker.execute(() -> {
            try {
                T data = task.load();
                // Du lieu dong goi san khong phai runtime cache cua CacheManager.
                mainHandler.post(() -> cb.onSuccess(data, false));
            } catch (SampleException e) {
                mainHandler.post(() -> cb.onError(e.getMessage()));
            } catch (IOException | JSONException | RuntimeException e) {
                Log.e("FakeRepository", "Cannot load a bundled JSON sample", e);
                String message = context.getString(R.string.m1_sample_read_error);
                mainHandler.post(() -> cb.onError(message));
            }
        });
    }

    private interface SampleTask<T> {
        T load() throws IOException, JSONException, SampleException;
    }

    private static final class SampleException extends Exception {
        SampleException(String message) {
            super(message);
        }
    }
}
