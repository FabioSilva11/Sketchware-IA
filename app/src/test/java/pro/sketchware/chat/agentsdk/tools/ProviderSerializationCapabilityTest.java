package pro.sketchware.chat.agentsdk.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Capability-aware serialization (item 2 of the stage): the gateway never
 * flattens the catalog up front — the faithful kinds reach
 * {@link ToolSpecSerializer#toProviderPayload} and the wire shape is chosen
 * per {@link ProviderToolCapabilities}. These tests prove:
 *
 * <ul>
 *   <li>FREEFORM stays FREEFORM when the transport carries it natively;</li>
 *   <li>a freeform/namespace/tool_search downgrade happens ONLY via a
 *       DECLARED fallback and is recorded ({@link ProviderCatalogPayload});</li>
 *   <li>no declared fallback = kind omitted (never silently converted to a
 *       function);</li>
 *   <li>on function-only transports the output is byte-equivalent (same
 *       semantics, same order) to the legacy {@code toFunctionEnvelope}.</li>
 * </ul>
 */
public class ProviderSerializationCapabilityTest {

    private static List<ToolRegistration> coreTools() {
        AxionToolRegistry registry = new AxionToolRegistry();
        WorkspaceToolProvider.registerCoreTools(registry, null);
        return registry.modelVisibleTools();
    }

    private static String types(JSONArray array) {
        return ToolSpecSerializer.catalogTypes(array);
    }

    @Test
    public void functionOnlyProducesExactlyTheLegacyEnvelope() throws Exception {
        List<ToolRegistration> tools = coreTools();
        JSONArray legacy = ToolSpecSerializer.toFunctionEnvelope(tools);
        ProviderCatalogPayload payload = ToolSpecSerializer.toProviderPayload(
                tools, ProviderToolCapabilities.FUNCTION_ONLY);
        // Same wire semantics, same registration order — the capability path
        // must not silently reorder or reshape what function-only transports
        // always received.
        assertEquals(ToolSpecSerializer.catalogTypes(legacy),
                ToolSpecSerializer.catalogTypes(payload.payload()));
        int functionEntries = 0;
        for (int i = 0; i < payload.payload().length(); i++) {
            JSONObject entry = payload.payload().getJSONObject(i);
            assertEquals("function", entry.getString("type"));
            functionEntries++;
        }
        assertEquals(legacy.length(), functionEntries);
        // tool_search has no function-only shape and must NOT be claimed:
        assertFalse(payload.toolSearchFellBack());
        // freeform apply_patch and the clock namespace WERE flattened — recorded.
        assertTrue(payload.freeformFellBack());
        assertTrue(payload.namespaceFellBack());
    }

    @Test
    public void nativeAllKeepsEveryKindAndNeverFallsBack() {
        List<ToolRegistration> tools = coreTools();
        JSONArray nativeWire = ToolSpecSerializer.toCatalog(tools);
        ProviderCatalogPayload payload = ToolSpecSerializer.toProviderPayload(
                tools, ProviderToolCapabilities.NATIVE_ALL);
        assertEquals(ToolSpecSerializer.catalogTypes(nativeWire),
                ToolSpecSerializer.catalogTypes(payload.payload()));
        assertFalse("apply_patch stays freeform natively",
                payload.freeformFellBack());
        assertFalse("clock stays a namespace natively",
                payload.namespaceFellBack());
        assertFalse(payload.toolSearchFellBack());
        assertFalse(payload.hadExplicitFallback());
    }

    @Test
    public void freeformNotDowngradedWhenFallbackIsNotDeclared() {
        List<ToolRegistration> tools = coreTools();
        ProviderToolCapabilities noFreeformNoFallback = ProviderToolCapabilities.builder()
                .supportsFreeformTools(false)
                .freeformFallbackToFunction(false)
                .supportsNamespaces(false)
                .namespaceFallbackToFunctions(true)
                .build();
        ProviderCatalogPayload payload = ToolSpecSerializer.toProviderPayload(
                tools, noFreeformNoFallback);
        // No freeform wire, no hidden "apply_patch as function" claim, and the
        // profile MUST say freeform was not downgraded (nothing was forged).
        assertFalse(payload.freeformFellBack());
        String wire = payload.payload().toString();
        assertFalse("freeform never appears", wire.contains("\"freeform\""));
        assertFalse("apply_patch never sneaks in as a function",
                wire.contains("apply_patch"));
    }

    @Test
    public void toolSearchFallbackIsExplicitNotSilent() {
        List<ToolRegistration> tools = coreTools();
        ProviderToolCapabilities withToolSearchFallback = ProviderToolCapabilities.builder()
                .supportsToolSearch(false)
                .toolSearchFallbackToFunction(true)
                .freeformFallbackToFunction(true)
                .namespaceFallbackToFunctions(true)
                .build();
        ProviderCatalogPayload payload = ToolSpecSerializer.toProviderPayload(
                tools, withToolSearchFallback);
        assertTrue("tool_search downgrade recorded", payload.toolSearchFellBack());
        String wire = payload.payload().toString();
        assertTrue("tool_search carried as a function envelope",
                wire.contains("tool_search"));
        assertFalse(wire.contains("\"tool_search\":"));
    }

    @Test
    public void nativeNamespacesAreNotFlattenedIntoFunctions() throws Exception {
        List<ToolRegistration> tools = coreTools();
        ProviderCatalogPayload nativePath = ToolSpecSerializer.toProviderPayload(
                tools, ProviderToolCapabilities.NATIVE_ALL);
        String wire = nativePath.payload().toString();
        assertTrue(wire.contains("\"type\":\"namespace\""));
        assertFalse("clock children never become ad-hoc root functions",
                wire.contains("clock.curr_time"));
        // The clock group still exists when the transport supports namespaces.
        for (int i = 0; i < nativePath.payload().length(); i++) {
            JSONObject entry = nativePath.payload().getJSONObject(i);
            if ("namespace".equals(entry.getString("type"))
                    && "clock".equals(entry.getString("name"))) {
                assertEquals(1, entry.getJSONArray("tools").length());
                return;
            }
        }
        org.junit.Assert.fail("native clock namespace entry missing");
    }

    @Test
    public void flatteningKeepsEveryNamespacedChildVisible() {
        List<ToolRegistration> tools = coreTools();
        ProviderCatalogPayload flattened = ToolSpecSerializer.toProviderPayload(
                tools, ProviderToolCapabilities.FUNCTION_ONLY);
        String wire = flattened.payload().toString();
        assertTrue("clock.curr_time present after flattening",
                wire.contains("clock.curr_time"));
        assertFalse("clock.sleep is not offered", wire.contains("clock.sleep"));
    }

    @Test
    public void namespacedChildrenWithoutDeclarationAreNeverLost() throws Exception {
        List<ToolRegistration> orphans = Arrays.asList(
                ToolRegistration.builder(ToolSpec.function(
                        ToolName.namespaced("raw", "first"), "d",
                        new JSONObject().put("type", "object"))).build(),
                ToolRegistration.builder(ToolSpec.function(
                        ToolName.namespaced("raw", "second"), "d",
                        new JSONObject().put("type", "object"))).build());
        ProviderCatalogPayload nativePath = ToolSpecSerializer.toProviderPayload(
                orphans, ProviderToolCapabilities.NATIVE_ALL);
        String wire = nativePath.payload().toString();
        assertTrue("orphan first emitted", wire.contains("raw.first"));
        assertTrue("orphan second emitted", wire.contains("raw.second"));
    }

    @Test
    public void declaredNamespaceWithCustomGroupIsEmittedNatively() throws Exception {
        ToolRegistration srv = ToolRegistration.builder(ToolSpec.namespace(
                ToolName.plain("srv"), "external svc",
                java.util.Collections.<ToolSpec>emptyList())).build();
        List<ToolRegistration> registrations = Arrays.asList(
                srv,
                ToolRegistration.builder(ToolSpec.function(
                        ToolName.namespaced("srv", "ping"), "p", new JSONObject())).build(),
                ToolRegistration.builder(ToolSpec.function(
                        ToolName.namespaced("srv", "fetch"), "f", new JSONObject())).build());
        ProviderCatalogPayload payload = ToolSpecSerializer.toProviderPayload(
                registrations, ProviderToolCapabilities.NATIVE_ALL);
        assertFalse(payload.namespaceFellBack());
        String wire = payload.payload().toString();
        assertTrue("grouped srv namespace: " + wire,
                wire.contains("\"type\":\"namespace\""));
        assertTrue("ping inside group", wire.contains("ping"));
        assertTrue("fetch inside group", wire.contains("fetch"));
    }

    @Test
    public void resolverResolvesEveryCurrentTransportToFunctionOnly() {
        for (String model : new String[]{"", "gpt-4o", "claude-3-5-sonnet-20241022",
                "gemini-2.0-flash", "llama3.1:8b"}) {
            ProviderToolCapabilities caps = ProviderToolCapabilitiesResolver.resolve("", model);
            assertEquals("function-only profile for " + model,
                    ProviderToolCapabilities.FUNCTION_ONLY.supportsFreeformTools(),
                    caps.supportsFreeformTools());
            assertTrue("declared freeform fallback for " + model,
                    caps.freeformFallbackToFunction());
            assertFalse("no native tool_search claim for " + model,
                    caps.supportsToolSearch());
        }
    }
}