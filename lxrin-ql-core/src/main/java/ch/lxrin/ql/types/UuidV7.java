package ch.lxrin.ql.types;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Generates time-ordered UUIDs (version 7, RFC 9562). New keys sort roughly
 * by creation time, which keeps B-tree indexes compact.
 *
 * <p>Within one millisecond the values of a JVM are strictly increasing
 * (a 12-bit counter, method 1 of RFC 9562 section 6.2).</p>
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static long lastMillis;
    private static int counter;

    private UuidV7() {}

    /** Returns a new version 7 UUID. */
    public static UUID generate() {
        long millis;
        int seq;
        synchronized (UuidV7.class) {
            long now = System.currentTimeMillis();
            if (now > lastMillis) {
                lastMillis = now;
                counter = RANDOM.nextInt(1 << 11);          // random start leaves room to count up
            } else if (++counter > 0xFFF) {
                lastMillis++;                               // counter overflow: borrow the next millisecond
                counter = 0;
            }
            millis = lastMillis;
            seq = counter;
        }
        long msb = (millis & 0xFFFF_FFFF_FFFFL) << 16 | 0x7000L | seq;
        long lsb = RANDOM.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL | 0x8000_0000_0000_0000L;
        return new UUID(msb, lsb);
    }

    /** Returns the creation time (milliseconds since the epoch) stored in a version 7 UUID. */
    public static long timestamp(UUID uuid) {
        if (uuid.version() != 7) throw new IllegalArgumentException("not a version 7 UUID: " + uuid);
        return uuid.getMostSignificantBits() >>> 16;
    }
}
