package pro.sketchware.store.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Signed records: a JSON object with a type ("t"), author ("a", a public key in hex), timestamp
 * ("ts", Unix milliseconds) and an Ed25519 signature ("sig") over the canonical JSON of every
 * other field. Peers only accept records that verify and pass {@link #validate}.
 */
public final class Records {

    public static final String PROFILE = "profile";
    public static final String PROJECT = "project";
    public static final String EVENT = "event";
    public static final String BUNDLE = "bundle";

    public static final String KIND_SKETCHWARE = "sketchware";
    public static final String KIND_ANDROID_STUDIO = "android_studio";

    public static final String EVENT_LIKE = "like";
    public static final String EVENT_UNLIKE = "unlike";
    public static final String EVENT_FAVORITE = "fav";
    public static final String EVENT_UNFAVORITE = "unfav";
    public static final String EVENT_VIEW = "view";
    public static final String EVENT_DOWNLOAD = "download";
    private static final Set<String> EVENT_KINDS = new HashSet<>(Arrays.asList(
            EVENT_LIKE, EVENT_UNLIKE, EVENT_FAVORITE, EVENT_UNFAVORITE, EVENT_VIEW, EVENT_DOWNLOAD));

    /** Leading zero bits each event needs; about 65 thousand hashes. */
    public static final int EVENT_WORK_BITS = 16;

    public static final int MAX_VERSIONS = 50;
    public static final int MAX_SCREENSHOTS = 8;
    public static final int MAX_TAGS = 10;
    public static final int MAX_KNOWN_AUTHORS = 300;
    public static final long MAX_PACKAGE_SIZE = 2L * 1024 * 1024 * 1024;

    /** Records can't predate the store, nor claim to come from the future. */
    private static final long EARLIEST_TIMESTAMP = 1_767_225_600_000L; // 2026-01-01
    private static final long ALLOWED_CLOCK_SKEW = 24L * 60 * 60 * 1000;

    private static final Pattern MEDIA_REF = Pattern.compile("[0-9a-f]{64}\\.(png|jpg|webp)");

    private Records() {
    }

    public static JsonObject newRecord(String type, Identity author, long timestamp) {
        JsonObject record = new JsonObject();
        record.addProperty("t", type);
        record.addProperty("a", author.id());
        record.addProperty("ts", timestamp);
        return record;
    }

    public static JsonObject sign(JsonObject record, Identity author) {
        JsonObject unsigned = record.deepCopy();
        unsigned.remove("sig");
        unsigned.addProperty("a", author.id());
        unsigned.addProperty("sig", Codec.hex(author.sign(Codec.canonical(withoutSignature(unsigned)))));
        return unsigned;
    }

    public static boolean verifySignature(JsonObject record) {
        String author = Codec.string(record, "a", null);
        String signature = Codec.string(record, "sig", null);
        if (author == null || !Codec.isHex(signature, 128)) {
            return false;
        }
        return Identity.verify(author, Codec.canonical(withoutSignature(record)), Codec.unhex(signature));
    }

    private static JsonObject withoutSignature(JsonObject record) {
        JsonObject copy = record.deepCopy();
        copy.remove("sig");
        return copy;
    }

    public static String type(JsonObject record) {
        return Codec.string(record, "t", "");
    }

    public static String author(JsonObject record) {
        return Codec.string(record, "a", "");
    }

    public static long timestamp(JsonObject record) {
        return Codec.number(record, "ts", 0);
    }

    /** Hash identifying a record, used to drop duplicates. */
    public static String hash(JsonObject record) {
        return Codec.hex(Codec.sha256(Codec.canonical(record)));
    }

    // Events

    public static JsonObject newEvent(Identity author, String kind, String projectAuthor, String projectId,
                                      long versionCode, long timestamp) {
        JsonObject event = newRecord(EVENT, author, timestamp);
        event.addProperty("k", kind);
        event.addProperty("p", projectId);
        event.addProperty("pa", projectAuthor);
        event.addProperty("v", versionCode);
        event.addProperty("n", ProofOfWork.solve(eventWorkInput(event), EVENT_WORK_BITS));
        return sign(event, author);
    }

    private static String eventWorkInput(JsonObject event) {
        return "swia-event:" + author(event) + ":" + Codec.string(event, "k", "") + ":"
                + Codec.string(event, "pa", "") + ":" + Codec.string(event, "p", "") + ":"
                + Codec.number(event, "v", 0) + ":" + timestamp(event);
    }

    // Validation

    /**
     * Checks the signature and the shape of a record of the given type. Nested records (a
     * bundle's profile and projects) must be by the same author.
     */
    public static boolean validate(JsonObject record, String expectedType, long now) {
        if (record == null || !expectedType.equals(type(record)) || !verifySignature(record)) {
            return false;
        }
        long ts = timestamp(record);
        if (ts < EARLIEST_TIMESTAMP || ts > now + ALLOWED_CLOCK_SKEW) {
            return false;
        }
        switch (expectedType) {
            case PROFILE:
                return validProfile(record);
            case PROJECT:
                return validProject(record);
            case EVENT:
                return validEvent(record);
            case BUNDLE:
                return validBundle(record, now);
            default:
                return false;
        }
    }

    private static boolean validProfile(JsonObject profile) {
        return Identity.verifyProof(author(profile), Codec.number(profile, "proof", -1))
                && length(profile, "name", 1, 60)
                && length(profile, "bio", 0, 2000)
                && optionalMedia(profile, "avatar")
                && optionalMedia(profile, "banner")
                && optionalMedia(profile, "logo");
    }

    private static boolean validProject(JsonObject project) {
        String kind = Codec.string(project, "kind", "");
        if (!Codec.isHex(Codec.string(project, "id", ""), 32)
                || !(KIND_SKETCHWARE.equals(kind) || KIND_ANDROID_STUDIO.equals(kind))
                || !length(project, "title", 1, 80)
                || !length(project, "description", 0, 8000)
                || !length(project, "pkg", 0, 200)
                || !length(project, "category", 0, 32)
                || !optionalMedia(project, "icon")) {
            return false;
        }
        if (!stringArray(project, "tags", MAX_TAGS, 24) || !mediaArray(project, "shots", MAX_SCREENSHOTS)) {
            return false;
        }
        JsonElement versions = project.get("versions");
        if (versions == null || !versions.isJsonArray() || versions.getAsJsonArray().size() > MAX_VERSIONS) {
            return false;
        }
        boolean removed = project.has("removed") && project.get("removed").getAsBoolean();
        if (!removed && versions.getAsJsonArray().isEmpty()) {
            return false;
        }
        for (JsonElement element : versions.getAsJsonArray()) {
            if (!element.isJsonObject() || !validVersion(element.getAsJsonObject())) {
                return false;
            }
        }
        return true;
    }

    private static boolean validVersion(JsonObject version) {
        long size = Codec.number(version, "size", -1);
        return length(version, "name", 1, 40)
                && Codec.number(version, "code", -1) >= 0
                && Codec.isHex(Codec.string(version, "ih", ""), 40)
                && Codec.isHex(Codec.string(version, "sha256", ""), 64)
                && size > 0 && size <= MAX_PACKAGE_SIZE
                && length(version, "notes", 0, 2000)
                && Codec.number(version, "ts", 0) >= EARLIEST_TIMESTAMP;
    }

    private static boolean validEvent(JsonObject event) {
        return EVENT_KINDS.contains(Codec.string(event, "k", ""))
                && Codec.isHex(Codec.string(event, "p", ""), 32)
                && Codec.isHex(Codec.string(event, "pa", ""), 64)
                && Codec.number(event, "v", -1) >= 0
                && ProofOfWork.check(eventWorkInput(event), Codec.number(event, "n", -1), EVENT_WORK_BITS);
    }

    private static boolean validBundle(JsonObject bundle, long now) {
        String author = author(bundle);
        if (Codec.number(bundle, "seq", -1) < 0) {
            return false;
        }
        JsonElement profile = bundle.get("profile");
        if (profile == null || !profile.isJsonObject() || !author.equals(author(profile.getAsJsonObject()))
                || !validate(profile.getAsJsonObject(), PROFILE, now)) {
            return false;
        }
        JsonElement projects = bundle.get("projects");
        if (projects == null || !projects.isJsonArray()) {
            return false;
        }
        for (JsonElement project : projects.getAsJsonArray()) {
            if (!project.isJsonObject() || !author.equals(author(project.getAsJsonObject()))
                    || !validate(project.getAsJsonObject(), PROJECT, now)) {
                return false;
            }
        }
        JsonElement known = bundle.get("known");
        if (known != null) {
            if (!known.isJsonArray() || known.getAsJsonArray().size() > MAX_KNOWN_AUTHORS) {
                return false;
            }
            for (JsonElement id : known.getAsJsonArray()) {
                if (!id.isJsonPrimitive() || !Codec.isHex(id.getAsString(), 64)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean length(JsonObject object, String key, int min, int max) {
        String value = Codec.string(object, key, min == 0 ? "" : null);
        return value != null && value.length() >= min && value.length() <= max;
    }

    private static boolean optionalMedia(JsonObject object, String key) {
        String value = Codec.string(object, key, "");
        return value.isEmpty() || MEDIA_REF.matcher(value).matches();
    }

    private static boolean stringArray(JsonObject object, String key, int maxItems, int maxLength) {
        JsonElement element = object.get(key);
        if (element == null) {
            return true;
        }
        if (!element.isJsonArray() || element.getAsJsonArray().size() > maxItems) {
            return false;
        }
        for (JsonElement item : element.getAsJsonArray()) {
            if (!item.isJsonPrimitive() || item.getAsString().length() > maxLength) {
                return false;
            }
        }
        return true;
    }

    private static boolean mediaArray(JsonObject object, String key, int maxItems) {
        JsonElement element = object.get(key);
        if (element == null) {
            return true;
        }
        if (!element.isJsonArray()) {
            return false;
        }
        JsonArray array = element.getAsJsonArray();
        if (array.size() > maxItems) {
            return false;
        }
        for (JsonElement item : array) {
            if (!item.isJsonPrimitive() || !MEDIA_REF.matcher(item.getAsString()).matches()) {
                return false;
            }
        }
        return true;
    }

    public static boolean isMediaRef(String value) {
        return value != null && MEDIA_REF.matcher(value).matches();
    }
}
