package pro.sketchware.activities.chat;

import android.net.Uri;

import androidx.annotation.Nullable;

import org.json.JSONObject;

/**
 * Staging selection item aligned with Void {@code StagingSelectionItem}:
 * File, CodeSelection, Folder and Image references attached to chat input.
 */
public class ChatReference {
    public static final int TYPE_FILE = 1;
    public static final int TYPE_FOLDER = 2;
    public static final int TYPE_IMAGE = 3;
    public static final int TYPE_CODE_SELECTION = 4;

    private final int type;
    private final String label;
    private final String path;
    private final @Nullable Uri uri;
    private final @Nullable String mimeType;
    private final long sizeBytes;
    private final int startLine;
    private final int endLine;
    private final String language;
    private final boolean wasAddedAsCurrentFile;

    private ChatReference(int type, String label, String path, @Nullable Uri uri,
                          @Nullable String mimeType, long sizeBytes,
                          int startLine, int endLine, String language,
                          boolean wasAddedAsCurrentFile) {
        this.type = type;
        this.label = label == null ? "" : label;
        this.path = path == null ? "" : path;
        this.uri = uri;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.startLine = startLine;
        this.endLine = endLine;
        this.language = language == null ? "" : language;
        this.wasAddedAsCurrentFile = wasAddedAsCurrentFile;
    }

    public static ChatReference file(String label, String path) {
        return file(label, path, false);
    }

    public static ChatReference file(String label, String path, boolean wasAddedAsCurrentFile) {
        return new ChatReference(TYPE_FILE, label, path, null, null, 0L,
                0, 0, "", wasAddedAsCurrentFile);
    }

    /**
     * A document selected through Android's Storage Access Framework. Unlike a
     * project file, this reference keeps the persisted {@code content://} URI
     * so it can still be read after the activity or process is recreated.
     */
    public static ChatReference externalFile(String label, Uri uri,
                                             @Nullable String mimeType, long sizeBytes) {
        String uriValue = uri == null ? "" : uri.toString();
        return new ChatReference(TYPE_FILE, label, uriValue, uri, mimeType, sizeBytes,
                0, 0, "", false);
    }

    public static ChatReference folder(String label, String path) {
        return new ChatReference(TYPE_FOLDER, label, path, null, null, 0L,
                0, 0, "", false);
    }

    public static ChatReference image(String label, Uri uri, @Nullable String mimeType, long sizeBytes) {
        return new ChatReference(TYPE_IMAGE, label, uri == null ? "" : uri.toString(), uri, mimeType, sizeBytes,
                0, 0, "", false);
    }

    public static ChatReference codeSelection(String label, String path, int startLine, int endLine, String language) {
        return new ChatReference(TYPE_CODE_SELECTION, label, path, null, null, 0L,
                startLine, endLine, language, false);
    }

    public static ChatReference fromJson(JSONObject object) {
        if (object == null) {
            return file("", "");
        }
        String voidType = object.optString("type", "");
        if ("File".equals(voidType)) {
            String path = object.optString("path", "");
            String uriValue = object.optString("uri", path);
            if (path.isEmpty()) {
                path = uriValue;
            }
            if (isDocumentUri(uriValue) || isDocumentUri(path)) {
                String persistedUri = isDocumentUri(uriValue) ? uriValue : path;
                return externalFile(
                        object.optString("label", object.optString("name", "")),
                        Uri.parse(persistedUri),
                        object.optString("mimeType", ""),
                        object.optLong("sizeBytes", 0L)
                );
            }
            return file(
                    object.optString("label", object.optString("name", "")),
                    path,
                    object.optJSONObject("state") != null
                            && object.optJSONObject("state").optBoolean("wasAddedAsCurrentFile", false)
            );
        }
        if ("Folder".equals(voidType)) {
            return folder(
                    object.optString("label", ""),
                    object.optString("path", object.optString("uri", ""))
            );
        }
        if ("CodeSelection".equals(voidType)) {
            int start = object.optInt("startLine", 0);
            int end = object.optInt("endLine", start);
            if (object.has("range")) {
                org.json.JSONArray range = object.optJSONArray("range");
                if (range != null && range.length() >= 2) {
                    start = range.optInt(0, start);
                    end = range.optInt(1, end);
                }
            }
            return codeSelection(
                    object.optString("label", ""),
                    object.optString("path", object.optString("uri", "")),
                    start,
                    end,
                    object.optString("language", "")
            );
        }
        int legacyType = object.optInt("type", TYPE_FILE);
        if (legacyType == TYPE_IMAGE) {
            // Prefer the dedicated "uri" field; fall back to "path" for backward compat.
            String uriStr = object.optString("uri", "");
            if (uriStr.isEmpty()) {
                uriStr = object.optString("path", "");
            }
            Uri parsedUri = uriStr.isEmpty() ? null : Uri.parse(uriStr);
            return image(
                    object.optString("label", ""),
                    parsedUri,
                    object.optString("mimeType", ""),
                    object.optLong("sizeBytes", 0L)
            );
        }
        if (legacyType == TYPE_FOLDER) {
            return folder(object.optString("label", ""), object.optString("path", ""));
        }
        if (legacyType == TYPE_CODE_SELECTION) {
            return codeSelection(
                    object.optString("label", ""),
                    object.optString("path", ""),
                    object.optInt("startLine", 0),
                    object.optInt("endLine", 0),
                    object.optString("language", "")
            );
        }
        return file(object.optString("label", ""), object.optString("path", ""));
    }

    public JSONObject toJson() {
        JSONObject object = new JSONObject();
        try {
            switch (type) {
                case TYPE_FILE:
                    object.put("type", "File");
                    if (uri != null) {
                        object.put("mimeType", mimeType == null ? "" : mimeType);
                        object.put("sizeBytes", sizeBytes);
                    }
                    break;
                case TYPE_FOLDER:
                    object.put("type", "Folder");
                    break;
                case TYPE_CODE_SELECTION:
                    object.put("type", "CodeSelection");
                    object.put("startLine", startLine);
                    object.put("endLine", endLine);
                    object.put("language", language);
                    object.put("range", new org.json.JSONArray().put(startLine).put(endLine));
                    break;
                case TYPE_IMAGE:
                default:
                    object.put("type", TYPE_IMAGE);
                    object.put("mimeType", mimeType == null ? "" : mimeType);
                    object.put("sizeBytes", sizeBytes);
                    // Store the URI string in both "path" (legacy) and "uri" (preferred)
                    // so that deserialization always has the right field regardless of version.
                    if (uri != null) {
                        object.put("uri", uri.toString());
                    }
                    break;
            }
            object.put("label", label);
            object.put("path", path);
            object.put("uri", path);
            if (type == TYPE_FILE) {
                JSONObject state = new JSONObject();
                state.put("wasAddedAsCurrentFile", wasAddedAsCurrentFile);
                object.put("state", state);
            }
        } catch (Exception ignored) {
        }
        return object;
    }

    public int getType() {
        return type;
    }

    public String getLabel() {
        return label;
    }

    public String getPath() {
        return path;
    }

    @Nullable
    public Uri getUri() {
        return uri;
    }

    @Nullable
    public String getMimeType() {
        return mimeType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public int getStartLine() {
        return startLine;
    }

    public int getEndLine() {
        return endLine;
    }

    public String getLanguage() {
        return language;
    }

    public boolean wasAddedAsCurrentFile() {
        return wasAddedAsCurrentFile;
    }

    public boolean isImage() {
        return type == TYPE_IMAGE;
    }

    public boolean isExternalFile() {
        return type == TYPE_FILE && uri != null;
    }

    public String mentionText() {
        return "@" + label;
    }

    public String stableKey() {
        if (type == TYPE_CODE_SELECTION) {
            return type + ":" + path + ":" + startLine + ":" + endLine;
        }
        return type + ":" + path;
    }

    private static boolean isDocumentUri(String value) {
        return value != null
                && (value.startsWith("content://") || value.startsWith("file://"));
    }
}
