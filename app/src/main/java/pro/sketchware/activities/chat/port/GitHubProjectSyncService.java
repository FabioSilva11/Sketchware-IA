package pro.sketchware.activities.chat.port;

import android.content.SharedPreferences;
import android.util.Base64;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import pro.sketchware.util.ProjectPathResolver;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Atomic GitHub versioning for Android Studio projects.
 * Native Sketchware projects are deliberately not accepted by this service.
 */
public final class GitHubProjectSyncService {
    private static final String API = "https://api.github.com";
    private static final String PREF_BINDINGS = "github_android_studio_bindings_v1";
    private static final long MAX_FILE_BYTES = 5L * 1024L * 1024L;
    private static final long MAX_SNAPSHOT_BYTES = 40L * 1024L * 1024L;
    private static final int MAX_FILES = 4000;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();
    private static final Set<String> IGNORED_DIRS = new HashSet<>(Arrays.asList(
            ".git", ".gradle", ".idea", "build", "captures", ".cxx", ".externalNativeBuild",
            "node_modules", "dist", "out"
    ));
    private static final Set<String> IGNORED_FILES = new HashSet<>(Arrays.asList(
            "local.properties", ".env", "google-services.json", "gradle.properties"
    ));

    private GitHubProjectSyncService() {}

    public static final class Binding {
        public final String owner;
        public final String repo;
        public final String branch;
        public final String lastCommitSha;

        public Binding(String owner, String repo, String branch, String lastCommitSha) {
            this.owner = cleanName(owner);
            this.repo = cleanName(repo);
            this.branch = branch == null || branch.trim().isEmpty() ? "main" : branch.trim();
            this.lastCommitSha = lastCommitSha == null ? "" : lastCommitSha.trim();
        }

        public boolean isValid() {
            return !owner.isEmpty() && !repo.isEmpty() && !branch.isEmpty();
        }
    }

    public static final class SyncResult {
        public final String commitSha;
        public final int fileCount;
        public final long totalBytes;
        public final String url;

        SyncResult(String commitSha, int fileCount, long totalBytes, String url) {
            this.commitSha = commitSha;
            this.fileCount = fileCount;
            this.totalBytes = totalBytes;
            this.url = url;
        }
    }

    public static Binding loadBinding(SharedPreferences prefs, String scId) {
        JSONObject all = readBindings(prefs);
        JSONObject value = all.optJSONObject(scId == null ? "" : scId);
        if (value == null) return new Binding("", "", "main", "");
        return new Binding(value.optString("owner"), value.optString("repo"),
                value.optString("branch", "main"), value.optString("lastCommitSha"));
    }

    public static void saveBinding(SharedPreferences prefs, String scId, Binding binding) throws Exception {
        if (scId == null || scId.trim().isEmpty() || binding == null || !binding.isValid()) {
            throw new IllegalArgumentException("Vínculo GitHub inválido");
        }
        JSONObject all = readBindings(prefs);
        JSONObject value = new JSONObject();
        value.put("projectType", "android_studio");
        value.put("owner", binding.owner);
        value.put("repo", binding.repo);
        value.put("branch", binding.branch);
        value.put("lastCommitSha", binding.lastCommitSha);
        all.put(scId, value);
        prefs.edit().putString(PREF_BINDINGS, all.toString()).apply();
    }

    public static String authenticatedLogin(SharedPreferences prefs) throws Exception {
        return request(prefs, "GET", "/user", null).optString("login", "");
    }

    public static Binding createRepository(SharedPreferences prefs, String scId, String name,
                                           String branch, boolean isPrivate) throws Exception {
        JSONObject body = new JSONObject();
        body.put("name", cleanName(name));
        body.put("private", isPrivate);
        body.put("auto_init", true);
        JSONObject repo = request(prefs, "POST", "/user/repos", body);
        JSONObject owner = repo.optJSONObject("owner");
        Binding binding = new Binding(owner == null ? "" : owner.optString("login"),
                repo.optString("name"), repo.optString("default_branch", branch), "");
        saveBinding(prefs, scId, binding);
        return binding;
    }

    public static void verifyAndSaveBinding(SharedPreferences prefs, String scId, Binding binding) throws Exception {
        JSONObject repo = request(prefs, "GET", repoPath(binding), null);
        String branch = binding.branch;
        if (branch.isEmpty()) branch = repo.optString("default_branch", "main");
        request(prefs, "GET", repoPath(binding) + "/git/ref/heads/" + enc(branch), null);
        saveBinding(prefs, scId, new Binding(binding.owner, binding.repo, branch, binding.lastCommitSha));
    }

    @NonNull
    public static SyncResult pushSnapshot(SharedPreferences prefs, String scId, File projectRoot,
                                          Binding binding, String message) throws Exception {
        if (projectRoot == null || !projectRoot.isDirectory()) {
            throw new IllegalArgumentException("Pasta Android Studio não encontrada");
        }
        if (!ProjectPathResolver.isAndroidStudioProject(scId)) {
            throw new IllegalArgumentException("Versionamento GitHub disponível somente para projetos Android Studio");
        }
        if (binding == null || !binding.isValid()) throw new IllegalArgumentException("Projeto sem vínculo GitHub");
        List<SnapshotFile> files = new ArrayList<>();
        collect(projectRoot, projectRoot, files, new long[]{0L});
        if (files.isEmpty()) throw new IllegalStateException("Nenhum arquivo seguro para versionar");

        String base = repoPath(binding);
        JSONObject ref = request(prefs, "GET", base + "/git/ref/heads/" + enc(binding.branch), null);
        String parentSha = ref.getJSONObject("object").getString("sha");
        if (!binding.lastCommitSha.isEmpty() && !binding.lastCommitSha.equals(parentSha)) {
            throw new IllegalStateException("A branch mudou no GitHub desde o último push. " +
                    "Sincronize ou altere o vínculo antes de enviar para evitar sobrescrever mudanças remotas.");
        }

        JSONArray treeEntries = new JSONArray();
        for (SnapshotFile file : files) {
            JSONObject blobBody = new JSONObject();
            blobBody.put("content", Base64.encodeToString(readAll(file.file), Base64.NO_WRAP));
            blobBody.put("encoding", "base64");
            String blobSha = request(prefs, "POST", base + "/git/blobs", blobBody).getString("sha");
            JSONObject entry = new JSONObject();
            entry.put("path", file.path);
            entry.put("mode", "100644");
            entry.put("type", "blob");
            entry.put("sha", blobSha);
            treeEntries.put(entry);
        }

        JSONObject treeBody = new JSONObject();
        treeBody.put("tree", treeEntries);
        String treeSha = request(prefs, "POST", base + "/git/trees", treeBody).getString("sha");

        JSONObject commitBody = new JSONObject();
        commitBody.put("message", message == null || message.trim().isEmpty()
                ? "Update Android Studio project" : message.trim());
        commitBody.put("tree", treeSha);
        commitBody.put("parents", new JSONArray().put(parentSha));
        JSONObject commit = request(prefs, "POST", base + "/git/commits", commitBody);
        String commitSha = commit.getString("sha");

        JSONObject updateRef = new JSONObject();
        updateRef.put("sha", commitSha);
        updateRef.put("force", false);
        request(prefs, "PATCH", base + "/git/refs/heads/" + enc(binding.branch), updateRef);
        Binding updated = new Binding(binding.owner, binding.repo, binding.branch, commitSha);
        saveBinding(prefs, scId, updated);
        long bytes = 0L;
        for (SnapshotFile file : files) bytes += file.file.length();
        return new SyncResult(commitSha, files.size(), bytes,
                "https://github.com/" + binding.owner + "/" + binding.repo + "/commit/" + commitSha);
    }

    private static void collect(File root, File current, List<SnapshotFile> out, long[] total) throws Exception {
        File[] children = current.listFiles();
        if (children == null) return;
        Arrays.sort(children, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File child : children) {
            String name = child.getName();
            if (child.isDirectory()) {
                if (!IGNORED_DIRS.contains(name)) collect(root, child, out, total);
                continue;
            }
            String lower = name.toLowerCase();
            if (IGNORED_FILES.contains(name) || lower.endsWith(".jks") || lower.endsWith(".keystore")
                    || lower.endsWith(".apk") || lower.endsWith(".aab") || lower.endsWith(".class")
                    || lower.endsWith(".so") || lower.startsWith(".env.")) continue;
            if (child.length() > MAX_FILE_BYTES) throw new IOException("Arquivo excede 5 MB: " + name);
            total[0] += child.length();
            if (total[0] > MAX_SNAPSHOT_BYTES) throw new IOException("Snapshot excede 40 MB");
            if (out.size() >= MAX_FILES) throw new IOException("Snapshot excede " + MAX_FILES + " arquivos");
            String path = root.toPath().relativize(child.toPath()).toString().replace(File.separatorChar, '/');
            out.add(new SnapshotFile(path, child));
        }
    }

    private static byte[] readAll(File file) throws IOException {
        if (file.length() > Integer.MAX_VALUE) throw new IOException("Arquivo muito grande");
        byte[] data = new byte[(int) file.length()];
        try (FileInputStream input = new FileInputStream(file)) {
            int offset = 0;
            while (offset < data.length) {
                int read = input.read(data, offset, data.length - offset);
                if (read < 0) break;
                offset += read;
            }
            if (offset != data.length) throw new IOException("Falha ao ler " + file.getName());
        }
        return data;
    }

    private static JSONObject request(SharedPreferences prefs, String method, String path, JSONObject body) throws Exception {
        String token = prefs.getString(VoidPortSettings.PREF_GITHUB_TOKEN, "").trim();
        if (token.isEmpty()) throw new IllegalStateException("Configure o token em GitHub Settings");
        Request.Builder builder = new Request.Builder().url(API + path)
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + token)
                .header("X-GitHub-Api-Version", "2022-11-28");
        if (!"GET".equals(method)) {
            RequestBody requestBody = RequestBody.create(body == null ? "{}" : body.toString(), JSON);
            builder.method(method, requestBody);
        }
        try (Response response = CLIENT.newCall(builder.build()).execute()) {
            ResponseBody responseBody = response.body();
            String text = responseBody == null ? "" : responseBody.string();
            if (!response.isSuccessful()) {
                String message;
                try { message = new JSONObject(text).optString("message", text); }
                catch (Exception ignored) { message = text; }
                throw new IOException("GitHub " + response.code() + ": " + message);
            }
            return text.isEmpty() ? new JSONObject() : new JSONObject(text);
        }
    }

    private static JSONObject readBindings(SharedPreferences prefs) {
        try { return new JSONObject(prefs.getString(PREF_BINDINGS, "{}")); }
        catch (Exception ignored) { return new JSONObject(); }
    }

    private static String repoPath(Binding binding) {
        return "/repos/" + enc(binding.owner) + "/" + enc(binding.repo);
    }

    private static String cleanName(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("[^A-Za-z0-9_.-]", "-");
    }

    private static String enc(String value) {
        try { return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20"); }
        catch (Exception ignored) { return value; }
    }

    private static final class SnapshotFile {
        final String path;
        final File file;
        SnapshotFile(String path, File file) { this.path = path; this.file = file; }
    }
}
