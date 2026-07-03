package pro.sketchware.activities.main.fragments.projects_store.api;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ProjectModel {

    @SerializedName("status")
    @Expose
    private String status;
    @SerializedName("total_pages")
    @Expose
    private String totalPages;
    @SerializedName("projects")
    @Expose
    private List<Project> projects;
    private int total;
    private int limit;
    private int offset;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(String totalPages) {
        this.totalPages = totalPages;
    }

    public List<Project> getProjects() {
        return projects;
    }

    public void setProjects(List<Project> projects) {
        this.projects = projects;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public static class StoreStats {
        @SerializedName("apks")
        @Expose
        private int apks;
        @SerializedName("swbs")
        @Expose
        private int swbs;
        @SerializedName("users")
        @Expose
        private int users;
        @SerializedName("downloads")
        @Expose
        private int downloads;

        public int getApks() {
            return apks;
        }

        public int getSwbs() {
            return swbs;
        }

        public int getUsers() {
            return users;
        }

        public int getDownloads() {
            return downloads;
        }

        public int getPublications() {
            return apks + swbs;
        }
    }

    public static class Comment {
        @SerializedName("id")
        @Expose
        private String id;
        @SerializedName("body")
        @Expose
        private String body;
        @SerializedName("created_at")
        @Expose
        private String createdAt;
        @SerializedName("is_developer")
        @Expose
        private boolean developer;
        @SerializedName("user")
        @Expose
        private User user;

        public String getId() {
            return id;
        }

        public String getBody() {
            return safe(body);
        }

        public String getCreatedAt() {
            return safe(createdAt);
        }

        public boolean isDeveloper() {
            return developer;
        }

        public String getUserName() {
            if (user == null) {
                return "";
            }
            return firstNonEmpty(user.displayName, user.username);
        }

        public String getUserAvatar() {
            return user == null ? "" : safe(user.avatarUrl);
        }
    }

    public static class Review {
        @SerializedName("id")
        @Expose
        private String id;
        @SerializedName("rating")
        @Expose
        private int rating;
        @SerializedName("body")
        @Expose
        private String body;
        @SerializedName("created_at")
        @Expose
        private String createdAt;
        @SerializedName("user")
        @Expose
        private User user;

        public String getId() {
            return id;
        }

        public float getRatingValue() {
            return Math.max(0, Math.min(5, rating));
        }

        public String getBody() {
            return safe(body);
        }

        public String getCreatedAt() {
            return formatDate(createdAt);
        }

        public String getUserName() {
            if (user == null) {
                return "";
            }
            return firstNonEmpty(user.displayName, user.username);
        }

        public String getUserAvatar() {
            return user == null ? "" : safe(user.avatarUrl);
        }
    }

    public static class Project {

        @SerializedName("id")
        @Expose
        private String id;
        @SerializedName("slug")
        @Expose
        private String slug;
        @SerializedName("name")
        @Expose
        private String name;
        @SerializedName("short_description")
        @Expose
        private String shortDescription;
        @SerializedName("description")
        @Expose
        private String description;
        @SerializedName("icon_url")
        @Expose
        private String iconUrl;
        @SerializedName("banner_url")
        @Expose
        private String bannerUrl;
        @SerializedName("current_version")
        @Expose
        private String currentVersion;
        @SerializedName("file_size")
        @Expose
        private long fileSize;
        @SerializedName("changelog")
        @Expose
        private String changelog;
        @SerializedName("language")
        @Expose
        private String language;
        @SerializedName("tags")
        @Expose
        private List<String> tags;
        @SerializedName("license")
        @Expose
        private String license;
        @SerializedName("privacy_policy")
        @Expose
        private String privacyPolicy;
        @SerializedName("sketchware_compat")
        @Expose
        private String sketchwareCompat;
        @SerializedName("dependencies")
        @Expose
        private JsonElement dependencies;
        @SerializedName("libraries")
        @Expose
        private JsonElement libraries;
        @SerializedName("permissions")
        @Expose
        private JsonElement permissions;
        @SerializedName("kind")
        @Expose
        private String kind;
        @SerializedName("developer_name")
        @Expose
        private String developerName;
        @SerializedName("website")
        @Expose
        private String website;
        @SerializedName("github")
        @Expose
        private String github;
        @SerializedName("video_url")
        @Expose
        private String videoUrl;
        @SerializedName("is_featured")
        @Expose
        private boolean featured;
        @SerializedName("is_open_source")
        @Expose
        private boolean openSource;
        @SerializedName("is_free")
        @Expose
        private boolean free;
        @SerializedName("downloads_count")
        @Expose
        private int downloadsCount;
        @SerializedName("likes_count")
        @Expose
        private int likesCount;
        @SerializedName("reviews_count")
        @Expose
        private int reviewsCount;
        @SerializedName("rating_avg")
        @Expose
        private double ratingAverage;
        @SerializedName("published_at")
        @Expose
        private String publishedAt;
        @SerializedName("updated_at")
        @Expose
        private String updatedAt;
        @SerializedName("category")
        @Expose
        private Category category;
        @SerializedName("developer")
        @Expose
        private User developer;
        @SerializedName("screenshots")
        @Expose
        private List<Screenshot> screenshots;
        @SerializedName("versions")
        @Expose
        private List<Version> versions;

        public String getId() {
            return safe(id);
        }

        public String getSlug() {
            return firstNonEmpty(slug, id);
        }

        public String getTitle() {
            return safe(name);
        }

        public String getDescription() {
            return firstNonEmpty(description, shortDescription);
        }

        public String getShortDescription() {
            return firstNonEmpty(shortDescription, description);
        }

        public String getWhatsnew() {
            return safe(changelog);
        }

        public String getCategory() {
            if (category != null) {
                String categoryName = firstNonEmpty(category.namePt, category.nameEn, category.slug);
                if (!categoryName.isEmpty()) {
                    return categoryName;
                }
            }
            return safe(kind).toUpperCase(Locale.US);
        }

        public String getProjectType() {
            return safe(kind);
        }

        public String getTypeLabel() {
            String type = safe(kind).toUpperCase(Locale.US);
            return type.isEmpty() ? "APP" : type;
        }

        public String getDemoLink() {
            return safe(website);
        }

        public String getVideoUrl() {
            return safe(videoUrl);
        }

        public String getIcon() {
            return firstNonEmpty(iconUrl, bannerUrl);
        }

        public String getBannerUrl() {
            return safe(bannerUrl);
        }

        public String getScreenshot1() {
            return getScreenshot(0);
        }

        public String getScreenshot2() {
            return getScreenshot(1);
        }

        public String getScreenshot3() {
            return getScreenshot(2);
        }

        public String getScreenshot4() {
            return getScreenshot(3);
        }

        public String getScreenshot5() {
            return getScreenshot(4);
        }

        public ArrayList<String> getScreenshotUrls() {
            ArrayList<String> urls = new ArrayList<>();
            if (screenshots != null) {
                for (Screenshot screenshot : screenshots) {
                    if (screenshot == null) {
                        continue;
                    }
                    String url = screenshot.getUrl();
                    if (!url.isEmpty()) {
                        urls.add(url);
                    }
                }
            }
            if (urls.isEmpty() && !safe(bannerUrl).isEmpty()) {
                urls.add(bannerUrl);
            }
            return urls;
        }

        public String getProjectSize() {
            return formatFileSize(fileSize);
        }

        public String getLikes() {
            return String.valueOf(likesCount);
        }

        public String getRating() {
            if (ratingAverage <= 0) {
                return "0.0";
            }
            return String.format(Locale.US, "%.1f", ratingAverage);
        }

        public float getRatingValue() {
            return (float) Math.max(0d, Math.min(5d, ratingAverage));
        }

        public String getRatingLabel() {
            return "Rating " + getRating();
        }

        public String getReviews() {
            return String.valueOf(reviewsCount);
        }

        public String getComments() {
            return String.valueOf(reviewsCount);
        }

        public String getDownloads() {
            return String.valueOf(downloadsCount);
        }

        public String getUid() {
            return "";
        }

        public String getTimestamp() {
            return safe(updatedAt);
        }

        public String getPublishedTimestamp() {
            return safe(publishedAt);
        }

        public String getPublishedDate() {
            return formatDate(firstNonEmpty(publishedAt, updatedAt));
        }

        public String getUpdatedDate() {
            return formatDate(firstNonEmpty(updatedAt, publishedAt));
        }

        public String getIsVerified() {
            return openSource ? "1" : "0";
        }

        public String getIsEditorChoice() {
            return featured ? "1" : "0";
        }

        public String getUserName() {
            if (developer != null) {
                String name = firstNonEmpty(developer.displayName, developer.username);
                if (!name.isEmpty()) {
                    return name;
                }
            }
            return safe(developerName);
        }

        public String getDeveloperUsername() {
            if (developer != null) {
                String username = safe(developer.username);
                if (!username.isEmpty()) {
                    return username;
                }
            }
            return safe(developerName);
        }

        public String getUserProfilePic() {
            return developer == null ? "" : safe(developer.avatarUrl);
        }

        public String getWebsite() {
            return safe(website);
        }

        public String getGithub() {
            return safe(github);
        }

        public String getCurrentVersion() {
            return safe(currentVersion);
        }

        public String getLanguage() {
            return safe(language);
        }

        public String getLicense() {
            return safe(license);
        }

        public String getPrivacyPolicy() {
            return safe(privacyPolicy);
        }

        public String getSketchwareCompat() {
            return safe(sketchwareCompat);
        }

        public String getTagsLabel() {
            if (tags == null || tags.isEmpty()) {
                return "";
            }
            StringBuilder builder = new StringBuilder();
            int count = Math.min(3, tags.size());
            for (int i = 0; i < count; i++) {
                String tag = safe(tags.get(i));
                if (tag.isEmpty()) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append(" / ");
                }
                builder.append(tag);
            }
            return builder.toString();
        }

        public String getDependenciesSummary() {
            return jsonSummary(dependencies);
        }

        public String getLibrariesSummary() {
            return jsonSummary(libraries);
        }

        public String getPermissionsSummary() {
            return jsonSummary(permissions);
        }

        public boolean isFree() {
            return free;
        }

        public boolean isOpenSource() {
            return openSource;
        }

        public boolean isFeatured() {
            return featured;
        }

        public String getPriceLabel() {
            return free ? "Free" : "Paid";
        }

        public boolean hasComments() {
            return reviewsCount > 0;
        }

        public String getFirstVersionFileUrl() {
            if (versions == null) {
                return "";
            }
            for (Version version : versions) {
                if (version == null) {
                    continue;
                }
                String url = version.getFileUrl();
                if (!url.isEmpty()) {
                    return url;
                }
            }
            return "";
        }

        public List<Version> getVersions() {
            return versions == null ? new ArrayList<>() : versions;
        }

        private String getScreenshot(int index) {
            ArrayList<String> urls = getScreenshotUrls();
            return index >= 0 && index < urls.size() ? urls.get(index) : "";
        }
    }

    public static class Category {
        @SerializedName("slug")
        @Expose
        private String slug;
        @SerializedName("name_pt")
        @Expose
        private String namePt;
        @SerializedName("name_en")
        @Expose
        private String nameEn;

        public String getSlug() {
            return safe(slug);
        }

        public String getName() {
            return firstNonEmpty(namePt, nameEn, slug);
        }
    }

    public static class Screenshot {
        @SerializedName("url")
        @Expose
        private String url;
        @SerializedName("image_url")
        @Expose
        private String imageUrl;
        @SerializedName("file_url")
        @Expose
        private String fileUrl;
        @SerializedName("public_url")
        @Expose
        private String publicUrl;

        public String getUrl() {
            return firstNonEmpty(url, imageUrl, fileUrl, publicUrl);
        }
    }

    public static class Version {
        @SerializedName("id")
        @Expose
        private String id;
        @SerializedName("version")
        @Expose
        private String version;
        @SerializedName("changelog")
        @Expose
        private String changelog;
        @SerializedName("file_url")
        @Expose
        private String fileUrl;
        @SerializedName("file_size")
        @Expose
        private long fileSize;
        @SerializedName("created_at")
        @Expose
        private String createdAt;

        public String getVersionName() {
            return firstNonEmpty(version, id);
        }

        public String getChangelog() {
            return safe(changelog);
        }

        public String getFileUrl() {
            return safe(fileUrl);
        }

        public String getFileSize() {
            return formatFileSize(fileSize);
        }

        public String getCreatedDate() {
            return formatDate(createdAt);
        }
    }

    public static class User {
        @SerializedName("username")
        @Expose
        private String username;
        @SerializedName("display_name")
        @Expose
        private String displayName;
        @SerializedName("avatar_url")
        @Expose
        private String avatarUrl;
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (!safe(value).isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static String jsonSummary(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (element.isJsonPrimitive()) {
            return safe(element.getAsString());
        }
        if (element.isJsonArray()) {
            return jsonArraySummary(element.getAsJsonArray());
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            String namedValue = firstJsonString(object, "name", "title", "label", "id");
            if (!namedValue.isEmpty()) {
                return namedValue;
            }
            StringBuilder builder = new StringBuilder();
            for (String key : object.keySet()) {
                JsonElement value = object.get(key);
                if (value == null || value.isJsonNull()) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append(" / ");
                }
                builder.append(key);
                if (builder.length() > 48) {
                    break;
                }
            }
            return builder.toString();
        }
        return "";
    }

    private static String jsonArraySummary(JsonArray array) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < array.size() && i < 3; i++) {
            String item = jsonSummary(array.get(i));
            if (item.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(" / ");
            }
            builder.append(item);
        }
        return builder.toString();
    }

    private static String firstJsonString(JsonObject object, String... keys) {
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value != null && value.isJsonPrimitive()) {
                String text = safe(value.getAsString());
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return "";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String formatDate(String value) {
        String date = safe(value);
        if (date.length() < 10) {
            return date;
        }

        String isoDate = date.substring(0, 10);
        try {
            int year = Integer.parseInt(isoDate.substring(0, 4));
            int month = Integer.parseInt(isoDate.substring(5, 7));
            int day = Integer.parseInt(isoDate.substring(8, 10));
            String[] months = {
                    "jan.", "fev.", "mar.", "abr.", "mai.", "jun.",
                    "jul.", "ago.", "set.", "out.", "nov.", "dez."
            };
            if (month >= 1 && month <= months.length) {
                return String.format(Locale.US, "%02d de %s de %04d", day, months[month - 1], year);
            }
        } catch (RuntimeException ignored) {
        }
        return isoDate;
    }

    private static String formatFileSize(long bytes) {
        if (bytes <= 0) {
            return "";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes / 1024d;
        if (value < 1024d) {
            return String.format(Locale.US, "%.1f KB", value);
        }
        value /= 1024d;
        if (value < 1024d) {
            return String.format(Locale.US, "%.1f MB", value);
        }
        return String.format(Locale.US, "%.1f GB", value / 1024d);
    }
}
