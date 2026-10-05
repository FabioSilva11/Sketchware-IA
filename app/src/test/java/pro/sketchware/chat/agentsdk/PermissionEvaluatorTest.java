package pro.sketchware.chat.agentsdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import pro.sketchware.chat.agentsdk.tools.ToolRegistration;

import org.junit.Test;

import java.util.Set;

/**
 * Decision-table unit tests for {@link PermissionEvaluator} (item 12): the
 * profile × approval-policy matrix plus capability resolution (declared caps
 * are authoritative, inference is only a defensive fallback).
 */
public class PermissionEvaluatorTest {

    private static final Set<ToolCapability> ONLY_WRITE =
            java.util.Collections.singleton(ToolCapability.WORKSPACE_WRITE);

    private static ToolRegistration tool(String name, ToolCapability... capabilities) {
        ToolRegistration.Builder builder = ToolRegistration.function(
                name, "test tool " + name, new org.json.JSONObject());
        for (ToolCapability capability : capabilities) {
            builder.capability(capability);
        }
        return builder.build();
    }

    private static PermissionEvaluator.Decision decide(
            PermissionConfig config, ToolRegistration tool) {
        return PermissionEvaluator.evaluate(config, tool);
    }

    @Test
    public void readOnly_allowsSafeReads_deniesEverythingElse() {
        PermissionConfig config = PermissionConfig.readOnly();
        assertEquals(PermissionEvaluator.Decision.ALLOW,
                decide(config, tool("read_file", ToolCapability.READ)));
        assertEquals(PermissionEvaluator.Decision.ALLOW,
                decide(config, tool("tool_search", ToolCapability.READ)));
        assertEquals(PermissionEvaluator.Decision.DENY,
                decide(config, tool("apply_patch",
                        ToolCapability.WORKSPACE_WRITE, ToolCapability.DESTRUCTIVE)));
        assertEquals(PermissionEvaluator.Decision.DENY,
                decide(config, tool("delete_file", ToolCapability.DESTRUCTIVE)));
        // An unclassified tool is treated as safe: reads run, nothing risky.
        assertEquals(PermissionEvaluator.Decision.ALLOW,
                decide(config, tool("unknown_helper")));
    }

    @Test
    public void workspaceOnRequest_promptsOnRiskyTools_allowsSafeReads() {
        PermissionConfig config = PermissionConfig.workspaceRequest();
        assertEquals(PermissionEvaluator.Decision.ALLOW,
                decide(config, tool("get_context_remaining", ToolCapability.READ)));
        assertEquals(PermissionEvaluator.Decision.PROMPT,
                decide(config, tool("apply_patch",
                        ToolCapability.WORKSPACE_WRITE, ToolCapability.DESTRUCTIVE)));
        assertEquals(PermissionEvaluator.Decision.PROMPT,
                decide(config, tool("mcp_ping", ToolCapability.NETWORK)));
        assertEquals(PermissionEvaluator.Decision.PROMPT,
                decide(config, tool("export_data",
                        ToolCapability.EXTERNAL_ACCESS, ToolCapability.WORKSPACE_WRITE)));
    }

    @Test
    public void workspaceNever_runsEverythingInside_isAbsoluteOnlyForExternalAccess() {
        PermissionConfig config = PermissionConfig.of(
                PermissionProfile.WORKSPACE, ApprovalPolicy.NEVER);
        assertEquals(PermissionEvaluator.Decision.ALLOW,
                decide(config, tool("apply_patch",
                        ToolCapability.WORKSPACE_WRITE, ToolCapability.DESTRUCTIVE)));
        assertEquals(PermissionEvaluator.Decision.ALLOW,
                decide(config, tool("get_context_remaining", ToolCapability.READ)));
        // NEVER must never promote a forbidden capability: EXTERNAL_ACCESS
        // stays DENY even without a user in the loop.
        assertEquals(PermissionEvaluator.Decision.DENY,
                decide(config, tool("export_data",
                        ToolCapability.EXTERNAL_ACCESS, ToolCapability.WORKSPACE_WRITE)));
    }

    @Test
    public void dangerFullAccess_allowsEverything() {
        PermissionConfig config = PermissionConfig.fullAccess();
        assertEquals(PermissionEvaluator.Decision.ALLOW,
                decide(config, tool("apply_patch",
                        ToolCapability.WORKSPACE_WRITE, ToolCapability.DESTRUCTIVE)));
        assertEquals(PermissionEvaluator.Decision.ALLOW,
                decide(config, tool("export_data",
                        ToolCapability.EXTERNAL_ACCESS, ToolCapability.WORKSPACE_WRITE)));
        assertEquals(PermissionEvaluator.Decision.ALLOW,
                decide(config, tool("read_file", ToolCapability.READ)));
    }

    @Test
    public void handoffIsAlwaysAllowedRegardlessOfProfile() {
        ToolRegistration handoff = ToolRegistration.function(
                        "handoff_to_planner", "Pass control.", new org.json.JSONObject())
                .handoffTarget("planner")
                .build();
        assertEquals(PermissionEvaluator.Decision.ALLOW,
                decide(PermissionConfig.readOnly(), handoff));
        assertEquals(PermissionEvaluator.Decision.ALLOW,
                decide(PermissionConfig.workspaceRequest(), handoff));
        assertEquals(PermissionEvaluator.Decision.ALLOW,
                decide(PermissionConfig.of(PermissionProfile.WORKSPACE, ApprovalPolicy.NEVER), handoff));
    }

    @Test
    public void nullArgsFailClosed() {
        // An unknown registration is never trusted (fail-closed), and a missing config means the safe
        // WORKSPACE + ON_REQUEST profile, not "no restrictions".
        assertEquals(PermissionEvaluator.Decision.DENY,
                PermissionEvaluator.evaluate(null, null));
        assertEquals(PermissionEvaluator.Decision.DENY,
                PermissionEvaluator.evaluate(PermissionConfig.workspaceRequest(), null));
        ToolRegistration write = tool("anything", ToolCapability.WORKSPACE_WRITE);
        assertEquals(PermissionEvaluator.evaluate(PermissionConfig.workspaceRequest(), write),
                PermissionEvaluator.evaluate(null, write));
    }

    @Test
    public void declaredCapabilitiesBeatNameInference() {
        // A tool literally named exec_command that declares READ is a read
        // tool; the declared metadata is the single source of truth.
        ToolRegistration read = tool("exec_command", ToolCapability.READ);
        assertEquals(PermissionEvaluator.Decision.ALLOW,
                decide(PermissionConfig.workspaceRequest(), read));
        assertEquals(PermissionEvaluator.Decision.ALLOW,
                decide(PermissionConfig.readOnly(), read));

        // Inference only kicks in when nothing was declared (MCP tools by name).
    }

    @Test
    public void inferenceCoversKnownReadNamesAndMutationFlags() {
        assertEquals(PermissionEvaluator.Decision.ALLOW,
                decide(PermissionConfig.workspaceRequest(), tool("get_context_remaining")));

        ToolRegistration flaggedPatch = ToolRegistration.freeform(
                        "apply_patch", "patch tool", "grammar", null)
                .fileMutation(true)
                .build();
        assertEquals(PermissionEvaluator.Decision.PROMPT,
                decide(PermissionConfig.workspaceRequest(), flaggedPatch));

        ToolRegistration destructivePatch = ToolRegistration.freeform(
                        "apply_patch", "patch tool", "grammar", null)
                .fileMutation(true)
                .destructive(true)
                .build();
        assertEquals(PermissionEvaluator.Decision.PROMPT,
                decide(PermissionConfig.workspaceRequest(), destructivePatch));
    }

    @Test
    public void mcpLikelyIsNetwork_neverExternalSoDangerOnlyFromDeclaration() {
        assertEquals(PermissionEvaluator.Decision.PROMPT,
                decide(PermissionConfig.workspaceRequest(),
                        tool("files_delete", ToolCapability.NETWORK,
                                ToolCapability.DESTRUCTIVE, ToolCapability.WORKSPACE_WRITE)));
        // Workspace + NEVER lets an MCP deletion run (network is not the
        // external-workspace escape hatch); only declared EXTERNAL_ACCESS is.
        assertEquals(PermissionEvaluator.Decision.ALLOW,
                decide(PermissionConfig.of(PermissionProfile.WORKSPACE, ApprovalPolicy.NEVER),
                        tool("files_delete", ToolCapability.NETWORK,
                                ToolCapability.DESTRUCTIVE, ToolCapability.WORKSPACE_WRITE)));
    }

    @Test
    public void mcpCapabilitiesClassifiesByToken() {
        assertTrue(PermissionEvaluator.mcpCapabilities("files_delete").contains(ToolCapability.NETWORK));
        assertTrue(PermissionEvaluator.mcpCapabilities("files_delete").contains(ToolCapability.DESTRUCTIVE));
        assertTrue(PermissionEvaluator.mcpCapabilities("files_delete").contains(ToolCapability.WORKSPACE_WRITE));
        assertTrue(PermissionEvaluator.mcpCapabilities("read_file").contains(ToolCapability.READ));
        assertTrue(PermissionEvaluator.mcpCapabilities("read_file").contains(ToolCapability.NETWORK));
        assertTrue(PermissionEvaluator.mcpCapabilities("ping").contains(ToolCapability.NETWORK));
        assertFalse(PermissionEvaluator.mcpCapabilities("ping").contains(ToolCapability.READ));
        assertFalse(PermissionEvaluator.mcpCapabilities("ping").contains(ToolCapability.DESTRUCTIVE));
    }

    @Test
    public void capabilitiesOfDeclaredAndInferredBehaveForPolicy() {
        ToolRegistration declared = tool("write_to", ToolCapability.WORKSPACE_WRITE);
        assertEquals(ONLY_WRITE, PermissionEvaluator.capabilitiesOf(declared));
        // No shell in Sketchware: a tool named like one gets no capability by name
        assertTrue(PermissionEvaluator.capabilitiesOf(tool("exec_command")).isEmpty());
    }
}