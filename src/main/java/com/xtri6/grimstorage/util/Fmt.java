package com.xtri6.grimstorage.util;

import java.util.Locale;

/** Number formatting shared by tooltips and screens. */
public final class Fmt {
    private Fmt() {}

    /** 1234 -> "1,234". */
    public static String big(long n) {
        return String.format(Locale.ROOT, "%,d", n);
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
