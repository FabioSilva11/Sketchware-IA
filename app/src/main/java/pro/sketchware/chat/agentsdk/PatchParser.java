package pro.sketchware.chat.agentsdk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Parser for the {@code apply_patch} patch format, ported from Codex's
 * {@code apply_patch} tool. A patch document is:
 *
 * <pre>
 * *** Begin Patch
 * *** Add File: path/to/file.kt
 * +line one
 * +line two
 * *** Update File: other.kt
 * @@ optional context marker
 *  unchanged line
 * -removed line
 * +added line
 * *** Delete File: legacy.txt
 * *** End Patch
 * </pre>
 *
 * <p>Deliberately stricter than Codex's parser: any malformed hunk aborts
 * the whole patch, so a partial application can never corrupt files.</p>
 */
public final class PatchParser {

    /** One file operation parsed from the patch. */
    public static final class PatchOp {
        public enum Type { ADD, UPDATE, DELETE }

        private final Type type;
        private final String path;
        private final List<Hunk> hunks;

        PatchOp(Type type, String path, List<Hunk> hunks) {
            this.type = type;
            this.path = path;
            this.hunks = hunks == null ? Collections.emptyList() : hunks;
        }

        public Type getType() {
            return type;
        }

        public String getPath() {
            return path;
        }

        public List<Hunk> getHunks() {
            return hunks;
        }
    }

    /** A contiguous change block within an Update op. */
    public static final class Hunk {
        private final String contextMarker;
        private final List<Line> lines;

        Hunk(String contextMarker, List<Line> lines) {
            this.contextMarker = contextMarker == null ? "" : contextMarker;
            this.lines = lines;
        }

        public String getContextMarker() {
            return contextMarker;
        }

        public List<Line> getLines() {
            return lines;
        }
    }

    /** One {@code context|add|remove} line of a hunk. */
    public static final class Line {
        public enum Kind { CONTEXT, ADD, REMOVE }

        private final Kind kind;
        private final String text;

        Line(Kind kind, String text) {
            this.kind = kind;
            this.text = text == null ? "" : text;
        }

        public Kind getKind() {
            return kind;
        }

        public String getText() {
            return text;
        }
    }

    /** A malformed patch; carries the offending line for the error result. */
    public static final class PatchParseException extends Exception {
        PatchParseException(String message) {
            super(message);
        }
    }

    private static final String BEGIN = "*** Begin Patch";
    private static final String END = "*** End Patch";
    private static final String ADD_FILE = "*** Add File: ";
    private static final String UPDATE_FILE = "*** Update File: ";
    private static final String DELETE_FILE = "*** Delete File: ";
    private static final String HUNK_HEADER = "@@";

    public static List<PatchOp> parse(String patch) throws PatchParseException {
        if (patch == null) {
            throw new PatchParseException("Patch is empty.");
        }
        List<String> lines = splitLines(patch);
        if (lines.isEmpty() || !BEGIN.equals(lines.get(0).trim())) {
            throw new PatchParseException("Patch must start with '" + BEGIN + "'.");
        }
        int end = -1;
        for (int i = lines.size() - 1; i >= 1; i--) {
            if (END.equals(lines.get(i).trim())) {
                end = i;
                break;
            }
        }
        if (end == -1) {
            throw new PatchParseException("Patch must end with '" + END + "'.");
        }

        List<PatchOp> ops = new ArrayList<>();
        int i = 1;
        while (i < end) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                i++;
                continue;
            }
            if (line.startsWith(ADD_FILE)) {
                String path = line.substring(ADD_FILE.length()).trim();
                if (path.isEmpty()) {
                    throw new PatchParseException("Add File requires a path (line " + (i + 1) + ").");
                }
                List<Line> addLines = new ArrayList<>();
                i++;
                while (i < end) {
                    String raw = lines.get(i);
                    if (raw.startsWith("*** ")) {
                        break;
                    }
                    if (!raw.startsWith("+")) {
                        throw new PatchParseException(
                                "Add File hunks only accept '+ ' lines (line " + (i + 1) + ").");
                    }
                    addLines.add(new Line(Line.Kind.ADD, raw.substring(1)));
                    i++;
                }
                ops.add(new PatchOp(PatchOp.Type.ADD, path, Collections.singletonList(new Hunk("", addLines))));
            } else if (line.startsWith(UPDATE_FILE)) {
                String path = line.substring(UPDATE_FILE.length()).trim();
                if (path.isEmpty()) {
                    throw new PatchParseException("Update File requires a path (line " + (i + 1) + ").");
                }
                i++;
                List<Hunk> hunks = new ArrayList<>();
                List<Line> current = new ArrayList<>();
                String currentMarker = "";
                while (i < end) {
                    String raw = lines.get(i);
                    if (raw.startsWith("*** ")) {
                        break;
                    }
                    if (raw.startsWith(HUNK_HEADER)) {
                        if (!current.isEmpty()) {
                            hunks.add(new Hunk(currentMarker, current));
                            current = new ArrayList<>();
                        }
                        currentMarker = raw.substring(HUNK_HEADER.length()).trim();
                        i++;
                        continue;
                    }
                    if (raw.startsWith("+")) {
                        current.add(new Line(Line.Kind.ADD, raw.substring(1)));
                    } else if (raw.startsWith("-")) {
                        current.add(new Line(Line.Kind.REMOVE, raw.substring(1)));
                    } else if (raw.startsWith(" ")) {
                        current.add(new Line(Line.Kind.CONTEXT, raw.substring(1)));
                    } else {
                        throw new PatchParseException(
                                "Update File hunks only accept ' ', '+' and '-' lines (line "
                                        + (i + 1) + ").");
                    }
                    i++;
                }
                if (!current.isEmpty()) {
                    hunks.add(new Hunk(currentMarker, current));
                }
                if (hunks.isEmpty()) {
                    throw new PatchParseException("Update File op has no hunks (line " + (i + 1) + ").");
                }
                ops.add(new PatchOp(PatchOp.Type.UPDATE, path, hunks));
            } else if (line.startsWith(DELETE_FILE)) {
                String path = line.substring(DELETE_FILE.length()).trim();
                if (path.isEmpty()) {
                    throw new PatchParseException("Delete File requires a path (line " + (i + 1) + ").");
                }
                ops.add(new PatchOp(PatchOp.Type.DELETE, path, Collections.emptyList()));
                i++;
            } else {
                throw new PatchParseException("Unrecognized patch directive: '" + line + "'.");
            }
        }
        if (ops.isEmpty()) {
            throw new PatchParseException("Patch contains no file operations.");
        }
        return ops;
    }

    private static List<String> splitLines(String patch) {
        List<String> lines = new ArrayList<>();
        String normalized = patch.replace("\r\n", "\n").replace("\r", "\n");
        int start = 0;
        for (int i = 0; i < normalized.length(); i++) {
            if (normalized.charAt(i) == '\n') {
                lines.add(normalized.substring(start, i));
                start = i + 1;
            }
        }
        if (start < normalized.length()) {
            lines.add(normalized.substring(start));
        }
        return lines;
    }

    /** Utility used by the {@code ApplyPatchTool} for error messages. */
    static String opSummary(PatchOp op) {
        return op.getType().name().toLowerCase(Locale.ROOT) + " " + op.getPath();
    }
}
