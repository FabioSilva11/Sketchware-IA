package pro.sketchware.chat;

public class ChatThread {
    public final String id;
    public final String scId;
    public final String title;
    public final String summary;
    public final long createdAt;
    public final long updatedAt;
    public final String activeModel;
    public final boolean pinned;

    public ChatThread(String id, String scId, String title, String summary,
                      long createdAt, long updatedAt, String activeModel) {
        this(id, scId, title, summary, createdAt, updatedAt, activeModel, false);
    }

    public ChatThread(String id, String scId, String title, String summary,
                      long createdAt, long updatedAt, String activeModel, boolean pinned) {
        this.id = id == null ? "" : id;
        this.scId = scId == null ? "" : scId;
        this.title = title == null ? "" : title;
        this.summary = summary == null ? "" : summary;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.activeModel = activeModel == null ? "" : activeModel;
        this.pinned = pinned;
    }
}
