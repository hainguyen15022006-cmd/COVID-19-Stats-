package vn.edu.usth.covidstats.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Cac ngay da duoc sap xep theo lich; gia tri so la tong tich luy. */
public final class HistoricalData {
    public final String iso2OrAll;
    public final String country;
    public final List<String> dates;
    public final Map<String, Long> cases;
    public final Map<String, Long> deaths;
    public final Map<String, Long> recovered;
    public final String lastDataDate;

    public HistoricalData(String iso2OrAll, String country, List<String> dates,
                          Map<String, Long> cases, Map<String, Long> deaths,
                          Map<String, Long> recovered) {
        this.iso2OrAll = iso2OrAll;
        this.country = country;
        this.dates = Collections.unmodifiableList(new ArrayList<>(dates));
        this.cases = Collections.unmodifiableMap(new LinkedHashMap<>(cases));
        this.deaths = Collections.unmodifiableMap(new LinkedHashMap<>(deaths));
        this.recovered = Collections.unmodifiableMap(new LinkedHashMap<>(recovered));
        this.lastDataDate = dates.isEmpty() ? "" : dates.get(dates.size() - 1);
    }
}
