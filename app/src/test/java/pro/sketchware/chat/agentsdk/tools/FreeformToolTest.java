package pro.sketchware.chat.agentsdk.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import pro.sketchware.chat.agentsdk.AgentToolResult;
import pro.sketchware.chat.agentsdk.EventStream;
import pro.sketchware.chat.agentsdk.RunContext;
import pro.sketchware.chat.agentsdk.tools.AxionToolRouter.Routed;

import org.junit.Test;

/**
 * Freeform-tool parity: a FREEFORM tool receives the model's RAW input
 * verbatim — never JSON-parsed — even when the input looks like JSON. The
 * router must not build function arguments for freeform specs.
 */
public class FreeformToolTest {

    private String capturedInput;

    private ToolRegistration freeformReg(String name, String grammar) {
        return ToolRegistration.builder(ToolSpec.freeform(
                        ToolName.plain(name), "freeform " + name,
                        "grammar", "lark", grammar, false))
                .executor(ctx -> {
                    capturedInput = ctx.freeformInput();
                    return AgentToolResult.success("processed " + name);
                })
                .source("test")
                .build();
    }

    @Test
    public void rawInputReachesExecutorVerbatim() {
        AxionToolRegistry registry = new AxionToolRegistry();
        registry.register(freeformReg("apply_patch", WorkspaceToolProvider.APPLY_PATCH_GRAMMAR));
        EventStream events = new EventStream(r -> r.run(), 8);

        String rawPatch = "*** Begin Patch\n"
                + "*** Add File: hello.txt\n"
                + "+Hello\n"
                + "*** End Patch\n";

        AxionToolRouter router = new AxionToolRouter(registry, null, events);
        Routed routed = router.route(new AxionToolRouter.Route("call_1", "apply_patch", rawPatch),
                "sc_free", RunContext.bare("sc_free", "a", null), null);

        assertFalse(routed.result().isError());
        assertEquals(rawPatch, capturedInput);
        assertEquals(1, router.executedCount());
    }

    @Test
    public void jsonLookalikeInputIsNotParsedForFreeform() {
        AxionToolRegistry registry = new AxionToolRegistry();
        registry.register(freeformReg("code_tool", "anything"));
        EventStream events = new EventStream(r -> r.run(), 8);

        String argsThatLookLikeJson = "{\"patch\":\"*** Begin Patch\"}";
        AxionToolRouter router = new AxionToolRouter(registry, null, events);
        Routed routed = router.route(new AxionToolRouter.Route("call_2", "code_tool",
                        argsThatLookLikeJson),
                "sc_free", RunContext.bare("sc_free", "a", null), null);

        assertFalse(routed.result().isError());
        assertEquals(argsThatLookLikeJson, capturedInput);
    }

    @Test
    public void emptyArgumentsDefaultsToJsonEmptyObjectForRoute() {
        // Router normalises empty args to "{}" so diagnosis stays stable, but
        // a freeform executor STILL sees the raw argument string it was given.
        AxionToolRegistry registry = new AxionToolRegistry();
        registry.register(freeformReg("free_tool", "g"));
        EventStream events = new EventStream(r -> r.run(), 8);
        AxionToolRouter router = new AxionToolRouter(registry, null, events);

        Routed routed = router.route(new AxionToolRouter.Route("call_3", "free_tool", ""),
                "sc_free", RunContext.bare("sc_free", "a", null), null);
        assertFalse(routed.result().isError());
        assertEquals("{}", capturedInput);
    }

    @Test
    public void routerBuildsNoFunctionArgumentsForFreeformSpec() throws Exception {
        AxionToolRegistry registry = new AxionToolRegistry();
        ToolRegistration reg = freeformReg("raw_tool", "g");
        registry.register(reg);
        EventStream events = new EventStream(r -> r.run(), 8);
        AxionToolRouter router = new AxionToolRouter(registry, null, events);

        Routed routed = router.route(new AxionToolRouter.Route("call_4", "raw_tool", "raw text"),
                "sc_free", RunContext.bare("sc_free", "a", null), null);
        assertFalse(routed.result().isError());
        assertEquals("raw text", capturedInput);
    }
}