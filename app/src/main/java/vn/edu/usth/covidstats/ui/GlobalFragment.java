package vn.edu.usth.covidstats.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Calendar;

import vn.edu.usth.covidstats.CovidApp;
import vn.edu.usth.covidstats.R;
import vn.edu.usth.covidstats.data.Callback;
import vn.edu.usth.covidstats.data.CovidRepository;
import vn.edu.usth.covidstats.model.GlobalStats;
import vn.edu.usth.covidstats.model.HistoricalData;

/** Global summary and history chart, loaded through the shared repository. */
public class GlobalFragment extends Fragment {
    private static final int CASE_COLOR = Color.rgb(33, 150, 243);   // xanh dương
    private static final int DEATH_COLOR = Color.rgb(211, 47, 47);   // đỏ
        /** Giữ dữ liệu qua xoay màn hình và qua việc vuốt tab. */
    public static class GlobalModel extends ViewModel {
        final MutableLiveData<GlobalStats> stats = new MutableLiveData<>();
        final MutableLiveData<HistoricalData> history = new MutableLiveData<>();
        final MutableLiveData<String> statsError = new MutableLiveData<>();
        final MutableLiveData<String> historyError = new MutableLiveData<>();
        final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    }

    private GlobalModel model;
    private SwipeRefreshLayout refreshLayout;
    private TextView status;
    private TextView error;
    private Button retry;
    private View statsContainer;
    private TextView cases;
    private TextView deaths;
    private TextView recovered;
    private TextView active;
    private TextView critical;
    private TextView tests;
    private TextView dataDate;
    private LineChart chart;
    private TextView empty;
    

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_global, container, false);
        refreshLayout = view.findViewById(R.id.global_refresh);
        status = view.findViewById(R.id.global_status);
        error = view.findViewById(R.id.global_error);
        retry = view.findViewById(R.id.global_retry);
        statsContainer = view.findViewById(R.id.global_stats);
        cases = view.findViewById(R.id.global_cases);
        deaths = view.findViewById(R.id.global_deaths);
        recovered = view.findViewById(R.id.global_recovered);
        active = view.findViewById(R.id.global_active);
        critical = view.findViewById(R.id.global_critical);
        tests = view.findViewById(R.id.global_tests);
        chart = view.findViewById(R.id.global_chart);
        empty = view.findViewById(R.id.global_empty);
        dataDate = view.findViewById(R.id.global_data_date);

        refreshLayout.setOnRefreshListener(() -> load(true));
        retry.setOnClickListener(ignored -> load(true));
        configureChart();
        return view;
    }

        @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        model = new ViewModelProvider(this).get(GlobalModel.class);

        model.loading.observe(getViewLifecycleOwner(), isLoading -> {
            boolean busy = Boolean.TRUE.equals(isLoading);
            refreshLayout.setRefreshing(busy);
            if (busy && model.stats.getValue() == null) {
                status.setText(R.string.global_loading);
                status.setVisibility(View.VISIBLE);
            }
        });

        model.stats.observe(getViewLifecycleOwner(), data -> {
            if (data == null) return;
            renderStats(data);
            statsContainer.setVisibility(View.VISIBLE);
            status.setVisibility(View.GONE);
            error.setVisibility(View.GONE);
            retry.setVisibility(View.GONE);
        });

        model.statsError.observe(getViewLifecycleOwner(), message -> {
            if (message == null) {
                error.setVisibility(View.GONE);
                retry.setVisibility(View.GONE);
                return;
            }
            error.setText(message);
            error.setVisibility(View.VISIBLE);
            retry.setVisibility(View.VISIBLE);
            status.setVisibility(View.GONE);
            if (model.stats.getValue() == null) {
                statsContainer.setVisibility(View.GONE);
            }
        });

        model.history.observe(getViewLifecycleOwner(), data -> {
            if (data != null) renderHistory(data);
        });

        model.historyError.observe(getViewLifecycleOwner(), message -> {
            if (message == null) return;
            chart.clear();
            dataDate.setVisibility(View.GONE);
            empty.setText(message);
            empty.setVisibility(View.VISIBLE);
        });

        // Chỉ tải lần đầu. Xoay màn hình hoặc quay lại tab thì dùng dữ liệu trong model.
        if (model.stats.getValue() == null
                && !Boolean.TRUE.equals(model.loading.getValue())) {
            load(false);
        }
    }
        /** Entry point used by the toolbar refresh action. */
    public void refresh() {
        if (isAdded() && model != null) {
            load(true);
        }
    }

    private void load(boolean forceRefresh) {
        if (!isAdded() || model == null) return;
        if (Boolean.TRUE.equals(model.loading.getValue())) return; // đang tải thì không gửi thêm
        android.util.Log.d("GlobalFragment", "load forceRefresh=" + forceRefresh);

        final GlobalModel viewModel = model;
        final CovidRepository repository = ((CovidApp) requireActivity().getApplication())
                .getRepository();
        viewModel.loading.setValue(true);
        viewModel.statsError.setValue(null);
        repository.getGlobal(forceRefresh, new Callback<GlobalStats>() {
            @Override
            public void onSuccess(GlobalStats data, boolean fromCache) {
                viewModel.stats.setValue(data);
                viewModel.loading.setValue(false);
                loadHistory(repository, viewModel);
            }

            @Override
            public void onError(String message) {
                viewModel.statsError.setValue(message);
                viewModel.loading.setValue(false);
            }
        });
    }

           

    private void renderStats(GlobalStats stats) {
        NumberFormat numbers = NumberFormat.getIntegerInstance(Locale.getDefault());
        cases.setText(numbers.format(stats.cases));
        deaths.setText(numbers.format(stats.deaths));
        recovered.setText(numbers.format(stats.recovered));
        active.setText(numbers.format(stats.active));
        critical.setText(numbers.format(stats.critical));
        tests.setText(numbers.format(stats.tests));
    }

        private void configureChart() {
        chart.getDescription().setEnabled(false);
        chart.setNoDataText(getString(R.string.global_history_empty));
        chart.setExtraBottomOffset(8f);

        Legend legend = chart.getLegend();
        legend.setEnabled(true);
        legend.setTextSize(13f);
        legend.setForm(Legend.LegendForm.LINE);
        legend.setWordWrapEnabled(true);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(5, false);
        xAxis.setDrawGridLines(false);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int index = Math.round(value);
                if (index < 0 || index >= chartDates.size()) {
                    return "";
                }
                    return formatDate(chartDates.get(index), "MMMyy");
            }
        });

        YAxis left = chart.getAxisLeft();
        left.setAxisMinimum(0f);
        left.setTextColor(CASE_COLOR);
        left.setValueFormatter(compactFormatter());

        YAxis right = chart.getAxisRight();
        right.setEnabled(true);
        right.setAxisMinimum(0f);
        right.setDrawGridLines(false);
        right.setTextColor(DEATH_COLOR);
        right.setValueFormatter(compactFormatter());
    }

        /** 700000000 -> "700M" (en) hoặc "700 tr" (vi). */
    private ValueFormatter compactFormatter() {
        return new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                NumberFormat numbers = NumberFormat.getIntegerInstance(Locale.getDefault());
                if (value >= 1_000_000f) {
                    return numbers.format(Math.round(value / 1_000_000f))
                            + chart.getContext().getString(R.string.global_unit_million);
                }
                return numbers.format(Math.round(value));
            }
        };
    }

        private String formatDate(String apiDate, String skeleton) {
        String[] parts = apiDate.split("/");
        if (parts.length != 3) {
            return apiDate;
        }
        try {
            int year = Integer.parseInt(parts[2]);
            if (year < 100) {
                year += 2000;
            }
            Calendar calendar = Calendar.getInstance();
            calendar.clear();
            calendar.set(year, Integer.parseInt(parts[0]) - 1, Integer.parseInt(parts[1]));
            String pattern = android.text.format.DateFormat
                    .getBestDateTimePattern(Locale.getDefault(), skeleton);
            return new SimpleDateFormat(pattern, Locale.getDefault())
                    .format(calendar.getTime());
        } catch (NumberFormatException e) {
            return apiDate;
        }
    }

    private final List<String> chartDates = new ArrayList<>();

    private void loadHistory(CovidRepository repository, GlobalModel viewModel) {
        repository.getHistory("all", CovidRepository.ALL_DAYS, new Callback<HistoricalData>() {
            @Override
            public void onSuccess(HistoricalData data, boolean fromCache) {
                viewModel.historyError.setValue(null);
                viewModel.history.setValue(data);
            }

            @Override
            public void onError(String message) {
                viewModel.historyError.setValue(message);
            }
        });
    }

    private void renderHistory(HistoricalData data) {
        chartDates.clear();
        chartDates.addAll(data.dates);
        List<Entry> caseEntries = new ArrayList<>();
        List<Entry> deathEntries = new ArrayList<>();
        for (int i = 0; i < data.dates.size(); i++) {
            String date = data.dates.get(i);
            Long caseCount = data.cases.get(date);
            Long deathCount = data.deaths.get(date);
            if (caseCount != null) caseEntries.add(new Entry(i, caseCount.floatValue()));
            if (deathCount != null) deathEntries.add(new Entry(i, deathCount.floatValue()));
        }
        if (caseEntries.isEmpty() && deathEntries.isEmpty()) {
            chart.clear();
            empty.setVisibility(View.VISIBLE);
            return;
        }

        LineDataSet caseSet = new LineDataSet(caseEntries,
                getString(R.string.global_chart_cases_axis_left));
        caseSet.setColor(CASE_COLOR);
        caseSet.setLineWidth(2f);
        caseSet.setDrawCircles(false);
        caseSet.setDrawValues(false);
        LineDataSet deathSet = new LineDataSet(deathEntries,
                getString(R.string.global_chart_deaths_axis_right));
        deathSet.setColor(DEATH_COLOR);
        deathSet.setLineWidth(2f);
        deathSet.setDrawCircles(false);
        deathSet.setDrawValues(false);
        deathSet.setAxisDependency(YAxis.AxisDependency.RIGHT);
        chart.setData(new LineData(caseSet, deathSet));
        chart.invalidate();
        if (!data.lastDataDate.isEmpty()) {
            dataDate.setText(getString(R.string.global_data_as_of,
                    formatDate(data.lastDataDate, "yMMMd")));
            dataDate.setVisibility(View.VISIBLE);
        }
        empty.setVisibility(View.GONE);
    }
}
