package mod.jbk.build.compiler.manifest;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * Merges the AndroidManifest.xml files of a project's libraries into the app's manifest,
 * following the default rules of the Gradle manifest merger: the app manifest wins,
 * elements with the same {@code android:name} are merged attribute-by-attribute and
 * child-by-child, and libraries may drop elements with {@code tools:node="remove"}.
 * <p>
 * Libraries declare their own activities, services, providers, receivers, permissions and
 * {@code <queries>} (e.g. Firebase's component registrars or WorkManager's schedulers), so
 * merging them keeps generated apps in sync with whichever library versions are bundled.
 */
public final class LibraryManifestMerger {
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String TOOLS_NS = "http://schemas.android.com/tools";
    private static final Set<String> ROOT_LEVEL_DECLARATIONS = Set.of(
            "uses-permission", "uses-permission-sdk-23", "permission", "permission-group",
            "permission-tree", "uses-feature");

    private LibraryManifestMerger() {
    }

    /**
     * @param appManifest       The manifest Sketchware generated for the project
     * @param libraryManifests  Manifests of the libraries the project uses, missing files are skipped
     * @param applicationId     Replaces {@code ${applicationId}} placeholders in library manifests
     * @param output            Where to write the merged manifest
     * @return The highest {@code minSdkVersion} any merged library declares, or 0 if none does
     */
    public static int merge(File appManifest, List<File> libraryManifests, String applicationId, File output) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();

            // A hand-edited manifest may use tools:/app: without binding them ("Undefined Prefix: tools").
            String appContent = ManifestNamespaces.declareUsedPrefixes(
                    new String(Files.readAllBytes(appManifest.toPath()), StandardCharsets.UTF_8));
            Document app = builder.parse(new InputSource(new StringReader(appContent)));
            Element appRoot = app.getDocumentElement();

            List<Document> libraries = new ArrayList<>();
            for (File manifest : libraryManifests) {
                if (manifest.isFile()) {
                    String content = new String(Files.readAllBytes(manifest.toPath()), StandardCharsets.UTF_8)
                            .replace("${applicationId}", applicationId);
                    content = ManifestNamespaces.declareUsedPrefixes(content);
                    libraries.add(builder.parse(new InputSource(new StringReader(content))));
                }
            }

            Set<String> removed = new HashSet<>();
            int requiredMinSdk = 0;
            for (Document library : libraries) {
                for (Element element : childElements(library.getDocumentElement())) {
                    if (isRemoval(element)) {
                        removed.add(key(element));
                    } else if (element.getTagName().equals("uses-sdk")) {
                        requiredMinSdk = Math.max(requiredMinSdk, parseInt(element.getAttributeNS(ANDROID_NS, "minSdkVersion")));
                    }
                }
            }

            Element appApplication = firstChild(appRoot, "application");
            for (Document library : libraries) {
                for (Element element : childElements(library.getDocumentElement())) {
                    if (isRemoval(element) || removed.contains(key(element))) {
                        continue;
                    }
                    String tag = element.getTagName();
                    if (ROOT_LEVEL_DECLARATIONS.contains(tag)) {
                        if (findMatching(appRoot, element) == null) {
                            appRoot.insertBefore(importStripped(app, element), appApplication);
                        }
                    } else if (tag.equals("queries")) {
                        mergeQueries(app, appRoot, appApplication, element);
                    } else if (tag.equals("application") && appApplication != null) {
                        copyMissingAttributes(element, appApplication);
                        for (Element component : childElements(element)) {
                            if (!isRemoval(component) && !removed.contains(key(component))) {
                                mergeElement(app, appApplication, component);
                            }
                        }
                    }
                }
            }

            write(app, output);
            return requiredMinSdk;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to merge library manifests into " + appManifest + ": " + e.getMessage(), e);
        }
    }

    private static void mergeQueries(Document app, Element appRoot, Element appApplication, Element libraryQueries) {
        Element queries = firstChild(appRoot, "queries");
        if (queries == null) {
            queries = app.createElement("queries");
            appRoot.insertBefore(queries, appApplication);
        }
        Set<String> existing = new HashSet<>();
        for (Element query : childElements(queries)) {
            existing.add(canonical(query));
        }
        for (Element query : childElements(libraryQueries)) {
            if (existing.add(canonical(query))) {
                queries.appendChild(importStripped(app, query));
            }
        }
    }

    private static void mergeElement(Document app, Element parent, Element libraryElement) {
        Element existing = findMatching(parent, libraryElement);
        if (existing == null) {
            parent.appendChild(importStripped(app, libraryElement));
            return;
        }
        if (!hasName(libraryElement)) {
            // Unnamed elements (intent filters, actions, ...) only match when they're identical.
            return;
        }
        copyMissingAttributes(libraryElement, existing);
        for (Element child : childElements(libraryElement)) {
            mergeElement(app, existing, child);
        }
    }

    private static Element findMatching(Element parent, Element element) {
        boolean named = hasName(element);
        String canonical = named ? null : canonical(element);
        for (Element candidate : childElements(parent)) {
            if (!candidate.getTagName().equals(element.getTagName())) {
                continue;
            }
            if (named ? name(candidate).equals(name(element)) : canonical(candidate).equals(canonical)) {
                return candidate;
            }
        }
        return null;
    }

    private static void copyMissingAttributes(Element from, Element to) {
        NamedNodeMap attributes = from.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attribute = (Attr) attributes.item(i);
            String namespace = attribute.getNamespaceURI();
            if (ANDROID_NS.equals(namespace) && !to.hasAttributeNS(ANDROID_NS, attribute.getLocalName())) {
                to.setAttributeNS(ANDROID_NS, "android:" + attribute.getLocalName(), attribute.getValue());
            }
        }
    }

    private static Node importStripped(Document app, Element element) {
        Element imported = (Element) app.importNode(element, true);
        stripToolsAttributes(imported);
        return imported;
    }

    private static void stripToolsAttributes(Element element) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = attributes.getLength() - 1; i >= 0; i--) {
            Attr attribute = (Attr) attributes.item(i);
            boolean toolsDeclaration = "http://www.w3.org/2000/xmlns/".equals(attribute.getNamespaceURI())
                    && TOOLS_NS.equals(attribute.getValue());
            if (TOOLS_NS.equals(attribute.getNamespaceURI()) || toolsDeclaration) {
                element.removeAttributeNode(attribute);
            }
        }
        for (Element child : childElements(element)) {
            stripToolsAttributes(child);
        }
    }

    private static boolean isRemoval(Element element) {
        return "remove".equals(element.getAttributeNS(TOOLS_NS, "node"));
    }

    private static boolean hasName(Element element) {
        return element.hasAttributeNS(ANDROID_NS, "name");
    }

    private static String name(Element element) {
        return element.getAttributeNS(ANDROID_NS, "name");
    }

    private static String key(Element element) {
        return element.getTagName() + "|" + name(element);
    }

    private static String canonical(Element element) {
        StringBuilder builder = new StringBuilder(element.getTagName()).append('[');
        NamedNodeMap attributes = element.getAttributes();
        List<String> values = new ArrayList<>();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attribute = (Attr) attributes.item(i);
            if (!TOOLS_NS.equals(attribute.getNamespaceURI()) && !"http://www.w3.org/2000/xmlns/".equals(attribute.getNamespaceURI())) {
                values.add(attribute.getLocalName() + "=" + attribute.getValue());
            }
        }
        values.sort(null);
        builder.append(String.join(",", values)).append(']');
        for (Element child : childElements(element)) {
            builder.append(canonical(child));
        }
        return builder.append(';').toString();
    }

    private static Element firstChild(Element parent, String tag) {
        for (Element child : childElements(parent)) {
            if (child.getTagName().equals(tag)) {
                return child;
            }
        }
        return null;
    }

    private static List<Element> childElements(Element parent) {
        List<Element> elements = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element element) {
                elements.add(element);
            }
        }
        return elements;
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void write(Document document, File output) throws Exception {
        File parent = output.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Couldn't create directory " + parent);
        }
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "utf-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        try (OutputStream stream = new FileOutputStream(output)) {
            stream.write(writer.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
}
