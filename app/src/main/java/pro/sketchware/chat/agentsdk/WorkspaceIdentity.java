package pro.sketchware.chat.agentsdk;

import pro.sketchware.chat.workspace.Workspace;

/**
 * Immutable workspace identity bound to one {@link RunContext}: the resolved
 * {@code scId → workspaceId → rootUri} triple (item 3 of the context-model
 * migration). An execution never consults the global active workspace —
 * whoever builds the run resolves this once, deterministically.
 *
 * <p>{@code cwd} is workspace-relative (Codex Cwd parity) and defaults to the
 * workspace root; the agent may navigate within it via tools.</p>
 */
public final class WorkspaceIdentity {

    private final String scId;
    private final String workspaceId;
    private final String rootUri;
    private final String displayName;
    private final String cwd;

    public WorkspaceIdentity(String scId, String workspaceId, String rootUri,
                             String displayName, String cwd) {
        this.scId = scId == null ? "" : scId;
        this.workspaceId = workspaceId == null ? "" : workspaceId;
        this.rootUri = rootUri == null ? "" : rootUri;
        this.displayName = displayName == null ? "" : displayName;
        this.cwd = cwd == null || cwd.isEmpty() ? "" : cwd;
    }

    /** Factory from a resolved {@link Workspace}: cwd defaults to the root. */
    public static WorkspaceIdentity of(String scId, Workspace workspace) {
        if (workspace == null) {
            return new WorkspaceIdentity(scId, "", "", "", "");
        }
        return new WorkspaceIdentity(
                scId,
                workspace.getId(),
                workspace.getRootUri(),
                workspace.getName(),
                workspace.getRootUri());
    }

    public String scId() {
        return scId;
    }

    /** Persistent workspace id ({@link Workspace#getId()}); empty when unknown. */
    public String workspaceId() {
        return workspaceId;
    }

    /** Root URI as persisted ({@code file://} path or {@code content://} tree). */
    public String rootUri() {
        return rootUri;
    }

    public String displayName() {
        return displayName;
    }

    /** Workspace-relative cwd of the run (empty = root). */
    public String cwd() {
        return cwd;
    }

    /** Stable key for caches: workspace id when known, scId otherwise. */
    public String cacheKey() {
        return workspaceId != null && !workspaceId.isEmpty() ? workspaceId : scId;
    }

    @Override
    public String toString() {
        return "WorkspaceIdentity{scId=" + scId + ", workspaceId=" + workspaceId
                + ", rootUri=" + rootUri + ", cwd=" + cwd + "}";
    }
}
