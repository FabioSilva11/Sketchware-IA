package pro.sketchware.chat.agentsdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for the {@link RunStatusReducer} / {@link AgentUiState} state
 * machine (item 13): the composer banner reacts to the structured events of a
 * run without the UI ever computing transitions itself, and a thinking phase
 * is never cleared by a premature event.
 */
public class RunStatusReducerTest {

    @Test
    public void freshReducerIsReadyAndLongLived() {
        RunStatusReducer reducer = new RunStatusReducer();
        assertEquals(RunStatus.READY, reducer.state().getRunStatus());
        assertEquals("", reducer.state().getActiveTool());
        assertFalse(reducer.state().isBusy());
    }

    @Test
    public void runStartedStaticChangesToWorking() {
        RunStatusReducer reducer = new RunStatusReducer();
        reducer.reduce(new AgentEvent.RunStarted("sc1"));
        assertEquals(RunStatus.WORKING, reducer.state().getRunStatus());
        assertTrue(reducer.state().isBusy());
    }

    @Test
    public void turnStartedShowsThinkingAndNothingClearsItPrematurely() {
        RunStatusReducer reducer = new RunStatusReducer();
        reducer.reduce(new AgentEvent.RunStarted("sc1"));
        reducer.reduce(new AgentEvent.TurnStarted("sc1", "coordinator", 1));
        assertEquals(RunStatus.THINKING, reducer.state().getRunStatus());

        // Delta / context expansion events never clear the banner.
        reducer.reduce(new AgentEvent.AssistantMessageDelta("sc1", "hel"));
        assertEquals(RunStatus.THINKING, reducer.state().getRunStatus());
        reducer.reduce(new AgentEvent.ContextCompacted("sc1", 120, 40));
        assertEquals(RunStatus.THINKING, reducer.state().getRunStatus());
        reducer.reduce(new AgentEvent.AssistantMessage("sc1", "hello"));
        assertEquals(RunStatus.THINKING, reducer.state().getRunStatus());
    }

    @Test
    public void toolCycleNavigatesToRunningToolAndBackToThinking() {
        RunStatusReducer reducer = new RunStatusReducer();
        reducer.reduce(new AgentEvent.RunStarted("sc1"));
        reducer.reduce(new AgentEvent.TurnStarted("sc1", "coordinator", 1));
        reducer.reduce(new AgentEvent.ToolCallStarted("sc1", "read_file", null));
        assertEquals(RunStatus.RUNNING_TOOL, reducer.state().getRunStatus());
        assertEquals("read_file", reducer.state().getActiveTool());
        assertTrue(reducer.state().isBusy());

        reducer.reduce(new AgentEvent.ToolCallCompleted("sc1", "read_file", null,
                AgentToolResult.success("ok")));
        assertEquals(RunStatus.THINKING, reducer.state().getRunStatus());
        assertEquals("", reducer.state().getActiveTool());
    }

    @Test
    public void approvalFlowWaitsThenContinuesToRun() {
        RunStatusReducer reducer = new RunStatusReducer();
        reducer.reduce(new AgentEvent.RunStarted("sc1"));
        reducer.reduce(new AgentEvent.TurnStarted("sc1", "coordinator", 1));
        PermissionRequest request = new PermissionRequest(
                "perm_1", "apply_patch", null, "needs approval");
        reducer.reduce(new AgentEvent.ApprovalRequired("sc1", "apply_patch", null, request));
        assertEquals(RunStatus.WAITING_APPROVAL, reducer.state().getRunStatus());
        assertEquals("apply_patch", reducer.state().getActiveTool());
        assertTrue(reducer.state().isBusy());

        reducer.reduce(new AgentEvent.PermissionResolved("sc1", "apply_patch",
                PermissionDecision.ALLOW, true, ApprovalHandler.ApprovalState.ALLOWED));
        assertEquals(RunStatus.THINKING, reducer.state().getRunStatus());
    }

    @Test
    public void denialReturnsToThinking() {
        RunStatusReducer reducer = new RunStatusReducer();
        reducer.reduce(new AgentEvent.RunStarted("sc1"));
        reducer.reduce(new AgentEvent.PolicyDenied("sc1", "apply_patch", "denied"));
        assertEquals(RunStatus.THINKING, reducer.state().getRunStatus());
    }

    @Test
    public void errorThenCompletionEndsReady() {
        RunStatusReducer reducer = new RunStatusReducer();
        reducer.reduce(new AgentEvent.RunStarted("sc1"));
        reducer.reduce(new AgentEvent.Error("sc1", "boom"));
        assertEquals(RunStatus.ERROR, reducer.state().getRunStatus());
        assertTrue(reducer.state().isBusy());

        reducer.reduce(new AgentEvent.RunCompleted("sc1", false, "boom"));
        assertEquals(RunStatus.READY, reducer.state().getRunStatus());
        assertFalse(reducer.state().isBusy());
    }

    @Test
    public void cancellationShowsCancellingUntilCompletion() {
        RunStatusReducer reducer = new RunStatusReducer();
        reducer.reduce(new AgentEvent.RunStarted("sc1"));
        reducer.reduce(new AgentEvent.TurnStarted("sc1", "coordinator", 1));
        reducer.requestCancellation();
        assertEquals(RunStatus.CANCELLING, reducer.state().getRunStatus());

        // The run still streams a completion; the reducer returns to READY.
        reducer.reduce(new AgentEvent.RunCompleted("sc1", false, "cancelled"));
        assertEquals(RunStatus.READY, reducer.state().getRunStatus());
    }

    @Test
    public void resetReturnsToIdle() {
        RunStatusReducer reducer = new RunStatusReducer();
        reducer.reduce(new AgentEvent.RunStarted("sc1"));
        reducer.reduce(new AgentEvent.ToolCallStarted("sc1", "x", null));
        reducer.reset();
        assertEquals(RunStatus.READY, reducer.state().getRunStatus());
    }

    @Test
    public void nullEventsAreIgnored() {
        RunStatusReducer reducer = new RunStatusReducer();
        reducer.reduce(new AgentEvent.RunStarted("sc1"));
        reducer.reduce(null);
        assertEquals(RunStatus.WORKING, reducer.state().getRunStatus());
    }

    @Test
    public void fullAccessRunNeverRequiresApprovalStep() {
        RunStatusReducer reducer = new RunStatusReducer();
        reducer.reduce(new AgentEvent.RunStarted("sc1"));
        reducer.reduce(new AgentEvent.TurnStarted("sc1", "coordinator", 1));
        // Nobody emits ApprovalRequired in a NEVER/full-access config; the
        // tool simply runs.
        reducer.reduce(new AgentEvent.ToolCallStarted("sc1", "apply_patch", null));
        assertEquals(RunStatus.RUNNING_TOOL, reducer.state().getRunStatus());
        reducer.reduce(new AgentEvent.ToolCallCompleted("sc1", "apply_patch", null,
                AgentToolResult.success("ok")));
        assertEquals(RunStatus.THINKING, reducer.state().getRunStatus());
    }
}