package pro.sketchware.chat.agent;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ToolSequenceValidatorTest {

    private static final String FILE = "app/src/main/AndroidManifest.xml";
    private static final String ARGS = "{\"uri\":\"" + FILE + "\"}";

    @Test
    public void editRequiresReadOfSameFile() {
        ToolSequenceValidator.ValidationResult result =
                validate("edit_file", new ArrayList<>());

        assertFalse(result.isValid());
        assertTrue(result.requiresPredecessor());
        assertTrue(result.getErrorMessage().contains("read_file"));
    }

    @Test
    public void editAcceptsFreshRead() {
        List<ToolSequenceValidator.ToolUsage> history = new ArrayList<>();
        history.add(usage("read_file", true));

        assertTrue(validate("edit_file", history).isValid());
    }

    @Test
    public void editRejectsReadMadeBeforeAnotherMutation() {
        List<ToolSequenceValidator.ToolUsage> history = new ArrayList<>();
        history.add(usage("read_file", true));
        history.add(usage("rewrite_file", true));

        ToolSequenceValidator.ValidationResult result =
                validate("edit_file", history);

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("stale"));
    }

    @Test
    public void editAcceptsReadAfterMutation() {
        List<ToolSequenceValidator.ToolUsage> history = new ArrayList<>();
        history.add(usage("read_file", true));
        history.add(usage("rewrite_file", true));
        history.add(usage("read_file", true));

        assertTrue(validate("edit_file", history).isValid());
    }

    @Test
    public void rewriteOfNewFileIsAllowedOnlyImmediatelyAfterCreate() {
        List<ToolSequenceValidator.ToolUsage> history = new ArrayList<>();
        history.add(usage("create_file_or_folder", true));
        assertTrue(validate("rewrite_file", history).isValid());

        history.add(usage("rewrite_file", true));
        assertFalse(validate("rewrite_file", history).isValid());

        history.add(usage("read_file", true));
        assertTrue(validate("rewrite_file", history).isValid());
    }

    @Test
    public void staleEditProducesAutomaticFreshReadArguments() throws Exception {
        List<ToolSequenceValidator.ToolUsage> history = new ArrayList<>();
        history.add(usage("read_file", true));
        history.add(usage("rewrite_file", true));
        ToolSequenceValidator.ValidationResult result = validate("edit_file", history);

        String predecessorArgs = ToolSequenceValidator.buildPredecessorArgs(result, ARGS);

        assertNotNull(predecessorArgs);
        assertEquals(FILE, new org.json.JSONObject(predecessorArgs).getString("uri"));
    }

    @Test
    public void legacyUrlArgumentCanBeRecovered() throws Exception {
        String legacyArgs = "{\"url\":\"" + FILE + "\"}";
        ToolSequenceValidator.ValidationResult result =
                ToolSequenceValidator.validate("edit_file", legacyArgs, new ArrayList<>(), null);

        String predecessorArgs =
                ToolSequenceValidator.buildPredecessorArgs(result, legacyArgs);

        assertNotNull(predecessorArgs);
        assertEquals(FILE, new org.json.JSONObject(predecessorArgs).getString("uri"));
    }

    @Test
    public void nonReadPredecessorIsNotSynthesized() {
        ToolSequenceValidator.ValidationResult result =
                ToolSequenceValidator.validate(
                        "delete_file_or_folder", ARGS, new ArrayList<>(), null);

        assertEquals(null, ToolSequenceValidator.buildPredecessorArgs(result, ARGS));
    }

    private static ToolSequenceValidator.ValidationResult validate(
            String toolName, List<ToolSequenceValidator.ToolUsage> history) {
        return ToolSequenceValidator.validate(toolName, ARGS, history, null);
    }

    private static ToolSequenceValidator.ToolUsage usage(
            String toolName, boolean success) {
        return ToolSequenceValidator.createUsage(toolName, ARGS, success);
    }
}
