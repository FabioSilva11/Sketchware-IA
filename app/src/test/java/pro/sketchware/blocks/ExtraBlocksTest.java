package pro.sketchware.blocks;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;

public class ExtraBlocksTest {

    @Test
    public void fusedLocationStartUsesJarCompatibleLocationRequestApi() {
        ArrayList<HashMap<String, Object>> blocks = new ArrayList<>();
        ExtraBlocks.extraBlocks(blocks);

        HashMap<String, Object> fusedLocationStart = null;
        for (HashMap<String, Object> block : blocks) {
            if ("fusedLocationStart".equals(block.get("name"))) {
                fusedLocationStart = block;
                break;
            }
        }

        assertNotNull(fusedLocationStart);
        String code = String.valueOf(fusedLocationStart.get("code"));
        assertTrue(code.contains("LocationRequest.create()"));
        assertTrue(code.contains("LocationRequest.PRIORITY_HIGH_ACCURACY"));
        assertTrue(code.contains(".setInterval("));
        assertTrue(code.contains(".setFastestInterval("));
        assertFalse(code.contains("new LocationRequest.Builder"));
        assertFalse(code.contains("Priority.PRIORITY_HIGH_ACCURACY"));
    }
}
