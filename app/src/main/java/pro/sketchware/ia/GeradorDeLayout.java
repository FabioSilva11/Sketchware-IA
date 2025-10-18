package pro.sketchware.ia;

import pro.sketchware.network.GroqClient;
import java.io.IOException;
import java.util.*;

public final class GeradorDeLayout {

    private final String texto;

    public GeradorDeLayout(String texto) {
        this.texto = texto;
    }

    private String montarPromptBase() {
        StringJoiner sj = new StringJoiner("\n");

        sj.add("You are an assistant that generates valid Android XML layouts for Sketchware.");
        sj.add("Goal: Simple, functional, Material Design-like layouts using only Sketchware components.");
        sj.add("");
        sj.add("== RULES ==");
        sj.add("1. Root layout: <LinearLayout> vertical, match_parent both dimensions.");
        sj.add("2. Use only Sketchware-supported attributes:");
        sj.add("   - Common: id, layout_width/height, layout_margin, padding, background, enabled, clickable, alpha, translationX/Y, scaleX/Y");
        sj.add("   - TextView/EditText/Button: text, textSize, textStyle, textColor, hint, textColorHint");
        sj.add("   - ImageView: src, scaleType, contentDescription");
        sj.add("   - CheckBox/RadioButton/Switch: text, checked");
        sj.add("   - ProgressBar/SeekBar: max, progress, indeterminate");
        sj.add("   - ListView: dividerHeight, choiceMode");
        sj.add("   - Spinner: prompt");
        sj.add("   - CalendarView: firstDayOfWeek");
        sj.add("");
        sj.add("3. Assign android:id to all interactive views (e.g. @+id/button1).");
        sj.add("4. Visual balance:");
        sj.add("   - Margins/paddings: 8dp or 16dp");
        sj.add("   - textSize in sp");
        sj.add("   - Colors: #000000, #FFFFFF, #2196F3");
        sj.add("");
        sj.add("5. Use only these components:");
        Map<String, List<String>> supported = getViewBeanParserSupportedTypes();
        sj.add("   - Layouts: " + String.join(", ", supported.get("layouts")));
        sj.add("   - Widgets: " + String.join(", ", supported.get("widgets")));
        sj.add("");
        sj.add("6. Output strictly XML — no explanations, comments, or code blocks.");
        sj.add("");
        sj.add("== DESIGN GUIDELINES ==");
        sj.add("- Structure vertically (LinearLayout).");
        sj.add("- Nest layouts when needed.");
        sj.add("- Keep hierarchy clear: title → content → actions.");
        sj.add("- Buttons: minimum height 48dp.");
        sj.add("");
        sj.add("== EXAMPLE ==");
        sj.add("<LinearLayout");
        sj.add("    android:layout_width=\"match_parent\"");
        sj.add("    android:layout_height=\"match_parent\"");
        sj.add("    android:orientation=\"vertical\">");
        sj.add("");
        sj.add("    <TextView");
        sj.add("        android:id=\"@+id/title\"");
        sj.add("        android:layout_width=\"wrap_content\"");
        sj.add("        android:layout_height=\"wrap_content\"");
        sj.add("        android:text=\"Hello World\"");
        sj.add("        android:textSize=\"20sp\"");
        sj.add("        android:textColor=\"#000000\"");
        sj.add("        android:layout_margin=\"16dp\" />");
        sj.add("");
        sj.add("    <Button");
        sj.add("        android:id=\"@+id/button1\"");
        sj.add("        android:layout_width=\"match_parent\"");
        sj.add("        android:layout_height=\"wrap_content\"");
        sj.add("        android:text=\"Click Here\"");
        sj.add("        android:layout_margin=\"16dp\"");
        sj.add("        android:background=\"#2196F3\"");
        sj.add("        android:textColor=\"#FFFFFF\" />");
        sj.add("");
        sj.add("</LinearLayout>");

        return sj.toString();
    }

    private Map<String, List<String>> getViewBeanParserSupportedTypes() {
        Map<String, List<String>> types = new HashMap<>();
        List<String> layouts = Arrays.asList(
            "LinearLayout", "RelativeLayout", "HorizontalScrollView", "ScrollView",
            "TabLayout", "BottomNavigationView", "CardView", "CollapsingToolbarLayout",
            "TextInputLayout", "SwipeRefreshLayout", "RadioGroup", "NestedScrollView"
        );

        List<String> widgets = Arrays.asList(
            "Button", "TextView", "EditText", "ImageView", "WebView", "ProgressBar", "ListView",
            "Spinner", "CheckBox", "Switch", "SeekBar", "CalendarView", "AdView", "MapView",
            "RadioButton", "RatingBar", "VideoView", "SearchView", "AutoCompleteTextView",
            "MultiAutoCompleteTextView", "GridView", "AnalogClock", "DatePicker", "TimePicker",
            "DigitalClock", "ViewPager", "BadgeView", "PatternLockView", "WaveSideBar",
            "MaterialButton", "SignInButton", "CircleImageView", "LottieAnimationView",
            "YoutubePlayerView", "OTPView", "CodeView", "RecyclerView", "ImageButton",
            "MaterialSwitch", "TextInputEditText"
        );

        types.put("layouts", layouts);
        types.put("widgets", widgets);
        return types;
    }

    public String getTexto() {
        return texto;
    }

    public String gerarLayout() throws IOException {
        String prompt = montarPromptBase() + "\n\nUser Request:\n" + texto;
        return GroqClient.getInstance().sendMessage(prompt);
    }
}
