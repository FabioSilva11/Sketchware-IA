package pro.sketchware.ia.layout;

import java.util.Locale;

public final class LayoutVisionSupport {
    private LayoutVisionSupport() {}

    public static boolean supports(String providerId, String modelName) {
        String value = ((providerId == null ? "" : providerId) + "/" + (modelName == null ? "" : modelName))
                .toLowerCase(Locale.US);
        return value.contains("gemini") || value.contains("gpt-4o") || value.contains("gpt-4.1")
                || value.contains("vision") || value.contains("-vl") || value.contains("claude-3")
                || value.contains("claude-sonnet") || value.contains("claude-opus");
    }
}
