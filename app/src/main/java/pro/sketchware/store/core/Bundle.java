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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Everything one user shares about themselves, as a folder that travels as a small torrent:
 * <pre>
 * bundle.json   signed "bundle" record: profile, projects, known users, sequence number
 * events.jsonl  the user's signed events (likes, favorites, views, downloads), one per line
 * media/        images the records refer to, named after their SHA-256
 * </pre>
 * The latest bundle's infohash is published in the DHT under the user's key, so anyone who
 * knows the user can fetch and verify it.
 */
public final class Bundle {

    public static final String MANIFEST = "bundle.json";
    public static final String EVENTS = "events.jsonl";
    public static final String MEDIA = "media";

    public static final int MAX_EVENTS = 5000;
    public static final long MAX_MEDIA_SIZE = 2L * 1024 * 1024;
    public static final int MAX_MEDIA_FILES = 200;

    public final JsonObject manifest;
    public final JsonObject profile;
    public final List<JsonObject> projects;
    public final List<JsonObject> events;
    public final List<String> known;
    public final File folder;

    private Bundle(JsonObject manifest, List<JsonObject> projects, List<JsonObject> events, List<String> known,
                   File folder) {
        this.manifest = manifest;
        profile = manifest.getAsJsonObject("profile");
        this.projects = Collections.unmodifiableList(projects);
        this.events = Collections.unmodifiableList(events);
        this.known = Collections.unmodifiableList(known);
        this.folder = folder;
    }

    public String author() {
        return Records.author(manifest);
    }

    public long sequence() {
        return Codec.number(manifest, "seq", 0);
    }

    public File media(String ref) {
        return Records.isMediaRef(ref) ? new File(new File(folder, MEDIA), ref) : null;
    }

    /**
     * Reads and verifies a bundle folder. Records that don't verify, events by someone else and
     * media whose hash doesn't match their name are dropped; a manifest that doesn't verify (or
     * isn't by {@code expectedAuthor}, when given) makes the whole bundle invalid.
     */
    public static Bundle read(File folder, String expectedAuthor, long now) throws IOException {
        File manifestFile = new File(folder, MANIFEST);
        if (!manifestFile.isFile() || manifestFile.length() > 4L * 1024 * 1024) {
            throw new IOException("Missing or oversized bundle manifest");
        }
        JsonObject manifest = Codec.parseObject(new String(Files.readAllBytes(manifestFile.toPath()),
                StandardCharsets.UTF_8));
        if (!Records.validate(manifest, Records.BUNDLE, now)) {
            throw new IOException("Bundle manifest doesn't verify");
        }
        String author = Records.author(manifest);
        if (expectedAuthor != null && !expectedAuthor.equals(author)) {
            throw new IOException("Bundle is by another user");
        }

        List<JsonObject> projects = new ArrayList<>();
        for (JsonElement project : manifest.getAsJsonArray("projects")) {
            projects.add(project.getAsJsonObject());
        }
        List<String> known = new ArrayList<>(new LinkedHashSet<>(Codec.strings(manifest, "known")));
        known.remove(author);

        List<JsonObject> events = new ArrayList<>();
        File eventsFile = new File(folder, EVENTS);
        if (eventsFile.isFile()) {
            Set<String> seen = new java.util.HashSet<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(eventsFile),
                    StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null && events.size() < MAX_EVENTS) {
                    if (line.isEmpty() || line.length() > 2048) {
                        continue;
                    }
                    try {
                        JsonObject event = Codec.parseObject(line);
                        if (author.equals(Records.author(event)) && Records.validate(event, Records.EVENT, now)
                                && seen.add(Records.hash(event))) {
                            events.add(event);
                        }
                    } catch (RuntimeException ignored) {
                        // A malformed line only costs that event
                    }
                }
            }
        }

        File mediaFolder = new File(folder, MEDIA);
        File[] mediaFiles = mediaFolder.listFiles();
        if (mediaFiles != null) {
            int count = 0;
            for (File file : mediaFiles) {
                boolean valid = ++count <= MAX_MEDIA_FILES && file.isFile() && file.length() <= MAX_MEDIA_SIZE
                        && Records.isMediaRef(file.getName())
                        && file.getName().startsWith(Codec.sha256Hex(file));
                if (!valid) {
                    file.delete();
                }
            }
        }
        return new Bundle(manifest, projects, events, known, folder);
    }

    /**
     * Writes a bundle folder for {@code author}. {@code mediaSources} are the image files the
     * records refer to (by their hash-named file name).
     */
    public static void write(File folder, Identity author, long sequence, JsonObject profile, List<JsonObject> projects,
                             List<String> known, List<JsonObject> events, List<File> mediaSources, long now)
            throws IOException {
        if (folder.exists()) {
            deleteRecursively(folder);
        }
        File media = new File(folder, MEDIA);
        if (!media.mkdirs() && !media.isDirectory()) {
            throw new IOException("Couldn't create " + media);
        }
        JsonObject manifest = Records.newRecord(Records.BUNDLE, author, now);
        manifest.addProperty("seq", sequence);
        manifest.add("profile", profile);
        JsonArray projectArray = new JsonArray();
        for (JsonObject project : projects) {
            projectArray.add(project);
        }
        manifest.add("projects", projectArray);
        List<String> knownIds = new ArrayList<>(new LinkedHashSet<>(known));
        knownIds.remove(author.id());
        if (knownIds.size() > Records.MAX_KNOWN_AUTHORS) {
            knownIds = knownIds.subList(0, Records.MAX_KNOWN_AUTHORS);
        }
        manifest.add("known", Codec.array(knownIds));
        manifest = Records.sign(manifest, author);
        Files.write(new File(folder, MANIFEST).toPath(), Codec.canonical(manifest));

        try (Writer writer = Files.newBufferedWriter(new File(folder, EVENTS).toPath(), StandardCharsets.UTF_8)) {
            int written = 0;
            // The newest events matter most when there are too many to share
            for (int i = Math.max(0, events.size() - MAX_EVENTS); i < events.size(); i++) {
                writer.write(new String(Codec.canonical(events.get(i)), StandardCharsets.UTF_8));
                writer.write('\n');
                written++;
            }
            if (written == 0) {
                writer.write("");
            }
        }
        for (File source : mediaSources) {
            if (source.isFile() && Records.isMediaRef(source.getName())) {
                Files.copy(source.toPath(), new File(media, source.getName()).toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    public static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }
}
