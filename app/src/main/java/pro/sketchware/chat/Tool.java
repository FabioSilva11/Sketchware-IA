package pro.sketchware.chat;

import org.json.JSONObject;

public interface Tool {
    String getName();
    String getDescription();
    JSONObject getParameters();
    String execute(String scId, JSONObject args) throws Exception;
    default boolean requiresApproval() { return false; }
    default boolean isDestructive() { return false; }
    default boolean isFileMutation() { return false; }
    default void cancelExecution() { }
}
