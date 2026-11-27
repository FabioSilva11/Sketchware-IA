package mod.hey.studios.project.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;

import pro.sketchware.activities.game.model.GameProject;
import pro.sketchware.activities.game.model.GameProjectRepository;
import pro.sketchware.workspace.RecentProjectsRegistry;

public class BackupFactoryThreeModesTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void androidStudioProjectExportsAndRestoresEditableSwb() throws Exception {
        File senderStorage = tempFolder.newFolder("sender_storage");
        File recipientStorage = tempFolder.newFolder("recipient_storage");

        File studioRoot = new File(senderStorage, ".sketchware/mysc/list/605");
        writeUtf8(new File(studioRoot, "settings.gradle"), "rootProject.name = \"EstudioAgil\"\ninclude ':app'\n");
        writeUtf8(new File(studioRoot, "build.gradle"), "// top-level build\n");
        writeUtf8(new File(studioRoot, "local.properties"), "sdk.dir=/Users/secret/Android/Sdk\n");
        writeUtf8(new File(studioRoot, "app/build.gradle"),
                "plugins { id 'com.android.application' }\n"
                        + "android {\n"
                        + "    compileSdkVersion 34\n"
                        + "    defaultConfig {\n"
                        + "        applicationId \"com.shared.estudio\"\n"
                        + "        minSdkVersion 24\n"
                        + "        targetSdkVersion 34\n"
                        + "        versionCode 3\n"
                        + "        versionName \"1.2\"\n"
                        + "    }\n"
                        + "}\n");
        writeUtf8(new File(studioRoot, "app/src/main/AndroidManifest.xml"),
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"com.shared.estudio\"/>\n");
        writeUtf8(new File(studioRoot, "app/src/main/res/values/strings.xml"),
                "<resources><string name=\"app_name\">Estúdio Ágil</string></resources>\n");
        writeUtf8(new File(studioRoot, "app/src/main/java/com/shared/estudio/MainActivity.java"),
                "package com.shared.estudio;\npublic class MainActivity {}\n");
        // Source package named "build" inside src/ must NOT be skipped
        writeUtf8(new File(studioRoot, "app/src/main/java/com/shared/estudio/build/BuildInfo.java"),
                "package com.shared.estudio.build;\npublic class BuildInfo {}\n");

        // Transient build/IDE folders that MUST be excluded from shared .swb
        writeUtf8(new File(studioRoot, "bin/classes.dex"), "dex-bytes");
        writeUtf8(new File(studioRoot, "gen/R.java"), "class R {}");
        writeUtf8(new File(studioRoot, "build/tmp/cache.bin"), "cache");
        writeUtf8(new File(studioRoot, "app/build/outputs/apk/debug/app-debug.apk"), "apk-bytes");
        writeUtf8(new File(studioRoot, ".gradle/cache.lock"), "lock");
        writeUtf8(new File(studioRoot, ".git/config"), "https://token@github.com/private/repo.git");

        HashMap<String, Object> metadata = new HashMap<>();
        metadata.put("sc_id", "605");
        metadata.put("my_ws_name", "EstudioAgil");
        metadata.put("my_app_name", "Estúdio Ágil");
        metadata.put("my_sc_pkg_name", "com.shared.estudio");
        metadata.put("sc_ver_code", "3");
        metadata.put("sc_ver_name", "1.2");
        metadata.put("custom_icon", true);
        metadata.put(RecentProjectsRegistry.PROJECT_KIND_KEY, RecentProjectsRegistry.PROJECT_KIND_ANDROID_STUDIO);
        metadata.put("proj_type", 2);
        assertTrue(BackupFactory.writeEncryptedProjectFile(new File(studioRoot, "project"), new Gson().toJson(metadata)));

        // Auxiliary SDK config, custom icon, and local library
        writeUtf8(new File(senderStorage, ".sketchware/data/605/project_config"), "{\"compile_sdk\":\"34\",\"min_sdk\":\"24\",\"target_sdk\":\"34\"}");
        writeUtf8(new File(senderStorage, ".sketchware/resources/icons/605/icon.png"), "PNG_ICON_DATA");
        File senderLibDir = new File(senderStorage, ".sketchware/libs/local_libs/gson-v2.10.1");
        File senderJar = new File(senderLibDir, "classes.jar");
        writeUtf8(senderJar, "JAR_BYTES");
        JSONArray localLibsJson = new JSONArray();
        localLibsJson.put(new JSONObject()
                .put("name", "gson-v2.10.1")
                .put("dependency", "com.google.code.gson:gson:2.10.1")
                .put("jarPath", senderJar.getAbsolutePath()));
        writeUtf8(new File(senderStorage, ".sketchware/data/605/local_library"), localLibsJson.toString());

        BackupFactory exporter = new BackupFactory("605", senderStorage);
        exporter.setBackupLocalLibs(true);
        exporter.backup(null, "EstudioAgil");

        File swbFile = exporter.getOutFile();
        assertNotNull(exporter.getError(), swbFile);
        assertTrue(swbFile.isFile());
        assertTrue("Android Studio backup must use .swb extension", swbFile.getName().endsWith(".swb"));
        assertTrue(BackupFactory.isAndroidStudioArchive(swbFile));
        assertTrue(BackupFactory.zipContainsFile(swbFile.getAbsolutePath(), "local_libs"));
        assertFalse(BackupFactory.zipContainsFile(swbFile.getAbsolutePath(), "studio_project/bin"));
        assertFalse(BackupFactory.zipContainsFile(swbFile.getAbsolutePath(), "studio_project/gen"));
        assertFalse(BackupFactory.zipContainsFile(swbFile.getAbsolutePath(), "studio_project/app/build"));
        assertFalse(BackupFactory.zipContainsFile(swbFile.getAbsolutePath(), "studio_project/.git"));
        assertFalse(BackupFactory.zipContainsFile(swbFile.getAbsolutePath(), "studio_project/local.properties"));

        // Recipient opens the .swb via generic restore() (same flow as WhatsApp / ACTION_VIEW)
        BackupFactory importer = new BackupFactory("710", recipientStorage);
        importer.setBackupLocalLibs(true);
        importer.restore(swbFile);
        assertTrue(importer.getError(), importer.isRestoreSuccess());

        File restoredStudioRoot = new File(recipientStorage, ".sketchware/mysc/list/710");
        assertTrue(new File(restoredStudioRoot, "app/src/main/java/com/shared/estudio/MainActivity.java").isFile());
        assertTrue(new File(restoredStudioRoot, "app/src/main/java/com/shared/estudio/build/BuildInfo.java").isFile());
        assertFalse(new File(restoredStudioRoot, "bin").exists());
        assertFalse(new File(restoredStudioRoot, "app/build").exists());
        assertFalse(new File(restoredStudioRoot, ".git").exists());

        HashMap<String, Object> restoredMeta = BackupFactory.readEncryptedProjectFile(new File(restoredStudioRoot, "project"));
        assertNotNull(restoredMeta);
        assertEquals("710", restoredMeta.get("sc_id"));
        assertEquals("Estúdio Ágil", restoredMeta.get("my_app_name"));
        assertEquals(RecentProjectsRegistry.PROJECT_KIND_ANDROID_STUDIO, restoredMeta.get(RecentProjectsRegistry.PROJECT_KIND_KEY));
        assertTrue(RecentProjectsRegistry.isAndroidStudioProject(restoredMeta));

        assertEquals("PNG_ICON_DATA", readUtf8(new File(recipientStorage, ".sketchware/resources/icons/710/icon.png")));
        assertTrue(new File(recipientStorage, ".sketchware/data/710/project_config").isFile());

        File recipientJar = new File(recipientStorage, ".sketchware/libs/local_libs/gson-v2.10.1/classes.jar");
        assertTrue(recipientJar.isFile());
        JSONArray restoredLibs = new JSONArray(readUtf8(new File(recipientStorage, ".sketchware/data/710/local_library")));
        assertEquals(1, restoredLibs.length());
        assertEquals(recipientJar.getAbsolutePath(), restoredLibs.getJSONObject(0).getString("jarPath"));
    }

    @Test
    public void legacyAndroidStudioZipStillRestoresCleanly() throws Exception {
        File rawStudioDir = tempFolder.newFolder("LegacyStudioZipSource");
        File nestedProject = new File(rawStudioDir, "MyLegacyApp");
        writeUtf8(new File(nestedProject, "settings.gradle"), "rootProject.name = \"MyLegacyApp\"\n");
        writeUtf8(new File(nestedProject, "app/build.gradle"),
                "android { defaultConfig { applicationId \"com.legacy.studio\" versionCode 2 versionName \"2.0\" } }\n");
        File legacyZip = new File(tempFolder.getRoot(), "MyLegacyApp.zip");
        BackupFactory.zipFolder(rawStudioDir, legacyZip);

        assertTrue(BackupFactory.isAndroidStudioArchive(legacyZip));

        File recipientStorage = tempFolder.newFolder("legacy_recipient");
        BackupFactory importer = new BackupFactory("711", recipientStorage);
        importer.restore(legacyZip);
        assertTrue(importer.getError(), importer.isRestoreSuccess());

        File restoredDir = new File(recipientStorage, ".sketchware/mysc/list/711");
        HashMap<String, Object> restoredMeta = BackupFactory.readEncryptedProjectFile(new File(restoredDir, "project"));
        assertNotNull(restoredMeta);
        assertEquals("711", restoredMeta.get("sc_id"));
        assertEquals("MyLegacyApp", restoredMeta.get("my_ws_name"));
        assertEquals("com.legacy.studio", restoredMeta.get("my_sc_pkg_name"));
        assertTrue(RecentProjectsRegistry.isAndroidStudioProject(restoredMeta));
    }

    @Test
    public void game2dAndNativeSwbRoundtripThroughUnifiedRestore() throws Exception {
        File senderStorage = tempFolder.newFolder("modes_sender");
        File recipientStorage = tempFolder.newFolder("modes_recipient");

        // 1. Game 2D project (601 -> 801)
        HashMap<String, Object> gameMeta = new HashMap<>();
        gameMeta.put("sc_id", "601");
        gameMeta.put("my_ws_name", "SpaceRunner");
        gameMeta.put("my_app_name", "Space Runner 2D");
        gameMeta.put("my_sc_pkg_name", "com.game.spacerunner");
        gameMeta.put("sc_ver_code", "1");
        gameMeta.put("sc_ver_name", "1.0");
        gameMeta.put(RecentProjectsRegistry.PROJECT_KIND_KEY, RecentProjectsRegistry.PROJECT_KIND_GAME_2D);
        assertTrue(BackupFactory.writeEncryptedProjectFile(
                new File(senderStorage, ".sketchware/mysc/list/601/project"),
                new Gson().toJson(gameMeta)));

        GameProject gameProject = GameProject.createDefault("601");
        gameProject.scenes.get(0).name = "Nebula Level";
        File senderGameJson = new File(senderStorage, ".sketchware/data/601/game/game_project.json");
        assertTrue(GameProjectRepository.saveToFile(senderGameJson, "601", gameProject));
        writeUtf8(new File(senderStorage, ".sketchware/resources/images/601/ship.png"), "SHIP_SPRITE");

        BackupFactory gameExporter = new BackupFactory("601", senderStorage);
        gameExporter.backup(null, "SpaceRunner");
        File gameSwb = gameExporter.getOutFile();
        assertNotNull(gameSwb);
        assertFalse(BackupFactory.isAndroidStudioArchive(gameSwb));

        BackupFactory gameImporter = new BackupFactory("801", recipientStorage);
        gameImporter.restore(gameSwb);
        assertTrue(gameImporter.getError(), gameImporter.isRestoreSuccess());

        HashMap<String, Object> restoredGameMeta = BackupFactory.readEncryptedProjectFile(
                new File(recipientStorage, ".sketchware/mysc/list/801/project"));
        assertNotNull(restoredGameMeta);
        assertEquals("801", restoredGameMeta.get("sc_id"));
        assertTrue(RecentProjectsRegistry.isGameProject(restoredGameMeta));
        GameProject restoredGame = GameProjectRepository.loadOrCreateFromFile(
                new File(recipientStorage, ".sketchware/data/801/game/game_project.json"), "801").project;
        assertEquals("801", restoredGame.scId);
        assertEquals("Nebula Level", restoredGame.scenes.get(0).name);
        assertEquals("SHIP_SPRITE", readUtf8(new File(recipientStorage, ".sketchware/resources/images/801/ship.png")));

        // 2. Native Sketchware project (602 -> 802)
        HashMap<String, Object> nativeMeta = new HashMap<>();
        nativeMeta.put("sc_id", "602");
        nativeMeta.put("my_ws_name", "CalcNative");
        nativeMeta.put("my_app_name", "Calculadora");
        nativeMeta.put("my_sc_pkg_name", "com.nativeapp.calc");
        nativeMeta.put("sc_ver_code", "1");
        nativeMeta.put("sc_ver_name", "1.0");
        nativeMeta.put(RecentProjectsRegistry.PROJECT_KIND_KEY, RecentProjectsRegistry.PROJECT_KIND_SKETCHWARE);
        assertTrue(BackupFactory.writeEncryptedProjectFile(
                new File(senderStorage, ".sketchware/mysc/list/602/project"),
                new Gson().toJson(nativeMeta)));
        writeUtf8(new File(senderStorage, ".sketchware/data/602/logic"), "@main.xml_onCreate\n");
        writeUtf8(new File(senderStorage, ".sketchware/data/602/view"), "@main.xml\n");

        BackupFactory nativeExporter = new BackupFactory("602", senderStorage);
        nativeExporter.backup(null, "CalcNative");
        File nativeSwb = nativeExporter.getOutFile();
        assertNotNull(nativeSwb);
        assertFalse(BackupFactory.isAndroidStudioArchive(nativeSwb));

        BackupFactory nativeImporter = new BackupFactory("802", recipientStorage);
        nativeImporter.restore(nativeSwb);
        assertTrue(nativeImporter.getError(), nativeImporter.isRestoreSuccess());

        HashMap<String, Object> restoredNativeMeta = BackupFactory.readEncryptedProjectFile(
                new File(recipientStorage, ".sketchware/mysc/list/802/project"));
        assertNotNull(restoredNativeMeta);
        assertEquals("802", restoredNativeMeta.get("sc_id"));
        assertEquals(RecentProjectsRegistry.PROJECT_KIND_SKETCHWARE, restoredNativeMeta.get(RecentProjectsRegistry.PROJECT_KIND_KEY));
        assertFalse(RecentProjectsRegistry.isAndroidStudioProject(restoredNativeMeta));
        assertFalse(RecentProjectsRegistry.isGameProject(restoredNativeMeta));
        assertEquals("@main.xml_onCreate\n", readUtf8(new File(recipientStorage, ".sketchware/data/802/logic")));
    }

    private static void writeUtf8(File file, String text) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            assertTrue(parent.mkdirs() || parent.isDirectory());
        }
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    private static String readUtf8(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
