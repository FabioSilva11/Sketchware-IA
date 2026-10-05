package pro.sketchware.chat.port;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import pro.sketchware.chat.FileChangeTracker;

/**
 * Android-friendly port of electron-main/voidSCMMainService.ts.
 */
public final class VoidPortScmService {
    private static final int MAX_DIFF_LENGTH = 8000;
    private static final int MAX_DIFF_FILES = 10;

    private VoidPortScmService() {
    }

    public static String gitBranch(String scId) {
        return "axion-project/" + (scId == null ? "unknown" : scId);
    }

    public static String gitStat(String scId) {
        List<FileChangeTracker.FileChange> changes = recentChangesSorted(scId);
        if (changes.isEmpty()) {
            return "No recent file changes";
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < changes.size() && i < MAX_DIFF_FILES; i++) {
            FileChangeTracker.FileChange change = changes.get(i);
            VoidPortDiffService.DiffStats stats =
                    VoidPortDiffService.stats(change.beforeContent, change.afterContent);
            builder.append(change.filePath)
                    .append(" | +")
                    .append(stats.added)
                    .append(" -")
                    .append(stats.removed)
                    .append("\n");
        }
        return builder.toString().trim();
    }

    public static String gitSampledDiffs(String scId) {
        List<FileChangeTracker.FileChange> changes = recentChangesSorted(scId);
        if (changes.isEmpty()) {
            return "No recent file changes";
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < changes.size() && i < MAX_DIFF_FILES; i++) {
            FileChangeTracker.FileChange change = changes.get(i);
            builder.append("==== ")
                    .append(change.filePath)
                    .append(" ====\n")
                    .append(VoidPortDiffService.toUnifiedDiff(
                            change.filePath,
                            change.beforeContent,
                            change.afterContent,
                            MAX_DIFF_LENGTH
                    ))
                    .append("\n\n");
            if (builder.length() > MAX_DIFF_LENGTH) {
                builder.setLength(MAX_DIFF_LENGTH);
                builder.append("\n... sampled diffs truncated ...");
                break;
            }
        }
        return builder.toString().trim();
    }

    public static String gitLog(String scId) {
        List<FileChangeTracker.FileChange> changes = recentChangesSorted(scId);
        if (changes.isEmpty()) {
            return "No recent local edit log";
        }

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < changes.size() && i < 5; i++) {
            FileChangeTracker.FileChange change = changes.get(i);
            builder.append(format.format(new Date(change.timestamp)))
                    .append("|")
                    .append(change.filePath)
                    .append("|local edit\n");
        }
        return builder.toString().trim();
    }

    public static int changedFileCount() {
        return FileChangeTracker.getAllRecentChanges().size();
    }

    public static int changedFileCount(String scId) {
        return FileChangeTracker.getAllRecentChanges(scId).size();
    }

    private static List<FileChangeTracker.FileChange> recentChangesSorted(String scId) {
        Map<String, FileChangeTracker.FileChange> changes = FileChangeTracker.getAllRecentChanges(scId);
        List<FileChangeTracker.FileChange> list = new ArrayList<>(changes.values());
        list.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
        return list;
    }
}



