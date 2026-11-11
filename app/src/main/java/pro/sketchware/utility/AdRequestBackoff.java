package pro.sketchware.utility;

/** Main-thread request cooldown, measured with SystemClock.elapsedRealtime(). */
final class AdRequestBackoff {
    /** Longest wait after repeated failures (e.g. no fill), so a screen recovers its banner quickly. */
    static final long MAX_DELAY_MS = 60_000L;

    private int failures;
    private long nextAllowedAt;

    boolean canRequest(long now) {
        return now >= nextAllowedAt;
    }

    long retryDelayMs(long now) {
        return Math.max(0, nextAllowedAt - now);
    }

    void onFailure(long now) {
        failures = Math.min(failures + 1, 6);
        nextAllowedAt = now + Math.min(MAX_DELAY_MS, 15_000L << (failures - 1));
    }

    void onSuccess() {
        failures = 0;
        nextAllowedAt = 0;
    }
}
