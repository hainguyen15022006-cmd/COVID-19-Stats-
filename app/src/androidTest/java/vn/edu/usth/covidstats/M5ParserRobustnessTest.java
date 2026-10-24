package vn.edu.usth.covidstats;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import vn.edu.usth.covidstats.data.JsonParser;
import vn.edu.usth.covidstats.model.CountryStats;
import vn.edu.usth.covidstats.model.HistoricalData;

/** T04: du lieu thieu/khong hop le, so lon, ngay nguon va snapshot bat bien. */
@RunWith(AndroidJUnit4.class)
public class M5ParserRobustnessTest {
    @Test public void nullMetadataLargeNumbersAndNonFiniteRatesAreSafe() throws Exception {
        List<CountryStats> countries = JsonParser.parseCountries("[null,17,"
                + "{\"country\":\"A\",\"cases\":3000000000,\"countryInfo\":null},"
                + "{\"country\":\"B\",\"deaths\":null,\"countryInfo\":{\"iso2\":\"vn\"},"
                + "\"casesPerOneMillion\":\"NaN\",\"deathsPerOneMillion\":\"Infinity\"}]");
        assertEquals(2, countries.size());
        assertEquals(3_000_000_000L, countries.get(0).cases);
        assertEquals("", countries.get(0).iso2);
        assertEquals("VN", countries.get(1).iso2);
        assertEquals(0, countries.get(1).deaths);
        assertEquals(0, countries.get(1).casesPerOneMillion, 0);
        assertEquals(0, countries.get(1).deathsPerOneMillion, 0);
    }

    @Test public void historyPreservesReportedZeroAndNegativeButSkipsMissingPoints() throws Exception {
        HistoricalData data = JsonParser.parseHistory("all", -1,
                "{\"cases\":{\"3/6/23\":null,\"3/7/23\":10,\"3/8/23\":0,\"3/9/23\":-1},"
                + "\"deaths\":{\"3/7/23\":1,\"3/9/23\":2}}");
        assertEquals(3, data.dates.size());
        assertEquals(Long.valueOf(0), data.cases.get("3/8/23"));
        assertEquals(Long.valueOf(-1), data.cases.get("3/9/23"));
        assertFalse(data.deaths.containsKey("3/8/23"));
        assertTrue(data.recovered.isEmpty());
        assertEquals("3/9/23", data.lastDataDate);
    }

    @Test public void callbacksCannotMutateSharedParserSnapshots() throws Exception {
        List<CountryStats> countries = JsonParser.parseCountries("[{\"country\":\"A\"}]");
        assertThrows(UnsupportedOperationException.class, countries::clear);
        HistoricalData data = JsonParser.parseHistory("all", -1,
                "{\"cases\":{\"3/9/23\":1},\"deaths\":{\"3/9/23\":0}}");
        assertThrows(UnsupportedOperationException.class, data.dates::clear);
        assertThrows(UnsupportedOperationException.class, () -> data.cases.put("3/9/23", 999L));
        assertEquals(Long.valueOf(1), data.cases.get("3/9/23"));
    }

    @Test public void duplicateCalendarDatesInvalidDatesAndWrongRootAreRejected() {
        assertThrows(JSONException.class, () -> JsonParser.parseHistory("all", -1,
                "{\"cases\":{\"3/9/23\":1,\"03/09/2023\":2},\"deaths\":{}}"));
        assertThrows(JSONException.class, () -> JsonParser.parseHistory("all", -1,
                "{\"cases\":{\"2/30/20\":1},\"deaths\":{}}"));
        assertThrows(JSONException.class, () -> JsonParser.parseHistory("VN", -1, "[]"));
    }
}
