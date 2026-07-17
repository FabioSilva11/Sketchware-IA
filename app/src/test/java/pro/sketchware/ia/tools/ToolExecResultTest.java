package pro.sketchware.ia.tools;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ToolExecResultTest {

    @Test
    public void knownToolFailuresAreNotReportedAsSuccess() {
        assertFalse(ToolExecResult.fromLegacyString(
                "Directory not found: app/missing").ok);
        assertFalse(ToolExecResult.fromLegacyString(
                "Could not apply edit_file: ORIGINAL block did not match").ok);
        assertFalse(ToolExecResult.fromLegacyString(
                "Invalid SEARCH/REPLACE blocks: no valid blocks found").ok);
    }

    @Test
    public void ordinaryContentContainingErrorWordRemainsSuccessful() {
        assertTrue(ToolExecResult.fromLegacyString(
                "The file contains an error handler and an exception class.").ok);
    }
}
