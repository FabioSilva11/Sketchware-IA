package pro.sketchware.chat.agentsdk.tools;

/**
 * Declared tool-serialization capabilities of a provider TRANSPORT. This is
 * metadata of the wire, never a second catalog: the canonical
 * {@link ToolCatalog} is provider-agnostic and {@link ToolSpecSerializer}
 * picks the wire representation per tool kind based on these capabilities.
 *
 * <p>The capability distinguishes what a provider carries NATIVELY from what
 * is only possible through an EXPLICIT fallback adapter:</p>
 * <ul>
 *   <li><b>supportsFunctionTools</b> — OpenAI-style
 *       {@code {"type":"function","function":{...}}} envelopes;</li>
 *   <li><b>supportsFreeformTools</b> — Codex-style
 *       {@code {"type":"freeform","freeform":{...}}} raw-input tools
 *       (apply_patch);</li>
 *   <li><b>supportsNamespaces</b> — {@code {"type":"namespace",...}} grouped
 *       declarations instead of flattened child functions;</li>
 *   <li><b>supportsToolSearch</b> — the dedicated {@code tool_search} wire
 *       shape that carries deferred-tool discovery;</li>
 *   <li><b>supportsDeferredTools</b> — the transport can represent
 *       loader-deferred tools (only when native tool_search is available);</li>
 *   <li><b>supportsOutputSchema</b> — whether the transport accepts an
 *       {@code output_schema} inside a function declaration. Function-only /
 *       OpenAI-compatible transports today reject it, so {@link #FUNCTION_ONLY}
 *       declares it unsupported and the serializer drops it at the wire while
 *       {@link ToolSpec#outputSchema()} stays intact internally;</li>
 *   <li>the three <b>...FallbackTo...</b> flags declare an EXPLICIT downgrade
 *       path. The serializer only downgrades a kind when the capability says
 *       the fallback exists — never silently.</li>
 * </ul>
 *
 * <p>Two named profiles cover the transports this app talks to today:
 * {@link #FUNCTION_ONLY} (every current provider family serialises function
 * envelopes at its HTTP boundary; freeform and namespace flattening are
 * declared and recorded) and {@link #NATIVE_ALL} (Codex-style per-kind wire,
 * used by tests and future native transports).</p>
 */
public final class ProviderToolCapabilities {

    private final boolean supportsFunctionTools;
    private final boolean supportsFreeformTools;
    private final boolean supportsNamespaces;
    private final boolean supportsToolSearch;
    private final boolean supportsDeferredTools;
    private final boolean supportsOutputSchema;
    private final boolean freeformFallbackToFunction;
    private final boolean namespaceFallbackToFunctions;
    private final boolean toolSearchFallbackToFunction;

    private ProviderToolCapabilities(Builder b) {
        this.supportsFunctionTools = b.supportsFunctionTools;
        this.supportsFreeformTools = b.supportsFreeformTools;
        this.supportsNamespaces = b.supportsNamespaces;
        this.supportsToolSearch = b.supportsToolSearch;
        this.supportsDeferredTools = b.supportsDeferredTools;
        this.supportsOutputSchema = b.supportsOutputSchema;
        this.freeformFallbackToFunction = b.freeformFallbackToFunction;
        this.namespaceFallbackToFunctions = b.namespaceFallbackToFunctions;
        this.toolSearchFallbackToFunction = b.toolSearchFallbackToFunction;
    }

    public boolean supportsFunctionTools() {
        return supportsFunctionTools;
    }

    public boolean supportsFreeformTools() {
        return supportsFreeformTools;
    }

    public boolean supportsNamespaces() {
        return supportsNamespaces;
    }

    public boolean supportsToolSearch() {
        return supportsToolSearch;
    }

    public boolean supportsDeferredTools() {
        return supportsDeferredTools;
    }

    /** Explicit freeform → function downgrade available (never implied). */
    public boolean freeformFallbackToFunction() {
        return freeformFallbackToFunction;
    }

    /** Explicit namespace → flattened functions downgrade available. */
    public boolean namespaceFallbackToFunctions() {
        return namespaceFallbackToFunctions;
    }

    /** Explicit tool_search → function downgrade available. */
    public boolean toolSearchFallbackToFunction() {
        return toolSearchFallbackToFunction;
    }

    /**
     * Whether the transport accepts {@code output_schema} inside a function
     * declaration. {@code false} for function-only / OpenAI-compatible
     * transports: the serializer strips the field at the wire while the
     * {@code ToolSpec} keeps its output schema internally.
     */
    public boolean supportsOutputSchema() {
        return supportsOutputSchema;
    }

    /**
     * Function-only transports (every provider family in this app today):
     * freeform and namespace are carried through the EXPLICIT fallback path;
     * tool_search has no representable shape and is omitted (never flattened
     * into a function, never claimed as supported). {@code output_schema} is
     * NOT accepted on this wire, so it is dropped at serialization.
     */
    public static final ProviderToolCapabilities FUNCTION_ONLY = builder()
            .supportsFunctionTools(true)
            .supportsFreeformTools(false)
            .freeformFallbackToFunction(true)
            .supportsNamespaces(false)
            .namespaceFallbackToFunctions(true)
            .supportsToolSearch(false)
            .toolSearchFallbackToFunction(false)
            .supportsDeferredTools(false)
            .supportsOutputSchema(false)
            .build();

    /** Codex-style per-kind wire: every kind is carried natively, no fallback. */
    public static final ProviderToolCapabilities NATIVE_ALL = builder()
            .supportsFunctionTools(true)
            .supportsFreeformTools(true)
            .supportsNamespaces(true)
            .supportsToolSearch(true)
            .supportsDeferredTools(true)
            .build();

    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder. Defaults: function-only, no fallbacks declared. */
    public static final class Builder {
        private boolean supportsFunctionTools = true;
        private boolean supportsFreeformTools;
        private boolean supportsNamespaces;
        private boolean supportsToolSearch;
        private boolean supportsDeferredTools;
        private boolean supportsOutputSchema = true;
        private boolean freeformFallbackToFunction;
        private boolean namespaceFallbackToFunctions;
        private boolean toolSearchFallbackToFunction;

        public Builder supportsFunctionTools(boolean value) {
            this.supportsFunctionTools = value;
            return this;
        }

        public Builder supportsFreeformTools(boolean value) {
            this.supportsFreeformTools = value;
            return this;
        }

        public Builder supportsNamespaces(boolean value) {
            this.supportsNamespaces = value;
            return this;
        }

        public Builder supportsToolSearch(boolean value) {
            this.supportsToolSearch = value;
            return this;
        }

        public Builder supportsDeferredTools(boolean value) {
            this.supportsDeferredTools = value;
            return this;
        }

        public Builder supportsOutputSchema(boolean value) {
            this.supportsOutputSchema = value;
            return this;
        }

        public Builder freeformFallbackToFunction(boolean value) {
            this.freeformFallbackToFunction = value;
            return this;
        }

        public Builder namespaceFallbackToFunctions(boolean value) {
            this.namespaceFallbackToFunctions = value;
            return this;
        }

        public Builder toolSearchFallbackToFunction(boolean value) {
            this.toolSearchFallbackToFunction = value;
            return this;
        }

        public ProviderToolCapabilities build() {
            return new ProviderToolCapabilities(this);
        }
    }
}