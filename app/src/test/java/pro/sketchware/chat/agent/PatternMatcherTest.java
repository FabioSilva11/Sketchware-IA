package pro.sketchware.chat.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class PatternMatcherTest {

    @Test
    public void readOnlyAnalysisNeverRequiresMutation() {
        PatternMatcher.Result result = PatternMatcher.analyze(
                "Analise esse erro no projeto e me diga a causa, sem alterar nada.", null, null);

        assertEquals(PatternMatcher.RequestType.ANALYZE_CODE, result.getPrimaryType());
        assertTrue(result.isReadOnly());
        assertFalse(result.getRequiredTools().contains("edit_file"));
        assertFalse(result.getRequiredTools().contains("rewrite_file"));
    }

    @Test
    public void reportAboutAppDoesNotBecomeAppCreation() {
        PatternMatcher.Result result = PatternMatcher.analyze(
                "Crie um relatório técnico sobre o app, sem modificar o projeto.", null, null);

        assertTrue(result.isReadOnly());
        assertFalse(result.getRequiredTools().contains(PatternMatcher.WORKSPACE_MUTATION_REQUIREMENT));
        assertFalse(result.getRequiredTools().contains("create_file_or_folder"));
    }

    @Test
    public void broadApplicationCreationIsHintNotHostMutationObligation() {
        PatternMatcher.Result result = PatternMatcher.analyze(
                "Crie um app de calculadora com Material Design", null, null);

        assertEquals(PatternMatcher.RequestType.GENERAL_CODING, result.getPrimaryType());
        assertTrue(result.allowsMutations());
        assertTrue(result.getRequiredTools().isEmpty());
        assertTrue(result.getOptionalTools().contains("edit_file"));
    }

    @Test
    public void compilationNeverRequiresMissingRunCommand() {
        PatternMatcher.Result result = PatternMatcher.analyze(
                "Compile o projeto e me mostre os erros", null, null);

        assertEquals(PatternMatcher.RequestType.RUN_COMMAND, result.getPrimaryType());
        assertFalse(result.getRequiredTools().contains("run_command"));
    }

    @Test
    public void explicitFileEditCreatesConcreteEvidenceRequirement() {
        PatternMatcher.Result result = PatternMatcher.analyze(
                "Edite MainActivity.java e corrija o título", null, null);

        assertEquals(PatternMatcher.RequestType.EDIT_FILE, result.getPrimaryType());
        assertTrue(result.getRequiredTools().contains("read_file"));
        assertTrue(result.getRequiredTools().contains("edit_file"));
        assertTrue(result.getExtractedFilePaths().contains("MainActivity.java"));
    }

    @Test
    public void localNegativeConstraintIsNotGlobalReadOnly() {
        PatternMatcher.Result result = PatternMatcher.analyze(
                "Não altere README.md, mas corrija MainActivity.java", null, null);

        assertTrue(result.allowsMutations());
        assertFalse(result.isReadOnly());
    }
}
