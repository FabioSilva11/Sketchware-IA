package pro.sketchware.chat.agentsdk.tools;

import pro.sketchware.chat.agentsdk.tools.ToolSpec.Type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * The SINGLE source of truth for every tool in the Axion stack — core, MCP,
 * dynamic, legacy-adapted. Ported from Codex {@code tools/registry.rs}.
 *
 * <p><b>One registry, one producer.</b> The runtime mounts the model catalog
 * ({@link #modelVisibleTools()}), the router resolves execution
 * ({@link AxionToolRouter}) and the diagnostics/serializers snapshot tools —
 * all from this registry. Nothing else may keep a parallel tool registry.</p>
 *
 * <h3>Collision rules (Codex parity)</h3>
 * <ul>
 *   <li>A tool is keyed by its fully-qualified {@link ToolName}: the same
 *   plain name under two namespaces is TWO distinct tools
 *   ({@code server_a.search} vs {@code server_b.search}).</li>
 *   <li>Registering a different registration under an already-present name
 *   throws {@link DuplicateToolException} — the registry never silently
 *   replaces a live tool.</li>
 *   <li>A namespace declaration (NAMESPACE spec) reserves its prefix: a later
 *   plain tool may not claim that name, nor may a namespace be declared over
 *   an existing plain tool name.</li>
 * </ul>
 */
public final class AxionToolRegistry {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<ToolName, ToolRegistration> tools = new LinkedHashMap<>();

    /** Whether tool_search / deferred activation is enabled for this registry. */
    private final boolean toolSearchEnabled;
    private final List<DiagnosticsListener> diagnosticsListeners = new ArrayList<>();

    /** Deferred tool names the model has discovered via tool_search (activated). */
    private final Set<ToolName> activatedDeferred = new LinkedHashSet<>();

    /** Raised when a registration collides with a live tool (Codex collision parity). */
    public static final class DuplicateToolException extends IllegalStateException {
        public DuplicateToolException(String message) {
            super(message);
        }
    }

    /** Observes registrations (diagnostics/telemetry); never mutates. */
    public interface DiagnosticsListener {
        void onToolRegistered(ToolRegistration registration);
    }

    public AxionToolRegistry() {
        this(true);
    }

    public AxionToolRegistry(boolean toolSearchEnabled) {
        this.toolSearchEnabled = toolSearchEnabled;
    }

    // ------------------------------------------------------------------
    // Registration
    // ------------------------------------------------------------------

    /**
     * Registers {@code registration}. Fails fast when the qualified name is
     * already taken by a different registration or when the name collides
     * with a reserved namespace prefix.
     *
     * @throws DuplicateToolException on collision
     */
    public void register(ToolRegistration registration) {
        registerOrThrow(registration, false);
    }

    /**
     * Registers {@code registration}, atomically replacing any existing
     * registration under the same name (used by MCP server refresh /
     * dynamic tool redeclaration).
     *
     * @return the replaced registration, or {@code null} when the name was
     *         not previously present.
     */
    public ToolRegistration registerOrReplace(ToolRegistration registration) {
        return registerOrThrow(registration, true);
    }

    private ToolRegistration registerOrThrow(ToolRegistration registration, boolean replace) {
        if (registration == null || registration.spec() == null) {
            throw new IllegalArgumentException("registration and spec are required");
        }
        ToolName name = registration.spec().name();
        if (name == null || name.name().isEmpty()) {
            throw new IllegalArgumentException("a registered tool must have a non-empty name");
        }
        lock.writeLock().lock();
        try {
            ToolRegistration previous = tools.get(name);
            if (previous != null) {
                if (!replace) {
                    throw new DuplicateToolException(
                            "duplicate tool name '" + name.qualifiedName() + "' (source '"
                                    + previous.source() + "' already registered under it).");
                }
                tools.put(name, registration);
                notifyDiagnostics(registration);
                return previous;
            }
            // Namespace-prefix collision: a plain NON-NAMESPACE tool may not
            // claim a namespace prefix that is active (declared namespace or
            // a prefix already owning namespaced children). NAMESPACE
            // declarations may be registered over their own children.
            if (name.isPlain()
                    && registration.spec().type() != Type.NAMESPACE
                    && isActiveNamespace(name.name())) {
                throw new DuplicateToolException(
                        "tool name '" + name.name() + "' collides with an active namespace prefix.");
            }
            // A namespaced tool may not shadow an existing plain tool as its
            // prefix — unless that plain tool is the namespace's own
            // declaration (a declared namespace legitimately owns its prefix).
            if (!name.isPlain()) {
                ToolRegistration prefixOwner = tools.get(ToolName.plain(name.namespace()));
                if (prefixOwner != null && prefixOwner.spec().type() != Type.NAMESPACE) {
                    throw new DuplicateToolException(
                            "namespaced tool '" + name.qualifiedName() + "' collides with the plain tool '"
                                    + name.namespace() + "'.");
                }
            }
            tools.put(name, registration);
            notifyDiagnostics(registration);
            return null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Removes a tool by qualified name; no-op when absent. */
    public void remove(String qualifiedName) {
        remove(ToolName.parse(qualifiedName));
    }

    /** Removes a tool; no-op when absent. */
    public void remove(ToolName name) {
        lock.writeLock().lock();
        try {
            tools.remove(name);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ------------------------------------------------------------------
    // Lookup
    // ------------------------------------------------------------------

    /** The registration under {@code qualifiedName} ({@code "ns.name"}/plain). */
    public ToolRegistration get(String qualifiedName) {
        return get(ToolName.parse(qualifiedName));
    }

    /** The registration under an exact {@link ToolName}, or {@code null}. */
    public ToolRegistration get(ToolName name) {
        lock.readLock().lock();
        try {
            return tools.get(name);
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Whether a tool is registered under the given qualified name. */
    public boolean contains(String qualifiedName) {
        return get(qualifiedName) != null;
    }

    public boolean contains(ToolName name) {
        return get(name) != null;
    }

    /** The number of registered tools (namespace declarations included). */
    public int size() {
        lock.readLock().lock();
        try {
            return tools.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    // ------------------------------------------------------------------
    // Catalog & exposure queries
    // ------------------------------------------------------------------

    /** Immutable snapshot of every registered tool. */
    public List<ToolRegistration> allTools() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(tools.values()));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * The model catalog: every DIRECT exposure plus DEFERRED tools the model
     * already activated via {@code tool_search}. Hidden and not-yet-activated
     * deferred tools are excluded. When {@code codeMode} is true, code-mode-only
     * tools are included too. This is what {@link ToolCatalog#from} snapshots:
     * a deferred tool discovered in a turn IS part of the next turn's catalog.
     */
    public List<ToolRegistration> modelVisibleTools() {
        return modelVisibleTools(false);
    }

    /** Model catalog with an explicit code-mode flag. */
    public List<ToolRegistration> modelVisibleTools(boolean codeMode) {
        lock.readLock().lock();
        try {
            List<ToolRegistration> visible = new ArrayList<>();
            for (ToolRegistration reg : tools.values()) {
                ToolExposure exposure = reg.exposure();
                boolean visibleNow = codeMode
                        ? exposure.isCodeModeVisible()
                        : exposure.isModelVisible();
                if (!visibleNow && exposure.isDeferred()
                        && activatedDeferred.contains(reg.spec().name())) {
                    visibleNow = true;
                }
                if (visibleNow) {
                    visible.add(reg);
                }
            }
            return Collections.unmodifiableList(visible);
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Every DIRECT-exposure tool (code-mode-aware like the model catalog). */
    public List<ToolRegistration> directTools() {
        return directTools(false);
    }

    public List<ToolRegistration> directTools(boolean codeMode) {
        lock.readLock().lock();
        try {
            List<ToolRegistration> direct = new ArrayList<>();
            for (ToolRegistration reg : tools.values()) {
                if (reg.exposure().isDirect()) {
                    if (!codeMode && reg.exposure().isCodeModeOnly()) {
                        continue;
                    }
                    direct.add(reg);
                }
            }
            return Collections.unmodifiableList(direct);
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Every DEFERRED-exposure tool (searchable via tool_search). */
    public List<ToolRegistration> deferredTools() {
        lock.readLock().lock();
        try {
            List<ToolRegistration> deferred = new ArrayList<>();
            for (ToolRegistration reg : tools.values()) {
                if (reg.exposure().isDeferred()) {
                    deferred.add(reg);
                }
            }
            return Collections.unmodifiableList(deferred);
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Every HIDDEN-exposure tool (internal executors, never in a catalog). */
    public List<ToolRegistration> hiddenTools() {
        lock.readLock().lock();
        try {
            List<ToolRegistration> hidden = new ArrayList<>();
            for (ToolRegistration reg : tools.values()) {
                if (reg.exposure().isHidden()) {
                    hidden.add(reg);
                }
            }
            return Collections.unmodifiableList(hidden);
        } finally {
            lock.readLock().unlock();
        }
    }

    /** The registered NAMESPACE declarations (their names are the prefixes). */
    public List<ToolRegistration> namespaceTools() {
        lock.readLock().lock();
        try {
            List<ToolRegistration> namespaces = new ArrayList<>();
            for (ToolRegistration reg : tools.values()) {
                if (reg.spec().type() == Type.NAMESPACE) {
                    namespaces.add(reg);
                }
            }
            return Collections.unmodifiableList(namespaces);
        } finally {
            lock.readLock().unlock();
        }
    }

    /** The exposed child registrations of a namespace prefix (e.g. {@code "clock"}). */
    public List<ToolRegistration> namespaceChildTools(String namespacePrefix) {
        if (namespacePrefix == null || namespacePrefix.trim().isEmpty()) {
            return Collections.emptyList();
        }
        lock.readLock().lock();
        try {
            List<ToolRegistration> children = new ArrayList<>();
            for (ToolRegistration reg : tools.values()) {
                if (reg.spec().name().namespace().equals(namespacePrefix)) {
                    children.add(reg);
                }
            }
            return Collections.unmodifiableList(children);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Whether {@code namespacePrefix} is an active declared namespace
     * (a NAMESPACE spec or a prefix that owns namespaced children).
     */
    public boolean isActiveNamespace(String namespacePrefix) {
        if (namespacePrefix == null || namespacePrefix.trim().isEmpty()) {
            return false;
        }
        lock.readLock().lock();
        try {
            if (tools.containsKey(ToolName.plain(namespacePrefix))
                    && tools.get(ToolName.plain(namespacePrefix)).spec().type() == Type.NAMESPACE) {
                return true;
            }
            for (ToolRegistration reg : tools.values()) {
                if (reg.spec().name().namespace().equals(namespacePrefix)) {
                    return true;
                }
            }
            return false;
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Whether {@code name} is a registered deferred tool that the model has
     *  already discovered (activated) via {@code tool_search}. */
    public boolean isDeferredActivated(ToolName name) {
        lock.readLock().lock();
        try {
            return activatedDeferred.contains(name);
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Marks deferred tools as activated (called by {@code tool_search}). */
    public void activateDeferred(ToolName... names) {
        lock.writeLock().lock();
        try {
            for (ToolName name : names) {
                if (name != null) {
                    activatedDeferred.add(name);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ------------------------------------------------------------------
    // Diagnostics
    // ------------------------------------------------------------------

    public void addDiagnosticsListener(DiagnosticsListener listener) {
        if (listener != null) {
            diagnosticsListeners.add(listener);
        }
    }

    private void notifyDiagnostics(ToolRegistration registration) {
        for (DiagnosticsListener listener : diagnosticsListeners) {
            try {
                listener.onToolRegistered(registration);
            } catch (Exception ignored) {
            }
        }
    }
}