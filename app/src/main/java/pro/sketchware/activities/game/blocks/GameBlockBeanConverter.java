package pro.sketchware.activities.game.blocks;

import android.content.Context;
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

    private static boolean isEmpty(@Nullable String s) {
        return s == null || s.isEmpty();
    }

    /**
     * Parses a nested block reference of the exact form {@code "@<non-negative-digits>"}.
     * Returns {@code -1} for ordinary text starting with {@code "@"} (e.g. {@code "@player"}).
     */
    public static int parseBlockRefId(@Nullable String param) {
        if (param == null || param.length() < 2 || param.charAt(0) != '@') {
            return -1;
        }
        for (int i = 1; i < param.length(); i++) {
            char c = param.charAt(i);
            if (c < '0' || c > '9') {
                return -1;
            }
        }
        try {
            return Integer.parseInt(param.substring(1));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    /**
     * Computes the highest numeric {@code @<digits>} literal across a tree of blocks so newly
     * allocated bean IDs never collide with literal user text such as {@code "@12"}.
     */
    private static int findMaxLiteralAtRef(@Nullable List<GameBlockItem> chain) {
        int max = 9;
        if (chain == null) {
            return max;
        }
        for (GameBlockItem item : chain) {
            if (item == null) {
                continue;
            }
            if (item.parameters != null) {
                for (int i = 0; i < item.parameters.size(); i++) {
                    if (item.getParamBlock(i) == null) {
                        int ref = parseBlockRefId(item.parameters.get(i));
                        if (ref > max) {
                            max = ref;
                        }
                    }
                }
            }
            max = Math.max(max, findMaxLiteralAtRef(item.paramBlocks));
            max = Math.max(max, findMaxLiteralAtRef(item.subStack1));
            max = Math.max(max, findMaxLiteralAtRef(item.subStack2));
        }
        return max;
    }

    @NonNull
    public static ConversionResult toBlockBeans(@Nullable Context context,
                                                @Nullable List<GameBlockItem> rootChain) {
        return toBlockBeans(context, rootChain, 10);
    }

    @NonNull
    public static ConversionResult toBlockBeans(@Nullable Context context,
                                                @Nullable List<GameBlockItem> rootChain,
                                                int minStartId) {
        ConversionResult result = new ConversionResult();
        int safeStart = Math.max(Math.max(10, minStartId), findMaxLiteralAtRef(rootChain) + 1);
        int[] nextId = new int[]{safeStart};
        if (rootChain != null && !rootChain.isEmpty()) {
            result.rootNextId = emitChain(context, rootChain, result.beans, nextId);
        }
        result.nextAvailableId = nextId[0];
        return result;
    }

    private static int emitChain(@Nullable Context context,
                                 @Nullable List<GameBlockItem> chain,
                                 @NonNull ArrayList<BlockBean> out,
                                 @NonNull int[] nextId) {
        if (chain == null || chain.isEmpty()) {
            return -1;
        }
        List<GameBlockItem> valid = new ArrayList<>();
        for (GameBlockItem item : chain) {
            if (item != null && !isEmpty(item.opCode)) {
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

    private static void emitBlock(@Nullable Context context,
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
            String varName = (!item.parameters.isEmpty() && !isEmpty(item.parameters.get(0)))
                    ? item.parameters.get(0)
                    : ((!isEmpty(item.spec) && !item.spec.contains("%")) ? item.spec : "score");
            spec = varName;
        } else {
            // Preserve legacy multi-arity specs when item carries more placeholders/params than def.spec
            int defPlaceholders = def != null ? GameBlockCatalog.countPlaceholders(def.spec) : 0;
            int itemPlaceholders = !isEmpty(item.spec) ? GameBlockCatalog.countPlaceholders(item.spec) : 0;
            boolean keepLegacySpec = def != null
                    && itemPlaceholders > defPlaceholders
                    && item.parameters != null
                    && item.parameters.size() >= itemPlaceholders;
            if (keepLegacySpec) {
                spec = item.spec;
            } else {
                spec = def != null
                        ? def.spec
                        : (!isEmpty(item.spec) ? item.spec : opCode);
            }
            int pCount = GameBlockCatalog.countPlaceholders(spec);
            for (int p = 0; p < pCount; p++) {
                GameBlockItem nested = item.getParamBlock(p);
                if (nested != null && !isEmpty(nested.opCode)) {
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

        BlockBean bean;
        if (context != null) {
            bean = new BlockBean(String.valueOf(id), spec, type, "", opCode);
        } else {
            bean = new BlockBean();
            bean.id = String.valueOf(id);
            bean.spec = spec;
            bean.type = type;
            bean.typeName = "";
            bean.opCode = opCode;
        }
        int rawColor = def != null
                ? def.color
                : (item.color != 0 ? item.color : GameBlockCatalog.COLOR_CONTROL);
        bean.color = context != null ? MaterialColors.harmonizeWithPrimary(context, rawColor) : rawColor;
        bean.parameters = params;
        bean.subStack1 = emitChain(context, item.subStack1, out, nextId);
        bean.subStack2 = emitChain(context, item.subStack2, out, nextId);
        bean.nextBlock = nextBlockId;
        out.add(bean);
    }

    /**
     * Instantiates and wires a collection of {@link BlockBean}s onto {@code pane} safely,
     * shared by initial pane load, subtree duplication, nested block insertion, and floating stack restore.
     */
    @NonNull
    public static Map<Integer, BlockView> materializeBeans(@NonNull Context context,
                                                           @NonNull BlockPane pane,
                                                           @Nullable List<BlockBean> beans,
                                                           @Nullable View.OnTouchListener touchListener,
                                                           boolean measureNow) {
        Map<Integer, BlockView> viewById = new HashMap<>();
        if (beans == null || beans.isEmpty()) {
            return viewById;
        }
        int maxId = pane.g;
        for (BlockBean bean : beans) {
            if (bean == null || isEmpty(bean.id)) {
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
            if (bean == null || isEmpty(bean.id)) {
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
                    int childId = parseBlockRefId(val);
                    BlockView child = childId >= 0 ? viewById.get(childId) : null;
                    if (child != null && child != parent && slot instanceof BlockCanvasView) {
                        parent.a((BlockCanvasView) slot, child);
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

        if (measureNow) {
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
        }
        return viewById;
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

        Map<Integer, BlockView> viewById = materializeBeans(context, pane, beans, touchListener, false);

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

    /**
     * Populates detached/floating stacks persisted on {@code GameScriptNode.floatingStacks} onto {@code pane}.
     */
    public static void populateFloatingStacks(@NonNull Context context,
                                              @NonNull BlockPane pane,
                                              @Nullable List<ArrayList<GameBlockItem>> floatingStacks,
                                              @Nullable View.OnTouchListener touchListener) {
        if (floatingStacks == null || floatingStacks.isEmpty()) {
            return;
        }
        for (ArrayList<GameBlockItem> stack : floatingStacks) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            ConversionResult conv = toBlockBeans(context, stack, Math.max(10, pane.g));
            if (conv.beans.isEmpty() || conv.rootNextId < 0) {
                continue;
            }
            Map<Integer, BlockView> created = materializeBeans(context, pane, conv.beans, touchListener, true);
            BlockView top = created.get(conv.rootNextId);
            if (top != null) {
                GameBlockItem head = stack.get(0);
                int fx = head != null && head.freeX > 0 ? head.freeX : 48;
                int fy = head != null && head.freeY > 0 ? head.freeY : 220;
                top.setX(fx);
                top.setY(fy);
                top.k();
            }
        }
        pane.b();
    }

    @NonNull
    public static ArrayList<GameBlockItem> fromBlockPane(@NonNull BlockPane pane) {
        BlockView root = pane.getRoot();
        if (root == null || root.ha < 0) {
            return new ArrayList<>();
        }
        @SuppressWarnings("unchecked")
        ArrayList<BlockBean> flat = pane.getBlocks();
        return fromBlockBeans(flat, root.ha);
    }

    /**
     * Extracts all detached/floating block stacks sitting on {@code pane} (preserving {@code freeX}/{@code freeY}).
     */
    @NonNull
    public static ArrayList<ArrayList<GameBlockItem>> extractFloatingStacksFromPane(@NonNull BlockPane pane) {
        ArrayList<ArrayList<GameBlockItem>> stacks = new ArrayList<>();
        BlockView root = pane.getRoot();
        for (int i = 0; i < pane.getChildCount(); i++) {
            View child = pane.getChildAt(i);
            if (!(child instanceof BlockView)) {
                continue;
            }
            BlockView bv = (BlockView) child;
            if (bv == root || bv.ea || bv.E != null) {
                continue;
            }
            int topId;
            try {
                topId = Integer.parseInt(String.valueOf(bv.getTag()));
            } catch (Exception e) {
                continue;
            }
            @SuppressWarnings("unchecked")
            ArrayList<BlockView> sub = bv.getAllChildren();
            if (sub == null || sub.isEmpty()) {
                continue;
            }
            ArrayList<BlockBean> subBeans = new ArrayList<>(sub.size());
            for (BlockView sb : sub) {
                if (sb != null && sb.getBean() != null) {
                    subBeans.add(sb.getBean().clone());
                }
            }
            ArrayList<GameBlockItem> converted = fromBlockBeans(subBeans, topId);
            if (!converted.isEmpty()) {
                converted.get(0).freeX = Math.max(0, Math.round(bv.getX()));
                converted.get(0).freeY = Math.max(0, Math.round(bv.getY()));
                stacks.add(converted);
            }
        }
        return stacks;
    }

    /**
     * Pure JVM-testable converter from a flat {@link BlockBean} list to a {@link GameBlockItem} chain.
     * Prevents literal {@code "@12"} strings from hijacking statement blocks by verifying structural ownership.
     */
    @NonNull
    public static ArrayList<GameBlockItem> fromBlockBeans(@Nullable List<BlockBean> flat, int startId) {
        ArrayList<GameBlockItem> out = new ArrayList<>();
        if (flat == null || flat.isEmpty() || startId < 0) {
            return out;
        }
        Map<Integer, BlockBean> byId = new HashMap<>();
        for (BlockBean b : flat) {
            if (b != null && !isEmpty(b.id)) {
                try {
                    byId.put(Integer.parseInt(b.id), b);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        // Collect all statement-chain IDs reachable via nextBlock/subStack1/subStack2 so literal "@<id>"
        // text parameters can never hijack an unrelated statement block.
        Set<Integer> statementIds = new HashSet<>();
        collectStatementChainIds(startId, byId, statementIds);

        Set<Integer> visited = new HashSet<>();
        readChain(startId, byId, statementIds, out, visited);
        for (GameBlockItem item : out) {
            GameBlockCatalog.syncNestedExpressions(item);
        }
        return out;
    }

    private static void collectStatementChainIds(int startId,
                                                 @NonNull Map<Integer, BlockBean> byId,
                                                 @NonNull Set<Integer> outIds) {
        int curId = startId;
        while (curId >= 0 && outIds.add(curId)) {
            BlockBean bean = byId.get(curId);
            if (bean == null) {
                break;
            }
            if (bean.subStack1 >= 0) {
                collectStatementChainIds(bean.subStack1, byId, outIds);
            }
            if (bean.subStack2 >= 0) {
                collectStatementChainIds(bean.subStack2, byId, outIds);
            }
            curId = bean.nextBlock;
        }
    }

    private static void readChain(int startId,
                                  @NonNull Map<Integer, BlockBean> byId,
                                  @NonNull Set<Integer> statementIds,
                                  @NonNull ArrayList<GameBlockItem> out,
                                  @NonNull Set<Integer> visited) {
        int curId = startId;
        while (curId >= 0 && !visited.contains(curId)) {
            BlockBean bean = byId.get(curId);
            if (bean == null) {
                break;
            }
            visited.add(curId);
            GameBlockItem item = singleFromBean(bean, byId, statementIds, visited);
            if (item != null) {
                out.add(item);
            }
            curId = bean.nextBlock;
        }
    }

    @Nullable
    private static GameBlockItem singleFromBean(@NonNull BlockBean bean,
                                                @NonNull Map<Integer, BlockBean> byId,
                                                @NonNull Set<Integer> statementIds,
                                                @NonNull Set<Integer> visited) {
        if (isEmpty(bean.opCode)) {
            return null;
        }
        GameBlockCatalog.BlockDef def = GameBlockCatalog.findByOpCode(bean.opCode);
        // Never leak transient Material You harmonized theme colors into persisted game_project.json
        int canonicalColor = def != null
                ? def.color
                : (bean.color != 0 ? bean.color : GameBlockCatalog.COLOR_CONTROL);
        String canonicalSpec = "getVar".equals(bean.opCode)
                ? (def != null ? def.spec : "%s")
                : (bean.spec != null ? bean.spec : (def != null ? def.spec : ""));

        GameBlockItem item = new GameBlockItem(
                bean.id != null ? bean.id : "",
                bean.opCode,
                canonicalSpec,
                bean.type != null ? bean.type : " ",
                canonicalColor,
                new String[0]);
        item.ensureInitialized();

        if ("getVar".equals(bean.opCode)) {
            if (bean.parameters != null && !bean.parameters.isEmpty() && !isEmpty(bean.parameters.get(0))) {
                item.parameters.add(bean.parameters.get(0));
            } else {
                item.parameters.add(bean.spec != null ? bean.spec : "score");
            }
        } else if (bean.parameters != null) {
            for (int i = 0; i < bean.parameters.size(); i++) {
                String p = bean.parameters.get(i);
                GameBlockItem nestedItem = null;
                int childId = parseBlockRefId(p);
                if (childId >= 0 && !visited.contains(childId) && !statementIds.contains(childId)) {
                    BlockBean childBean = byId.get(childId);
                    if (childBean != null) {
                        visited.add(childId);
                        nestedItem = singleFromBean(childBean, byId, statementIds, visited);
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
            readChain(bean.subStack1, byId, statementIds, item.subStack1, visited);
        }
        if (bean.subStack2 >= 0) {
            readChain(bean.subStack2, byId, statementIds, item.subStack2, visited);
        }
        return item;
    }

    @NonNull
    public static String normalizeBlockType(@Nullable String rawType,
                                            @Nullable GameBlockCatalog.BlockDef def) {
        String t = !isEmpty(rawType) ? rawType : (def != null ? def.type : " ");
        if ("n".equals(t)) {
            return "d";
        }
        if ("v".equals(t) || isEmpty(t)) {
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
