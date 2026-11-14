package pro.sketchware.activities.game.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.divider.MaterialDivider;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.MaterialShapeUtils;
import com.google.android.material.shape.ShapeAppearanceModel;

import pro.sketchware.R;
import pro.sketchware.databinding.DesignDrawerItemBinding;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.UI;

/**
 * Right-side configuration & media drawer for the 2D Game Engine Activity,
 * styled to match {@link com.besome.sketch.design.DesignDrawer}.
 */
public class GameDrawerView extends LinearLayout {

    public interface DrawerActionListener {
        void onOpenImageManager();

        void onOpenSoundManager();

        void onOpenFontManager();

        void onOpenLottieManager();

        void onOpenAssetsManager();

        void onOpenResourcesEditor();

        void onOpenSceneManager();

        void onOpenEngineSettings();

        void onOpenHierarchy();

        void onOpenPermissionManager();

        void onOpenJavaManager();

        void onOpenLogcatReader();
    }

    private DrawerActionListener listener;
    private final Context themedContext;

    public GameDrawerView(Context context) {
        this(context, null);
    }

    public GameDrawerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        this.themedContext = ensureMaterialTheme(context);
        init();
    }

    public void setDrawerActionListener(DrawerActionListener listener) {
        this.listener = listener;
    }

    private static Context ensureMaterialTheme(Context context) {
        TypedValue tv = new TypedValue();
        if (context.getTheme() != null && context.getTheme().resolveAttribute(R.attr.colorSurfaceContainerLow, tv, true)) {
            return context;
        }
        return new ContextThemeWrapper(context, R.style.Theme_SketchwarePro);
    }

    private void init() {
        setOrientation(VERTICAL);
        setFocusable(true);
        setClickable(true);

        ShapeAppearanceModel shape = ShapeAppearanceModel.builder()
                .setTopLeftCornerSize(SketchwareUtil.getDip(24))
                .setBottomLeftCornerSize(SketchwareUtil.getDip(24))
                .build();

        int surfaceColor = MaterialColors.getColor(themedContext, R.attr.colorSurfaceContainerLow, 0xFF1E2430);
        MaterialShapeDrawable background = new MaterialShapeDrawable(shape);
        background.setFillColor(ColorStateList.valueOf(surfaceColor));
        background.initializeElevationOverlay(themedContext);
        setBackground(background);
        setElevation(3f);
        setPadding(0, 0, 0, SketchwareUtil.dpToPx(4));

        ScrollView scrollView = new ScrollView(themedContext);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        addView(scrollView, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout content = new LinearLayout(themedContext);
        content.setOrientation(VERTICAL);
        scrollView.addView(content);

        UI.addSystemWindowInsetToPadding(scrollView, false, true, false, false);
        UI.addSystemWindowInsetToPadding(this, false, false, true, true);

        // Section 1: Project Files & Media
        addDrawerSubheader("Project Files & Media", content);
        addDrawerItem(R.drawable.ic_mtrl_image, "Images / Sprites", "Import sprites, backgrounds and textures", content,
                () -> { if (listener != null) listener.onOpenImageManager(); });
        addDrawerItem(R.drawable.ic_mtrl_music, "Sounds / Music", "Import sound effects and BGM", content,
                () -> { if (listener != null) listener.onOpenSoundManager(); });
        addDrawerItem(R.drawable.ic_mtrl_font, "Fonts", "Import TTF and OTF fonts", content,
                () -> { if (listener != null) listener.onOpenFontManager(); });
        addDrawerItem(R.drawable.ic_mtrl_animation, "Lottie", "Manage Lottie vector animations", content,
                () -> { if (listener != null) listener.onOpenLottieManager(); });
        addDrawerItem(R.drawable.ic_mtrl_file_present, "Assets", "Import extra files in assets/", content,
                () -> { if (listener != null) listener.onOpenAssetsManager(); });
        addDrawerItem(R.drawable.ic_mtrl_folder_code, "Strings & Colors", "Edit project XML resources", content,
                () -> { if (listener != null) listener.onOpenResourcesEditor(); });

        addDrawerDivider(content);

        // Section 2: 2D Engine & System
        addDrawerSubheader("2D Engine & System", content);
        addDrawerItem(R.drawable.ic_mtrl_devices, "Scene Manager", "Create, duplicate and set start scene", content,
                () -> { if (listener != null) listener.onOpenSceneManager(); });
        addDrawerItem(R.drawable.ic_mtrl_list, "Hierarchy & Prefabs", "Layers, lock, visibility and prefabs", content,
                () -> { if (listener != null) listener.onOpenHierarchy(); });
        addDrawerItem(R.drawable.ic_mtrl_settings, "2D Engine Settings", "Virtual resolution, orientation, FPS and hitboxes", content,
                () -> { if (listener != null) listener.onOpenEngineSettings(); });
        addDrawerItem(R.drawable.ic_mtrl_shield_check, "Permissions", "Manage project Android permissions", content,
                () -> { if (listener != null) listener.onOpenPermissionManager(); });
        addDrawerItem(R.drawable.ic_mtrl_java, "Java / Kotlin Files", "Import and manage native classes", content,
                () -> { if (listener != null) listener.onOpenJavaManager(); });
        addDrawerItem(R.drawable.ic_mtrl_article, "Logcat", "View app logs and debugging", content,
                () -> { if (listener != null) listener.onOpenLogcatReader(); });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        MaterialShapeUtils.setParentAbsoluteElevation(this);
    }

    @Override
    public void setElevation(float elevation) {
        super.setElevation(elevation);
        MaterialShapeUtils.setElevation(this, elevation);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int maxWidth = SketchwareUtil.dpToPx(300);
        switch (MeasureSpec.getMode(widthSpec)) {
            case MeasureSpec.EXACTLY:
                break;
            case MeasureSpec.AT_MOST:
                widthSpec = MeasureSpec.makeMeasureSpec(Math.min(MeasureSpec.getSize(widthSpec), maxWidth), MeasureSpec.EXACTLY);
                break;
            case MeasureSpec.UNSPECIFIED:
                widthSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.EXACTLY);
                break;
        }
        super.onMeasure(widthSpec, heightSpec);
    }

    private void addDrawerSubheader(String title, ViewGroup parent) {
        TextView subheader = new TextView(themedContext);
        subheader.setText(title);
        subheader.setTextSize(12f);
        subheader.setTypeface(null, Typeface.BOLD);
        subheader.setTextColor(MaterialColors.getColor(themedContext, R.attr.colorPrimary, 0xFF3B82F6));
        subheader.setEllipsize(TextUtils.TruncateAt.END);
        subheader.setGravity(Gravity.CENTER_VERTICAL);
        subheader.setSingleLine(true);

        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.topMargin = SketchwareUtil.dpToPx(12);
        lp.bottomMargin = SketchwareUtil.dpToPx(6);
        lp.setMarginStart(SketchwareUtil.dpToPx(20));
        lp.setMarginEnd(SketchwareUtil.dpToPx(16));
        subheader.setLayoutParams(lp);
        parent.addView(subheader);
    }

    private void addDrawerDivider(ViewGroup parent) {
        MaterialDivider divider = new MaterialDivider(themedContext);
        divider.setDividerInsetStart(SketchwareUtil.dpToPx(20));
        divider.setDividerInsetEnd(SketchwareUtil.dpToPx(20));

        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.topMargin = SketchwareUtil.dpToPx(8);
        lp.bottomMargin = SketchwareUtil.dpToPx(4);
        divider.setLayoutParams(lp);
        parent.addView(divider);
    }

    private void addDrawerItem(@DrawableRes int iconResId, String title, String subtitle, ViewGroup parent, Runnable action) {
        DesignDrawerItemBinding binding = DesignDrawerItemBinding.inflate(LayoutInflater.from(themedContext), parent, false);
        binding.getRoot().setStrokeWidth(0);
        binding.imgIcon.setImageResource(iconResId);
        binding.tvRootTitle.setText(title);
        binding.tvSubTitle.setText(subtitle);
        binding.getRoot().setOnClickListener(v -> action.run());
        parent.addView(binding.getRoot());
    }
}
