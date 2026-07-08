package pro.sketchware.util;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pro.sketchware.SketchApplication;
import pro.sketchware.activities.chat.port.VoidPortDiffService;

/**
 * Tracks project file changes for the chat diff view.
 */
public class FileChangeTracker {
    private static final String PREFS_NAME = "chat_file_changes";
    private static final String PREF_KEY_PREFIX = "changes_";
    private static final String DEFAULT_SCOPE = "_global";
    private static final int MAX_PERSISTED_CHANGES = 25;

    private static final Map<String, Map<String, FileChange>> scopedChanges = new HashMap<>();

    public static class FileChange {
        public final String filePath;
        public final String beforeContent;
        public final String afterContent;
        public final long timestamp;
        /** False when the change CREATED the file — rejecting must delete it, not write "". */
        public final boolean existedBefore;

        public FileChange(String filePath, String beforeContent, String afterContent) {
            this(filePath, beforeContent, afterContent, System.currentTimeMillis(), true);
        }

        public FileChange(String filePath, String beforeContent, String afterContent, long timestamp) {
            this(filePath, beforeContent, afterContent, timestamp, true);
        }

        public FileChange(String filePath, String beforeContent, String afterContent, long timestamp, boolean existedBefore) {
            this.filePath = filePath;
            this.beforeContent = beforeContent;
            this.afterContent = afterContent;
            this.timestamp = timestamp;
            this.existedBefore = existedBefore;
        }

        public String generateDiff() {
            if (beforeContent == null || afterContent == null) {
                return "No diff available";
            }

            return VoidPortDiffService.toUnifiedDiff(filePath, beforeContent, afterContent, 8000);
        }
    }

    public static void trackChange(String filePath, String beforeContent, String afterContent) {
        trackChange(DEFAULT_SCOPE, filePath, beforeContent, afterContent);
    }

    public static synchronized void trackChange(String scId, String filePath, String beforeContent, String afterContent) {
        trackChange(scId, filePath, beforeContent, afterContent, true);
    }

    public static synchronized void trackChange(String scId, String filePath, String beforeContent,
                                                String afterContent, boolean existedBefore) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return;
        }
        String scope = scopeKey(scId);
        Map<String, FileChange> changes = loadScope(scope);

        // If a change for this file is already pending, keep the ORIGINAL
        // beforeContent/existedBefore. Previously each edit overwrote the entry,
        // so "reject" only undid the last edit instead of the whole change.
        FileChange pending = changes.get(filePath);
        String effectiveBefore = pending != null ? pending.beforeContent : beforeContent;
        boolean effectiveExisted = pending != null ? pending.existedBefore : existedBefore;

        String safeAfter = afterContent == null ? "" : afterContent;
        if (effectiveExisted && safeAfter.equals(effectiveBefore == null ? "" : effectiveBefore)) {
            // The file ended up back at its original content — nothing to review.
            changes.remove(filePath);
        } else {
            changes.put(filePath, new FileChange(filePath, effectiveBefore, safeAfter,
                    System.currentTimeMillis(), effectiveExisted));
        }
        trimOldChanges(changes);
        persistScope(scope, changes);
    }

    public static FileChange getLastChange(String filePath) {
        return getLastChange(DEFAULT_SCOPE, filePath);
    }

    public static synchronized FileChange getLastChange(String scId, String filePath) {
        if (filePath == null) {
            return null;
        }
        return loadScope(scopeKey(scId)).get(filePath);
    }

    public static Map<String, FileChange> getAllRecentChanges() {
        return getAllRecentChanges(DEFAULT_SCOPE);
    }

    public static synchronized Map<String, FileChange> getAllRecentChanges(String scId) {
        return new HashMap<>(loadScope(scopeKey(scId)));
    }

    public static synchronized boolean acceptChange(String scId, String filePath) {
        String scope = scopeKey(scId);
        Map<String, FileChange> changes = loadScope(scope);
        boolean removed = changes.remove(filePath) != null;
        if (removed) {
            persistScope(scope, changes);
        }
        return removed;
    }

    public static synchronized boolean rejectChange(String scId, String filePath) {
        FileChange change = getLastChange(scId, filePath);
        if (change == null) {
            return false;
        }
        boolean restored;
        if (!change.existedBefore) {
            // The change created this file: rejecting must DELETE it, not
            // leave an empty file behind.
            try {
                ProjectPathResolver.ResolvedPath resolved = ProjectPathResolver.resolveForWrite(scId, filePath);
                java.io.File file = resolved == null ? null : resolved.getFile();
                restored = file == null || !file.exists() || file.delete();
            } catch (Exception e) {
                restored = false;
            }
        } else {
            restored = SketchwareFileEncryptor.encryptAndSaveFile(scId, filePath, change.beforeContent);
        }
        if (restored) {
            acceptChange(scId, filePath);
        }
        return restored;
    }

    public static void clearChanges() {
        clearChanges(DEFAULT_SCOPE);
    }

    public static synchronized void clearChanges(String scId) {
        String scope = scopeKey(scId);
        Map<String, FileChange> changes = loadScope(scope);
        changes.clear();
        persistScope(scope, changes);
    }

    public static String generateChangesSummary() {
        return generateChangesSummary(DEFAULT_SCOPE);
    }

    public static synchronized String generateChangesSummary(String scId) {
        Map<String, FileChange> changes = loadScope(scopeKey(scId));
        if (changes.isEmpty()) {
            return "No recent changes";
        }

        StringBuilder summary = new StringBuilder();
        summary.append("**Recent File Changes:**\n\n");

        for (FileChange change : changes.values()) {
            summary.append("**File:** ").append(change.filePath).append("\n");
            summary.append("**Time:** ").append(new java.util.Date(change.timestamp)).append("\n");
            summary.append("**Diff:**\n```\n");
            summary.append(change.generateDiff());
            summary.append("\n```\n\n");
        }

        return summary.toString();
    }

    private static Map<String, FileChange> loadScope(String scope) {
        Map<String, FileChange> cached = scopedChanges.get(scope);
        if (cached != null) {
            return cached;
        }

        Map<String, FileChange> loaded = new HashMap<>();
        SharedPreferences prefs = prefs();
        if (prefs != null) {
            String raw = prefs.getString(PREF_KEY_PREFIX + scope, "");
            if (raw != null && !raw.trim().isEmpty()) {
                try {
                    JSONArray array = new JSONArray(raw);
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject object = array.optJSONObject(i);
                        if (object == null) {
                            continue;
                        }
                        String filePath = object.optString("filePath", "");
                        if (filePath.isEmpty()) {
                            continue;
                        }
                        loaded.put(filePath, new FileChange(
                                filePath,
                                object.optString("beforeContent", ""),
                                object.optString("afterContent", ""),
                                object.optLong("timestamp", System.currentTimeMillis()),
                                object.optBoolean("existedBefore", true)
                        ));
                    }
                } catch (Exception ignored) {
                }
            }
        }

        scopedChanges.put(scope, loaded);
        return loaded;
    }

    private static void persistScope(String scope, Map<String, FileChange> changes) {
        SharedPreferences prefs = prefs();
        if (prefs == null || changes == null) {
            return;
        }
        try {
            JSONArray array = new JSONArray();
            List<FileChange> sorted = new ArrayList<>(changes.values());
            sorted.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
            for (FileChange change : sorted) {
                JSONObject object = new JSONObject();
                object.put("filePath", change.filePath);
                object.put("beforeContent", change.beforeContent == null ? "" : change.beforeContent);
                object.put("afterContent", change.afterContent == null ? "" : change.afterContent);
                object.put("timestamp", change.timestamp);
                object.put("existedBefore", change.existedBefore);
                array.put(object);
            }
            prefs.edit().putString(PREF_KEY_PREFIX + scope, array.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private static void trimOldChanges(Map<String, FileChange> changes) {
        if (changes.size() <= MAX_PERSISTED_CHANGES) {
            return;
        }
        List<FileChange> sorted = new ArrayList<>(changes.values());
        sorted.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
        for (int i = MAX_PERSISTED_CHANGES; i < sorted.size(); i++) {
            changes.remove(sorted.get(i).filePath);
        }
    }

    private static SharedPreferences prefs() {
        Context context = SketchApplication.getContext();
        return context == null ? null : context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String scopeKey(String scId) {
        String raw = scId == null || scId.trim().isEmpty() ? DEFAULT_SCOPE : scId.trim();
        return raw.replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}
