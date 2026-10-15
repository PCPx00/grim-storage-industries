package com.xtri6.grimstorage.storage;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The last things that went wrong or are worth knowing (a job stuck, a machine whose recipes couldn't be read), kept
 * in memory for {@code /grimstorage debug} reports. Nothing is written anywhere until a report is asked for.
 */
public final class DebugLog {
    private static final int MAX = 200;
    private static final Deque<String> LINES = new ArrayDeque<>();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private DebugLog() {}

    public static synchronized void note(String message) {
        if (LINES.size() >= MAX) {
            LINES.removeFirst();
        }
        LINES.addLast(LocalTime.now().format(TIME) + "  " + message);
    }

    public static synchronized List<String> lines() {
        return new ArrayList<>(LINES);
    }
}
