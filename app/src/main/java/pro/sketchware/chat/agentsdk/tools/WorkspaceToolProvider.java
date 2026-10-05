package pro.sketchware.chat.agentsdk.tools;

import pro.sketchware.chat.agentsdk.AgentToolResult;
import pro.sketchware.chat.agentsdk.ApprovalHandler;
import pro.sketchware.chat.agentsdk.RunContext;
import pro.sketchware.chat.agentsdk.ToolCapability;
import pro.sketchware.chat.agentsdk.schema.ToolJsonSchema;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

/**
 * Central assembly of the model-facing core toolset, registered into an
 * {@link AxionToolRegistry} with the Codex-parity contracts. This is the
 * migration target: what used to be assembled from {@code AgentTool}s by
 * {@code ToolManager} is now expressed as {@link ToolRegistration}s whose
 * specs are serialised by {@link ToolSpecSerializer}.
 *
 * <p>Registered families:</p>
 * <ul>
 *   <li><b>Codex core</b> — {@code apply_patch} (FREEFORM), {@code update_plan}, {@code request_user_input},
 *       {@code get_context_remaining}, {@code new_context}, the {@code clock}
 *       namespace, and synthetic {@code tool_search} when deferred tools exist;</li>
 *   <li><b>Internal executors</b> — registered as HIDDEN (never model-visible)
 *       when the host wants the legacy write machinery available internally.</li>
 * </ul>
 */
public final class WorkspaceToolProvider {

    /** The lark grammar definition of the apply_patch FREEFORM wire format. */
    public static final String APPLY_PATCH_GRAMMAR =
            "start: begin_patch hunk+ end_patch\n"
            + "begin_patch: \"*** Begin Patch\" LF\n"
            + "end_patch: \"*** End Patch\" LF?\n"
            + "hunk: add_hunk | delete_hunk | update_hunk\n"
            + "add_hunk: \"*** Add File: \" filename LF add_line+\n"
            + "delete_hunk: \"*** Delete File: \" filename LF\n"
            + "update_hunk: \"*** Update File: \" filename LF change_move? change?\n"
            + "filename: /(.+)/\n"
            + "add_line: \"+\" /(.*)/ LF -> line\n"
            + "change_move: \"*** Move to: \" filename LF\n"
            + "change: (change_context | change_line)+ eof_line?\n"
            + "change_context: (\"@@\" | \"@@ \" /(.+)/) LF\n"
            + "change_line: (\"+\" | \"-\" | \" \") /(.*)/ LF\n"
            + "eof_line: \"*** End of File\" LF\n"
            + "%import common.LF";

    private WorkspaceToolProvider() {
    }

    /**
     * Registers the Codex-parity core toolset into the registry.
     *
     * @param patchToolFactory binds an {@code ApplyPatchTool} to a run context
     * @param approvalHandler  routes {@code request_user_input} questions to the user
     */
    public static void registerCoreTools(AxionToolRegistry registry,
                                         ApplyPatchExecutor.PatchToolFactory patchToolFactory,
                                         ApprovalHandler approvalHandler) {
        registerApplyPatch(registry, patchToolFactory);
        registerPlanTools(registry, approvalHandler);
        registerContextTools(registry);
        registerClockAndWindowTools(registry);
        registerToolSearch(registry);
    }

    /** Convenience: apply_patch wired to resolve the run's filesystem fail-closed. */
    public static void registerCoreTools(AxionToolRegistry registry, ApprovalHandler approvalHandler) {
        registerCoreTools(registry, new ApplyPatchExecutor.PatchToolFactory() {
            @Override
            public pro.sketchware.chat.agentsdk.ApplyPatchTool create(
                    RunContext context, pro.sketchware.chat.agentsdk.EventStream events, String scId) {
                return new pro.sketchware.chat.agentsdk.ApplyPatchTool(
                        scId == null || scId.isEmpty() ? context.scId() : scId,
                        events,
                        context.filesystem());
            }
        }, approvalHandler);
    }

    // ------------------------------------------------------------------
    // workspace read/search tools backed by VoidPortToolsService
    // ------------------------------------------------------------------

    /**
     * Legacy void tools that still have no newer contract (the six remaining
     * {@code LEGACY_MODEL_COMPATIBLE} names): {@code read_file}, {@code ls_dir},
     * {@code get_dir_tree}, {@code search_pathnames_only}, {@code search_for_files}
     * and {@code search_in_file}. They are now first-class {@link ToolRegistration}s
     * whose schemas come from {@code VoidPortToolsService.getAllToolsAsMCP} and
     * whose executors delegate to {@code VoidPortToolsService.executeTool} — the
     * {@code ToolManager}/{@code WorkspaceAgents} bridge is gone.
     */
    public static void registerWorkspaceReadTools(AxionToolRegistry registry) {
        registerWorkspaceToolFamily(registry, WORKSPACE_READ_TOOL_NAMES,
                name -> new WorkspaceReadExecutor(name),
                name -> ToolCapability.READ);
    }

    /**
     * Registers the workspace MUTATION tools ({@code create_file_or_folder},
     * {@code delete_file_or_folder}, {@code edit_file}, {@code rewrite_file},
     * {@code move_file}, {@code rename_file}, {@code copy_file}) as
     * first-class {@link ToolRegistration}s, reachable directly by the model.
     * Execution is delegated through {@link WorkspaceMutationExecutor}, which
     * calls {@code VoidPortToolsService.executeTool(...)}.
     */
    public static void registerWorkspaceMutationTools(AxionToolRegistry registry) {
        registerWorkspaceToolFamily(registry, WORKSPACE_MUTATION_TOOL_NAMES,
                WorkspaceMutationExecutor::new,
                name -> DESTRUCTIVE_MUTATION_TOOL_NAMES.contains(name)
                        ? new ToolCapability[]{ToolCapability.WORKSPACE_WRITE, ToolCapability.DESTRUCTIVE}
                        : new ToolCapability[]{ToolCapability.WORKSPACE_WRITE});
    }

    /** Shared MCP-schema-to-ToolRegistration plumbing for a named family of tools. */
    private static void registerWorkspaceToolFamily(AxionToolRegistry registry,
                                                     java.util.Set<String> names,
                                                     java.util.function.Function<String, ToolExecutor> executorFactory,
                                                     java.util.function.Function<String, Object> capabilityFactory) {
        JSONArray mcpSchemas =
                pro.sketchware.chat.port.VoidPortToolsService.getAllToolsAsMCP();
        for (int i = 0; i < mcpSchemas.length(); i++) {
            JSONObject entry = mcpSchemas.optJSONObject(i);
            JSONObject fn = entry == null ? null : entry.optJSONObject("function");
            if (fn == null) {
                continue;
            }
            String name = fn.optString("name", "").trim();
            if (!names.contains(name)) {
                continue;
            }
            JSONObject parameters = fn.optJSONObject("parameters");
            if (parameters == null) {
                parameters = new JSONObject();
            }
            String description = fn.optString("description", "");
            ToolRegistration.Builder builder = ToolRegistration.builder(ToolSpec.function(
                            ToolName.plain(name), description, parameters))
                    .executor(executorFactory.apply(name))
                    .source("workspace");
            Object capability = capabilityFactory.apply(name);
            if (capability instanceof ToolCapability[]) {
                ToolCapability[] caps = (ToolCapability[]) capability;
                builder.capabilities(caps);
                for (ToolCapability c : caps) {
                    if (c == ToolCapability.DESTRUCTIVE) {
                        builder.destructive(true);
                    }
                }
            } else {
                builder.capability((ToolCapability) capability);
            }
            if (WORKSPACE_MUTATION_TOOL_NAMES.contains(name)) {
                builder.fileMutation(true);
            }
            registry.register(builder.build());
        }
    }

    private static final java.util.Set<String> WORKSPACE_READ_TOOL_NAMES =
            new java.util.HashSet<>(Arrays.asList(
                    "read_file", "ls_dir", "get_dir_tree",
                    "search_pathnames_only", "search_for_files", "search_in_file",
                    "get_file_info"));

    private static final java.util.Set<String> WORKSPACE_MUTATION_TOOL_NAMES =
            new java.util.HashSet<>(Arrays.asList(
                    "create_file_or_folder", "delete_file_or_folder", "edit_file",
                    "rewrite_file", "move_file", "rename_file", "copy_file"));

    private static final java.util.Set<String> DESTRUCTIVE_MUTATION_TOOL_NAMES =
            new java.util.HashSet<>(Arrays.asList("delete_file_or_folder"));

    // WorkspaceMutationExecutor now lives in its own file (see
    // WorkspaceMutationExecutor.java): ToolRegistration -> WorkspaceMutationExecutor
    // -> VoidPortToolsService.executeTool(...).

    /** Executes one workspace read tool through the VoidToolsService bridge. */
    private static final class WorkspaceReadExecutor implements ToolExecutor {
        private final String toolName;

        WorkspaceReadExecutor(String toolName) {
            this.toolName = toolName;
        }

        @Override
        public AgentToolResult execute(ToolExecutionContext context) {
            String result = pro.sketchware.chat.port.VoidPortToolsService.executeTool(
                    context.scId(), toolName, context.functionArguments());
            return AgentToolResult.success(result);
        }
    }

    // ------------------------------------------------------------------
    // apply_patch (FREEFORM)
    // ------------------------------------------------------------------

    private static void registerApplyPatch(AxionToolRegistry registry,
                                           ApplyPatchExecutor.PatchToolFactory factory) {
        String description =
                "The `apply_patch` tool can be used to edit files. This is a FREEFORM tool, so do not wrap the patch in JSON.";
        ApplyPatchExecutor executor = new ApplyPatchExecutor(factory);
        ToolRegistration reg = ToolRegistration.freeform("apply_patch", description, APPLY_PATCH_GRAMMAR, executor)
                .source("core")
                .fileMutation(true)
                .destructive(true)
                .capabilities(ToolCapability.WORKSPACE_WRITE, ToolCapability.DESTRUCTIVE)
                .build();
        registry.register(reg);
    }

    // ------------------------------------------------------------------
    // update_plan / request_user_input (FUNCTION)
    // ------------------------------------------------------------------

    private static void registerPlanTools(AxionToolRegistry registry, ApprovalHandler handler) {
        Map<String, ToolJsonSchema> planItemProps = ToolJsonSchema.properties()
                .put("step", ToolJsonSchema.string("Task step text."))
                .put("status", ToolJsonSchema.stringEnum(
                        Arrays.asList("pending", "in_progress", "completed"), "Step status."))
                .build();
        Map<String, ToolJsonSchema> updatePlanProps = ToolJsonSchema.properties()
                .put("explanation", ToolJsonSchema.string("Optional explanation for this plan update."))
                .put("plan", ToolJsonSchema.array(
                        ToolJsonSchema.object(planItemProps, Arrays.asList("step", "status"), false),
                        "The list of steps"))
                .build();
        ToolRegistration updatePlan = ToolRegistration.builder(ToolSpec.function(
                        ToolName.plain("update_plan"),
                        "Updates the task plan.\nProvide an optional explanation and a list of plan items, "
                                + "each with a step and status.\nAt most one step can be in_progress at a time.\n",
                        ToolJsonSchema.object(updatePlanProps, Arrays.asList("plan"), false).toJson()))
                .executor(new UpdatePlanExecutor())
                .source("core")
                .capability(ToolCapability.READ)
                .build();
        registry.register(updatePlan);

        Map<String, ToolJsonSchema> optionProps = ToolJsonSchema.properties()
                .put("label", ToolJsonSchema.string("User-facing label (1-5 words)."))
                .put("description", ToolJsonSchema.string("One short sentence explaining impact/tradeoff if selected."))
                .build();
        Map<String, ToolJsonSchema> questionProps = ToolJsonSchema.properties()
                .put("id", ToolJsonSchema.string("Stable identifier for mapping answers (snake_case)."))
                .put("header", ToolJsonSchema.string("Short header label shown in the UI (12 or fewer chars)."))
                .put("question", ToolJsonSchema.string("Single-sentence prompt shown to the user."))
                .put("options", ToolJsonSchema.array(
                        ToolJsonSchema.object(optionProps, Arrays.asList("label", "description"), false),
                        "Provide 2-3 mutually exclusive choices. Put the recommended option first and suffix "
                                + "its label with \"(Recommended)\". Do not include an \"Other\" option in this "
                                + "list; the client will add a free-form \"Other\" option automatically."))
                .build();
        Map<String, ToolJsonSchema> requestProps = ToolJsonSchema.properties()
                .put("questions", ToolJsonSchema.array(
                        ToolJsonSchema.object(questionProps,
                                Arrays.asList("id", "header", "question", "options"), false),
                        "Questions to show the user. Prefer 1 and do not exceed 3"))
                .build();
        ToolRegistration requestInput = ToolRegistration.builder(ToolSpec.function(
                        ToolName.plain("request_user_input"),
                        "Asks the user a structured question when the task is ambiguous or a decision belongs "
                                + "to them. Provide a short question, up to 4 concrete options, and whether "
                                + "free-form input is allowed. Use it sparingly: prefer proceeding with a "
                                + "sensible default when the choice is reversible and low-impact.",
                        ToolJsonSchema.object(requestProps, Arrays.asList("questions"), false).toJson()))
                .executor(new RequestUserInputExecutor(handler))
                .source("core")
                .capability(ToolCapability.READ)
                .build();
        registry.register(requestInput);
    }

    // ------------------------------------------------------------------
    // get_context_remaining (FUNCTION, Codex output contract)
    // ------------------------------------------------------------------

    private static void registerContextTools(AxionToolRegistry registry) {
        JSONObject emptyParams = ToolJsonSchema.object(
                ToolJsonSchema.properties().build(), Arrays.asList(), false).toJson();
        try {
            JSONObject outputSchema = new JSONObject()
                    .put("type", "object")
                    .put("properties", new JSONObject()
                            .put("tokens_left", new JSONObject()
                                    .put("type", "integer")
                                    .put("description", "Remaining tokens in the current context window, or null if unknown."))
                            .put("budget_enforced", new JSONObject()
                                    .put("type", "boolean")
                                    .put("description", "Whether a hard token budget is enforced for this run.")))
                    .put("required", new JSONArray().put("tokens_left"))
                    .put("additionalProperties", false);
            ToolRegistration ctx = ToolRegistration.builder(ToolSpec.function(
                            ToolName.plain("get_context_remaining"),
                            "Reports how much of the current run's context/token budget has been used and how much "
                                    + "remains. Call this when a long task makes you unsure whether you can finish "
                                    + "within the limit: if remaining is low, wrap up, summarize the state, and tell "
                                    + "the user what is left instead of continuing to call tools.",
                            emptyParams, outputSchema, false))
                    .executor(ctxExec -> {
                        RunContext rc = ctxExec.runContext();
                        pro.sketchware.chat.agentsdk.ContextTracker tracker =
                                rc == null ? null : rc.contextTracker();
                        if (tracker == null) {
                            return AgentToolResult.success("{\"tokens_left\":null}");
                        }
                        JSONObject report = tracker.report();
                        boolean enforced = report.optBoolean("budget_enforced", false);
                        try {
                            JSONObject out = new JSONObject()
                                    .put("tokens_left", enforced ? (long) report.optLong("remaining_tokens", 0L) : JSONObject.NULL)
                                    .put("budget_enforced", enforced);
                            if (enforced) {
                                out.put("maximum_tokens", report.optLong("maximum_tokens", 0L));
                                out.put("used_tokens", report.optLong("used_tokens", 0L));
                            }
                            return AgentToolResult.success(out.toString());
                        } catch (org.json.JSONException e) {
                            return AgentToolResult.error("Error: could not build the context report.");
                        }
                    })
                    .source("core")
                    .capability(ToolCapability.READ)
                    .build();
            registry.register(ctx);
        } catch (org.json.JSONException e) {
            throw new IllegalStateException("Could not build get_context_remaining output schema", e);
        }
    }

    // ------------------------------------------------------------------
    // clock namespace + new_context
    // ------------------------------------------------------------------

    private static void registerClockAndWindowTools(AxionToolRegistry registry) {
        Map<String, ToolJsonSchema> noProps = ToolJsonSchema.properties().build();
        // clock namespace declaration — namespaced tools are emitted in the
        // catalog only when a NAMESPACE container groups them (Codex parity).
        registry.register(ToolRegistration.builder(ToolSpec.namespace(
                        ToolName.plain(ClockTools.NAMESPACE),
                        "Tools for reading and waiting on time.",
                        Collections.emptyList()))
                .source("core")
                .build());
        // clock.curr_time
        ToolRegistration currTime = ToolRegistration.builder(ToolSpec.function(
                        ToolName.namespaced(ClockTools.NAMESPACE, ClockTools.CURRENT_TIME_TOOL),
                        "Return the current time in UTC.",
                        ToolJsonSchema.object(noProps, Arrays.asList(), false).toJson()))
                .executor(ClockTools.currentTimeExecutor())
                .source("core")
                .capability(ToolCapability.READ)
                .build();
        registry.register(currTime);

        // clock.sleep
        Map<String, ToolJsonSchema> sleepProps = ToolJsonSchema.properties()
                .put("duration_ms", ToolJsonSchema.number(
                        "How long to sleep in milliseconds. Must be between 1 and " + ClockTools.MAX_SLEEP_DURATION_MS + "."))
                .build();
        ToolRegistration sleep = ToolRegistration.builder(ToolSpec.function(
                        ToolName.namespaced(ClockTools.NAMESPACE, ClockTools.SLEEP_TOOL),
                        "Pause execution for a specified duration. The sleep ends early when new input arrives "
                                + "for the active turn. Returns the elapsed wall-clock time.",
                        ToolJsonSchema.object(sleepProps, Arrays.asList("duration_ms"), false).toJson()))
                .executor(ClockTools.sleepExecutor())
                .source("core")
                .capability(ToolCapability.READ)
                .build();
        registry.register(sleep);

        // new_context
        ToolRegistration newContext = ToolRegistration.builder(ToolSpec.function(
                        ToolName.plain("new_context"),
                        "Start a new context window. Does not clear, reset, or otherwise affect environment state.",
                        ToolJsonSchema.object(noProps, Arrays.asList(), false).toJson()))
                .executor(ctx -> AgentToolResult.success("{\"started\":true}"))
                .source("core")
                .capability(ToolCapability.READ)
                .build();
        registry.register(newContext);
    }

    // ------------------------------------------------------------------
    // tool_search (TOOL_SEARCH) — its catalog entry only matters when deferred tools exist
    // ------------------------------------------------------------------

    private static void registerToolSearch(AxionToolRegistry registry) {
        Map<String, ToolJsonSchema> props = ToolJsonSchema.properties()
                .put("query", ToolJsonSchema.string(
                        "A natural-language search query for the tool you want, e.g. \"create a ticket\"."))
                .put("limit", ToolJsonSchema.number(
                        "The maximum number of tools to return. Defaults to 10, capped at 25."))
                .build();
        JSONObject params = ToolJsonSchema.object(props, Arrays.asList("query"), false).toJson();
        ToolRegistration toolSearch = ToolRegistration.builder(ToolSpec.toolSearch(
                        "Searches for a tool by name or description and activates it for direct use. "
                                + "The result is a short list with the qualifying tool names and their descriptions; "
                                + "you may then call any of them directly.",
                        params))
                .executor(new ToolSearchTool(registry))
                .source("core")
                .capability(ToolCapability.READ)
                .build();
        registry.register(toolSearch);
    }
}