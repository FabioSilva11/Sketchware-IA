package pro.sketchware.chat.agentsdk;

import pro.sketchware.chat.workspace.WorkspaceFileSystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Codex AGENTS.md parity (item 5 of the context-model migration): project
 * instructions are discovered from THIS run's {@link WorkspaceFileSystem}
 * following the Codex directory-hierarchy convention —
 *
 * <pre>
 * workspace/
 *   AGENTS.md                 ← applies to everything
 *   app/
 *     AGENTS.md               ← refines for app/**
 *     src/
 *       AGENTS.override.md    ← overrides app/AGENTS.md for app/src/**
 * </pre>
 *
 * <p>Precedence (Codex behavior): instructions from the root up to the cwd are
 * concatenated in order (root → deep), and an {@code AGENTS.override.md}
 * replaces the regular {@code AGENTS.md} of the SAME directory. Only files on
 * the path from the workspace root to the run's {@code cwd} apply — deeper
 * or unrelated directories are not included. The lookup runs against the
 * run's own filesystem (never the global active workspace), and results are
 * cached per workspace id with a short TTL, keyed so different workspaces
 * never share cache entries.</p>
 */
public final class ProjectInstructions {

    public static final String FILE_NAME = "AGENTS.md";
    public static final String OVERRIDE_NAME = "AGENTS.override.md";
    private static final long CACHE_TTL_MS = 5000L;

    private static final Object CACHE_LOCK = new Object();
    private static volatile String cachedKey = "";
    private static volatile long cachedAtMs;
    private static volatile String cachedValue = "";
    /** Test/multi-host override; when set, bypasses WorkspaceManager. */
    private static volatile WorkspaceFileSystem overrideFs;

    private ProjectInstructions() {
    }

    /** Forces reads from an injected filesystem (tests, evals). */
    public static void setOverrideFileSystem(WorkspaceFileSystem fs) {
        overrideFs = fs;
        invalidate();
    }

    /**
     * Legacy entry point: instructions for the workspace ROOT of the run's
     * filesystem. Kept for compatibility with existing call sites; the
     * {@link AgentRuntime} uses the hierarchy-aware overload below.
     */
    public static String load(int maxChars) {
        WorkspaceFileSystem fs = overrideFs != null ? overrideFs : activeFsSafe();
        return load(fs, "", maxChars);
    }

    /** Test overload: hierarchy instructions for the root of {@code fs}. */
    public static String load(WorkspaceFileSystem fs, int maxChars) {
        return load(fs, "", maxChars);
    }

    /**
     * Hierarchy-aware load (Codex parity): collects AGENTS.md /
     * AGENTS.override.md files from the workspace root down to {@code cwd}
     * (inclusive), root → deep, with the override replacing the regular file
     * of its own directory. Missing files yield an empty contribution —
     * never an error.
     */
    public static String load(WorkspaceFileSystem fs, String cwd, int maxChars) {
        if (fs == null) {
            return "";
        }
        String key = cacheKey(fs, cwd);
        long now = System.currentTimeMillis();
        synchronized (CACHE_LOCK) {
            if (key.equals(cachedKey) && now - cachedAtMs < CACHE_TTL_MS) {
                return truncate(cachedValue, maxChars);
            }
        }
        String raw = collect(fs, cwd);
        synchronized (CACHE_LOCK) {
            cachedKey = key;
            cachedAtMs = now;
            cachedValue = raw;
        }
        return truncate(raw, maxChars);
    }

    /** Clears the memoization cache (tests, workspace switches). */
    public static void invalidate() {
        synchronized (CACHE_LOCK) {
            cachedKey = "";
            cachedAtMs = 0;
            cachedValue = "";
        }
    }

    /**
     * Structured result of one lookup: the ordered fragments with their
     * provenance (source path), used by the prompt assembler to cite where
     * each instruction came from.
     */
    public static final class Hierarchy {
        final List<Fragment> fragments;

        Hierarchy(List<Fragment> fragments) {
            this.fragments = Collections.unmodifiableList(fragments);
        }

        public List<Fragment> fragments() {
            return fragments;
        }

        public boolean isEmpty() {
            return fragments.isEmpty();
        }
    }

    /** One instruction file contribution with its origin directory. */
    public static final class Fragment {
        final String sourcePath;
        final String content;

        Fragment(String sourcePath, String content) {
            this.sourcePath = sourcePath;
            this.content = content;
        }

        /** Workspace-relative path of the source file (provenance). */
        public String sourcePath() {
            return sourcePath;
        }

        public String content() {
            return content;
        }
    }

    /** Structured variant used by the {@link RunContextAssembler}. */
    public static Hierarchy loadHierarchy(WorkspaceFileSystem fs, String cwd) {
        return new Hierarchy(collectFragments(fs, cwd));
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    /** Concatenates the applicable fragments, root → deep. */
    private static String collect(WorkspaceFileSystem fs, String cwd) {
        StringBuilder sb = new StringBuilder();
        for (Fragment fragment : collectFragments(fs, cwd)) {
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(fragment.content.trim());
        }
        return sb.toString();
    }

    private static List<Fragment> collectFragments(WorkspaceFileSystem fs, String cwd) {
        List<Fragment> fragments = new ArrayList<>();
        for (String dir : applicableDirectories(cwd)) {
            String overridePath = join(dir, OVERRIDE_NAME);
            String regularPath = join(dir, FILE_NAME);
            boolean overrideUsed = false;
            try {
                if (fs.exists(overridePath) && !fs.isDirectory(overridePath)) {
                    addFragment(fragments, overridePath, fs.readText(overridePath));
                    overrideUsed = true;
                }
            } catch (Exception ignored) {
                // unreadable override falls through to the regular file
            }
            if (!overrideUsed) {
                try {
                    if (fs.exists(regularPath) && !fs.isDirectory(regularPath)) {
                        addFragment(fragments, regularPath, fs.readText(regularPath));
                    }
                } catch (Exception ignored) {
                    // instructions are optional; never break a run over them
                }
            }
        }
        return fragments;
    }

    private static void addFragment(List<Fragment> fragments, String path, String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return;
        }
        fragments.add(new Fragment(path, raw.replace("\r\n", "\n").trim()));
    }

    /** Directories from the workspace root down to cwd (root first). */
    static List<String> applicableDirectories(String cwd) {
        List<String> dirs = new ArrayList<>();
        dirs.add(""); // workspace root always applies
        String normalized;
        try {
            normalized = pro.sketchware.chat.workspace.WorkspacePath.normalize(cwd);
        } catch (Exception e) {
            normalized = "";
        }
        if (normalized == null || normalized.isEmpty()) {
            return dirs;
        }
        String[] segments = normalized.split("/");
        StringBuilder current = new StringBuilder();
        for (String segment : segments) {
            if (segment.isEmpty()) {
                continue;
            }
            if (current.length() > 0) {
                current.append('/');
            }
            current.append(segment);
            dirs.add(current.toString());
        }
        return dirs;
    }

    private static String join(String dir, String file) {
        return dir == null || dir.isEmpty() ? file : dir + "/" + file;
    }

    private static String cacheKey(WorkspaceFileSystem fs, String cwd) {
        String identity;
        try {
            identity = String.valueOf(fs);
        } catch (Exception e) {
            identity = "";
        }
        return identity + "|" + (cwd == null ? "" : cwd);
    }

    private static WorkspaceFileSystem activeFsSafe() {
        try {
            return pro.sketchware.chat.workspace.WorkspaceManager.getActiveFileSystem();
        } catch (Exception e) {
            return null;
        }
    }

    private static String truncate(String text, int maxChars) {
        if (text == null || maxChars <= 0) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, maxChars) + "\n… (AGENTS.md truncado)";
    }
}
