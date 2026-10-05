package pro.sketchware.chat.agentsdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import pro.sketchware.chat.FileChangeTracker;
import pro.sketchware.chat.FileChangeTrackerWorkspaceTest.FakeWorkspaceFileSystem;
import pro.sketchware.chat.workspace.Workspace;
import pro.sketchware.chat.workspace.WorkspaceManager;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Eval suite for the context-model migration (Codex architecture parity):
 * one execution identity per run, run-scoped filesystem for prompt/tools/
 * mutations, hierarchical AGENTS.md, honest project discovery and durable
 * task memory across compaction-like state resets.
 *
 * <pre>
 * scId → RunContextFactory → RunContext{WorkspaceIdentity, filesystem}
 *     → instructions from THIS fs → snapshot (UNKNOWN-aware)
 *     → tools + ApplyPatchTool on THIS fs → TaskMemory persisted per scId
 * </pre>
 */
public class ContextModelMigrationEvalTest {

    private FakeWorkspaceFileSystem fsA;
    private FakeWorkspaceFileSystem fsB;
    private EventStream events;

    private static final String SC_A = "sc_ctx_A";
    private static final String SC_B = "sc_ctx_B";

    @Before
    public void setUp() {
        fsA = new FakeWorkspaceFileSystem();
        fsB = new FakeWorkspaceFileSystem();
        events = new EventStream(Runnable::run, 256);
        FileChangeTracker.clearFileSystemBindings();
        FileChangeTracker.clearChanges(SC_A);
        FileChangeTracker.clearChanges(SC_B);
        ProjectInstructions.invalidate();
        RuntimeFileContext.clearForTest();
    }

    @After
    public void tearDown() {
        RunContextFactory.clearOverrideForTest();
        RuntimeFileContext.clearForTest();
        FileChangeTracker.clearFileSystemBindings();
        FileChangeTracker.clearChanges(SC_A);
        FileChangeTracker.clearChanges(SC_B);
        ProjectInstructions.invalidate();
    }

    // ------------------------------------------------------------------
    // Test B — AGENTS.md per workspace: run A never sees B's instructions
    // ------------------------------------------------------------------

    @Test
    public void agentsMd_isResolvedFromTheRunWorkspace_notTheGlobalOne() throws IOException {
        fsA.writeText("AGENTS.md", "# Regras A\nSem TODOs.");
        fsB.writeText("AGENTS.md", "# Regras B\nPadrao B.");

        // Run pinned on A: only A's instructions are visible.
        bindRun(SC_A, fsA);
        String instructionsA = ProjectInstructions.load(RuntimeFileContext.effectiveFileSystem(), "", 4000);
        assertTrue(instructionsA.contains("Regras A"));
        assertFalse("run A must NOT see workspace B instructions", instructionsA.contains("Regras B"));

        // Run pinned on B: only B's instructions are visible.
        bindRun(SC_B, fsB);
        String instructionsB = ProjectInstructions.load(RuntimeFileContext.effectiveFileSystem(), "", 4000);
        assertTrue(instructionsB.contains("Regras B"));
        assertFalse("run B must NOT see workspace A instructions", instructionsB.contains("Regras A"));
    }

    // ------------------------------------------------------------------
    // Test C — hierarchical AGENTS.md with Codex precedence
    // ------------------------------------------------------------------

    @Test
    public void agentsMd_hierarchy_rootPlusNestedPlusOverride() throws IOException {
        fsA.writeText("AGENTS.md", "ROOT-RULE");
        fsA.writeText("app/AGENTS.md", "APP-RULE");
        fsA.writeText("app/src/AGENTS.override.md", "OVERRIDE-RULE");

        bindRun(SC_A, fsA);

        String atRoot = ProjectInstructions.load(RuntimeFileContext.effectiveFileSystem(), "", 8000);
        assertTrue(atRoot.contains("ROOT-RULE"));
        assertFalse("deep instructions do not apply above their directory",
                atRoot.contains("APP-RULE"));

        String atSrc = ProjectInstructions.load(RuntimeFileContext.effectiveFileSystem(), "app/src/SomeFile.kt", 8000);
        assertTrue(atSrc.contains("ROOT-RULE"));
        assertTrue(atSrc.contains("APP-RULE"));
        assertTrue(atSrc.contains("OVERRIDE-RULE"));
        // Precedence order: root first, then deeper, override last on its dir.
        assertTrue(atSrc.indexOf("ROOT-RULE") < atSrc.indexOf("APP-RULE"));
        assertTrue(atSrc.indexOf("APP-RULE") < atSrc.indexOf("OVERRIDE-RULE"));
    }

    // ------------------------------------------------------------------
    // Test D — cache identity: instructions cache never leaks across runs
    // ------------------------------------------------------------------

    @Test
    public void instructionsCache_identityIncludesTheRunFilesystem() throws IOException {
        fsA.writeText("AGENTS.md", "WS-A-ONLY");
        bindRun(SC_A, fsA);
        String first = ProjectInstructions.load(RuntimeFileContext.effectiveFileSystem(), "", 4000);
        assertTrue(first.contains("WS-A-ONLY"));

        // Run B pinned on its own fs: the cache key follows the run identity,
        // so cached[A] can never be served to a run pinned on B's filesystem.
        bindRun(SC_B, fsB);
        fsB.writeText("AGENTS.md", "WS-B-ONLY");
        String second = ProjectInstructions.load(RuntimeFileContext.effectiveFileSystem(), "", 4000);
        assertTrue(second.contains("WS-B-ONLY"));
        assertNotEquals(first, second);
    }

    // ------------------------------------------------------------------
    // Test F/G — honest snapshot: discovery never invents values
    // ------------------------------------------------------------------

    @Test
    public void projectSnapshot_gradleDiscovery_realValuesAndUnknownElsewhere() throws IOException {
        fsA.writeText("settings.gradle", "include ':app'\n");
        fsA.writeText("app/build.gradle",
                "android {\n    namespace 'com.example.appa'\n    applicationId 'com.example.appa'\n}\n");

        ProjectSnapshot snapshot = ProjectDiscovery.discover(fsA, "");

        assertEquals("Gradle", snapshot.buildSystem());
        assertEquals("com.example.appa", snapshot.namespace());
        assertEquals("com.example.appa", snapshot.applicationId());
        assertEquals(true, snapshot.modules().contains("app"));
        // Anti-hallucination contract: what was NOT verified stays UNKNOWN.
        assertTrue(snapshot.renderPromptBlock().contains("UNKNOWN"));
    }

    @Test
    public void projectSnapshot_emptyWorkspace_staysUnknown() {
        ProjectSnapshot snapshot = ProjectDiscovery.discover(fsA, "");
        assertEquals(ProjectSnapshot.UNKNOWN, snapshot.buildSystem());
        assertEquals(ProjectSnapshot.UNKNOWN, snapshot.namespace());
        assertEquals(ProjectSnapshot.UNKNOWN, snapshot.applicationId());
    }

    // ------------------------------------------------------------------
    // Test E — task memory survives compaction-like state loss
    // ------------------------------------------------------------------

    @Test
    public void taskMemory_persistsAcrossRuns_viaStore() {
        TaskMemory first = new TaskMemory(SC_A, "ws-a", "refatorar o módulo de login");
        first.recordFile("app/src/main/java/Login.kt");
        first.setPhase("implementando");
        first.recordAppliedChange("edit_file: Login.kt");
        TaskMemoryStore.save(SC_A, first);

        // "Compaction"/process death: a brand-new memory restores from disk.
        TaskMemory second = new TaskMemory(SC_A, "ws-a", "");
        TaskMemoryStore.restoreInto(SC_A, second);

        assertEquals("implementando", second.phase());
        assertTrue(second.relevantFiles().contains("app/src/main/java/Login.kt"));
        assertTrue(second.appliedChanges().contains("edit_file: Login.kt"));
        assertEquals(true, second.renderPromptBlock().contains("refatorar o módulo de login"));

        TaskMemoryStore.clear(SC_A);
        assertNull(TaskMemoryStore.load(SC_A));
    }

    // ------------------------------------------------------------------
    // Tests A + H — run-scoped fs for tools and mutations
    // ------------------------------------------------------------------

    @Test
    public void runPinnedFilesystem_toolsAndMutationsStayOnTheRunWorkspace() throws Exception {
        bindRun(SC_A, fsA);

        // The run's binding is what tools see (same resolution order the
        // VoidPortToolsService registry and ApplyPatchTool use).
        assertTrue("effective fs must be the run's fs",
                RuntimeFileContext.effectiveFileSystem() == fsA);
        WorkspaceIdentity pinnedId = RuntimeFileContext.effectiveIdentity();
        assertNotNull(pinnedId);
        assertEquals(SC_A, pinnedId.scId());

        // apply_patch with NO injected fs still mutates the RUN's workspace.
        fsA.writeText("src/A.java", "original A\n");
        ApplyPatchTool patch = new ApplyPatchTool(SC_A, events);
        AgentToolResult result = patch.apply(RunContext.bare(SC_A, "agent", null),
                "*** Begin Patch\n*** Update File: src/A.java\n@@\n-original A\n+patched A\n*** End Patch");
        assertFalse(result.isError());
        assertEquals("patched A\n", fsA.readText("src/A.java"));
        assertFalse("B untouched by the run pinned on A", fsB.exists("src/A.java"));
    }

    @Test
    public void runtimeRun_usesTheRunFilesystem_forPromptAndSnapshot() throws IOException {
        fsA.writeText("AGENTS.md", "RUN-SCOPED-INSTRUCTION");
        fsA.writeText("package.json", "{\"name\": \"appa\"}");

        RunContextFactory.setOverrideForTest(new RunContextFactory.Resolved(
                new WorkspaceIdentity(SC_A, "ws-ctx-a", "file:///ws-a", "wsA", ""), fsA));

        FakeAgentLlmGateway gateway = new FakeAgentLlmGateway(
                FakeAgentLlmGateway.ScriptedTurn.text("done"));
        AgentRuntime runtime = new AgentRuntime.Builder(gateway)
                .includeProjectInstructions(true)
                .toolRegistry(new pro.sketchware.chat.agentsdk.tools.AxionToolRegistry())
                .build();

        RunResult result = runtime.run(agent(), "mecha o projeto", SC_A);

        assertTrue(result.isSuccessful());
        assertNotNull(result.context());
        assertEquals(SC_A, result.context().scId());
        assertEquals("ws-ctx-a", result.context().workspace().workspaceId());
        // Prompt came from the RUN's workspace (not the global one).
        assertTrue(gateway.requestedSystemPrompts().get(0).contains("RUN-SCOPED-INSTRUCTION"));
        // Snapshot was discovered from the run's fs and is UNKNOWN-aware.
        assertTrue(gateway.requestedSystemPrompts().get(0).contains("workspace_snapshot"));
        assertTrue(gateway.requestedSystemPrompts().get(0).contains("Node.js"));
        // Task memory was persisted for the next run of this conversation.
        assertNotNull(TaskMemoryStore.load(SC_A));
        TaskMemoryStore.clear(SC_A);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static Agent agent() {
        return Agent.Builder.forName("coordinator", "You are a helpful coding agent.").build();
    }

    /** Simulates the runtime pin for one run on the given fake workspace. */
    private static void bindRun(String scId, FakeWorkspaceFileSystem fs) {
        RunContextFactory.setOverrideForTest(new RunContextFactory.Resolved(
                new WorkspaceIdentity(scId, "ws-" + scId, "fake://" + scId, scId, ""), fs));
        RuntimeFileContext.pin("test_" + scId,
                new WorkspaceIdentity(scId, "ws-" + scId, "fake://" + scId, scId, ""), fs);
    }
}
