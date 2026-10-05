package pro.sketchware.chat.agentsdk;

import android.content.Context;

/**
 * Assembly of the production chat runtime (item 1 of the migration):
 *
 * <pre>
 * ChatActivity
 *     ↓ AgentManager (UI controller only)
 *     ↓ AgentRuntime  ← AgentRuntimeFactory.createForChat(context)
 *     ↓ AxionAgentGateway
 *     ↓ AiProviderService
 * </pre>
 *
 * <p>The factory is the ONLY place the UI's runtime is built, guaranteeing:</p>
 * <ul>
 *   <li>a real streaming gateway ({@link AxionAgentGateway}) declaring
 *       {@code NativeToolCallsOnly};</li>
 *   <li>a MANDATORY {@link PermissionLayer} — the {@link ApprovalHandler}
 *       interactive resolver parks requests until the UI resolves them by
 *       {@code requestId}; mutations NEVER execute merely because the
 *       host forgot a policy (safe default = WORKSPACE + ON_REQUEST);</li>
 *   <li>the runtime's own {@link EventStream} — the {@code HostBridge}
 *       subscribes to THIS stream, not to a second one.</li>
 * </ul>
 */
public final class AgentRuntimeFactory {

    private AgentRuntimeFactory() {
    }

    /** Production runtime for the chat UI with host-driven approvals. */
    public static AgentRuntime createForChat(Context context) {
        pro.sketchware.chat.AiProviderService aiService =
                pro.sketchware.chat.AiProviderService.getInstance();
        AxionAgentGateway gateway = new AxionAgentGateway(aiService, "agent");
        android.content.SharedPreferences prefs =
                context == null ? null : pro.sketchware.chat.port.VoidPortSettings.prefs(context);
        return createForChatWithGateway(gateway, prefs,
                pro.sketchware.chat.skills.SkillManager.appFlow(context));
    }

    /**
     * Production assembly over an explicit {@link AgentLlmGateway}. The memory
     * owner is always constructed here — never by the caller — so the UI
     * runtime, its registry, its permission layer, its approval input channel
     * and its event stream are built exactly once and exactly the same way for
     * every gateway. Tests drive the REAL factory with a scripted gateway and
     * assert on the assembly's own pieces (no wiring duplication in tests).
     *
     * <p>{@code context} is deliberately not passed further: the whole
     * assembly after the gateway is pure memory and runs on the JVM, which is
     * what makes {@code ChatActivity → AgentManager → AgentRuntime} provably
     * buildable and runnable under unit tests.</p>
     */
    public static AgentRuntime createForChatWithGateway(
            pro.sketchware.chat.agentsdk.AgentLlmGateway gateway) {
        return createForChatWithGateway(gateway, null);
    }

    /**
     * Production assembly over an explicit {@link AgentLlmGateway} and the
     * optional MCP preferences. When {@code prefs == null} (JVM tests) no MCP
     * server is reachable and none is registered.
     */
    public static AgentRuntime createForChatWithGateway(
            pro.sketchware.chat.agentsdk.AgentLlmGateway gateway,
            android.content.SharedPreferences mcpPrefs) {
        return createForChatWithGateway(gateway, mcpPrefs, null);
    }

    /**
     * Variante que também injeta o fluxo de Skills (produção usa
     * {@code SkillManager.appFlow(context)}; JVM/mainstay passa {@code null}
     * para deixar o runtime sem skills).
     */
    public static AgentRuntime createForChatWithGateway(
            pro.sketchware.chat.agentsdk.AgentLlmGateway gateway,
            android.content.SharedPreferences mcpPrefs,
            pro.sketchware.chat.skills.SkillFlow skills) {
        // Item 16: the interactive resolver is the explicit approval
        // protocol — requests are resolved by requestId from the UI thread.
        ApprovalHandler.Resolver approvals = new ApprovalHandler.Resolver();
        EventStream events = new EventStream();
        // Item (registry-backed catalog): the SINGLE model-facing tool source.
        // The chat tools and the remaining workspace read tools
        // (read_file, ls_dir, search* ...) are registered once here; MCP
        // servers follow as registry citizens. Nothing is adapted at run time,
        // so legacy names with a replacement never reach the model. Only tools
        // the chat really serves are offered (see registerChatTools).
        pro.sketchware.chat.agentsdk.tools.AxionToolRegistry registry =
                new pro.sketchware.chat.agentsdk.tools.AxionToolRegistry();
        pro.sketchware.chat.agentsdk.tools.WorkspaceToolProvider.registerChatTools(registry);
        pro.sketchware.chat.agentsdk.tools.WorkspaceToolProvider.registerWorkspaceReadTools(
                registry);
        // Mutation tools (create/delete/edit/rewrite/move/rename/copy) are
        // first-class ToolRegistrations too (see ToolSelectionPolicy).
        pro.sketchware.chat.agentsdk.tools.WorkspaceToolProvider.registerWorkspaceMutationTools(
                registry);
        pro.sketchware.chat.agentsdk.tools.McpToolSource.discover(mcpPrefs, registry);
        // Item (permission model): the safe default WORKSPACE + ON_REQUEST.
        // Reads run automatically; workspace writes / network ask the
        // user before executing. The host UI switches the profile via
        // AgentRuntime#updatePermissionConfig.
        return new AgentRuntime.Builder(gateway)
                .events(events)
                .permissions(new PermissionLayer(approvals, events))
                .toolRegistry(registry)
                .skills(skills)
                // Removed: expectFileMutations(true) - Codex alignment
                // The runtime no longer forces mutations for all chats.
                // Text-only responses are valid for read-only queries.
                .build();
    }
}
