package pro.sketchware.store.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * The store on this device: the user's own profile, projects and interactions, kept under
 * {@code root/me}; what the network shares, in the {@link Catalog}; and the work that keeps both
 * in sync over the {@link P2PNode}: publishing the user's bundle, finding other users (the shared
 * DHT directory, the local network, and the users every bundle lists) and fetching their bundles.
 */
public final class StoreEngine {

    public interface Listener {
        /** The catalog or the node's status changed. Called on a background thread. */
        void onStoreChanged();
    }

    public static final class Status {
        public final boolean running;
        public final boolean networkAllowed;
        public final boolean sharing;
        public final long dhtNodes;
        public final int users;
        public final boolean syncing;
        public final long cacheBytes;

        Status(boolean running, boolean networkAllowed, boolean sharing, long dhtNodes, int users, boolean syncing,
               long cacheBytes) {
            this.running = running;
            this.networkAllowed = networkAllowed;
            this.sharing = sharing;
            this.dhtNodes = dhtNodes;
            this.users = users;
            this.syncing = syncing;
            this.cacheBytes = cacheBytes;
        }
    }

    private static final long MINUTE = 60_000L;
    private static final long DAY = 24 * 60 * MINUTE;
    private static final long REPUBLISH_INTERVAL = 30 * MINUTE;
    private static final long REGISTER_INTERVAL = 60 * MINUTE;
    private static final long DIRECTORY_INTERVAL = 10 * MINUTE;
    private static final long DIRECTORY_INTERVAL_ALONE = 2 * MINUTE;
    private static final long USER_REFRESH_INTERVAL = 30 * MINUTE;
    private static final long UNKNOWN_USER_RETRY_INTERVAL = 3 * MINUTE;
    private static final long MAX_RETRY_INTERVAL = 12 * 60 * MINUTE;
    private static final long MIN_DHT_NODES = 20;
    private static final long CACHE_MAX_AGE = 30 * DAY;
    private static final int LAN_PORT = 47318;
    private static final long LAN_ANNOUNCE_INTERVAL = 30_000L;
    private static final int MAX_CONCURRENT_LOOKUPS = 8;

    private final File root;
    private final File meDir;
    private final File mediaDir;
    private final Identity identity;
    private final Catalog catalog;
    private final P2PNode node;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "store-sync");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService lookups = Executors.newFixedThreadPool(MAX_CONCURRENT_LOOKUPS, r -> {
        Thread thread = new Thread(r, "store-lookup");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService publisher = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "store-publish");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService directoryReads = Executors.newFixedThreadPool(8, r -> {
        Thread thread = new Thread(r, "store-directory");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, Long> nextLookup = new ConcurrentHashMap<>();
    private final Map<String, Integer> failures = new ConcurrentHashMap<>();
    private final Set<String> inFlight = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> directoryUsers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private volatile boolean readingDirectory;
    private volatile String publishedBundle;
    private volatile long publishedSequence;
    private volatile long pointerPublishedWithNodes;
    private final Set<String> lanUsers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private ScheduledFuture<?> syncTask;
    private volatile boolean dirty = true;
    private volatile long lastPublish;
    private volatile long lastDirectory;
    private volatile long lastRegister;
    private volatile Consumer<String> logger;
    private volatile long cacheLimit = 1024L * 1024 * 1024;
    private volatile DatagramSocket lanSocket;
    private Thread lanThread;

    public StoreEngine(File root, Identity identity) {
        this.root = root;
        this.identity = identity;
        meDir = new File(root, "me");
        mediaDir = new File(meDir, "media");
        mediaDir.mkdirs();
        new File(meDir, "projects").mkdirs();
        catalog = new Catalog(new File(root, "catalog"));
        node = new P2PNode(new File(root, "p2p"));
        catalog.load(System.currentTimeMillis());
    }

    public Identity identity() {
        return identity;
    }

    public Catalog catalog() {
        return catalog;
    }

    public P2PNode node() {
        return node;
    }

    /** Receives diagnostic messages about the sync. */
    public void setLogger(Consumer<String> logger) {
        this.logger = logger;
    }

    private void log(String message) {
        Consumer<String> current = logger;
        if (current != null) {
            current.accept(message);
        }
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private void notifyChanged() {
        for (Listener listener : listeners) {
            try {
                listener.onStoreChanged();
            } catch (RuntimeException ignored) {
                // A broken listener mustn't stop the sync
            }
        }
    }

    public Status status() {
        long cacheBytes = 0;
        for (P2PNode.CachedItem item : node.cachedItems()) {
            cacheBytes += item.size;
        }
        return new Status(node.isRunning(), node.isNetworkAllowed(), sharing, node.dhtNodes(), catalog.userCount(),
                readingDirectory || !inFlight.isEmpty(), cacheBytes);
    }

    // Lifecycle

    private volatile boolean sharing = true;

    public synchronized void start(boolean sharing, long cacheLimitBytes) {
        this.sharing = sharing;
        cacheLimit = cacheLimitBytes;
        node.start(sharing);
        startLan();
        if (syncTask == null) {
            syncTask = scheduler.scheduleWithFixedDelay(this::syncSafely, 2, 120, TimeUnit.SECONDS);
        }
        notifyChanged();
    }

    public synchronized void stop() {
        if (syncTask != null) {
            syncTask.cancel(false);
            syncTask = null;
        }
        stopLan();
        node.stop();
        notifyChanged();
    }

    public void setSharing(boolean sharing) {
        this.sharing = sharing;
        node.setSharing(sharing);
        notifyChanged();
    }

    public void setNetworkAllowed(boolean allowed) {
        node.setNetworkAllowed(allowed);
        notifyChanged();
        if (allowed) {
            syncSoon();
        }
    }

    public void setCacheLimit(long bytes) {
        cacheLimit = bytes;
        scheduler.execute(this::enforceCache);
    }

    public void syncSoon() {
        if (node.isRunning()) {
            nextLookup.clear();
            lastDirectory = 0;
            scheduler.execute(this::syncSafely);
        }
    }

    // Own profile and projects

    public synchronized JsonObject myProfile() {
        File file = new File(meDir, "profile.json");
        try {
            if (file.isFile()) {
                return Codec.parseObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            }
        } catch (IOException | RuntimeException ignored) {
            // Treated as no profile yet
        }
        return null;
    }

    public boolean hasProfile() {
        return myProfile() != null;
    }

    /**
     * Updates the user's public profile. Images are optional; pass the current value to keep it
     * (see {@link #myMedia}) or null to remove it.
     */
    public synchronized void updateProfile(String name, String bio, File avatar, File banner, File logo)
            throws IOException {
        JsonObject profile = Records.newRecord(Records.PROFILE, identity, System.currentTimeMillis());
        profile.addProperty("name", clip(name, 60));
        profile.addProperty("bio", clip(bio, 2000));
        profile.addProperty("proof", identity.proof());
        putMedia(profile, "avatar", avatar);
        putMedia(profile, "banner", banner);
        putMedia(profile, "logo", logo);
        profile = Records.sign(profile, identity);
        if (!Records.validate(profile, Records.PROFILE, System.currentTimeMillis())) {
            throw new IOException("The profile isn't valid");
        }
        Files.write(new File(meDir, "profile.json").toPath(), Codec.canonical(profile));
        markDirty();
    }

    /** The file of one of the user's own images, by the reference a record holds. */
    public File myMedia(String ref) {
        return Records.isMediaRef(ref) ? new File(mediaDir, ref) : null;
    }

    private void putMedia(JsonObject record, String key, File image) throws IOException {
        String ref = addMedia(image);
        if (ref != null) {
            record.addProperty(key, ref);
        }
    }

    /** Copies an image into the user's media, named after its hash. */
    public String addMedia(File image) throws IOException {
        if (image == null || !image.isFile()) {
            return null;
        }
        if (image.length() > Bundle.MAX_MEDIA_SIZE) {
            throw new IOException("Images can be at most 2 MB");
        }
        if (image.getParentFile() != null && image.getParentFile().equals(mediaDir) && Records.isMediaRef(image.getName())) {
            return image.getName();
        }
        String name = image.getName().toLowerCase(java.util.Locale.ROOT);
        String extension = name.endsWith(".png") ? "png" : name.endsWith(".webp") ? "webp" : "jpg";
        String ref = Codec.sha256Hex(image) + "." + extension;
        Files.copy(image.toPath(), new File(mediaDir, ref).toPath(), StandardCopyOption.REPLACE_EXISTING);
        return ref;
    }

    public synchronized List<JsonObject> myProjects() {
        List<JsonObject> projects = new ArrayList<>();
        File[] files = new File(meDir, "projects").listFiles();
        if (files != null) {
            for (File file : files) {
                try {
                    projects.add(Codec.parseObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)));
                } catch (IOException | RuntimeException ignored) {
                    // Skipped
                }
            }
        }
        projects.sort((a, b) -> Long.compare(Records.timestamp(b), Records.timestamp(a)));
        return projects;
    }

    public synchronized JsonObject myProject(String projectId) {
        File file = new File(new File(meDir, "projects"), projectId + ".json");
        try {
            return file.isFile()
                    ? Codec.parseObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8))
                    : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Listing details the publisher fills in; images are files to copy into the user's media. */
    public static final class ProjectDetails {
        public String title;
        public String description;
        public String kind;
        public String packageName;
        public String category;
        public List<String> tags = new ArrayList<>();
        public File icon;
        public List<File> screenshots = new ArrayList<>();
    }

    /**
     * Publishes a new version of a project ({@code projectId} null for a new project): shares
     * {@code archive} (moved into the node), adds it to the project's signed record and republishes
     * the user's bundle.
     *
     * @return the project id
     */
    public synchronized String publishVersion(String projectId, ProjectDetails details, File archive,
                                              String versionName, long versionCode, String notes) throws IOException {
        if (!hasProfile()) {
            throw new IOException("Create your store profile first");
        }
        if (archive.length() <= 0 || archive.length() > Records.MAX_PACKAGE_SIZE) {
            throw new IOException("The project package is empty or too big");
        }
        JsonObject previous = projectId == null ? null : myProject(projectId);
        if (projectId == null) {
            projectId = Codec.sha256Hex(identity.id() + ":" + UUID.randomUUID()).substring(0, 32);
        }
        String sha256 = Codec.sha256Hex(archive);
        long size = archive.length();
        String infohash = node.share(archive, P2PNode.KIND_VERSION, true, identity.id());

        long now = System.currentTimeMillis();
        JsonObject project = Records.newRecord(Records.PROJECT, identity, now);
        project.addProperty("id", projectId);
        fillDetails(project, details, previous);
        JsonArray versions = new JsonArray();
        JsonObject version = new JsonObject();
        version.addProperty("name", clip(versionName, 40));
        version.addProperty("code", Math.max(0, versionCode));
        version.addProperty("ih", infohash);
        version.addProperty("size", size);
        version.addProperty("sha256", sha256);
        version.addProperty("notes", clip(notes, 2000));
        version.addProperty("ts", now);
        versions.add(version);
        if (previous != null && previous.get("versions") != null && previous.get("versions").isJsonArray()) {
            for (JsonElement old : previous.getAsJsonArray("versions")) {
                if (versions.size() >= Records.MAX_VERSIONS) {
                    break;
                }
                if (!infohash.equals(Codec.string(old.getAsJsonObject(), "ih", ""))) {
                    versions.add(old);
                }
            }
        }
        project.add("versions", versions);
        saveProject(project);
        return projectId;
    }

    /** Changes a published project's listing without adding a version. */
    public synchronized void updateProjectDetails(String projectId, ProjectDetails details) throws IOException {
        JsonObject previous = myProject(projectId);
        if (previous == null) {
            throw new IOException("Unknown project");
        }
        JsonObject project = Records.newRecord(Records.PROJECT, identity, System.currentTimeMillis());
        project.addProperty("id", projectId);
        fillDetails(project, details, previous);
        project.add("versions", previous.get("versions").deepCopy());
        saveProject(project);
    }

    /** Takes a project down: its listing stays as a signed tombstone so peers drop it too. */
    public synchronized void unpublish(String projectId) throws IOException {
        JsonObject previous = myProject(projectId);
        if (previous == null) {
            return;
        }
        JsonObject project = previous.deepCopy();
        project.remove("sig");
        project.addProperty("ts", System.currentTimeMillis());
        project.addProperty("removed", true);
        project.add("versions", new JsonArray());
        for (JsonElement version : previous.getAsJsonArray("versions")) {
            String ih = Codec.string(version.getAsJsonObject(), "ih", "");
            node.remove(ih, true);
        }
        saveProject(project);
    }

    private void fillDetails(JsonObject project, ProjectDetails details, JsonObject previous) throws IOException {
        String kind = details.kind != null ? details.kind
                : previous != null ? Codec.string(previous, "kind", Records.KIND_SKETCHWARE) : Records.KIND_SKETCHWARE;
        project.addProperty("title", clip(details.title, 80));
        project.addProperty("description", clip(details.description, 8000));
        project.addProperty("kind", kind);
        project.addProperty("pkg", clip(details.packageName, 200));
        project.addProperty("category", clip(details.category, 32));
        List<String> tags = new ArrayList<>();
        for (String tag : details.tags) {
            String clean = clip(tag, 24).trim();
            if (!clean.isEmpty() && tags.size() < Records.MAX_TAGS) {
                tags.add(clean);
            }
        }
        project.add("tags", Codec.array(tags));
        String icon = details.icon != null ? addMedia(details.icon)
                : previous != null ? Codec.string(previous, "icon", "") : "";
        if (icon != null && !icon.isEmpty()) {
            project.addProperty("icon", icon);
        }
        List<String> shots = new ArrayList<>();
        for (File shot : details.screenshots) {
            String ref = addMedia(shot);
            if (ref != null && shots.size() < Records.MAX_SCREENSHOTS) {
                shots.add(ref);
            }
        }
        if (details.screenshots.isEmpty() && previous != null) {
            shots.addAll(Codec.strings(previous, "shots"));
        }
        project.add("shots", Codec.array(shots));
    }

    private void saveProject(JsonObject project) throws IOException {
        project = Records.sign(project, identity);
        if (!Records.validate(project, Records.PROJECT, System.currentTimeMillis())) {
            throw new IOException("The project listing isn't valid");
        }
        Files.write(new File(new File(meDir, "projects"), Codec.string(project, "id", "") + ".json").toPath(),
                Codec.canonical(project));
        markDirty();
    }

    // Interactions

    /**
     * Records an interaction with a project as a signed event (with its proof of work) and
     * shares it with the next bundle. Views count once per project and day, downloads once per
     * version.
     */
    public void recordEvent(String kind, String projectAuthor, String projectId, long versionCode) {
        scheduler.execute(() -> {
            try {
                // Events travel in the user's bundle, which needs a profile; a pseudonymous one will do
                if (!hasProfile()) {
                    updateProfile("User " + identity.id().substring(0, 6), "", null, null, null);
                }
                long now = System.currentTimeMillis();
                List<JsonObject> events = myEvents();
                for (JsonObject event : events) {
                    boolean sameProject = projectId.equals(Codec.string(event, "p", ""))
                            && projectAuthor.equals(Codec.string(event, "pa", ""));
                    if (!sameProject || !kind.equals(Codec.string(event, "k", ""))) {
                        continue;
                    }
                    if (Records.EVENT_VIEW.equals(kind) && Records.timestamp(event) / DAY == now / DAY) {
                        return;
                    }
                    if (Records.EVENT_DOWNLOAD.equals(kind) && Codec.number(event, "v", -1) == versionCode) {
                        return;
                    }
                }
                JsonObject event = Records.newEvent(identity, kind, projectAuthor, projectId, versionCode, now);
                events.add(event);
                writeEvents(compact(events, now));
                markDirty();
            } catch (IOException | RuntimeException ignored) {
                // An interaction that can't be recorded is just not counted
            }
        });
    }

    public synchronized List<JsonObject> myEvents() {
        List<JsonObject> events = new ArrayList<>();
        File file = new File(meDir, "events.jsonl");
        if (!file.isFile()) {
            return events;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty()) {
                    try {
                        events.add(Codec.parseObject(line));
                    } catch (RuntimeException ignored) {
                        // Skipped
                    }
                }
            }
        } catch (IOException ignored) {
            // Treated as no events
        }
        return events;
    }

    private synchronized void writeEvents(List<JsonObject> events) throws IOException {
        File file = new File(meDir, "events.jsonl");
        File temp = new File(meDir, "events.jsonl.tmp");
        try (Writer writer = Files.newBufferedWriter(temp.toPath(), StandardCharsets.UTF_8)) {
            for (JsonObject event : events) {
                writer.write(new String(Codec.canonical(event), StandardCharsets.UTF_8));
                writer.write('\n');
            }
        }
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Keeps what still matters: the latest like and favorite state per project, views from the
     * last 90 days and every download.
     */
    private static List<JsonObject> compact(List<JsonObject> events, long now) {
        events.sort((a, b) -> Long.compare(Records.timestamp(a), Records.timestamp(b)));
        Map<String, JsonObject> latestToggle = new HashMap<>();
        List<JsonObject> kept = new ArrayList<>();
        for (JsonObject event : events) {
            String kind = Codec.string(event, "k", "");
            String project = Codec.string(event, "pa", "") + "/" + Codec.string(event, "p", "");
            switch (kind) {
                case Records.EVENT_LIKE:
                case Records.EVENT_UNLIKE:
                    latestToggle.put("like/" + project, event);
                    break;
                case Records.EVENT_FAVORITE:
                case Records.EVENT_UNFAVORITE:
                    latestToggle.put("fav/" + project, event);
                    break;
                case Records.EVENT_VIEW:
                    if (now - Records.timestamp(event) <= 90 * DAY) {
                        kept.add(event);
                    }
                    break;
                default:
                    kept.add(event);
                    break;
            }
        }
        kept.addAll(latestToggle.values());
        kept.sort((a, b) -> Long.compare(Records.timestamp(a), Records.timestamp(b)));
        return kept;
    }

    // Sync

    /** The user's own data changed: share a new bundle right away. */
    private void markDirty() {
        dirty = true;
        publishSoon();
    }

    /**
     * Rebuilds and shares the user's bundle on its own thread, so a change shows up at once (in this
     * catalog, and for peers) instead of waiting behind lookups of other users.
     */
    private void publishSoon() {
        publisher.execute(() -> {
            if (!hasProfile()) {
                return;
            }
            try {
                publishBundle(System.currentTimeMillis());
            } catch (IOException | RuntimeException e) {
                log("Couldn't publish: " + e.getMessage());
            }
        });
    }

    private void syncSafely() {
        if (!node.isRunning() || !node.isNetworkAllowed()) {
            return;
        }
        try {
            sync();
        } catch (RuntimeException ignored) {
            // Retried on the next round
        }
        notifyChanged();
    }

    /** One round: quick, everything slow (DHT lookups, downloads) runs in the background. */
    private void sync() {
        long now = System.currentTimeMillis();
        if (hasProfile() && (dirty || now - lastPublish > REPUBLISH_INTERVAL)) {
            publishSoon();
        }
        long nodes = node.dhtNodes();
        if (nodes < MIN_DHT_NODES) {
            // Not bootstrapped yet (only the local network works meanwhile); look again shortly
            log("DHT has " + nodes + " nodes, waiting");
            scheduler.schedule(this::syncSafely, 20, TimeUnit.SECONDS);
            return;
        }
        String bundle = publishedBundle;
        if (bundle != null && pointerPublishedWithNodes < MIN_DHT_NODES) {
            // The pointer went out before the DHT was reachable, so few nodes (if any) store it
            node.publishBundlePointer(identity, bundle, publishedSequence);
            pointerPublishedWithNodes = nodes;
            log("Republished the bundle pointer now that the DHT is reachable");
        }
        if (hasProfile() && now - lastRegister > REGISTER_INTERVAL) {
            lastRegister = now;
            lookups.execute(() -> {
                node.registerInDirectory(identity.id(), System.currentTimeMillis() / DAY);
                log("Registered in the directory");
            });
        }
        boolean alone = catalog.userCount() <= (hasProfile() ? 1 : 0);
        if (!readingDirectory && now - lastDirectory > (alone ? DIRECTORY_INTERVAL_ALONE : DIRECTORY_INTERVAL)) {
            lastDirectory = now;
            readingDirectory = true;
            lookups.execute(this::readDirectory);
        }
        lookUpUsers();
        enforceCache();
    }

    private void readDirectory() {
        try {
            List<java.util.concurrent.Future<Map<String, Long>>> buckets = new ArrayList<>();
            for (int bucket = 0; bucket < P2PNode.DIRECTORY_BUCKETS; bucket++) {
                final int index = bucket;
                buckets.add(directoryReads.submit(() -> node.readBucket(index, 20)));
            }
            int found = 0;
            for (java.util.concurrent.Future<Map<String, Long>> bucket : buckets) {
                try {
                    Set<String> users = bucket.get(60, TimeUnit.SECONDS).keySet();
                    found += users.size();
                    directoryUsers.addAll(users);
                } catch (Exception ignored) {
                    // A bucket that doesn't answer is tried next time
                }
            }
            log("Directory listed " + found + " users");
        } finally {
            readingDirectory = false;
        }
        scheduler.execute(this::lookUpUsers);
    }

    /**
     * Starts fetching the bundles of every user worth asking now. Users that don't answer are
     * asked again less and less often, so long-gone users don't keep the device busy.
     */
    private void lookUpUsers() {
        long now = System.currentTimeMillis();
        Set<String> candidates = new LinkedHashSet<>(lanUsers);
        candidates.addAll(directoryUsers);
        candidates.addAll(catalog.knownUsers());
        candidates.remove(identity.id());
        for (String user : candidates) {
            Long next = nextLookup.get(user);
            if ((next != null && now < next) || !inFlight.add(user)) {
                continue;
            }
            lookups.execute(() -> {
                boolean answered = false;
                try {
                    answered = refreshUser(user, null);
                } finally {
                    inFlight.remove(user);
                    long delay;
                    if (answered) {
                        failures.remove(user);
                        delay = USER_REFRESH_INTERVAL;
                    } else {
                        int count = failures.merge(user, 1, Integer::sum);
                        delay = Math.min(UNKNOWN_USER_RETRY_INTERVAL << Math.min(count - 1, 8), MAX_RETRY_INTERVAL);
                    }
                    nextLookup.put(user, System.currentTimeMillis() + delay);
                    notifyChanged();
                }
            });
        }
    }

    /**
     * Fetches {@code user}'s latest bundle if it changed; {@code knownInfohash} skips the DHT lookup.
     *
     * @return whether the user could be reached (their bundle is current or was fetched)
     */
    private boolean refreshUser(String user, String knownInfohash) {
        String infohash = knownInfohash != null ? knownInfohash : node.findBundlePointer(user, 25);
        if (infohash == null) {
            log("No answer for " + user.substring(0, 8));
            return false;
        }
        if (infohash.equals(catalog.infohash(user))) {
            return true;
        }
        log("Fetching bundle " + infohash.substring(0, 8) + " of " + user.substring(0, 8));
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        boolean[] fetched = {false};
        node.download(infohash, P2PNode.KIND_BUNDLE, user, 0, new P2PNode.TransferListener() {
            @Override
            public void onProgress(P2PNode.Transfer transfer) {
            }

            @Override
            public void onFinished(P2PNode.Transfer transfer) {
                try {
                    File content = node.content(infohash);
                    if (content != null) {
                        Bundle bundle = Bundle.read(content, user, System.currentTimeMillis());
                        String previous = catalog.infohash(user);
                        if (catalog.put(bundle, infohash, System.currentTimeMillis())) {
                            if (previous != null && !previous.equals(infohash)) {
                                node.remove(previous, true);
                            }
                            log("Got bundle of " + user.substring(0, 8) + " with " + bundle.projects.size() + " projects");
                            notifyChanged();
                        }
                        fetched[0] = true;
                    }
                } catch (IOException | RuntimeException e) {
                    log("Rejected bundle of " + user.substring(0, 8) + ": " + e.getMessage());
                    node.remove(infohash, true);
                } finally {
                    done.countDown();
                }
            }

            @Override
            public void onFailed(P2PNode.Transfer transfer, String error) {
                log("Bundle of " + user.substring(0, 8) + " failed: " + error);
                done.countDown();
            }
        });
        try {
            done.await(3, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (done.getCount() > 0) {
            node.cancel(infohash);
        }
        return fetched[0];
    }

    private synchronized void publishBundle(long now) throws IOException {
        JsonObject profile = myProfile();
        if (profile == null) {
            return;
        }
        long sequence = Math.max(catalog.sequence(identity.id()) + 1, now);
        List<JsonObject> projects = myProjects();
        Set<String> refs = new HashSet<>();
        collectMedia(profile, refs);
        for (JsonObject project : projects) {
            collectMedia(project, refs);
        }
        List<File> media = new ArrayList<>();
        for (String ref : refs) {
            File file = new File(mediaDir, ref);
            if (file.isFile()) {
                media.add(file);
            }
        }
        List<String> known = new ArrayList<>(catalog.knownUsers());
        Collections.shuffle(known);
        File staging = new File(root, "staging/bundle-" + identity.id().substring(0, 16));
        Bundle.write(staging, identity, sequence, profile, projects, known, myEvents(), media, now);
        String previous = catalog.infohash(identity.id());
        String infohash = node.share(staging, P2PNode.KIND_BUNDLE, true, identity.id());
        Bundle bundle = Bundle.read(node.content(infohash), identity.id(), now);
        catalog.put(bundle, infohash, now);
        if (previous != null && !previous.equals(infohash)) {
            node.remove(previous, true);
        }
        node.publishBundlePointer(identity, infohash, sequence);
        publishedBundle = infohash;
        publishedSequence = sequence;
        pointerPublishedWithNodes = node.dhtNodes();
        log("Published bundle " + infohash.substring(0, 8) + " (sequence " + sequence + ")");
        lastPublish = now;
        dirty = false;
        announceOnLan();
        notifyChanged();
    }

    private static void collectMedia(JsonObject record, Set<String> refs) {
        for (String key : new String[]{"avatar", "banner", "logo", "icon"}) {
            String ref = Codec.string(record, key, "");
            if (Records.isMediaRef(ref)) {
                refs.add(ref);
            }
        }
        for (String shot : Codec.strings(record, "shots")) {
            if (Records.isMediaRef(shot)) {
                refs.add(shot);
            }
        }
    }

    // Downloading versions

    /**
     * Downloads one version of a listing and checks it against the hash its publisher signed.
     * {@code onDone} gets the package file, or null after {@code onError}.
     */
    public void downloadVersion(Catalog.Listing listing, Catalog.Version version, P2PNode.TransferListener progress,
                                Consumer<File> onDone, Consumer<String> onError) {
        node.download(version.infohash, P2PNode.KIND_VERSION, listing.author, version.size,
                new P2PNode.TransferListener() {
                    @Override
                    public void onProgress(P2PNode.Transfer transfer) {
                        if (progress != null) {
                            progress.onProgress(transfer);
                        }
                    }

                    @Override
                    public void onFinished(P2PNode.Transfer transfer) {
                        lookups.execute(() -> {
                            File file = node.content(version.infohash);
                            try {
                                if (file == null || !file.isFile() || file.length() != version.size
                                        || !version.sha256.equals(Codec.sha256Hex(file))) {
                                    node.remove(version.infohash, true);
                                    onError.accept("The downloaded project doesn't match what its publisher signed");
                                    return;
                                }
                            } catch (IOException e) {
                                onError.accept(e.getMessage());
                                return;
                            }
                            node.touch(version.infohash);
                            onDone.accept(file);
                        });
                    }

                    @Override
                    public void onFailed(P2PNode.Transfer transfer, String error) {
                        onError.accept(error);
                    }
                });
    }

    public void refreshPeerCount(Catalog.Listing listing) {
        lookups.execute(() -> {
            boolean changed = false;
            for (Catalog.Version version : listing.versions) {
                int peers = node.countPeers(version.infohash, 15);
                catalog.setPeerCount(version.infohash, peers);
                changed = true;
            }
            if (changed) {
                notifyChanged();
            }
        });
    }

    // Cache

    /**
     * Drops cached content that isn't the user's own: anything unused for 30 days, then the least
     * recently used until the cache fits its limit. Running downloads and the current bundles of
     * known users are kept.
     */
    public void enforceCache() {
        List<P2PNode.CachedItem> items = new ArrayList<>(node.cachedItems());
        long now = System.currentTimeMillis();
        Set<String> currentBundles = new HashSet<>();
        for (String user : catalog.knownUsers()) {
            String ih = catalog.infohash(user);
            if (ih != null) {
                currentBundles.add(ih);
            }
        }
        long total = 0;
        Iterator<P2PNode.CachedItem> iterator = items.iterator();
        while (iterator.hasNext()) {
            P2PNode.CachedItem item = iterator.next();
            boolean protectedItem = item.pinned || node.transfer(item.infohash) != null;
            if (!protectedItem && !currentBundles.contains(item.infohash) && now - item.lastAccess > CACHE_MAX_AGE) {
                node.remove(item.infohash, true);
                iterator.remove();
                continue;
            }
            if (protectedItem) {
                iterator.remove();
            } else {
                total += item.size;
            }
        }
        items.sort((a, b) -> Long.compare(a.lastAccess, b.lastAccess));
        for (P2PNode.CachedItem item : items) {
            if (total <= cacheLimit) {
                break;
            }
            if (currentBundles.contains(item.infohash) && item.size < 1024 * 1024) {
                continue;
            }
            node.remove(item.infohash, true);
            total -= item.size;
        }
    }

    /** Removes everything cached that isn't the user's own content. */
    public void clearCache() {
        for (P2PNode.CachedItem item : node.cachedItems()) {
            if (!item.pinned && node.transfer(item.infohash) == null && !identity.id().equals(item.owner)) {
                node.remove(item.infohash, true);
            }
        }
        notifyChanged();
    }

    // Local network

    private void startLan() {
        if (lanThread != null) {
            return;
        }
        lanThread = new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(null)) {
                socket.setReuseAddress(true);
                socket.setBroadcast(true);
                socket.bind(new java.net.InetSocketAddress(LAN_PORT));
                lanSocket = socket;
                byte[] buffer = new byte[512];
                long lastAnnounce = 0;
                while (!Thread.currentThread().isInterrupted()) {
                    long now = System.currentTimeMillis();
                    if (now - lastAnnounce >= LAN_ANNOUNCE_INTERVAL) {
                        announceOnLan();
                        lastAnnounce = now;
                    }
                    // Our own beacons come back too, so wait out the interval instead of announcing per packet
                    socket.setSoTimeout((int) Math.max(1000, LAN_ANNOUNCE_INTERVAL - (now - lastAnnounce)));
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        socket.receive(packet);
                        handleLanBeacon(new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8));
                    } catch (SocketTimeoutException ignored) {
                        // Time to announce again
                    }
                }
            } catch (IOException ignored) {
                // No local network discovery on this network
            } finally {
                lanSocket = null;
            }
        }, "store-lan");
        lanThread.setDaemon(true);
        lanThread.start();
    }

    private void stopLan() {
        if (lanThread != null) {
            lanThread.interrupt();
            DatagramSocket socket = lanSocket;
            if (socket != null) {
                socket.close();
            }
            lanThread = null;
        }
    }

    private void announceOnLan() {
        DatagramSocket socket = lanSocket;
        String infohash = catalog.infohash(identity.id());
        if (socket == null || infohash == null || !node.isNetworkAllowed()) {
            return;
        }
        byte[] beacon = ("SWIA1 " + identity.id() + " " + infohash).getBytes(StandardCharsets.UTF_8);
        try {
            socket.send(new DatagramPacket(beacon, beacon.length, InetAddress.getByName("255.255.255.255"), LAN_PORT));
        } catch (IOException ignored) {
            // Best effort
        }
    }

    private void handleLanBeacon(String beacon) {
        String[] parts = beacon.trim().split(" ");
        if (parts.length != 3 || !"SWIA1".equals(parts[0]) || !Codec.isHex(parts[1], 64) || !Codec.isHex(parts[2], 40)
                || parts[1].equals(identity.id())) {
            return;
        }
        String user = parts[1];
        if (lanUsers.add(user)) {
            log("Found " + user.substring(0, 8) + " on the local network");
        }
        if (!parts[2].equals(catalog.infohash(user)) && node.transfer(parts[2]) == null && node.isNetworkAllowed()
                && inFlight.add(user)) {
            lookups.execute(() -> {
                try {
                    refreshUser(user, parts[2]);
                } finally {
                    inFlight.remove(user);
                }
            });
        }
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
