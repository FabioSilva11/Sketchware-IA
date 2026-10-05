package pro.sketchware.chat.agentsdk;

/**
 * Decision for a {@link PermissionRequest}, mirroring the approval states of
 * Codex ({@code Skip / NeedsApproval / Forbidden}) mapped to host intents:
 * the model never decides, only the policy and the user do.
 */
public enum PermissionDecision {

    /** Run the tool and remember the choice for the rest of the run. */
    ALLOW,

    /** Run the tool this time only; ask again on the next call. */
    ALLOW_ONCE,

    /** Do not run the tool; the model receives a rejection result. */
    DENY
}
