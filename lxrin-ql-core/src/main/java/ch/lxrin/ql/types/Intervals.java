package ch.lxrin.ql.types;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses PostgreSQL interval output (the default {@code intervalstyle = postgres})
 * into a {@link Duration}. Intervals with months or years have no fixed
 * length and are rejected.
 */
final class Intervals {

    private static final Pattern PART = Pattern.compile(
            "([+-]?\\d+) (years?|mons?|days?)|([+-])?(\\d+):(\\d{2}):(\\d{2})(?:\\.(\\d{1,9}))?");

    private Intervals() {}

    static Duration parse(String text) {
        String s = text.trim();
        if (s.startsWith("P") || s.startsWith("-P")) return Duration.parse(s);
        Duration result = Duration.ZERO;
        Matcher m = PART.matcher(s);
        int pos = 0;
        while (m.find()) {
            if (!s.substring(pos, m.start()).isBlank()) throw new IllegalArgumentException("unsupported interval: " + text);
            pos = m.end();
            if (m.group(1) != null) {
                long n = Long.parseLong(m.group(1));
                String unit = m.group(2);
                if (unit.startsWith("day")) {
                    result = result.plusDays(n);
                } else if (n != 0) {
                    throw new IllegalArgumentException("interval '" + text + "' has months or years and cannot be "
                            + "converted to a Duration; use SqlTypes.INTERVAL_TEXT instead");
                }
            } else {
                Duration time = Duration.ofHours(Long.parseLong(m.group(4)))
                        .plusMinutes(Long.parseLong(m.group(5)))
                        .plusSeconds(Long.parseLong(m.group(6)));
                if (m.group(7) != null) {
                    String nanos = (m.group(7) + "000000000").substring(0, 9);
                    time = time.plusNanos(Long.parseLong(nanos));
                }
                result = "-".equals(m.group(3)) ? result.minus(time) : result.plus(time);
            }
        }
        if (!s.substring(pos).isBlank()) throw new IllegalArgumentException("unsupported interval: " + text);
        return result;
    }
}
