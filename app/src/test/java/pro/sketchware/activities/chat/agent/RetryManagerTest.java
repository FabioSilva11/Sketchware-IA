package pro.sketchware.activities.chat.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class RetryManagerTest {

    @Test
    public void neverOverwritesExistingFileAutomatically() {
        RetryManager.RetryDecision decision = RetryManager.shouldRetry(
                "create_file_or_folder",
                "{\"uri\":\"Existing.java\"}",
                "File already exists",
                1,
                new ArrayList<>());
        assertFalse(decision.shouldRetry());
    }

    @Test
    public void staleEditFallsBackToReadOnlyRefresh() {
        List<ToolSequenceValidator.ToolUsage> history = new ArrayList<>();
        history.add(ToolSequenceValidator.createUsage(
                "edit_file", "{\"uri\":\"Target.java\"}", false));
        RetryManager.RetryDecision decision = RetryManager.shouldRetry(
                "edit_file",
                "{\"uri\":\"Target.java\"}",
                "Could not apply edit_file: ORIGINAL blocks did not match",
                1,
                history);
        assertTrue(decision.shouldRetry());
        assertEquals("read_file", decision.getAlternativeTool());
    }
}
