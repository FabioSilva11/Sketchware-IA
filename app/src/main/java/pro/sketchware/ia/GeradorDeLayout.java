package pro.sketchware.ia;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import pro.sketchware.SketchApplication;
import pro.sketchware.network.AiProviderService;

public final class GeradorDeLayout {
    private static final String TAG = "GeradorDeLayout";

    private final String texto;
    private final String currentLayout;
    private final List<LayoutHistoryManager.HistoryEntry> history;
    private final String referenceContext;
    private final List<String> projectDrawables;

    public GeradorDeLayout(String texto) {
        this(texto, null, new ArrayList<>(), null, new ArrayList<>());
    }

    public GeradorDeLayout(String texto, String currentLayout) {
        this(texto, currentLayout, new ArrayList<>(), null, new ArrayList<>());
    }

    public GeradorDeLayout(String texto, String currentLayout, List<LayoutHistoryManager.HistoryEntry> history) {
        this(texto, currentLayout, history, null, new ArrayList<>());
    }

    public GeradorDeLayout(String texto, String currentLayout, List<LayoutHistoryManager.HistoryEntry> history,
                           String referenceContext, List<String> projectDrawables) {
        this.texto = texto;
        this.currentLayout = currentLayout;
        this.history = history != null ? history : new ArrayList<>();
        this.referenceContext = referenceContext;
        this.projectDrawables = projectDrawables != null ? projectDrawables : new ArrayList<>();
    }

    private String montarPromptBase() {
        StringJoiner prompt = new StringJoiner("\n");

        prompt.add("You generate Android XML layouts that must be compatible with Sketchware.");
        prompt.add("Return only XML. No markdown, no explanations, no comments.");
        prompt.add("Return exactly one root ViewGroup with all generated views inside it.");
        prompt.add("The root ViewGroup must be a neutral LinearLayout unless the user explicitly asks otherwise.");
        prompt.add("");
        prompt.add("== RULES ==");
        prompt.add("1. Use only Sketchware-supported components and attributes.");
        prompt.add("2. Every interactive view must have android:id.");
        prompt.add("3. Keep the hierarchy simple, readable and mobile friendly.");
        prompt.add("4. Use valid Android XML attribute names and values.");
        prompt.add("5. Prefer 8dp or 16dp spacing and text sizes in sp.");
        prompt.add("6. Do not use Compose, binding expressions or unsupported custom XML syntax.");
        prompt.add("7. Keep the root layout clean (no backgrounds, borders or padding) unless explicitly requested.");
        prompt.add("8. Use layout_weight or match_parent to ensure layouts are responsive across different screen sizes.");
        prompt.add("9. Never add root margins, root padding, or root background unless the user explicitly asks for root styling.");
        prompt.add("10. Use @drawable/name references only when the resource name is listed below.");
        prompt.add("");
        prompt.add("== SUPPORTED COMPONENTS ==");
        Map<String, List<String>> supported = getViewBeanParserSupportedTypes();
        prompt.add("Layouts: " + String.join(", ", supported.get("layouts")));
        prompt.add("Widgets: " + String.join(", ", supported.get("widgets")));
        prompt.add("");

        if (!projectDrawables.isEmpty()) {
            prompt.add("== AVAILABLE PROJECT DRAWABLES AND ICONS ==");
            prompt.add(String.join(", ", projectDrawables));
            prompt.add("Use these for ImageView android:src or app:srcCompat when the requested layout needs icons/images.");
            prompt.add("");
        }

        if (referenceContext != null && !referenceContext.trim().isEmpty()) {
            prompt.add("== REFERENCE IMAGE ==");
            prompt.add(referenceContext.trim());
            prompt.add("Use the reference to infer structure, spacing, visual rhythm, and icon/image placement.");
            prompt.add("");
        }
        prompt.add("== DESIGN GOAL ==");
        prompt.add("Create a clean, professional Android layout with the following standards:");
        prompt.add("1. Buttons must always have centered text (android:gravity=\"center\").");
        prompt.add("2. Use consistent padding (8dp or 16dp) and margins (8dp or 16dp) for a balanced look.");
        prompt.add("3. Prefer standard widgets (Button, EditText, Switch, etc.) unless specifically asked otherwise.");
        prompt.add("4. Ensure all interactive elements have a minimum touch target height/width of 48dp.");
        prompt.add("5. Keep the hierarchy simple and avoid unnecessary nesting.");
        prompt.add("6. Ensure high readability and enough whitespace.");
        prompt.add("");

        if (!history.isEmpty()) {
            prompt.add("== PREVIOUS REQUESTS ==");
            appendHistory(prompt);
            prompt.add("");
        }

        if (currentLayout != null && !currentLayout.trim().isEmpty()) {
            prompt.add("== CURRENT LAYOUT ==");
            prompt.add(currentLayout.trim());
            prompt.add("");
            prompt.add("== TASK ==");
            prompt.add("Refine the current layout according to this request:");
            prompt.add(texto);
        } else {
            prompt.add("== TASK ==");
            prompt.add(texto);
        }

        return prompt.toString();
    }

    private void appendHistory(StringJoiner prompt) {
        int totalLength = 0;
        final int maxHistoryLength = 20000;

        for (int i = history.size() - 1; i >= 0; i--) {
            LayoutHistoryManager.HistoryEntry entry = history.get(i);
            String block = "Request: " + entry.userPrompt + "\n"
                    + "Layout:\n" + entry.generatedLayout + "\n";

            if (totalLength + block.length() > maxHistoryLength) {
                break;
            }

            prompt.add(block);
            totalLength += block.length();
        }
    }

    private Map<String, List<String>> getViewBeanParserSupportedTypes() {
        Map<String, List<String>> types = new HashMap<>();
        List<String> layouts = Arrays.asList(
                "LinearLayout", "RelativeLayout", "HorizontalScrollView", "ScrollView",
                "TabLayout", "BottomNavigationView", "CardView", "CollapsingToolbarLayout",
                "TextInputLayout", "SwipeRefreshLayout", "RadioGroup", "NestedScrollView"
        );

        List<String> widgets = Arrays.asList(
                "Button", "TextView", "EditText", "ImageView", "WebView", "ProgressBar", "ListView",
                "Spinner", "CheckBox", "Switch", "SeekBar", "CalendarView", "AdView", "MapView",
                "RadioButton", "RatingBar", "VideoView", "SearchView", "AutoCompleteTextView",
                "MultiAutoCompleteTextView", "GridView", "AnalogClock", "DatePicker", "TimePicker",
                "DigitalClock", "ViewPager", "BadgeView", "PatternLockView", "WaveSideBar",
                "MaterialButton", "SignInButton", "CircleImageView", "LottieAnimationView",
                "YoutubePlayerView", "OTPView", "CodeView", "RecyclerView", "ImageButton",
                "MaterialSwitch", "TextInputEditText"
        );

        types.put("layouts", layouts);
        types.put("widgets", widgets);
        return types;
    }

    public String getTexto() {
        return texto;
    }

    public String gerarLayout() throws IOException {
        Context context = SketchApplication.getContext();
        
        // Randomly select a configured AI provider and model
        LayoutGeneratorModelSelector.SelectedModel selectedModel = 
                LayoutGeneratorModelSelector.selectRandomModel(context);
        
        Log.d(TAG, "Layout generation using: " + selectedModel);
        
        // Temporarily apply the selected model
        LayoutGeneratorModelSelector.SelectedModel previousModel = 
                LayoutGeneratorModelSelector.applyModelSelection(context, selectedModel);
        
        try {
            String systemPrompt = "You generate Sketchware-compatible Android XML layouts. "
                    + "Return only one XML root ViewGroup. Do not use markdown, explanations or comments.";
            String initialLayout = cleanXmlLayout(AiProviderService.getInstance().sendTextMessage(
                    systemPrompt,
                    montarPromptBase()
            ));
            if (!looksLikeXml(initialLayout)) {
                throw new IOException("A resposta da IA não retornou XML utilizável.");
            }

            try {
                String instructions = "Refine this Android XML for Sketchware. Keep it valid, compact, well-indented, "
                        + "and do not add explanations or markdown. Preserve the requested behavior. "
                        + "Remove root margins, root padding, and root background unless explicitly requested.";
                String refinePrompt = instructions
                        + "\n\nCurrent XML:\n"
                        + initialLayout
                        + "\n\nOriginal request:\n"
                        + texto;
                String refinedLayout = cleanXmlLayout(AiProviderService.getInstance().sendTextMessage(systemPrompt, refinePrompt));
                return looksLikeXml(refinedLayout) ? refinedLayout : initialLayout;
            } catch (IOException ignored) {
                return initialLayout;
            }
        } finally {
            // Restore the previous model selection
            LayoutGeneratorModelSelector.restoreModelSelection(context, previousModel);
        }
    }

    private boolean looksLikeXml(String layout) {
        if (layout == null) {
            return false;
        }
        String trimmed = layout.trim();
        return !trimmed.isEmpty() && trimmed.contains("<") && trimmed.contains(">");
    }

    private String cleanXmlLayout(String layout) {
        if (layout == null) {
            return "";
        }

        String cleaned = layout
                .replace("```xml", "")
                .replace("```", "")
                .trim();

        cleaned = cleaned.replaceFirst("^<\\?xml[^>]*>\\s*", "");

        int firstTag = cleaned.indexOf('<');
        int lastTag = cleaned.lastIndexOf('>');
        if (firstTag >= 0 && lastTag > firstTag) {
            cleaned = cleaned.substring(firstTag, lastTag + 1);
        }

        return cleaned.trim();
    }
}
