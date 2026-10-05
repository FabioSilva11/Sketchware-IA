package pro.sketchware.chat.port;

import android.content.SharedPreferences;
import android.os.Looper;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import pro.sketchware.chat.SslUtils;

/**
 * Android-safe port of electron-main/mcpChannel.ts.
 *
 * The mobile app cannot spawn desktop stdio/SSE MCP clients reliably, so this
 * class preserves config parsing, status reporting and tool naming semantics.
 */
public final class VoidPortMcpChannel {
    /** Latest MCP version implemented by this HTTP client. */
    public static final String PROTOCOL_VERSION = "2026-07-28";
    /** Legacy Streamable-HTTP revision kept as an interoperability fallback. */
    public static final String LEGACY_PROTOCOL_VERSION = "2025-06-18";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");
    private static final OkHttpClient HTTP_CLIENT = SslUtils.relaxedClientBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    private static final ConcurrentHashMap<String, McpSession> SESSIONS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ToolCatalogCache> DISCOVERED_TOOLS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Thread, Call> ACTIVE_HTTP_CALLS = new ConcurrentHashMap<>();

    private VoidPortMcpChannel() {
    }


    private static final class ToolCatalogCache {
        final JSONArray tools;
        final long expiresAtMs;
        final String cacheScope;

        ToolCatalogCache(JSONArray tools, long expiresAtMs, String cacheScope) {
            this.tools = tools == null ? new JSONArray() : tools;
            this.expiresAtMs = expiresAtMs;
            this.cacheScope = cacheScope == null ? "private" : cacheScope;
        }

        boolean isFresh() {
            return expiresAtMs > System.currentTimeMillis();
        }
    }

    private static final class JsonRpcResponse {
        final JSONObject body;
        final String sessionId;

        JsonRpcResponse(JSONObject body, String sessionId) {
            this.body = body == null ? new JSONObject() : body;
            this.sessionId = sessionId == null ? "" : sessionId;
        }
    }

    /** Stateful Streamable-HTTP MCP connection negotiated through initialize. */
    private static final class McpSession {
        final String url;
        final String sessionId;
        final String protocolVersion;
        final JSONObject serverCapabilities;

        McpSession(String url, String sessionId, String protocolVersion, JSONObject serverCapabilities) {
            this.url = url == null ? "" : url;
            this.sessionId = sessionId == null ? "" : sessionId;
            this.protocolVersion = protocolVersion == null ? "" : protocolVersion;
            this.serverCapabilities = serverCapabilities == null ? new JSONObject() : serverCapabilities;
        }
    }

    public static final class ServerStatus {
        public final String name;
        public final String command;
        public final boolean enabled;
        public final String status;

        ServerStatus(String name, String command, boolean enabled, String status) {
            this.name = name == null ? "" : name;
            this.command = command == null ? "" : command;
            this.enabled = enabled;
            this.status = status == null ? "offline" : status;
        }
    }

    public static List<ServerStatus> readServerStatuses(SharedPreferences prefs) {
        List<ServerStatus> result = new ArrayList<>();
        JSONObject servers = VoidPortSettings.readMcpConfigObject(prefs).optJSONObject("mcpServers");
        JSONArray names = servers == null ? null : servers.names();
        for (int i = 0; names != null && i < names.length(); i++) {
            String name = names.optString(i, "");
            JSONObject server = servers.optJSONObject(name);
            if (server == null) {
                continue;
            }
            boolean enabled = server.optBoolean("enabled", true);
            String command = server.optString("command", "");
            String url = server.optString("url", "");
            if (command.isEmpty()) {
                command = url;
            }
            String status = !enabled ? "offline" : !url.isEmpty() ? "http-jsonrpc" : "stdio-config-only";
            result.add(new ServerStatus(name, command, enabled, status));
        }
        return result;
    }

    public static String buildPromptSummary(SharedPreferences prefs) {
        List<ServerStatus> servers = readServerStatuses(prefs);
        if (servers.isEmpty()) {
            return "MCP Android bridge: no configured servers.";
        }
        StringBuilder builder = new StringBuilder("MCP Android bridge: URL servers are callable through JSON-RPC HTTP; command/stdio servers need an Android-accessible URL endpoint.\n");
        for (ServerStatus server : servers) {
            builder.append("- ")
                    .append(server.name)
                    .append(" [")
                    .append(server.status)
                    .append("] ");
            if (!server.command.isEmpty()) {
                builder.append(server.command);
            }
            builder.append("\n");
        }
        return builder.toString().trim();
    }

    public static JSONArray getToolsAsMCP(SharedPreferences prefs) {
        JSONArray result = new JSONArray();
        JSONObject servers = VoidPortSettings.readMcpConfigObject(prefs).optJSONObject("mcpServers");
        JSONArray names = servers == null ? null : servers.names();
        for (int i = 0; names != null && i < names.length(); i++) {
            String serverName = names.optString(i, "");
            JSONObject server = servers.optJSONObject(serverName);
            if (server == null || !server.optBoolean("enabled", true)) {
                continue;
            }
            JSONArray declaredTools = firstArray(server, "tools", "toolDefinitions");
            if (declaredTools != null && declaredTools.length() > 0) {
                appendDeclaredTools(result, serverName, declaredTools);
                continue;
            }
            if (!server.optString("url", "").trim().isEmpty()) {
                String key = toolCacheKey(serverName, server);
                ToolCatalogCache cached = DISCOVERED_TOOLS.get(key);
                JSONArray discoveredTools = cached != null && cached.isFresh() ? cached.tools : null;
                if (discoveredTools == null && Looper.myLooper() != Looper.getMainLooper()) {
                    try {
                        JSONObject response = request(serverName, server, "tools/list", new JSONObject());
                        discoveredTools = response.optJSONArray("tools");
                        if (discoveredTools != null) {
                            cacheToolCatalog(key, response, discoveredTools);
                        }
                    } catch (Exception ignored) {
                        // An unavailable server must not prevent the chat from starting.
                    }
                }
                if (discoveredTools != null && discoveredTools.length() > 0) {
                    appendDeclaredTools(result, serverName, discoveredTools);
                    continue;
                }
                result.put(genericServerTool(serverName));
            }
        }
        return result;
    }

    @Nullable
    public static String resolveServerNameForTool(SharedPreferences prefs, String prefixedToolName) {
        if (prefixedToolName == null || !prefixedToolName.startsWith("mcp_")) {
            return null;
        }
        JSONObject servers = VoidPortSettings.readMcpConfigObject(prefs).optJSONObject("mcpServers");
        JSONArray names = servers == null ? null : servers.names();
        for (int i = 0; names != null && i < names.length(); i++) {
            String serverName = names.optString(i, "");
            JSONObject server = servers.optJSONObject(serverName);
            if (server == null || !server.optBoolean("enabled", true)) {
                continue;
            }
            if (!findDirectToolName(serverName, server, prefixedToolName).isEmpty()) {
                return serverName;
            }
            if (addUniquePrefix(serverName, "call_tool").equals(prefixedToolName)) {
                return serverName;
            }
        }
        return null;
    }

    public static String callTool(SharedPreferences prefs, String prefixedToolName, JSONObject args) {
        JSONObject servers = VoidPortSettings.readMcpConfigObject(prefs).optJSONObject("mcpServers");
        JSONArray names = servers == null ? null : servers.names();
        for (int i = 0; names != null && i < names.length(); i++) {
            String serverName = names.optString(i, "");
            JSONObject server = servers.optJSONObject(serverName);
            if (server == null || !server.optBoolean("enabled", true)) {
                continue;
            }

            String directToolName = findDirectToolName(serverName, server, prefixedToolName);
            if (directToolName.isEmpty() && !server.optString("url", "").trim().isEmpty()) {
                try {
                    refreshToolCatalog(serverName, server);
                    directToolName = findDirectToolName(serverName, server, prefixedToolName);
                } catch (Exception ignored) {
                }
            }
            if (!directToolName.isEmpty()) {
                return callServerTool(serverName, server, directToolName, args);
            }

            String genericName = addUniquePrefix(serverName, "call_tool");
            if (genericName.equals(prefixedToolName)) {
                String actualToolName = args == null ? "" : args.optString("tool_name", "").trim();
                JSONObject actualArgs = parseArgumentsObject(args == null ? null : args.opt("arguments"));
                if (actualToolName.isEmpty()) {
                    return "MCP error: tool_name is required for " + genericName + ".";
                }
                if (actualArgs == null) {
                    return "MCP error: arguments must be a valid JSON object for " + genericName + ".";
                }
                return callServerTool(serverName, server, actualToolName, actualArgs);
            }
        }
        return "MCP error: tool '" + prefixedToolName + "' is not configured or is disabled.";
    }

    private static void appendDeclaredTools(JSONArray result, String serverName, JSONArray declaredTools) {
        for (int i = 0; i < declaredTools.length(); i++) {
            JSONObject declared = declaredTools.optJSONObject(i);
            if (declared == null) {
                continue;
            }
            String name = declared.optString("name", "").trim();
            if (name.isEmpty()) {
                continue;
            }
            JSONObject function = new JSONObject();
            try {
                function.put("name", addUniquePrefix(serverName, name));
                function.put("description", declared.optString("description", "MCP tool from " + serverName));
                JSONObject schema = declared.optJSONObject("inputSchema");
                if (schema == null) {
                    schema = declared.optJSONObject("parameters");
                }
                if (schema != null && !isValidMcpHeaderSchema(schema)) {
                    android.util.Log.w("VoidPortMcpChannel",
                            "Ignoring MCP tool with invalid x-mcp-header schema: " + serverName + "/" + name);
                    continue;
                }
                function.put("parameters", schema == null
                        ? new JSONObject().put("type", "object").put("properties", new JSONObject())
                        : schema);
                result.put(new JSONObject().put("type", "function").put("function", function));
            } catch (Exception ignored) {
            }
        }
    }

    private static JSONObject genericServerTool(String serverName) {
        JSONObject tool = new JSONObject();
        try {
            JSONObject properties = new JSONObject();
            properties.put("tool_name", new JSONObject()
                    .put("type", "string")
                    .put("description", "The MCP tool name exposed by server " + serverName + "."));
            properties.put("arguments", new JSONObject()
                    .put("type", "string")
                    .put("description", "JSON object string with arguments for the MCP tool. Use {} when empty."));
            JSONObject parameters = new JSONObject()
                    .put("type", "object")
                    .put("properties", properties)
                    .put("additionalProperties", false)
                    .put("required", new JSONArray().put("tool_name"));
            JSONObject function = new JSONObject()
                    .put("name", addUniquePrefix(serverName, "call_tool"))
                    .put("description", "Calls an MCP tool on Android through the configured HTTP JSON-RPC URL for server " + serverName + ".")
                    .put("parameters", parameters);
            tool.put("type", "function").put("function", function);
        } catch (Exception ignored) {
        }
        return tool;
    }

    private static String findDirectToolName(String serverName, JSONObject server, String prefixedToolName) {
        JSONArray declaredTools = firstArray(server, "tools", "toolDefinitions");
        String found = findToolNameInArray(serverName, prefixedToolName, declaredTools);
        if (!found.isEmpty()) {
            return found;
        }
        ToolCatalogCache cached = DISCOVERED_TOOLS.get(toolCacheKey(serverName, server));
        if (cached != null) {
            found = findToolNameInArray(serverName, prefixedToolName, cached.tools);
        }
        return found;
    }

    private static String findToolNameInArray(String serverName, String prefixedToolName, JSONArray tools) {
        for (int i = 0; tools != null && i < tools.length(); i++) {
            JSONObject declared = tools.optJSONObject(i);
            String name = declared == null ? "" : declared.optString("name", "").trim();
            if (!name.isEmpty() && addUniquePrefix(serverName, name).equals(prefixedToolName)) {
                return name;
            }
        }
        return "";
    }

    private static String callServerTool(String serverName, JSONObject server, String toolName, JSONObject args) {
        String url = server.optString("url", "").trim();
        if (url.isEmpty()) {
            return "MCP server '" + serverName + "' uses command/stdio. Android cannot launch desktop MCP stdio clients reliably; expose it as an HTTP URL in mcpServers." ;
        }
        try {
            JSONObject params = new JSONObject()
                    .put("name", toolName)
                    .put("arguments", args == null ? new JSONObject() : args);
            JSONObject result = request(serverName, server, "tools/call", params);
            return result.toString();
        } catch (Exception e) {
            return "MCP HTTP call failed for " + serverName + "/" + toolName + ": " + e.getMessage();
        }
    }

    /** Sends any server capability request after a fully negotiated lifecycle. */
    public static JSONObject request(SharedPreferences prefs, String serverName, String method, JSONObject params)
            throws Exception {
        JSONObject server = findEnabledServer(prefs, serverName);
        if (server == null) {
            throw new IOException("MCP server is not configured or is disabled: " + serverName);
        }
        return request(serverName, server, method, params);
    }

    public static JSONObject listTools(SharedPreferences prefs, String serverName, @Nullable String cursor)
            throws Exception {
        return request(prefs, serverName, "tools/list", cursorParams(cursor));
    }

    public static JSONObject listResources(SharedPreferences prefs, String serverName, @Nullable String cursor)
            throws Exception {
        return request(prefs, serverName, "resources/list", cursorParams(cursor));
    }

    public static JSONObject listResourceTemplates(SharedPreferences prefs, String serverName, @Nullable String cursor)
            throws Exception {
        return request(prefs, serverName, "resources/templates/list", cursorParams(cursor));
    }

    public static JSONObject readResource(SharedPreferences prefs, String serverName, String uri) throws Exception {
        return request(prefs, serverName, "resources/read", new JSONObject().put("uri", uri));
    }

    public static JSONObject listPrompts(SharedPreferences prefs, String serverName, @Nullable String cursor)
            throws Exception {
        return request(prefs, serverName, "prompts/list", cursorParams(cursor));
    }

    public static JSONObject getPrompt(SharedPreferences prefs, String serverName, String name, JSONObject arguments)
            throws Exception {
        return request(prefs, serverName, "prompts/get", new JSONObject()
                .put("name", name).put("arguments", arguments == null ? new JSONObject() : arguments));
    }

    public static JSONObject complete(SharedPreferences prefs, String serverName, JSONObject ref, JSONObject argument)
            throws Exception {
        return request(prefs, serverName, "completion/complete", new JSONObject()
                .put("ref", ref == null ? new JSONObject() : ref)
                .put("argument", argument == null ? new JSONObject() : argument));
    }

    public static JSONObject subscribeResource(SharedPreferences prefs, String serverName, String uri) throws Exception {
        return request(prefs, serverName, "resources/subscribe", new JSONObject().put("uri", uri));
    }

    public static JSONObject unsubscribeResource(SharedPreferences prefs, String serverName, String uri) throws Exception {
        return request(prefs, serverName, "resources/unsubscribe", new JSONObject().put("uri", uri));
    }

    public static JSONObject setLoggingLevel(SharedPreferences prefs, String serverName, String level) throws Exception {
        return request(prefs, serverName, "logging/setLevel", new JSONObject().put("level", level));
    }

    public static JSONObject ping(SharedPreferences prefs, String serverName) throws Exception {
        return request(prefs, serverName, "ping", new JSONObject());
    }

    private static JSONObject request(String serverName, JSONObject server, String method, JSONObject params)
            throws Exception {
        String url = server.optString("url", "").trim();
        if (url.isEmpty()) {
            throw new IOException("MCP server uses stdio and has no Android-accessible HTTP URL.");
        }

        // 2026-07-28 is stateless: every request carries its own protocol/client
        // metadata and no initialize/session is required.
        try {
            return requestStateless(serverName, server, url, method, params, true);
        } catch (IOException modernFailure) {
            if (!shouldFallbackToLegacy(modernFailure)) {
                throw modernFailure;
            }
            // Backwards compatibility for servers that only implement the 2025
            // Streamable-HTTP lifecycle.
            return requestLegacy(serverName, server, method, params);
        }
    }

    private static JSONObject requestStateless(String serverName, JSONObject server, String url,
                                                String method, JSONObject params,
                                                boolean allowHeaderRetry) throws Exception {
        JSONObject effectiveParams = params == null
                ? new JSONObject()
                : new JSONObject(params.toString());
        JSONObject meta = effectiveParams.optJSONObject("_meta");
        if (meta == null) {
            meta = new JSONObject();
            effectiveParams.put("_meta", meta);
        }
        meta.put("io.modelcontextprotocol/protocolVersion", PROTOCOL_VERSION);
        meta.put("io.modelcontextprotocol/clientInfo", new JSONObject()
                .put("name", "Sketchware IA")
                .put("title", "Sketchware IA")
                .put("version", "android"));
        meta.put("io.modelcontextprotocol/clientCapabilities", new JSONObject());

        JSONObject body = new JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", UUID.randomUUID().toString())
                .put("method", method)
                .put("params", effectiveParams);
        try {
            JsonRpcResponse response = postJsonRpc(serverName, url, body, "", PROTOCOL_VERSION, server);
            if (response.body.has("error")) {
                JSONObject error = response.body.optJSONObject("error");
                throw new IOException(error == null ? response.body.toString() : error.toString());
            }
            JSONObject result = response.body.optJSONObject("result");
            if (result == null) {
                throw new IOException("Invalid MCP response: missing result for " + method);
            }
            return result;
        } catch (IOException error) {
            if (allowHeaderRetry && "tools/call".equals(method) && isHeaderMismatch(error)) {
                refreshToolCatalog(serverName, server);
                return requestStateless(serverName, server, url, method, params, false);
            }
            throw error;
        }
    }

    private static JSONObject requestLegacy(String serverName, JSONObject server, String method, JSONObject params)
            throws Exception {
        McpSession session = getOrCreateSession(serverName, server);
        JsonRpcResponse response = postJsonRpc(serverName, session.url, new JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", UUID.randomUUID().toString())
                .put("method", method)
                .put("params", params == null ? new JSONObject() : params),
                session.sessionId, session.protocolVersion, server);
        if (response.body.has("error")) {
            JSONObject error = response.body.optJSONObject("error");
            throw new IOException(error == null ? response.body.toString() : error.toString());
        }
        JSONObject result = response.body.optJSONObject("result");
        if (result == null) {
            throw new IOException("Invalid MCP response: missing result for " + method);
        }
        if (!response.sessionId.equals(session.sessionId)) {
            SESSIONS.put(sessionCacheKey(serverName, session.url),
                    new McpSession(session.url, response.sessionId, session.protocolVersion, session.serverCapabilities));
        }
        return result;
    }

    private static boolean shouldFallbackToLegacy(IOException error) {
        String message = error == null || error.getMessage() == null
                ? "" : error.getMessage().toLowerCase(Locale.US);
        return message.contains("http 400") || message.contains("http 404")
                || message.contains("http 405") || message.contains("unsupportedprotocol")
                || message.contains("unsupported protocol version")
                || message.contains("method not found") && message.contains("server/discover");
    }

    private static boolean isHeaderMismatch(IOException error) {
        String message = error == null || error.getMessage() == null
                ? "" : error.getMessage().toLowerCase(Locale.US);
        return message.contains("headermismatch") || message.contains("-32001")
                || message.contains("header mismatch");
    }

    private static McpSession getOrCreateSession(String serverName, JSONObject server) throws Exception {
        String url = server.optString("url", "").trim();
        if (url.isEmpty()) {
            throw new IOException("MCP server uses stdio and has no Android-accessible HTTP URL.");
        }
        String key = sessionCacheKey(serverName, url);
        McpSession existing = SESSIONS.get(key);
        if (existing != null) {
            return existing;
        }
        synchronized (SESSIONS) {
            existing = SESSIONS.get(key);
            if (existing != null) return existing;
            McpSession initialized = initializeLegacyHttpSession(serverName, url, server);
            SESSIONS.put(key, initialized);
            return initialized;
        }
    }

    private static McpSession initializeLegacyHttpSession(String serverName, String url, JSONObject server) throws Exception {
        JSONObject capabilities = new JSONObject();
        JsonRpcResponse init = postJsonRpc(serverName, url, new JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", UUID.randomUUID().toString())
                .put("method", "initialize")
                .put("params", new JSONObject()
                        .put("protocolVersion", LEGACY_PROTOCOL_VERSION)
                        .put("capabilities", capabilities)
                        .put("clientInfo", new JSONObject()
                                .put("name", "Sketchware IA")
                                .put("title", "Sketchware IA")
                                .put("version", "android"))), "", "", server);
        if (init.body.has("error")) {
            throw new IOException("MCP initialization failed: " + init.body.optJSONObject("error"));
        }
        JSONObject initResult = init.body.optJSONObject("result");
        if (initResult == null) {
            throw new IOException("MCP initialization returned no result.");
        }
        String negotiatedVersion = initResult.optString("protocolVersion", "");
        if (negotiatedVersion.isEmpty()) negotiatedVersion = LEGACY_PROTOCOL_VERSION;
        String sessionId = init.sessionId;
        postJsonRpc(serverName, url, new JSONObject()
                .put("jsonrpc", "2.0")
                .put("method", "notifications/initialized")
                .put("params", new JSONObject()), sessionId, negotiatedVersion, server);
        return new McpSession(url, sessionId, negotiatedVersion, initResult.optJSONObject("capabilities"));
    }

    /** Cancels an in-flight MCP HTTP call associated with one agent worker. */
    public static void cancelRequestsForThread(@Nullable Thread worker) {
        if (worker == null) return;
        Call call = ACTIVE_HTTP_CALLS.remove(worker);
        if (call != null) call.cancel();
    }

    private static JsonRpcResponse postJsonRpc(String serverName, String url, JSONObject body, String sessionId,
                                                String protocolVersion, JSONObject server) throws IOException {
        Headers.Builder headers = new Headers.Builder()
                .add("Content-Type", "application/json")
                .add("Accept", "application/json, text/event-stream");
        if (sessionId != null && !sessionId.trim().isEmpty()) {
            headers.add("Mcp-Session-Id", sessionId.trim());
        }
        if (protocolVersion != null && !protocolVersion.trim().isEmpty()) {
            headers.add("MCP-Protocol-Version", protocolVersion.trim());
        }

        addStandardMcpHeaders(headers, body);
        addToolParameterHeaders(headers, serverName, server, body);

        JSONObject configuredHeaders = server == null ? null : server.optJSONObject("headers");
        JSONArray headerNames = configuredHeaders == null ? null : configuredHeaders.names();
        for (int i = 0; headerNames != null && i < headerNames.length(); i++) {
            String name = headerNames.optString(i, "").trim();
            String value = configuredHeaders.optString(name, "");
            if (!name.isEmpty() && !value.isEmpty()) headers.set(name, value);
        }

        Request request = new Request.Builder()
                .url(url)
                .headers(headers.build())
                .post(RequestBody.create(body.toString(), JSON_MEDIA_TYPE))
                .build();
        Thread worker = Thread.currentThread();
        Call call = HTTP_CLIENT.newCall(request);
        ACTIVE_HTTP_CALLS.put(worker, call);
        try (Response response = call.execute()) {
            String raw = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " - " + raw);
            }
            String nextSessionId = response.header("Mcp-Session-Id", sessionId == null ? "" : sessionId);
            return new JsonRpcResponse(parseJsonRpcBody(raw), nextSessionId);
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException(e);
        } finally {
            ACTIVE_HTTP_CALLS.remove(worker, call);
        }
    }

    private static void addStandardMcpHeaders(Headers.Builder headers, JSONObject body) {
        String method = body == null ? "" : body.optString("method", "").trim();
        if (!method.isEmpty()) headers.set("Mcp-Method", method);
        JSONObject params = body == null ? null : body.optJSONObject("params");
        String name = "";
        if (params != null) {
            if ("tools/call".equals(method) || "prompts/get".equals(method)) {
                name = params.optString("name", "").trim();
            } else if ("resources/read".equals(method)) {
                name = params.optString("uri", "").trim();
            }
        }
        if (!name.isEmpty()) headers.set("Mcp-Name", name);
    }

    private static void addToolParameterHeaders(Headers.Builder headers, String serverName,
                                                JSONObject server, JSONObject body) {
        if (body == null || !"tools/call".equals(body.optString("method", ""))) return;
        JSONObject params = body.optJSONObject("params");
        if (params == null) return;
        String toolName = params.optString("name", "").trim();
        JSONObject arguments = params.optJSONObject("arguments");
        JSONObject schema = findToolSchema(serverName, server, toolName);
        JSONObject properties = schema == null ? null : schema.optJSONObject("properties");
        JSONArray names = properties == null ? null : properties.names();
        for (int i = 0; names != null && i < names.length(); i++) {
            String parameter = names.optString(i, "");
            JSONObject property = properties.optJSONObject(parameter);
            String headerPart = property == null ? "" : property.optString("x-mcp-header", "").trim();
            if (headerPart.isEmpty() || arguments == null || !arguments.has(parameter) || arguments.isNull(parameter)) continue;
            Object value = arguments.opt(parameter);
            if (!(value instanceof String || value instanceof Number || value instanceof Boolean)) continue;
            headers.set("Mcp-Param-" + headerPart, encodeMcpHeaderValue(String.valueOf(value)));
        }
    }

    private static String encodeMcpHeaderValue(String raw) {
        String value = raw == null ? "" : raw;
        boolean plain = value.equals(value.trim());
        for (int i = 0; plain && i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c > 0x7e || c == 0x7f) plain = false;
        }
        if (plain) return value;
        String encoded = android.util.Base64.encodeToString(
                value.getBytes(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP);
        return "=?base64?" + encoded + "?=";
    }

    private static JSONObject parseJsonRpcBody(String raw) throws Exception {
        String body = raw == null ? "" : raw.trim();
        if (body.startsWith("data:")) {
            String[] lines = body.split("\\r?\\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("data:")) {
                    String data = trimmed.substring(5).trim();
                    if (!data.isEmpty() && !"[DONE]".equals(data)) {
                        return new JSONObject(data);
                    }
                }
            }
        }
        return body.isEmpty() ? new JSONObject() : new JSONObject(body);
    }

    private static JSONObject cursorParams(@Nullable String cursor) {
        JSONObject params = new JSONObject();
        if (cursor != null && !cursor.trim().isEmpty()) {
            try {
                params.put("cursor", cursor.trim());
            } catch (Exception ignored) {
            }
        }
        return params;
    }

    @Nullable
    private static JSONObject findEnabledServer(SharedPreferences prefs, String serverName) {
        if (prefs == null || serverName == null || serverName.trim().isEmpty()) {
            return null;
        }
        JSONObject servers = VoidPortSettings.readMcpConfigObject(prefs).optJSONObject("mcpServers");
        JSONObject server = servers == null ? null : servers.optJSONObject(serverName);
        return server != null && server.optBoolean("enabled", true) ? server : null;
    }

    private static String toolCacheKey(String serverName, JSONObject server) {
        String url = server == null ? "" : server.optString("url", "").trim();
        // Conservative identity isolation: include configured auth/header context
        // even when a server advertises a public catalog.
        String headers = server == null || server.optJSONObject("headers") == null
                ? "" : server.optJSONObject("headers").toString();
        return sessionCacheKey(serverName, url) + "\n" + Integer.toHexString(headers.hashCode());
    }

    private static void cacheToolCatalog(String key, JSONObject response, JSONArray tools) {
        long ttl = Math.max(0L, response == null ? 0L : response.optLong("ttlMs", 0L));
        String scope = response == null ? "private" : response.optString("cacheScope", "private");
        if (!"public".equals(scope) && !"private".equals(scope)) scope = "private";
        // ttlMs=0 means immediately stale. Keep the snapshot only for resolving a
        // tool call already shown to the model; getToolsAsMCP will re-fetch it.
        long expiresAt = System.currentTimeMillis() + ttl;
        JSONArray toolsCopy;
        try {
            toolsCopy = tools == null ? new JSONArray() : new JSONArray(tools.toString());
        } catch (Exception e) {
            toolsCopy = new JSONArray();
        }
        DISCOVERED_TOOLS.put(key, new ToolCatalogCache(toolsCopy, expiresAt, scope));
    }

    private static JSONObject refreshToolCatalog(String serverName, JSONObject server) throws Exception {
        JSONObject response = request(serverName, server, "tools/list", new JSONObject());
        JSONArray tools = response.optJSONArray("tools");
        if (tools == null) tools = new JSONArray();
        cacheToolCatalog(toolCacheKey(serverName, server), response, tools);
        return response;
    }

    @Nullable
    private static JSONObject findToolSchema(String serverName, JSONObject server, String toolName) {
        JSONObject declared = findToolDefinition(firstArray(server, "tools", "toolDefinitions"), toolName);
        if (declared == null) {
            ToolCatalogCache cached = DISCOVERED_TOOLS.get(toolCacheKey(serverName, server));
            declared = cached == null ? null : findToolDefinition(cached.tools, toolName);
        }
        if (declared == null) return null;
        JSONObject schema = declared.optJSONObject("inputSchema");
        return schema == null ? declared.optJSONObject("parameters") : schema;
    }

    @Nullable
    private static JSONObject findToolDefinition(JSONArray tools, String toolName) {
        for (int i = 0; tools != null && i < tools.length(); i++) {
            JSONObject tool = tools.optJSONObject(i);
            if (tool != null && toolName.equals(tool.optString("name", "").trim())) return tool;
        }
        return null;
    }

    private static boolean isValidMcpHeaderSchema(JSONObject schema) {
        JSONObject properties = schema == null ? null : schema.optJSONObject("properties");
        JSONArray names = properties == null ? null : properties.names();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (int i = 0; names != null && i < names.length(); i++) {
            JSONObject property = properties.optJSONObject(names.optString(i, ""));
            if (property == null || !property.has("x-mcp-header")) continue;
            String headerPart = property.optString("x-mcp-header", "");
            if (headerPart.isEmpty() || !headerPart.matches("[\\x21-\\x39\\x3B-\\x7E]+")) return false;
            String lower = headerPart.toLowerCase(Locale.US);
            if (!seen.add(lower)) return false;
            String type = property.optString("type", "");
            if (!("string".equals(type) || "number".equals(type)
                    || "integer".equals(type) || "boolean".equals(type))) return false;
        }
        return true;
    }

    private static String sessionCacheKey(String serverName, String url) {
        return (serverName == null ? "" : serverName) + "\n" + (url == null ? "" : url);
    }

    private static JSONArray firstArray(JSONObject object, String... keys) {
        for (String key : keys) {
            JSONArray array = object == null ? null : object.optJSONArray(key);
            if (array != null) {
                return array;
            }
        }
        return null;
    }

    @Nullable
    private static JSONObject parseArgumentsObject(Object raw) {
        if (raw instanceof JSONObject) return (JSONObject) raw;
        if (raw == null || raw == JSONObject.NULL) return new JSONObject();
        try {
            Object parsed = new org.json.JSONTokener(String.valueOf(raw)).nextValue();
            return parsed instanceof JSONObject ? (JSONObject) parsed : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String addUniquePrefix(String serverName, String toolName) {
        String safeServer = slug(serverName);
        String safeTool = slug(toolName);
        if (safeServer.isEmpty()) {
            return safeTool;
        }
        return "mcp_" + safeServer + "_" + safeTool;
    }

    public static String removeMcpToolNamePrefix(String prefixedToolName) {
        if (prefixedToolName == null) {
            return "";
        }
        String value = prefixedToolName;
        if (value.startsWith("mcp_")) {
            value = value.substring(4);
        }
        int first = value.indexOf('_');
        return first >= 0 && first < value.length() - 1 ? value.substring(first + 1) : value;
    }

    private static String slug(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }
}
