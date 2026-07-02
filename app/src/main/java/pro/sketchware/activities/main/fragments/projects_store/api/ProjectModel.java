package pro.sketchware.activities.main.fragments.projects_store.api;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

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

        public String getDemoLink() {
            return safe(website);
        }

        public String getVideoUrl() {
            return "";
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
            String value = firstNonEmpty(publishedAt, updatedAt);
            if (value.length() >= 10) {
                return value.substring(0, 10);
            }
            return value;
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
        @SerializedName("file_url")
        @Expose
        private String fileUrl;

        public String getFileUrl() {
            return safe(fileUrl);
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

    private static String safe(String value) {
        return value == null ? "" : value.trim();
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
