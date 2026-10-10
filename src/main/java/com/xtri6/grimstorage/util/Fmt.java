package com.xtri6.grimstorage.util;

import java.util.Locale;

/** Number formatting shared by tooltips and screens. */
public final class Fmt {
    private Fmt() {}

    /** 1234 -> "1,234". */
    public static String big(long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    /** A fluid/chemical/energy/Source amount with its unit: "250 mB", "64 B", "1.2k B", "16M FE", "10k Source". */
    public static String amount(com.xtri6.grimstorage.storage.BulkKind kind, long n) {
        return switch (kind) {
            case FLUID, CHEMICAL -> n < 1000 ? n + " mB" : compact(n / 1000) + " B";
            case ENERGY -> compact(n) + " FE";
            case SOURCE -> compact(n) + " Source";
        };
    }

    /** Short amount for a terminal slot (no unit for fluids/chemicals below 1 B shows mB). */
    public static String shortAmount(com.xtri6.grimstorage.storage.BulkKind kind, long n) {
        return switch (kind) {
            case FLUID, CHEMICAL -> n < 1000 ? n + "m" : compact(n / 1000) + "B";
            default -> compact(n);
        };
    }

    /** Short form for item slots: 999, 1.2k, 45k, 3.4M, 7B. */
    public static String compact(long n) {
        if (n < 1000) {
            return Long.toString(n);
        }
        String[] units = {"k", "M", "B", "T", "Q"};
        double v = n;
        int u = -1;
        while (v >= 1000 && u < units.length - 1) {
            v /= 1000;
            u++;
        }
        return v < 10 ? String.format(Locale.ROOT, "%.1f%s", Math.floor(v * 10) / 10, units[u]) : ((long) v) + units[u];
    }
}
