package pro.sketchware.store.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What this device knows about the store: the newest verified bundle of every user it has heard
 * of, kept on disk under {@code root/<user id>/}, and the metrics their events add up to.
 */
public final class Catalog {

    public static final String SORT_POPULAR = "popular";
    public static final String SORT_NEWEST = "newest";
    public static final String SORT_LIKES = "likes";
    public static final String SORT_DOWNLOADS = "downloads";

    /** Events one user can contribute per day; more than that is treated as spam. */
    private static final int MAX_COUNTED_EVENTS_PER_DAY = 200;
    private static final long DAY = 24L * 60 * 60 * 1000;

    private final File root;
    private final Map<String, Entry> entries = new HashMap<>();
    private final Map<String, Long> peerCounts = new HashMap<>();
    private Map<String, Metrics> metrics;

    public Catalog(File root) {
        this.root = root;
    }

    public static final class Entry {
        public final Bundle bundle;
        public final String infohash;
        public final long fetchedAt;

        Entry(Bundle bundle, String infohash, long fetchedAt) {
            this.bundle = bundle;
            this.infohash = infohash;
            this.fetchedAt = fetchedAt;
        }
    }

    public synchronized void load(long now) {
        entries.clear();
        metrics = null;
        File[] folders = root.listFiles();
        if (folders == null) {
            return;
        }
        for (File folder : folders) {
            if (!folder.isDirectory() || !Codec.isHex(folder.getName(), 64)) {
                continue;
            }
            try {
                Bundle bundle = Bundle.read(folder, folder.getName(), now);
                String infohash = readText(new File(folder, "infohash"));
                entries.put(bundle.author(), new Entry(bundle, infohash, folder.lastModified()));
            } catch (IOException | RuntimeException e) {
                Bundle.deleteRecursively(folder);
            }
        }
    }

    /**
     * Keeps a verified bundle if it's newer than what's known about its author. The files are
     * copied, so the downloaded copy can keep being shared (or be evicted) independently.
     *
     * @return whether the catalog changed
     */
    public synchronized boolean put(Bundle bundle, String infohash, long now) throws IOException {
        Entry current = entries.get(bundle.author());
        if (current != null && current.bundle.sequence() >= bundle.sequence()) {
            return false; // Stale or replayed
        }
        File target = new File(root, bundle.author());
        File staging = new File(root, bundle.author() + ".new");
        Bundle.deleteRecursively(staging);
        copyFolder(bundle.folder, staging);
        Files.write(new File(staging, "infohash").toPath(), (infohash == null ? "" : infohash).getBytes());
        Bundle.deleteRecursively(target);
        if (!staging.renameTo(target)) {
            throw new IOException("Couldn't store bundle of " + bundle.author());
        }
        Bundle stored = Bundle.read(target, bundle.author(), now);
        entries.put(stored.author(), new Entry(stored, infohash, now));
        metrics = null;
        return true;
    }

    public synchronized Entry entry(String author) {
        return entries.get(author);
    }

    public synchronized long sequence(String author) {
        Entry entry = entries.get(author);
        return entry == null ? -1 : entry.bundle.sequence();
    }

    public synchronized String infohash(String author) {
        Entry entry = entries.get(author);
        return entry == null ? null : entry.infohash;
    }

    /** Every user this catalog knows, directly or because a known user listed them. */
    public synchronized Set<String> knownUsers() {
        Set<String> users = new LinkedHashSet<>(entries.keySet());
        for (Entry entry : entries.values()) {
            users.addAll(entry.bundle.known);
        }
        return users;
    }

    public synchronized int userCount() {
        return entries.size();
    }

    public synchronized void setPeerCount(String infohash, long peers) {
        peerCounts.put(infohash, peers);
        metrics = null;
    }

    // Queries

    public synchronized Profile profile(String author) {
        Entry entry = entries.get(author);
        return entry == null ? null : new Profile(entry.bundle);
    }

    public synchronized Listing listing(String author, String projectId) {
        Entry entry = entries.get(author);
        if (entry == null) {
            return null;
        }
        for (JsonObject project : entry.bundle.projects) {
            if (projectId.equals(Codec.string(project, "id", ""))) {
                return new Listing(entry.bundle, project, metrics().get(key(author, projectId)));
            }
        }
        return null;
    }

    public synchronized List<Listing> listings(String query, String kind, String sort, String onlyAuthor) {
        Map<String, Metrics> allMetrics = metrics();
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Listing> result = new ArrayList<>();
        for (Entry entry : entries.values()) {
            if (onlyAuthor != null && !onlyAuthor.equals(entry.bundle.author())) {
                continue;
            }
            for (JsonObject project : entry.bundle.projects) {
                Listing listing = new Listing(entry.bundle, project,
                        allMetrics.get(key(entry.bundle.author(), Codec.string(project, "id", ""))));
                if (listing.removed || (kind != null && !kind.equals(listing.kind))) {
                    continue;
                }
                if (!needle.isEmpty() && !listing.matches(needle)) {
                    continue;
                }
                result.add(listing);
            }
        }
        Comparator<Listing> order;
        if (SORT_NEWEST.equals(sort)) {
            order = Comparator.comparingLong((Listing l) -> l.updatedAt).reversed();
        } else if (SORT_LIKES.equals(sort)) {
            order = Comparator.comparingLong((Listing l) -> l.metrics.likes).reversed();
        } else if (SORT_DOWNLOADS.equals(sort)) {
            order = Comparator.comparingLong((Listing l) -> l.metrics.downloads).reversed();
        } else {
            order = Comparator.comparingDouble((Listing l) -> l.metrics.score).reversed();
        }
        result.sort(order.thenComparing(l -> l.title.toLowerCase(Locale.ROOT)));
        return result;
    }

    // Metrics

    private static String key(String author, String projectId) {
        return author + "/" + projectId;
    }

    private Map<String, Metrics> metrics() {
        if (metrics != null) {
            return metrics;
        }
        Map<String, Metrics> result = new HashMap<>();
        Map<String, String> versionInfohash = new HashMap<>();
        for (Entry entry : entries.values()) {
            for (JsonObject project : entry.bundle.projects) {
                String id = Codec.string(project, "id", "");
                Metrics m = new Metrics();
                JsonElement versions = project.get("versions");
                m.versions = versions != null && versions.isJsonArray() ? versions.getAsJsonArray().size() : 0;
                for (JsonElement version : versions != null && versions.isJsonArray()
                        ? versions.getAsJsonArray() : new com.google.gson.JsonArray()) {
                    String ih = Codec.string(version.getAsJsonObject(), "ih", "");
                    Long peers = peerCounts.get(ih);
                    if (peers != null) {
                        m.peers += peers;
                    }
                    versionInfohash.put(ih, id);
                }
                result.put(key(entry.bundle.author(), id), m);
            }
        }
        // Latest like/favorite state per user and project; distinct views per day and downloads per version
        Map<String, Long> likeState = new HashMap<>();
        Map<String, Long> favoriteState = new HashMap<>();
        Set<String> views = new HashSet<>();
        Set<String> downloads = new HashSet<>();
        for (Entry entry : entries.values()) {
            String user = entry.bundle.author();
            Map<Long, Integer> perDay = new HashMap<>();
            List<JsonObject> events = new ArrayList<>(entry.bundle.events);
            events.sort(Comparator.comparingLong(Records::timestamp));
            for (JsonObject event : events) {
                String projectAuthor = Codec.string(event, "pa", "");
                String projectKey = key(projectAuthor, Codec.string(event, "p", ""));
                if (!result.containsKey(projectKey) || user.equals(projectAuthor)) {
                    continue; // Unknown project, or the publisher interacting with their own
                }
                long day = Records.timestamp(event) / DAY;
                int count = perDay.merge(day, 1, Integer::sum);
                if (count > MAX_COUNTED_EVENTS_PER_DAY) {
                    continue;
                }
                String userProject = user + "|" + projectKey;
                long ts = Records.timestamp(event);
                switch (Codec.string(event, "k", "")) {
                    case Records.EVENT_LIKE:
                        likeState.put(userProject, ts);
                        break;
                    case Records.EVENT_UNLIKE:
                        likeState.put(userProject, -ts);
                        break;
                    case Records.EVENT_FAVORITE:
                        favoriteState.put(userProject, ts);
                        break;
                    case Records.EVENT_UNFAVORITE:
                        favoriteState.put(userProject, -ts);
                        break;
                    case Records.EVENT_VIEW:
                        if (views.add(userProject + "|" + day)) {
                            result.get(projectKey).views++;
                        }
                        break;
                    case Records.EVENT_DOWNLOAD:
                        if (downloads.add(userProject + "|" + Codec.number(event, "v", 0))) {
                            result.get(projectKey).downloads++;
                        }
                        break;
                    default:
                        break;
                }
            }
        }
        for (Map.Entry<String, Long> like : likeState.entrySet()) {
            if (like.getValue() > 0) {
                result.get(like.getKey().substring(like.getKey().indexOf('|') + 1)).likes++;
            }
        }
        for (Map.Entry<String, Long> favorite : favoriteState.entrySet()) {
            if (favorite.getValue() > 0) {
                result.get(favorite.getKey().substring(favorite.getKey().indexOf('|') + 1)).favorites++;
            }
        }
        for (Metrics m : result.values()) {
            m.score = m.likes * 4 + m.favorites * 3 + m.downloads * 2 + m.peers + m.views * 0.25 + m.versions * 0.5;
        }
        metrics = result;
        return result;
    }

    /** Whether {@code user}'s latest like (or favorite, for {@code favorite}) of a project is on. */
    public synchronized boolean hasLiked(String user, String projectAuthor, String projectId, boolean favorite) {
        Entry entry = entries.get(user);
        if (entry == null) {
            return false;
        }
        long latest = 0;
        boolean on = false;
        for (JsonObject event : entry.bundle.events) {
            if (!projectId.equals(Codec.string(event, "p", "")) || !projectAuthor.equals(Codec.string(event, "pa", ""))) {
                continue;
            }
            String kind = Codec.string(event, "k", "");
            boolean relevant = favorite
                    ? Records.EVENT_FAVORITE.equals(kind) || Records.EVENT_UNFAVORITE.equals(kind)
                    : Records.EVENT_LIKE.equals(kind) || Records.EVENT_UNLIKE.equals(kind);
            if (relevant && Records.timestamp(event) >= latest) {
                latest = Records.timestamp(event);
                on = Records.EVENT_LIKE.equals(kind) || Records.EVENT_FAVORITE.equals(kind);
            }
        }
        return on;
    }

    // Models

    public static final class Metrics {
        public long likes;
        public long downloads;
        public long views;
        public long favorites;
        public long versions;
        public long peers;
        public double score;
    }

    public static final class Profile {
        public final String author;
        public final String name;
        public final String bio;
        public final File avatar;
        public final File banner;
        public final File logo;
        public final int projectCount;

        Profile(Bundle bundle) {
            JsonObject profile = bundle.profile;
            author = bundle.author();
            name = Codec.string(profile, "name", "");
            bio = Codec.string(profile, "bio", "");
            avatar = bundle.media(Codec.string(profile, "avatar", ""));
            banner = bundle.media(Codec.string(profile, "banner", ""));
            logo = bundle.media(Codec.string(profile, "logo", ""));
            int count = 0;
            for (JsonObject project : bundle.projects) {
                if (!project.has("removed") || !project.get("removed").getAsBoolean()) {
                    count++;
                }
            }
            projectCount = count;
        }
    }

    public static final class Version {
        public final String name;
        public final long code;
        public final String infohash;
        public final long size;
        public final String sha256;
        public final String notes;
        public final long publishedAt;

        Version(JsonObject version) {
            name = Codec.string(version, "name", "");
            code = Codec.number(version, "code", 0);
            infohash = Codec.string(version, "ih", "");
            size = Codec.number(version, "size", 0);
            sha256 = Codec.string(version, "sha256", "");
            notes = Codec.string(version, "notes", "");
            publishedAt = Codec.number(version, "ts", 0);
        }
    }

    public static final class Listing {
        public final String author;
        public final String authorName;
        public final File authorAvatar;
        public final String id;
        public final String title;
        public final String description;
        public final String kind;
        public final String packageName;
        public final String category;
        public final List<String> tags;
        public final File icon;
        public final List<File> screenshots;
        public final List<Version> versions;
        public final long updatedAt;
        public final boolean removed;
        public final Metrics metrics;
        public final JsonObject record;

        Listing(Bundle bundle, JsonObject project, Metrics metrics) {
            record = project;
            author = bundle.author();
            authorName = Codec.string(bundle.profile, "name", "");
            authorAvatar = bundle.media(Codec.string(bundle.profile, "avatar", ""));
            id = Codec.string(project, "id", "");
            title = Codec.string(project, "title", "");
            description = Codec.string(project, "description", "");
            kind = Codec.string(project, "kind", Records.KIND_SKETCHWARE);
            packageName = Codec.string(project, "pkg", "");
            category = Codec.string(project, "category", "");
            tags = Codec.strings(project, "tags");
            icon = bundle.media(Codec.string(project, "icon", ""));
            List<File> shots = new ArrayList<>();
            for (String shot : Codec.strings(project, "shots")) {
                File file = bundle.media(shot);
                if (file != null && file.isFile()) {
                    shots.add(file);
                }
            }
            screenshots = Collections.unmodifiableList(shots);
            List<Version> versionList = new ArrayList<>();
            JsonElement versionArray = project.get("versions");
            if (versionArray != null && versionArray.isJsonArray()) {
                for (JsonElement version : versionArray.getAsJsonArray()) {
                    versionList.add(new Version(version.getAsJsonObject()));
                }
            }
            versionList.sort(Comparator.comparingLong((Version v) -> v.code).reversed()
                    .thenComparing(Comparator.comparingLong((Version v) -> v.publishedAt).reversed()));
            versions = Collections.unmodifiableList(versionList);
            updatedAt = Records.timestamp(project);
            removed = project.has("removed") && project.get("removed").getAsBoolean();
            this.metrics = metrics == null ? new Metrics() : metrics;
        }

        public Version latest() {
            return versions.isEmpty() ? null : versions.get(0);
        }

        boolean matches(String needle) {
            if (title.toLowerCase(Locale.ROOT).contains(needle)
                    || description.toLowerCase(Locale.ROOT).contains(needle)
                    || authorName.toLowerCase(Locale.ROOT).contains(needle)
                    || packageName.toLowerCase(Locale.ROOT).contains(needle)
                    || category.toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
            for (String tag : tags) {
                if (tag.toLowerCase(Locale.ROOT).contains(needle)) {
                    return true;
                }
            }
            return false;
        }
    }

    // Files

    private static String readText(File file) {
        try {
            return file.isFile() ? new String(Files.readAllBytes(file.toPath())).trim() : null;
        } catch (IOException e) {
            return null;
        }
    }

    static void copyFolder(File source, File target) throws IOException {
        if (source.isDirectory()) {
            if (!target.mkdirs() && !target.isDirectory()) {
                throw new IOException("Couldn't create " + target);
            }
            File[] children = source.listFiles();
            if (children != null) {
                for (File child : children) {
                    copyFolder(child, new File(target, child.getName()));
                }
            }
        } else if (source.isFile()) {
            Files.copy(source.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
