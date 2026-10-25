package vn.edu.usth.covidstats.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Locale;

import org.junit.Test;

public class FormattersTest {

    private static final Locale EN = Locale.US;
    private static final Locale VI = new Locale("vi", "VN");

    @Test
    public void numberHasGroupingSeparators() {
        assertEquals("1,234,567", Formatters.number(1234567, EN));
        assertEquals("1.234.567", Formatters.number(1234567, VI));
        assertEquals("0", Formatters.number(0, EN));
    }

    @Test
    public void zeroOrNullShowsDash() {
        assertEquals("–", Formatters.numberOrDash(0L, EN));
        assertEquals("–", Formatters.numberOrDash((Long) null, VI));
        assertEquals("704,753,890", Formatters.numberOrDash(704753890L, EN));
    }

    @Test
    public void percentAndRatio() {
        assertEquals("1.23%", Formatters.percent(1.2345, EN));
        assertEquals("1,23%", Formatters.percent(1.2345, VI));
        assertEquals("50%", Formatters.ratio(1, 2, EN));
        assertEquals("–", Formatters.ratio(5, 0, EN));
        assertEquals("–", Formatters.percent(Double.NaN, EN));
    }

    @Test
    public void timelineKeyParsesAndFormats() {
        assertEquals("09/03/2023", Formatters.timelineDate("3/9/23", VI));
        assertEquals("Mar 9, 2023", Formatters.timelineDate("3/9/23", EN));
        assertEquals("22/01/2020", Formatters.timelineDate("1/22/20", VI));
    }

    @Test
    public void badTimelineKeyDoesNotCrash() {
        assertEquals(-1, Formatters.parseTimelineKey(null));
        assertEquals(-1, Formatters.parseTimelineKey(""));
        assertEquals(-1, Formatters.parseTimelineKey("abc"));
        assertEquals(-1, Formatters.parseTimelineKey("13/40/23"));
        assertEquals("–", Formatters.timelineDate("abc", EN));
    }

    @Test
    public void latestTimelineKeyComparesDatesNotStrings() {
        // So chuỗi thì "9/1/22" > "10/1/22"; so ngày thì 10/1/22 mới hơn.
        assertEquals("3/9/23",
                Formatters.latestTimelineKey(Arrays.asList("9/1/22", "10/1/22", "3/9/23", "1/22/20")));
        assertEquals("10/1/22", Formatters.latestTimelineKey(Arrays.asList("9/1/22", "10/1/22")));
        assertNull(Formatters.latestTimelineKey(Arrays.asList("abc", "")));
        assertNull(Formatters.latestTimelineKey(null));
    }
}
