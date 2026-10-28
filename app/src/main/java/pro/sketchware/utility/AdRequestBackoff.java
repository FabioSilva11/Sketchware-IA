package pro.sketchware.utility;

/** Main-thread request cooldown, measured with SystemClock.elapsedRealtime(). */
final class AdRequestBackoff {
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
        nextAllowedAt = now + Math.min(300_000L, 15_000L << (failures - 1));
    }

    void onSuccess() {
        failures = 0;
        nextAllowedAt = 0;
    }
}
