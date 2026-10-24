package vn.edu.usth.covidstats.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import vn.edu.usth.covidstats.R;

/** Mot store cho ca app. Doc/commit SharedPreferences tren worker, ket qua ve main. */
public final class WatchlistStore {
    private static volatile WatchlistStore instance;
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            task -> new Thread(task, "covid-watchlist"));
    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();

    public interface Listener {
        void onWatchlistChanged(Set<String> codes);
    }

    private WatchlistStore(Context context) {
        this.context = context.getApplicationContext();
    }

    public static WatchlistStore getInstance(Context context) {
        if (instance == null) {
            synchronized (WatchlistStore.class) {
                if (instance == null) instance = new WatchlistStore(context);
            }
        }
        return instance;
    }

    public void addListener(Listener listener) { listeners.add(Objects.requireNonNull(listener)); }
    public void removeListener(Listener listener) { listeners.remove(listener); }

    public void getCodes(Callback<Set<String>> callback) {
        submit(null, null, false, callback);
    }

    public void setStarred(String iso2, boolean starred, Callback<Set<String>> callback) {
        submit(iso2, starred, false, callback);
    }

    public void toggle(String iso2, Callback<Set<String>> callback) {
        submit(iso2, null, true, callback);
    }

    private void submit(String input, Boolean starred, boolean toggle, Callback<Set<String>> callback) {
        Objects.requireNonNull(callback, "callback");
        worker.execute(() -> {
            try {
                // Khoi tao SharedPreferences cung o worker: lan doc dau co the cho disk.
                SharedPreferences prefs = context.getSharedPreferences("covid_watchlist", Context.MODE_PRIVATE);
                Set<String> codes = new TreeSet<>();
                for (String stored : prefs.getStringSet("iso2_codes", Collections.emptySet())) {
                    if (stored != null && stored.matches("[A-Z]{2}")) codes.add(stored);
                }
                Set<String> before = new HashSet<>(codes);
                boolean changed = false;
                if (starred != null || toggle) {
                    String code = input == null ? "" : input.trim().toUpperCase(Locale.ROOT);
                    if (!code.matches("[A-Z]{2}")) {
                        main.post(() -> callback.onError(context.getString(R.string.m1_invalid_country_code)));
                        return;
                    }
                    boolean add = toggle ? !codes.contains(code) : starred;
                    changed = add ? codes.add(code) : codes.remove(code);
                    // commit tren worker: chi bao thanh cong sau khi da ghi ben vung.
                    if (changed && !prefs.edit().putStringSet("iso2_codes", new HashSet<>(codes)).commit()) {
                        // commit false van co the doi ban trong RAM: khoi phuc snapshot cu.
                        prefs.edit().putStringSet("iso2_codes", before).commit();
                        throw new IllegalStateException("Watchlist commit failed");
                    }
                }
                Set<String> snapshot = Collections.unmodifiableSet(new TreeSet<>(codes));
                boolean notify = changed;
                main.post(() -> {
                    if (notify) for (Listener listener : listeners) listener.onWatchlistChanged(snapshot);
                    callback.onSuccess(snapshot, false);
                });
            } catch (RuntimeException e) {
                Log.e("WatchlistStore", "Cannot read or save watchlist", e);
                main.post(() -> callback.onError(context.getString(R.string.watchlist_storage_error)));
            }
        });
    }
}
