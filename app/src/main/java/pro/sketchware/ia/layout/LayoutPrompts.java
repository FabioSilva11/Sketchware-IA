package pro.sketchware.ia.layout;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * Prompts for layout generation. The rules below are the same ones {@link LayoutSpecCompiler}
 * enforces, so the model knows exactly what will be accepted.
 */
public final class LayoutPrompts {

    private LayoutPrompts() {
    }

    @NonNull
    public static String system(@NonNull LayoutComponentRegistry registry) {
        return "You design Android screens for Sketchware, a visual app builder. You don't write XML: you answer with ONE JSON object "
                + "that describes the layout, and Sketchware builds the widgets from it exactly as if the user had dragged them.\n\n"
                + "OUTPUT: only the JSON object, no markdown fences, no comments, no text before or after it.\n\n"
                + "SCHEMA\n"
                + "{\n"
                + "  \"root\": {\"type\": <container>, \"orientation\": \"vertical\"|\"horizontal\" (LinearLayout only), \"gravity\": <gravity>, \"padding\": <box>, \"background\": <color>},\n"
                + "  \"views\": [ <view>, ... ]\n"
                + "}\n"
                + "<view> = {\n"
                + "  \"id\": snake_case, unique, describes the role (\"email_input\", \"login_button\"),\n"
                + "  \"type\": a component of the catalog,\n"
                + "  \"parent\": \"root\" or the id of a container that appears EARLIER in the array,\n"
                + "  \"width\" / \"height\": \"match_parent\" | \"wrap_content\" | \"<n>dp\" (default wrap_content),\n"
                + "  \"margin\" / \"padding\": \"16dp\" or {\"top\",\"bottom\",\"start\",\"end\",\"horizontal\",\"vertical\": \"<n>dp\"},\n"
                + "  \"text\": visible text, written in the user's language (Sketchware stores it in strings.xml),\n"
                + "  \"hint\": placeholder of EditText / TextInputEditText / AutoCompleteTextView,\n"
                + "  \"textSize\": \"<n>sp\", \"textStyle\": \"normal\"|\"bold\"|\"italic\"|\"bold|italic\", \"textColor\": <color>,\n"
                + "  \"gravity\": <gravity> (content alignment), \"layoutGravity\": <gravity> (position inside a LinearLayout, FrameLayout, card or scroll view),\n"
                + "  \"weight\": number (children of LinearLayout; use with width or height \"0dp\"),\n"
                + "  \"orientation\": \"vertical\"|\"horizontal\" (LinearLayout and RadioGroup; default vertical),\n"
                + "  \"background\": <color> or one of the project drawables, \"src\": a project drawable (images only),\n"
                + "  \"layout\": layout name (only for type include),\n"
                + "  \"relative\": {...} (only for children of a RelativeLayout),\n"
                + "  \"constraints\": {...} (REQUIRED for children of a ConstraintLayout),\n"
                + "  \"attributes\": {\"android:contentDescription\": \"...\", \"android:inputType\": \"textEmailAddress\", ...} extra android:/app: attributes\n"
                + "}\n"
                + "<color> = \"#RRGGBB\" | \"#AARRGGBB\" | \"?attr/colorPrimary\" (theme) | a project color \"@color/name\".\n"
                + "<gravity> = top, bottom, start, end, center, center_horizontal, center_vertical joined with | (\"center_vertical|end\").\n\n"
                + "RELATIVELAYOUT children: \"relative\": {\"alignParentTop\": true, \"alignParentBottom\": true, \"alignParentStart\": true, "
                + "\"alignParentEnd\": true, \"centerInParent\": true, \"centerHorizontal\": true, \"centerVertical\": true, "
                + "\"below\": id, \"above\": id, \"toStartOf\": id, \"toEndOf\": id, \"alignTop\": id, \"alignBottom\": id, \"alignStart\": id, "
                + "\"alignEnd\": id, \"alignBaseline\": id}. Ids must be siblings (same parent). Never make two views depend on each other in a circle.\n\n"
                + "CONSTRAINTLAYOUT children: \"constraints\": {\"top\": \"parent.top\" | \"<sibling>.top\" | \"<sibling>.bottom\", "
                + "\"bottom\": same, \"start\": \"parent.start\" | \"<sibling>.start\" | \"<sibling>.end\", \"end\": same, "
                + "\"baseline\": \"<sibling>.baseline\", \"horizontalBias\": 0..1, \"verticalBias\": 0..1, \"dimensionRatio\": \"16:9\"}. "
                + "Every child needs at least one horizontal (start/end) AND one vertical (top/bottom) constraint. "
                + "Use width \"0dp\" with both start and end constraints to fill the space between them.\n\n"
                + "RULES\n"
                + "1. Use only components from the catalog. Containers can have children; widgets can't.\n"
                + "2. ScrollView, NestedScrollView, HorizontalScrollView, TextInputLayout and SwipeRefreshLayout take exactly ONE child.\n"
                + "3. Put long content in a ScrollView/NestedScrollView with one vertical LinearLayout inside.\n"
                + "4. Use only the drawables, colors, layouts and strings listed in the project context; never invent resources.\n"
                + "5. Give every image and icon button an android:contentDescription in \"attributes\".\n"
                + "6. Prefer \"match_parent\" width for text fields and full-width buttons; use dp for fixed sizes and sp for text.\n"
                + "7. Keep it simple: no wrappers that do nothing, no Toolbar/ActionBar (Sketchware adds them from the screen settings).\n"
                + "8. When editing an existing layout, keep the ids of the views you keep (code uses them) and output the WHOLE new layout.\n\n"
                + "CATALOG (components available in this project)\n" + registry.promptCatalog();
    }

    @NonNull
    public static String user(@NonNull String request, @NonNull LayoutProjectContext project, boolean editing,
                              @NonNull String rootPreference, @NonNull List<String> recentRequests, boolean imagesAttached) {
        StringBuilder out = new StringBuilder();
        out.append("PROJECT CONTEXT\n");
        out.append("Layout file: ").append(project.currentLayout).append(".xml\n");
        out.append("Drawables: ").append(project.drawables.isEmpty() ? "(none: don't use src or drawable backgrounds)" : String.join(", ", project.drawables)).append('\n');
        out.append("Colors: ").append(project.colors.isEmpty() ? "(none: use hex colors or ?attr/ theme colors)" : String.join(", ", project.colorReferences())).append('\n');
        out.append("Layouts that can be included: ").append(project.layouts.isEmpty() ? "(none)" : String.join(", ", project.layouts)).append('\n');
        if (!project.fonts.isEmpty()) {
            out.append("Fonts (android:fontFamily=\"@font/name\"): ").append(String.join(", ", project.fonts)).append('\n');
        }
        out.append('\n');
        if (!recentRequests.isEmpty()) {
            out.append("Earlier requests for this layout (only for context):\n");
            for (String previous : recentRequests) out.append("- ").append(previous).append('\n');
            out.append('\n');
        }
        if (editing && !project.currentSpec.isEmpty()) {
            out.append("TASK: change the current layout below as requested. Return the complete updated layout.\n");
            out.append("CURRENT LAYOUT\n").append(project.currentSpec).append("\n\n");
        } else {
            out.append("TASK: create a new layout for this screen.\n");
        }
        if (!rootPreference.isEmpty()) {
            out.append("Use ").append(rootPreference).append(" as root.type.\n");
        }
        if (!project.referenceNotes.isEmpty()) {
            out.append("Reference notes: ").append(project.referenceNotes).append('\n');
        }
        if (imagesAttached) {
            out.append("Reference images are attached: reproduce their structure, spacing and hierarchy with the catalog components.\n");
        } else if (!project.referenceImages.isEmpty()) {
            out.append("Reference images were selected, but this model can't see images: rely on the notes.\n");
        }
        out.append("\nREQUEST\n").append(request.trim()).append('\n');
        return out.toString();
    }

    @NonNull
    public static String repair(@NonNull String previousAnswer, @NonNull List<String> errors) {
        StringBuilder out = new StringBuilder();
        out.append("Sketchware rejected your JSON. Fix ONLY these problems and return the complete corrected JSON object:\n");
        for (String error : errors) out.append("- ").append(error).append('\n');
        out.append("\nYOUR PREVIOUS ANSWER\n").append(LayoutSpecCompiler.extractJson(previousAnswer));
        return out.toString();
    }
}
