package pro.sketchware.chat.agentsdk.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import pro.sketchware.chat.agentsdk.AgentToolResult;
import pro.sketchware.chat.agentsdk.tools.AxionToolRegistry.DuplicateToolException;

import org.json.JSONObject;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Registry parity: single source of truth, Codex collision rules (qualified
 * name identity, duplicate rejection, namespace-prefix reservation) and the
 * exposure/activation queries the catalog and router rely on.
 */
public class ToolRegistryParityTest {

    private static ToolRegistration simpleFunction(String name, ToolExposure exposure) {
        try {
            return ToolRegistration.builder(ToolSpec.function(
                            ToolName.plain(name), "Description of " + name,
                            new JSONObject().put("type", "object")))
                    .executor(ctx -> AgentToolResult.success("ran " + name))
                    .exposure(exposure)
                    .source("test")
                    .build();
        } catch (org.json.JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    public void registerAndLookupByQualifiedAndPlainName() {
        AxionToolRegistry registry = new AxionToolRegistry();
        registry.register(simpleFunction("read_file", ToolExposure.direct()));

        assertTrue(registry.contains("read_file"));
        assertNotNull(registry.get("read_file"));
        assertEquals("read_file", registry.get("read_file").spec().name().qualifiedName());
        assertEquals(1, registry.size());
    }

    @Test
    public void duplicateRegistrationThrowsAndKeepsFirst() {
        AxionToolRegistry registry = new AxionToolRegistry();
        registry.register(simpleFunction("read_file", ToolExposure.direct()));

        assertThrows(DuplicateToolException.class,
                () -> registry.register(simpleFunction("read_file", ToolExposure.direct())));
        assertEquals("read_file", registry.get("read_file").spec().name().name());
    }

    @Test
    public void namespacedSameNameIsDistinctTool() {
        AxionToolRegistry registry = new AxionToolRegistry();
        registry.register(ToolRegistration.builder(ToolSpec.function(
                        ToolName.namespaced("server_a", "search"), "a", new JSONObject()))
                .executor(ctx -> AgentToolResult.success("a")).build());
        registry.register(ToolRegistration.builder(ToolSpec.function(
                        ToolName.namespaced("server_b", "search"), "b", new JSONObject()))
                .executor(ctx -> AgentToolResult.success("b")).build());

        assertTrue(registry.contains(ToolName.namespaced("server_a", "search")));
        assertTrue(registry.contains(ToolName.namespaced("server_b", "search")));
        assertEquals(2, registry.size());
    }

    @Test
    public void plainToolCannotClaimActiveNamespacePrefix() {
        AxionToolRegistry registry = new AxionToolRegistry();
        registry.register(ToolRegistration.builder(ToolSpec.namespace(
                        ToolName.plain("clock"), "clock namespace", java.util.Collections.emptyList()))
                .build());
        registry.register(ToolRegistration.builder(ToolSpec.function(
                        ToolName.namespaced("clock", "curr_time"), "now", new JSONObject()))
                .executor(ctx -> AgentToolResult.success("time")).build());

        // A plain tool named "clock" would collide with the active namespace.
        assertThrows(DuplicateToolException.class,
                () -> registry.register(simpleFunction("clock", ToolExposure.direct())));
    }

    @Test
    public void registerOrReplaceReturnsPrevious() {
        AxionToolRegistry registry = new AxionToolRegistry();
        ToolRegistration first = simpleFunction("edit_file", ToolExposure.direct());
        registry.register(first);
        ToolRegistration second = simpleFunction("edit_file", ToolExposure.hidden());
        ToolRegistration replaced = registry.registerOrReplace(second);

        assertEquals(first, replaced);
        assertTrue(registry.get("edit_file").exposure().isHidden());
    }

    @Test
    public void modelVisibleExcludesDeferredAndHidden() {
        AxionToolRegistry registry = new AxionToolRegistry();
        registry.register(simpleFunction("read_file", ToolExposure.direct()));
        registry.register(simpleFunction("secret_tool", ToolExposure.deferred()));
        registry.register(simpleFunction("internal", ToolExposure.hidden()));

        java.util.List<ToolRegistration> visible = registry.modelVisibleTools();
        assertEquals(1, visible.size());
        assertEquals("read_file", visible.get(0).spec().name().name());
        assertEquals(1, registry.deferredTools().size());
        assertEquals(1, registry.hiddenTools().size());
    }

    @Test
    public void codeModeFlagIncludesCodeModeOnlyTools() {
        AxionToolRegistry registry = new AxionToolRegistry();
        registry.register(ToolRegistration.builder(ToolSpec.function(
                        ToolName.plain("shell"), "x", new JSONObject()))
                .exposure(ToolExposure.directCodeModeOnly())
                .executor(ctx -> AgentToolResult.success("x")).build());

        assertTrue(registry.modelVisibleTools(false).isEmpty());
        assertEquals(1, registry.modelVisibleTools(true).size());
        assertEquals(0, registry.directTools(false).size());
        assertEquals(1, registry.directTools(true).size());
        assertFalse(registry.get("shell").exposure().isModelVisible());
        assertTrue(registry.get("shell").exposure().isCodeModeVisible());
    }

    @Test
    public void diagnosticsListenerObservesEveryRegistration() {
        AxionToolRegistry registry = new AxionToolRegistry();
        AtomicInteger seen = new AtomicInteger(0);
        registry.addDiagnosticsListener(reg -> seen.incrementAndGet());
        registry.register(simpleFunction("a", ToolExposure.direct()));
        registry.register(simpleFunction("b", ToolExposure.deferred()));
        assertEquals(2, seen.get());
    }

    @Test
    public void removeAndQueriesAreStable() {
        AxionToolRegistry registry = new AxionToolRegistry();
        registry.register(simpleFunction("read_file", ToolExposure.direct()));
        registry.remove("read_file");
        assertFalse(registry.contains("read_file"));
        assertNull(registry.get("read_file"));
        assertEquals(0, registry.size());
    }

    @Test
    public void deferredActivationSurfacesInRouterQuery() {
        AxionToolRegistry registry = new AxionToolRegistry();
        ToolRegistration deferred = simpleFunction("multiplayer", ToolExposure.deferred());
        registry.register(deferred);
        assertFalse(registry.isDeferredActivated(deferred.spec().name()));
        registry.activateDeferred(deferred.spec().name());
        assertTrue(registry.isDeferredActivated(deferred.spec().name()));
    }
}