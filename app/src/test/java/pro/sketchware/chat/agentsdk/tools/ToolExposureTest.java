package pro.sketchware.chat.agentsdk.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Exposure parity: DIRECT / DEFERRED / HIDDEN and the code-mode / model-only
 * axes mirror Codex ToolExposure — the model catalog only ever sees DIRECT
 * (with optional mode flags).
 */
public class ToolExposureTest {

    @Test
    public void directIsVisibleEverywhereByDefault() {
        ToolExposure e = ToolExposure.direct();
        assertTrue(e.isDirect());
        assertFalse(e.isDeferred());
        assertFalse(e.isHidden());
        assertTrue(e.isModelVisible());
        assertTrue(e.isCodeModeVisible());
        assertFalse(e.isCodeModeOnly());
        assertFalse(e.isModelOnly());
    }

    @Test
    public void deferredIsOnlySearchDiscoverable() {
        ToolExposure e = ToolExposure.deferred();
        assertTrue(e.isDeferred());
        assertFalse(e.isModelVisible());
        assertFalse(e.isCodeModeVisible());
    }

    @Test
    public void hiddenIsNeverModelVisible() {
        ToolExposure e = ToolExposure.hidden();
        assertTrue(e.isHidden());
        assertFalse(e.isModelVisible());
        assertFalse(e.isCodeModeVisible());
    }

    @Test
    public void codeModeOnlyHidesFromPlainModelCatalog() {
        ToolExposure e = ToolExposure.directCodeModeOnly();
        assertTrue(e.isDirect());
        assertTrue(e.isCodeModeOnly());
        assertFalse(e.isModelVisible());
        assertTrue(e.isCodeModeVisible());
    }

    @Test
    public void modelOnlyIsVisibleToModelButNotSurfaceChat() {
        ToolExposure e = ToolExposure.directModelOnly();
        assertTrue(e.isModelOnly());
        assertTrue(e.isModelVisible());
        assertTrue(e.isCodeModeVisible());
    }

    @Test
    public void kindEnumOrderMatchesCodex() {
        assertEquals(ToolExposure.Kind.DIRECT, ToolExposure.direct().kind());
        assertEquals(ToolExposure.Kind.DEFERRED, ToolExposure.deferred().kind());
        assertEquals(ToolExposure.Kind.HIDDEN, ToolExposure.hidden().kind());
    }
}