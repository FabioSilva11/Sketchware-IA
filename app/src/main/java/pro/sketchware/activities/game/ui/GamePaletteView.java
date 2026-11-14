package pro.sketchware.activities.game.ui;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.Context;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;
import pro.sketchware.activities.game.model.GameObjectBean;
import pro.sketchware.utility.SketchwareUtil;

/**
 * Compact vertical palette matching Sketchware's native PaletteWidget/widget_layout style,
 * supporting quick-tap creation and drag-and-drop onto the 2D Game Stage.
 */
public class GamePaletteView extends LinearLayout {

    public interface PaletteListener {
        void onQuickAddObject(String type);
    }

    private static class PaletteEntry {
        final String type;
        final String label;
        final int iconRes;
        final int accentColor;

        PaletteEntry(String type, String label, @DrawableRes int iconRes, int accentColor) {
            this.type = type;
            this.label = label;
            this.iconRes = iconRes;
            this.accentColor = accentColor;
        }
    }

    private final List<PaletteEntry> entries = new ArrayList<>();
    private PaletteListener paletteListener;

    private int colorSurface;
    private int colorOnSurface;
    private int colorOutlineVariant;
    private int colorPrimary;

    public GamePaletteView(Context context) {
        this(context, null);
    }

    public GamePaletteView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public void setPaletteListener(PaletteListener listener) {
        this.paletteListener = listener;
    }

    private void init(Context context) {
        setOrientation(HORIZONTAL);

        colorSurface = MaterialColors.getColor(context, R.attr.colorSurface, 0xFF141821);
        colorOnSurface = MaterialColors.getColor(context, R.attr.colorOnSurface, 0xFFE5E7EB);
        colorOutlineVariant = MaterialColors.getColor(context, R.attr.colorOutlineVariant, 0x3394A3B8);
        colorPrimary = MaterialColors.getColor(context, R.attr.colorPrimary, 0xFF3B82F6);

        setBackgroundColor(colorSurface);

        // Main scrollable palette column (120dp parent width minus 1dp right divider)
        LinearLayout contentColumn = new LinearLayout(context);
        contentColumn.setOrientation(VERTICAL);
        contentColumn.setBackgroundColor(colorSurface);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setClipToPadding(false);

        LinearLayout listContainer = new LinearLayout(context);
        listContainer.setOrientation(VERTICAL);
        listContainer.setPadding(0, dp(4), 0, dp(8));
        scrollView.addView(listContainer, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        contentColumn.addView(scrollView, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(contentColumn, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));

        // Thin 1dp right divider matching native palette separation
        View rightDivider = new View(context);
        rightDivider.setBackgroundColor(colorOutlineVariant);
        addView(rightDivider, new LayoutParams(dp(1), LayoutParams.MATCH_PARENT));

        // Section 1: 2D World
        List<PaletteEntry> worldEntries = new ArrayList<>();
        worldEntries.add(new PaletteEntry(GameObjectBean.TYPE_SPRITE, "Sprite", R.drawable.ic_mtrl_image, 0xFF3B82F6));
        worldEntries.add(new PaletteEntry(GameObjectBean.TYPE_ANIMATED_SPRITE, "Animated", R.drawable.ic_mtrl_animation, 0xFF8B5CF6));
        worldEntries.add(new PaletteEntry(GameObjectBean.TYPE_PLATFORM, "Platform", R.drawable.ic_mtrl_view_horizontal, 0xFF10B981));
        worldEntries.add(new PaletteEntry(GameObjectBean.TYPE_PHYSICS_BOX, "Physics Box", R.drawable.ic_mtrl_box, 0xFFF59E0B));
        worldEntries.add(new PaletteEntry(GameObjectBean.TYPE_TRIGGER, "Trigger", R.drawable.ic_mtrl_sensor, 0xFF14B8A6));
        worldEntries.add(new PaletteEntry(GameObjectBean.TYPE_PARTICLES, "Particles", R.drawable.ic_mtrl_auto_awesome, 0xFFEC4899));
        worldEntries.add(new PaletteEntry(GameObjectBean.TYPE_CAMERA, "Camera", R.drawable.ic_mtrl_camera, 0xFFEAB308));
        entries.addAll(worldEntries);
        addSection(context, listContainer, "2D World", worldEntries);

        // Section 2: UI / HUD
        List<PaletteEntry> hudEntries = new ArrayList<>();
        hudEntries.add(new PaletteEntry(GameObjectBean.TYPE_HUD_TEXT, "HUD Text", R.drawable.ic_mtrl_formattext, 0xFF06B6D4));
        hudEntries.add(new PaletteEntry(GameObjectBean.TYPE_HUD_BUTTON, "HUD Button", R.drawable.ic_mtrl_button_click, 0xFF6366F1));
        hudEntries.add(new PaletteEntry(GameObjectBean.TYPE_HUD_JOYSTICK, "Joystick", R.drawable.ic_mtrl_target, 0xFF0EA5E9));
        hudEntries.add(new PaletteEntry(GameObjectBean.TYPE_HUD_BAR, "HUD Bar", R.drawable.ic_mtrl_progress_bar, 0xFF22C55E));
        entries.addAll(hudEntries);
        addSection(context, listContainer, "UI / HUD", hudEntries);
    }

    private void addSection(Context context, LinearLayout parent, String title, List<PaletteEntry> sectionEntries) {
        TextView header = new TextView(context);
        header.setText(title);
        header.setTextColor(colorPrimary);
        header.setTextSize(12f);
        header.setSingleLine(true);
        header.setEllipsize(TextUtils.TruncateAt.END);
        LayoutParams headerLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        headerLp.setMargins(dp(4), dp(4), dp(4), dp(4));
        parent.addView(header, headerLp);

        LinearLayout itemsContainer = new LinearLayout(context);
        itemsContainer.setOrientation(VERTICAL);
        itemsContainer.setPadding(0, 0, dp(4), dp(4));
        parent.addView(itemsContainer, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        header.setOnClickListener(v -> itemsContainer.setVisibility(
                itemsContainer.getVisibility() == VISIBLE ? GONE : VISIBLE));

        for (PaletteEntry entry : sectionEntries) {
            itemsContainer.addView(createPaletteItem(context, entry));
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private View createPaletteItem(Context context, PaletteEntry entry) {
        LinearLayout item = new LinearLayout(context);
        item.setOrientation(HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setBackgroundResource(R.drawable.icon_bg);
        item.setClickable(true);
        item.setFocusable(true);
        item.setMinimumHeight(dp(34));

        LayoutInflater.from(context).inflate(R.layout.widget_layout, item, true);

        ImageView imgWidget = item.findViewById(R.id.img_widget);
        imgWidget.setImageResource(entry.iconRes);
        imgWidget.setImageTintList(ColorStateList.valueOf(entry.accentColor));

        TextView tvWidget = item.findViewById(R.id.tv_widget);
        tvWidget.setText(entry.label);
        tvWidget.setTextColor(colorOnSurface);
        tvWidget.setSingleLine(true);
        tvWidget.setEllipsize(TextUtils.TruncateAt.END);

        LayoutParams itemLp = new LayoutParams(LayoutParams.MATCH_PARENT, dp(34));
        item.setLayoutParams(itemLp);

        item.setOnClickListener(v -> {
            if (paletteListener != null) {
                paletteListener.onQuickAddObject(entry.type);
            }
        });

        final int touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        final float[] downPos = new float[2];
        final boolean[] dragStarted = new boolean[1];
        final Runnable longPressDrag = () -> {
            if (!dragStarted[0]) {
                dragStarted[0] = true;
                startPaletteDrag(item, entry.type);
            }
        };

        item.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN -> {
                    downPos[0] = event.getX();
                    downPos[1] = event.getY();
                    dragStarted[0] = false;
                    // Prevent ancestor ViewPager from stealing horizontal drag gestures out of the palette
                    ViewParent paletteParent = GamePaletteView.this.getParent();
                    if (paletteParent != null) {
                        paletteParent.requestDisallowInterceptTouchEvent(true);
                    }
                    v.removeCallbacks(longPressDrag);
                    v.postDelayed(longPressDrag, Math.max(160L, ViewConfiguration.getLongPressTimeout() / 2L));
                    return false;
                }
                case MotionEvent.ACTION_MOVE -> {
                    if (!dragStarted[0]) {
                        float dx = event.getX() - downPos[0];
                        float dy = event.getY() - downPos[1];
                        if (Math.abs(dy) > touchSlop && Math.abs(dy) > Math.abs(dx)) {
                            v.removeCallbacks(longPressDrag);
                            return false;
                        }
                        if (Math.abs(dx) > touchSlop * 0.9f && Math.abs(dx) > Math.abs(dy) * 1.1f) {
                            v.removeCallbacks(longPressDrag);
                            dragStarted[0] = true;
                            startPaletteDrag(v, entry.type);
                            return true;
                        }
                    }
                    return dragStarted[0];
                }
                case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.removeCallbacks(longPressDrag);
                    ViewParent paletteParent = GamePaletteView.this.getParent();
                    if (paletteParent != null) {
                        paletteParent.requestDisallowInterceptTouchEvent(false);
                    }
                    return dragStarted[0];
                }
                default -> {
                    return false;
                }
            }
        });

        return item;
    }

    private void startPaletteDrag(View view, String type) {
        if (view.getParent() != null) {
            view.getParent().requestDisallowInterceptTouchEvent(true);
        }
        ViewParent paletteParent = getParent();
        if (paletteParent != null) {
            paletteParent.requestDisallowInterceptTouchEvent(true);
        }
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        ClipData data = ClipData.newPlainText("game_palette_type", type);
        View.DragShadowBuilder shadow = new View.DragShadowBuilder(view);
        view.startDragAndDrop(data, shadow, type, 0);
    }

    private int dp(float val) {
        return SketchwareUtil.dpToPx(val);
    }
}
