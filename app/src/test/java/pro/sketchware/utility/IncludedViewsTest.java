package pro.sketchware.utility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.besome.sketch.beans.ViewBean;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import mod.agus.jcoderz.beans.ViewBeans;

public class IncludedViewsTest {

    private final Map<String, List<ViewBean>> layouts = new HashMap<>();

    private static ViewBean view(String id, int type, String convert) {
        ViewBean bean = new ViewBean();
        bean.id = id;
        bean.type = type;
        bean.convert = convert;
        bean.inject = "";
        return bean;
    }

    private static ViewBean include(String id, String layout) {
        ViewBean bean = view(id, ViewBeans.VIEW_TYPE_LAYOUT_INCLUDE, "include");
        bean.inject = layout == null ? "" : "layout=\"@layout/" + layout + "\"";
        return bean;
    }

    private void layout(String name, ViewBean... views) {
        layouts.put(name + ".xml", new ArrayList<>(Arrays.asList(views)));
    }

    private IncludedViews.Result resolve(ViewBean... screen) {
        return IncludedViews.resolve("main.xml", Arrays.asList(screen), layouts::get);
    }

    private static List<String> ids(IncludedViews.Result result) {
        List<String> ids = new ArrayList<>();
        for (IncludedViews.Entry entry : result.entries) ids.add(entry.id);
        return ids;
    }

    private void cabecalho() {
        layout("cabecalho",
                view("linear1", ViewBean.VIEW_TYPE_LAYOUT_LINEAR, "LinearLayout"),
                view("button1", ViewBean.VIEW_TYPE_WIDGET_BUTTON, "Button"),
                view("edittext1", ViewBean.VIEW_TYPE_WIDGET_EDITTEXT, "EditText"),
                view("switch1", ViewBean.VIEW_TYPE_WIDGET_SWITCH, "Switch"));
    }

    @Test
    public void twoInstancesOfALayoutGetTheirOwnWidgets() {
        cabecalho();
        IncludedViews.Result result = resolve(include("include1", "cabecalho"), include("include2", "cabecalho"),
                view("button1", ViewBean.VIEW_TYPE_WIDGET_BUTTON, "Button"));
        assertEquals(Arrays.asList("include1_linear1", "include1_button1", "include1_edittext1", "include1_switch1",
                "include2_linear1", "include2_button1", "include2_edittext1", "include2_switch1"), ids(result));
        IncludedViews.Entry second = result.find("include2_button1");
        // Looked up inside its own instance, never a screen-wide search for button1
        assertEquals("findViewById(R.id.include2).findViewById(R.id.button1)", second.lookupExpression(""));
        assertEquals("_view.findViewById(R.id.include2).findViewById(R.id.button1)", second.lookupExpression("_view"));
        assertEquals("binding.include2.button1", second.bindingExpression());
        assertEquals("include2", second.includeId());
        assertEquals(4, result.under("include1").size());
        assertTrue(result.problems.isEmpty());
    }

    @Test
    public void nestedIncludesChainTheLookup() {
        cabecalho();
        layouts.get("cabecalho.xml").add(include("include3", "rodape"));
        layout("rodape", view("text_view", ViewBean.VIEW_TYPE_WIDGET_TEXTVIEW, "TextView"));
        IncludedViews.Entry entry = resolve(include("include1", "cabecalho")).find("include1_include3_text_view");
        assertEquals(Arrays.asList("include1", "include3", "text_view"), entry.path);
        assertEquals(Arrays.asList("main.xml", "cabecalho.xml", "rodape.xml"), entry.layouts);
        assertEquals("findViewById(R.id.include1).findViewById(R.id.include3).findViewById(R.id.text_view)",
                entry.lookupExpression(""));
        // View binding names: underscores become camel case, as in the generated binding classes
        assertEquals("binding.include1.include3.textView", entry.bindingExpression());
    }

    @Test
    public void aMissingLayoutIsReportedInsteadOfGuessed() {
        IncludedViews.Result result = resolve(include("include1", "apagado"));
        assertTrue(result.entries.isEmpty());
        assertEquals(1, result.problems.size());
        assertEquals(IncludedViews.Problem.Kind.MISSING_LAYOUT, result.problems.get(0).kind);
        assertEquals("apagado.xml", result.problems.get(0).detail);
    }

    @Test
    public void includeCyclesStop() {
        layout("a", include("to_b", "b"), view("text_a", ViewBean.VIEW_TYPE_WIDGET_TEXTVIEW, "TextView"));
        layout("b", include("to_a", "a"), view("text_b", ViewBean.VIEW_TYPE_WIDGET_TEXTVIEW, "TextView"));
        IncludedViews.Result result = resolve(include("include1", "a"));
        assertEquals(Arrays.asList("include1_to_b_text_b", "include1_text_a"), ids(result));
        assertEquals(IncludedViews.Problem.Kind.CYCLE, result.problems.get(0).kind);
        // A layout including itself
        layout("self", include("again", "self"), view("t", ViewBean.VIEW_TYPE_WIDGET_TEXTVIEW, "TextView"));
        assertEquals(Arrays.asList("include9_t"), ids(resolve(include("include9", "self"))));
        // The screen's own layout included in itself
        layout("main", view("x", ViewBean.VIEW_TYPE_WIDGET_TEXTVIEW, "TextView"));
        assertTrue(resolve(include("include1", "main")).entries.isEmpty());
    }

    @Test
    public void aNameTakenByAWidgetOfTheScreenIsNotShadowed() {
        cabecalho();
        IncludedViews.Result result = resolve(view("include1_button1", ViewBean.VIEW_TYPE_WIDGET_BUTTON, "Button"),
                include("include1", "cabecalho"));
        assertNull(result.find("include1_button1"));
        assertEquals(IncludedViews.Problem.Kind.NAME_TAKEN, result.problems.get(0).kind);
        assertEquals(3, result.entries.size());
    }

    @Test
    public void includesWithoutLayoutOrIdExposeNothing() {
        cabecalho();
        ViewBean noId = include("include1", "cabecalho");
        noId.inject += "\ntools:replace=\"android:id\"";
        assertTrue(resolve(include("include2", null), noId).entries.isEmpty());
        // A child whose id is dropped from the XML can't be found either
        layouts.get("cabecalho.xml").get(1).inject = "tools:replace=\"android:layout_width, android:id\"";
        assertNull(resolve(include("include3", "cabecalho")).find("include3_button1"));
    }

    @Test
    public void oldConvertedIncludesAndInternalViewsAreSkipped() {
        layout("lista",
                view("legacy", ViewBean.VIEW_TYPE_LAYOUT_LINEAR, "include"),
                view("_fab", ViewBean.VIEW_TYPE_WIDGET_FAB, ""),
                view("ok", ViewBean.VIEW_TYPE_WIDGET_TEXTVIEW, "TextView"));
        assertEquals(Arrays.asList("include1_ok"), ids(resolve(include("include1", "lista"))));
        // An old converted include on the screen doesn't expand
        ViewBean legacy = view("conv", ViewBean.VIEW_TYPE_LAYOUT_LINEAR, "include");
        legacy.inject = "layout=\"@layout/lista\"";
        assertTrue(resolve(legacy).entries.isEmpty());
    }

    @Test
    public void entriesAreCopiesThatLeaveTheIncludedLayoutAlone() {
        cabecalho();
        ViewBean button = layouts.get("cabecalho.xml").get(1);
        IncludedViews.Entry entry = resolve(include("include1", "cabecalho")).find("include1_button1");
        assertNotSame(button, entry.view);
        assertEquals("include1_button1", entry.view.id);
        assertEquals(ViewBean.VIEW_TYPE_WIDGET_BUTTON, entry.view.type);
        entry.view.text.text = "changed";
        assertEquals("button1", button.id);
        assertTrue(button.text.text == null || !button.text.text.equals("changed"));
        assertEquals("cabecalho", IncludedViews.includedLayout(include("i", "cabecalho")));
    }

    /** Deleting an include, or a widget anywhere on the way, takes the logic of the widgets reached through it. */
    @Test
    public void entriesKnowWhatTheyAreReachedThrough() {
        cabecalho();
        layouts.get("cabecalho.xml").add(include("include3", "rodape"));
        layout("rodape", view("text_view", ViewBean.VIEW_TYPE_WIDGET_TEXTVIEW, "TextView"));
        IncludedViews.Result result = resolve(include("include1", "cabecalho"), include("include2", "rodape"));
        IncludedViews.Entry nested = result.find("include1_include3_text_view");
        IncludedViews.Entry button = result.find("include1_button1");
        IncludedViews.Entry direct = result.find("include2_text_view");

        // The screen's include
        assertTrue(nested.reachedThrough("main.xml", Set.of("include1")));
        assertTrue(button.reachedThrough("main.xml", Set.of("include1")));
        assertFalse(direct.reachedThrough("main.xml", Set.of("include1")));
        // An include inside the included layout
        assertTrue(nested.reachedThrough("cabecalho.xml", Set.of("include3")));
        assertFalse(button.reachedThrough("cabecalho.xml", Set.of("include3")));
        // The widget itself, deleted in its own layout: every instance loses it
        assertTrue(nested.reachedThrough("rodape.xml", Set.of("text_view")));
        assertTrue(direct.reachedThrough("rodape.xml", Set.of("text_view")));
        assertTrue(button.reachedThrough("cabecalho.xml", Set.of("button1")));
        // The same id in another layout is another widget
        assertFalse(button.reachedThrough("rodape.xml", Set.of("button1")));
        assertFalse(button.reachedThrough("main.xml", Set.of("button1")));

        // Deleting a layout of the project
        assertTrue(nested.reachedThroughLayouts(Set.of("rodape.xml")));
        assertTrue(direct.reachedThroughLayouts(Set.of("rodape.xml")));
        assertFalse(button.reachedThroughLayouts(Set.of("rodape.xml")));
        assertTrue(button.reachedThroughLayouts(Set.of("cabecalho.xml")));
        // The screen's own layout isn't "reached through"
        assertFalse(button.reachedThroughLayouts(Set.of("main.xml")));
    }
}
