package vn.edu.usth.covidstats.data;

import java.util.List;

import vn.edu.usth.covidstats.model.CountryStats;
import vn.edu.usth.covidstats.model.GlobalStats;
import vn.edu.usth.covidstats.model.HistoricalData;

/** Hop dong dung chung cho FakeRepository va Repository mang sau nay. */
public interface CovidRepository {
    int ALL_DAYS = -1;

    void getGlobal(boolean forceRefresh, Callback<GlobalStats> cb);
    void getCountries(boolean forceRefresh, Callback<List<CountryStats>> cb);
    void getCountry(String iso2, Callback<CountryStats> cb);
    void getHistory(String iso2OrAll, int days, Callback<HistoricalData> cb);
}
