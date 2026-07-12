package pro.sketchware.network;

import okhttp3.Call;

/** Owns cancellation state for one asynchronous AI request and all its retries. */
public final class AiRequestHandle {
    private volatile Call call;
    private volatile boolean cancelled;

    void attach(Call nextCall) {
        call = nextCall;
        if (cancelled && nextCall != null) {
            nextCall.cancel();
        }
    }

    void clear(Call completedCall) {
        if (call == completedCall) {
            call = null;
        }
    }

    public void cancel() {
        cancelled = true;
        Call activeCall = call;
        if (activeCall != null) {
            activeCall.cancel();
        }
        call = null;
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
