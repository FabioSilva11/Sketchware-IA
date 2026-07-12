package pro.sketchware.ia;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import pro.sketchware.SketchApplication;
import pro.sketchware.ia.layout.LayoutComponentRegistry;
import pro.sketchware.ia.layout.LayoutGenerationValidator;
import pro.sketchware.ia.layout.LayoutProjectContext;
import pro.sketchware.ia.layout.LayoutVisionSupport;
import pro.sketchware.ia.layout.SketchwareLayoutCompiler;
import pro.sketchware.network.AiProviderService;

/** Structured, validated Sketchware layout generation pipeline. */
public final class GeradorDeLayout {
    private static final String TAG = "GeradorDeLayout";
    private static final int MAX_REPAIR_ATTEMPTS = 2;

    private final String request;
    private final List<LayoutHistoryManager.HistoryEntry> history;
    private final LayoutProjectContext projectContext;
    private final LayoutComponentRegistry componentRegistry = new LayoutComponentRegistry();
    private final SketchwareLayoutCompiler compiler = new SketchwareLayoutCompiler();

    public GeradorDeLayout(String texto) {
        this(texto, null, new ArrayList<>(), null, new ArrayList<>(), new ArrayList<>());
    }

    public GeradorDeLayout(String texto, String currentLayout) {
        this(texto, currentLayout, new ArrayList<>(), null, new ArrayList<>(), new ArrayList<>());
    }

    public GeradorDeLayout(String texto, String currentLayout, List<LayoutHistoryManager.HistoryEntry> history) {
        this(texto, currentLayout, history, null, new ArrayList<>(), new ArrayList<>());
    }

    public GeradorDeLayout(String texto, String currentLayout,
                           List<LayoutHistoryManager.HistoryEntry> history,
                           String referenceContext, List<String> projectDrawables) {
        this(texto, currentLayout, history, referenceContext, projectDrawables, new ArrayList<>());
    }

    public GeradorDeLayout(String texto, String currentLayout,
                           List<LayoutHistoryManager.HistoryEntry> history,
                           String referenceContext, List<String> projectDrawables,
                           List<String> referenceImageDataUrls) {
        request = texto == null ? "" : texto.trim();
        this.history = history == null ? new ArrayList<>() : history;
        projectContext = new LayoutProjectContext(
                currentLayout,
                projectDrawables,
                referenceContext,
                referenceImageDataUrls
        );
    }

    public String getTexto() {
        return request;
    }

    public String gerarLayout() throws IOException {
        Context context = SketchApplication.getContext();
        LayoutGeneratorModelSelector.SelectedModel selectedModel =
                LayoutGeneratorModelSelector.getCurrentChatModel(context);
        boolean visionEnabled = LayoutVisionSupport.supports(selectedModel.providerId, selectedModel.modelName);
        List<String> images = visionEnabled ? projectContext.referenceImages : new ArrayList<>();

        Log.d(TAG, "Layout generation using deterministic model: " + selectedModel
                + ", vision=" + visionEnabled + ", images=" + images.size());

        String systemPrompt = "You are the Sketchware native layout planner. "
                + "Follow the supplied component catalog and defaults exactly. "
                + SketchwareLayoutCompiler.contractPrompt();
        String userPrompt = buildPrompt(visionEnabled);

        String response = AiProviderService.getInstance().sendTextMessage(
                selectedModel.providerId,
                selectedModel.modelName,
                systemPrompt,
                userPrompt,
                images
        );

        LayoutGenerationValidator validator = new LayoutGenerationValidator(componentRegistry, projectContext.drawables);
        String lastErrors = "";
        for (int attempt = 0; attempt <= MAX_REPAIR_ATTEMPTS; attempt++) {
            try {
                String xml = compiler.compile(response);
                LayoutGenerationValidator.Result validation = validator.validate(xml);
                if (validation.isValid()) return xml;
                lastErrors = validation.repairPrompt();
            } catch (Exception e) {
                lastErrors = "Invalid structured response: "
                        + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
            if (attempt >= MAX_REPAIR_ATTEMPTS) break;
            String repairPrompt = "Repair the previous structured layout.\n"
                    + "Original request:\n" + request + "\n\n"
                    + "Validation errors:\n- " + lastErrors + "\n\n"
                    + "Previous response:\n" + response + "\n\n"
                    + SketchwareLayoutCompiler.contractPrompt();
            response = AiProviderService.getInstance().sendTextMessage(
                    selectedModel.providerId, selectedModel.modelName,
                    systemPrompt, repairPrompt, images);
        }
        throw new IOException("Layout incompatible with Sketchware: " + lastErrors);
    }

    private String buildPrompt(boolean visionEnabled) {
        StringJoiner prompt = new StringJoiner("\n");
        prompt.add(projectContext.toPrompt(componentRegistry));
        if (!projectContext.referenceImages.isEmpty() && !visionEnabled) {
            prompt.add("Reference images were selected, but the configured model is not vision-capable. "
                    + "Use only the written reference notes and do not claim to have inspected the images.");
        }
        appendHistorySummary(prompt);
        prompt.add("User intent:");
        prompt.add(request);
        prompt.add("");
        prompt.add(SketchwareLayoutCompiler.contractPrompt());
        return prompt.toString();
    }

    private void appendHistorySummary(StringJoiner prompt) {
        if (history.isEmpty()) return;
        prompt.add("Recent accepted intent examples (do not copy obsolete hierarchy):");
        int chars = 0;
        for (int i = history.size() - 1; i >= 0 && chars < 4000; i--) {
            LayoutHistoryManager.HistoryEntry entry = history.get(i);
            String line = "- " + safe(entry.userPrompt);
            prompt.add(line);
            chars += line.length();
        }
        prompt.add("");
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
