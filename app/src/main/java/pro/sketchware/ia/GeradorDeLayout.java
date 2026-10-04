package pro.sketchware.ia;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import pro.sketchware.SketchApplication;
import pro.sketchware.ia.layout.LayoutComponentRegistry;
import pro.sketchware.ia.layout.LayoutProjectContext;
import pro.sketchware.ia.layout.LayoutPrompts;
import pro.sketchware.ia.layout.LayoutSpecCompiler;
import pro.sketchware.ia.layout.LayoutVisionSupport;
import pro.sketchware.network.AiProviderService;

/**
 * Layout generation: the model answers with a JSON layout spec, {@link LayoutSpecCompiler} checks
 * it against the project and turns it into layout XML, and rejected answers go back to the model with
 * the exact errors (up to {@link #MAX_REPAIR_ATTEMPTS} times).
 */
public final class GeradorDeLayout {

    private static final String TAG = "GeradorDeLayout";
    private static final int MAX_REPAIR_ATTEMPTS = 2;
    private static final int MAX_HISTORY = 5;

    public static final class Result {
        /** Layout XML, ready for ViewBeanParser. */
        public final String xml;
        /** The accepted JSON spec. */
        public final String spec;

        Result(String xml, String spec) {
            this.xml = xml;
            this.spec = spec;
        }
    }

    private final String request;
    private final boolean editing;
    private final String rootPreference;
    private final LayoutProjectContext project;
    private final LayoutComponentRegistry registry;
    private final List<LayoutHistoryManager.HistoryEntry> history;

    public GeradorDeLayout(@NonNull String request, boolean editing, @NonNull String rootPreference,
                           @NonNull LayoutProjectContext project, @NonNull LayoutComponentRegistry registry,
                           List<LayoutHistoryManager.HistoryEntry> history) {
        this.request = request.trim();
        this.editing = editing;
        this.rootPreference = rootPreference;
        this.project = project;
        this.registry = registry;
        this.history = history == null ? new ArrayList<>() : history;
    }

    @NonNull
    public Result generate() throws IOException {
        Context context = SketchApplication.getContext();
        LayoutGeneratorModelSelector.SelectedModel model = LayoutGeneratorModelSelector.getCurrentChatModel(context);
        boolean vision = LayoutVisionSupport.supports(model.providerId, model.modelName);
        List<String> images = vision ? project.referenceImages : new ArrayList<>();
        Log.d(TAG, "Generating with " + model + ", vision=" + vision + ", images=" + images.size());

        String system = LayoutPrompts.system(registry);
        String user = LayoutPrompts.user(request, project, editing, rootPreference, recentRequests(), !images.isEmpty());
        AiProviderService service = AiProviderService.getInstance();
        String answer = service.sendTextMessage(model.providerId, model.modelName, system, user, images);

        LayoutSpecCompiler compiler = new LayoutSpecCompiler(registry, project);
        List<String> errors = new ArrayList<>();
        for (int attempt = 0; ; attempt++) {
            LayoutSpecCompiler.Result result = compiler.compile(answer);
            if (result.isValid()) {
                return new Result(result.xml, answer);
            }
            errors = result.errors;
            Log.d(TAG, "Attempt " + attempt + " rejected: " + errors);
            if (attempt >= MAX_REPAIR_ATTEMPTS) break;
            // The repair keeps the original request so the model fixes the layout without losing its goal.
            answer = service.sendTextMessage(model.providerId, model.modelName, system,
                    user + "\n\n" + LayoutPrompts.repair(answer, errors), images);
        }
        StringBuilder message = new StringBuilder("The AI layout still had problems after " + MAX_REPAIR_ATTEMPTS + " corrections:");
        for (int i = 0; i < Math.min(errors.size(), 6); i++) message.append("\n• ").append(errors.get(i));
        if (errors.size() > 6) message.append("\n• … ").append(errors.size() - 6).append(" more");
        throw new IOException(message.toString());
    }

    private List<String> recentRequests() {
        List<String> requests = new ArrayList<>();
        for (int i = history.size() - 1; i >= 0 && requests.size() < MAX_HISTORY; i--) {
            String previous = history.get(i).userPrompt;
            if (previous != null && !previous.isBlank()) requests.add(previous.trim());
        }
        return requests;
    }
}
