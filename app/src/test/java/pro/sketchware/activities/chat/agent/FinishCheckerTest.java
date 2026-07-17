package pro.sketchware.activities.chat.agent;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class FinishCheckerTest {

    @Test
    public void oldOrMissingToolCallsCannotFinishCurrentAction() {
        PatternMatcher.Result pattern = PatternMatcher.analyze(
                "Corrija o erro em AgentManager.java", null, null);
        AgentMemory memory = AgentMemory.builder("Corrija o erro em AgentManager.java")
                .addKeyFiles(pattern.getExtractedFilePaths())
                .build();
        TaskPlanner.Plan plan = TaskPlanner.createPlan(pattern, memory.getOriginalUserMessage());

        assertFalse(FinishChecker.validate(
                memory, pattern, plan, new ArrayList<>(), "Vou corrigir.", "agent").canFinish());
    }

    @Test
    public void verifiedCurrentRunCanFinish() {
        PatternMatcher.Result pattern = PatternMatcher.analyze(
                "Corrija o erro em AgentManager.java", null, null);
        AgentMemory memory = AgentMemory.builder("Corrija o erro em AgentManager.java")
                .addKeyFiles(pattern.getExtractedFilePaths())
                .build();
        TaskPlanner.Plan plan = TaskPlanner.createPlan(pattern, memory.getOriginalUserMessage());
        List<ToolSequenceValidator.ToolUsage> tools = new ArrayList<>();

        record(plan, tools, "search_pathnames_only", "{\"query\":\"AgentManager.java\"}");
        record(plan, tools, "read_file", "{\"uri\":\"AgentManager.java\"}");
        record(plan, tools, "edit_file", "{\"uri\":\"AgentManager.java\"}");
        record(plan, tools, "read_lint_errors", "{}");

        assertTrue(FinishChecker.validate(
                memory, pattern, plan, tools, "Correção aplicada e verificada.", "agent").canFinish());
    }

    private static void record(TaskPlanner.Plan plan,
                               List<ToolSequenceValidator.ToolUsage> tools,
                               String name, String args) {
        tools.add(ToolSequenceValidator.createUsage(name, args, true));
        plan.recordToolUsage(name);
    }
}
