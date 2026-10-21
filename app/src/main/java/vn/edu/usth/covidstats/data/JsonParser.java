package vn.edu.usth.covidstats.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

import vn.edu.usth.covidstats.model.CountryStats;
import vn.edu.usth.covidstats.model.GlobalStats;
import vn.edu.usth.covidstats.model.HistoricalData;

/** Logic doc JSON, khong goi mang hay dung View. Nguoi goi chay tren worker thread. */
public final class JsonParser {
    private static final long DAY_MILLIS = 24L * 60 * 60 * 1000;

    private JsonParser() { }

    public static GlobalStats parseGlobal(String json) throws JSONException {
        JSONObject o = new JSONObject(json);
        return new GlobalStats(o.optLong("cases", 0), o.optLong("deaths", 0),
                o.optLong("recovered", 0), o.optLong("active", 0),
                o.optLong("critical", 0), o.optLong("tests", 0),
                o.optLong("population", 0), o.optLong("affectedCountries", 0),
                o.optLong("updated", 0));
    }

    public static CountryStats parseCountry(String json) throws JSONException {
        return country(new JSONObject(json));
    }

    public static List<CountryStats> parseCountries(String json) throws JSONException {
        JSONArray array = new JSONArray(json);
        List<CountryStats> countries = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object != null) {
                countries.add(country(object));
            }
        }
        return Collections.unmodifiableList(countries);
    }

    private static CountryStats country(JSONObject o) {
        JSONObject info = o.optJSONObject("countryInfo");
        return new CountryStats(text(o, "country"),
                text(info, "iso2").toUpperCase(Locale.ROOT),
                text(info, "iso3").toUpperCase(Locale.ROOT), text(info, "flag"),
                text(o, "continent"), o.optLong("cases", 0), o.optLong("deaths", 0),
                o.optLong("recovered", 0), o.optLong("active", 0),
                o.optLong("critical", 0), o.optLong("tests", 0),
                o.optLong("population", 0), finiteDouble(o, "casesPerOneMillion"),
                finiteDouble(o, "deathsPerOneMillion"), o.optLong("updated", 0));
    }

    public static HistoricalData parseHistory(String iso2OrAll, int days, String json)
            throws JSONException {
        if (days != CovidRepository.ALL_DAYS && days <= 0) {
            throw new IllegalArgumentException("days must be positive or ALL_DAYS");
        }
        JSONObject root = new JSONObject(json);
        boolean global = "all".equalsIgnoreCase(iso2OrAll);
        JSONObject timeline = global ? root : root.optJSONObject("timeline");
        if (timeline == null) {
            throw new JSONException("Expected an object named timeline");
        }
        JSONObject caseObject = timeline.optJSONObject("cases");
        JSONObject deathObject = timeline.optJSONObject("deaths");
        if (caseObject == null || deathObject == null) {
            throw new JSONException("Expected cases and deaths objects");
        }
        Map<String, Long> cases = series(caseObject);
        Map<String, Long> deaths = series(deathObject);
        Map<String, Long> recovered = series(timeline.optJSONObject("recovered"));

        // JSON object khong dam bao thu tu. Sap theo ngay UTC, khong sap theo chuoi.
        TreeMap<Long, String> ordered = new TreeMap<>();
        addDates(ordered, cases);
        addDates(ordered, deaths);
        addDates(ordered, recovered);
        long threshold = Long.MIN_VALUE;
        if (!ordered.isEmpty() && days != CovidRepository.ALL_DAYS) {
            threshold = ordered.lastKey() - (days - 1L) * DAY_MILLIS;
        }
        List<String> dates = new ArrayList<>();
        for (Map.Entry<Long, String> entry : ordered.entrySet()) {
            if (entry.getKey() >= threshold) {
                dates.add(entry.getValue());
            }
        }
        return new HistoricalData(global ? "all" : iso2OrAll.toUpperCase(Locale.ROOT),
                global ? "all" : text(root, "country"), dates,
                orderedValues(dates, cases), orderedValues(dates, deaths),
                orderedValues(dates, recovered));
    }

    private static Map<String, Long> series(JSONObject object) throws JSONException {
        Map<String, Long> values = new LinkedHashMap<>();
        if (object == null) {
            return values;
        }
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String date = keys.next();
            if (!object.isNull(date)) {
                Object value = object.get(date);
                try {
                    // Khong tu bien null hay gia tri loi thanh diem 0 tren bieu do.
                    values.put(date, Long.parseLong(String.valueOf(value)));
                } catch (NumberFormatException e) {
                    throw new JSONException("Invalid historical value at " + date);
                }
            }
        }
        return values;
    }

    private static void addDates(TreeMap<Long, String> dates, Map<String, Long> values)
            throws JSONException {
        for (String key : values.keySet()) {
            long timestamp = dateMillis(key);
            String previous = dates.put(timestamp, key);
            if (previous != null && !previous.equals(key)) {
                throw new JSONException("Duplicate calendar date: " + key);
            }
        }
    }

    private static long dateMillis(String key) throws JSONException {
        String[] parts = key.split("/", -1);
        if (parts.length != 3) {
            throw new JSONException("Invalid historical date: " + key);
        }
        try {
            int year = Integer.parseInt(parts[2]);
            if (parts[2].length() == 2) {
                year += 2000;
            } else if (parts[2].length() != 4) {
                throw new IllegalArgumentException("Invalid year");
            }
            GregorianCalendar calendar = new GregorianCalendar(
                    TimeZone.getTimeZone("UTC"), Locale.US);
            calendar.clear();
            calendar.setLenient(false);
            calendar.set(year, Integer.parseInt(parts[0]) - 1,
                    Integer.parseInt(parts[1]));
            return calendar.getTimeInMillis();
        } catch (IllegalArgumentException e) {
            throw new JSONException("Invalid historical date: " + key);
        }
    }

    private static Map<String, Long> orderedValues(List<String> dates,
                                                   Map<String, Long> source) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (String date : dates) {
            if (source.containsKey(date)) {
                result.put(date, source.get(date));
            }
        }
        return result;
    }

    private static String text(JSONObject object, String key) {
        if (object == null || object.isNull(key)) {
            return "";
        }
        return object.optString(key, "");
    }

    private static double finiteDouble(JSONObject object, String key) {
        double value = object.optDouble(key, 0);
        return Double.isNaN(value) || Double.isInfinite(value) ? 0 : value;
    }
}
