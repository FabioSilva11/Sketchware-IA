package pro.sketchware.chat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ChatMessagePositionMapperTest {
    private static final String U = ChatMessage.ROLE_USER;
    private static final String A = ChatMessage.ROLE_ASSISTANT;
    private static final String T = ChatMessage.ROLE_TOOL;

    private static int[] layout(boolean showAds, int turnsPerAd, String... roles) {
        List<Integer> ordinals = new ArrayList<>();
        for (int i = 0; i < roles.length; i++) {
            ordinals.add(i);
        }
        return ChatMessagePositionMapper.layout(ordinals, Arrays.asList(roles), showAds, turnsPerAd);
    }

    @Test
    public void withoutAdsEveryRowIsAMessage() {
        assertArrayEquals(new int[]{0, 1, 2, 3}, layout(false, 3, U, A, U, A));
    }

    @Test
    public void adsSitOnlyBeforeAUserMessageEveryFewTurns() {
        int[] rows = layout(true, 2, U, A, U, T, A, U, A, U, A, U);
        // turns start at ordinals 0, 2, 5, 7, 9: an ad before the 3rd and the 5th turn
        assertArrayEquals(new int[]{0, 1, 2, 3, 4, -1, 5, 6, 7, 8, -2, 9}, rows);
        assertEquals(0, ChatMessagePositionMapper.adSlot(rows[5]));
        assertEquals(1, ChatMessagePositionMapper.adSlot(rows[10]));
    }

    @Test
    public void aRunningTurnNeverCrossesAnAd() {
        // A turn gets a placeholder and tool bubbles while it runs: the rows before its user message don't move
        int[] before = layout(true, 1, U, A, U, A);
        int[] during = layout(true, 1, U, A, U, A, T, T);
        int[] after = layout(true, 1, U, A, U, T, T, A);
        assertArrayEquals(new int[]{0, 1, -1, 2, 3}, before);
        assertArrayEquals(new int[]{0, 1, -1, 2, 3, 4, 5}, during);
        assertArrayEquals(new int[]{0, 1, -1, 2, 3, 4, 5}, after);
    }
}
