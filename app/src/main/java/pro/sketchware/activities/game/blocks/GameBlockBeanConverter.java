package pro.sketchware.activities.game.blocks;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.editor.logic.BlockPane;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import pro.sketchware.activities.game.model.GameBlockItem;
import pro.sketchware.editor.BlockCanvasView;
import pro.sketchware.editor.BlockMenuItem;
import pro.sketchware.editor.BlockView;

/**
 * Bidirectional converter between Game Engine's hierarchical {@link GameBlockItem} tree
 * and Sketchware's native flat {@link BlockBean} graph used by {@link BlockPane} / {@link BlockView}.
 */
public final class GameBlockBeanConverter {

    public static final class ConversionResult {
        public final ArrayList<BlockBean> beans = new ArrayList<>();
        public int rootNextId = -1;
        public int nextAvailableId = 10;
    }

    private GameBlockBeanConverter() {
    }

    @NonNull
    public static ConversionResult toBlockBeans(@NonNull Context context,
                                                @Nullable List<GameBlockItem> rootChain) {
        ConversionResult result = new ConversionResult();
        int[] nextId = new int[]{10};
        if (rootChain != null && !rootChain.isEmpty()) {
            result.rootNextId = emitChain(context, rootChain, result.beans, nextId);
        }
        result.nextAvailableId = nextId[0];
        return result;
    }

    private static int emitChain(@NonNull Context context,
                                 @Nullable List<GameBlockItem> chain,
                                 @NonNull ArrayList<BlockBean> out,
                                 @NonNull int[] nextId) {
        if (chain == null || chain.isEmpty()) {
            return -1;
        }
        List<GameBlockItem> valid = new ArrayList<>();
        for (GameBlockItem item : chain) {
            if (item != null && !TextUtils.isEmpty(item.opCode)) {
                valid.add(item);
            }
        }
        if (valid.isEmpty()) {
            return -1;
        }
        int[] ids = new int[valid.size()];
        for (int i = 0; i < valid.size(); i++) {
            ids[i] = nextId[0]++;
        }
        for (int i = 0; i < valid.size(); i++) {
            int nextSiblingId = (i + 1 < valid.size()) ? ids[i + 1] : -1;
            emitBlock(context, valid.get(i), ids[i], nextSiblingId, out, nextId);
        }
        return ids[0];
    }

    private static void emitBlock(@NonNull Context context,
                                  @NonNull GameBlockItem item,
                                  int id,
                                  int nextBlockId,
                                  @NonNull ArrayList<BlockBean> out,
                                  @NonNull int[] nextId) {
        item.ensureInitialized();
        GameBlockCatalog.BlockDef def = GameBlockCatalog.findByOpCode(item.opCode);

        String type = normalizeBlockType(item.type, def);
        String opCode = item.opCode;
        String spec;
        ArrayList<String> params = new ArrayList<>();

        if ("getVar".equals(opCode)) {
            String varName = (!item.parameters.isEmpty() && !TextUtils.isEmpty(item.parameters.get(0)))
                    ? item.parameters.get(0)
                    : ((!TextUtils.isEmpty(item.spec) && !item.spec.contains("%")) ? item.spec : "score");
            spec = varName;
        } else {
            spec = def != null
                    ? def.spec
                    : (!TextUtils.isEmpty(item.spec) ? item.spec : opCode);
            int pCount = GameBlockCatalog.countPlaceholders(spec);
            for (int p = 0; p < pCount; p++) {
                GameBlockItem nested = item.getParamBlock(p);
                if (nested != null && !TextUtils.isEmpty(nested.opCode)) {
                    int childId = nextId[0]++;
                    params.add("@" + childId);
                    emitBlock(context, nested, childId, -1, out, nextId);
                } else {
                    String val = "";
                    if (item.parameters != null && p < item.parameters.size() && item.parameters.get(p) != null) {
                        val = item.parameters.get(p);
                    } else if (def != null && def.defaultParams != null && p < def.defaultParams.length) {
                        val = def.defaultParams[p];
                    }
                    params.add(val);
                }
            }
        }

        BlockBean bean = new BlockBean(String.valueOf(id), spec, type, "", opCode);
        int rawColor = def != null
                ? def.color
                : (item.color != 0 ? item.color : GameBlockCatalog.COLOR_CONTROL);
        bean.color = MaterialColors.harmonizeWithPrimary(context, rawColor);
        bean.parameters = params;
        bean.subStack1 = emitChain(context, item.subStack1, out, nextId);
        bean.subStack2 = emitChain(context, item.subStack2, out, nextId);
        bean.nextBlock = nextBlockId;
        out.add(bean);
    }

    public static void populatePane(@NonNull Context context,
                                    @NonNull BlockPane pane,
                                    @Nullable List<BlockBean> beans,
                                    int rootNextId,
                                    @Nullable View.OnTouchListener touchListener) {
        if (beans == null || beans.isEmpty()) {
            BlockView root = pane.getRoot();
            if (root != null) {
                root.ha = -1;
                root.k();
            }
            pane.b();
            return;
        }

        Map<Integer, BlockView> viewById = new HashMap<>();
        int maxId = pane.g;
        for (BlockBean bean : beans) {
            if (bean == null || TextUtils.isEmpty(bean.id)) {
                continue;
            }
            int id;
            try {
                id = Integer.parseInt(bean.id);
            } catch (NumberFormatException e) {
                continue;
            }
            maxId = Math.max(maxId, id + 1);
            int color = bean.color != 0 ? bean.color : resolveHarmonizedColor(context, bean.opCode);
            BlockView bv = new BlockView(
                    context,
                    id,
                    bean.spec != null ? bean.spec : "",
                    bean.type != null ? bean.type : " ",
                    bean.typeName != null ? bean.typeName : "",
                    bean.opCode != null ? bean.opCode : "",
                    color);
            bv.pa = pane;
            if (touchListener != null) {
                bv.setOnTouchListener(touchListener);
            }
            pane.addView(bv);
            viewById.put(id, bv);
        }
        pane.g = maxId;

        for (BlockBean bean : beans) {
            if (bean == null || TextUtils.isEmpty(bean.id)) {
                continue;
            }
            int id;
            try {
                id = Integer.parseInt(bean.id);
            } catch (NumberFormatException e) {
                continue;
            }
            BlockView parent = viewById.get(id);
            if (parent == null) {
                continue;
            }
            if (bean.parameters != null && parent.V != null) {
                for (int p = 0; p < bean.parameters.size() && p < parent.V.size(); p++) {
                    String val = bean.parameters.get(p);
                    View slot = parent.V.get(p);
                    if (val != null && val.startsWith("@")) {
                        try {
                            int childId = Integer.parseInt(val.substring(1));
                            BlockView child = viewById.get(childId);
                            if (child != null && slot instanceof BlockCanvasView) {
                                parent.a((BlockCanvasView) slot, child);
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    } else if (slot instanceof BlockMenuItem) {
                        ((BlockMenuItem) slot).setArgValue(val != null ? val : "");
                    }
                }
            }
            if (bean.subStack1 >= 0) {
                BlockView sub1 = viewById.get(bean.subStack1);
                if (sub1 != null) {
                    parent.e(sub1);
                }
            }
            if (bean.subStack2 >= 0) {
                BlockView sub2 = viewById.get(bean.subStack2);
                if (sub2 != null) {
                    parent.f(sub2);
                }
            }
            if (bean.nextBlock >= 0) {
                BlockView next = viewById.get(bean.nextBlock);
                if (next != null) {
                    parent.b(next);
                }
            }
        }

        BlockView root = pane.getRoot();
        if (root != null) {
            if (rootNextId >= 0) {
                BlockView first = viewById.get(rootNextId);
                if (first != null) {
                    root.b(first);
                } else {
                    root.ha = -1;
                }
            } else {
                root.ha = -1;
            }
        }

        // Measure leaf reporters first up to root so every parent computes nested heights/widths cleanly
        for (BlockView bv : viewById.values()) {
            if (bv.fa) {
                bv.o();
            }
        }
        for (BlockView bv : viewById.values()) {
            if (!bv.fa) {
                bv.o();
            }
        }
        if (root != null) {
            root.k();
        }
        pane.b();
    }

    @NonNull
    public static ArrayList<GameBlockItem> fromBlockPane(@NonNull BlockPane pane) {
        ArrayList<GameBlockItem> out = new ArrayList<>();
        BlockView root = pane.getRoot();
        if (root == null || root.ha < 0) {
            return out;
        }
        @SuppressWarnings("unchecked")
        ArrayList<BlockBean> flat = pane.getBlocks();
        if (flat == null || flat.isEmpty()) {
            return out;
        }
        Map<Integer, BlockBean> byId = new HashMap<>();
        for (BlockBean b : flat) {
            if (b != null && !TextUtils.isEmpty(b.id)) {
                try {
                    byId.put(Integer.parseInt(b.id), b);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        Set<Integer> visited = new HashSet<>();
        readChain(root.ha, byId, out, visited);
        for (GameBlockItem item : out) {
            GameBlockCatalog.syncNestedExpressions(item);
        }
        return out;
    }

    private static void readChain(int startId,
                                  @NonNull Map<Integer, BlockBean> byId,
                                  @NonNull ArrayList<GameBlockItem> out,
                                  @NonNull Set<Integer> visited) {
        int curId = startId;
        while (curId >= 0 && !visited.contains(curId)) {
            BlockBean bean = byId.get(curId);
            if (bean == null) {
                break;
            }
            visited.add(curId);
            GameBlockItem item = singleFromBean(bean, byId, visited);
            if (item != null) {
                out.add(item);
            }
            curId = bean.nextBlock;
        }
    }

    @Nullable
    private static GameBlockItem singleFromBean(@NonNull BlockBean bean,
                                                @NonNull Map<Integer, BlockBean> byId,
                                                @NonNull Set<Integer> visited) {
        if (TextUtils.isEmpty(bean.opCode)) {
            return null;
        }
        GameBlockItem item = new GameBlockItem(
                bean.id != null ? bean.id : "",
                bean.opCode,
                bean.spec != null ? bean.spec : "",
                bean.type != null ? bean.type : " ",
                bean.color,
                new String[0]);
        item.ensureInitialized();

        if ("getVar".equals(bean.opCode)) {
            if (bean.parameters != null && !bean.parameters.isEmpty() && !TextUtils.isEmpty(bean.parameters.get(0))) {
                item.parameters.add(bean.parameters.get(0));
            } else {
                item.parameters.add(bean.spec != null ? bean.spec : "score");
            }
        } else if (bean.parameters != null) {
            for (int i = 0; i < bean.parameters.size(); i++) {
                String p = bean.parameters.get(i);
                GameBlockItem nestedItem = null;
                if (p != null && p.startsWith("@")) {
                    try {
                        int childId = Integer.parseInt(p.substring(1));
                        if (!visited.contains(childId)) {
                            BlockBean childBean = byId.get(childId);
                            if (childBean != null) {
                                visited.add(childId);
                                nestedItem = singleFromBean(childBean, byId, visited);
                            }
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
                while (item.paramBlocks.size() <= i) {
                    item.paramBlocks.add(null);
                }
                item.paramBlocks.set(i, nestedItem);
                item.parameters.add(nestedItem != null ? "" : (p != null ? p : ""));
            }
        }

        if (bean.subStack1 >= 0) {
            readChain(bean.subStack1, byId, item.subStack1, visited);
        }
        if (bean.subStack2 >= 0) {
            readChain(bean.subStack2, byId, item.subStack2, visited);
        }
        return item;
    }

    @NonNull
    public static String normalizeBlockType(@Nullable String rawType,
                                            @Nullable GameBlockCatalog.BlockDef def) {
        String t = !TextUtils.isEmpty(rawType) ? rawType : (def != null ? def.type : " ");
        if ("n".equals(t)) {
            return "d";
        }
        if ("v".equals(t) || TextUtils.isEmpty(t)) {
            return " ";
        }
        return t;
    }

    public static int resolveHarmonizedColor(@NonNull Context context, @Nullable String opCode) {
        GameBlockCatalog.BlockDef def = GameBlockCatalog.findByOpCode(opCode);
        int raw = def != null ? def.color : GameBlockCatalog.COLOR_CONTROL;
        return MaterialColors.harmonizeWithPrimary(context, raw);
    }
}
