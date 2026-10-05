package pro.sketchware.chat.agentsdk;

/**
 * Immutable snapshot of a run's permission settings: a sandbox
 * {@link PermissionProfile} plus an {@link ApprovalPolicy}. The UI swaps the
 * whole config atomically; mid-run changes affect the next tool check (and
 * therefore the next run from the user's perspective).
 */
public final class PermissionConfig {

    private final PermissionProfile profile;
    private final ApprovalPolicy approvalPolicy;

    private PermissionConfig(PermissionProfile profile, ApprovalPolicy approvalPolicy) {
        this.profile = profile == null ? PermissionProfile.WORKSPACE : profile;
        this.approvalPolicy = approvalPolicy == null ? ApprovalPolicy.ON_REQUEST : approvalPolicy;
    }

    /** Explicit combination (used by tests and future GRANULAR policies). */
    public static PermissionConfig of(PermissionProfile profile, ApprovalPolicy policy) {
        return new PermissionConfig(profile, policy);
    }

    /** "Pedir aprovação": normal workspace, ask whenever a tool needs a decision. */
    public static PermissionConfig workspaceRequest() {
        return of(PermissionProfile.WORKSPACE, ApprovalPolicy.ON_REQUEST);
    }

    /** "Somente leitura": mutations are denied outright, safe reads run. */
    public static PermissionConfig readOnly() {
        return of(PermissionProfile.READ_ONLY, ApprovalPolicy.ON_REQUEST);
    }

    /** "Acesso completo": full authority, the user confirmed explicitly. */
    public static PermissionConfig fullAccess() {
        return of(PermissionProfile.DANGER_FULL_ACCESS, ApprovalPolicy.NEVER);
    }

    public PermissionProfile profile() {
        return profile;
    }

    public ApprovalPolicy approvalPolicy() {
        return approvalPolicy;
    }

    /** Whether this config auto-runs everything the profile allows. */
    public boolean neverAsk() {
        return approvalPolicy == ApprovalPolicy.NEVER;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PermissionConfig)) {
            return false;
        }
        PermissionConfig that = (PermissionConfig) other;
        return profile == that.profile && approvalPolicy == that.approvalPolicy;
    }

    @Override
    public int hashCode() {
        return profile.hashCode() * 31 + approvalPolicy.hashCode();
    }

    @Override
    public String toString() {
        return "PermissionConfig{profile=" + profile + ", approvalPolicy=" + approvalPolicy + "}";
    }
}