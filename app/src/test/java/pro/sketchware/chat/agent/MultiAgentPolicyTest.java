package pro.sketchware.chat.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class MultiAgentPolicyTest {

    @Test
    public void explicitRequestWinsEvenWhenClassifierCallsItChat() {
        PatternMatcher.Result pattern = PatternMatcher.analyze(
                "Use multiagente nesta tarefa", null, null);

        MultiAgentPolicy.Decision decision = MultiAgentPolicy.decide(
                MultiAgentPolicy.MODE_AUTO, pattern, "Use multiagente nesta tarefa");

        assertTrue(pattern.isChatOnly());
        assertTrue(decision.isEnabled());
        assertEquals("explicit_request", decision.getReason());
    }

    @Test
    public void broadShortBugFixUsesSpecialistsAutomatically() {
        String request = "Corrija os erros de lógica do meu app";
        PatternMatcher.Result pattern = PatternMatcher.analyze(request, null, null);

        MultiAgentPolicy.Decision decision = MultiAgentPolicy.decide(
                MultiAgentPolicy.MODE_AUTO, pattern, request);

        assertEquals(PatternMatcher.RequestType.FIX_BUG, pattern.getPrimaryType());
        assertTrue(decision.isEnabled());
        assertEquals("broad_bug_fix", decision.getReason());
    }

    @Test
    public void oneNamedFileEditStaysOnFastMainAgent() {
        String request = "Edite MainActivity.java e corrija o título";
        PatternMatcher.Result pattern = PatternMatcher.analyze(request, null, null);

        MultiAgentPolicy.Decision decision = MultiAgentPolicy.decide(
                MultiAgentPolicy.MODE_AUTO, pattern, request);

        assertFalse(decision.isEnabled());
        assertEquals("simple_request", decision.getReason());
    }

    @Test
    public void userOffSettingOverridesExplicitPrompt() {
        PatternMatcher.Result pattern = PatternMatcher.analyze(
                "Use multiagente para corrigir meu app", null, null);

        MultiAgentPolicy.Decision decision = MultiAgentPolicy.decide(
                MultiAgentPolicy.MODE_OFF, pattern, "Use multiagente para corrigir meu app");

        assertFalse(decision.isEnabled());
        assertEquals("disabled_by_user", decision.getReason());
    }

    @Test
    public void alwaysModeStillAvoidsPlainConversation() {
        PatternMatcher.Result pattern = PatternMatcher.analyze("Olá", null, null);

        MultiAgentPolicy.Decision decision = MultiAgentPolicy.decide(
                MultiAgentPolicy.MODE_ALWAYS, pattern, "Olá");

        assertFalse(decision.isEnabled());
        assertEquals("chat_only", decision.getReason());
    }

    @Test
    public void autoEscalatesWhenInspectionRevealsSeveralToolUses() {
        PatternMatcher.Result pattern = PatternMatcher.analyze(
                "Analise meu projeto", null, null);
        List<ToolSequenceValidator.ToolUsage> usages = new ArrayList<>();
        usages.add(ToolSequenceValidator.createUsage(
                "get_dir_tree", "{\"path\":\".\"}", true));
        usages.add(ToolSequenceValidator.createUsage(
                "read_file", "{\"path\":\"app/MainActivity.java\"}", true));

        MultiAgentPolicy.Decision decision =
                MultiAgentPolicy.reconsiderAfterInspection(
                        MultiAgentPolicy.MODE_AUTO, pattern, 1, usages);

        assertTrue(pattern.requiresProjectExploration());
        assertTrue(decision.isEnabled());
        assertEquals("complexity_found_during_inspection", decision.getReason());
    }
}
