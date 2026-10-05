package pro.sketchware.chat.agentsdk.tools;

/**
 * Which tool fits which job, stated once for the model. Sketchware's chat has no shell: every operation on the
 * project goes through a workspace tool, so it stays inside the project and its encrypted files are handled.
 */
public final class ToolSelectionPolicy {

    private ToolSelectionPolicy() {
    }

    /** The permanent system-prompt instruction (Requirement 9). Kept here, not as a loose string. */
    public static final String TOOL_USAGE_POLICY_PROMPT =
            "TOOL USAGE POLICY\n\n"
            + "Use the workspace tools for every operation; there is no shell.\n\n"
            + "Use:\n"
            + "- read_file for reading files\n"
            + "- ls_dir/get_dir_tree for directory inspection\n"
            + "- search_pathnames_only/search_for_files/search_in_file for searching\n"
            + "- create_file_or_folder for creation\n"
            + "- edit_file/apply_patch for edits\n"
            + "- rewrite_file for full replacement\n"
            + "- delete_file_or_folder for deletion\n"
            + "- move_file/rename_file/copy_file for filesystem operations";
}
