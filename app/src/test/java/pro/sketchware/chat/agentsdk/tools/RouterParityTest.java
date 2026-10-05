package pro.sketchware.chat.agentsdk.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import pro.sketchware.chat.agentsdk.AgentToolResult;
import pro.sketchware.chat.agentsdk.EventStream;
import pro.sketchware.chat.agentsdk.PermissionLayer;
import pro.sketchware.chat.agentsdk.RunContext;
import pro.sketchware.chat.agentsdk.ToolPolicy;
import pro.sketchware.chat.agentsdk.tools.AxionToolRouter.Route;
import pro.sketchware.chat.agentsdk.tools.AxionToolRouter.Routed;

import org.json.JSONObject;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Router execution parity (Codex tools/router.rs): lookup → exposure check →
 * permission layer → sandbox pre-check → executor; dedupe is by callId (never
 * by name); unknown / hidden / deferred / blocked calls produce typed errors,
 * and run lifecycle resets the dedupe table.
 */
public class RouterParityTest {

    private static ToolRegistration d(String name) {
        return ToolRegistration.builder(ToolSpec.function(
                        ToolName.plain(name), "desc " + name, new JSONObject()))
                .executor(ctx -> AgentToolResult.success(name + ":ok"))
                .source("test")
                .build();
    }

    @Test
    public void executesFunctionToolWithParsedArgs() {
        AxionToolRegistry registry = new AxionToolRegistry();
        final String[] captured = new String[1];
        ToolRegistration reg = ToolRegistration.builder(ToolSpec.function(
                        ToolName.plain("echo"), "echo", new JSONObject()))
                .executor(ctx -> {
                    captured[0] = ctx.functionArguments().optString("text");
                    return AgentToolResult.success("done");
                }).build();
        registry.register(reg);
        AxionToolRouter router = new AxionToolRouter(registry, null,
                new EventStream(r -> r.run(), 8));
        Routed routed = router.route(new Route("c1", "echo", "{\"text\":\"hi\"}"),
                "sc", RunContext.bare("sc", "a", null), null);
        assertFalse(routed.result().isError());
        assertEquals("hi", captured[0]);
        assertEquals(1, router.executedCount());
    }

    @Test
    public void invalidJsonArgumentsIsError() {
        AxionToolRegistry registry = new AxionToolRegistry();
        registry.register(d("echo"));
        AxionToolRouter router = new AxionToolRouter(registry, null,
                new EventStream(r -> r.run(), 8));
        Routed routed = router.route(new Route("c1", "echo", "not-json"),
                "sc", RunContext.bare("sc", "a", null), null);
        assertTrue(routed.result().isError());
        assertTrue(routed.result().output().contains("invalid JSON"));
    }

    @Test
    public void unknownToolYieldsTypedError() {
        AxionToolRegistry registry = new AxionToolRegistry();
        AxionToolRouter router = new AxionToolRouter(registry, null,
                new EventStream(r -> r.run(), 8));
        Routed routed = router.route(new Route("c1", "nope", "{}"),
                "sc", RunContext.bare("sc", "a", null), null);
        assertTrue(routed.result().isError());
        assertTrue(routed.result().output().contains("unknown tool 'nope'"));
    }

    @Test
    public void hiddenToolIsBlockedFromModel() {
        AxionToolRegistry registry = new AxionToolRegistry();
        registry.register(ToolRegistration.builder(ToolSpec.function(
                        ToolName.plain("internal"), "h", new JSONObject()))
                .executor(ctx -> AgentToolResult.success("leak"))
                .exposure(ToolExposure.hidden()).build());
        AxionToolRouter router = new AxionToolRouter(registry, null,
                new EventStream(r -> r.run(), 8));
        Routed routed = router.route(new Route("c1", "internal", "{}"),
                "sc", RunContext.bare("sc", "a", null), null);
        assertTrue(routed.result().isError());
        assertTrue(routed.result().output().contains("not exposed"));
    }

    @Test
    public void deferredToolIsBlockedUntilActivated() {
        AxionToolRegistry registry = new AxionToolRegistry();
        ToolRegistration deferred = ToolRegistration.builder(ToolSpec.function(
                        ToolName.plain("find_me"), "hidden", new JSONObject()))
                .executor(ctx -> AgentToolResult.success("found"))
                .exposure(ToolExposure.deferred()).build();
        registry.register(deferred);
        AxionToolRouter router = new AxionToolRouter(registry, null,
                new EventStream(r -> r.run(), 8));
        Routed blocked = router.route(new Route("c1", "find_me", "{}"),
                "sc", RunContext.bare("sc", "a", null), null);
        assertTrue(blocked.result().isError());
        assertTrue(blocked.result().output().contains("deferred"));

        registry.activateDeferred(deferred.spec().name());
        Routed allowed = router.route(new Route("c2", "find_me", "{}"),
                "sc", RunContext.bare("sc", "a", null), null);
        assertFalse(allowed.result().isError());
        assertEquals("found", allowed.result().output());
    }

    @Test
    public void permissionDenyBlocksExecution() {
        AxionToolRegistry registry = new AxionToolRegistry();
        ToolRegistration mutation = ToolRegistration.builder(ToolSpec.function(
                        ToolName.plain("edit"), "edit", new JSONObject()))
                .executor(ctx -> AgentToolResult.success("edited"))
                .fileMutation(true)
                .build();
        registry.register(mutation);
        PermissionLayer layers = new PermissionLayer(
                ToolPolicy.readOnly(), null, new EventStream(r -> r.run(), 8));
        AxionToolRouter router = new AxionToolRouter(registry, layers,
                new EventStream(r -> r.run(), 8));

        Routed routed = router.route(new Route("c1", "edit", "{}"),
                "sc", RunContext.bare("sc", "a", null), null);
        assertTrue(routed.result().isError());
        assertTrue(routed.result().output().contains("denied"));
    }

    @Test
    public void sandboxPreCheckBlocksExecution() {
        AxionToolRegistry registry = new AxionToolRegistry();
        ToolRegistration reg = ToolRegistration.builder(ToolSpec.function(
                        ToolName.plain("sandboxed"), "s", new JSONObject()))
                .executor(ctx -> AgentToolResult.success("ran"))
                .sandbox((callId, scId) -> AgentToolResult.error("Error: sandbox denied"))
                .build();
        registry.register(reg);
        AxionToolRouter router = new AxionToolRouter(registry, null,
                new EventStream(r -> r.run(), 8));
        Routed routed = router.route(new Route("c1", "sandboxed", "{}"),
                "sc", RunContext.bare("sc", "a", null), null);
        assertTrue(routed.result().isError());
        assertTrue(routed.result().output().contains("sandbox denied"));
        assertEquals(1, router.executedCount());
    }

    @Test
    public void dedupeIsByCallIdNotByName() {
        AxionToolRegistry registry = new AxionToolRegistry();
        AtomicInteger executions = new AtomicInteger();
        registry.register(ToolRegistration.builder(ToolSpec.function(
                        ToolName.plain("count"), "c", new JSONObject()))
                .executor(ctx -> AgentToolResult.success("n=" + executions.incrementAndGet()))
                .build());
        AxionToolRouter router = new AxionToolRouter(registry, null,
                new EventStream(r -> r.run(), 8));
        RunContext context = RunContext.bare("sc", "a", null);

        Routed first = router.route(new Route("same_id", "count", "{}"), "sc", context, null);
        Routed second = router.route(new Route("same_id", "count", "{}"), "sc", context, null);
        assertTrue(second.wasDeduplicated());
        assertEquals(1, router.executedCount());
        assertEquals(1, executions.get());
        assertEquals(1, router.distinctCallIdsSeen());

        // A DIFFERENT call id with the same tool name executes fresh.
        Routed third = router.route(new Route("other_id", "count", "{}"), "sc", context, null);
        assertFalse(third.wasDeduplicated());
        assertEquals(2, router.executedCount());
        assertEquals(2, executions.get());
        assertEquals(2, router.distinctCallIdsSeen());
    }

    @Test
    public void resetForNewRunClearsDedupeTable() {
        AxionToolRegistry registry = new AxionToolRegistry();
        registry.register(d("echo"));
        AxionToolRouter router = new AxionToolRouter(registry, null,
                new EventStream(r -> r.run(), 8));
        RunContext context = RunContext.bare("sc", "a", null);
        router.route(new Route("id1", "echo", "{}"), "sc", context, null);
        assertEquals(1, router.executedCount());

        router.resetForNewRun();
        Routed after = router.route(new Route("id1", "echo", "{}"), "sc", context, null);
        assertFalse(after.wasDeduplicated());
        assertEquals(1, router.executedCount());
    }

    @Test
    public void loopHooksObserveStartAndCompletion() {
        AxionToolRegistry registry = new AxionToolRegistry();
        registry.register(d("echo"));
        AxionToolRouter router = new AxionToolRouter(registry, null,
                new EventStream(r -> r.run(), 8));
        final java.util.List<String> seen = new java.util.ArrayList<>();
        AxionToolRouter.LoopHooks hooks = new AxionToolRouter.LoopHooks() {
            @Override
            public void onToolCallStarted(Route call, ToolRegistration registration) {
                seen.add("started:" + call.toolName());
            }

            @Override
            public void onToolCallCompleted(Route call, ToolRegistration registration,
                                            AgentToolResult result) {
                seen.add("completed:" + call.toolName());
            }
        };
        router.route(new Route("id1", "echo", "{}"), "sc",
                RunContext.bare("sc", "a", null), hooks);
        assertEquals(java.util.List.of("started:echo", "completed:echo"), seen);
    }
}