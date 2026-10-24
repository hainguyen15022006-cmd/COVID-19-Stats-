package vn.edu.usth.covidstats.util;


import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

import vn.edu.usth.covidstats.R;
import vn.edu.usth.covidstats.data.CovidRepository;
import vn.edu.usth.covidstats.model.HistoricalData;

/** Draws the reusable cases/deaths history chart without owning any screen state. */
public final class ChartHelper {
    private static final Map<LineChart, RenderKey> LAST_RENDER = new WeakHashMap<>();

    private ChartHelper() { }

    /** Draws all available history. This is the shared entry point used by other screens. */
    public static void drawLine(LineChart chart, HistoricalData data) {
        drawLine(chart, data, CovidRepository.ALL_DAYS);
    }

    /** Draws the last {@code days} dates, or all dates when {@code days == ALL_DAYS}. */
    public static void drawLine(LineChart chart, HistoricalData data, int days) {
        if (chart == null) {
            return;
        }
        int normalizedDays = normalizeDays(days);
        String localeTag = Locale.getDefault().toLanguageTag();
        RenderKey previous = LAST_RENDER.get(chart);
        if (previous != null && previous.matches(data, normalizedDays, localeTag)) {
            return;
        }
        LAST_RENDER.put(chart, new RenderKey(data, normalizedDays, localeTag));

        configureChart(chart);
        if (data == null || data.dates == null || data.dates.isEmpty()) {
            showEmpty(chart);
            return;
        }

        int start = normalizedDays == CovidRepository.ALL_DAYS
                ? 0 : Math.max(0, data.dates.size() - normalizedDays);
        List<String> labels = new ArrayList<>(data.dates.size() - start);
        List<Entry> caseEntries = new ArrayList<>();
        List<Entry> deathEntries = new ArrayList<>();
        for (int sourceIndex = start; sourceIndex < data.dates.size(); sourceIndex++) {
            String date = data.dates.get(sourceIndex);
            int chartIndex = sourceIndex - start;
            labels.add(formatDateLabel(date));
            addEntry(caseEntries, data.cases, date, chartIndex);
            addEntry(deathEntries, data.deaths, date, chartIndex);
        }

        if (caseEntries.isEmpty() && deathEntries.isEmpty()) {
            showEmpty(chart);
            return;
        }

        LineData lineData = new LineData();
        if (!caseEntries.isEmpty()) {
            lineData.addDataSet(createDataSet(caseEntries,
                    chart.getContext().getString(R.string.detail_chart_cases),
                    ContextCompat.getColor(chart.getContext(), R.color.chart_cases)));
        }
        if (!deathEntries.isEmpty()) {
            lineData.addDataSet(createDataSet(deathEntries,
                    chart.getContext().getString(R.string.detail_chart_deaths),
                    ContextCompat.getColor(chart.getContext(), R.color.chart_deaths)));
        }

        XAxis xAxis = chart.getXAxis();
        xAxis.setValueFormatter(new DateAxisFormatter(labels));
        xAxis.setAxisMinimum(0f);
        xAxis.setAxisMaximum(Math.max(0, labels.size() - 1));
        if (labels.size() > 1) {
            xAxis.setLabelCount(Math.min(6, labels.size()), false);
        }
        chart.setData(lineData);
        chart.notifyDataSetChanged();
        chart.invalidate();
    }

    private static int normalizeDays(int days) {
        return days > 0 ? days : CovidRepository.ALL_DAYS;
    }

    private static void configureChart(LineChart chart) {
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);
        chart.setNoDataText(chart.getContext().getString(R.string.detail_history_empty));
        chart.getAxisRight().setEnabled(false);
        chart.getAxisLeft().setAxisMinimum(0f);
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setGranularityEnabled(true);
        xAxis.setAvoidFirstLastClipping(true);
    }

    private static void addEntry(List<Entry> entries, Map<String, Long> values,
                                 String date, int chartIndex) {
        if (values == null) {
            return;
        }
        Long value = values.get(date);
        if (value != null && value >= 0) {
            entries.add(new Entry(chartIndex, value.floatValue()));
        }
    }

    private static LineDataSet createDataSet(List<Entry> entries, String label, int color) {
        LineDataSet set = new LineDataSet(entries, label);
        set.setColor(color);
        set.setLineWidth(2f);
        set.setDrawCircles(false);
        set.setDrawValues(false);
        set.setMode(LineDataSet.Mode.LINEAR);
        set.setHighlightEnabled(true);
        return set;
    }

    private static void showEmpty(LineChart chart) {
        chart.clear();
        chart.invalidate();
    }

    private static String formatDateLabel(String source) {
        if (source == null) {
            return "";
        }
        String[] parts = source.split("/", -1);
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
            return DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
                    .format(calendar.getTime());
        } catch (IllegalArgumentException exception) {
            return source;
        }
    }

    private static final class DateAxisFormatter extends ValueFormatter {
        private final List<String> labels;

        DateAxisFormatter(List<String> labels) {
            this.labels = labels;
        }

        @Override
        public String getFormattedValue(float value) {
            int index = Math.round(value);
            return index >= 0 && index < labels.size() ? labels.get(index) : "";
        }
    }

    private static final class RenderKey {
        private final HistoricalData data;
        private final int days;
        private final String localeTag;

        RenderKey(HistoricalData data, int days, String localeTag) {
            this.data = data;
            this.days = days;
            this.localeTag = localeTag;
        }

        boolean matches(HistoricalData otherData, int otherDays, String otherLocaleTag) {
            return data == otherData && days == otherDays && localeTag.equals(otherLocaleTag);
        }
    }
}
