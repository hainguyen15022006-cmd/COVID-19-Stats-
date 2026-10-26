package vn.edu.usth.covidstats.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.NetworkImageView;
import com.github.mikephil.charting.charts.LineChart;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import vn.edu.usth.covidstats.CovidApp;
import vn.edu.usth.covidstats.R;
import vn.edu.usth.covidstats.data.Callback;
import vn.edu.usth.covidstats.data.CovidRepository;
import vn.edu.usth.covidstats.data.WatchlistStore;
import vn.edu.usth.covidstats.model.CountryStats;
import vn.edu.usth.covidstats.model.HistoricalData;
import vn.edu.usth.covidstats.util.ChartHelper;

/** Displays one country's current totals and its reusable historical chart. */
public final class CountryDetailActivity extends AppCompatActivity {
    public static final String EXTRA_COUNTRY_ISO2 = "country_iso2";

    private static final String STATE_ISO2 = "detail.iso2";
    private static final String STATE_PERIOD = "detail.period";
    private static final String STATE_COUNTRY = "detail.country";
    private static final String STATE_HISTORY = "detail.history";
    private static final String STATE_COUNTRY_ERROR = "detail.country_error";
    private static final String STATE_HISTORY_ERROR = "detail.history_error";

    private View content;
    private View errorPanel;
    private ProgressBar loading;
    private TextView errorMessage;
    private Button errorRetry;
    private NetworkImageView flag;
    private TextView countryName;
    private ImageButton watchlistButton;
    private TextView cases;
    private TextView deaths;
    private TextView recovered;
    private TextView active;
    private TextView tests;
    private TextView population;
    private TextView casesPerMillion;
    private TextView deathsPerMillion;
    private CircularProgressIndicator recoveryRing;
    private TextView recoveryPercent;
    private TextView recoveryCaption;
    private TextView historyDate;
    private TextView historyMessage;
    private ProgressBar historyLoading;
    private Button historyRetry;
    private RadioGroup periodGroup;
    private LineChart historyChart;

    private CovidRepository repository;
    private ImageLoader imageLoader;
    private WatchlistStore watchlistStore;
    private LoadState state;
    private boolean invalidCountryCode;
    private boolean watchlistReady;
    private boolean starred;
    private int watchlistGeneration;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Dark design: light status and navigation bar icons.
        EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT),
                SystemBarStyle.dark(Color.TRANSPARENT));
        setContentView(R.layout.activity_country_detail);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.detail_root), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        bindViews();

        CovidApp app = (CovidApp) getApplication();
        repository = app.getRepository();
        imageLoader = app.getImageLoader();
        watchlistStore = WatchlistStore.getInstance(this);

        Object retained = getLastCustomNonConfigurationInstance();
        if (retained instanceof LoadState) {
            state = (LoadState) retained;
        } else if (savedInstanceState != null) {
            state = restoreState(savedInstanceState);
        } else {
            state = new LoadState(normalizeCountryCode(
                    getIntent().getStringExtra(EXTRA_COUNTRY_ISO2)));
        }

        invalidCountryCode = !isValidCountryCode(state.iso2);
        wireActions();
        state.attach(this);
        if (!invalidCountryCode) {
            state.loadMissing(repository);
            loadWatchlistState();
        }
        render();
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        return state;
    }

    @Override
    protected void onDestroy() {
        watchlistGeneration++;
        if (state != null) {
            state.detach(this);
        }
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_ISO2, state.iso2);
        outState.putInt(STATE_PERIOD, state.selectedDays);
        outState.putString(STATE_COUNTRY_ERROR, state.countryError);
        outState.putString(STATE_HISTORY_ERROR, state.historyError);
        putCountry(outState, state.country);
        putHistory(outState, state.history);
    }

    private void bindViews() {
        content = findViewById(R.id.detail_content);
        errorPanel = findViewById(R.id.detail_error_panel);
        loading = findViewById(R.id.detail_loading);
        errorMessage = findViewById(R.id.detail_error_message);
        errorRetry = findViewById(R.id.detail_error_retry);
        flag = findViewById(R.id.detail_flag);
        countryName = findViewById(R.id.detail_country_name);
        watchlistButton = findViewById(R.id.detail_watchlist_button);
        cases = findViewById(R.id.detail_cases_value);
        deaths = findViewById(R.id.detail_deaths_value);
        recovered = findViewById(R.id.detail_recovered_value);
        active = findViewById(R.id.detail_active_value);
        tests = findViewById(R.id.detail_tests_value);
        population = findViewById(R.id.detail_population_value);
        casesPerMillion = findViewById(R.id.detail_cases_per_million_value);
        deathsPerMillion = findViewById(R.id.detail_deaths_per_million_value);
        recoveryRing = findViewById(R.id.detail_recovery_ring);
        recoveryPercent = findViewById(R.id.detail_recovery_percent);
        recoveryCaption = findViewById(R.id.detail_recovery_caption);
        historyDate = findViewById(R.id.detail_history_date);
        historyMessage = findViewById(R.id.detail_history_message);
        historyLoading = findViewById(R.id.detail_history_loading);
        historyRetry = findViewById(R.id.detail_history_retry);
        periodGroup = findViewById(R.id.detail_period_group);
        historyChart = findViewById(R.id.detail_history_chart);
    }

    private void wireActions() {
        findViewById(R.id.detail_back).setOnClickListener(view -> finish());
        watchlistButton.setOnClickListener(view -> toggleWatchlist());
        errorRetry.setOnClickListener(view -> state.retryCountry(repository));
        historyRetry.setOnClickListener(view -> state.retryHistory(repository));
        periodGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int days = daysForButton(checkedId);
            if (state.selectedDays != days) {
                state.selectedDays = days;
                drawHistory();
            }
        });
    }

    private void render() {
        if (invalidCountryCode) {
            loading.setVisibility(View.GONE);
            content.setVisibility(View.GONE);
            errorPanel.setVisibility(View.VISIBLE);
            errorMessage.setText(R.string.m1_invalid_country_code);
            errorRetry.setVisibility(View.GONE);
            return;
        }

        if (state.country == null) {
            content.setVisibility(View.GONE);
            if (state.countryLoading) {
                loading.setVisibility(View.VISIBLE);
                errorPanel.setVisibility(View.GONE);
            } else {
                loading.setVisibility(View.GONE);
                errorPanel.setVisibility(View.VISIBLE);
                errorMessage.setText(nonEmpty(state.countryError,
                        getString(R.string.detail_country_error)));
                errorRetry.setVisibility(View.VISIBLE);
            }
            return;
        }

        loading.setVisibility(View.GONE);
        errorPanel.setVisibility(View.GONE);
        content.setVisibility(View.VISIBLE);
        showCountry(state.country);
        showHistoryState();
    }

    private void showCountry(CountryStats country) {
        countryName.setText(country.country);
        updateWatchlistButton();
        flag.setContentDescription(getString(R.string.detail_country_flag, country.country));
        flag.setImageUrl(country.flag, imageLoader);
        cases.setText(formatLong(country.cases));
        deaths.setText(formatLong(country.deaths));
        recovered.setText(formatLong(country.recovered));
        active.setText(formatLong(country.active));
        tests.setText(formatLong(country.tests));
        population.setText(formatLong(country.population));
        casesPerMillion.setText(formatDouble(country.casesPerOneMillion));
        deathsPerMillion.setText(formatDouble(country.deathsPerOneMillion));
        showRecoveryRate(country);
    }

    /** Ring and caption: share of cases that recovered. The source often has no recovered count. */
    private void showRecoveryRate(CountryStats country) {
        if (country.cases <= 0 || country.recovered <= 0) {
            recoveryRing.setProgress(0);
            recoveryPercent.setText(R.string.detail_value_unavailable);
            recoveryCaption.setText(R.string.detail_recovery_unavailable);
            return;
        }
        int percent = (int) Math.min(100, Math.round(country.recovered * 100.0 / country.cases));
        recoveryRing.setProgress(percent);
        recoveryPercent.setText(getString(R.string.detail_recovery_percent, percent));
        recoveryCaption.setText(getString(R.string.detail_recovery_caption, percent));
    }

    private void loadWatchlistState() {
        watchlistReady = false;
        updateWatchlistButton();
        int request = ++watchlistGeneration;
        watchlistStore.getCodes(new Callback<Set<String>>() {
            @Override
            public void onSuccess(Set<String> codes, boolean fromCache) {
                if (request != watchlistGeneration || isFinishing() || isDestroyed()) return;
                starred = codes.contains(state.iso2);
                watchlistReady = true;
                updateWatchlistButton();
            }

            @Override
            public void onError(String message) {
                if (request != watchlistGeneration || isFinishing() || isDestroyed()) return;
                watchlistReady = false;
                updateWatchlistButton();
                showWatchlistError(message);
            }
        });
    }

    private void toggleWatchlist() {
        if (!watchlistReady) return;
        watchlistButton.setEnabled(false);
        int request = ++watchlistGeneration;
        watchlistStore.toggle(state.iso2, new Callback<Set<String>>() {
            @Override
            public void onSuccess(Set<String> codes, boolean fromCache) {
                if (request != watchlistGeneration || isFinishing() || isDestroyed()) return;
                starred = codes.contains(state.iso2);
                watchlistReady = true;
                updateWatchlistButton();
            }

            @Override
            public void onError(String message) {
                if (request != watchlistGeneration || isFinishing() || isDestroyed()) return;
                // Keep the previous icon because the persistent update did not succeed.
                watchlistReady = true;
                updateWatchlistButton();
                showWatchlistError(message);
            }
        });
    }

    private void updateWatchlistButton() {
        if (watchlistButton == null) return;
        watchlistButton.setEnabled(watchlistReady && !invalidCountryCode);
        watchlistButton.setImageResource(starred
                ? R.drawable.ic_star_filled_24 : R.drawable.ic_star_outline_24);
        String country = state != null && state.country != null
                ? state.country.country : state == null ? "" : state.iso2;
        watchlistButton.setContentDescription(getString(starred
                ? R.string.detail_watchlist_remove : R.string.detail_watchlist_add, country));
    }

    private void showWatchlistError(String message) {
        Toast.makeText(this, nonEmpty(message, getString(R.string.detail_watchlist_error)),
                Toast.LENGTH_LONG).show();
    }

    private void showHistoryState() {
        selectPeriodButton(state.selectedDays);
        if (state.history != null && !state.history.dates.isEmpty()) {
            historyLoading.setVisibility(View.GONE);
            historyMessage.setVisibility(View.GONE);
            historyRetry.setVisibility(View.GONE);
            historyChart.setVisibility(View.VISIBLE);
            historyDate.setVisibility(View.VISIBLE);
            historyDate.setText(getString(R.string.detail_history_date,
                    formatHistoryDate(state.history.lastDataDate)));
            drawHistory();
        } else if (state.historyLoading) {
            historyLoading.setVisibility(View.VISIBLE);
            historyMessage.setVisibility(View.VISIBLE);
            historyMessage.setText(R.string.detail_history_loading);
            historyRetry.setVisibility(View.GONE);
            historyChart.setVisibility(View.GONE);
            historyDate.setVisibility(View.GONE);
        } else {
            historyLoading.setVisibility(View.GONE);
            historyMessage.setVisibility(View.VISIBLE);
            historyMessage.setText(nonEmpty(state.historyError,
                    getString(R.string.detail_history_empty)));
            historyRetry.setVisibility(View.VISIBLE);
            historyChart.setVisibility(View.GONE);
            historyDate.setVisibility(View.GONE);
        }
    }

    private void drawHistory() {
        if (state.history != null && !state.history.dates.isEmpty()) {
            ChartHelper.drawLine(historyChart, state.history, state.selectedDays);
        }
    }

    /**
     * T07: a zero field shows the dash. The source stopped counting in 2023, so a zero
     * usually means "not reported" rather than a true zero - detail_unavailable_explanation
     * states this on screen for both meanings.
     */
    private String formatLong(long value) {
        return value <= 0 ? getString(R.string.detail_value_unavailable)
                : NumberFormat.getIntegerInstance(Locale.getDefault()).format(value);
    }

    private String formatDouble(double value) {
        if (value <= 0 || Double.isInfinite(value) || Double.isNaN(value)) {
            return getString(R.string.detail_value_unavailable);
        }
        NumberFormat format = NumberFormat.getNumberInstance(Locale.getDefault());
        format.setMaximumFractionDigits(2);
        return format.format(value);
    }

    private String formatHistoryDate(String source) {
        String[] parts = source == null ? new String[0] : source.split("/", -1);
        if (parts.length != 3) {
            return source;
        }
        try {
            int year = Integer.parseInt(parts[2]);
            if (year < 100) {
                year += 2000;
            }
            GregorianCalendar calendar = new GregorianCalendar();
            calendar.clear();
            calendar.setLenient(false);
            calendar.set(year, Integer.parseInt(parts[0]) - 1,
                    Integer.parseInt(parts[1]));
            return DateFormat.getDateInstance(DateFormat.LONG, Locale.getDefault())
                    .format(calendar.getTime());
        } catch (IllegalArgumentException exception) {
            return source;
        }
    }

    private void selectPeriodButton(int days) {
        int id = buttonForDays(days);
        if (periodGroup.getCheckedRadioButtonId() != id) {
            periodGroup.check(id);
        }
    }

    private static int daysForButton(int id) {
        if (id == R.id.detail_period_30) {
            return 30;
        }
        if (id == R.id.detail_period_90) {
            return 90;
        }
        if (id == R.id.detail_period_365) {
            return 365;
        }
        return CovidRepository.ALL_DAYS;
    }

    private static int buttonForDays(int days) {
        if (days == 30) {
            return R.id.detail_period_30;
        }
        if (days == 90) {
            return R.id.detail_period_90;
        }
        if (days == 365) {
            return R.id.detail_period_365;
        }
        return R.id.detail_period_all;
    }

    private static String normalizeCountryCode(String input) {
        return input == null ? "" : input.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean isValidCountryCode(String code) {
        return code != null && code.matches("[A-Z]{2}");
    }

    private static String nonEmpty(String value, String fallback) {
        return TextUtils.isEmpty(value) ? fallback : value;
    }

    private static void putCountry(Bundle bundle, CountryStats country) {
        if (country == null) {
            return;
        }
        Bundle value = new Bundle();
        value.putString("country", country.country);
        value.putString("iso2", country.iso2);
        value.putString("iso3", country.iso3);
        value.putString("flag", country.flag);
        value.putString("continent", country.continent);
        value.putLong("cases", country.cases);
        value.putLong("deaths", country.deaths);
        value.putLong("recovered", country.recovered);
        value.putLong("active", country.active);
        value.putLong("critical", country.critical);
        value.putLong("tests", country.tests);
        value.putLong("population", country.population);
        value.putDouble("cases_per_million", country.casesPerOneMillion);
        value.putDouble("deaths_per_million", country.deathsPerOneMillion);
        value.putLong("updated", country.updated);
        bundle.putBundle(STATE_COUNTRY, value);
    }

    private static CountryStats getCountry(Bundle state) {
        Bundle value = state.getBundle(STATE_COUNTRY);
        if (value == null) {
            return null;
        }
        return new CountryStats(value.getString("country", ""),
                value.getString("iso2", ""), value.getString("iso3", ""),
                value.getString("flag", ""), value.getString("continent", ""),
                value.getLong("cases"), value.getLong("deaths"),
                value.getLong("recovered"), value.getLong("active"),
                value.getLong("critical"), value.getLong("tests"),
                value.getLong("population"), value.getDouble("cases_per_million"),
                value.getDouble("deaths_per_million"), value.getLong("updated"));
    }

    private static void putHistory(Bundle bundle, HistoricalData history) {
        if (history == null) {
            return;
        }
        Bundle value = new Bundle();
        value.putString("iso2_or_all", history.iso2OrAll);
        value.putString("country", history.country);
        value.putStringArrayList("dates", new ArrayList<>(history.dates));
        value.putSerializable("cases", new LinkedHashMap<>(history.cases));
        value.putSerializable("deaths", new LinkedHashMap<>(history.deaths));
        value.putSerializable("recovered", new LinkedHashMap<>(history.recovered));
        bundle.putBundle(STATE_HISTORY, value);
    }

    @SuppressWarnings("unchecked")
    private static HistoricalData getHistory(Bundle state) {
        Bundle value = state.getBundle(STATE_HISTORY);
        if (value == null) {
            return null;
        }
        ArrayList<String> dates = value.getStringArrayList("dates");
        Object cases = value.getSerializable("cases");
        Object deaths = value.getSerializable("deaths");
        Object recovered = value.getSerializable("recovered");
        if (dates == null || !(cases instanceof Map) || !(deaths instanceof Map)
                || !(recovered instanceof Map)) {
            return null;
        }
        return new HistoricalData(value.getString("iso2_or_all", ""),
                value.getString("country", ""), dates,
                (Map<String, Long>) cases, (Map<String, Long>) deaths,
                (Map<String, Long>) recovered);
    }

    private static LoadState restoreState(Bundle bundle) {
        LoadState restored = new LoadState(bundle.getString(STATE_ISO2, ""));
        restored.selectedDays = bundle.getInt(STATE_PERIOD, CovidRepository.ALL_DAYS);
        restored.country = getCountry(bundle);
        restored.history = getHistory(bundle);
        restored.countryError = bundle.getString(STATE_COUNTRY_ERROR);
        restored.historyError = bundle.getString(STATE_HISTORY_ERROR);
        return restored;
    }

    private static final class LoadState {
        final String iso2;
        CountryStats country;
        HistoricalData history;
        String countryError;
        String historyError;
        boolean countryLoading;
        boolean historyLoading;
        int selectedDays = CovidRepository.ALL_DAYS;
        private CountryDetailActivity activity;

        LoadState(String iso2) {
            this.iso2 = iso2;
        }

        void attach(CountryDetailActivity activity) {
            this.activity = activity;
        }

        void detach(CountryDetailActivity activity) {
            if (this.activity == activity) {
                this.activity = null;
            }
        }

        void loadMissing(CovidRepository repository) {
            if (country == null && !countryLoading && TextUtils.isEmpty(countryError)) {
                loadCountry(repository);
            }
            if (history == null && !historyLoading && TextUtils.isEmpty(historyError)) {
                loadHistory(repository);
            }
        }

        void retryCountry(CovidRepository repository) {
            countryError = null;
            loadCountry(repository);
        }

        void retryHistory(CovidRepository repository) {
            historyError = null;
            loadHistory(repository);
        }

        private void loadCountry(CovidRepository repository) {
            if (countryLoading) {
                return;
            }
            countryLoading = true;
            dispatch();
            repository.getCountry(iso2, new Callback<CountryStats>() {
                @Override
                public void onSuccess(CountryStats data, boolean fromCache) {
                    countryLoading = false;
                    country = data;
                    countryError = null;
                    dispatch();
                }

                @Override
                public void onError(String message) {
                    countryLoading = false;
                    countryError = message;
                    dispatch();
                }
            });
        }

        private void loadHistory(CovidRepository repository) {
            if (historyLoading) {
                return;
            }
            historyLoading = true;
            dispatch();
            repository.getHistory(iso2, CovidRepository.ALL_DAYS,
                    new Callback<HistoricalData>() {
                        @Override
                        public void onSuccess(HistoricalData data, boolean fromCache) {
                            historyLoading = false;
                            history = data;
                            historyError = null;
                            dispatch();
                        }

                        @Override
                        public void onError(String message) {
                            historyLoading = false;
                            historyError = message;
                            dispatch();
                        }
                    });
        }

        private void dispatch() {
            CountryDetailActivity current = activity;
            if (current != null && !current.isFinishing() && !current.isDestroyed()) {
                current.render();
            }
        }
    }
}
