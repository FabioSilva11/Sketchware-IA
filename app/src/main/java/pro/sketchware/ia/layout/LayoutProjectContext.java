package pro.sketchware.ia.layout;

import androidx.annotation.NonNull;

import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ProjectResourceBean;
import com.besome.sketch.editor.manage.library.material3.Material3LibraryManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import a.a.a.jC;
import a.a.a.wq;
import pro.sketchware.activities.resourceseditor.components.utils.StringsEditorManager;
import pro.sketchware.utility.FileUtil;

/** What the AI needs to know about the project to produce a layout that compiles. */
public final class LayoutProjectContext {

    /** File name of the layout being generated, without .xml. */
    public final String currentLayout;
    /** Other layouts that can be included. */
    public final List<String> layouts;
    /** "@drawable/name" of the project's images and vector drawables. */
    public final List<String> drawables;
    /** Names of colors in values/colors.xml. */
    public final List<String> colors;
    /** Keys of values/strings.xml. */
    public final List<String> strings;
    public final List<String> fonts;
    public final boolean appCompat;
    public final boolean material3;
    /** Current layout as a spec, when the user asked to edit it; empty otherwise. */
    public final String currentSpec;
    public final String referenceNotes;
    public final List<String> referenceImages;

    public LayoutProjectContext(String currentLayout, List<String> layouts, List<String> drawables,
                                List<String> colors, List<String> strings, List<String> fonts,
                                boolean appCompat, boolean material3, String currentSpec,
                                String referenceNotes, List<String> referenceImages) {
        this.currentLayout = currentLayout;
        this.layouts = immutable(layouts);
        this.drawables = immutable(drawables);
        this.colors = immutable(colors);
        this.strings = immutable(strings);
        this.fonts = immutable(fonts);
        this.appCompat = appCompat;
        this.material3 = material3;
        this.currentSpec = currentSpec == null ? "" : currentSpec;
        this.referenceNotes = referenceNotes == null ? "" : referenceNotes.trim();
        this.referenceImages = immutable(referenceImages);
    }

    /** Reads the project. Must run off the main thread (it reads resource files). */
    @NonNull
    public static LayoutProjectContext read(@NonNull String scId, @NonNull String xmlName, @NonNull String currentSpec,
                                            String referenceNotes, List<String> referenceImages) {
        String current = xmlName.endsWith(".xml") ? xmlName.substring(0, xmlName.length() - 4) : xmlName;
        TreeSet<String> layouts = new TreeSet<>();
        try {
            for (ProjectFileBean file : jC.b(scId).b()) layouts.add(file.getXmlName().replace(".xml", ""));
            for (ProjectFileBean file : jC.b(scId).c()) layouts.add(file.getXmlName().replace(".xml", ""));
        } catch (Exception ignored) {
        }
        layouts.remove(current);

        TreeSet<String> drawables = new TreeSet<>();
        try {
            ArrayList<ProjectResourceBean> images = jC.d(scId).b;
            if (images != null) {
                for (ProjectResourceBean image : images) {
                    if (image != null && image.resName != null && !image.resName.isBlank()) {
                        drawables.add("@drawable/" + image.resName.trim().replace(".9", ""));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        String resources = wq.b(scId) + "/files/resource/";
        for (String folder : new String[]{"drawable", "drawable-xhdpi"}) {
            ArrayList<String> files = new ArrayList<>();
            FileUtil.listDir(resources + folder, files);
            for (String path : files) {
                String name = new File(path).getName();
                int dot = name.indexOf('.');
                if (dot > 0) drawables.add("@drawable/" + name.substring(0, dot));
            }
        }

        List<String> colors = new ArrayList<>(xmlNames(FileUtil.readFileIfExist(resources + "values/colors.xml"), "color"));
        ArrayList<HashMap<String, Object>> stringList = new ArrayList<>();
        new StringsEditorManager().convertXmlStringsToListMap(FileUtil.readFileIfExist(resources + "values/strings.xml"), stringList);
        List<String> strings = new ArrayList<>();
        for (Map<String, Object> map : stringList) strings.add(String.valueOf(map.get("key")));
        if (!strings.contains("app_name")) strings.add("app_name");

        List<String> fonts = new ArrayList<>();
        ArrayList<String> fontFiles = new ArrayList<>();
        FileUtil.listDir(resources + "font", fontFiles);
        for (String path : fontFiles) {
            String name = new File(path).getName();
            int dot = name.lastIndexOf('.');
            fonts.add(dot > 0 ? name.substring(0, dot) : name);
        }

        boolean appCompat = false;
        boolean material3 = false;
        try {
            appCompat = jC.c(scId).c().isEnabled();
            material3 = appCompat && new Material3LibraryManager(scId).isMaterial3Enabled();
        } catch (Exception ignored) {
        }
        return new LayoutProjectContext(current, new ArrayList<>(layouts), new ArrayList<>(drawables), colors, strings, fonts,
                appCompat, material3, currentSpec, referenceNotes, referenceImages);
    }

    private static List<String> xmlNames(String xml, String tag) {
        List<String> names = new ArrayList<>();
        if (xml == null) return names;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("<" + tag + "\\s+name=\"([^\"]+)\"").matcher(xml);
        while (matcher.find()) names.add(matcher.group(1));
        return names;
    }

    public List<String> colorReferences() {
        List<String> references = new ArrayList<>();
        for (String color : colors) references.add("@color/" + color);
        return references;
    }

    private static List<String> immutable(List<String> source) {
        return source == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(source));
    }
}
