package pro.sketchware.utility;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AdRequestBackoffTest {
    @Test
    public void retryUsesOnlyTheRemainingCooldownAndNeverANegativeDelay() {
        AdRequestBackoff backoff = new AdRequestBackoff();
        assertEquals(0, backoff.retryDelayMs(100));
        backoff.onFailure(100);
        assertEquals(15_000, backoff.retryDelayMs(100));
        assertEquals(10_000, backoff.retryDelayMs(5_100));
        assertEquals(0, backoff.retryDelayMs(15_100));
        assertEquals(0, backoff.retryDelayMs(20_000));
        backoff.onSuccess();
        assertEquals(0, backoff.retryDelayMs(20_000));
    }

    @Test
    public void failedRequestsWaitBeforeRetrying() {
        AdRequestBackoff backoff = new AdRequestBackoff();
        assertTrue(backoff.canRequest(100));
        backoff.onFailure(100);
        assertFalse(backoff.canRequest(15_099));
        assertTrue(backoff.canRequest(15_100));
        backoff.onFailure(15_100);
        assertFalse(backoff.canRequest(45_099));
        assertTrue(backoff.canRequest(45_100));
    }

    @Test
    public void repeatedFailuresNeverWaitMoreThanFiveMinutes() {
        AdRequestBackoff backoff = new AdRequestBackoff();
        long now = 0;
        for (int attempt = 0; attempt < 100; attempt++) {
            backoff.onFailure(now);
            assertTrue(backoff.canRequest(now + 300_000));
            now += 300_000;
        }
        assertFalse(backoff.canRequest(now - 1));
    }

    @Test
    public void successResetsTheDelayForTheNextFailure() {
        AdRequestBackoff backoff = new AdRequestBackoff();
        backoff.onFailure(0);
        backoff.onFailure(15_000);
        backoff.onSuccess();
        assertTrue(backoff.canRequest(15_001));
        backoff.onFailure(15_001);
        assertFalse(backoff.canRequest(30_000));
        assertTrue(backoff.canRequest(30_001));
    }
}
