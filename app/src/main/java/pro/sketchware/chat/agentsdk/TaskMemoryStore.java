package pro.sketchware.chat.agentsdk;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

/**
 * Real persistence for the {@link TaskMemory} (item 7 of the context-model
 * migration): one JSON file per conversation under the Axion-internal
 * {@code tasks/} directory — the SAME storage contract used by the
 * {@code FileChangeTracker} ({@code .axion}-style internal state, never the
 * user's workspace, never SAF).
 *
 * <pre>
 * RunContext.taskMemory()
 *     ↓
 * TaskMemoryStore.save(scId, memory)     ← end of run
 *     ↓
 * TaskMemoryStore.load(scId)             ← next run / after process death
 * </pre>
 *
 * <p>Durability guarantees (item 26): the save is ATOMIC (temp file + rename)
 * and VERIFIED — the metadata timestamp is only updated after the rename
 * actually produced the new state; a failed rename leaves the previous file
 * intact and the metadata untouched. The storage key is SANITIZED
 * (no path traversal: only [A-Za-z0-9._-] survive, and the result can never
 * escape the {@code tasks/} directory). Corrupted files fail safe: load
 * returns null instead of throwing or restoring partial state.</p>
 *
 * <p>Identity (item 27): the persisted record carries {@code scId} AND
 * {@code workspaceId}; {@link #loadValidated} refuses to restore a memory
 * whose stored workspace does not match the caller's run — a memory is only
 * restored when scId + workspaceId belong to the SAME run context.</p>
 */
public final class TaskMemoryStore {

    /**
     * Pseudo-conversation id for the host loop's single live run: one run
     * per process (the UI blocks concurrent runs), so the legacy path keeps
     * its durable task state under this key.
     */
    public static final String LEGACY_RUN_KEY = "_RUN";

    private static final Object LOCK = new Object();
    private static final Map<String, Long> LAST_WRITE_BY_SC = new ConcurrentHashMap<>();

    private TaskMemoryStore() {
    }

    /**
     * Persists {@code memory} for {@code scId} (best-effort, never throws).
     * The LAST_WRITE_BY_SC timestamp is updated ONLY after a verified rename,
     * so a failed save never makes metadata claim a state that is not on
     * disk.
     */
    public static boolean save(String scId, @Nullable TaskMemory memory) {
        if (scId == null || scId.isEmpty() || memory == null) {
            return false;
        }
        synchronized (LOCK) {
            try {
                File file = fileFor(scId);
                if (file == null) {
                    return false;
                }
                JSONObject json = memory.toJson();
                json.put("savedAt", System.currentTimeMillis());
                json.put("scId", scId);
                json.put("workspaceId", memory.workspaceId());
                File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    out.write(json.toString().getBytes(StandardCharsets.UTF_8));
                    out.getFD().sync();
                }
                // Atomic swap. On Windows/older Androids renameTo may refuse
                // when the target exists: delete-then-rename, then VERIFY.
                boolean replaced;
                if (file.exists()) {
                    replaced = tmp.renameTo(file) || (file.delete() && tmp.renameTo(file));
                } else {
                    replaced = tmp.renameTo(file);
                }
                if (!replaced || !file.exists() || file.length() == 0) {
                    // Keep the previous state intact; do NOT update metadata.
                    //noinspection ResultOfMethodCallIgnored
                    tmp.delete();
                    return false;
                }
                LAST_WRITE_BY_SC.put(scId, System.currentTimeMillis());
                return true;
            } catch (Exception ignored) {
                // Persistence is best-effort: a failed save must never fail a run.
                return false;
            }
        }
    }

    /** Restores the persisted memory for {@code scId}, or {@code null}. */
    @Nullable
    public static TaskMemory load(String scId) {
        return loadInternal(scId, null);
    }

    /**
     * Identity-checked restore (item 27): the memory is only returned when
     * its persisted {@code scId}/{@code workspaceId} match the expected
     * ones. Prevents restoring another workspace's task state just because
     * an scId partially coincides.
     *
     * @param expectedWorkspaceId the run's workspace id (empty/null skips
     *                            the workspace check for legacy records)
     */
    @Nullable
    public static TaskMemory loadValidated(String scId, @Nullable String expectedWorkspaceId) {
        return loadInternal(scId, expectedWorkspaceId);
    }

    @Nullable
    private static TaskMemory loadInternal(String scId, @Nullable String expectedWorkspaceId) {
        if (scId == null || scId.isEmpty()) {
            return null;
        }
        synchronized (LOCK) {
            try {
                File file = fileFor(scId);
                if (file == null || !file.exists() || file.length() == 0) {
                    return null;
                }
                byte[] bytes = new byte[(int) file.length()];
                try (FileInputStream in = new FileInputStream(file)) {
                    int total = 0;
                    while (total < bytes.length) {
                        int read = in.read(bytes, total, bytes.length - total);
                        if (read < 0) {
                            break;
                        }
                        total += read;
                    }
                }
                String text = new String(bytes, StandardCharsets.UTF_8).trim();
                if (text.isEmpty()) {
                    return null;
                }
                JSONObject json;
                try {
                    json = new JSONObject(text);
                } catch (Exception corrupted) {
                    // Corrupted file: fail safe — a broken record is never
                    // restored as if it were valid state.
                    return null;
                }
                // Identity validation: the record must declare the same
                // scId, and (when checked) the same workspaceId.
                String storedScId = json.optString("scId", "");
                if (!storedScId.isEmpty() && !storedScId.equals(scId)) {
                    return null;
                }
                if (expectedWorkspaceId != null && !expectedWorkspaceId.isEmpty()) {
                    String storedWorkspace = json.optString("workspaceId", "");
                    if (!storedWorkspace.isEmpty() && !storedWorkspace.equals(expectedWorkspaceId)) {
                        return null;
                    }
                }
                return TaskMemory.fromJson(json);
            } catch (Exception e) {
                return null;
            }
        }
    }

    /**
     * Restores the persisted memory into {@code memory} when the on-disk
     * state is newer than {@code memory}'s creation — the compaction-proof
     * handoff: the next run starts where the previous one stopped.
     */
    public static void restoreInto(String scId, @Nullable TaskMemory memory) {
        if (memory == null) {
            return;
        }
        Long lastWrite = LAST_WRITE_BY_SC.get(scId);
        if (lastWrite != null && lastWrite < memory.startedAt()) {
            return; // in-memory state is newer: nothing to restore
        }
        TaskMemory persisted = loadInternal(scId, memory.workspaceId());
        if (persisted != null) {
            memory.mergeFrom(persisted);
        }
    }

    /** Clears the persisted memory for {@code scId} (conversation reset). */
    public static void clear(String scId) {
        if (scId == null || scId.isEmpty()) {
            return;
        }
        synchronized (LOCK) {
            File file = fileFor(scId);
            if (file != null && file.exists()) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
            LAST_WRITE_BY_SC.remove(scId);
        }
    }

    /**
     * Resolves the storage file for {@code scId} with path-traversal
     * protection: the sanitized name keeps only safe filename characters,
     * so {@code "../x"} or {@code "a/b"} can never escape {@code tasks/}.
     */
    @Nullable
    private static File fileFor(String scId) {
        try {
            String sanitized = sanitize(scId);
            if (sanitized.isEmpty()) {
                return null;
            }
            Context context = pro.sketchware.SketchApplication.getContext();
            File dir;
            if (context != null) {
                dir = new File(context.getFilesDir(), "tasks");
            } else {
                // No Android host (JVM evals): temp-dir fallback so the
                // persistence contract stays observable in unit tests.
                dir = new File(System.getProperty("java.io.tmpdir"), "axion-tasks");
            }
            if (!dir.exists() && !dir.mkdirs()) {
                return null;
            }
            File file = new File(dir, sanitized + ".json");
            // Belt and suspenders: the canonical path MUST stay inside dir.
            if (!file.getCanonicalPath().startsWith(dir.getCanonicalPath() + File.separator)) {
                return null;
            }
            return file;
        } catch (RuntimeException | IOException e) {
            return null;
        }
    }

    /** Keeps only filename-safe characters; everything else is dropped. */
    static String sanitize(String scId) {
        if (scId == null) {
            return "";
        }
        return scId.replaceAll("[^A-Za-z0-9._-]", "");
    }
}
