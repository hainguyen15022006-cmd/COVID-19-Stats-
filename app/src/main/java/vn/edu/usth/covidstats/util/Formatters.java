package vn.edu.usth.covidstats.util;

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Định dạng số và ngày theo ngôn ngữ (Anh / Việt).
 *
 * Lớp thuần Java: không dùng Context, không dùng giao diện, không chạy luồng nền.
 * Không dùng java.time vì minSdk 24 chưa có (java.time cần API 26).
 */
public final class Formatters {

    /** Ký hiệu hiện thay cho số 0 hoặc dữ liệu thiếu (T07). */
    public static final String DASH = "–";

    /** Định dạng khóa ngày trong "timeline" của disease.sh, ví dụ "1/22/20" hay "3/9/23". */
    private static final String TIMELINE_PATTERN = "M/d/yy";

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    private Formatters() {
        // Không cho tạo đối tượng: chỉ dùng hàm static.
    }

    // ---------------------------------------------------------------- Số

    /** 1234567 -> "1,234,567" (tiếng Anh) hoặc "1.234.567" (tiếng Việt). */
    public static String number(long value, Locale locale) {
        NumberFormat nf = NumberFormat.getIntegerInstance(safe(locale));
        nf.setGroupingUsed(true);
        return nf.format(value);
    }

    /** Giống number(), nhưng 0 hoặc số âm (dữ liệu lỗi) thì trả về "–". */
    public static String numberOrDash(long value, Locale locale) {
        return value <= 0 ? DASH : number(value, locale);
    }

    /** Bản nhận Long, để model trả về null vẫn không crash. */
    public static String numberOrDash(Long value, Locale locale) {
        return value == null ? DASH : numberOrDash(value.longValue(), locale);
    }

    /** 1.2345 -> "1.23%" (Anh) hoặc "1,23%" (Việt). Tối đa 2 chữ số thập phân. */
    public static String percent(double value, Locale locale) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return DASH;
        }
        NumberFormat nf = NumberFormat.getNumberInstance(safe(locale));
        nf.setMinimumFractionDigits(0);
        nf.setMaximumFractionDigits(2);
        return nf.format(value) + "%";
    }

    /** Tỷ lệ part/total tính bằng %, total = 0 thì trả về "–". */
    public static String ratio(long part, long total, Locale locale) {
        if (total <= 0 || part < 0) {
            return DASH;
        }
        return percent(part * 100.0 / total, locale);
    }

    // ---------------------------------------------------------------- Ngày

    /** Mốc thời gian (mili giây) -> "24/10/2026" (Việt) hoặc "Oct 24, 2026" (Anh). */
    public static String date(long epochMillis, Locale locale) {
        Locale l = safe(locale);
        SimpleDateFormat f = new SimpleDateFormat(datePattern(l), l);
        f.setTimeZone(UTC);
        return f.format(new Date(epochMillis));
    }

    /**
     * Đọc khóa ngày của timeline ("3/9/23") thành mili giây (UTC).
     * Trả về -1 nếu chuỗi rỗng hoặc sai định dạng, không ném lỗi.
     */
    public static long parseTimelineKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            return -1;
        }
        SimpleDateFormat f = new SimpleDateFormat(TIMELINE_PATTERN, Locale.US);
        f.setTimeZone(UTC);
        f.setLenient(false);
        try {
            return f.parse(key.trim()).getTime();
        } catch (ParseException e) {
            return -1;
        }
    }

    /** "3/9/23" -> "09/03/2023" (Việt) hoặc "Mar 9, 2023" (Anh). Sai định dạng thì "–". */
    public static String timelineDate(String key, Locale locale) {
        long millis = parseTimelineKey(key);
        return millis < 0 ? DASH : date(millis, locale);
    }

    /**
     * Tìm ngày mới nhất trong các khóa timeline.
     * Dùng cho dòng "Số liệu tính đến…" (T01): lấy từ timeline, không lấy trường "updated".
     * Trả về null nếu không có khóa hợp lệ nào.
     */
    public static String latestTimelineKey(Iterable<String> keys) {
        if (keys == null) {
            return null;
        }
        String best = null;
        long bestMillis = -1;
        for (String k : keys) {
            long m = parseTimelineKey(k);
            if (m > bestMillis) {
                bestMillis = m;
                best = k;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- Phụ trợ

    private static boolean isVietnamese(Locale locale) {
        return "vi".equals(locale.getLanguage());
    }

    private static String datePattern(Locale locale) {
        return isVietnamese(locale) ? "dd/MM/yyyy" : "MMM d, yyyy";
    }

    private static Locale safe(Locale locale) {
        return locale == null ? Locale.US : locale;
    }
}
