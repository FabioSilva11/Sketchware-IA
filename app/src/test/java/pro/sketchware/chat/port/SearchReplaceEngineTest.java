package pro.sketchware.chat.port;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SearchReplaceEngineTest {

    @Test
    public void appliesUniqueExactBlockOnce() {
        SearchReplaceEngine.Result result = SearchReplaceEngine.apply(
                "alpha\nbeta\ngamma\n",
                block("beta", "updated"));

        assertTrue(result.succeeded());
        assertEquals("alpha\nupdated\ngamma\n", result.content);
    }

    @Test
    public void toleratesTrailingWhitespaceWithoutMisalignedSplice() {
        SearchReplaceEngine.Result result = SearchReplaceEngine.apply(
                "prefix  \nvalue  \nsuffix\n",
                block("value\nsuffix", "new-value\nsuffix"));

        assertTrue(result.succeeded());
        assertEquals("prefix  \nnew-value\nsuffix\n", result.content);
    }

    @Test
    public void appliesScreenshotStyleBlockWithMissingIndentation() {
        String content = "<manifest\n"
                + "    package=\"com.fabio.teste\">\n"
                + "    <application\n"
                + "        android:name=\".App\">\n"
                + "    </application>\n"
                + "</manifest>\n";
        String replacement = "package=\"com.fabio.teste\">\n"
                + "\n"
                + "<uses-permission android:name=\"android.permission.INTERNET\" />\n"
                + "\n"
                + "<application";

        SearchReplaceEngine.Result result = SearchReplaceEngine.apply(
                content,
                block("package=\"com.fabio.teste\">\n<application", replacement));

        assertTrue(result.succeeded());
        assertTrue(result.content.contains(
                "    <uses-permission android:name=\"android.permission.INTERNET\" />"));
        assertTrue(result.content.contains("\n    <application\n"));
    }

    @Test
    public void rejectsAmbiguousOriginalInsteadOfReplacingEveryOccurrence() {
        String content = "same\nmiddle\nsame\n";
        SearchReplaceEngine.Result result =
                SearchReplaceEngine.apply(content, block("same", "changed"));

        assertFalse(result.succeeded());
        assertEquals(content, result.content);
        assertEquals(1, result.failedBlock);
    }

    @Test
    public void rollsBackAllBlocksWhenLaterBlockIsStale() {
        String content = "one\ntwo\nthree\n";
        String blocks = block("one", "ONE") + "\n" + block("missing", "MISSING");

        SearchReplaceEngine.Result result = SearchReplaceEngine.apply(content, blocks);

        assertFalse(result.succeeded());
        assertEquals(content, result.content);
        assertEquals(2, result.failedBlock);
        assertEquals(0, result.appliedCount);
    }

    @Test
    public void normalizesCrLfBlocksAndFileContent() {
        SearchReplaceEngine.Result result = SearchReplaceEngine.apply(
                "one\r\ntwo\r\n",
                block("one\r\ntwo", "ONE\r\nTWO"));

        assertTrue(result.succeeded());
        assertEquals("ONE\r\nTWO\r\n", result.content);
    }

    private static String block(String original, String updated) {
        return "<<<<<<< ORIGINAL\n"
                + original
                + "\n=======\n"
                + updated
                + "\n>>>>>>> UPDATED";
    }
}
