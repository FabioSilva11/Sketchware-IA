package pro.sketchware.network;

import android.os.Handler;
import android.os.SystemClock;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Provider-neutral asynchronous HTTP transport.
 *
 * <p>It owns connection retries, rate-limit backoff and request-scoped
 * cancellation. Provider adapters remain responsible only for payloads and
 * response parsing.</p>
 */
final class AiStreamingTransport {
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2500L;

    interface ClientProvider {
        OkHttpClient clientFor(String providerId);
    }

    interface ErrorFormatter {
        String format(String providerId, int statusCode, String responseBody);
    }

    interface ResponseHandler {
        void handle(Call call, Response response) throws Exception;
    }

    interface Listener {
        void onDebug(String message);
        void onError(String message, Throwable error);
        boolean hasEmitted();
    }

    private final Handler callbackHandler;
    private final ClientProvider clientProvider;
    private final ErrorFormatter errorFormatter;

    AiStreamingTransport(Handler callbackHandler, ClientProvider clientProvider, ErrorFormatter errorFormatter) {
        this.callbackHandler = callbackHandler;
        this.clientProvider = clientProvider;
        this.errorFormatter = errorFormatter;
    }

    void execute(Request request, int retryCount, String providerId, Listener listener,
                 ResponseHandler responseHandler, AiRequestHandle requestHandle) {
        if (requestHandle.isCancelled()) {
            return;
        }

        final long requestStartedAt = SystemClock.elapsedRealtime();
        Call call = clientProvider.clientFor(providerId).newCall(request);
        requestHandle.attach(call);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call failedCall, IOException error) {
                requestHandle.clear(failedCall);
                if (failedCall.isCanceled() || requestHandle.isCancelled()) {
                    listener.onError("cancelled", error);
                    return;
                }
                if (shouldRetryForFailure(error, retryCount)) {
                    scheduleRetry(request, retryCount, providerId, listener, responseHandler, -1, requestHandle);
                    return;
                }
                listener.onError(error.getMessage(), error);
            }

            @Override
            public void onResponse(Call respondedCall, Response response) throws IOException {
                if (requestHandle.isCancelled()) {
                    response.close();
                    requestHandle.clear(respondedCall);
                    return;
                }
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    requestHandle.clear(respondedCall);
                    if (shouldRetryForStatus(response.code(), retryCount)) {
                        long retryAfterMs = retryAfterMillis(response);
                        if (response.code() == 429) {
                            listener.onDebug("HTTP 429 rate-limit from " + providerId
                                    + (retryAfterMs > 0
                                    ? ", Retry-After=" + (retryAfterMs / 1000) + "s"
                                    : ", using default backoff"));
                        }
                        response.close();
                        scheduleRetry(request, retryCount, providerId, listener, responseHandler,
                                retryAfterMs, requestHandle);
                        return;
                    }
                    response.close();
                    listener.onError(errorFormatter.format(providerId, response.code(), errorBody), null);
                    return;
                }

                listener.onDebug("HTTP " + response.code() + " em "
                        + (SystemClock.elapsedRealtime() - requestStartedAt) + "ms");
                try (Response safeResponse = response) {
                    responseHandler.handle(respondedCall, safeResponse);
                } catch (Exception error) {
                    if (!listener.hasEmitted() && shouldRetryForFailure(error, retryCount)) {
                        scheduleRetry(request, retryCount, providerId, listener, responseHandler,
                                -1, requestHandle);
                        return;
                    }
                    listener.onError("Stream reading error", error);
                } finally {
                    requestHandle.clear(respondedCall);
                }
            }
        });
    }

    private void scheduleRetry(Request request, int retryCount, String providerId, Listener listener,
                               ResponseHandler responseHandler, long retryAfterMs,
                               AiRequestHandle requestHandle) {
        if (requestHandle.isCancelled()) {
            return;
        }
        if (retryCount >= MAX_RETRIES) {
            listener.onError("Request failed after retries for provider: " + providerId, null);
            return;
        }
        long baseDelayMs = retryAfterMs > 0 ? retryAfterMs : RETRY_DELAY_MS * (retryCount + 1L);
        long jitter = (long) (baseDelayMs * 0.2 * (Math.random() * 2 - 1));
        long delayMs = Math.max(250L, baseDelayMs + jitter);
        callbackHandler.postDelayed(() -> execute(request, retryCount + 1, providerId, listener,
                responseHandler, requestHandle), delayMs);
    }

    private boolean shouldRetryForStatus(int statusCode, int retryCount) {
        return retryCount < MAX_RETRIES
                && (statusCode == 408 || statusCode == 409 || statusCode == 429 || statusCode >= 500);
    }

    private boolean shouldRetryForFailure(Throwable throwable, int retryCount) {
        return retryCount < MAX_RETRIES && throwable instanceof IOException;
    }

    private long retryAfterMillis(Response response) {
        if (response.code() != 429) {
            return -1;
        }
        String value = response.header("Retry-After");
        if (value == null || value.trim().isEmpty()) {
            return -1;
        }
        try {
            return Math.min(Long.parseLong(value.trim()) * 1000L, 60_000L);
        } catch (NumberFormatException ignored) {
            try {
                java.util.Date date = new java.text.SimpleDateFormat(
                        "EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US).parse(value.trim());
                if (date != null) {
                    return Math.min(Math.max(date.getTime() - System.currentTimeMillis(), -1L), 60_000L);
                }
            } catch (Exception ignoredDate) {
                // Use default backoff.
            }
            return -1;
        }
    }
}
