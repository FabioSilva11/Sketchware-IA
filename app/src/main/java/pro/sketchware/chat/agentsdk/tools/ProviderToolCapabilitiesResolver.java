package pro.sketchware.chat.agentsdk.tools;

import pro.sketchware.chat.port.VoidPortModelCapabilities;

/**
 * Resolves the {@link ProviderToolCapabilities} of a concrete provider/model
 * pair at the point of serialization. The gateway consults this BEFORE
 * touching the catalog so {@code ToolSpec.Type.FREEFORM} stays FREEFORM until
 * the capability decides the wire representation.
 *
 * <p>Every provider family this app talks to today (OpenAI-compatible,
 * Anthropic, Gemini, Ollama/XML) serialises tools as function envelopes at its
 * HTTP boundary, so the honest declared profile is {@link #FUNCTION_ONLY}:
 * freeform/namespace flattening happens through the EXPLICIT fallback path and
 * is recorded; native freeform/namespace/tool_search wire shapes are never
 * claimed where the transport cannot carry them.</p>
 */
public final class ProviderToolCapabilitiesResolver {

    private ProviderToolCapabilitiesResolver() {
    }

    /**
     * Maps the provider/model identity to the transport capability.
     *
     * @param providerId frozen provider id of the turn (may be {@code ""})
     * @param modelName  frozen model name of the turn (may be {@code ""})
     */
    public static ProviderToolCapabilities resolve(String providerId, String modelName) {
        // The model capability table is the future decision point: when a
        // family starts carrying freeform/namespace/tool_search natively this
        // switch grows per ToolFormat. Today the table has no such entry, so
        // every format resolves to the function-only profile on purpose.
        VoidPortModelCapabilities.ToolFormat format =
                VoidPortModelCapabilities.expectedToolFormat(providerId, modelName);
        return ProviderToolCapabilities.FUNCTION_ONLY;
    }
}