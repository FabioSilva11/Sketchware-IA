package pro.sketchware.ai.config;

import android.text.TextUtils;

import java.util.Locale;

public final class DeviceLanguage {
    private DeviceLanguage() {
    }

    public static String responseInstruction() {
        Locale locale = Locale.getDefault();
        String displayLanguage = locale.getDisplayLanguage(Locale.ENGLISH);
        String displayCountry = locale.getDisplayCountry(Locale.ENGLISH);
        String languageTag = locale.toLanguageTag();

        StringBuilder language = new StringBuilder();
        if (!TextUtils.isEmpty(displayLanguage)) {
            language.append(displayLanguage);
        } else if (!TextUtils.isEmpty(locale.getLanguage())) {
            language.append(locale.getLanguage());
        } else {
            language.append("the user's device language");
        }

        if (!TextUtils.isEmpty(displayCountry)) {
            language.append(" (").append(displayCountry).append(")");
        }
        if (!TextUtils.isEmpty(languageTag)) {
            language.append(" [").append(languageTag).append("]");
        }

        return "Respond to the user in the device language: " + language
                + ". Keep code, file paths, commands, API names, class names, and literal errors unchanged.";
    }
}
