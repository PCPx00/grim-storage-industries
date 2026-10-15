package com.xtri6.grimstorage.client;

/**
 * The terminal's search. A name matches when it contains the text, or when one of its words is close to a single
 * typed word: one typo for a 4 to 6 letter word, two for 7 or more ("medning" finds "mending").
 */
public final class SearchMatch {
    private SearchMatch() {}

    /** {@code hay} and {@code needle} are already lowercased. */
    public static boolean matches(String hay, String needle) {
        if (needle.isEmpty() || hay.contains(needle)) {
            return true;
        }
        if (needle.indexOf(' ') >= 0) {
            return false;
        }
        int tolerance = needle.length() >= 7 ? 2 : needle.length() >= 4 ? 1 : 0;
        if (tolerance == 0) {
            return false;
        }
        for (String word : hay.split("[^\\p{L}\\p{N}]+")) {
            if (word.length() >= needle.length() - tolerance && word.length() <= needle.length() + tolerance
                    && distance(word, needle) <= tolerance) {
                return true;
            }
        }
        return false;
    }

    /** Optimal string alignment distance: an insert, delete, replace or swap of two neighbours each cost one. */
    static int distance(String a, String b) {
        int n = a.length(), m = b.length();
        int[][] d = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= m; j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[n][m];
    }
}
