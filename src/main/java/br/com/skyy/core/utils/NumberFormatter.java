package br.com.skyy.core.utils;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Utility to format numbers and durations.
 *
 * format(1500000)      → "1.5M"
 * format(1000)         → "1.000"
 * formatTime(90000L)   → "1m 30s"   (input: millis)
 */
public final class NumberFormatter {

    private static final DecimalFormat GROUPED;
    private static final DecimalFormat SHORT_DECIMAL;

    static {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.forLanguageTag("pt-BR"));
        sym.setGroupingSeparator('.');
        sym.setDecimalSeparator(',');
        GROUPED       = new DecimalFormat("#,###", sym);
        SHORT_DECIMAL = new DecimalFormat("#,##0.0#", sym);
    }

    private NumberFormatter() {}

    /**
     * Compact number format:
     * 999 → "999"
     * 1000 → "1.000"
     * 1500000 → "1.5M"
     * 2000000000 → "2B"
     */
    public static String format(double value) {
        if (value < 0) return "-" + format(-value);

        if (value >= 1_000_000_000_000.0) {
            double v = value / 1_000_000_000_000.0;
            return cleanCompact(v) + "T";
        }
        if (value >= 1_000_000_000.0) {
            double v = value / 1_000_000_000.0;
            return cleanCompact(v) + "B";
        }
        if (value >= 1_000_000.0) {
            double v = value / 1_000_000.0;
            return cleanCompact(v) + "M";
        }
        if (value >= 1_000.0) {
            // Use grouped thousands: 1.500
            return GROUPED.format((long) value);
        }
        return String.valueOf((long) value);
    }

    public static String format(long value) {
        return format((double) value);
    }

    /** Full decimal format with 2 decimal places: 1.500,50 */
    public static String formatFull(double value) {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.forLanguageTag("pt-BR"));
        sym.setGroupingSeparator('.');
        sym.setDecimalSeparator(',');
        DecimalFormat df = new DecimalFormat("#,##0.00", sym);
        return df.format(value);
    }

    /**
     * Formats milliseconds into human-readable time:
     * < 60s  → "45s"
     * < 60m  → "10m 30s"
     * < 24h  → "2h 10m 30s"
     * >= 24h → "2d 3h 10m 5s"
     */
    public static String formatTime(long millis) {
        if (millis < 0) millis = 0;
        long totalSeconds = millis / 1000L;

        long days    = totalSeconds / 86400L;
        long hours   = (totalSeconds % 86400L) / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        StringBuilder sb = new StringBuilder();
        if (days    > 0) sb.append(days).append("d ");
        if (hours   > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        sb.append(seconds).append("s");

        return sb.toString().trim();
    }

    /** Formats seconds (not millis) into HH:MM:SS or MM:SS */
    public static String formatTimeClock(long seconds) {
        long h = seconds / 3600L;
        long m = (seconds % 3600L) / 60L;
        long s = seconds % 60L;
        if (h > 0) return String.format("%02d:%02d:%02d", h, m, s);
        return String.format("%02d:%02d", m, s);
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    /** Returns compact decimal: strips trailing .0 */
    private static String cleanCompact(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        String formatted = SHORT_DECIMAL.format(value);
        // Strip trailing zeros after comma: "1,50" → "1,5"
        if (formatted.contains(",")) {
            formatted = formatted.replaceAll(",?0+$", "");
        }
        return formatted;
    }
}
