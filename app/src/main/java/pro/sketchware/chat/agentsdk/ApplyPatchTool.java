package pro.sketchware.chat.agentsdk;

import pro.sketchware.chat.FileChangeTracker;
import pro.sketchware.chat.workspace.WorkspaceFileSystem;
import pro.sketchware.chat.workspace.WorkspacePath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Port of Codex's {@code apply_patch} tool: the model expresses multi-file
 * changes as one patch document which is validated in full before anything
 * is written. Validation is all-or-nothing: if any op fails validation, no
 * file is touched and the model receives a structured error it can retry on.
 *
 * <p>This class is the patch ENGINE. It is deliberately NOT an {@code AgentTool}:
 * the model-facing registration lives in the tool registry
 * ({@code WorkspaceToolProvider} registers {@code apply_patch} as a FREEFORM
 * registration whose executor feeds the raw patch document here, bound to the
 * run's workspace and event stream).</p>
 *
 * <p>Announcement is commit-ordered: {@link AgentEvent.FileChanged} events and
 * {@link FileChangeTracker} entries are produced ONLY after every operation of
 * the patch is confirmed on the workspace filesystem.</p>
 *
 * <p>Failure semantics are honest about the two possible outcomes:</p>
 * <ul>
 *   <li><b>Rollback complete</b> — the already-applied ops are restored from
 *   their captured pre-patch content and the restore is verified against the
 *   real filesystem. The tool returns an error with no events and no tracked
 *   changes: the failed patch leaves zero traces of a commit.</li>
 *   <li><b>Rollback incomplete</b> — a restore itself failed verification. The
 *   filesystem may be PARTIALLY patched: the tool emits an
 *   {@link AgentEvent.Error}, names the affected files, and the error message
 *   explicitly says changes may remain applied. It never claims a clean
 *   revert and never fakes tracker entries or commit events.</li>
 * </ul>
 *
 * <p>Path security: every path goes through {@link WorkspacePath#normalize}
 * (rejects traversal) and must not be absolute or contain a drive letter —
 * the workspace filesystem works with relative paths only.</p>
 */
public final class ApplyPatchTool {

    /** The registered model-facing name (FREEFORM, {@code WorkspaceToolProvider}). */
    public static final String NAME = "apply_patch";

    private final String scId;
    private final EventStream events;
    private final WorkspaceFileSystem injectedFs;

    /** Production constructor: resolves the active workspace at execute time. */
    public ApplyPatchTool(String scId, EventStream events) {
        this(scId, events, null);
    }

    /** Injected-filesystem constructor (tests, evals, multi-workspace hosts). */
    public ApplyPatchTool(String scId, EventStream events, WorkspaceFileSystem injectedFs) {
        this.scId = scId == null ? "" : scId;
        this.events = events;
        this.injectedFs = injectedFs;
    }

    /** FREEFORM execution: the raw patch document, never JSON-wrapped. */
    public AgentToolResult apply(RunContext context, String patch) {
        List<PatchParser.PatchOp> ops;
        try {
            ops = PatchParser.parse(patch);
        } catch (PatchParser.PatchParseException e) {
            return AgentToolResult.error("Error: invalid patch — " + e.getMessage());
        }

        // ---- Pass 1: validate everything against the run's workspace ----
        // FAIL-CLOSED (item 13 of the migration): the resolution order is
        // injected fs (tests) → the pinned run filesystem (RuntimeFileContext)
        // and NOTHING else. A run with an unresolved workspace can NEVER fall
        // back to the global active workspace: unknown workspace → DENY, not
        // "whatever the UI currently has open". For a destructive tool this
        // is the difference between failing safely and mutating the wrong
        // project.
        WorkspaceFileSystem fs = injectedFs != null
                ? injectedFs
                : RuntimeFileContext.effectiveFileSystem();
        if (fs == null) {
            return AgentToolResult.error(
                    "Error: run workspace could not be resolved. No mutation was applied.");
        }
        List<PatchMutation> mutations = new ArrayList<>();

        try {
            for (PatchParser.PatchOp op : ops) {
                String path = safePath(op.getPath());
                if (path == null) {
                    return AgentToolResult.error("Error: unsafe path '" + op.getPath() + "'.");
                }
                switch (op.getType()) {
                    case ADD: {
                        if (fs.exists(path)) {
                            return AgentToolResult.error("Error: Add File '" + path
                                    + "' but the file already exists.");
                        }
                        mutations.add(PatchMutation.created(path, renderContent(op.getHunks())));
                        break;
                    }
                    case UPDATE: {
                        if (!fs.exists(path) || fs.isDirectory(path)) {
                            return AgentToolResult.error("Error: Update File '" + path
                                    + "' but the file does not exist.");
                        }
                        String original = fs.readText(path);
                        String updated = applyHunks(path, original, op.getHunks());
                        if (updated == null) {
                            return AgentToolResult.error("Error: patch does not apply cleanly to '"
                                    + path + "'.");
                        }
                        mutations.add(PatchMutation.modified(path, original, updated));
                        break;
                    }
                    case DELETE: {
                        if (!fs.exists(path) || fs.isDirectory(path)) {
                            return AgentToolResult.error("Error: Delete File '" + path
                                    + "' but the file does not exist.");
                        }
                        // Capture the previous content now: it feeds both the
                        // tracker entry and the rollback on failure.
                        mutations.add(PatchMutation.deleted(path, fs.readText(path)));
                        break;
                    }
                    default:
                        return AgentToolResult.error("Error: unsupported patch operation.");
                }
            }
        } catch (Exception e) {
            return AgentToolResult.error("Error: validation failed — "
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }

        // ---- Pass 2: apply everything; announce only after full success ----
        StringBuilder report = new StringBuilder();
        List<PatchMutation> applied = new ArrayList<>();
        try {
            for (PatchMutation mutation : mutations) {
                mutation.applyTo(fs);
                applied.add(mutation);
                report.append(mutation.reportLine()).append('\n');
            }
        } catch (Exception e) {
            // Roll back in reverse order and VERIFY the restored state; a
            // failed patch with a complete rollback must look like it never
            // ran: no tracker entries, no FileChanged events.
            String incompleteRollback = rollback(fs, applied);
            String cause = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            if (incompleteRollback.isEmpty()) {
                return AgentToolResult.error("Error: patch failed and was fully rolled back — "
                        + cause + ". No changes were kept.");
            }
            // Rollback itself failed: the filesystem may be partially patched.
            // Never claim a clean revert, never fake tracker entries or commit
            // events — surface the inconsistency as an explicit error.
            if (events != null) {
                events.emit(new AgentEvent.Error(scId,
                        "apply_patch rollback incomplete for: " + incompleteRollback.trim()
                                + "; filesystem state may be partially patched."));
            }
            android.util.Log.e("ApplyPatchTool", "Rollback incomplete after patch failure (" + cause
                    + ") for: " + incompleteRollback.trim());
            return AgentToolResult.error("Error: patch failed and rollback was incomplete ("
                    + cause + "). Some filesystem changes may remain applied: "
                    + incompleteRollback.trim());
        }

        // ---- Pass 3: commit-ordered announcement. The patch is fully on disk:
        // record history for the diff review page and emit FileChanged. ----
        if (scId != null && !scId.isEmpty()) {
            for (PatchMutation mutation : applied) {
                mutation.trackIn(scId);
            }
        }
        if (events != null) {
            for (PatchMutation mutation : applied) {
                events.emit(new AgentEvent.FileChanged(scId, mutation.path,
                        mutation.kind, NAME));
            }
        }
        return AgentToolResult.success(report.toString().trim());
    }

    /**
     * Restores the already-applied mutations, newest first, verifying the
     * real filesystem state after each restore (not trusting void calls).
     *
     * @return the paths whose rollback could not be verified; empty when the
     *         rollback completed for every mutation.
     */
    private static String rollback(WorkspaceFileSystem fs, List<PatchMutation> applied) {
        StringBuilder problems = new StringBuilder();
        for (int i = applied.size() - 1; i >= 0; i--) {
            try {
                applied.get(i).restoreFrom(fs);
            } catch (Exception rollbackFailure) {
                problems.append(applied.get(i).path).append("; ");
            }
        }
        return problems.toString();
    }

    /**
     * One planned/applied file mutation: the single structure used for
     * validation, execution, rollback, tracker records and events. Carries
     * the pre-patch content so failures can undo exactly what was touched.
     */
    private static final class PatchMutation {
        final String path;
        final AgentEvent.FileChangeKind kind;
        final String previousContent; // null when the patch creates the file
        final String newContent;      // null when the patch deletes the file

        private PatchMutation(String path, AgentEvent.FileChangeKind kind,
                              String previousContent, String newContent) {
            this.path = path;
            this.kind = kind;
            this.previousContent = previousContent;
            this.newContent = newContent;
        }

        static PatchMutation created(String path, String newContent) {
            return new PatchMutation(path, AgentEvent.FileChangeKind.CREATED, null, newContent);
        }

        static PatchMutation modified(String path, String previousContent, String newContent) {
            return new PatchMutation(path, AgentEvent.FileChangeKind.MODIFIED,
                    previousContent, newContent);
        }

        static PatchMutation deleted(String path, String previousContent) {
            return new PatchMutation(path, AgentEvent.FileChangeKind.DELETED,
                    previousContent, null);
        }

        /** Performs the real filesystem mutation; throws when it did not stick. */
        void applyTo(WorkspaceFileSystem fs) {
            switch (kind) {
                case CREATED:
                case MODIFIED:
                    fs.writeText(path, newContent);
                    break;
                case DELETED:
                    boolean deleted = fs.delete(path);
                    if (!deleted || fs.exists(path)) {
                        throw new IllegalStateException("filesystem did not delete '" + path + "'");
                    }
                    break;
                default:
                    throw new IllegalStateException("unsupported mutation kind: " + kind);
            }
        }

        /**
         * Restores the pre-patch state of this mutation and verifies the
         * real filesystem outcome. A rollback is only considered done when
         * the on-disk state matches the pre-patch content again.
         *
         * @throws IllegalStateException when the restore did not stick.
         */
        void restoreFrom(WorkspaceFileSystem fs) {
            switch (kind) {
                case CREATED:
                    // The patch created it: remove it again.
                    boolean deleted = fs.delete(path);
                    if (!deleted || fs.exists(path)) {
                        throw new IllegalStateException("rollback could not delete '" + path + "'");
                    }
                    break;
                case MODIFIED:
                case DELETED:
                    String restoredContent = previousContent == null ? "" : previousContent;
                    fs.writeText(path, restoredContent);
                    if (!fs.exists(path)) {
                        throw new IllegalStateException("rollback did not recreate '" + path + "'");
                    }
                    String restored = fs.readText(path);
                    if (!restoredContent.equals(restored)) {
                        throw new IllegalStateException("rollback left '" + path
                                + "' with different content than the pre-patch state");
                    }
                    break;
                default:
                    break;
            }
        }

        /** Records the applied change in the diff review history. */
        void trackIn(String scId) {
            switch (kind) {
                case CREATED:
                    FileChangeTracker.trackChange(scId, path, "", newContent, false);
                    break;
                case MODIFIED:
                    FileChangeTracker.trackChange(scId, path, previousContent, newContent, true);
                    break;
                case DELETED:
                    FileChangeTracker.trackChange(scId, path, previousContent, "", true);
                    break;
                default:
                    break;
            }
        }

        String reportLine() {
            switch (kind) {
                case CREATED:
                    return "Created " + path;
                case MODIFIED:
                    return "Updated " + path;
                case DELETED:
                    return "Deleted " + path;
                default:
                    return path;
            }
        }
    }

    /** Normalizes and rejects unsafe paths; returns {@code null} when unsafe. */
    private static String safePath(String rawPath) {
        if (rawPath == null || rawPath.trim().isEmpty()) {
            return null;
        }
        String trimmed = rawPath.trim();
        if (trimmed.startsWith("/") || trimmed.contains(":") || trimmed.contains("\\")) {
            return null;
        }
        try {
            String normalized = WorkspacePath.normalize(trimmed);
            return normalized.isEmpty() ? null : normalized;
        } catch (Exception e) {
            return null;
        }
    }

    /** Concatenates ADD lines into the new file content. */
    private static String renderContent(List<PatchParser.Hunk> hunks) {
        StringBuilder sb = new StringBuilder();
        for (PatchParser.Hunk hunk : hunks) {
            for (PatchParser.Line line : hunk.getLines()) {
                sb.append(line.getText()).append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * Applies UPDATE hunks: each hunk is located by its context lines
     * (optionally seeded by the {@code @@ marker}) and applied once.
     * Returns {@code null} when any hunk fails to match.
     */
    static String applyHunks(String path, String original, List<PatchParser.Hunk> hunks) {
        List<String> lines = new ArrayList<>();
        String[] split = original.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1);
        Collections.addAll(lines, split);

        for (PatchParser.Hunk hunk : hunks) {
            int at = findHunkPosition(lines, hunk);
            if (at < 0) {
                return null;
            }
            lines = applyAt(lines, at, hunk);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(lines.get(i));
        }
        return sb.toString();
    }

    private static int findHunkPosition(List<String> lines, PatchParser.Hunk hunk) {
        List<PatchParser.Line> hunkLines = hunk.getLines();
        if (hunkLines.isEmpty()) {
            return -1;
        }
        // Fast path: append-only hunk (adds after last context/add line).
        boolean onlyAdds = true;
        for (PatchParser.Line line : hunkLines) {
            if (line.getKind() == PatchParser.Line.Kind.REMOVE
                    || line.getKind() == PatchParser.Line.Kind.CONTEXT) {
                onlyAdds = false;
                break;
            }
        }
        if (onlyAdds && hunk.getContextMarker().isEmpty()) {
            return lines.size();
        }

        // The optional @@ marker is a disambiguator (Codex semantics): it must
        // match some line of the file, and the hunk applies at or after it.
        int searchFrom = 0;
        String anchor = hunk.getContextMarker().trim();
        if (!anchor.isEmpty()) {
            int markerIndex = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).contains(anchor)) {
                    markerIndex = i;
                    break;
                }
            }
            if (markerIndex < 0) {
                return -1;
            }
            searchFrom = markerIndex;
        }

        for (int start = searchFrom; start <= lines.size(); start++) {
            if (matchesAt(lines, start, hunkLines)) {
                return start;
            }
        }
        return -1;
    }

    private static boolean matchesAt(List<String> lines, int start, List<PatchParser.Line> hunkLines) {
        int cursor = start;
        for (PatchParser.Line line : hunkLines) {
            if (line.getKind() == PatchParser.Line.Kind.ADD) {
                continue; // adds do not consume original lines
            }
            if (cursor >= lines.size()) {
                return false;
            }
            if (!lines.get(cursor).equals(line.getText())) {
                return false;
            }
            cursor++;
        }
        return true;
    }

    private static List<String> applyAt(List<String> lines, int at, PatchParser.Hunk hunk) {
        List<String> result = new ArrayList<>();
        result.addAll(lines.subList(0, at));
        int cursor = at;
        for (PatchParser.Line line : hunk.getLines()) {
            switch (line.getKind()) {
                case ADD:
                    result.add(line.getText());
                    break;
                case REMOVE:
                    cursor++; // skip the original line
                    break;
                case CONTEXT:
                    if (cursor < lines.size()) {
                        result.add(lines.get(cursor));
                        cursor++;
                    }
                    break;
            }
        }
        result.addAll(lines.subList(Math.min(cursor, lines.size()), lines.size()));
        return result;
    }
}
