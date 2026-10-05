package pro.sketchware.chat.agentsdk.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import pro.sketchware.chat.agentsdk.AgentToolResult;
import pro.sketchware.chat.agentsdk.EventStream;
import pro.sketchware.chat.agentsdk.RunContext;
import pro.sketchware.chat.agentsdk.WorkspaceIdentity;
import pro.sketchware.chat.agentsdk.tools.AxionToolRouter.Routed;
import pro.sketchware.chat.agentsdk.tools.ToolSpec.Type;
import pro.sketchware.chat.FileChangeTrackerWorkspaceTest.FakeWorkspaceFileSystem;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

/**
 * apply_patch parity: the FREEFORM spec carries the Codex lark grammar, the
 * executor receives the raw patch document (no JSON envelope), actual file
 * mutations hit the run's workspace, and events + tracker entries appear only
 * after a fully-applied patch.
 */
public class ApplyPatchFreeformTest {

    private FakeWorkspaceFileSystem fs;
    private static final String SC = "sc_apply_patch";

    @Before
    public void setUp() {
        fs = new FakeWorkspaceFileSystem();
        fs.directories.add("sub");
        fs.files.put("sub/hello.txt", "Hello World\n");
    }

    @After
    public void tearDown() {
        pro.sketchware.chat.FileChangeTracker.clearChanges(SC);
    }

    private AxionToolRegistry coreRegistry() {
        AxionToolRegistry registry = new AxionToolRegistry();
        WorkspaceToolProvider.registerCoreTools(registry, null);
        return registry;
    }

    @Test
    public void applyPatchIsFreeformWithLarkGrammar() {
        AxionToolRegistry registry = coreRegistry();
        ToolRegistration apply = registry.get("apply_patch");
        assertNotNull(apply);
        assertEquals(Type.FREEFORM, apply.spec().type());
        FreeformToolSpec freeform = (FreeformToolSpec) apply.spec();
        assertEquals("lark", freeform.syntax());
        assertEquals(WorkspaceToolProvider.APPLY_PATCH_GRAMMAR, freeform.definition());
        assertTrue(apply.isFileMutation());
        assertTrue(apply.isDestructive());
    }

    @Test
    public void freeformAddFileAppliesToRunWorkspace() throws Exception {
        AxionToolRegistry registry = coreRegistry();
        EventStream events = new EventStream(r -> r.run(), 32);
        AxionToolRouter router = new AxionToolRouter(registry, null, events);
        RunContext context = RunContext.bare(SC, "assistant", null);
        try (AutoCloseable binding = pro.sketchware.chat.agentsdk.RuntimeFileContext
                .install(WorkspaceIdentity.of(SC, null), fs)) {
            String patch = "*** Begin Patch\n"
                    + "*** Add File: sub/newfile.txt\n"
                    + "+Hello patch\n"
                    + "*** End Patch\n";
            Routed routed = router.route(new AxionToolRouter.Route("call_p1", "apply_patch", patch),
                    SC, context, null);
            assertFalse("patch should apply cleanly: " + routed.result().output(),
                    routed.result().isError());
            assertEquals("Hello patch\n", fs.files.get("sub/newfile.txt"));
        }
    }

    @Test
    public void freeformUpdateAppliesHunk() throws Exception {
        AxionToolRegistry registry = coreRegistry();
        AxionToolRouter router = new AxionToolRouter(registry, null,
                new EventStream(r -> r.run(), 32));
        RunContext context = RunContext.bare(SC, "assistant", null);
        try (AutoCloseable binding = pro.sketchware.chat.agentsdk.RuntimeFileContext
                .install(WorkspaceIdentity.of(SC, null), fs)) {
            String patch = "*** Begin Patch\n"
                    + "*** Update File: sub/hello.txt\n"
                    + "@@\n"
                    + "-Hello World\n"
                    + "+Hello Codex\n"
                    + "*** End Patch\n";
            Routed routed = router.route(new AxionToolRouter.Route("call_p2", "apply_patch", patch),
                    SC, context, null);
            assertFalse(routed.result().isError());
            assertEquals("Hello Codex\n", fs.files.get("sub/hello.txt"));
        }
    }

    @Test
    public void invalidPatchIsErrorAndTouchesNothing() throws Exception {
        AxionToolRegistry registry = coreRegistry();
        AxionToolRouter router = new AxionToolRouter(registry, null,
                new EventStream(r -> r.run(), 32));
        RunContext context = RunContext.bare(SC, "assistant", null);
        try (AutoCloseable binding = pro.sketchware.chat.agentsdk.RuntimeFileContext
                .install(WorkspaceIdentity.of(SC, null), fs)) {
            String patch = "*** Begin Patch\n"
                    + "*** Add File: sub/x.txt\n"
                    + "+A line\n"
                    + "no marker line\n"
                    + "*** End Patch\n";
            Routed routed = router.route(new AxionToolRouter.Route("call_p3", "apply_patch", patch),
                    SC, context, null);
            assertTrue(routed.result().isError());
            assertFalse("nothing must be written for a malformed patch",
                    fs.files.containsKey("sub/x.txt"));
        }
    }

    @Test
    public void missingRunWorkspaceFailsClosed() {
        AxionToolRegistry registry = coreRegistry();
        AxionToolRouter router = new AxionToolRouter(registry, null,
                new EventStream(r -> r.run(), 32));
        RunContext context = RunContext.bare(SC, "assistant", null);
        // No RuntimeFileContext binding → run workspace unresolved → DENY.
        Routed routed = router.route(new AxionToolRouter.Route("call_p4", "apply_patch",
                        "*** Begin Patch\n*** Add File: a.txt\n+a\n*** End Patch\n"),
                SC, context, null);
        assertTrue("fail-closed without a run workspace", routed.result().isError());
        assertTrue(routed.result().output().contains("workspace"));
    }
}