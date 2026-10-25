package vn.edu.usth.covidstats.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Tên nước và mã lấy đúng như disease.sh trả về ("USA", "UK", "S. Korea"...). */
public class CountryNameMatcherTest {

    private static boolean vn(String q) {
        return CountryNameMatcher.matches(q, "Vietnam", "VN", "VNM");
    }

    private static boolean usa(String q) {
        return CountryNameMatcher.matches(q, "USA", "US", "USA");
    }

    private static boolean uk(String q) {
        return CountryNameMatcher.matches(q, "UK", "GB", "GBR");
    }

    @Test
    public void t05RequiredQueries() {
        assertTrue(vn("viet"));
        assertTrue(vn("vn"));
        assertTrue(usa("usa"));
        assertTrue(usa("us"));
        assertTrue(uk("uk"));
    }

    @Test
    public void ignoresCaseAccentsAndSpaces() {
        assertTrue(vn("VIET"));
        assertTrue(vn("Việt Nam"));
        assertTrue(vn("viet nam"));
        assertTrue(usa("UsA"));
        assertTrue(CountryNameMatcher.matches("cote", "Côte d'Ivoire", "CI", "CIV"));
    }

    @Test
    public void vietnameseAliases() {
        assertTrue(usa("Mỹ"));
        assertTrue(usa("hoa kỳ"));
        assertTrue(uk("Anh"));
        assertTrue(CountryNameMatcher.matches("trung quoc", "China", "CN", "CHN"));
        assertTrue(CountryNameMatcher.matches("trung", "China", "CN", "CHN"));
        assertTrue(CountryNameMatcher.matches("korea", "S. Korea", "KR", "KOR"));
    }

    @Test
    public void exactCodeRanksAboveContains() {
        int usaScore = CountryNameMatcher.score("us", "USA", "US", "USA");
        int australiaScore = CountryNameMatcher.score("us", "Australia", "AU", "AUS");
        assertEquals(0, usaScore);
        assertTrue(australiaScore > usaScore);
    }

    @Test
    public void noMatch() {
        assertFalse(vn("japan"));
        assertFalse(usa("viet"));
        assertFalse(CountryNameMatcher.matches("xyz", "France", "FR", "FRA"));
    }

    @Test
    public void emptyQueryAndNullFieldsDoNotCrash() {
        assertTrue(vn(""));
        assertTrue(vn(null));
        assertTrue(CountryNameMatcher.matches("dia", "Diamond Princess", null, null));
        assertFalse(CountryNameMatcher.matches("vn", null, null, null));
    }
}
