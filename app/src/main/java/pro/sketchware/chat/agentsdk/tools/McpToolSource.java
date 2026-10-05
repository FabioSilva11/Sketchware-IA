package pro.sketchware.chat.agentsdk.tools;

import android.content.SharedPreferences;

import pro.sketchware.chat.agentsdk.AgentToolResult;
import pro.sketchware.chat.agentsdk.ToolCapability;
import pro.sketchware.chat.port.VoidPortMcpChannel;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Registers the configured MCP servers as registry citizens through the
 * {@link VoidPortMcpChannel} bridge. The model catalog is still built
 * exclusively from {@link AxionToolRegistry#modelVisibleTools()}; MCP tool
 * definitions (declared, cached or discovered) become DIRECT FUNCTION
 * {@link ToolRegistration}s whose executors call
 * {@link VoidPortMcpChannel#callTool(SharedPreferences, String, JSONObject)}.
 *
 * <p>Discovery is best-effort and never blocks chat startup: servers without
 * an Android-accessible HTTP URL surface as the generic {@code mcp_&lt;server&gt;_call_tool}
 * entry and network discovery happens off the main thread inside the bridge.</p>
 */
public final class McpToolSource {

    private McpToolSource() {
    }

    /**
     * Registers every enabled MCP server tool from {@code prefs}. When
     * {@code prefs} is {@code null} (JVM tests, no Android context) nothing
     * is registered.
     */
    public static void discover(SharedPreferences prefs, AxionToolRegistry registry) {
        if (prefs == null) {
            return;
        }
        try {
            JSONArray functionTools = VoidPortMcpChannel.getToolsAsMCP(prefs);
            for (int i = 0; i < functionTools.length(); i++) {
                JSONObject entry = functionTools.optJSONObject(i);
                JSONObject fn = entry == null ? null : entry.optJSONObject("function");
                if (fn == null) {
                    continue;
                }
                String name = fn.optString("name", "").trim();
                if (name.isEmpty()) {
                    continue;
                }
                JSONObject parameters = fn.optJSONObject("parameters");
                if (parameters == null) {
                    parameters = new JSONObject();
                }
                String serverName = VoidPortMcpChannel.resolveServerNameForTool(prefs, name);
                ToolRegistration registration = ToolRegistration.builder(ToolSpec.function(
                                ToolName.plain(name),
                                fn.optString("description", "MCP tool via " + name + "."),
                                parameters))
                        .executor(ctx -> {
                            String out = VoidPortMcpChannel.callTool(prefs, name, ctx.functionArguments());
                            return VoidPortMcpChannel.isFailure(out)
                                    ? AgentToolResult.error(out) : AgentToolResult.success(out);
                        })
                        .source(serverName == null ? "mcp" : "mcp:" + serverName)
                        .capability(ToolCapability.NETWORK)
                        .build();
                registry.register(registration);
            }
        } catch (Exception ignored) {
            // An unavailable or irregular MCP config must not prevent chat startup.
        }
    }
}