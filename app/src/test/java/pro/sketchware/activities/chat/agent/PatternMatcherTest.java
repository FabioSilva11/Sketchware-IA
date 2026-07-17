package pro.sketchware.activities.chat.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PatternMatcherTest {

    @Test
    public void greetingDoesNotForceTools() {
        PatternMatcher.Result result = PatternMatcher.analyze("Olá!", null, null);
        assertTrue(result.isChatOnly());
        assertFalse(result.hasRequiredTools());
    }

    @Test
    public void portugueseFixRequestRequiresWorkspaceTools() {
        PatternMatcher.Result result = PatternMatcher.analyze(
                "Corrija o erro no arquivo AgentManager.java", null, null);
        assertEquals(PatternMatcher.RequestType.FIX_BUG, result.getPrimaryType());
        assertTrue(result.getRequiredTools().contains("read_file"));
        assertTrue(result.getRequiredTools().contains("edit_file"));
        assertTrue(result.getExtractedFilePaths().contains("AgentManager.java"));
    }

    @Test
    public void conceptualQuestionDoesNotRequireWorkspaceInspection() {
        PatternMatcher.Result result = PatternMatcher.analyze(
                "Explique como funciona o garbage collector do Java", null, null);
        assertTrue(result.isChatOnly());
    }

    @Test
    public void projectAnalysisRequiresReading() {
        PatternMatcher.Result result = PatternMatcher.analyze(
                "Analise este projeto e explique a arquitetura", null, null);
        assertEquals(PatternMatcher.RequestType.ANALYZE_CODE, result.getPrimaryType());
        assertTrue(result.getRequiredTools().contains("read_file"));
    }

    @Test
    public void broadImplementationRequestCannotFinishAsTextOnly() {
        PatternMatcher.Result result = PatternMatcher.analyze(
                "Implemente autenticação neste app", null, null);
        assertEquals(PatternMatcher.RequestType.GENERAL_CODING, result.getPrimaryType());
        assertTrue(result.hasRequiredTools());
    }
}
