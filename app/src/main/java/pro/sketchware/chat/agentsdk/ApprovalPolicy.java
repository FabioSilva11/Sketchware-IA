package pro.sketchware.chat.agentsdk;

/**
 * When the agent may run a tool without asking (Codex
 * {@code approval-policy} parity). The policy NEVER turns a DENY into an
 * ALLOW: absolutely-forbidden tools stay denied no matter what.
 */
public enum ApprovalPolicy {

    /**
     * Ask whenever a tool needs a decision (the default safe mode). Safe
     * reads run automatically; risky tools park for the user.
     */
    ON_REQUEST,

    /**
     * Never ask: tools the profile allows run, absolutely-forbidden ones
     * (e.g. leaving the workspace under WORKSPACE) are denied outright.
     */
    NEVER,

    /**
     * Reserved: per-tool approval rules will compose onto this policy in a
     * future iteration. Not selectable by the UI yet.
     */
    GRANULAR
}