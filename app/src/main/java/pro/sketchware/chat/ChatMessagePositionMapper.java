package pro.sketchware.chat;

import java.util.List;

/**
 * Lays out a thread for the RecyclerView: its messages in order, with native-ad slots between conversation turns.
 * <p>
 * An ad only ever sits right before a user message (every {@code turnsPerAd} turns). Everything a run adds or
 * removes — the "thinking" placeholder, tool bubbles, the answer — comes after the user message that started it,
 * so a running turn never moves across an ad (counting positions made rows jump over the ad as tool bubbles came
 * and went).
 */
final class ChatMessagePositionMapper {
    private ChatMessagePositionMapper() {
    }

    /**
     * @param ordinals message ordinals in display order
     * @param roles    role of each message ({@link ChatMessage#getRole()}), same order
     * @return one entry per row: a message ordinal (&ge; 0) or an ad slot encoded as {@code -(slot + 1)}
     */
    static int[] layout(List<Integer> ordinals, List<String> roles, boolean showAds, int turnsPerAd) {
        int ads = 0;
        int users = 0;
        if (showAds && turnsPerAd > 0) {
            for (String role : roles) {
                if (ChatMessage.ROLE_USER.equals(role)) {
                    if (users > 0 && users % turnsPerAd == 0) {
                        ads++;
                    }
                    users++;
                }
            }
        }
        int[] rows = new int[ordinals.size() + ads];
        int row = 0;
        users = 0;
        for (int i = 0; i < ordinals.size(); i++) {
            if (showAds && turnsPerAd > 0 && ChatMessage.ROLE_USER.equals(roles.get(i))) {
                if (users > 0 && users % turnsPerAd == 0) {
                    rows[row++] = -(users / turnsPerAd);
                }
                users++;
            }
            rows[row++] = ordinals.get(i);
        }
        return rows;
    }

    static boolean isAd(int row) {
        return row < 0;
    }

    static int adSlot(int row) {
        return -row - 1;
    }
}
