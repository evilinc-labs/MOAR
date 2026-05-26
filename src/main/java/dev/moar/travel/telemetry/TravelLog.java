package dev.moar.travel.telemetry;

import dev.moar.travel.TravelPhase;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Bounded ring buffer of recent travel events. */
public final class TravelLog {

    private static final int CAPACITY = 64;
    private static final TravelLog INSTANCE = new TravelLog();

    public static TravelLog get() { return INSTANCE; }

    public record Entry(long missionId, long tick, TravelPhase from, TravelPhase to,
                        String kind, String detail) {}

    private final Deque<Entry> entries = new ArrayDeque<>(CAPACITY);

    private TravelLog() {}

    public synchronized void recordTransition(long missionId, long tick,
                                              TravelPhase from, TravelPhase to, String reason) {
        push(new Entry(missionId, tick, from, to, "TRANSITION", reason));
    }

    public synchronized void recordEvent(long missionId, long tick,
                                         TravelPhase phase, String kind, String detail) {
        push(new Entry(missionId, tick, phase, phase, kind, detail));
    }

    private void push(Entry e) {
        if (entries.size() >= CAPACITY) entries.pollFirst();
        entries.addLast(e);
    }

    public synchronized List<Entry> snapshot() {
        return new ArrayList<>(entries);
    }

    public synchronized void clear() { entries.clear(); }
}
