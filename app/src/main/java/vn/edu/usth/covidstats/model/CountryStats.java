package vn.edu.usth.covidstats.model;

/** Model bat bien. Ma ISO thieu duoc giu la chuoi rong, khong tu bia ma. */
public final class CountryStats {
    public final String country;
    public final String iso2;
    public final String iso3;
    public final String flag;
    public final String continent;
    public final long cases;
    public final long deaths;
    public final long recovered;
    public final long active;
    public final long critical;
    public final long tests;
    public final long population;
    public final double casesPerOneMillion;
    public final double deathsPerOneMillion;
    public final long updated;

    public CountryStats(String country, String iso2, String iso3, String flag,
                        String continent, long cases, long deaths, long recovered,
                        long active, long critical, long tests, long population,
                        double casesPerOneMillion, double deathsPerOneMillion,
                        long updated) {
        this.country = country;
        this.iso2 = iso2;
        this.iso3 = iso3;
        this.flag = flag;
        this.continent = continent;
        this.cases = cases;
        this.deaths = deaths;
        this.recovered = recovered;
        this.active = active;
        this.critical = critical;
        this.tests = tests;
        this.population = population;
        this.casesPerOneMillion = casesPerOneMillion;
        this.deathsPerOneMillion = deathsPerOneMillion;
        this.updated = updated;
    }
}
