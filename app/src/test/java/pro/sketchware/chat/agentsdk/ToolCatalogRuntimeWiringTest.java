package pro.sketchware.chat.agentsdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import pro.sketchware.chat.ChatMessage;
import pro.sketchware.chat.agentsdk.tools.AxionToolRegistry;
import pro.sketchware.chat.agentsdk.tools.ToolCatalog;
import pro.sketchware.chat.agentsdk.tools.ToolRegistration;
import pro.sketchware.chat.agentsdk.tools.WorkspaceToolProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Registry-backed catalog wiring (migration, "ToolCatalog canônico no
 * gateway"): the runtime mounts the catalog EXCLUSIVELY from the registry —
 * legacy AgentTool[] sets are never adapted at run time. Legacy tools enter
 * the model catalog only through an explicit, classification-guarded
 * {@link LegacyToolAdapter} registration, and execution routes through the
 * registry.
 */
public class ToolCatalogRuntimeWiringTest {

    /** A fake gateway capturing the ToolCatalog overload call. */
    private static final class CatalogCaptureGateway implements AgentLlmGateway {
        final List<String> catalogNames = new ArrayList<>();
        final List<ChatMessage> history = new ArrayList<>();
        LlmTurnOutput next = new LlmTurnOutput("done", "", "stop", null);
        LlmTurnProvider nextProvider;
        JavaSerializationResult lastSerialization = null;

        interface LlmTurnProvider {
            LlmTurnOutput provide(String systemPrompt, ToolCatalog catalog,
                                  List<ChatMessage> messages,
                                  pro.sketchware.chat.AiOperationContext operationContext);
        }

        static final class JavaSerializationResult {
            final JSONArray schema;
            JavaSerializationResult(JSONArray schema) {
                this.schema = schema;
            }
        }

        @Override
        public LlmTurnOutput completeTurn(String systemPrompt,
                                          ToolCatalog catalog,
                                          List<ChatMessage> messages,
                                          pro.sketchware.chat.AiOperationContext operationContext) {
            catalogNames.clear();
            for (ToolRegistration reg : catalog.registrations()) {
                catalogNames.add(reg.qualifiedName());
            }
            history.clear();
            history.addAll(messages);
            return nextProvider != null
                    ? nextProvider.provide(systemPrompt, catalog, messages, operationContext)
                    : next;
        }

        @Override
        public LlmTurnOutput completeTurn(String systemPrompt,
                                          JSONArray tools,
                                          List<ChatMessage> messages,
                                          pro.sketchware.chat.AiOperationContext operationContext) {
            lastSerialization = new JavaSerializationResult(tools);
            return next;
        }
    }

    @Test
    public void unregisteredNamesNeverEnterTheCatalog() throws Exception {
        AxionToolRegistry registry = new AxionToolRegistry();
        WorkspaceToolProvider.registerCoreTools(registry, null);
        CatalogCaptureGateway gateway = new CatalogCaptureGateway();
        AgentRuntime runtime = new AgentRuntime.Builder(gateway)
                .toolRegistry(registry)
                .maxTurns(2)
                .build();

        // A tool that was never registered must not leak into the catalog:
        // the model-facing catalog is EXCLUSIVELY registry-driven.
        Agent agent = Agent.Builder.forName("calculator", "")
                .instructions("You calculate.")
                .build();

        RunResult result = runtime.run(agent, "What is 6*7?", "sc-before");
        assertTrue("registry run must complete: " + result.getOutput()
                + " | reason: " + result.getFailureReason(), result.isSuccessful());
        assertTrue("catalog must include core apply_patch", gateway.catalogNames.contains("apply_patch"));
        assertFalse("catalog must not include exec_command in Sketchware", gateway.catalogNames.contains("exec_command"));
        assertFalse("an unregistered tool must never be adapted into the model catalog",
                gateway.catalogNames.contains("legacy_calc"));
    }

    @Test
    public void explicitlyRegisteredToolsAreModelVisibleAndRoutable() throws Exception {
        AxionToolRegistry registry = new AxionToolRegistry();
        WorkspaceToolProvider.registerCoreTools(registry, null);
        // Explicit registration: the tool becomes a registry citizen and only
        // then reaches the model catalog and the router.
        registry.register(ToolRegistration.function(
                        "legacy_calc", "Legacy calculator.",
                        new JSONObject()
                                .put("type", "object")
                                .put("properties", new JSONObject()
                                        .put("expr", new JSONObject().put("type", "string")))
                                .put("required", new JSONArray().put("expr")))
                .executor(context -> AgentToolResult.success("42"))
                .source("test")
                .build());

        CatalogCaptureGateway gateway = new CatalogCaptureGateway();
        AgentRuntime runtime = new AgentRuntime.Builder(gateway)
                .toolRegistry(registry)
                .maxTurns(2)
                .build();
        Agent agent = Agent.Builder.forName("calculator", "")
                .instructions("You calculate.")
                .build();

        // First turn: the agent calls legacy_calc via a tool call. Second
        // turn: it reports the answer. The capture gateway serves its turn
        // script exactly once each.
        final AtomicInteger served = new AtomicInteger(0);
        gateway.nextProvider = (systemPrompt, catalog, messages, operationContext) -> {
            if (served.getAndIncrement() == 0) {
                return new LlmTurnOutput("", "", "tool_calls",
                        java.util.Arrays.asList(new pro.sketchware.chat.toolcalling.ToolCall(
                                "legacy_calc", "{\"expr\":\"6*7\"}", "call_1")));
            }
            return new LlmTurnOutput("result: 42", "", "stop", null);
        };

        RunResult result = runtime.run(agent, "What is 6*7?", "sc-after");
        assertTrue("registry run must complete: " + result.getOutput()
                + " | reason: " + result.getFailureReason(), result.isSuccessful());
        assertTrue("catalog must include core apply_patch", gateway.catalogNames.contains("apply_patch"));
        assertTrue("explicitly registered tool is model-visible",
                gateway.catalogNames.contains("legacy_calc"));
        // The registry path executed the call through the router: the model
        // history contains this turn's tool result.
        boolean sawToolResult = false;
        for (ChatMessage message : gateway.history) {
            if (message.getType() == ChatMessage.TYPE_TOOL) {
                sawToolResult = true;
            }
        }
        assertTrue(sawToolResult);
    }

    @Test
    public void registryChatModeRoutesThroughToolCatalogOverload() throws Exception {
        AxionToolRegistry registry = new AxionToolRegistry();
        WorkspaceToolProvider.registerCoreTools(registry, null);
        CatalogCaptureGateway gateway = new CatalogCaptureGateway();
        AgentRuntime runtime = new AgentRuntime.Builder(gateway)
                .toolRegistry(registry)
                .maxTurns(1)
                .includeProjectInstructions(false)
                .build();
        Agent agent = Agent.Builder.forName("plain", "")
                .instructions("Answer.")
                .build();

        RunResult result = runtime.run(agent, "hi", "sc-2");
        assertTrue("registry plain chat must complete: " + result.getOutput()
                + " | reason: " + result.getFailureReason(), result.isSuccessful());
        // The runtime routes through the ToolCatalog overload: the gateway saw
        // the canonical registry catalog (core tools, no app-internal tools).
        assertFalse("catalog must not include exec_command in Sketchware",
                gateway.catalogNames.contains("exec_command"));
        assertTrue("catalog must include core apply_patch",
                gateway.catalogNames.contains("apply_patch"));
    }

    @Test
    public void toolCatalogOverloadDefaultSerializesSameEnvelopeShape() throws Exception {
        AxionToolRegistry registry = new AxionToolRegistry();
        WorkspaceToolProvider.registerCoreTools(registry, null);

        // Direct default method through a minimal Gateway that only overrides
        // the JSONArray variant.
        final AtomicReference<JSONArray> captured = new AtomicReference<>(null);
        AgentLlmGateway minimal = new AgentLlmGateway() {
            @Override
            public LlmTurnOutput completeTurn(String systemPrompt,
                                              JSONArray tools,
                                              List<ChatMessage> messages,
                                              pro.sketchware.chat.AiOperationContext operationContext) {
                captured.set(tools);
                return new LlmTurnOutput("ok", "", "stop", null);
            }
        };
        LlmTurnOutput out = minimal.completeTurn("sys", ToolCatalog.from(registry),
                new ArrayList<>(), null);
        assertNotNull(out);
        JSONArray envelope = captured.get();
        assertNotNull(envelope);
        boolean sawGetContext = false;
        for (int i = 0; i < envelope.length(); i++) {
            JSONObject entry = envelope.getJSONObject(i);
            if ("function".equals(entry.getString("type"))
                    && "get_context_remaining".equals(entry.optJSONObject("function")
                    .optString("name"))) {
                sawGetContext = true;
            }
        }
        assertTrue(sawGetContext);
    }
}
