package pro.sketchware.ia.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable snapshot of project facts supplied to layout generation. */
public final class LayoutProjectContext {
    public final String currentLayout;
    public final List<String> drawables;
    public final String referenceNotes;
    public final List<String> referenceImages;

    public LayoutProjectContext(String currentLayout, List<String> drawables,
                                String referenceNotes, List<String> referenceImages) {
        this.currentLayout = safe(currentLayout);
        this.drawables = immutable(drawables);
        this.referenceNotes = safe(referenceNotes);
        this.referenceImages = immutable(referenceImages);
    }

    public String toPrompt(LayoutComponentRegistry registry) {
        StringBuilder result = new StringBuilder();
        result.append("Sketchware component catalog:\n").append(registry.promptCatalog()).append("\n\n");
        result.append("Defaults already handled by Sketchware:\n")
                .append("- root width/height are match_parent\n")
                .append("- root is vertical LinearLayout unless another container is explicitly required\n")
                .append("- omit redundant root padding, margin, background and decorative wrappers\n")
                .append("- omit properties that merely repeat platform defaults\n\n");
        if (!drawables.isEmpty()) {
            result.append("Available drawable resources:\n")
                    .append(String.join(", ", drawables)).append("\n\n");
        }
        if (!currentLayout.isEmpty()) {
            result.append("Current editable layout:\n").append(currentLayout).append("\n\n");
        }
        if (!referenceNotes.isEmpty()) {
            result.append("Visual reference notes:\n").append(referenceNotes).append("\n\n");
        }
        return result.toString();
    }

    private static List<String> immutable(List<String> source) {
        return source == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(source));
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
