package pro.sketchware.ia.tools;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import pro.sketchware.activities.chat.port.VoidToolWrapper;

/**
 * ToolManager using Void ported tools as priority.
 * Legacy tools have been replaced by Void equivalents:
 * - ListProjectEntriesTool -> ls_dir, get_dir_tree
 * - ReadProjectFileTool -> read_file
 * - SearchProjectContentTool -> search_for_files, search_pathnames_only
 * - RewriteProjectFileTool -> rewrite_file
 * - EditProjectFileTool -> edit_file
 * - ListProjectFilesTool -> ls_dir
 * - DecryptTool -> (built into read_file)
 * - EncryptTool -> (built into rewrite_file, edit_file)
 * - ShellTool -> run_command
 */
public class ToolManager {
    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private volatile Tool activeTool;

    public ToolManager() {
        // Register only Void builtin chat tools. Editor-side AI services stay outside chat tool calling.
        VoidToolWrapper.registerAllVoidTools(this);
    }

    public Tool getTool(String name) {
        return tools.get(name);
    }

    public boolean hasToolForChatMode(String name, String chatMode) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        for (Tool tool : getToolsForChatMode(chatMode)) {
            if (tool != null && name.equals(tool.getName())) {
                return true;
            }
        }
        return false;
    }

    public String getToolNamesForChatMode(String chatMode) {
        StringBuilder builder = new StringBuilder();
        for (Tool tool : getToolsForChatMode(chatMode)) {
            if (tool == null || tool.getName() == null || tool.getName().trim().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(tool.getName());
        }
        return builder.toString();
    }

    public List<Tool> getToolsForChatMode(String chatMode) {
        List<Tool> available = new ArrayList<>();
        boolean includeAnyTools = !"normal".equals(chatMode);
        boolean includeApprovalTools = "agent".equals(chatMode);

        if (!includeAnyTools) {
            return available;
        }

        for (Tool tool : tools.values()) {
            if (!includeApprovalTools && tool.requiresApproval()) {
                continue;
            }
            available.add(tool);
        }
        return available;
    }

    public void registerTool(Tool tool) {
        if (tool == null || tool.getName() == null || tool.getName().trim().isEmpty()) {
            return;
        }
        tools.put(tool.getName(), tool);
    }

    public JSONArray getToolsAsMCP() {
        return getToolsAsMCP("agent");
    }

    public JSONArray getToolsAsMCP(String chatMode) {
        JSONArray array = new JSONArray();
        for (Tool tool : getToolsForChatMode(chatMode)) {
            try {
                JSONObject toolObj = new JSONObject();
                JSONObject function = new JSONObject();

                function.put("name", tool.getName());
                function.put("description", tool.getDescription());
                function.put("parameters", tool.getParameters());

                toolObj.put("type", "function");
                toolObj.put("function", function);

                array.put(toolObj);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return array;
    }

    public ToolExecResult executeTool(String scId, String name, String arguments) {
        Tool tool = null;
        try {
            if (name == null || name.trim().isEmpty()) {
                return ToolExecResult.error("Erro: nome da ferramenta nao informado.");
            }

            tool = tools.get(name);

            if (tool == null) {
                return ToolExecResult.error("Erro: ferramenta '" + name + "' nao encontrada.");
            }

            JSONObject args;

            if (arguments == null || arguments.trim().isEmpty() || "null".equals(arguments.trim())) {
                args = new JSONObject();
            } else {
                args = new JSONObject(arguments);
            }

            activeTool = tool;
            // The Void-ported tools signal failure via an explicit start-of-string
            // prefix ("Error"/"Erro"/...); fromLegacyString honours only that
            // protocol, so file contents mentioning "error" no longer flag as failure.
            return ToolExecResult.fromLegacyString(tool.execute(scId, args));

        } catch (Exception e) {
            return ToolExecResult.error("Erro ao executar ferramenta '" + name + "': " + e.getMessage());
        } finally {
            if (activeTool == tool) {
                activeTool = null;
            }
        }
    }

    public void cancelActiveTool() {
        Tool tool = activeTool;
        if (tool != null) {
            tool.cancelExecution();
        }
    }

}
