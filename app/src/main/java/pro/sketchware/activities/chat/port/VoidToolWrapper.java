package pro.sketchware.activities.chat.port;

import org.json.JSONArray;
import org.json.JSONObject;

import pro.sketchware.activities.chat.PromptConstants;
import pro.sketchware.ia.tools.Tool;
import pro.sketchware.ia.tools.ToolManager;

/**
 * Wrapper that adapts VoidPortToolsService builtin tools to the Tool interface.
 * All Void tools are prioritized over legacy Sketchware-IA tools.
 */
public class VoidToolWrapper implements Tool {
    private final String toolName;
    private final String description;
    private final JSONObject parameters;
    private final boolean requiresApproval;
    private final boolean isDestructive;
    private final boolean isFileMutation;

    public VoidToolWrapper(String toolName, String description, JSONObject parameters, 
                          boolean requiresApproval, boolean isDestructive, boolean isFileMutation) {
        this.toolName = toolName;
        this.description = description;
        this.parameters = parameters;
        this.requiresApproval = requiresApproval;
        this.isDestructive = isDestructive;
        this.isFileMutation = isFileMutation;
    }

    @Override
    public String getName() {
        return toolName;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public JSONObject getParameters() {
        return parameters;
    }

    @Override
    public String execute(String scId, JSONObject args) throws Exception {
        return VoidPortToolsService.executeTool(scId, toolName, args);
    }

    @Override
    public boolean requiresApproval() {
        return requiresApproval;
    }

    @Override
    public boolean isDestructive() {
        return isDestructive;
    }

    @Override
    public boolean isFileMutation() {
        return isFileMutation;
    }

    public static void registerAllVoidTools(ToolManager manager) {
        if (registerVoidToolDefinitions(manager)) {
            return;
        }

        // File tools - read operations (no approval required)
        manager.registerTool(new VoidToolWrapper(
            "read_file",
            "Returns full contents of a given file.",
            createParams(new String[][]{
                {"uri", "string", "The FULL path to the file."}
            }, new String[][]{
                {"start_line", "number", "Optional. Do NOT fill this field in unless you were specifically given exact line numbers to search. Defaults to the beginning of the file."},
                {"end_line", "number", "Optional. Do NOT fill this field in unless you were specifically given exact line numbers to search. Defaults to the end of the file."},
                {"page_number", "number", "Optional. The page number of the result. Default is 1."}
            }),
            false,
            false,
            false
        ));

        manager.registerTool(new VoidToolWrapper(
            "ls_dir",
            "Lists all files and folders in the given URI.",
            createParams(null, new String[][]{
                {"uri", "string", "Optional. The FULL path to the folder. Leave this as empty or \"\" to search all folders."},
                {"page_number", "number", "Optional. The page number of the result. Default is 1."}
            }),
            false,
            false,
            false
        ));

        manager.registerTool(new VoidToolWrapper(
            "get_dir_tree",
            "This is a very effective way to learn about the user's codebase. Returns a tree diagram of all the files and folders in the given folder.",
            createParams(new String[][]{
                {"uri", "string", "The FULL path to the folder."}
            }, null),
            false,
            false,
            false
        ));

        // Search tools (no approval required)
        manager.registerTool(new VoidToolWrapper(
            "search_pathnames_only",
            "Returns all pathnames that match a given query (searches ONLY file names). You should use this when looking for a file with a specific name or path.",
            createParams(new String[][]{
                {"query", "string", "Your query for the search."}
            }, new String[][]{
                {"include_pattern", "string", "Optional. Only fill this in if you need to limit your search because there were too many results."},
                {"page_number", "number", "Optional. The page number of the result. Default is 1."}
            }),
            false,
            false,
            false
        ));

        manager.registerTool(new VoidToolWrapper(
            "search_for_files",
            "Returns a list of file names whose content matches the given query. The query can be any substring or regex.",
            createParams(new String[][]{
                {"query", "string", "Your query for the search."}
            }, new String[][]{
                {"search_in_folder", "string", "Optional. Leave as blank by default. ONLY fill this in if your previous search with the same query was truncated. Searches descendants of this folder only."},
                {"is_regex", "boolean", "Optional. Default is false. Whether the query is a regex."},
                {"page_number", "number", "Optional. The page number of the result. Default is 1."}
            }),
            false,
            false,
            false
        ));

        manager.registerTool(new VoidToolWrapper(
            "search_in_file",
            "Returns an array of all the start line numbers where the content appears in the file.",
            createParams(new String[][]{
                {"uri", "string", "The FULL path to the file."},
                {"query", "string", "The string or regex to search for in the file."}
            }, new String[][]{
                {"is_regex", "boolean", "Optional. Default is false. Whether the query is a regex."}
            }),
            false,
            false,
            false
        ));

        manager.registerTool(new VoidToolWrapper(
            "read_lint_errors",
            "Use this tool to view all the lint errors on a file.",
            createParams(new String[][]{
                {"uri", "string", "The FULL path to the file."}
            }, null),
            false,
            false,
            false
        ));

        // Edit tools - require approval (destructive operations)
        manager.registerTool(new VoidToolWrapper(
            "create_file_or_folder",
            "Create a file or folder at the given path. To create a folder, the path MUST end with a trailing slash.",
            createParams(new String[][]{
                {"uri", "string", "The FULL path to the file or folder."}
            }, null),
            true,
            false,
            true
        ));

        manager.registerTool(new VoidToolWrapper(
            "delete_file_or_folder",
            "Delete a file or folder at the given path.",
            createParams(new String[][]{
                {"uri", "string", "The FULL path to the file or folder."}
            }, new String[][]{
                {"is_recursive", "boolean", "Optional. Return true to delete recursively."}
            }),
            true,
            true,
            true
        ));

        manager.registerTool(new VoidToolWrapper(
            "edit_file",
            "Edit the contents of a file. You must provide the file's URI as well as a SINGLE string of SEARCH/REPLACE block(s) that will be used to apply the edit.",
            createParams(new String[][]{
                {"uri", "string", "The FULL path to the file."},
                {"search_replace_blocks", "string", PromptConstants.SEARCH_REPLACE_BLOCKS_TOOL_DESCRIPTION}
            }, null),
            true,
            true,
            true
        ));

        manager.registerTool(new VoidToolWrapper(
            "rewrite_file",
            "Edits a file, deleting all the old contents and replacing them with your new contents. Use this tool if you want to edit a file you just created.",
            createParams(new String[][]{
                {"uri", "string", "The FULL path to the file."},
                {"new_content", "string", "The new contents of the file. Must be a string."}
            }, null),
            true,
            true,
            true
        ));

        // Planning tool (no approval required) — keeps the plan tab in sync with
        // the model's own step plan, Codex-style.
        manager.registerTool(new VoidToolWrapper(
            "update_plan",
            "Updates the step-by-step plan shown to the user. Call this when starting a multi-step task and again whenever a step's status changes. Always send the FULL plan, one step per line, in the format 'pending: step title', 'running: step title' or 'done: step title'.",
            createParams(new String[][]{
                {"plan", "string", "The full plan, one step per line: 'pending|running|done: step title'."}
            }, null),
            false,
            false,
            false
        ));

        // Terminal/shell tools removed: the assistant no longer has shell access.
    }

    private static boolean registerVoidToolDefinitions(ToolManager manager) {
        if (manager == null) {
            return false;
        }
        JSONArray toolDefinitions = VoidPortToolsService.getAllToolsAsMCP();
        boolean registeredAny = false;
        for (int i = 0; toolDefinitions != null && i < toolDefinitions.length(); i++) {
            JSONObject toolObject = toolDefinitions.optJSONObject(i);
            JSONObject function = toolObject == null ? null : toolObject.optJSONObject("function");
            if (function == null) {
                continue;
            }
            String name = function.optString("name", "").trim();
            if (name.isEmpty()) {
                continue;
            }
            JSONObject parameters = function.optJSONObject("parameters");
            manager.registerTool(new VoidToolWrapper(
                    name,
                    function.optString("description", ""),
                    parameters == null ? new JSONObject() : parameters,
                    requiresApprovalFor(name),
                    isDestructiveTool(name),
                    isFileMutationTool(name)
            ));
            registeredAny = true;
        }
        return registeredAny;
    }

    private static boolean requiresApprovalFor(String toolName) {
        return switch (toolName) {
            case "rewrite_file", "edit_file", "create_file_or_folder", "delete_file_or_folder" -> true;
            default -> false;
        };
    }

    private static boolean isDestructiveTool(String toolName) {
        return switch (toolName) {
            case "rewrite_file", "edit_file", "delete_file_or_folder" -> true;
            default -> false;
        };
    }

    private static boolean isFileMutationTool(String toolName) {
        return switch (toolName) {
            case "rewrite_file", "edit_file", "create_file_or_folder", "delete_file_or_folder" -> true;
            default -> false;
        };
    }

    /**
     * Creates parameter schema with required and optional parameters.
     * @param required Array of [name, type, description] for required parameters
     * @param optional Array of [name, type, description] for optional parameters
     */
    private static JSONObject createParams(String[][] required, String[][] optional) {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            
            JSONObject properties = new JSONObject();
            JSONArray requiredArray = new JSONArray();
            
            // Add required parameters
            if (required != null) {
                for (String[] req : required) {
                    JSONObject prop = new JSONObject();
                    prop.put("type", req[1]);
                    prop.put("description", req[2]);
                    properties.put(req[0], prop);
                    requiredArray.put(req[0]);
                }
            }
            
            // Add optional parameters
            if (optional != null) {
                for (String[] opt : optional) {
                    JSONObject prop = new JSONObject();
                    prop.put("type", opt[1]);
                    prop.put("description", opt[2]);
                    properties.put(opt[0], prop);
                }
            }
            
            params.put("properties", properties);
            params.put("required", requiredArray);
            
            return params;
        } catch (Exception e) {
            e.printStackTrace();
            return new JSONObject();
        }
    }
}
