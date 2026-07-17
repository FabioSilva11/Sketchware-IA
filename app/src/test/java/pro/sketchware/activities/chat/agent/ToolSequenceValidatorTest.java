package pro.sketchware.activities.chat.agent;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class ToolSequenceValidatorTest {

    @Test
    public void editRequiresReadOfSameFile() {
        List<ToolSequenceValidator.ToolUsage> history = new ArrayList<>();
        history.add(ToolSequenceValidator.createUsage(
                "read_file", "{\"uri\":\"Other.java\"}", true));

        assertFalse(ToolSequenceValidator.validate(
                "edit_file", "{\"uri\":\"Target.java\"}", history, null).isValid());

        history.add(ToolSequenceValidator.createUsage(
                "read_file", "{\"uri\":\"Target.java\"}", true));
        assertTrue(ToolSequenceValidator.validate(
                "edit_file", "{\"uri\":\"Target.java\"}", history, null).isValid());
    }

    @Test
    public void rewriteRequiresReadOrCreate() {
        List<ToolSequenceValidator.ToolUsage> history = new ArrayList<>();
        assertFalse(ToolSequenceValidator.validate(
                "rewrite_file", "{\"uri\":\"New.java\"}", history, null).isValid());

        history.add(ToolSequenceValidator.createUsage(
                "create_file_or_folder", "{\"uri\":\"New.java\"}", true));
        assertTrue(ToolSequenceValidator.validate(
                "rewrite_file", "{\"uri\":\"New.java\"}", history, null).isValid());
    }
}
