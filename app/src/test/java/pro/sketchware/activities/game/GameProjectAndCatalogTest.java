package pro.sketchware.activities.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import pro.sketchware.activities.game.blocks.GameBlockBeanConverter;
import pro.sketchware.activities.game.blocks.GameBlockCatalog;
import pro.sketchware.activities.game.compiler.GameApkSourceGenerator;
import pro.sketchware.activities.game.model.GameBlockItem;
import pro.sketchware.activities.game.model.GameObjectBean;
import pro.sketchware.activities.game.model.GameProject;
import pro.sketchware.activities.game.model.GameProjectRepository;
import pro.sketchware.activities.game.model.GameScene;
import pro.sketchware.activities.game.model.GameScriptNode;
import pro.sketchware.activities.game.ui.GamePropertyPanelView;

public class GameProjectAndCatalogTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void renameSceneValidatesUniquenessAndPropagatesReferences() {
        GameProject project = new GameProject();
        GameScene scene1 = new GameScene("scene_1", "Stage 1");
        GameScene scene2 = new GameScene("scene_2", "Stage 2");
        project.scenes.add(scene1);
        project.scenes.add(scene2);

        GameObjectBean goal = new GameObjectBean();
        goal.type = GameObjectBean.TYPE_GOAL;
        goal.targetScene = "Stage 2";
        scene1.objects.add(goal);

        GameBlockItem loadBlock = GameBlockCatalog.createBlockInstance(
                GameBlockCatalog.findByOpCode("loadScene"));
        loadBlock.parameters.set(0, "Stage 2");
        GameBlockItem ifBlock = GameBlockCatalog.createBlockInstance(
                GameBlockCatalog.findByOpCode("if"));
        ifBlock.subStack1.add(loadBlock);

        GameScriptNode sceneScript = new GameScriptNode();
        sceneScript.blocks.add(ifBlock);
        scene1.scripts.add(sceneScript);

        GameBlockItem prefabLoad = GameBlockCatalog.createBlockInstance(
                GameBlockCatalog.findByOpCode("loadSceneWithTransition"));
        prefabLoad.parameters.set(0, "Stage 2");
        GameScriptNode prefabScript = new GameScriptNode();
        prefabScript.blocks.add(prefabLoad);
        project.prefabScripts.add(prefabScript);

        assertFalse(project.renameScene(scene2, "   "));
        assertFalse(project.renameScene(scene2, null));
        assertFalse(project.renameScene(scene2, "stage 1"));
        assertEquals("Stage 2", scene2.name);

        assertTrue(project.renameScene(scene2, "Boss Arena"));
        assertEquals("Boss Arena", scene2.name);
        assertEquals("Boss Arena", goal.targetScene);
        assertEquals("Boss Arena", loadBlock.parameters.get(0));
        assertEquals("Boss Arena", prefabLoad.parameters.get(0));

        assertTrue(project.renameScene(scene1, "stage 1"));
        assertEquals("stage 1", scene1.name);
    }

    @Test
    public void catalogHidesRawExprAndUsesScratchBooleanDefaults() {
        Set<String> emptyHexOps = new HashSet<>(Arrays.asList(
                "if", "ifElse", "waitUntil", "repeatUntil", "opAnd", "opOr", "opNot"));
        Set<String> seenEmptyHexOps = new HashSet<>();

        for (GameBlockCatalog.CategoryDef cat : GameBlockCatalog.getCategories()) {
            for (GameBlockCatalog.BlockDef def : cat.blocks) {
                assertNotEquals("boolTrue", def.opCode);
                assertNotEquals("boolFalse", def.opCode);

                if (def.paramKinds != null) {
                    for (int i = 0; i < def.paramKinds.length; i++) {
                        String kind = def.paramKinds[i];
                        assertNotEquals(
                                "Visible block " + def.opCode + " must not use KIND_EXPR",
                                GameBlockCatalog.KIND_EXPR,
                                kind);
                        if (emptyHexOps.contains(def.opCode)
                                && GameBlockCatalog.KIND_COND.equals(kind)) {
                            assertEquals(
                                    "Hex slot on " + def.opCode + " must default empty",
                                    "",
                                    def.defaultParams[i]);
                            seenEmptyHexOps.add(def.opCode);
                        }
                    }
                }
                if (def.defaultParams != null) {
                    for (String param : def.defaultParams) {
                        assertFalse(
                                "Default param on " + def.opCode + " must not expose {var} syntax",
                                param != null && param.contains("{score}"));
                    }
                }
            }
        }

        assertEquals(emptyHexOps, seenEmptyHexOps);
        assertNotNull(GameBlockCatalog.findByOpCode("boolTrue"));
        assertNotNull(GameBlockCatalog.findByOpCode("boolFalse"));
    }

    @Test
    public void catalogToExpressionHandlesNestedComparisonAndEmptyConditions() {
        GameBlockItem cmp = GameBlockCatalog.createBlockInstance(
                GameBlockCatalog.findByOpCode("opGt"));
        GameBlockItem varBlock = GameBlockCatalog.createBlockInstance(
                GameBlockCatalog.findByOpCode("getVar"));
        varBlock.parameters.set(0, "coins");
        cmp.setParamBlock(0, varBlock);
        cmp.parameters.set(1, "10");

        GameBlockCatalog.syncNestedExpressions(cmp);
        assertEquals("(coins > 10)", GameBlockCatalog.toExpression(cmp));

        GameBlockItem emptyAnd = GameBlockCatalog.createBlockInstance(
                GameBlockCatalog.findByOpCode("opAnd"));
        assertEquals("(false && false)", GameBlockCatalog.toExpression(emptyAnd));

        GameBlockItem emptyNot = GameBlockCatalog.createBlockInstance(
                GameBlockCatalog.findByOpCode("opNot"));
        assertEquals("!(false)", GameBlockCatalog.toExpression(emptyNot));
    }

    @Test
    public void catalogMathFnMappingAndEqQuotingAndStringReporter() {
        assertTrue(GameBlockCatalog.isReporterBlock(
                GameBlockCatalog.createBlockInstance(GameBlockCatalog.findByOpCode("opJoin"))));
        assertTrue(GameBlockCatalog.isReporterBlock(
                GameBlockCatalog.createBlockInstance(GameBlockCatalog.findByOpCode("getSpriteName"))));
        assertFalse(GameBlockCatalog.isReporterBlock(
                GameBlockCatalog.createBlockInstance(GameBlockCatalog.findByOpCode("moveSteps"))));

        GameBlockItem eqText = GameBlockCatalog.createBlockInstance(
                GameBlockCatalog.findByOpCode("opEq"));
        GameBlockItem getState = GameBlockCatalog.createBlockInstance(
                GameBlockCatalog.findByOpCode("getVar"));
        getState.parameters.set(0, "state");
        eqText.setParamBlock(0, getState);
        eqText.parameters.set(1, "idle");
        assertEquals("(state == \"idle\")", GameBlockCatalog.toExpression(eqText));

        GameBlockItem eqNum = GameBlockCatalog.createBlockInstance(
                GameBlockCatalog.findByOpCode("opEq"));
        eqNum.setParamBlock(0, getState);
        eqNum.parameters.set(1, "42");
        assertEquals("(state == 42)", GameBlockCatalog.toExpression(eqNum));

        GameBlockItem ceilFn = GameBlockCatalog.createBlockInstance(
                GameBlockCatalog.findByOpCode("opMathFn"));
        ceilFn.parameters.set(0, "ceiling");
        ceilFn.parameters.set(1, "3.2");
        assertEquals("ceil(3.2)", GameBlockCatalog.toExpression(ceilFn));

        GameBlockItem logFn = GameBlockCatalog.createBlockInstance(
                GameBlockCatalog.findByOpCode("opMathFn"));
        logFn.parameters.set(0, "log");
        logFn.parameters.set(1, "100");
        assertEquals("log10(100)", GameBlockCatalog.toExpression(logFn));

        GameBlockItem expFn = GameBlockCatalog.createBlockInstance(
                GameBlockCatalog.findByOpCode("opMathFn"));
        expFn.parameters.set(0, "e ^");
        expFn.parameters.set(1, "2");
        assertEquals("exp(2)", GameBlockCatalog.toExpression(expFn));

        GameBlockItem pow10Fn = GameBlockCatalog.createBlockInstance(
                GameBlockCatalog.findByOpCode("opMathFn"));
        pow10Fn.parameters.set(0, "10 ^");
        pow10Fn.parameters.set(1, "3");
        assertEquals("pow10(3)", GameBlockCatalog.toExpression(pow10Fn));
    }

    @Test
    public void repositoryAtomicSaveBackupRecoveryAndQuarantine() throws Exception {
        File dir = tempFolder.newFolder("game_repo");
        File projectFile = new File(dir, "game_project.json");

        GameProject p = GameProject.createDefault("601");
        p.scenes.get(0).name = "World Alpha";
        p.globalVariables.put("score", "999");

        // Floating stack must survive JSON serialization (not transient)
        GameScriptNode script = new GameScriptNode();
        script.id = "scr_float";
        ArrayList<GameBlockItem> floating = new ArrayList<>();
        floating.add(GameBlockCatalog.createBlockInstance(GameBlockCatalog.findByOpCode("show")));
        script.floatingStacks.add(floating);
        p.scenes.get(0).scripts.add(script);

        assertTrue(GameProjectRepository.saveToFile(projectFile, "601", p));
        assertTrue(projectFile.exists());
        assertEquals(GameProject.CURRENT_SCHEMA_VERSION, p.schemaVersion);

        // Save a second time so .bak is populated with previous valid JSON
        p.scenes.get(0).name = "World Beta";
        assertTrue(GameProjectRepository.saveToFile(projectFile, "601", p));
        File backupFile = GameProjectRepository.getBackupFile(projectFile);
        assertTrue(backupFile.exists());

        // Normal load returns World Beta with floating stack preserved
        GameProjectRepository.LoadResult normalLoad = GameProjectRepository.loadOrCreateFromFile(projectFile, "601");
        assertFalse(normalLoad.recoveredFromBackup);
        assertEquals("World Beta", normalLoad.project.scenes.get(0).name);
        assertEquals(1, normalLoad.project.scenes.get(0).scripts.get(0).floatingStacks.size());
        assertEquals("show", normalLoad.project.scenes.get(0).scripts.get(0).floatingStacks.get(0).get(0).opCode);

        // Corrupt primary JSON -> must recover from .bak and quarantine corrupt file
        Files.write(projectFile.toPath(), "{corrupt json!!!".getBytes(StandardCharsets.UTF_8));
        GameProjectRepository.LoadResult recovered = GameProjectRepository.loadOrCreateFromFile(projectFile, "601");
        assertTrue(recovered.recoveredFromBackup);
        assertEquals("World Alpha", recovered.project.scenes.get(0).name);

        File[] files = dir.listFiles();
        assertNotNull(files);
        boolean hasQuarantine = false;
        for (File f : files) {
            if (f.getName().contains(".corrupt.")) {
                hasQuarantine = true;
                break;
            }
        }
        assertTrue("Corrupt JSON must be quarantined instead of erased", hasQuarantine);
    }

    @Test
    public void cascadeDeletionLayerZIndexAndPrefabLinking() {
        GameProject project = new GameProject();
        GameScene scene1 = new GameScene("scene_1", "Level 1");
        GameScene scene2 = new GameScene("scene_2", "Level 2");
        project.scenes.add(scene1);
        project.scenes.add(scene2);
        project.startSceneId = "scene_2";

        // Layer-scoped Z-index calculation
        GameObjectBean worldObj = GameObjectBean.createDefaultForType(GameObjectBean.TYPE_SPRITE, 10, 10, 1);
        worldObj.zIndex = 5;
        GameObjectBean hudObj = GameObjectBean.createDefaultForType(GameObjectBean.TYPE_HUD_TEXT, 10, 10, 2);
        hudObj.zIndex = 120;
        scene1.objects.add(worldObj);
        scene1.objects.add(hudObj);
        assertEquals(5, scene1.computeTopZIndex(false));
        assertEquals(120, scene1.computeTopZIndex(true));

        // Object cascade removal removes scripts targeting that object
        GameScriptNode objScript = new GameScriptNode();
        objScript.targetObjectId = worldObj.id;
        scene1.scripts.add(objScript);
        assertEquals(1, scene1.removeObjectCascade(worldObj.id).size());
        assertEquals(0, scene1.scripts.size());

        // Prefab spawn linking + prefab removal cascade
        GameObjectBean prefab = GameObjectBean.createDefaultForType(GameObjectBean.TYPE_SPRITE, 0, 0, 1);
        prefab.tag = "enemy";
        prefab.id = "prefab_slime";
        prefab.name = "Slime";
        project.prefabs.add(prefab);
        GameScriptNode prefabScript = new GameScriptNode();
        prefabScript.targetObjectId = prefab.id;
        project.prefabScripts.add(prefabScript);

        GameObjectBean spawned = scene1.cloneObjectWithUniqueIdentity(prefab, 40f, 40f, true);
        assertEquals("prefab_slime", spawned.prefabId);
        assertNotEquals(prefab.id, spawned.id);

        assertTrue(project.removePrefab(prefab.id));
        assertTrue(project.prefabs.isEmpty());
        assertTrue(project.prefabScripts.isEmpty());

        // Scene delete cleanup repairs startSceneId and goal references
        GameObjectBean goal = GameObjectBean.createDefaultForType(GameObjectBean.TYPE_GOAL, 0, 0, 1);
        goal.targetScene = "Level 2";
        scene1.objects.add(goal);
        project.scenes.remove(scene2);
        project.cleanupDeletedSceneReferences(scene2.id, scene2.name);
        assertEquals("scene_1", project.startSceneId);
        assertEquals("__next__", goal.targetScene);

        // Physics capability filtering for Inspector UX
        assertTrue(GamePropertyPanelView.supportsPhysics(
                GameObjectBean.createDefaultForType(GameObjectBean.TYPE_SPRITE, 0, 0, 1)));
        assertTrue(GamePropertyPanelView.supportsPhysics(
                GameObjectBean.createDefaultForType(GameObjectBean.TYPE_GOAL, 0, 0, 1)));
        assertFalse(GamePropertyPanelView.supportsPhysics(
                GameObjectBean.createDefaultForType(GameObjectBean.TYPE_HUD_BUTTON, 0, 0, 1)));
        assertFalse(GamePropertyPanelView.supportsPhysics(
                GameObjectBean.createDefaultForType(GameObjectBean.TYPE_CAMERA, 0, 0, 1)));
        assertFalse(GamePropertyPanelView.supportsPhysics(
                GameObjectBean.createDefaultForType(GameObjectBean.TYPE_LIGHT_2D, 0, 0, 1)));
        assertFalse(GamePropertyPanelView.supportsPhysics(
                GameObjectBean.createDefaultForType(GameObjectBean.TYPE_PARALLAX_BG, 0, 0, 1)));
        assertFalse(GamePropertyPanelView.supportsPhysics(
                GameObjectBean.createDefaultForType(GameObjectBean.TYPE_PARTICLES, 0, 0, 1)));
        assertFalse(GamePropertyPanelView.supportsPhysics(
                GameObjectBean.createDefaultForType(GameObjectBean.TYPE_SPAWNER, 0, 0, 1)));
    }

    @Test
    public void blockBeanConverterPreservesAtLiteralsAndCanonicalSpecs() {
        assertEquals(-1, GameBlockBeanConverter.parseBlockRefId("@player"));
        assertEquals(-1, GameBlockBeanConverter.parseBlockRefId("@"));
        assertEquals(12, GameBlockBeanConverter.parseBlockRefId("@12"));

        GameBlockItem sayBlock = GameBlockCatalog.createBlockInstance(
                GameBlockCatalog.findByOpCode("sayText"));
        sayBlock.parameters.set(0, "@player");

        GameBlockItem setVarBlock = GameBlockCatalog.createBlockInstance(
                GameBlockCatalog.findByOpCode("setVar"));
        setVarBlock.parameters.set(0, "handle");
        setVarBlock.parameters.set(1, "@12");

        GameBlockItem getVarBlock = GameBlockCatalog.createBlockInstance(
                GameBlockCatalog.findByOpCode("getVar"));
        getVarBlock.parameters.set(0, "score");
        GameBlockItem moveBlock = GameBlockCatalog.createBlockInstance(
                GameBlockCatalog.findByOpCode("moveSteps"));
        moveBlock.setParamBlock(0, getVarBlock);

        ArrayList<GameBlockItem> chain = new ArrayList<>();
        chain.add(sayBlock);
        chain.add(setVarBlock);
        chain.add(moveBlock);

        GameBlockBeanConverter.ConversionResult conv = GameBlockBeanConverter.toBlockBeans(null, chain, 10);
        // Safe start ID must be > 12 so "@12" cannot hijack any converted BlockBean ID
        assertTrue("Allocated bean IDs must skip literal @12", conv.rootNextId > 12);

        ArrayList<GameBlockItem> roundTrip = GameBlockBeanConverter.fromBlockBeans(conv.beans, conv.rootNextId);
        assertEquals(3, roundTrip.size());
        assertEquals("@player", roundTrip.get(0).parameters.get(0));
        assertEquals("@12", roundTrip.get(1).parameters.get(1));
        assertNotNull(roundTrip.get(2).getParamBlock(0));
        assertEquals("getVar", roundTrip.get(2).getParamBlock(0).opCode);
        assertEquals("score", roundTrip.get(2).getParamBlock(0).parameters.get(0));
        assertEquals(GameBlockCatalog.findByOpCode("moveSteps").color, roundTrip.get(2).color);
    }

    @Test
    public void generatedApkMainActivityCompilesCleanlyWithJavac() throws Exception {
        String source = GameApkSourceGenerator.buildMainActivitySource("com.example.gameparity", true);
        assertTrue(source.contains("spawned.isClone = true;"));
        assertTrue(source.contains("targetObj.matchKey()"));
        assertTrue(source.contains("lastCheckpointId"));
        assertTrue(source.contains("waitingFibers"));
        assertTrue(source.contains("resolveCircleSolidContact"));
        assertTrue(source.contains("coyoteTimer"));
        assertTrue(source.contains("dstOutXfermode"));
        assertTrue(source.contains("isKnownBuiltInIdentifier"));

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull("JDK JavaCompiler must be available in test environment", compiler);

        File outDir = tempFolder.newFolder("apk_javac_out");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            JavaFileObject srcObj = new SimpleJavaFileObject(
                    URI.create("string:///com/example/gameparity/MainActivity.java"),
                    JavaFileObject.Kind.SOURCE) {
                @Override
                public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                    return source;
                }
            };
            String classpath = System.getProperty("java.class.path", "");
            JavaCompiler.CompilationTask task = compiler.getTask(
                    new StringWriter(),
                    fileManager,
                    diagnostics,
                    Arrays.asList("-classpath", classpath, "-d", outDir.getAbsolutePath()),
                    null,
                    Collections.singletonList(srcObj));
            boolean ok = Boolean.TRUE.equals(task.call());
            if (!ok) {
                StringBuilder errors = new StringBuilder("Generated MainActivity.java failed javac:\n");
                for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                    if (d.getKind() == Diagnostic.Kind.ERROR) {
                        errors.append("Line ").append(d.getLineNumber())
                                .append(": ").append(d.getMessage(null)).append('\n');
                    }
                }
                assertTrue(errors.toString(), false);
            }
        }
    }
}
