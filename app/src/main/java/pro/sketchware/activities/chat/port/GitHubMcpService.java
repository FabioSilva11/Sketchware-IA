package pro.sketchware.activities.chat.port;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Native GitHub MCP tool provider.
 *
 * Exposes a subset of the GitHub REST API as chat tools so the agent can
 * browse repos, read files, search code, create/update issues and open PRs
 * without leaving the chat.  The token is stored in SharedPreferences under
 * {@link VoidPortSettings#PREF_GITHUB_TOKEN} and is never sent to the LLM.
 *
 * All network calls are synchronous (called from a background thread by
 * {@link pro.sketchware.activities.chat.AgentManager}).
 */
public final class GitHubMcpService {

    public static final String TOOL_PREFIX = "github_";

    // Tool names (without prefix)
    public static final String TOOL_LIST_REPOS       = TOOL_PREFIX + "list_repos";
    public static final String TOOL_GET_REPO         = TOOL_PREFIX + "get_repo";
    public static final String TOOL_LIST_BRANCHES    = TOOL_PREFIX + "list_branches";
    public static final String TOOL_GET_FILE         = TOOL_PREFIX + "get_file";
    public static final String TOOL_LIST_FILES       = TOOL_PREFIX + "list_files";
    public static final String TOOL_SEARCH_CODE      = TOOL_PREFIX + "search_code";
    public static final String TOOL_LIST_ISSUES      = TOOL_PREFIX + "list_issues";
    public static final String TOOL_CREATE_ISSUE     = TOOL_PREFIX + "create_issue";
    public static final String TOOL_LIST_PRS         = TOOL_PREFIX + "list_pull_requests";
    public static final String TOOL_CREATE_PR        = TOOL_PREFIX + "create_pull_request";
    public static final String TOOL_CREATE_COMMIT    = TOOL_PREFIX + "create_or_update_file";
    public static final String TOOL_GET_COMMIT       = TOOL_PREFIX + "get_commit";
    public static final String TOOL_LIST_COMMITS     = TOOL_PREFIX + "list_commits";
    public static final String TOOL_CREATE_REPO      = TOOL_PREFIX + "create_repository";
    public static final String TOOL_CREATE_BRANCH    = TOOL_PREFIX + "create_branch";

    private static final String GITHUB_API = "https://api.github.com";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build();

    private GitHubMcpService() {}

    // ─────────────────────────────────────────────────────────────────
    // Tool schema registration
    // ─────────────────────────────────────────────────────────────────

    /** Returns MCP-style tool definitions for all GitHub tools. */
    @NonNull
    public static JSONArray getToolDefinitions() {
        JSONArray tools = new JSONArray();
        try {
            tools.put(tool(TOOL_LIST_REPOS,
                    "List repositories for a GitHub user or organisation.",
                    params()
                        .req("owner", "string", "GitHub user or org name")));

            tools.put(tool(TOOL_GET_REPO,
                    "Get details (description, stars, language, default branch) of a GitHub repository.",
                    params()
                        .req("owner", "string", "Repository owner")
                        .req("repo",  "string", "Repository name")));

            tools.put(tool(TOOL_LIST_BRANCHES,
                    "List branches of a GitHub repository.",
                    params()
                        .req("owner", "string", "Repository owner")
                        .req("repo",  "string", "Repository name")));

            tools.put(tool(TOOL_CREATE_REPO,
                    "Create a GitHub repository for the authenticated user.",
                    params()
                        .req("name", "string", "Repository name")
                        .opt("description", "string", "Repository description")
                        .opt("private", "boolean", "Create a private repository (default: true)")));

            tools.put(tool(TOOL_CREATE_BRANCH,
                    "Create a branch from an existing branch or commit.",
                    params()
                        .req("owner", "string", "Repository owner")
                        .req("repo", "string", "Repository name")
                        .req("branch", "string", "New branch name")
                        .opt("from", "string", "Source branch or commit SHA (default: repository default branch)")));

            tools.put(tool(TOOL_GET_FILE,
                    "Read the content of a file from a GitHub repository.",
                    params()
                        .req("owner", "string", "Repository owner")
                        .req("repo",  "string", "Repository name")
                        .req("path",  "string", "File path inside the repository")
                        .opt("ref",   "string", "Branch, tag or commit SHA (default: default branch)")));

            tools.put(tool(TOOL_LIST_FILES,
                    "List files and directories at a path in a GitHub repository.",
                    params()
                        .req("owner", "string", "Repository owner")
                        .req("repo",  "string", "Repository name")
                        .opt("path",  "string", "Directory path (default: root)")
                        .opt("ref",   "string", "Branch, tag or commit SHA")));

            tools.put(tool(TOOL_SEARCH_CODE,
                    "Search code inside a GitHub repository using the GitHub Search API.",
                    params()
                        .req("query", "string", "Search query, e.g. 'AgentManager repo:FabioSilva11/Sketchware-IA'")));

            tools.put(tool(TOOL_LIST_ISSUES,
                    "List open issues of a GitHub repository.",
                    params()
                        .req("owner", "string", "Repository owner")
                        .req("repo",  "string", "Repository name")
                        .opt("state", "string", "Filter by state: open (default), closed, all")));

            tools.put(tool(TOOL_CREATE_ISSUE,
                    "Create a new issue in a GitHub repository.",
                    params()
                        .req("owner", "string", "Repository owner")
                        .req("repo",  "string", "Repository name")
                        .req("title", "string", "Issue title")
                        .opt("body",  "string", "Issue body in Markdown")
                        .opt("labels","string", "Comma-separated label names")));

            tools.put(tool(TOOL_LIST_PRS,
                    "List pull requests of a GitHub repository.",
                    params()
                        .req("owner", "string", "Repository owner")
                        .req("repo",  "string", "Repository name")
                        .opt("state", "string", "Filter by state: open (default), closed, all")));

            tools.put(tool(TOOL_CREATE_PR,
                    "Create a pull request in a GitHub repository.",
                    params()
                        .req("owner", "string", "Repository owner")
                        .req("repo",  "string", "Repository name")
                        .req("title", "string", "PR title")
                        .req("head",  "string", "Branch to merge FROM")
                        .req("base",  "string", "Branch to merge INTO")
                        .opt("body",  "string", "PR description in Markdown")));

            tools.put(tool(TOOL_CREATE_COMMIT,
                    "Create or update a single file in a GitHub repository (creates a commit).",
                    params()
                        .req("owner",   "string", "Repository owner")
                        .req("repo",    "string", "Repository name")
                        .req("path",    "string", "File path inside the repository")
                        .req("message", "string", "Commit message")
                        .req("content", "string", "New file content (plain text, not base64)")
                        .opt("branch",  "string", "Target branch (default: default branch)")));

            tools.put(tool(TOOL_GET_COMMIT,
                    "Get details of a specific commit in a GitHub repository.",
                    params()
                        .req("owner", "string", "Repository owner")
                        .req("repo",  "string", "Repository name")
                        .req("sha",   "string", "Commit SHA")));

            tools.put(tool(TOOL_LIST_COMMITS,
                    "List recent commits on a branch of a GitHub repository.",
                    params()
                        .req("owner", "string", "Repository owner")
                        .req("repo",  "string", "Repository name")
                        .opt("branch","string", "Branch name (default: default branch)")
                        .opt("limit", "string", "Max number of commits to return (default: 20)")));

        } catch (JSONException ignored) {}
        return tools;
    }

    // ─────────────────────────────────────────────────────────────────
    // Tool dispatch
    // ─────────────────────────────────────────────────────────────────

    /**
     * Dispatches a tool call to the GitHub REST API.
     *
     * @param prefs    SharedPreferences containing the GitHub token
     * @param toolName Full tool name (including {@link #TOOL_PREFIX})
     * @param args     Tool arguments from the LLM
     * @return Human-readable result string for the LLM
     */
    @NonNull
    public static String callTool(SharedPreferences prefs, String toolName, JSONObject args) {
        String token = prefs.getString(VoidPortSettings.PREF_GITHUB_TOKEN, "").trim();
        if (token.isEmpty()) {
            return "GitHub MCP error: no token configured. Go to AI Settings → GitHub and enter a Personal Access Token.";
        }
        if (args == null) args = new JSONObject();
        try {
            return dispatch(token, toolName, args);
        } catch (Exception e) {
            return "GitHub MCP error for '" + toolName + "': " + e.getMessage();
        }
    }

    @NonNull
    private static String dispatch(String token, String toolName, JSONObject a)
            throws Exception {
        switch (toolName) {

            case TOOL_LIST_REPOS: {
                String owner = req(a, "owner");
                JSONArray repos = getArray(token, "/users/" + enc(owner) + "/repos?per_page=50&sort=updated");
                return formatRepoList(repos);
            }

            case TOOL_GET_REPO: {
                JSONObject repo = getObject(token, "/repos/" + ownerRepo(a));
                return compact(repo, "full_name","description","language","stargazers_count",
                        "forks_count","open_issues_count","default_branch","html_url");
            }

            case TOOL_LIST_BRANCHES: {
                JSONArray branches = getArray(token, "/repos/" + ownerRepo(a) + "/branches?per_page=50");
                return formatNameList(branches, "name");
            }

            case TOOL_CREATE_REPO: {
                JSONObject body = new JSONObject();
                body.put("name", req(a, "name"));
                body.put("private", a.has("private") ? a.optBoolean("private", true) : true);
                body.put("auto_init", true);
                String description = a.optString("description", "");
                if (!description.isEmpty()) body.put("description", description);
                JSONObject result = postObject(token, "/user/repos", body);
                return "Repository created: " + result.optString("html_url");
            }

            case TOOL_CREATE_BRANCH: {
                String repoPath = "/repos/" + ownerRepo(a);
                String from = a.optString("from", "");
                if (from.isEmpty()) {
                    from = getObject(token, repoPath).optString("default_branch", "main");
                }
                String sourceSha;
                try {
                    sourceSha = getObject(token, repoPath + "/git/ref/heads/" + enc(from))
                            .getJSONObject("object").getString("sha");
                } catch (Exception ignored) {
                    sourceSha = from;
                }
                JSONObject body = new JSONObject();
                body.put("ref", "refs/heads/" + req(a, "branch"));
                body.put("sha", sourceSha);
                JSONObject result = postObject(token, repoPath + "/git/refs", body);
                return "Branch created: " + result.optString("ref");
            }

            case TOOL_GET_FILE: {
                String path = req(a, "path");
                String ref  = a.optString("ref", "");
                String url  = "/repos/" + ownerRepo(a) + "/contents/" + enc(path)
                        + (ref.isEmpty() ? "" : "?ref=" + enc(ref));
                JSONObject file = getObject(token, url);
                String encoding = file.optString("encoding", "");
                String content  = file.optString("content", "");
                if ("base64".equals(encoding)) {
                    content = new String(android.util.Base64.decode(
                            content.replaceAll("\\s", ""), android.util.Base64.DEFAULT));
                }
                return content;
            }

            case TOOL_LIST_FILES: {
                String path = a.optString("path", "");
                String ref  = a.optString("ref", "");
                String url  = "/repos/" + ownerRepo(a) + "/contents/" + enc(path)
                        + (ref.isEmpty() ? "" : "?ref=" + enc(ref));
                JSONArray entries = getArray(token, url);
                return formatDirListing(entries);
            }

            case TOOL_SEARCH_CODE: {
                String query = req(a, "query");
                JSONObject result = getObject(token, "/search/code?q=" + enc(query) + "&per_page=20");
                return formatSearchResults(result);
            }

            case TOOL_LIST_ISSUES: {
                String state = a.optString("state", "open");
                JSONArray issues = getArray(token, "/repos/" + ownerRepo(a)
                        + "/issues?state=" + enc(state) + "&per_page=30");
                return formatIssueList(issues);
            }

            case TOOL_CREATE_ISSUE: {
                JSONObject body = new JSONObject();
                body.put("title", req(a, "title"));
                String issueBody = a.optString("body", "");
                if (!issueBody.isEmpty()) body.put("body", issueBody);
                String labels = a.optString("labels", "");
                if (!labels.isEmpty()) {
                    JSONArray labelArray = new JSONArray();
                    for (String l : labels.split(",")) labelArray.put(l.trim());
                    body.put("labels", labelArray);
                }
                JSONObject result = postObject(token, "/repos/" + ownerRepo(a) + "/issues", body);
                return "Issue created: #" + result.optInt("number") + " – " + result.optString("html_url");
            }

            case TOOL_LIST_PRS: {
                String state = a.optString("state", "open");
                JSONArray prs = getArray(token, "/repos/" + ownerRepo(a)
                        + "/pulls?state=" + enc(state) + "&per_page=20");
                return formatPrList(prs);
            }

            case TOOL_CREATE_PR: {
                JSONObject body = new JSONObject();
                body.put("title", req(a, "title"));
                body.put("head",  req(a, "head"));
                body.put("base",  req(a, "base"));
                String prBody = a.optString("body", "");
                if (!prBody.isEmpty()) body.put("body", prBody);
                JSONObject result = postObject(token, "/repos/" + ownerRepo(a) + "/pulls", body);
                return "PR created: #" + result.optInt("number") + " – " + result.optString("html_url");
            }

            case TOOL_CREATE_COMMIT: {
                String path    = req(a, "path");
                String message = req(a, "message");
                String content = req(a, "content");
                String branch  = a.optString("branch", "");

                // Encode content as base64
                String encoded = android.util.Base64.encodeToString(
                        content.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        android.util.Base64.NO_WRAP);

                // Get existing file SHA if it exists (needed for updates)
                String apiPath = "/repos/" + ownerRepo(a) + "/contents/" + enc(path);
                String existingSha = "";
                try {
                    JSONObject existing = getObject(token, apiPath + (branch.isEmpty() ? "" : "?ref=" + enc(branch)));
                    existingSha = existing.optString("sha", "");
                } catch (Exception ignored) { /* file is new */ }

                JSONObject body = new JSONObject();
                body.put("message", message);
                body.put("content", encoded);
                if (!existingSha.isEmpty()) body.put("sha", existingSha);
                if (!branch.isEmpty())      body.put("branch", branch);

                JSONObject result = putObject(token, apiPath, body);
                JSONObject commit = result.optJSONObject("commit");
                String sha = commit == null ? "" : commit.optString("sha", "");
                return "Committed: " + (sha.isEmpty() ? "OK" : sha.substring(0, Math.min(12, sha.length())))
                        + " → " + path;
            }

            case TOOL_GET_COMMIT: {
                String sha = req(a, "sha");
                JSONObject commit = getObject(token, "/repos/" + ownerRepo(a) + "/commits/" + enc(sha));
                return formatCommit(commit);
            }

            case TOOL_LIST_COMMITS: {
                String branch = a.optString("branch", "");
                int limit      = parseInt(a.optString("limit", "20"), 20);
                String url     = "/repos/" + ownerRepo(a) + "/commits?per_page=" + limit
                        + (branch.isEmpty() ? "" : "&sha=" + enc(branch));
                JSONArray commits = getArray(token, url);
                return formatCommitList(commits);
            }

            default:
                return "GitHub MCP: unknown tool '" + toolName + "'.";
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // HTTP helpers
    // ─────────────────────────────────────────────────────────────────

    @NonNull
    private static JSONObject getObject(String token, String path) throws Exception {
        String body = get(token, path);
        return new JSONObject(body);
    }

    @NonNull
    private static JSONArray getArray(String token, String path) throws Exception {
        String body = get(token, path);
        // GitHub may return an object with 'items' for search endpoints
        if (body.trim().startsWith("{")) {
            JSONObject obj = new JSONObject(body);
            if (obj.has("items")) return obj.getJSONArray("items");
            // repos API sometimes wraps in object
        }
        return new JSONArray(body);
    }

    @NonNull
    private static JSONObject postObject(String token, String path, JSONObject payload) throws Exception {
        return new JSONObject(post(token, path, payload));
    }

    @NonNull
    private static JSONObject putObject(String token, String path, JSONObject payload) throws Exception {
        return new JSONObject(put(token, path, payload));
    }

    @NonNull
    private static String get(String token, String path) throws IOException {
        Request request = baseRequest(token, path).get().build();
        return executeAndRead(request);
    }

    @NonNull
    private static String post(String token, String path, JSONObject payload) throws IOException {
        Request request = baseRequest(token, path)
                .post(RequestBody.create(payload.toString(), JSON))
                .build();
        return executeAndRead(request);
    }

    @NonNull
    private static String put(String token, String path, JSONObject payload) throws IOException {
        Request request = baseRequest(token, path)
                .put(RequestBody.create(payload.toString(), JSON))
                .build();
        return executeAndRead(request);
    }

    @NonNull
    private static Request.Builder baseRequest(String token, String path) {
        return new Request.Builder()
                .url(GITHUB_API + path)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "Sketchware-IA-Android");
    }

    @NonNull
    private static String executeAndRead(Request request) throws IOException {
        try (Response response = CLIENT.newCall(request).execute()) {
            ResponseBody body = response.body();
            String bodyStr = body == null ? "" : body.string();
            if (!response.isSuccessful()) {
                String msg = bodyStr;
                try {
                    msg = new JSONObject(bodyStr).optString("message", bodyStr);
                } catch (Exception ignored) {}
                throw new IOException("HTTP " + response.code() + ": " + msg);
            }
            return bodyStr;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Formatting helpers
    // ─────────────────────────────────────────────────────────────────

    private static String formatRepoList(JSONArray arr) {
        if (arr.length() == 0) return "No repositories found.";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject r = arr.optJSONObject(i);
            if (r == null) continue;
            sb.append(r.optString("full_name")).append(" [")
              .append(r.optString("language", "?")).append("] ★")
              .append(r.optInt("stargazers_count")).append("\n");
        }
        return sb.toString().trim();
    }

    private static String formatNameList(JSONArray arr, String key) {
        if (arr.length() == 0) return "None found.";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null) sb.append(o.optString(key)).append("\n");
        }
        return sb.toString().trim();
    }

    private static String formatDirListing(JSONArray arr) {
        if (arr.length() == 0) return "Empty directory.";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject e = arr.optJSONObject(i);
            if (e == null) continue;
            String type = e.optString("type", "blob");
            String name = e.optString("name", "");
            sb.append("dir".equals(type) ? "[DIR] " : "      ").append(name).append("\n");
        }
        return sb.toString().trim();
    }

    private static String formatSearchResults(JSONObject result) {
        JSONArray items = result.optJSONArray("items");
        if (items == null || items.length() == 0) return "No results found.";
        int total = result.optInt("total_count", items.length());
        StringBuilder sb = new StringBuilder("Found ~" + total + " result(s):\n");
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            sb.append(item.optString("path")).append(" (")
              .append(item.optString("repository", "")).append(")\n");
        }
        return sb.toString().trim();
    }

    private static String formatIssueList(JSONArray arr) {
        if (arr.length() == 0) return "No issues found.";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject issue = arr.optJSONObject(i);
            if (issue == null || issue.has("pull_request")) continue; // skip PRs
            sb.append("#").append(issue.optInt("number")).append(" ")
              .append(issue.optString("title")).append("\n");
        }
        return sb.length() == 0 ? "No issues found." : sb.toString().trim();
    }

    private static String formatPrList(JSONArray arr) {
        if (arr.length() == 0) return "No pull requests found.";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject pr = arr.optJSONObject(i);
            if (pr == null) continue;
            sb.append("#").append(pr.optInt("number")).append(" ")
              .append(pr.optString("title")).append(" [")
              .append(pr.optString("head", "?")).append(" → ")
              .append(pr.optString("base", "?")).append("]\n");
        }
        return sb.toString().trim();
    }

    private static String formatCommit(JSONObject commit) {
        JSONObject commitObj = commit.optJSONObject("commit");
        if (commitObj == null) return commit.toString();
        JSONObject author = commitObj.optJSONObject("author");
        String name    = author == null ? "?" : author.optString("name", "?");
        String date    = author == null ? "?" : author.optString("date", "?");
        String message = commitObj.optString("message", "");
        String sha     = commit.optString("sha", "");
        return sha.substring(0, Math.min(12, sha.length())) + " by " + name + " on " + date + "\n" + message;
    }

    private static String formatCommitList(JSONArray arr) {
        if (arr.length() == 0) return "No commits found.";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject c = arr.optJSONObject(i);
            if (c == null) continue;
            String sha = c.optString("sha", "");
            JSONObject commit = c.optJSONObject("commit");
            String msg = commit == null ? "" : commit.optString("message", "");
            // first line only
            int nl = msg.indexOf('\n');
            if (nl > 0) msg = msg.substring(0, nl);
            sb.append(sha, 0, Math.min(8, sha.length())).append(" ")
              .append(msg).append("\n");
        }
        return sb.toString().trim();
    }

    private static String compact(JSONObject obj, String... keys) {
        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            Object val = obj.opt(key);
            if (val != null && !"null".equals(val.toString())) {
                sb.append(key).append(": ").append(val).append("\n");
            }
        }
        return sb.toString().trim();
    }

    // ─────────────────────────────────────────────────────────────────
    // Schema builder helpers
    // ─────────────────────────────────────────────────────────────────

    private static JSONObject tool(String name, String description, ParamBuilder params)
            throws JSONException {
        JSONObject function = new JSONObject()
                .put("name", name)
                .put("description", description)
                .put("parameters", params.build());
        return new JSONObject().put("type", "function").put("function", function);
    }

    private static ParamBuilder params() {
        return new ParamBuilder();
    }

    private static final class ParamBuilder {
        private final JSONObject properties = new JSONObject();
        private final JSONArray required = new JSONArray();

        ParamBuilder req(String name, String type, String description) throws JSONException {
            properties.put(name, new JSONObject().put("type", type).put("description", description));
            required.put(name);
            return this;
        }

        ParamBuilder opt(String name, String type, String description) throws JSONException {
            properties.put(name, new JSONObject().put("type", type).put("description", description));
            return this;
        }

        JSONObject build() throws JSONException {
            return new JSONObject()
                    .put("type", "object")
                    .put("properties", properties)
                    .put("required", required);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Misc
    // ─────────────────────────────────────────────────────────────────

    @NonNull
    private static String ownerRepo(JSONObject a) throws Exception {
        return enc(req(a, "owner")) + "/" + enc(req(a, "repo"));
    }

    @NonNull
    private static String req(JSONObject a, String key) throws Exception {
        String v = a.optString(key, "").trim();
        if (v.isEmpty()) throw new IllegalArgumentException("Required argument '" + key + "' is missing.");
        return v;
    }

    @NonNull
    private static String enc(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return s;
        }
    }

    private static int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return fallback; }
    }
}
