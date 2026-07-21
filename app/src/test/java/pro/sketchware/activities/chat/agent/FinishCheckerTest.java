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

    @Test
    public void alternativeDiscoveryToolsSatisfyBroadImplementation() {
        String[] discoveryTools = {
                "ls_dir", "get_dir_tree", "search_pathnames_only", "search_for_files"
        };

        for (String discoveryTool : discoveryTools) {
            PatternMatcher.Result pattern = PatternMatcher.analyze(
                    "Implemente autenticação neste app", null, null);
            TaskPlanner.Plan plan = TaskPlanner.createPlan(pattern, "Implement authentication");
            List<ToolSequenceValidator.ToolUsage> tools = new ArrayList<>();

            record(plan, tools, discoveryTool, "{}");
            record(plan, tools, "edit_file", "{\"uri\":\"Auth.java\"}");
            record(plan, tools, "read_file", "{\"uri\":\"Auth.java\"}");

            assertTrue(discoveryTool, FinishChecker.validate(
                    null, pattern, plan, tools, "Implementação concluída.", "agent").canFinish());
        }
    }

    @Test
    public void broadImplementationStillCannotFinishAsTextOnly() {
        PatternMatcher.Result pattern = PatternMatcher.analyze(
                "Implemente autenticação neste app", null, null);

        FinishChecker.ValidationResult result = FinishChecker.validate(
                null, pattern, null, new ArrayList<>(), "Implementação concluída.", "agent");

        assertFalse(result.canFinish());
        assertFalse(result.getFeedbackPrompt().contains(
                PatternMatcher.PROJECT_DISCOVERY_REQUIREMENT));
        assertTrue(result.getFeedbackPrompt().contains("ls_dir"));
        assertTrue(result.getFeedbackPrompt().contains("get_dir_tree"));
        assertTrue(result.getFeedbackPrompt().contains("search_pathnames_only"));
        assertTrue(result.getFeedbackPrompt().contains("search_for_files"));
    }

    private static void record(TaskPlanner.Plan plan,
                               List<ToolSequenceValidator.ToolUsage> tools,
                               String name, String args) {
        tools.add(ToolSequenceValidator.createUsage(name, args, true));
        plan.recordToolUsage(name);
    }
}
