package pro.sketchware.ia;

import androidx.annotation.NonNull;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import com.besome.sketch.beans.ViewBean;
import mod.agus.jcoderz.beans.ViewBeans;
import pro.sketchware.network.GroqClient;

/**
 * High-level helper that asks AI models to generate Android XML layout code
 * constrained to the components available in the Sketchware palette.
 * Supports multiple AI providers: Groq, OpenAI, and Claude.
 */
public final class AiCodeGenerator {

    private final GroqClient groqClient;

    public AiCodeGenerator() {
        this.groqClient = GroqClient.getInstance();
    }

    public String generateLayoutXml(@NonNull String userPrompt) throws IOException, JSONException {
        return generateLayoutXml(userPrompt, null, null);
    }

    public String generateLayoutXml(@NonNull String userPrompt, String currentLayoutXml, Map<String, String> stylePreferences) throws IOException, JSONException {
        String system = buildSystemPrompt(currentLayoutXml, stylePreferences);
        
        // Combine system prompt and user prompt
        String fullPrompt = system + "\n\nUser Request: " + userPrompt;
        
        // Use GroqClient to generate layout
        return groqClient.sendMessage(fullPrompt);
    }

    private String buildSystemPrompt() {
        return buildSystemPrompt(null, null);
    }

    private String buildSystemPrompt(String currentLayoutXml, Map<String, String> stylePreferences) {
        StringJoiner sj = new StringJoiner("\n");
        
        sj.add("You are an assistant that generates Android XML layouts compatible with Sketchware Pro.");
        sj.add("");
        sj.add("Objective:");
        sj.add("- Create simple, beautiful and functional layouts.");
        sj.add("- Always respect the limits of the Sketchware component palette.");
        sj.add("- Apply basic Material Design principles: alignment, margins, spacing and readable text sizes.");
        sj.add("");
        sj.add("Rules:");
        sj.add("1. The root layout must ALWAYS be a LinearLayout with:");
        sj.add("   android:layout_width=\"match_parent\"");
        sj.add("   android:layout_height=\"match_parent\"");
        sj.add("   android:orientation=\"vertical\"");
        sj.add("");
        sj.add("2. Use only attributes supported by the Sketchware palette:");
        sj.add("   - **Common to all widgets**:");
        sj.add("     android:id, android:layout_width, android:layout_height, android:layout_margin, android:padding, android:background, android:enabled, android:clickable, android:alpha, android:translationX, android:translationY, android:scaleX, android:scaleY");
        sj.add("");
        sj.add("   - **RelativeLayout**:");
        sj.add("     android:layout_alignParentTop/Bottom/Start/End, android:layout_alignTop/Bottom/Start/End, android:layout_toStartOf/toEndOf, android:layout_centerInParent, android:layout_centerHorizontal, android:layout_centerVertical");
        sj.add("");
        sj.add("   - **TextView/EditText/Button**:");
        sj.add("     android:text, android:textSize, android:textStyle, android:textColor, android:hint, android:textColorHint");
        sj.add("");
        sj.add("   - **ImageView**:");
        sj.add("     android:src, android:scaleType, android:contentDescription");
        sj.add("");
        sj.add("   - **CheckBox/RadioButton/Switch**:");
        sj.add("     android:text, android:checked");
        sj.add("");
        sj.add("   - **SeekBar/ProgressBar**:");
        sj.add("     android:max, android:progress, android:indeterminate");
        sj.add("");
        sj.add("   - **ListView**:");
        sj.add("     android:dividerHeight, android:choiceMode");
        sj.add("");
        sj.add("   - **Spinner**:");
        sj.add("     android:prompt");
        sj.add("");
        sj.add("   - **CalendarView**:");
        sj.add("     android:firstDayOfWeek");
        sj.add("");
        sj.add("3. Always assign **android:id** to each interactive view (ex: @+id/button1, @+id/textview1).");
        sj.add("");
        sj.add("4. Layouts must be visually balanced:");
        sj.add("   - Use consistent margins and paddings (ex: 8dp, 16dp).");
        sj.add("   - Use textSize in sp.");
        sj.add("   - Simple colors in hex (#000000, #FFFFFF, #2196F3).");
        sj.add("");
        sj.add("5. If a current layout is provided, preserve existing views and IDs.");
        sj.add("   Only modify or add as requested by the user.");
        sj.add("");
        sj.add("6. Use ONLY the components supported by the palette:");
        sj.add("   - **Layouts**: LinearLayout, RelativeLayout, ScrollView, HorizontalScrollView, CardView, TextInputLayout, TabLayout, BottomNavigationView, CollapsingToolbarLayout, SwipeRefreshLayout, RadioGroup");
        sj.add("   - **Widgets**: Button, TextView, EditText, ImageView, CheckBox, RadioButton, Switch, SeekBar, ProgressBar, ListView, Spinner, GridView, RecyclerView, CalendarView, DatePicker, TimePicker, DigitalClock, AnalogClock, WebView, MapView, VideoView, AdView, SearchView, RatingBar, AutoCompleteTextView, MultiAutoCompleteTextView, ViewPager, BadgeView, PatternLockView, WaveSideBar, SignInButton, MaterialButton, CircleImageView, LottieAnimation, YoutubePlayer, OTPView, CodeView");
        sj.add("");
        sj.add("7. The output must be only a valid XML snippet.");
        sj.add("   ❌ Do not include explanations.");
        sj.add("   ❌ Do not use code blocks.");
        sj.add("   ✅ Only clean XML.");
        sj.add("");
        sj.add("Design Guidelines:");
        sj.add("- Structure content in column (vertical LinearLayout).");
        sj.add("- Use nested layouts when necessary to better organize.");
        sj.add("- Maintain clear hierarchy: titles → content → buttons.");
        sj.add("- Buttons should be large and easy to touch (minimum height ~48dp).");
        sj.add("");
        sj.add("Example:");
        sj.add("<LinearLayout");
        sj.add("    android:layout_width=\"match_parent\"");
        sj.add("    android:layout_height=\"match_parent\"");
        sj.add("    android:orientation=\"vertical\">");
        sj.add("");
        sj.add("    <TextView");
        sj.add("        android:id=\"@+id/titulo\"");
        sj.add("        android:layout_width=\"wrap_content\"");
        sj.add("        android:layout_height=\"wrap_content\"");
        sj.add("        android:text=\"Hello World\"");
        sj.add("        android:textSize=\"20sp\"");
        sj.add("        android:textColor=\"#000000\"");
        sj.add("        android:layout_margin=\"16dp\" />");
        sj.add("");
        sj.add("    <Button");
        sj.add("        android:id=\"@+id/botao1\"");
        sj.add("        android:layout_width=\"match_parent\"");
        sj.add("        android:layout_height=\"wrap_content\"");
        sj.add("        android:text=\"Click Here\"");
        sj.add("        android:layout_margin=\"16dp\"");
        sj.add("        android:background=\"#2196F3\"");
        sj.add("        android:textColor=\"#FFFFFF\" />");
        sj.add("");
        sj.add("</LinearLayout>");
        
        // Add current layout context if available
        if (currentLayoutXml != null && !currentLayoutXml.trim().isEmpty()) {
            sj.add("");
            sj.add("CURRENT LAYOUT CONTEXT:");
            sj.add("The user is currently editing this layout. Preserve existing elements and IDs when possible:");
            sj.add(currentLayoutXml);
            sj.add("");
            sj.add("IMPORTANT: When modifying the layout, try to preserve existing views and their IDs.");
            sj.add("Only add new elements or modify existing ones as requested by the user.");
        }
        
        // Add style preferences if available
        if (stylePreferences != null && !stylePreferences.isEmpty()) {
            sj.add("");
            sj.add("STYLE PREFERENCES:");
            sj.add("Maintain these style characteristics throughout the layout:");
            for (Map.Entry<String, String> entry : stylePreferences.entrySet()) {
                sj.add("- " + entry.getKey() + ": " + entry.getValue());
            }
        }
        
        return sj.toString();
    }

    
    /**
     * Extrai os tipos suportados pelo ViewBeanParser para fornecer contexto mais preciso à AI
     */
    private Map<String, List<String>> getViewBeanParserSupportedTypes() {
        Map<String, List<String>> types = new HashMap<>();
        List<String> layouts = new ArrayList<>();
        List<String> widgets = new ArrayList<>();
        
        // Tipos básicos do ViewBean
        layouts.add("LinearLayout");
        layouts.add("RelativeLayout");
        layouts.add("HorizontalScrollView");
        layouts.add("ScrollView");
        
        widgets.add("Button");
        widgets.add("TextView");
        widgets.add("EditText");
        widgets.add("ImageView");
        widgets.add("WebView");
        widgets.add("ProgressBar");
        widgets.add("ListView");
        widgets.add("Spinner");
        widgets.add("CheckBox");
        widgets.add("Switch");
        widgets.add("SeekBar");
        widgets.add("CalendarView");
        widgets.add("AdView");
        widgets.add("MapView");
        
        // Tipos estendidos do ViewBeans
        widgets.add("RadioButton");
        widgets.add("RatingBar");
        widgets.add("VideoView");
        widgets.add("SearchView");
        widgets.add("AutoCompleteTextView");
        widgets.add("MultiAutoCompleteTextView");
        widgets.add("GridView");
        widgets.add("AnalogClock");
        widgets.add("DatePicker");
        widgets.add("TimePicker");
        widgets.add("DigitalClock");
        widgets.add("ViewPager");
        widgets.add("BadgeView");
        widgets.add("PatternLockView");
        widgets.add("WaveSideBar");
        widgets.add("MaterialButton");
        widgets.add("SignInButton");
        widgets.add("CircleImageView");
        widgets.add("LottieAnimationView");
        widgets.add("YoutubePlayerView");
        widgets.add("OTPView");
        widgets.add("CodeView");
        widgets.add("RecyclerView");
        
        layouts.add("TabLayout");
        layouts.add("BottomNavigationView");
        layouts.add("CardView");
        layouts.add("CollapsingToolbarLayout");
        layouts.add("TextInputLayout");
        layouts.add("SwipeRefreshLayout");
        layouts.add("RadioGroup");
        
        // Tipos especiais que o ViewBeanParser pode mapear
        widgets.add("ImageButton");
        layouts.add("NestedScrollView");
        widgets.add("MaterialSwitch");
        widgets.add("TextInputEditText");
        
        types.put("layouts", layouts);
        types.put("widgets", widgets);
        
        return types;
    }
    
}


