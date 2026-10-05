package pro.sketchware.chat.agentsdk.tools;

import org.json.JSONArray;

/**
 * Result of a capability-aware catalog serialization: the transport payload
 * PLUS explicit flags recording whether any tool kind had to fall back to a
 * different wire shape. Tests and hosts use the flags to verify that a
 * downgrade is declared and never silent.
 */
public final class ProviderCatalogPayload {

    private final JSONArray payload;
    private final boolean freeformFellBack;
    private final boolean namespaceFellBack;
    private final boolean toolSearchFellBack;

    ProviderCatalogPayload(JSONArray payload, boolean freeformFellBack,
                           boolean namespaceFellBack, boolean toolSearchFellBack) {
        this.payload = payload == null ? new JSONArray() : payload;
        this.freeformFellBack = freeformFellBack;
        this.namespaceFellBack = namespaceFellBack;
        this.toolSearchFellBack = toolSearchFellBack;
    }

    /** The wire payload to hand to the transport. */
    public JSONArray payload() {
        return payload;
    }

    /** True when a freeform tool was downgraded to a function envelope. */
    public boolean freeformFellBack() {
        return freeformFellBack;
    }

    /** True when namespace declarations were flattened to top-level functions. */
    public boolean namespaceFellBack() {
        return namespaceFellBack;
    }

    /** True when a tool_search entry was downgraded to a function envelope. */
    public boolean toolSearchFellBack() {
        return toolSearchFellBack;
    }

    /** True when any kind needed an explicit fallback on this transport. */
    public boolean hadExplicitFallback() {
        return freeformFellBack || namespaceFellBack || toolSearchFellBack;
    }
}