package pro.sketchware.chat;

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
    private final AiRetryController retryController = new AiRetryController();

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
                AiRetryController.RetryDecision decision = retryController.shouldRetry(
                        retryCount + 1, -1, null, -1L, true);
                if (decision.shouldRetry()) {
                    scheduleRetry(request, retryCount, providerId, listener, responseHandler,
                            decision, requestHandle);
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
                    long retryAfterSeconds = AiRetryController.parseRetryAfter(response.header("Retry-After"));
                    AiRetryController.RetryDecision decision = retryController.shouldRetry(
                            retryCount + 1, response.code(), errorBody,
                            retryAfterSeconds, false);
                    if (decision.shouldRetry()) {
                        if (response.code() == 429) {
                            listener.onDebug("HTTP 429 rate-limit from " + providerId);
                        }
                        response.close();
                        scheduleRetry(request, retryCount, providerId, listener, responseHandler,
                                decision, requestHandle);
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
                    AiRetryController.RetryDecision decision = retryController.shouldRetry(
                            retryCount + 1, -1, null, -1L, error instanceof IOException);
                    if (!listener.hasEmitted() && decision.shouldRetry()) {
                        scheduleRetry(request, retryCount, providerId, listener, responseHandler,
                                decision, requestHandle);
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
                               ResponseHandler responseHandler,
                               AiRetryController.RetryDecision decision,
                               AiRequestHandle requestHandle) {
        if (requestHandle.isCancelled() || decision == null || !decision.shouldRetry()) {
            return;
        }
        callbackHandler.postDelayed(() -> execute(request, retryCount + 1, providerId, listener,
                responseHandler, requestHandle), decision.getDelayMillis());
    }


}
