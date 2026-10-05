package pro.sketchware.chat.agentsdk;

import pro.sketchware.chat.workspace.WorkspaceFileSystem;

/**
 * Resolves the single execution identity of a run (Codex environment/cwd
 * parity): {@code scId → Workspace → WorkspaceIdentity + WorkspaceFileSystem}.
 *
 * <p>Created once per run by whoever starts it (the {@link AgentRuntime} or a
 * host bridge), then carried inside the {@link RunContext}. Components that
 * need the workspace resolve it through {@link RunContext#filesystem()} —
 * never from {@code WorkspaceManager.getActiveFileSystem()}, which reflects a
 * UI selection, not the run's identity.</p>
 *
 * <p>Resolution contract:</p>
 * <ol>
 *   <li>resolve the {@link pro.sketchware.chat.workspace.Workspace}
 *       whose id (or legacy project path root) matches the {@code scId};</li>
 *   <li>when the resolved workspace is the globally active one, reuse the
 *       active {@link WorkspaceFileSystem} instance (cheap, no reopen);</li>
 *   <li>otherwise construct a filesystem for that workspace's {@code rootUri}
 *       WITHOUT switching the global selection — the run works against the
 *       correct workspace even if the UI later points elsewhere.</li>
 * </ol>
 *
 * <p>When no workspace matches the {@code scId} (legacy chat-only project),
 * the identity is absent and tools fall back to the legacy
 * {@code ProjectPathResolver} contract, exactly as today.</p>
 */
public final class RunContextFactory {

    private RunContextFactory() {
    }

    /** Fully-resolved context parts for one run. */
    public static final class Resolved {
        final WorkspaceIdentity workspace;
        final WorkspaceFileSystem filesystem;

        Resolved(WorkspaceIdentity workspace, WorkspaceFileSystem filesystem) {
            this.workspace = workspace;
            this.filesystem = filesystem;
        }

        public WorkspaceIdentity workspace() {
            return workspace;
        }

        public WorkspaceFileSystem filesystem() {
            return filesystem;
        }
    }

    /** Test/eval hook: forces the resolution result (JVM tests without Android). */
    private static volatile Resolved testOverride;

    /** Forces every subsequent {@link #resolve(String)} to return {@code resolved}. */
    static void setOverrideForTest(Resolved resolved) {
        testOverride = resolved;
    }

    /** Clears the forced resolution (tests). */
    static void clearOverrideForTest() {
        testOverride = null;
    }

    /**
     * Resolves the workspace identity + filesystem for {@code scId} without
     * touching the global selection. Pure resolution — no instructions,
     * snapshot or memory assembly happens here (those live in
     * {@link RunContextAssembler}).
     */
    public static Resolved resolve(String scId) {
        Resolved forced = testOverride;
        if (forced != null) {
            return forced;
        }
        if (scId == null || scId.isEmpty()) {
            return new Resolved(null, null);
        }
        try {
            pro.sketchware.chat.workspace.WorkspaceManager manager =
                    pro.sketchware.chat.workspace.WorkspaceManager.INSTANCE;
            pro.sketchware.chat.workspace.Workspace active =
                    manager.getActiveWorkspace();
            pro.sketchware.chat.workspace.WorkspaceFileSystem activeFs =
                    manager.getActiveFileSystem();

            // 1) The active workspace IS the run's workspace: reuse it.
            if (active != null && activeFs != null && servesProject(active, scId)) {
                return new Resolved(WorkspaceIdentity.of(scId, active), activeFs);
            }

            // 2) Another workspace matches the scId by id: build its own
            //    filesystem WITHOUT switching the global selection.
            if (active != null && scId.equals(active.getId())) {
                return new Resolved(WorkspaceIdentity.of(scId, active), activeFs);
            }

            // 3) A Sketchware project (native or Android Studio): its own scoped
            //    filesystem, so a run can never reach another project's files.
            pro.sketchware.chat.workspace.Workspace project =
                    pro.sketchware.chat.SketchwareWorkspace.workspaceOf(scId);
            if (project != null) {
                return new Resolved(WorkspaceIdentity.of(scId, project),
                        pro.sketchware.chat.SketchwareWorkspace.fileSystemOf(scId));
            }
            try {
                android.content.Context ctx =
                        pro.sketchware.SketchApplication.getContext();
                if (ctx != null) {
                    pro.sketchware.chat.workspace.Workspace match =
                            new pro.sketchware.chat.workspace.WorkspaceRepository(ctx)
                                    .getById(scId);
                    if (match != null) {
                        return new Resolved(
                                WorkspaceIdentity.of(scId, match),
                                filesystemFor(match, ctx));
                    }
                }
            } catch (Exception repositoryFailure) {
                // No Android context in evals, or repository unavailable: fall
                // through to the legacy path below.
            }
            return new Resolved(null, null);
        } catch (Exception e) {
            // No workspace layer available (JVM tests): legacy path only.
            return new Resolved(null, null);
        }
    }

    /**
     * Identity check: does this workspace serve {@code scId}? Prefer the
     * stable workspace id; legacy projects whose scId is a folder name under
     * the Axion project roots are matched by path.
     */
    private static boolean servesProject(
            pro.sketchware.chat.workspace.Workspace workspace, String scId) {
        if (workspace == null || scId == null || scId.isEmpty()) {
            return false;
        }
        if (scId.equals(workspace.getId())) {
            return true;
        }
        // Legacy parity: scId is the project folder name under the Axion
        // roots (web/Android Studio projects) and the workspace root points
        // into that folder. Only compared for local file roots — content://
        // URIs are never resolved by path.
        String rootUri = workspace.getRootUri();
        if (rootUri == null || rootUri.startsWith("content://")) {
            return false;
        }
        try {
            java.io.File root = new java.io.File(rootUri).getCanonicalFile();
            for (java.io.File candidate : legacyRootCandidates(scId)) {
                if (root.equals(candidate)) {
                    return true;
                }
                if (root.toPath().startsWith(candidate.toPath())) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }

    private static java.util.List<java.io.File> legacyRootCandidates(String scId) {
        java.util.List<java.io.File> candidates = new java.util.ArrayList<>();
        try {
            for (String base : new String[]{
                    pro.sketchware.chat.ProjectManager.getWebProjectsRoot(),
                    pro.sketchware.chat.ProjectManager.getAndroidStudioProjectsRoot()}) {
                if (base == null || base.isEmpty()) {
                    continue;
                }
                try {
                    candidates.add(new java.io.File(base, scId).getCanonicalFile());
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return candidates;
    }

    /**
     * Builds a filesystem for {@code workspace} WITHOUT switching the global
     * active selection. Content URIs need a Context for SAF resolution.
     */
    private static pro.sketchware.chat.workspace.WorkspaceFileSystem filesystemFor(
            pro.sketchware.chat.workspace.Workspace workspace,
            android.content.Context context) {
        String rootUri = workspace.getRootUri();
        if (rootUri != null && rootUri.startsWith("content://")) {
            return new pro.sketchware.chat.workspace.SafWorkspaceFileSystem(
                    context.getApplicationContext(),
                    android.net.Uri.parse(rootUri));
        }
        return new pro.sketchware.chat.workspace.LocalFolderWorkspaceFileSystem(
                new java.io.File(rootUri));
    }
}
