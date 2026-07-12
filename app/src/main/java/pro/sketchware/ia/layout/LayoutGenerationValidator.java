package pro.sketchware.ia.layout;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import pro.sketchware.tools.ViewBeanParser;

public final class LayoutGenerationValidator {
    public static final class Result {
        public final List<String> errors;
        Result(List<String> errors) { this.errors = errors; }
        public boolean isValid() { return errors.isEmpty(); }
        public String repairPrompt() { return String.join("\n- ", errors); }
    }

    private final LayoutComponentRegistry registry;
    private final Set<String> resources;

    public LayoutGenerationValidator(LayoutComponentRegistry registry, List<String> resources) {
        this.registry = registry;
        this.resources = new HashSet<>(resources == null ? List.of() : resources);
    }

    public Result validate(String xml) {
        List<String> errors = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        try {
            XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(new StringReader(xml));
            int depth = 0;
            while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if (!registry.supports(tag)) errors.add("Unsupported component: " + tag);
                    String id = parser.getAttributeValue(null, "android:id");
                    if (depth > 0 && (id == null || id.trim().isEmpty())) errors.add("Missing android:id on " + tag);
                    if (id != null && !ids.add(id)) errors.add("Duplicate id: " + id);
                    for (int i = 0; i < parser.getAttributeCount(); i++) {
                        String value = parser.getAttributeValue(i);
                        if (value != null && value.startsWith("@drawable/") && !resources.contains(value)) {
                            errors.add("Unknown drawable resource: " + value);
                        }
                    }
                    depth++;
                } else if (parser.getEventType() == XmlPullParser.END_TAG) {
                    depth--;
                }
                parser.next();
            }
            ViewBeanParser beanParser = new ViewBeanParser(xml);
            beanParser.setSkipRoot(true);
            if (beanParser.parse().isEmpty()) errors.add("Layout has no editable child views");
        } catch (Exception e) {
            errors.add("Parser rejected layout: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
        return new Result(errors);
    }
}
