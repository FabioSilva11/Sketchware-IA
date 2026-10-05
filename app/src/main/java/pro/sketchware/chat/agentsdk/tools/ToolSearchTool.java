package pro.sketchware.chat.agentsdk.tools;

import pro.sketchware.chat.agentsdk.AgentToolResult;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * {@code tool_search} executor: discovers deferred tools from the registry by
 * free-text query and activates them (Codex {@code tools/tool_search.rs}
 * parity). Returns short entries the model can call directly by name.
 *
 * <p>Deferred tools are absent from the catalog; the model finds them HERE.
 * A match both informs the model and {@code activateDeferred}s the name so
 * the router permits the call.</p>
 */
public final class ToolSearchTool implements ToolExecutor {

    /** Maximum results returned per search (Codex caps search responses). */
    public static final int DEFAULT_LIMIT = 10;

    private final AxionToolRegistry registry;

    public ToolSearchTool(AxionToolRegistry registry) {
        this.registry = registry;
    }

    /** Executes one tool_search call. */
    @Override
    public AgentToolResult execute(ToolExecutionContext ctx) {
        JSONObject args = ctx.functionArguments();
        String query = args == null ? "" : args.optString("query", "").trim();
        int limit = DEFAULT_LIMIT;
        if (args != null && args.has("limit")) {
            limit = Math.min(Math.max(1, args.optInt("limit", DEFAULT_LIMIT)), 25);
        }
        if (query.isEmpty()) {
            return AgentToolResult.error("Error: 'query' is required for tool_search.");
        }

        List<ToolRegistration> deferred = registry.deferredTools();
        String normalized = query.toLowerCase(Locale.ROOT);
        List<ToolRegistration> matches = new ArrayList<>();
        for (ToolRegistration reg : deferred) {
            if (matches.size() >= limit) {
                break;
            }
            if (matchesQuery(reg, normalized)) {
                matches.add(reg);
            }
        }

        if (matches.isEmpty()) {
            return AgentToolResult.success(
                    "No tools matched the query. The query targets tool names and descriptions.");
        }

        // Activate discovered deferred tools so the router permits calling them.
        ToolName[] activated = new ToolName[matches.size()];
        for (int i = 0; i < matches.size(); i++) {
            activated[i] = matches.get(i).spec().name();
        }
        registry.activateDeferred(activated);

        try {
            JSONArray results = new JSONArray();
            for (ToolRegistration match : matches) {
                results.put(new JSONObject()
                        .put("name", match.spec().name().qualifiedName())
                        .put("description", match.spec().description()));
            }
            return AgentToolResult.success(new JSONObject()
                    .put("tools", results)
                    .put("total", results.length())
                    .toString());
        } catch (org.json.JSONException e) {
            return AgentToolResult.error("Error: could not serialize search results.");
        }
    }

    private static boolean matchesQuery(ToolRegistration reg, String normalizedQuery) {
        // Codex-style token matching: split both sides on non-alphanumeric
        // boundaries and require every query token to appear in the tool's
        // searchable text. Substrings like "deferred_tool_3" never leak into
        // "deferred_tool_30" because "3" is a distinct token.
        java.util.Set<String> docTokens = tokenize(
                reg.spec().name().qualifiedName() + " " + reg.spec().description());
        for (String token : tokenize(normalizedQuery)) {
            if (!docTokens.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private static java.util.Set<String> tokenize(String text) {
        java.util.Set<String> tokens = new java.util.LinkedHashSet<>();
        for (String part : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (!part.isEmpty() && part.length() <= 64) {
                tokens.add(part);
            }
        }
        return tokens;
    }
}