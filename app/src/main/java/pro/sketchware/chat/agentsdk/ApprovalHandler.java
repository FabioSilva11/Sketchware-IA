package pro.sketchware.chat.agentsdk;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Host boundary for human-in-the-loop approvals with an EXPLICIT resolution
 * protocol (item 16 of the migration) — the run thread never blocks for
 * minutes on an uncancellable callback:
 *
 * <pre>
 * ToolCall → PermissionLayer → ASK_USER → ApprovalRequired event
 *     → host stores the request → user answers
 *     → host calls resolve(requestId, decision)  [or cancel/times out]
 *     → runtime continues
 * </pre>
 *
 * <p>The lifecycle of every request is the typed
 * {@link ApprovalState}: PENDING → ALLOWED | DENIED | TIMED_OUT | CANCELLED.
 * Resolving an unknown or already-final request is a no-op (returns
 * {@code false}), so a stale dialog can never decide a new tool call.</p>
 */
public interface ApprovalHandler {

    /** Default maximum time a pending approval may wait for the user. */
    long DEFAULT_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(10);

    /** Terminal states of an approval request (Codex ReviewDecision parity). */
    enum ApprovalState {
        PENDING, ALLOWED, DENIED, TIMED_OUT, CANCELLED
    }

    /** Immutable snapshot of one request's lifecycle. */
    final class ApprovalRecord {
        private final PermissionRequest request;
        private final ApprovalState state;

        ApprovalRecord(PermissionRequest request, ApprovalState state) {
            this.request = request;
            this.state = state;
        }

        public PermissionRequest getRequest() {
            return request;
        }

        public String getRequestId() {
            return request == null ? "" : request.getId();
        }

        public ApprovalState getState() {
            return state;
        }
    }

    /**
     * Called on the run thread when a tool needs approval. Implementations
     * must either block (queue/future style) or answer directly via
     * {@link #decideNow}.
     */
    PermissionDecision onRequest(PermissionRequest request);

    /** Default approval timeout; override to allow longer waits. */
    default long timeoutMs() {
        return DEFAULT_TIMEOUT_MS;
    }

    // ------------------------------------------------------------------
    // Explicit resolution protocol (item 16)
    // ------------------------------------------------------------------

    /**
     * Resolves the pending request with the host's decision.
     *
     * @return true when the request existed and was still PENDING.
     */
    default boolean resolve(String requestId, PermissionDecision decision) {
        return false;
    }

    /**
     * Cancels the pending request (user closed the dialog / run ended).
     *
     * @return true when the request existed and was still PENDING.
     */
    default boolean cancel(String requestId) {
        return false;
    }

    /**
     * The request currently PENDING for this handler, or {@code null}.
     * When several are pending (parallel tool calls) the host should inspect
     * {@link #pendingRequests()}.
     */
    default PermissionRequest currentPendingRequest() {
        List<PermissionRequest> all = pendingRequests();
        return all.isEmpty() ? null : all.get(all.size() - 1);
    }

    /** Every request still PENDING (second tool while one approval pends). */
    default List<PermissionRequest> pendingRequests() {
        return java.util.Collections.emptyList();
    }

    /** Observes every lifecycle transition (UI status, telemetry). */
    default void addStateListener(Consumer<ApprovalRecord> listener) {
    }

    // ------------------------------------------------------------------
    // Turn-scoped teardown: a cancelled/finished run must never leave a
    // PENDING approval that a later run could mistakenly resolve.
    // ------------------------------------------------------------------

    /**
     * Cancels every still-PENDING request owned by this handler. Called by
     * the runtime when the run is cancelled or ends.
     */
    default void cancelAll() {
    }

    /**
     * Waits for the decision, honoring {@link #timeoutMs}. The default
     * implementation parks on a one-slot queue and translates a timeout into
     * a fail-closed DENY with the TIMED_OUT state recorded.
     */
    default PermissionDecision awaitDecision(PermissionRequest request)
            throws InterruptedException, TimeoutException {
        long timeout = timeoutMs();
        if (timeout <= 0) {
            return onRequest(request);
        }
        BlockingQueue<PermissionDecision> slot = new LinkedBlockingQueue<>(1);
        Thread responder = new Thread(() -> {
            PermissionDecision decision = onRequest(request);
            if (decision == null) {
                decision = PermissionDecision.DENY;
            }
            slot.offer(decision);
        }, "axion-approval-" + request.getId());
        responder.setDaemon(true);
        responder.start();
        try {
            PermissionDecision decision = slot.poll(timeout, TimeUnit.MILLISECONDS);
            if (decision == null) {
                throw new TimeoutException("Approval timed out for tool '" + request.getTool() + "'");
            }
            return decision;
        } finally {
            responder.interrupt();
        }
    }

    /** Non-blocking answer used by synchronous hosts and tests. */
    default PermissionDecision decideNow(PermissionRequest request) {
        return onRequest(request);
    }

    /**
     * Free-text answer typed by the user for the most recent request — used by
     * the {@code request_user_input} tool. Hosts that support typed answers
     * store the text before resolving the decision; the default returns null
     * (interpreted as "no answer provided").
     */
    default String lastResponseText() {
        return null;
    }

    /** Convenience: a future-based variant for hosts that resolve later. */
    static ApprovalHandler fromFuture(java.util.function.Function<PermissionRequest, CompletableFuture<PermissionDecision>> resolver,
                                      long timeoutMs) {
        return new ApprovalHandler() {
            @Override
            public PermissionDecision onRequest(PermissionRequest request) {
                try {
                    PermissionDecision decision = resolver.apply(request).get(timeoutMs, TimeUnit.MILLISECONDS);
                    return decision == null ? PermissionDecision.DENY : decision;
                } catch (Exception e) {
                    return PermissionDecision.DENY;
                }
            }
        };
    }

    /**
     * Ready-made registry implementation: the host parks requests here and
     * resolves them by {@code requestId} from any thread. The run thread
     * awaits a future per request; timeouts and cancellations fail closed.
     */
    final class Resolver implements ApprovalHandler {

        private static final class Entry {
            final PermissionRequest request;
            final CompletableFuture<PermissionDecision> future = new CompletableFuture<>();
            final AtomicBoolean decided = new AtomicBoolean(false);

            Entry(PermissionRequest request) {
                this.request = request;
            }
        }

        private final long timeoutMs;
        private final Map<String, Entry> pending = new ConcurrentHashMap<>();
        private final List<Consumer<ApprovalRecord>> listeners = new CopyOnWriteArrayList<>();

        public Resolver() {
            this(DEFAULT_TIMEOUT_MS);
        }

        public Resolver(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        @Override
        public long timeoutMs() {
            return timeoutMs;
        }

        @Override
        public PermissionDecision onRequest(PermissionRequest request) {
            Entry entry = new Entry(request);
            pending.put(request.getId(), entry);
            notifyState(request, ApprovalState.PENDING);
            try {
                PermissionDecision decision =
                        entry.future.get(timeoutMs, TimeUnit.MILLISECONDS);
                return decision == null ? PermissionDecision.DENY : decision;
            } catch (TimeoutException e) {
                finish(entry, ApprovalState.TIMED_OUT, PermissionDecision.DENY);
                return PermissionDecision.DENY;
            } catch (Exception e) {
                finish(entry, ApprovalState.CANCELLED, PermissionDecision.DENY);
                return PermissionDecision.DENY;
            }
        }

        @Override
        public boolean resolve(String requestId, PermissionDecision decision) {
            Entry entry = pending.get(requestId);
            if (entry == null || decision == null) {
                return false;
            }
            ApprovalState state = decision == PermissionDecision.DENY
                    ? ApprovalState.DENIED
                    : ApprovalState.ALLOWED;
            return finish(entry, state, decision);
        }

        @Override
        public boolean cancel(String requestId) {
            Entry entry = pending.get(requestId);
            if (entry == null) {
                return false;
            }
            return finish(entry, ApprovalState.CANCELLED, PermissionDecision.DENY);
        }

        @Override
        public List<PermissionRequest> pendingRequests() {
            List<PermissionRequest> result = new java.util.ArrayList<>();
            for (Entry entry : pending.values()) {
                result.add(entry.request);
            }
            return result;
        }

        @Override
        public void addStateListener(Consumer<ApprovalRecord> listener) {
            if (listener != null) {
                listeners.add(listener);
            }
        }

        @Override
        public void cancelAll() {
            for (Entry entry : pending.values().toArray(new Entry[0])) {
                finish(entry, ApprovalState.CANCELLED, PermissionDecision.DENY);
            }
        }

        private boolean finish(Entry entry, ApprovalState state, PermissionDecision decision) {
            if (!entry.decided.compareAndSet(false, true)) {
                return false; // already final: a second resolution is a no-op
            }
            pending.remove(entry.request.getId());
            entry.future.complete(decision);
            notifyState(entry.request, state);
            return true;
        }

        private void notifyState(PermissionRequest request, ApprovalState state) {
            ApprovalRecord record = new ApprovalRecord(request, state);
            for (Consumer<ApprovalRecord> listener : listeners) {
                try {
                    listener.accept(record);
                } catch (Exception ignored) {
                    // A broken listener must never break the approval flow.
                }
            }
        }
    }
}
