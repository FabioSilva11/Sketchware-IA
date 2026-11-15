package pro.sketchware.activities.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import pro.sketchware.activities.game.blocks.GameBlockCatalog;
import pro.sketchware.activities.game.model.GameBlockItem;
import pro.sketchware.activities.game.model.GameObjectBean;
import pro.sketchware.activities.game.model.GameProject;
import pro.sketchware.activities.game.model.GameScene;
import pro.sketchware.activities.game.model.GameScriptNode;

public class GameProjectAndCatalogTest {

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
}
