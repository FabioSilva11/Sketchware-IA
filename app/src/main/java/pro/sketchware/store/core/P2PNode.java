package pro.sketchware.store.core;

import com.google.gson.JsonObject;

import org.libtorrent4j.AlertListener;
import org.libtorrent4j.Entry;
import org.libtorrent4j.SessionManager;
import org.libtorrent4j.SessionParams;
import org.libtorrent4j.SettingsPack;
import org.libtorrent4j.Sha1Hash;
import org.libtorrent4j.TcpEndpoint;
import org.libtorrent4j.TorrentBuilder;
import org.libtorrent4j.TorrentFlags;
import org.libtorrent4j.TorrentHandle;
import org.libtorrent4j.TorrentInfo;
import org.libtorrent4j.TorrentStatus;
import org.libtorrent4j.alerts.Alert;
import org.libtorrent4j.alerts.AlertType;
import org.libtorrent4j.alerts.TorrentAlert;
import org.libtorrent4j.swig.add_torrent_params;
import org.libtorrent4j.swig.libtorrent;
import org.libtorrent4j.swig.remove_flags_t;
import org.libtorrent4j.swig.settings_pack;
import org.libtorrent4j.swig.torrent_flags_t;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The device's place in the network: a BitTorrent session on the Mainline DHT. Content (user
 * bundles and project versions) is shared as torrents, found by infohash through the DHT and
 * local service discovery, downloaded from every peer that has it, verified piece by piece and
 * shared again. Small signed values (where a user's latest bundle is) live in the DHT itself
 * (BEP 44), so no server is involved at any point.
 *
 * <p>Everything this node shares or caches is kept under {@code root}:
 * {@code torrents/<infohash>.torrent} plus a {@code .json} sidecar, and the content in
 * {@code data/<infohash>/}.
 */
public final class P2PNode {

    public static final String KIND_BUNDLE = "bundle";
    public static final String KIND_VERSION = "version";

    /** Salt of the DHT item where a user publishes the infohash of their latest bundle. */
    private static final byte[] BUNDLE_POINTER_SALT = "swia/bundle/v1".getBytes(StandardCharsets.UTF_8);
    private static final Identity DIRECTORY = Identity.wellKnown("sketchware-ia/store/directory/v1");
    public static final int DIRECTORY_BUCKETS = 32;
    private static final int DIRECTORY_BUCKET_SIZE = 12;
    private static final long DIRECTORY_ENTRY_TTL_DAYS = 30;

    public static final long MAX_BUNDLE_SIZE = 32L * 1024 * 1024;

    public interface TransferListener {
        void onProgress(Transfer transfer);

        void onFinished(Transfer transfer);

        void onFailed(Transfer transfer, String error);
    }

    public static final class Transfer {
        public final String infohash;
        public final File folder;
        public final long expectedSize;
        public volatile float progress;
        public volatile int peers;
        public volatile int seeds;
        public volatile long downloadRate;
        public volatile long size;
        public volatile boolean finished;
        final List<TransferListener> listeners = new CopyOnWriteArrayList<>();

        Transfer(String infohash, File folder, long expectedSize) {
            this.infohash = infohash;
            this.folder = folder;
            this.expectedSize = expectedSize;
        }
    }

    private final File root;
    private final File torrentsDir;
    private final File dataDir;
    private final SessionManager session = new SessionManager(false);
    private final Map<String, Transfer> transfers = new ConcurrentHashMap<>();
    private volatile boolean sharing = true;
    private volatile boolean networkAllowed = true;

    public P2PNode(File root) {
        this.root = root;
        torrentsDir = new File(root, "torrents");
        dataDir = new File(root, "data");
        torrentsDir.mkdirs();
        dataDir.mkdirs();
    }

    public File root() {
        return root;
    }

    // Lifecycle

    public synchronized void start(boolean sharing) {
        if (session.isRunning()) {
            return;
        }
        this.sharing = sharing;
        SettingsPack settings = new SettingsPack();
        settings.listenInterfaces("0.0.0.0:0,[::]:0");
        settings.setEnableDht(true);
        settings.setEnableLsd(true);
        settings.setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), true);
        settings.setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), true);
        settings.setMaxMetadataSize(4 * 1024 * 1024);
        settings.activeDownloads(4);
        settings.activeSeeds(sharing ? 40 : 0);
        settings.activeLimit(60);
        settings.connectionsLimit(120);
        settings.alertQueueSize(5000);
        session.addListener(alertListener);
        session.start(new SessionParams(settings));
        if (!networkAllowed) {
            session.pause();
        }
        resumeSharedTorrents();
    }

    public synchronized void stop() {
        if (session.isRunning()) {
            session.removeListener(alertListener);
            session.stop();
        }
        transfers.clear();
    }

    public boolean isRunning() {
        return session.isRunning();
    }

    public long dhtNodes() {
        return session.isRunning() ? session.dhtNodes() : 0;
    }

    public long downloadRate() {
        return session.isRunning() ? session.downloadRate() : 0;
    }

    public long uploadRate() {
        return session.isRunning() ? session.stats().uploadRate() : 0;
    }

    /** Pauses all traffic, e.g. while only a metered connection is available. */
    public void setNetworkAllowed(boolean allowed) {
        networkAllowed = allowed;
        if (!session.isRunning()) {
            return;
        }
        if (allowed) {
            session.resume();
        } else {
            session.pause();
        }
    }

    public boolean isNetworkAllowed() {
        return networkAllowed;
    }

    /** With sharing off, finished content stops being uploaded to others. */
    public void setSharing(boolean sharing) {
        this.sharing = sharing;
        if (!session.isRunning()) {
            return;
        }
        SettingsPack settings = new SettingsPack();
        settings.activeSeeds(sharing ? 40 : 0);
        session.applySettings(settings);
        for (File meta : listMeta()) {
            TorrentHandle handle = session.find(Sha1Hash.parseHex(stripExtension(meta.getName())));
            if (handle == null || !handle.isValid()) {
                continue;
            }
            TorrentStatus status = handle.status();
            if (status.isFinished()) {
                if (sharing) {
                    handle.resume();
                } else {
                    handle.pause();
                }
            }
        }
    }

    // DHT values

    /** Publishes where {@code identity}'s latest bundle is. Needs refreshing, DHT nodes forget items after a while. */
    public void publishBundlePointer(Identity identity, String bundleInfohash, long sequence) {
        if (!session.isRunning()) {
            return;
        }
        Map<String, Object> value = new HashMap<>();
        value.put("ih", bundleInfohash);
        value.put("seq", sequence);
        session.dhtPutItem(identity.publicKey(), identity.secretKey(), Entry.fromMap(value), BUNDLE_POINTER_SALT);
    }

    /** The infohash of a user's latest bundle, or null when the DHT doesn't answer in time. */
    public String findBundlePointer(String userId, int timeoutSeconds) {
        if (!session.isRunning() || !Codec.isHex(userId, 64)) {
            return null;
        }
        SessionManager.MutableItem item = session.dhtGetItem(Codec.unhex(userId), BUNDLE_POINTER_SALT, timeoutSeconds);
        if (item == null || item.item == null) {
            return null;
        }
        try {
            Entry ih = item.item.dictionary().get("ih");
            String infohash = ih == null ? null : ih.string();
            return Codec.isHex(infohash, 40) ? infohash : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Adds {@code userId} to its bucket of the shared directory. The directory is a set of DHT
     * items under a key everyone knows, so any node can write to it; it only holds user ids,
     * everything else is fetched from the users' own signed items.
     */
    public void registerInDirectory(String userId, long today) {
        if (!session.isRunning()) {
            return;
        }
        int bucket = (Codec.sha256(userId.getBytes(StandardCharsets.UTF_8))[0] & 0xff) % DIRECTORY_BUCKETS;
        byte[] salt = directorySalt(bucket);
        Map<String, Long> entries = readBucket(bucket, 15);
        entries.remove(userId);
        entries.entrySet().removeIf(entry -> today - entry.getValue() > DIRECTORY_ENTRY_TTL_DAYS);
        List<Map.Entry<String, Long>> newest = new ArrayList<>(entries.entrySet());
        newest.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        List<String> values = new ArrayList<>();
        values.add(userId + ":" + today);
        for (Map.Entry<String, Long> entry : newest) {
            if (values.size() >= DIRECTORY_BUCKET_SIZE) {
                break;
            }
            values.add(entry.getKey() + ":" + entry.getValue());
        }
        Map<String, Object> value = new HashMap<>();
        value.put("u", values);
        session.dhtPutItem(DIRECTORY.publicKey(), DIRECTORY.secretKey(), Entry.fromMap(value), salt);
    }

    /** User ids listed in one directory bucket, with the day they were last seen. */
    public Map<String, Long> readBucket(int bucket, int timeoutSeconds) {
        Map<String, Long> entries = new HashMap<>();
        if (!session.isRunning()) {
            return entries;
        }
        SessionManager.MutableItem item = session.dhtGetItem(DIRECTORY.publicKey(), directorySalt(bucket), timeoutSeconds);
        if (item == null || item.item == null) {
            return entries;
        }
        try {
            Entry users = item.item.dictionary().get("u");
            if (users == null) {
                return entries;
            }
            for (Entry user : users.list()) {
                String[] parts = user.string().split(":");
                if (parts.length == 2 && Codec.isHex(parts[0], 64)) {
                    entries.put(parts[0], Long.parseLong(parts[1]));
                }
            }
        } catch (RuntimeException ignored) {
            // Anyone can write the directory; garbage in a bucket is skipped
        }
        return entries;
    }

    private static byte[] directorySalt(int bucket) {
        return ("swia/dir/v1/" + bucket).getBytes(StandardCharsets.UTF_8);
    }

    /** How many peers the DHT knows for a torrent. */
    public int countPeers(String infohash, int timeoutSeconds) {
        if (!session.isRunning() || !Codec.isHex(infohash, 40)) {
            return 0;
        }
        Set<String> unique = new HashSet<>();
        for (TcpEndpoint endpoint : session.dhtGetPeers(Sha1Hash.parseHex(infohash), timeoutSeconds)) {
            unique.add(endpoint.toString());
        }
        TorrentHandle handle = session.find(Sha1Hash.parseHex(infohash));
        int connected = 0;
        if (handle != null && handle.isValid()) {
            TorrentStatus status = handle.status();
            connected = status.numPeers();
            if (status.isFinished()) {
                connected++; // This device has it too
            }
        }
        return Math.max(unique.size(), connected);
    }

    // Sharing content

    /**
     * Turns {@code content} (a file or folder) into a torrent and starts sharing it. The content is
     * moved under the node's data folder, so it must not be modified afterwards.
     *
     * @return the infohash
     */
    public String share(File content, String kind, boolean pinned, String owner) throws IOException {
        // v1 only: magnet links by btih don't carry v2 piece layers, so hybrid torrents can't be
        // downloaded that way. Pieces are still hash-checked, and what was signed is verified on top.
        TorrentBuilder.Result result = new TorrentBuilder().path(content).creator("Sketchware IA")
                .flags(TorrentBuilder.V1_ONLY).setPrivate(false).generate();
        byte[] torrent = result.entry().bencode();
        TorrentInfo info = TorrentInfo.bdecode(torrent);
        String infohash = info.infoHash().toHex();
        File folder = new File(dataDir, infohash);
        File target = new File(folder, content.getName());
        if (!target.exists()) {
            folder.mkdirs();
            if (!content.renameTo(target)) {
                Catalog.copyFolder(content, target);
                Bundle.deleteRecursively(content);
            }
        } else if (!content.equals(target)) {
            Bundle.deleteRecursively(content);
        }
        Files.write(new File(torrentsDir, infohash + ".torrent").toPath(), torrent);
        writeMeta(infohash, kind, pinned, owner, info.totalSize());
        if (session.isRunning()) {
            torrent_flags_t flags = TorrentFlags.SEED_MODE;
            if (!sharing && !pinned) {
                flags = flags.or_(TorrentFlags.PAUSED);
            }
            session.download(info, folder, null, null, null, flags);
        }
        return infohash;
    }

    /**
     * Downloads a torrent by infohash into the node's data folder, from every peer that has it.
     * Calls back on the alert thread. {@code expectedSize} (0 for unknown) rejects content of
     * another size; bundles are capped at {@link #MAX_BUNDLE_SIZE}.
     */
    public Transfer download(String infohash, String kind, String owner, long expectedSize, TransferListener listener) {
        Transfer existing = transfers.get(infohash);
        if (existing != null) {
            if (listener != null) {
                existing.listeners.add(listener);
            }
            return existing;
        }
        File folder = new File(dataDir, infohash);
        Transfer transfer = new Transfer(infohash, folder, expectedSize);
        if (listener != null) {
            transfer.listeners.add(listener);
        }
        TorrentHandle handle = session.isRunning() ? session.find(Sha1Hash.parseHex(infohash)) : null;
        if (handle != null && handle.isValid() && handle.status().isFinished()) {
            touch(infohash);
            transfer.finished = true;
            transfer.progress = 1;
            for (TransferListener l : transfer.listeners) {
                l.onFinished(transfer);
            }
            return transfer;
        }
        if (!session.isRunning()) {
            if (listener != null) {
                listener.onFailed(transfer, "The P2P network isn't running");
            }
            return transfer;
        }
        transfers.put(infohash, transfer);
        writeMeta(infohash, kind, false, owner, expectedSize);
        folder.mkdirs();
        session.download("magnet:?xt=urn:btih:" + infohash, folder, new torrent_flags_t());
        return transfer;
    }

    public void cancel(String infohash) {
        Transfer transfer = transfers.remove(infohash);
        remove(infohash, true);
        if (transfer != null) {
            for (TransferListener listener : transfer.listeners) {
                listener.onFailed(transfer, "Cancelled");
            }
        }
    }

    public Transfer transfer(String infohash) {
        return transfers.get(infohash);
    }

    /** Refreshes the progress of running transfers; call it periodically while showing progress. */
    public void pollTransfers() {
        if (!session.isRunning()) {
            return;
        }
        for (Transfer transfer : transfers.values()) {
            TorrentHandle handle = session.find(Sha1Hash.parseHex(transfer.infohash));
            if (handle == null || !handle.isValid()) {
                continue;
            }
            TorrentStatus status = handle.status();
            transfer.progress = status.progress();
            transfer.peers = status.numPeers();
            transfer.seeds = status.numSeeds();
            transfer.downloadRate = status.downloadPayloadRate();
            transfer.size = status.totalWanted();
            for (TransferListener listener : transfer.listeners) {
                listener.onProgress(transfer);
            }
        }
    }

    public File content(String infohash) {
        File folder = new File(dataDir, infohash);
        File[] children = folder.listFiles();
        return children != null && children.length == 1 ? children[0] : null;
    }

    public boolean has(String infohash) {
        return new File(torrentsDir, infohash + ".torrent").isFile() && content(infohash) != null;
    }

    public void remove(String infohash, boolean deleteFiles) {
        if (session.isRunning()) {
            TorrentHandle handle = session.find(Sha1Hash.parseHex(infohash));
            if (handle != null && handle.isValid()) {
                session.remove(handle, new remove_flags_t());
            }
        }
        new File(torrentsDir, infohash + ".torrent").delete();
        new File(torrentsDir, infohash + ".json").delete();
        if (deleteFiles) {
            Bundle.deleteRecursively(new File(dataDir, infohash));
        }
    }

    // Cache bookkeeping

    public static final class CachedItem {
        public final String infohash;
        public final String kind;
        public final String owner;
        public final boolean pinned;
        public final long size;
        public final long lastAccess;

        CachedItem(String infohash, JsonObject meta) {
            this.infohash = infohash;
            kind = Codec.string(meta, "kind", "");
            owner = Codec.string(meta, "owner", "");
            pinned = meta.has("pinned") && meta.get("pinned").getAsBoolean();
            size = Codec.number(meta, "size", 0);
            lastAccess = Codec.number(meta, "access", 0);
        }
    }

    public List<CachedItem> cachedItems() {
        List<CachedItem> items = new ArrayList<>();
        for (File meta : listMeta()) {
            try {
                JsonObject json = Codec.parseObject(new String(Files.readAllBytes(meta.toPath()), StandardCharsets.UTF_8));
                items.add(new CachedItem(stripExtension(meta.getName()), json));
            } catch (IOException | RuntimeException e) {
                meta.delete();
            }
        }
        return items;
    }

    public void setPinned(String infohash, boolean pinned) {
        File meta = new File(torrentsDir, infohash + ".json");
        try {
            JsonObject json = meta.isFile()
                    ? Codec.parseObject(new String(Files.readAllBytes(meta.toPath()), StandardCharsets.UTF_8))
                    : new JsonObject();
            json.addProperty("pinned", pinned);
            Files.write(meta.toPath(), Codec.canonical(json));
        } catch (IOException | RuntimeException ignored) {
            // Bookkeeping only
        }
    }

    public void touch(String infohash) {
        File meta = new File(torrentsDir, infohash + ".json");
        try {
            if (meta.isFile()) {
                JsonObject json = Codec.parseObject(new String(Files.readAllBytes(meta.toPath()), StandardCharsets.UTF_8));
                json.addProperty("access", System.currentTimeMillis());
                Files.write(meta.toPath(), Codec.canonical(json));
            }
        } catch (IOException | RuntimeException ignored) {
            // Bookkeeping only
        }
    }

    private void writeMeta(String infohash, String kind, boolean pinned, String owner, long size) {
        JsonObject json = new JsonObject();
        json.addProperty("kind", kind);
        json.addProperty("pinned", pinned);
        json.addProperty("owner", owner == null ? "" : owner);
        json.addProperty("size", size);
        json.addProperty("access", System.currentTimeMillis());
        try {
            Files.write(new File(torrentsDir, infohash + ".json").toPath(), Codec.canonical(json));
        } catch (IOException ignored) {
            // Bookkeeping only
        }
    }

    private List<File> listMeta() {
        List<File> files = new ArrayList<>();
        File[] children = torrentsDir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.getName().endsWith(".json") && Codec.isHex(stripExtension(child.getName()), 40)) {
                    files.add(child);
                }
            }
        }
        return files;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private void resumeSharedTorrents() {
        for (CachedItem item : cachedItems()) {
            File torrent = new File(torrentsDir, item.infohash + ".torrent");
            File folder = new File(dataDir, item.infohash);
            if (!torrent.isFile() || !folder.isDirectory()) {
                // An unfinished download from a previous run; the content will be asked for again
                remove(item.infohash, true);
                continue;
            }
            try {
                TorrentInfo info = new TorrentInfo(torrent);
                torrent_flags_t flags = new torrent_flags_t();
                if (!sharing && !item.pinned) {
                    flags = flags.or_(TorrentFlags.PAUSED);
                }
                session.download(info, folder, null, null, null, flags);
            } catch (RuntimeException e) {
                remove(item.infohash, true);
            }
        }
    }

    // Alerts

    private final AlertListener alertListener = new AlertListener() {
        @Override
        public int[] types() {
            return new int[]{AlertType.METADATA_RECEIVED.swig(), AlertType.TORRENT_FINISHED.swig(),
                    AlertType.TORRENT_ERROR.swig(), AlertType.FILE_ERROR.swig()};
        }

        @Override
        public void alert(Alert<?> alert) {
            if (!(alert instanceof TorrentAlert)) {
                return;
            }
            TorrentHandle handle = ((TorrentAlert<?>) alert).handle();
            if (handle == null || !handle.isValid()) {
                return;
            }
            String infohash = handle.infoHash().toHex();
            Transfer transfer = transfers.get(infohash);
            if (transfer == null) {
                return;
            }
            AlertType type = alert.type();
            if (type == AlertType.METADATA_RECEIVED) {
                TorrentInfo info = handle.torrentFile();
                long limit = transfer.expectedSize > 0 ? transfer.expectedSize : MAX_BUNDLE_SIZE;
                if (info == null || info.totalSize() > limit
                        || (transfer.expectedSize > 0 && info.totalSize() != transfer.expectedSize)) {
                    fail(transfer, "The shared content isn't the size its publisher signed");
                }
            } else if (type == AlertType.TORRENT_FINISHED) {
                try {
                    saveTorrentFile(handle, infohash);
                    transfer.finished = true;
                    transfer.progress = 1;
                    transfers.remove(infohash);
                    if (!sharing) {
                        handle.pause();
                    }
                    for (TransferListener listener : transfer.listeners) {
                        listener.onFinished(transfer);
                    }
                } catch (IOException | RuntimeException e) {
                    fail(transfer, e.getMessage());
                }
            } else {
                fail(transfer, alert.message());
            }
        }
    };

    private void fail(Transfer transfer, String error) {
        transfers.remove(transfer.infohash);
        remove(transfer.infohash, true);
        for (TransferListener listener : transfer.listeners) {
            listener.onFailed(transfer, error);
        }
    }

    private void saveTorrentFile(TorrentHandle handle, String infohash) throws IOException {
        TorrentInfo info = handle.torrentFile();
        add_torrent_params params = new add_torrent_params();
        params.set_ti(info.swig());
        byte[] torrent = new Entry(libtorrent.write_torrent_file(params)).bencode();
        Files.write(new File(torrentsDir, infohash + ".torrent").toPath(), torrent);
        touch(infohash);
    }
}
