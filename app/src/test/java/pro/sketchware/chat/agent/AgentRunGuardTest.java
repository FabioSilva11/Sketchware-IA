package pro.sketchware.chat.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AgentRunGuardTest {

    @Test
    public void allowsDistinctSuccessfulObservationsWithinBudget() {
        AgentRunGuard guard = new AgentRunGuard(10, 20, 3, 4);
        for (int i = 0; i < 12; i++) {
            String args = "{\"path\":\"file" + i + ".java\"}";
            assertTrue(guard.beforeToolCall("read_file", args, false).shouldContinue());
            guard.onToolCompleted("read_file", args, "content " + i, true);
        }
        assertEquals(12, guard.getToolCalls());
        assertEquals(0, guard.getObservationsWithoutProgress());
        assertTrue(guard.hasSuccessfulToolCall());
    }

    @Test
    public void blocksFourthIdenticalSuccessfulCall() {
        AgentRunGuard guard = new AgentRunGuard(10, 20, 3, 8);
        String args = "{\"q\":\"x\"}";
        for (int i = 0; i < 3; i++) {
            assertEquals(AgentRunGuard.Outcome.ALLOW,
                    guard.beforeToolCall("search_in_file", args, false).getOutcome());
            guard.onToolCompleted("search_in_file", args, "same result", true);
        }
        assertEquals(AgentRunGuard.Outcome.FORCE_FINAL_RESPONSE,
                guard.beforeToolCall("search_in_file", args, false).getOutcome());
    }

    @Test
    public void mutationResetsRepeatedObservationWindow() {
        AgentRunGuard guard = new AgentRunGuard(10, 20, 2, 3);
        String readArgs = "{\"path\":\"same.java\"}";
        for (int i = 0; i < 2; i++) {
            assertTrue(guard.beforeToolCall("read_file", readArgs, false).shouldContinue());
            guard.onToolCompleted("read_file", readArgs, "same", true);
        }
        assertTrue(guard.beforeToolCall("edit_file", "{\"path\":\"same.java\"}", false)
                .shouldContinue());
        guard.onToolCompleted("edit_file", "{\"path\":\"same.java\"}", "edited", true);
        assertTrue(guard.beforeToolCall("read_file", readArgs, false).shouldContinue());
    }

    @Test
    public void modelTurnBudgetForcesTerminalResponse() {
        AgentRunGuard guard = new AgentRunGuard(2, 20, 3, 8);
        assertEquals(AgentRunGuard.Outcome.ALLOW, guard.beforeModelTurn(0, false).getOutcome());
        assertEquals(AgentRunGuard.Outcome.ALLOW, guard.beforeModelTurn(1, false).getOutcome());
        assertEquals(AgentRunGuard.Outcome.FORCE_FINAL_RESPONSE,
                guard.beforeModelTurn(2, false).getOutcome());
        assertEquals(AgentRunGuard.Outcome.ALLOW,
                guard.beforeModelTurn(2, true).getOutcome());
    }

    @Test
    public void toolBudgetForcesTerminalResponse() {
        AgentRunGuard guard = new AgentRunGuard(10, 2, 3, 8);
        assertTrue(guard.beforeToolCall("read_file", "{\"p\":1}", false).shouldContinue());
        guard.onToolCompleted("read_file", "{\"p\":1}", "one", true);
        assertTrue(guard.beforeToolCall("read_file", "{\"p\":2}", false).shouldContinue());
        guard.onToolCompleted("read_file", "{\"p\":2}", "two", true);
        assertEquals(AgentRunGuard.Outcome.FORCE_FINAL_RESPONSE,
                guard.beforeToolCall("read_file", "{\"p\":3}", false).getOutcome());
    }

    @Test
    public void finalResponseOnlyStillBlocksNewTools() {
        AgentRunGuard guard = new AgentRunGuard();
        AgentRunGuard.Decision decision = guard.beforeToolCall("edit_file", "{}", true);
        assertEquals(AgentRunGuard.Outcome.FORCE_FINAL_RESPONSE, decision.getOutcome());
        assertFalse(decision.shouldContinue());
        assertEquals(0, guard.getToolCalls());
    }

    @Test
    public void repeatedCompletionCandidatesForceFinalButMutationResetsWindow() {
        AgentRunGuard guard = new AgentRunGuard();
        assertEquals(AgentRunGuard.Outcome.ALLOW,
                guard.afterCompletionCandidate(true).getOutcome());
        assertEquals(AgentRunGuard.Outcome.ALLOW,
                guard.afterCompletionCandidate(true).getOutcome());

        guard.beforeToolCall("edit_file", "{\"path\":\"main.js\"}", false);
        guard.onToolCompleted("edit_file", "{\"path\":\"main.js\"}", "edited", true);

        assertEquals(AgentRunGuard.Outcome.ALLOW,
                guard.afterCompletionCandidate(true).getOutcome());
        assertEquals(AgentRunGuard.Outcome.ALLOW,
                guard.afterCompletionCandidate(true).getOutcome());
        assertEquals(AgentRunGuard.Outcome.FORCE_FINAL_RESPONSE,
                guard.afterCompletionCandidate(true).getOutcome());
    }

    @Test
    public void incompletePlanClearsCompletionCandidateWindow() {
        AgentRunGuard guard = new AgentRunGuard();
        guard.afterCompletionCandidate(true);
        guard.afterCompletionCandidate(true);
        assertEquals(AgentRunGuard.Outcome.ALLOW,
                guard.afterCompletionCandidate(false).getOutcome());
        assertEquals(AgentRunGuard.Outcome.ALLOW,
                guard.afterCompletionCandidate(true).getOutcome());
    }

    @Test
    public void resetClearsCountersAndFingerprints() {
        AgentRunGuard guard = new AgentRunGuard();
        guard.beforeModelTurn(3, false);
        guard.beforeToolCall("edit_file", "{}", false);
        guard.onToolCompleted("edit_file", "{}", "ok", true);
        guard.reset();
        assertEquals(0, guard.getModelTurns());
        assertEquals(0, guard.getToolCalls());
        assertFalse(guard.hasSuccessfulToolCall());
        assertTrue(guard.beforeToolCall("edit_file", "{}", false).shouldContinue());
    }
}
