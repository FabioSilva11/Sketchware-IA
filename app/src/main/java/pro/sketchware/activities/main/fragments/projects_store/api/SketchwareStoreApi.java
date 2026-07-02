package pro.sketchware.activities.main.fragments.projects_store.api;

import android.net.Uri;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.function.Consumer;

import pro.sketchware.utility.Network;

public class SketchwareStoreApi {

    public static final String SITE_URL = "https://sketchware-nexus.lovable.app";
    public static final String BASE_URL = SITE_URL + "/api/public";
    private static final String TAG = "SketchwareStoreApi";
    private static final int PAGE_SIZE = 20;

    private final Network network = new Network();
    private final Gson gson = new Gson();

    public void getEditorsChoiceProjects(int pageNumber, Consumer<ProjectModel> consumer) {
        getProjects("rating", true, pageNumber, projectModel -> {
            if (projectModel != null && projectModel.getProjects() != null && !projectModel.getProjects().isEmpty()) {
                consumer.accept(projectModel);
            } else {
                getProjects("rating", false, pageNumber, consumer);
            }
        });
    }

    public void getMostDownloadedProjects(int pageNumber, Consumer<ProjectModel> consumer) {
        getProjects("downloads", false, pageNumber, consumer);
    }

    public void getRecentProjects(int pageNumber, Consumer<ProjectModel> consumer) {
        getProjects("newest", false, pageNumber, consumer);
    }

    public void getProjectDetails(String slug, Consumer<ProjectModel.Project> consumer) {
        if (isEmpty(slug)) {
            consumer.accept(null);
            return;
        }

        network.get(BASE_URL + "/publications/" + Uri.encode(slug), response -> {
            if (isEmpty(response)) {
                consumer.accept(null);
                return;
            }

            try {
                PublicationDetailResponse detailResponse = gson.fromJson(response, PublicationDetailResponse.class);
                consumer.accept(detailResponse == null ? null : detailResponse.data);
            } catch (JsonSyntaxException e) {
                Log.e(TAG, "Failed to parse publication details", e);
                consumer.accept(null);
            }
        });
    }

    public void getComments(String slug, Consumer<List<ProjectModel.Comment>> consumer) {
        if (isEmpty(slug)) {
            consumer.accept(null);
            return;
        }

        Uri uri = Uri.parse(BASE_URL + "/publications/" + Uri.encode(slug) + "/comments")
                .buildUpon()
                .appendQueryParameter("limit", "50")
                .appendQueryParameter("offset", "0")
                .build();

        network.get(uri.toString(), response -> {
            if (isEmpty(response)) {
                consumer.accept(null);
                return;
            }

            try {
                CommentsResponse commentsResponse = gson.fromJson(response, CommentsResponse.class);
                consumer.accept(commentsResponse == null ? null : commentsResponse.data);
            } catch (JsonSyntaxException e) {
                Log.e(TAG, "Failed to parse publication comments", e);
                consumer.accept(null);
            }
        });
    }

    public void getDownloadUrl(String slug, Consumer<String> consumer) {
        if (isEmpty(slug)) {
            consumer.accept(null);
            return;
        }

        network.get(BASE_URL + "/publications/" + Uri.encode(slug) + "/download", response -> {
            if (isEmpty(response)) {
                consumer.accept(null);
                return;
            }

            try {
                JsonElement root = gson.fromJson(response, JsonElement.class);
                consumer.accept(extractUrl(root));
            } catch (JsonSyntaxException e) {
                Log.e(TAG, "Failed to parse download response", e);
                consumer.accept(null);
            }
        });
    }

    public String getPublicationApiUrl(String slug) {
        return BASE_URL + "/publications/" + Uri.encode(slug);
    }

    private void getProjects(String sort, boolean featured, int pageNumber, Consumer<ProjectModel> consumer) {
        int page = Math.max(1, pageNumber);
        Uri.Builder builder = Uri.parse(BASE_URL + "/publications")
                .buildUpon()
                .appendQueryParameter("kind", "all")
                .appendQueryParameter("sort", sort)
                .appendQueryParameter("limit", String.valueOf(PAGE_SIZE))
                .appendQueryParameter("offset", String.valueOf((page - 1) * PAGE_SIZE));

        if (featured) {
            builder.appendQueryParameter("featured", "true");
        }

        network.get(builder.build().toString(), response -> {
            if (isEmpty(response)) {
                consumer.accept(null);
                return;
            }

            try {
                PublicationsResponse publicationsResponse = gson.fromJson(response, PublicationsResponse.class);
                ProjectModel projectModel = new ProjectModel();
                projectModel.setStatus("success");
                projectModel.setProjects(publicationsResponse.data);
                projectModel.setTotalPages(calculateTotalPages(publicationsResponse.total, publicationsResponse.limit));
                consumer.accept(projectModel);
            } catch (JsonSyntaxException e) {
                Log.e(TAG, "Failed to parse publications", e);
                consumer.accept(null);
            }
        });
    }

    private String calculateTotalPages(int total, int limit) {
        if (total <= 0 || limit <= 0) {
            return "0";
        }
        return String.valueOf((int) Math.ceil(total / (double) limit));
    }

    private String extractUrl(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }

        if (element.isJsonPrimitive()) {
            String value = element.getAsString();
            return value.startsWith("http") ? value : null;
        }

        if (!element.isJsonObject()) {
            return null;
        }

        JsonObject object = element.getAsJsonObject();
        String direct = firstString(object, "url", "download_url", "downloadUrl", "signed_url", "signedUrl", "file_url", "fileUrl");
        if (!isEmpty(direct)) {
            return direct;
        }

        String fromData = extractUrl(object.get("data"));
        if (!isEmpty(fromData)) {
            return fromData;
        }

        String fromFile = extractUrl(object.get("file"));
        if (!isEmpty(fromFile)) {
            return fromFile;
        }

        return extractUrl(object.get("version"));
    }

    private String firstString(JsonObject object, String... names) {
        for (String name : names) {
            JsonElement value = object.get(name);
            if (value != null && value.isJsonPrimitive()) {
                String text = value.getAsString();
                if (!isEmpty(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static class PublicationsResponse {
        @SerializedName("data")
        List<ProjectModel.Project> data;
        @SerializedName("total")
        int total;
        @SerializedName("limit")
        int limit;
    }

    private static class PublicationDetailResponse {
        @SerializedName("data")
        ProjectModel.Project data;
    }

    private static class CommentsResponse {
        @SerializedName("data")
        List<ProjectModel.Comment> data;
    }
}
