package vn.edu.usth.covidstats.util;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Khớp chuỗi người dùng gõ với một nước (T05).
 *
 * Gõ "viet", "vn", "usa", "us", "uk", "Việt Nam", "my" (Mỹ)... đều ra đúng nước,
 * không phân biệt hoa thường, không phân biệt dấu tiếng Việt.
 *
 * Cách dùng trong danh sách quốc gia:
 *   int s = CountryNameMatcher.score(query, c.getCountry(), c.getIso2(), c.getIso3());
 *   if (s >= 0) giữ lại nước này, sắp xếp theo s tăng dần (nhỏ = khớp tốt hơn).
 *
 * Lớp thuần Java, không đụng giao diện hay mạng.
 */
public final class CountryNameMatcher {

    /** Không khớp. */
    public static final int NO_MATCH = -1;

    // Điểm khớp: số nhỏ = khớp tốt hơn, nằm trên đầu danh sách.
    static final int EXACT = 0;        // trùng mã iso2/iso3 hoặc trùng nguyên tên
    static final int ALIAS = 1;        // trùng tên gọi khác ("my" -> US, "anh" -> GB)
    static final int PREFIX = 2;       // tên bắt đầu bằng chuỗi gõ ("viet" -> Vietnam)
    static final int WORD_PREFIX = 3;  // một từ trong tên bắt đầu bằng chuỗi gõ ("korea" -> S. Korea)
    static final int ALIAS_PREFIX = 4; // tên gọi khác bắt đầu bằng chuỗi gõ ("trung" -> China)
    static final int CONTAINS = 5;     // tên chứa chuỗi gõ ở giữa

    /** Tên gọi khác (đã chuẩn hóa) -> mã iso2. Chỉ thêm những tên disease.sh không tự có. */
    private static final Map<String, String> ALIASES = new HashMap<>();

    static {
        alias("US", "my", "hoaky", "america", "unitedstates", "unitedstatesofamerica");
        alias("GB", "anh", "vuongquocanh", "england", "britain", "greatbritain", "unitedkingdom");
        alias("VN", "vietnam");
        alias("CN", "trungquoc");
        alias("JP", "nhatban");
        alias("KR", "hanquoc", "southkorea", "korea");
        alias("KP", "trieutien", "northkorea");
        alias("FR", "phap");
        alias("DE", "duc");
        alias("RU", "nga");
        alias("TH", "thailan", "thailand");
        alias("KH", "campuchia");
        alias("LA", "lao", "laos");
        alias("AU", "uc");
        alias("IN", "ando");
        alias("IT", "y");
        alias("ES", "taybannha");
        alias("CA", "canada");
        alias("SG", "singapore");
        alias("MY", "malaysia", "malai");
        alias("ID", "indonesia", "indo");
        alias("PH", "philippines", "philippin");
    }

    private CountryNameMatcher() {
    }

    /** true nếu nước này khớp chuỗi gõ. Chuỗi gõ rỗng thì mọi nước đều khớp. */
    public static boolean matches(String query, String name, String iso2, String iso3) {
        return score(query, name, iso2, iso3) != NO_MATCH;
    }

    /**
     * Điểm khớp từ 0 (tốt nhất) đến 5, hoặc NO_MATCH (-1).
     * Chuỗi gõ rỗng trả về 0 để không lọc mất nước nào.
     */
    public static int score(String query, String name, String iso2, String iso3) {
        String q = normalize(query);
        if (q.isEmpty()) {
            return EXACT;
        }
        String n = normalize(name);
        String i2 = normalize(iso2);
        String i3 = normalize(iso3);

        if (q.equals(i2) || q.equals(i3) || q.equals(n)) {
            return EXACT;
        }

        String code = iso2 == null ? "" : iso2.trim().toUpperCase(Locale.ROOT);
        String aliasCode = ALIASES.get(q);
        if (aliasCode != null && aliasCode.equals(code)) {
            return ALIAS;
        }

        if (n.startsWith(q)) {
            return PREFIX;
        }
        if (anyWordStartsWith(name, q)) {
            return WORD_PREFIX;
        }
        // Gợi ý theo tên gọi khác khi gõ dở, ví dụ "trung" -> trungquoc. Cần ít nhất 3 ký tự
        // để "a" hay "an" không kéo ra cả loạt nước.
        if (q.length() >= 3 && aliasPrefixMatches(q, code)) {
            return ALIAS_PREFIX;
        }
        if (n.contains(q)) {
            return CONTAINS;
        }
        return NO_MATCH;
    }

    /**
     * Chuẩn hóa để so sánh: bỏ dấu tiếng Việt, đ -> d, chữ thường,
     * bỏ mọi ký tự không phải chữ hoặc số. "Việt Nam" -> "vietnam", "S. Korea" -> "skorea".
     */
    public static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String noMarks = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(noMarks.length());
        for (int i = 0; i < noMarks.length(); i++) {
            char c = noMarks.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- Phụ trợ

    private static void alias(String iso2, String... names) {
        for (String n : names) {
            ALIASES.put(n, iso2);
        }
    }

    private static boolean anyWordStartsWith(String name, String q) {
        if (name == null) {
            return false;
        }
        String[] words = name.split("[^\\p{L}\\p{N}]+");
        for (String w : words) {
            if (normalize(w).startsWith(q)) {
                return true;
            }
        }
        return false;
    }

    private static boolean aliasPrefixMatches(String q, String code) {
        if (code.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, String> e : ALIASES.entrySet()) {
            if (e.getValue().equals(code) && e.getKey().startsWith(q)) {
                return true;
            }
        }
        return false;
    }
}
