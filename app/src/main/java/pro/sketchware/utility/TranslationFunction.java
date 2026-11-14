package pro.sketchware.utility;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

import pro.sketchware.workspace.WorkspacePathManager;

public class TranslationFunction {
    private static final String TAG = "TranslationFunction";
    private static final String ASSET_PATH = "localization/strings.xml";
    private static TranslationFunction instance;
    private static final Map<Resources, Resources> WRAPPED_RESOURCES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private final Map<String, String> externalStrings;
    private final Resources originalResources;

    private TranslationFunction(Context context, Map<String, String> externalStrings) {
        this.externalStrings = externalStrings;
        this.originalResources = unwrapResources(context.getResources());
    }

    public static synchronized boolean initialize(Context context) {
        refreshProvidedStringsFile(context);
        Map<String, String> strings = loadExternalStrings();
        instance = new TranslationFunction(context.getApplicationContext(), strings);
        return !strings.isEmpty();
    }

    public static TranslationFunction get() {
        if (instance == null) {
            throw new IllegalStateException("TranslationFunction not initialized. Call initialize() first.");
        }
        return instance;
    }

    public boolean hasExternalTranslations() {
        return !externalStrings.isEmpty();
    }

    public static Resources wrapResources(Context context, Resources resources) {
        Resources rawResources = unwrapResources(resources);
        if (resources instanceof TranslationResources) {
            return resources;
        }
        synchronized (WRAPPED_RESOURCES) {
            Resources wrapped = WRAPPED_RESOURCES.get(rawResources);
            if (wrapped == null) {
                wrapped = new TranslationResources(context.getApplicationContext(), rawResources);
                WRAPPED_RESOURCES.put(rawResources, wrapped);
            }
            return wrapped;
        }
    }

    public static Resources unwrapResources(Resources resources) {
        if (resources instanceof TranslationResources) {
            return ((TranslationResources) resources).getBaseResources();
        }
        return resources;
    }

    static String getOverrideString(Context context, Resources resources, int resId) {
        TranslationFunction translationFunction = instance;
        if (translationFunction == null) {
            return null;
        }
        return translationFunction.findOverride(unwrapResources(resources), resId);
    }

    private String findOverride(Resources resources, int resId) {
        try {
            if (!"string".equals(resources.getResourceTypeName(resId))) {
                return null;
            }
            String key = resources.getResourceEntryName(resId);
            String externalValue = externalStrings.get(key);
            return externalValue != null && !externalValue.isEmpty() ? externalValue : null;
        } catch (Resources.NotFoundException e) {
            return null;
        }
    }

    public String getString(int resId) {
        String externalValue = findOverride(originalResources, resId);
        return externalValue != null ? externalValue : originalResources.getString(resId);
    }

    public String getString(int resId, Object... formatArgs) {
        return formatString(Locale.getDefault(), getString(resId), formatArgs);
    }

    static String formatString(Locale locale, String template, Object... formatArgs) {
        if (formatArgs == null || formatArgs.length == 0) {
            return template;
        }
        try {
            return String.format(locale, template, formatArgs);
        } catch (IllegalFormatException e) {
            return template;
        }
    }

    public static String getString(Context context, int resId) {
        TranslationFunction translationFunction = instance;
        return translationFunction != null ? translationFunction.getString(resId) : context.getString(resId);
    }

    public static String getString(Context context, int resId, Object... formatArgs) {
        TranslationFunction translationFunction = instance;
        return translationFunction != null
                ? translationFunction.getString(resId, formatArgs)
                : formatString(Locale.getDefault(), context.getString(resId), formatArgs);
    }

    private static void refreshProvidedStringsFile(Context context) {
        try {
            File providedStringsFile = new File(WorkspacePathManager.m());
            ensureParentDirectoryExists(providedStringsFile);

            long assetSize = getAssetSize(context, ASSET_PATH);
            long currentFileSize = providedStringsFile.exists() ? providedStringsFile.length() : 0L;
            if (assetSize != currentFileSize) {
                copyAssetToFile(context, ASSET_PATH, providedStringsFile);
            }
        } catch (Exception e) {
            Log.e(TAG, "Couldn't extract default strings to storage", e);
        }
    }

    private static Map<String, String> loadExternalStrings() {
        File externalStringsFile = new File(WorkspacePathManager.l());
        if (!externalStringsFile.isFile()) {
            return new HashMap<>();
        }

        Map<String, String> strings = new HashMap<>();
        try (FileInputStream fis = new FileInputStream(externalStringsFile)) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(fis, null);

            int eventType = parser.getEventType();
            boolean inString = false;
            String name = null;
            StringBuilder text = new StringBuilder();

            while (eventType != XmlPullParser.END_DOCUMENT) {
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        if ("string".equals(parser.getName())) {
                            inString = true;
                            name = parser.getAttributeValue(null, "name");
                            text.setLength(0);
                        }
                        break;

                    case XmlPullParser.TEXT:
                        if (inString) {
                            text.append(parser.getText());
                        }
                        break;

                    case XmlPullParser.END_TAG:
                        if ("string".equals(parser.getName()) && inString && name != null) {
                            strings.put(name, text.toString().trim());
                            inString = false;
                            name = null;
                        }
                        break;

                    default:
                        break;
                }
                eventType = parser.next();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to read custom strings.xml", e);
        }
        return strings;
    }

    private static void ensureParentDirectoryExists(File file) throws IOException {
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            throw new IOException("Failed to create directory: " + parentDir.getAbsolutePath());
        }
    }

    private static long getAssetSize(Context context, String assetPath) throws IOException {
        long total = 0L;
        AssetManager assetManager = context.getAssets();
        try (InputStream in = assetManager.open(assetPath)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
            }
        }
        return total;
    }

    private static void copyAssetToFile(Context context, String assetPath, File destFile) throws IOException {
        AssetManager assetManager = context.getAssets();
        try (InputStream in = assetManager.open(assetPath);
             FileOutputStream out = new FileOutputStream(destFile)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        }
    }
}
