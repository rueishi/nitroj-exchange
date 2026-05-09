package ig.rueishi.nitroj.exchange.execution;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Startup-loaded per-venue maker/taker fee table for venue-indifferent routing.
 *
 * <p>The parser is intentionally cold-path and small: production routing reads
 * primitive array slots only. Fees are basis points scaled as signed longs so
 * tests can cover zero, high, and asymmetric maker/taker cases without boxed
 * maps or String work on the execution hot path.</p>
 */
public final class FeeSchedule {
    private static final int MAX_VENUE_ID = 255;
    private final long[] makerFeeBps = new long[MAX_VENUE_ID + 1];
    private final long[] takerFeeBps = new long[MAX_VENUE_ID + 1];
    private final boolean[] configured = new boolean[MAX_VENUE_ID + 1];

    public static FeeSchedule defaults() {
        final FeeSchedule schedule = new FeeSchedule();
        schedule.set(1, 0L, 6L);
        schedule.set(2, 0L, 10L);
        return schedule;
    }

    public static FeeSchedule load(final Path path) throws IOException {
        final FeeSchedule schedule = new FeeSchedule();
        final List<String> lines = Files.readAllLines(path);
        int venueId = 0;
        for (int i = 0; i < lines.size(); i++) {
            final String line = stripComment(lines.get(i)).trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                venueId = parseVenueHeader(line, i + 1);
                continue;
            }
            if (venueId == 0) {
                throw new IllegalArgumentException("fee entry before venue header at line " + (i + 1));
            }
            final int equals = line.indexOf('=');
            if (equals <= 0 || equals == line.length() - 1) {
                throw new IllegalArgumentException("malformed fee entry at line " + (i + 1));
            }
            final String key = line.substring(0, equals).trim();
            final long value = Long.parseLong(line.substring(equals + 1).trim());
            if (value < 0L) {
                throw new IllegalArgumentException("fee must be non-negative at line " + (i + 1));
            }
            if ("makerFeeBps".equals(key)) {
                schedule.makerFeeBps[venueId] = value;
                schedule.configured[venueId] = true;
            } else if ("takerFeeBps".equals(key)) {
                schedule.takerFeeBps[venueId] = value;
                schedule.configured[venueId] = true;
            } else {
                throw new IllegalArgumentException("unknown fee key '" + key + "' at line " + (i + 1));
            }
        }
        return schedule;
    }

    public void set(final int venueId, final long makerFeeBps, final long takerFeeBps) {
        validateVenueId(venueId);
        if (makerFeeBps < 0L || takerFeeBps < 0L) {
            throw new IllegalArgumentException("fees must be non-negative");
        }
        this.makerFeeBps[venueId] = makerFeeBps;
        this.takerFeeBps[venueId] = takerFeeBps;
        configured[venueId] = true;
    }

    public long makerFeeBps(final int venueId) {
        return venueId > 0 && venueId <= MAX_VENUE_ID ? makerFeeBps[venueId] : 0L;
    }

    public long takerFeeBps(final int venueId) {
        return venueId > 0 && venueId <= MAX_VENUE_ID ? takerFeeBps[venueId] : 0L;
    }

    public boolean configured(final int venueId) {
        return venueId > 0 && venueId <= MAX_VENUE_ID && configured[venueId];
    }

    private static String stripComment(final String line) {
        final int index = line.indexOf('#');
        return index < 0 ? line : line.substring(0, index);
    }

    private static int parseVenueHeader(final String line, final int lineNumber) {
        if (!line.startsWith("[venue.") || !line.endsWith("]")) {
            throw new IllegalArgumentException("malformed venue fee header at line " + lineNumber);
        }
        final int venueId = Integer.parseInt(line.substring("[venue.".length(), line.length() - 1));
        validateVenueId(venueId);
        return venueId;
    }

    private static void validateVenueId(final int venueId) {
        if (venueId <= 0 || venueId > MAX_VENUE_ID) {
            throw new IllegalArgumentException("venue id out of range: " + venueId);
        }
    }
}
