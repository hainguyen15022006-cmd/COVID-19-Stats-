package vn.edu.usth.covidstats.model;

/** So lieu tong hop. updated chi la thoi diem tai, khong phai ngay du lieu. */
public final class GlobalStats {
    public final long cases;
    public final long deaths;
    public final long recovered;
    public final long active;
    public final long critical;
    public final long tests;
    public final long population;
    public final long affectedCountries;
    public final long updated;

    public GlobalStats(long cases, long deaths, long recovered, long active,
                       long critical, long tests, long population,
                       long affectedCountries, long updated) {
        this.cases = cases;
        this.deaths = deaths;
        this.recovered = recovered;
        this.active = active;
        this.critical = critical;
        this.tests = tests;
        this.population = population;
        this.affectedCountries = affectedCountries;
        this.updated = updated;
    }
}
