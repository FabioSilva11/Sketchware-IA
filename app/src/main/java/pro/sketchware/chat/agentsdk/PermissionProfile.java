package pro.sketchware.chat.agentsdk;

/**
 * Sandbox scope of a run (Codex {@code sandbox-mode} parity): how much of
 * the machine the agent may touch. Only the USER picks the profile; the
 * {@link PermissionEvaluator} enforces it and the model never widens it.
 *
 * <p>DANGER_FULL_ACCESS means "do not restrict to the logical workspace when
 * the executor genuinely supports beyond it" — it never fakes access for an
 * executor that is intrinsically workspace-limited.</p>
 */
public enum PermissionProfile {

    /** Read-only turns: reads and safe helpers are allowed, mutations are denied. */
    READ_ONLY,

    /** Normal workspace mode: workspace writes run only when policy/user ok. */
    WORKSPACE,

    /** Full authority: the workspace and beyond, when the executor supports it. */
    DANGER_FULL_ACCESS
}