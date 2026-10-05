package pro.sketchware.chat.agent;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MultiAgentOrchestratorTest {

    @Test
    public void parsesApprovedJsonInsideMarkdownFence() {
        MultiAgentOrchestrator.ReviewDecision decision =
                MultiAgentOrchestrator.parseReviewDecision(
                        "```json\n{\"approved\":true,\"reason\":\"verified\",\"feedback\":\"\"}\n```");

        assertTrue(decision.isApproved());
        assertFalse(decision.isDegraded());
        assertEquals("verified", decision.getReason());
    }

    @Test
    public void rejectedReviewAlwaysHasCorrectiveFeedback() {
        MultiAgentOrchestrator.ReviewDecision decision =
                MultiAgentOrchestrator.parseReviewDecision(
                        "{\"approved\":false,\"reason\":\"missing test\",\"feedback\":\"\"}");

        assertFalse(decision.isApproved());
        assertFalse(decision.getFeedback().isEmpty());
    }

    @Test
    public void malformedReviewerOutputDegradesWithoutBlockingCompletion() {
        MultiAgentOrchestrator.ReviewDecision decision =
                MultiAgentOrchestrator.parseReviewDecision("not-json");

        assertTrue(decision.isApproved());
        assertTrue(decision.isDegraded());
    }
}
