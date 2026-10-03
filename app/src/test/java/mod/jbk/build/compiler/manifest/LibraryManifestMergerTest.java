package mod.jbk.build.compiler.manifest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LibraryManifestMergerTest {
    private static final String HEADER = "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\""
            + " xmlns:tools=\"http://schemas.android.com/tools\"";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File write(String name, String content) throws IOException {
        File file = folder.newFile(name);
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private String merge(List<File> libraries, int[] requiredMinSdk) throws IOException {
        File app = write("AndroidManifest.xml", HEADER + " package=\"com.example.app\">"
                + "<uses-permission android:name=\"android.permission.INTERNET\"/>"
                + "<application android:label=\"App\">"
                + "<activity android:name=\".MainActivity\" android:exported=\"true\"/>"
                + "<service android:name=\"com.google.firebase.components.ComponentDiscoveryService\" android:exported=\"true\"/>"
                + "</application></manifest>");
        File output = new File(folder.getRoot(), "merged/AndroidManifest.xml");
        requiredMinSdk[0] = LibraryManifestMerger.merge(app, libraries, "com.example.app", output);
        return new String(Files.readAllBytes(output.toPath()), StandardCharsets.UTF_8);
    }

    private static int count(String haystack, String needle) {
        Matcher matcher = Pattern.compile(Pattern.quote(needle)).matcher(haystack);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    @Test
    public void mergesLibraryComponentsLikeGradle() throws IOException {
        File messaging = write("messaging.xml", HEADER + " package=\"com.google.firebase.messaging\">"
                + "<uses-sdk android:minSdkVersion=\"23\"/>"
                + "<uses-permission android:name=\"android.permission.INTERNET\"/>"
                + "<uses-permission android:name=\"android.permission.RECEIVE_BOOT_COMPLETED\"/>"
                + "<application>"
                + "<service android:name=\"com.google.firebase.components.ComponentDiscoveryService\" android:exported=\"false\">"
                + "<meta-data android:name=\"com.google.firebase.components:Messaging\" android:value=\"com.google.firebase.components.ComponentRegistrar\"/>"
                + "</service>"
                + "<provider android:name=\"com.google.firebase.provider.FirebaseInitProvider\""
                + " android:authorities=\"${applicationId}.firebaseinitprovider\" tools:ignore=\"MissingClass\"/>"
                + "</application></manifest>");
        File ads = write("ads.xml", HEADER + " package=\"com.google.android.gms.ads\">"
                + "<uses-sdk android:minSdkVersion=\"24\"/>"
                + "<uses-permission android:name=\"android.permission.RECEIVE_BOOT_COMPLETED\" tools:node=\"remove\"/>"
                + "<queries><intent><action android:name=\"android.intent.action.VIEW\"/></intent></queries>"
                + "<application>"
                + "<service android:name=\"com.google.firebase.components.ComponentDiscoveryService\">"
                + "<meta-data android:name=\"com.google.firebase.components:Ads\" android:value=\"com.google.firebase.components.ComponentRegistrar\"/>"
                + "</service>"
                + "</application></manifest>");
        File other = write("other.xml", HEADER + " package=\"com.example.other\">"
                + "<queries><intent><action android:name=\"android.intent.action.VIEW\"/></intent></queries>"
                + "</manifest>");

        int[] requiredMinSdk = new int[1];
        String merged = merge(List.of(messaging, ads, other, new File(folder.getRoot(), "missing.xml")), requiredMinSdk);

        assertEquals(24, requiredMinSdk[0]);
        // Same-named components become one, with the children of every declaration
        assertEquals(1, count(merged, "android:name=\"com.google.firebase.components.ComponentDiscoveryService\""));
        assertTrue(merged.contains("com.google.firebase.components:Messaging"));
        assertTrue(merged.contains("com.google.firebase.components:Ads"));
        // The app manifest wins on conflicting attributes
        assertTrue(merged.contains("android:exported=\"true\""));
        assertFalse(merged.contains("android:exported=\"false\""));
        // Placeholders get the application ID
        assertTrue(merged.contains("com.example.app.firebaseinitprovider"));
        // tools:node="remove" drops what other libraries declare, tools:* never reaches aapt2
        assertFalse(merged.contains("RECEIVE_BOOT_COMPLETED"));
        assertFalse(merged.contains("tools:"));
        // No duplicates of what the app already declares, identical <queries> intents once
        assertEquals(1, count(merged, "android.permission.INTERNET"));
        assertEquals(1, count(merged, "android.intent.action.VIEW"));
        assertTrue(merged.contains(".MainActivity"));
    }
}
