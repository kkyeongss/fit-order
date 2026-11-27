package kr.fitorder.module.uniform.loader;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 원본 문자열 값 해석. 해석할 수 없으면 빈 값을 돌려주고, 플래그는 호출한 쪽이 단다. */
public final class RecordParser {

    private static final Pattern COMPACT_DATE = Pattern.compile("(\\d{4})(\\d{2})(\\d{2})");
    private static final Pattern SEPARATED_DATE = Pattern.compile("(\\d{4})[-./](\\d{1,2})[-./](\\d{1,2})\\.?");
    private static final Pattern DECIMAL_ZERO = Pattern.compile("(\\d+)\\.0+");

    private RecordParser() {
    }

    /** 2024-04-01, 2024/4/1, 2024.04.01, 20240401, 뒤에 시각이 붙은 값을 받는다. */
    public static Optional<LocalDate> parseDate(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String value = raw.strip().split("[ T]")[0];
        Matcher m = COMPACT_DATE.matcher(value);
        if (!m.matches()) {
            m = SEPARATED_DATE.matcher(value);
            if (!m.matches()) {
                return Optional.empty();
            }
        }
        try {
            return Optional.of(LocalDate.of(
                    Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))));
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }

    /** 1,200 / 10 / 10.0 을 받는다. 음수·소수는 해석하지 않는다. */
    public static Optional<Integer> parseQuantity(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        try {
            BigDecimal value = new BigDecimal(raw.strip().replace(",", ""));
            if (value.signum() < 0) {
                return Optional.empty();
            }
            return Optional.of(value.intValueExact());
        } catch (NumberFormatException | ArithmeticException e) {
            return Optional.empty();
        }
    }

    /** 사이즈 코드 정리. 엑셀을 거치며 생긴 95.0 은 95 로 본다. */
    public static String normalizeSize(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.strip();
        if (value.isEmpty()) {
            return null;
        }
        Matcher m = DECIMAL_ZERO.matcher(value);
        return m.matches() ? m.group(1) : value;
    }

    /** 품목명 공백 정리. */
    public static String normalizeName(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.strip().replaceAll("\\s+", " ");
        return value.isEmpty() ? null : value;
    }
}
