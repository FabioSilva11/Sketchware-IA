package pro.sketchware.chat.agentsdk;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import pro.sketchware.chat.workspace.WorkspaceFileSystem;

/**
 * Execution-time filesystem binding for ONE agent run (item 14 of the
 * migration): the {@link AgentRuntime} pins the {@link WorkspaceIdentity}/
 * {@link WorkspaceFileSystem} resolved by the {@link RunContextFactory} at
 * run start, and every tool that resolves paths through static helpers (the
 * Void-ported registry, {@code ContextBuilder}, {@code ApplyPatchTool})
 * reads THIS binding instead of the global active workspace.
 *
 * <p><b>Run-scoped, not process-global.</b> The binding is keyed by the
 * run's {@code runId}, so concurrent runs on different threads never share
 * (or overwrite) each other's filesystem. Resolution order for a thread:</p>
 *
 * <ol>
 *   <li>the thread's own nested binding (explicit install/uninstall),</li>
 *   <li>the binding of the run in flight ON THAT THREAD
 *       ({@code runId → binding}, resolved from the current thread),</li>
 *   <li>{@code null} — callers must fail closed; there is no silent
 *       fallback to the global active workspace inside the runtime.</li>
 * </ol>
 *
 * <p>Async provider/tool callbacks that capture the binding at enqueue time
 * keep resolving the same filesystem even when they run later, on another
 * thread: use {@link #snapshotOf(String)} / {@link #withRun(String, ...)} to
 * re-install the captured binding explicitly.</p>
 */
public final class RuntimeFileContext {

    /** Identity + filesystem pinned for one run. */
    public static final class Binding {
        private final String runId;
        private final WorkspaceIdentity identity;
        private final WorkspaceFileSystem filesystem;

        Binding(String runId, WorkspaceIdentity identity, WorkspaceFileSystem filesystem) {
            this.runId = runId == null ? "" : runId;
            this.identity = identity;
            this.filesystem = filesystem;
        }

        /** The owning run id (stable identity of the binding). */
        public String runId() {
            return runId;
        }

        public WorkspaceIdentity identity() {
            return identity;
        }

        public WorkspaceFileSystem filesystem() {
            return filesystem;
        }
    }

    /** The ONLY binding store: runId → binding. Never a single global slot. */
    private static final Map<String, Binding> BINDINGS = new ConcurrentHashMap<>();

    /** Nested (thread-scoped) bindings for unmanaged threads. */
    private static final ThreadLocal<Deque<Binding>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    private RuntimeFileContext() {
    }

    /**
     * Pins the run's binding under {@code runId} for the whole duration of
     * the run. The close token removes EXACTLY this run's binding (never a
     * concurrent run's) and restores the thread's nested state semantics.
     * MUST be closed in the run's {@code finally}.
     */
    public static AutoCloseable pin(String runId, WorkspaceIdentity identity,
                                    WorkspaceFileSystem filesystem) {
        String safeRunId = runId == null || runId.trim().isEmpty()
                ? "run_" + UUID.randomUUID()
                : runId.trim();
        Binding binding = new Binding(safeRunId, identity, filesystem);
        BINDINGS.put(safeRunId, binding);
        // ALSO install the binding on the calling thread (the run thread):
        // every tool/ContextBuilder lookup on THIS thread resolves the run's
        // filesystem directly, even with several concurrent runs registered
        // in the map. Cross-thread consumers still use snapshotOf(runId).
        Deque<Binding> stack = STACK.get();
        stack.push(binding);
        return () -> {
            Deque<Binding> current = STACK.get();
            // Pop exactly OUR thread binding (never a nested install).
            if (!current.isEmpty() && current.peek() == binding) {
                current.pop();
            }
            // Remove only OUR map binding: a rebinding of the same runId by a
            // later run must survive an unbalanced close of an earlier one.
            if (BINDINGS.get(safeRunId) == binding) {
                BINDINGS.remove(safeRunId);
            }
        };
    }

    /**
     * Installs a thread-local binding; returns a token to pop it precisely.
     * Used by unmanaged threads (provider callbacks, executors) that must
     * observe a specific run's filesystem.
     */
    public static AutoCloseable install(String runId, WorkspaceIdentity identity,
                                        WorkspaceFileSystem filesystem) {
        Deque<Binding> stack = STACK.get();
        stack.push(new Binding(runId, identity, filesystem));
        return () -> {
            Deque<Binding> current = STACK.get();
            if (!current.isEmpty()) {
                current.pop();
            }
        };
    }

    /** Backward-compatible install without a runId (nested callers only). */
    public static AutoCloseable install(WorkspaceIdentity identity, WorkspaceFileSystem filesystem) {
        return install("", identity, filesystem);
    }

    /**
     * The binding of the run in flight on the CALLING thread, or the most
     * recently pinned run when only one is active (single-run host loop).
     * {@code null} when no run binding applies.
     */
    public static Binding activeBinding() {
        Deque<Binding> stack = STACK.get();
        Binding threadTop = stack.peek();
        if (threadTop != null && threadTop.filesystem() != null) {
            return threadTop;
        }
        if (BINDINGS.size() == 1) {
            return BINDINGS.values().iterator().next();
        }
        // Multiple concurrent runs: no global choice. The caller must
        // declare the run explicitly (snapshotOf / install with runId).
        return null;
    }

    /** Explicitly resolves the binding of ONE run by its id. */
    public static Binding snapshotOf(String runId) {
        return runId == null ? null : BINDINGS.get(runId.trim());
    }

    /**
     * The filesystem bound to the running execution: the thread's own
     * binding when installed, else the single in-flight run's binding,
     * else {@code null} — in which case callers MUST fail closed.
     * The legacy {@code WorkspaceManager.getActiveFileSystem()} fallback is
     * NOT part of this contract anymore.
     */
    public static WorkspaceFileSystem effectiveFileSystem() {
        Binding active = activeBinding();
        return active == null ? null : active.filesystem();
    }

    /** The identity bound to the running execution, or {@code null}. */
    public static WorkspaceIdentity effectiveIdentity() {
        Binding active = activeBinding();
        return active == null ? null : active.identity();
    }

    /** The filesystem bound to THIS thread only (no run-wide fallback). */
    public static WorkspaceFileSystem currentFileSystem() {
        Deque<Binding> stack = STACK.get();
        Binding top = stack.peek();
        return top == null ? null : top.filesystem();
    }

    /** The identity bound to THIS thread only, or {@code null}. */
    public static WorkspaceIdentity currentIdentity() {
        Deque<Binding> stack = STACK.get();
        Binding top = stack.peek();
        return top == null ? null : top.identity();
    }

    /** True when a thread-local binding is installed on this thread. */
    public static boolean isInstalled() {
        return !STACK.get().isEmpty();
    }

    /** Test hook: clears every binding (thread-local of the caller + all runs). */
    static void clearForTest() {
        STACK.get().clear();
        BINDINGS.clear();
    }
}
