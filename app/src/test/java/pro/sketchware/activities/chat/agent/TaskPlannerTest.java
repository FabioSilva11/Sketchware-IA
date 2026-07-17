package pro.sketchware.activities.chat.agent;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TaskPlannerTest {

    @Test
    public void fixPlanCompletesOnlyAfterVerification() {
        PatternMatcher.Result pattern = PatternMatcher.analyze(
                "Fix the bug in AgentManager.java", null, null);
        TaskPlanner.Plan plan = TaskPlanner.createPlan(pattern, "Fix the agent bug");

        plan.recordToolUsage("search_pathnames_only");
        plan.recordToolUsage("read_file");
        plan.recordToolUsage("edit_file");
        assertFalse(plan.isComplete());

        plan.recordToolUsage("read_lint_errors");
        assertTrue(plan.isComplete());
    }

    @Test
    public void rewriteIsAcceptedAsAnEditAlternative() {
        PatternMatcher.Result pattern = PatternMatcher.analyze(
                "Modify the file Settings.java", null, null);
        TaskPlanner.Plan plan = TaskPlanner.createPlan(pattern, "Modify Settings.java");

        plan.recordToolUsage("read_file");
        plan.recordToolUsage("rewrite_file");
        plan.recordToolUsage("read_file");
        assertTrue(plan.isComplete());
    }
}
