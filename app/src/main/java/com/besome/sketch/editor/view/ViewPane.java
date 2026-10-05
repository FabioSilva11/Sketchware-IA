package com.besome.sketch.editor.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.NinePatch;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.NinePatchDrawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.CalendarView;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.besome.sketch.beans.ImageBean;
import com.besome.sketch.beans.LayoutBean;
import com.besome.sketch.beans.ProjectResourceBean;
import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.design.DesignActivity;
import com.besome.sketch.editor.manage.library.material3.Material3LibraryManager;
import com.besome.sketch.editor.view.item.ItemAdView;
import com.besome.sketch.editor.view.item.ItemBottomNavigationView;
import com.besome.sketch.editor.view.item.ItemButton;
import com.besome.sketch.editor.view.item.ItemCalendarView;
import com.besome.sketch.editor.view.item.ItemCardView;
import com.besome.sketch.editor.view.item.ItemCheckBox;
import com.besome.sketch.editor.view.item.ItemConstraintLayout;
import com.besome.sketch.editor.view.item.ItemEditText;
import com.besome.sketch.editor.view.item.ItemFloatingActionButton;
import com.besome.sketch.editor.view.item.ItemFrameLayout;
import com.besome.sketch.editor.view.item.ItemHorizontalScrollView;
import com.besome.sketch.editor.view.item.ItemImageView;
import com.besome.sketch.editor.view.item.ItemInclude;
import com.besome.sketch.editor.view.item.ItemLinearLayout;
import com.besome.sketch.editor.view.item.ItemListView;
import com.besome.sketch.editor.view.item.ItemMapView;
import com.besome.sketch.editor.view.item.ItemPlainView;
import com.besome.sketch.editor.view.item.ItemProgressBar;
import com.besome.sketch.editor.view.item.ItemRecyclerView;
import com.besome.sketch.editor.view.item.ItemRelativeLayout;
import com.besome.sketch.editor.view.item.ItemSearchView;
import com.besome.sketch.editor.view.item.ItemSeekBar;
import com.besome.sketch.editor.view.item.ItemSignInButton;
import com.besome.sketch.editor.view.item.ItemSpinner;
import com.besome.sketch.editor.view.item.ItemSwitch;
import com.besome.sketch.editor.view.item.ItemTabLayout;
import com.besome.sketch.editor.view.item.ItemTextView;
import com.besome.sketch.editor.view.item.ItemVerticalScrollView;
import com.besome.sketch.editor.view.item.ItemWebView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import a.a.a.Gx;
import a.a.a.kC;
import a.a.a.lC;
import a.a.a.wB;
import a.a.a.wq;
import a.a.a.yB;
import a.a.a.zB;
import dev.aldi.sayuti.editor.view.item.ItemBadgeView;
import dev.aldi.sayuti.editor.view.item.ItemCircleImageView;
import dev.aldi.sayuti.editor.view.item.ItemCodeView;
import dev.aldi.sayuti.editor.view.item.ItemLottieAnimation;
import dev.aldi.sayuti.editor.view.item.ItemMaterialButton;
import dev.aldi.sayuti.editor.view.item.ItemOTPView;
import dev.aldi.sayuti.editor.view.item.ItemPatternLockView;
import dev.aldi.sayuti.editor.view.item.ItemViewPager;
import dev.aldi.sayuti.editor.view.item.ItemWaveSideBar;
import dev.aldi.sayuti.editor.view.item.ItemYoutubePlayer;
import mod.agus.jcoderz.beans.ViewBeans;
import mod.agus.jcoderz.editor.view.item.ItemAnalogClock;
import mod.agus.jcoderz.editor.view.item.ItemAutoCompleteTextView;
import mod.agus.jcoderz.editor.view.item.ItemDatePicker;
import mod.agus.jcoderz.editor.view.item.ItemDigitalClock;
import mod.agus.jcoderz.editor.view.item.ItemGridView;
import mod.agus.jcoderz.editor.view.item.ItemMultiAutoCompleteTextView;
import mod.agus.jcoderz.editor.view.item.ItemRadioButton;
import mod.agus.jcoderz.editor.view.item.ItemRatingBar;
import mod.agus.jcoderz.editor.view.item.ItemTimePicker;
import mod.agus.jcoderz.editor.view.item.ItemVideoView;
import mod.bobur.VectorDrawableLoader;
import mod.hey.studios.util.ProjectFile;
import pro.sketchware.R;
import pro.sketchware.activities.resourceseditor.components.utils.ColorsEditorManager;
import pro.sketchware.activities.resourceseditor.components.utils.StringsEditorManager;
import pro.sketchware.managers.inject.InjectRootLayoutManager;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.InjectAttributeHandler;
import pro.sketchware.utility.InjectAttributes;
import pro.sketchware.utility.InvokeUtil;
import pro.sketchware.utility.ProjectStrings;
import pro.sketchware.utility.PropertiesUtil;
import pro.sketchware.utility.ResourceUtil;
import pro.sketchware.utility.SvgUtils;
import pro.sketchware.utility.ThemeUtils;
import pro.sketchware.utility.TranslationFunction;

public class ViewPane extends RelativeLayout {
    private final String stringsStart = "@string/";
    private final FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
    private Context context;
    private ViewGroup rootLayout;
    private int b = 99;
    private ArrayList<ViewInfo> viewInfos = new ArrayList<>();
    private ViewInfo viewInfo;
    private TextView highlightedTextView;
    private kC resourcesManager;
    private String sc_id;
    private SvgUtils svgUtils;
    private ColorsEditorManager colorsEditorManager;
    private int defaultTextColor = 0; // need to save the original color before changes, cause using getDefaultColor() returns the current text color
    private int defaultHintColor = 0;
    private Material3LibraryManager material3LibraryManager;
    private HashMap<String, String> cachedStrings;
    private LayoutRelationsOverlay relationsOverlay;
    /** Drop position (dp from the content edge) of a widget dragged over a RelativeLayout/ConstraintLayout. */
    private android.graphics.Point dropPoint;
    /** layout_gravity of a widget dragged over a FrameLayout, or -1. */
    private int dropGravity = -1;
    private ViewGroup dropGroup;
    private long cachedStringsStamp;
    /** Minimum size each editor item set for itself, before any android:minWidth/minHeight. */
    private final java.util.Map<View, int[]> editorMinimums = new java.util.WeakHashMap<>();

    private static final String TAG = "ViewPane";

    /** How a container positions its children, which decides the LayoutParams class they need. */
    private enum Container {LINEAR, RELATIVE, CONSTRAINT, FRAME}

    /** RelativeLayout rules by attribute name (without the android: prefix). */
    private static final java.util.Map<String, Integer> RELATIVE_RULES = new java.util.LinkedHashMap<>();
    /** Rules whose value is "true"/"false"; the others point at a sibling. */
    private static final java.util.Set<Integer> RELATIVE_BOOLEAN_RULES = new java.util.HashSet<>();

    static {
        RELATIVE_RULES.put("layout_alignParentTop", RelativeLayout.ALIGN_PARENT_TOP);
        RELATIVE_RULES.put("layout_alignParentBottom", RelativeLayout.ALIGN_PARENT_BOTTOM);
        RELATIVE_RULES.put("layout_alignParentLeft", RelativeLayout.ALIGN_PARENT_LEFT);
        RELATIVE_RULES.put("layout_alignParentRight", RelativeLayout.ALIGN_PARENT_RIGHT);
        RELATIVE_RULES.put("layout_alignParentStart", RelativeLayout.ALIGN_PARENT_START);
        RELATIVE_RULES.put("layout_alignParentEnd", RelativeLayout.ALIGN_PARENT_END);
        RELATIVE_RULES.put("layout_centerInParent", RelativeLayout.CENTER_IN_PARENT);
        RELATIVE_RULES.put("layout_centerHorizontal", RelativeLayout.CENTER_HORIZONTAL);
        RELATIVE_RULES.put("layout_centerVertical", RelativeLayout.CENTER_VERTICAL);
        RELATIVE_BOOLEAN_RULES.addAll(RELATIVE_RULES.values());
        RELATIVE_RULES.put("layout_above", RelativeLayout.ABOVE);
        RELATIVE_RULES.put("layout_below", RelativeLayout.BELOW);
        RELATIVE_RULES.put("layout_toLeftOf", RelativeLayout.LEFT_OF);
        RELATIVE_RULES.put("layout_toRightOf", RelativeLayout.RIGHT_OF);
        RELATIVE_RULES.put("layout_toStartOf", RelativeLayout.START_OF);
        RELATIVE_RULES.put("layout_toEndOf", RelativeLayout.END_OF);
        RELATIVE_RULES.put("layout_alignTop", RelativeLayout.ALIGN_TOP);
        RELATIVE_RULES.put("layout_alignBottom", RelativeLayout.ALIGN_BOTTOM);
        RELATIVE_RULES.put("layout_alignLeft", RelativeLayout.ALIGN_LEFT);
        RELATIVE_RULES.put("layout_alignRight", RelativeLayout.ALIGN_RIGHT);
        RELATIVE_RULES.put("layout_alignStart", RelativeLayout.ALIGN_START);
        RELATIVE_RULES.put("layout_alignEnd", RelativeLayout.ALIGN_END);
        RELATIVE_RULES.put("layout_alignBaseline", RelativeLayout.ALIGN_BASELINE);
    }

    public ViewPane(Context context) {
        super(context);
        addRelationsOverlay();
    }

    public ViewPane(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        addRelationsOverlay();
    }

    private void addRelationsOverlay() {
        relationsOverlay = new LayoutRelationsOverlay(getContext(), this);
        addView(relationsOverlay, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        getViewTreeObserver().addOnScrollChangedListener(relationsOverlay::invalidate);
    }

    public LayoutRelationsOverlay getRelationsOverlay() {
        return relationsOverlay;
    }

    public ViewGroup getRootLayout() {
        return rootLayout;
    }

    @Override
    public void onViewAdded(View child) {
        super.onViewAdded(child);
        // The overlay draws over the layout and the FAB, and gets touches on anchors first.
        if (relationsOverlay != null && child != relationsOverlay) {
            relationsOverlay.bringToFront();
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (relationsOverlay != null) relationsOverlay.invalidate();
    }

    public void clearViews() {
        resetView(true);
        viewInfos = new ArrayList<>();
        if (rootLayout != null) {
            ((ScrollContainer) rootLayout).setChildScrollEnabled(true);
        }
    }

    public void setResourceManager(kC resourcesManager) {
        this.resourcesManager = resourcesManager;
    }

    /**
     * The drop preview: a translucent box with a border and a light shadow, so the widgets under it
     * stay visible while it shows where the dragged widget will land.
     */
    private void initTextView() {
        float dp = getResources().getDisplayMetrics().density;
        highlightedTextView = new TextView(getContext());
        android.graphics.drawable.GradientDrawable preview = new android.graphics.drawable.GradientDrawable();
        preview.setColor(0x331E88E5);
        preview.setStroke(Math.max(1, Math.round(2 * dp)), 0xFF1E88E5);
        preview.setCornerRadius(4 * dp);
        highlightedTextView.setBackground(preview);
        highlightedTextView.setElevation(3 * dp);
        highlightedTextView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        highlightedTextView.setVisibility(GONE);
    }

    public void clearViewPane() {
        if (rootLayout != null) {
            rootLayout.removeAllViews();
        }
    }

    public void removeFabView() {
        View findViewWithTag = findViewWithTag("_fab");
        if (findViewWithTag == null) {
            return;
        }
        removeView(findViewWithTag);
    }

    public void removeView(ViewBean viewBean) {
        if (rootLayout == null || viewBean == null || viewBean.id == null) return;
        View view = rootLayout.findViewWithTag(viewBean.id);
        if (view == null || view == rootLayout || !(view.getParent() instanceof ViewGroup parent)) return;
        parent.removeView(view);
        if (parent instanceof ScrollContainer container) {
            container.reindexChildren();
        }
        refreshPositionRules(parent);
        if (relationsOverlay != null) relationsOverlay.invalidate();
    }

    /** The container view with this id ("root" is the root layout), or null. */
    private ViewGroup findContainer(String id) {
        if (rootLayout == null || id == null) return null;
        View view = "root".equals(id) ? rootLayout : rootLayout.findViewWithTag(id);
        return view instanceof ViewGroup group ? group : null;
    }

    /** Whether {@code view} is {@code group} or contains it. */
    private static boolean containsOrIs(View view, View group) {
        for (View current = group; current != null; ) {
            if (current == view) return true;
            ViewParent parent = current.getParent();
            current = parent instanceof View parentView ? parentView : null;
        }
        return false;
    }

    public ItemView g(ViewBean viewBean) {
        View findViewWithTag;
        String preId = viewBean.preId;
        if (preId != null && !preId.isEmpty() && !preId.equals(viewBean.id)) {
            View preView = rootLayout.findViewWithTag(preId);
            if (preView != null) preView.setTag(viewBean.id);
            viewBean.preId = "";
        }
        if (viewBean.id.charAt(0) == '_') {
            findViewWithTag = findViewWithTag(viewBean.id);
        } else {
            findViewWithTag = rootLayout.findViewWithTag(viewBean.id);
        }
        if (!(findViewWithTag instanceof ItemView)) {
            Log.w(TAG, "No view for widget " + viewBean.id);
            return null;
        }
        updateItemView(findViewWithTag, viewBean);
        return (ItemView) findViewWithTag;
    }

    /**
     * Puts the view of a moved widget where its bean now says: another parent and/or another index.
     * The view is detached from wherever it really is, so a stale preParent can't break the move, and
     * its LayoutParams are rebuilt for the new container.
     */
    public ItemView d(ViewBean viewBean) {
        View view = viewBean.id.charAt(0) == '_' ? findViewWithTag(viewBean.id)
                : rootLayout == null ? null : rootLayout.findViewWithTag(viewBean.id);
        if (!(view instanceof ItemView)) {
            Log.w(TAG, "No view for moved widget " + viewBean.id);
            clearMoveState(viewBean);
            return null;
        }
        String preParent = viewBean.preParent;
        boolean parentChanged = preParent != null && !preParent.isEmpty() && !viewBean.parent.equals(preParent);
        ViewParent current = view.getParent();
        boolean wrongParent = current != findContainer(viewBean.parent);
        if (view != rootLayout && (parentChanged || wrongParent || viewBean.index != viewBean.preIndex)) {
            if (current instanceof ViewGroup oldParent) {
                oldParent.removeView(view);
                if (oldParent instanceof ScrollContainer container) container.reindexChildren();
                refreshPositionRules(oldParent);
            }
            addViewAndUpdateIndex(view);
        }
        clearMoveState(viewBean);
        view.setVisibility(VISIBLE);
        if (view != rootLayout) updateItemView(view, viewBean);
        return (ItemView) view;
    }

    private static void clearMoveState(ViewBean viewBean) {
        viewBean.preId = "";
        viewBean.preIndex = -1;
        viewBean.preParent = "";
        viewBean.preParentType = -1;
    }

    public void initialize(String sc_id, boolean isPreviewMode) {
        this.sc_id = sc_id;
        material3LibraryManager = new Material3LibraryManager(getContext(), sc_id);
        colorsEditorManager = new ColorsEditorManager();
        int viewEditorThemeOverlay = material3LibraryManager.getViewEditorThemeOverlay();
        context = new ContextThemeWrapper(getContext(), viewEditorThemeOverlay);
        svgUtils = new SvgUtils(context);
        svgUtils.initImageLoader();
        if (viewEditorThemeOverlay == R.style.ThemeOverlay_SketchwarePro_ViewEditor) {
            setBackgroundColor(Color.WHITE);
        } else if (isPreviewMode) {
            setBackgroundColor(ThemeUtils.getColor(context, R.attr.colorSurface));
        } else {
            setBackground(AppCompatResources.getDrawable(context, R.drawable.bg_view_pane));
        }
        //addRootLayout();
        initTextView();
    }

    public void addRootLayout(ViewBean viewBean) {
        viewInfo = null;
        if (rootLayout != null) {
            if (rootLayout instanceof ItemLinearLayout linearLayout) {
                a(viewBean, linearLayout);
            } else if (rootLayout instanceof ItemHorizontalScrollView || rootLayout instanceof ItemVerticalScrollView) {
                a(viewBean, rootLayout);
            } else {
                addDroppableForViewGroup(viewBean, rootLayout);
            }
            ((ScrollContainer) rootLayout).setChildScrollEnabled(false);
        }
    }

    private int calculateViewDepth(View view) {
        View currentView = view;
        int depth = 0;
        while (currentView != null && currentView != rootLayout) {
            depth++;
            currentView = (View) currentView.getParent();
        }
        return depth * 2;
    }

    public View createItemView(ViewBean viewBean) {
        View item = switch (viewBean.type) {
            case ViewBean.VIEW_TYPE_LAYOUT_LINEAR,
                 ViewBeans.VIEW_TYPE_LAYOUT_COLLAPSINGTOOLBARLAYOUT,
                 ViewBeans.VIEW_TYPE_LAYOUT_TEXTINPUTLAYOUT,
                 ViewBeans.VIEW_TYPE_LAYOUT_SWIPEREFRESHLAYOUT,
                 ViewBeans.VIEW_TYPE_LAYOUT_RADIOGROUP -> new ItemLinearLayout(context);
            case ViewBean.VIEW_TYPE_LAYOUT_RELATIVE -> new ItemRelativeLayout(context);
            case ViewBeans.VIEW_TYPE_LAYOUT_CARDVIEW -> new ItemCardView(context);
            case ViewBean.VIEW_TYPE_LAYOUT_HSCROLLVIEW -> new ItemHorizontalScrollView(context);
            case ViewBean.VIEW_TYPE_WIDGET_BUTTON -> new ItemButton(context);
            case ViewBean.VIEW_TYPE_WIDGET_TEXTVIEW -> new ItemTextView(context);
            case ViewBean.VIEW_TYPE_WIDGET_EDITTEXT -> new ItemEditText(context);
            case ViewBean.VIEW_TYPE_WIDGET_IMAGEVIEW -> new ItemImageView(context);
            case ViewBean.VIEW_TYPE_WIDGET_WEBVIEW -> new ItemWebView(context);
            case ViewBean.VIEW_TYPE_WIDGET_PROGRESSBAR -> new ItemProgressBar(context);
            case ViewBean.VIEW_TYPE_WIDGET_LISTVIEW -> new ItemListView(context);
            case ViewBean.VIEW_TYPE_WIDGET_SPINNER -> new ItemSpinner(context);
            case ViewBean.VIEW_TYPE_WIDGET_CHECKBOX -> new ItemCheckBox(context);
            case ViewBean.VIEW_TYPE_LAYOUT_VSCROLLVIEW -> new ItemVerticalScrollView(context);
            case ViewBean.VIEW_TYPE_WIDGET_SWITCH -> new ItemSwitch(context);
            case ViewBean.VIEW_TYPE_WIDGET_SEEKBAR -> new ItemSeekBar(context);
            case ViewBean.VIEW_TYPE_WIDGET_CALENDARVIEW -> new ItemCalendarView(context);
            case ViewBean.VIEW_TYPE_WIDGET_ADVIEW -> new ItemAdView(context);
            case ViewBean.VIEW_TYPE_WIDGET_MAPVIEW -> new ItemMapView(context);
            case ViewBeans.VIEW_TYPE_WIDGET_RADIOBUTTON -> new ItemRadioButton(context);
            case ViewBeans.VIEW_TYPE_WIDGET_RATINGBAR -> new ItemRatingBar(context);
            case ViewBeans.VIEW_TYPE_WIDGET_VIDEOVIEW -> new ItemVideoView(context);
            case ViewBeans.VIEW_TYPE_WIDGET_SEARCHVIEW -> new ItemSearchView(context);
            case ViewBeans.VIEW_TYPE_WIDGET_AUTOCOMPLETETEXTVIEW ->
                    new ItemAutoCompleteTextView(context);
            case ViewBeans.VIEW_TYPE_WIDGET_MULTIAUTOCOMPLETETEXTVIEW ->
                    new ItemMultiAutoCompleteTextView(context);
            case ViewBeans.VIEW_TYPE_WIDGET_GRIDVIEW -> new ItemGridView(context);
            case ViewBeans.VIEW_TYPE_WIDGET_ANALOGCLOCK -> new ItemAnalogClock(context);
            case ViewBeans.VIEW_TYPE_WIDGET_DATEPICKER -> new ItemDatePicker(context);
            case ViewBeans.VIEW_TYPE_WIDGET_TIMEPICKER -> new ItemTimePicker(context);
            case ViewBeans.VIEW_TYPE_WIDGET_DIGITALCLOCK -> new ItemDigitalClock(context);
            case ViewBeans.VIEW_TYPE_LAYOUT_TABLAYOUT -> new ItemTabLayout(context);
            case ViewBeans.VIEW_TYPE_LAYOUT_VIEWPAGER -> new ItemViewPager(context);
            case ViewBeans.VIEW_TYPE_LAYOUT_BOTTOMNAVIGATIONVIEW ->
                    new ItemBottomNavigationView(context);
            case ViewBeans.VIEW_TYPE_WIDGET_BADGEVIEW -> new ItemBadgeView(context);
            case ViewBeans.VIEW_TYPE_WIDGET_PATTERNLOCKVIEW -> new ItemPatternLockView(context);
            case ViewBeans.VIEW_TYPE_WIDGET_WAVESIDEBAR -> new ItemWaveSideBar(context);
            case ViewBeans.VIEW_TYPE_WIDGET_MATERIALBUTTON -> new ItemMaterialButton(context);
            case ViewBeans.VIEW_TYPE_WIDGET_SIGNINBUTTON -> new ItemSignInButton(context);
            case ViewBeans.VIEW_TYPE_WIDGET_CIRCLEIMAGEVIEW -> new ItemCircleImageView(context);
            case ViewBeans.VIEW_TYPE_WIDGET_LOTTIEANIMATIONVIEW -> new ItemLottieAnimation(context);
            case ViewBeans.VIEW_TYPE_WIDGET_YOUTUBEPLAYERVIEW -> new ItemYoutubePlayer(context);
            case ViewBeans.VIEW_TYPE_WIDGET_OTPVIEW -> new ItemOTPView(context);
            case ViewBeans.VIEW_TYPE_WIDGET_CODEVIEW -> new ItemCodeView(context);
            case ViewBeans.VIEW_TYPE_WIDGET_RECYCLERVIEW -> new ItemRecyclerView(context);
            case ViewBeans.VIEW_TYPE_LAYOUT_CONSTRAINTLAYOUT -> new ItemConstraintLayout(context);
            case ViewBeans.VIEW_TYPE_LAYOUT_FRAMELAYOUT -> new ItemFrameLayout(context);
            case ViewBeans.VIEW_TYPE_LAYOUT_INCLUDE -> new ItemInclude(context);
            case ViewBeans.VIEW_TYPE_WIDGET_VIEW -> new ItemPlainView(context);
            default -> getUnknownItemView(viewBean);
        };
        item.setId(++b);
        item.setTag(viewBean.id);
        ((ItemView) item).setBean(viewBean);
        updateItemView(item, viewBean);
        return item;
    }

    private View getUnknownItemView(ViewBean bean) {
        bean.type = ViewBean.VIEW_TYPE_LAYOUT_LINEAR;
        return new ItemLinearLayout(context);
    }

    public void updateRootLayout(String sc_id, String fileName) {
        InjectRootLayoutManager manager = new InjectRootLayoutManager(sc_id);
        var currentBean = manager.toBean(fileName);
        View rootView = createItemView(currentBean);
        if (rootView instanceof ItemView sy) {
            sy.setFixed(true);
        }
        if (rootLayout != null) {
            removeView(rootLayout);
        } else {
            rootLayout = (ViewGroup) rootView;
        }
        if (rootLayout instanceof ItemView sy) {
            if (!currentBean.isEqual(sy.getBean())) {
                rootLayout = (ViewGroup) rootView;
            }
        }
        addView(rootLayout);
    }

    private void updateItemView(View view, ViewBean viewBean) {
        ImageBean imageBean;
        String str;
        var injectHandler = new InjectAttributeHandler(viewBean);
        if (viewBean.id.charAt(0) == '_') {
            LayoutParams layoutParams = new LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            layoutParams.leftMargin = (int) wB.a(getContext(), (float) viewBean.layout.marginLeft);
            layoutParams.topMargin = (int) wB.a(getContext(), (float) viewBean.layout.marginTop);
            layoutParams.rightMargin = (int) wB.a(getContext(), (float) viewBean.layout.marginRight);
            layoutParams.bottomMargin = (int) wB.a(getContext(), (float) viewBean.layout.marginBottom);
            int layoutGravity = viewBean.layout.layoutGravity;
            if ((layoutGravity & Gravity.LEFT) == Gravity.LEFT) {
                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            }
            if ((layoutGravity & Gravity.TOP) == Gravity.TOP) {
                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            }
            if ((layoutGravity & Gravity.RIGHT) == Gravity.RIGHT) {
                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            }
            if ((layoutGravity & Gravity.BOTTOM) == Gravity.BOTTOM) {
                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            }
            if ((layoutGravity & Gravity.CENTER_HORIZONTAL) == Gravity.CENTER_HORIZONTAL) {
                layoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
            }
            if ((layoutGravity & Gravity.CENTER_VERTICAL) == Gravity.CENTER_VERTICAL) {
                layoutParams.addRule(RelativeLayout.CENTER_VERTICAL);
            }
            if ((layoutGravity & Gravity.CENTER) == Gravity.CENTER) {
                layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);
            }
            view.setLayoutParams(layoutParams);
            if (viewBean.getClassInfo().b("FloatingActionButton") && (imageBean = viewBean.image) != null && (str = imageBean.resName) != null && !str.isEmpty()) {
                try {
                    crashlytics.log("ViewPane: trying to set image to FAB");
                    FloatingActionButton fab = (FloatingActionButton) view;
                    if (resourcesManager.h(viewBean.image.resName) == ProjectResourceBean.PROJECT_RES_TYPE_RESOURCE) {
                        int resourceId = getContext().getResources().getIdentifier(viewBean.image.resName, "drawable", getContext().getPackageName());
                        if (resourceId != 0) {
                            fab.setImageResource(resourceId);
                        }
                    } else if (viewBean.image.resName.equals("default_image")) {
                        fab.setImageResource(R.drawable.default_image);
                    } else {
                        String imagePath = resourcesManager.f(viewBean.image.resName);
                        File imageFile = new File(imagePath);

                        if (imageFile.exists()) {
                            int scaleFactor = Math.round(getResources().getDisplayMetrics().density / 2.0f);

                            if (imagePath.endsWith(".xml")) {
                                crashlytics.log("ViewPane: loading scaled XML/SVG image");
                                FilePathUtil fpu = new FilePathUtil();
                                svgUtils.loadScaledSvgIntoImageView(new AppCompatImageView(getContext()) {
                                    @Override
                                    public void setImageBitmap(Bitmap bitmap) {
                                        fab.setImageBitmap(bitmap);
                                    }
                                }, fpu.getSvgFullPath(sc_id, viewBean.image.resName), scaleFactor);
                            } else {
                                Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
                                if (bitmap != null) {
                                    Bitmap scaledBitmap = Bitmap.createScaledBitmap(
                                            bitmap,
                                            bitmap.getWidth() * scaleFactor,
                                            bitmap.getHeight() * scaleFactor,
                                            true
                                    );
                                    fab.setImageBitmap(scaledBitmap);
                                }
                            }
                        } else {
                            crashlytics.log("ViewPane: converting XML to SVG for FAB");
                            VectorDrawableLoader vectorDrawableLoader = new VectorDrawableLoader();
                            ImageView tempImageView = new AppCompatImageView(getContext()) {
                                @Override
                                public void setImageDrawable(android.graphics.drawable.Drawable drawable) {
                                    fab.setImageDrawable(drawable);
                                }
                            };
                            vectorDrawableLoader.setImageVectorFromFile(tempImageView, vectorDrawableLoader.getVectorFullPath(DesignActivity.sc_id, viewBean.image.resName));
                        }
                    }
                } catch (Exception exception) {
                    crashlytics.recordException(exception);
                }
            }
            view.setRotation(viewBean.image.rotate);
            view.setAlpha(viewBean.alpha);
            view.setTranslationX(wB.a(getContext(), viewBean.translationX));
            view.setTranslationY(wB.a(getContext(), viewBean.translationY));
            view.setScaleX(viewBean.scaleX);
            view.setScaleY(viewBean.scaleY);
            view.setVisibility(View.VISIBLE);
            return;
        }
        ViewGroup container = view == rootLayout ? null : parentContainerOf(view, viewBean);
        Container containerKind = updateLayout(view, viewBean, container);
        view.setRotation(viewBean.image.rotate);
        view.setAlpha(viewBean.alpha);
        view.setTranslationX(wB.a(getContext(), viewBean.translationX));
        view.setTranslationY(wB.a(getContext(), viewBean.translationY));
        view.setScaleX(viewBean.scaleX);
        view.setScaleY(viewBean.scaleY);
        String backgroundResource = viewBean.layout.backgroundResource;
        if (backgroundResource != null) {
            try {
                if (resourcesManager.h(backgroundResource) == ProjectResourceBean.PROJECT_RES_TYPE_RESOURCE) {
                    view.setBackgroundResource(getContext().getResources().getIdentifier(viewBean.layout.backgroundResource, "drawable", getContext().getPackageName()));
                } else {
                    String backgroundRes = resourcesManager.f(viewBean.layout.backgroundResource);
                    if (backgroundRes.endsWith(".9.png")) {
                        Bitmap decodedBitmap = zB.a(backgroundRes);
                        byte[] ninePatchChunk = decodedBitmap.getNinePatchChunk();
                        if (NinePatch.isNinePatchChunk(ninePatchChunk)) {
                            view.setBackground(new NinePatchDrawable(getResources(), decodedBitmap, ninePatchChunk, new Rect(), null));
                        } else {
                            view.setBackground(new BitmapDrawable(getResources(), backgroundRes));
                        }
                    } else {
                        Bitmap decodeFile2 = BitmapFactory.decodeFile(backgroundRes);
                        int round2 = Math.round(getResources().getDisplayMetrics().density / 2.0f);
                        view.setBackground(new BitmapDrawable(getResources(), Bitmap.createScaledBitmap(decodeFile2, decodeFile2.getWidth() * round2, decodeFile2.getHeight() * round2, true)));
                    }
                }
            } catch (Exception e) {
                Log.e("DEBUG", e.getMessage(), e);
            }
        }
        Gx classInfo = viewBean.getClassInfo();
        if (classInfo.a("LinearLayout")) {
            LinearLayout linearLayout = (LinearLayout) view;
            linearLayout.setOrientation(viewBean.layout.orientation);
            linearLayout.setWeightSum(viewBean.layout.weightSum);
            if (view instanceof ItemLinearLayout) {
                ((ItemLinearLayout) view).setLayoutGravity(viewBean.layout.gravity);
            }
        }
        if (container instanceof ItemRelativeLayout && containerKind == Container.RELATIVE) {
            // The rules of the siblings can point at this widget (and cycles depend on all of them).
            refreshPositionRules(container);
        } else if (container instanceof ItemConstraintLayout && containerKind == Container.CONSTRAINT) {
            updateConstraints(view, viewBean, container);
        }
        if (classInfo.a("TextView")) {
            TextView textView = (TextView) view;
            updateTextView(textView, viewBean);
            if (!classInfo.b("Button") && !classInfo.b("Switch")) {
                textView.setGravity(viewBean.layout.gravity);
            } else {
                int gravity = viewBean.layout.gravity;
                if (gravity == LayoutBean.GRAVITY_NONE) {
                    textView.setGravity(Gravity.CENTER);
                } else {
                    textView.setGravity(gravity);
                }
            }
        }
        if (classInfo.a("EditText")) {
            updateEditText((EditText) view, viewBean);
        }
        if (classInfo.a("ImageView")) {
            if (resourcesManager.h(viewBean.image.resName) == ProjectResourceBean.PROJECT_RES_TYPE_RESOURCE) {
                ((ImageView) view).setImageResource(getContext().getResources().getIdentifier(viewBean.image.resName, "drawable", getContext().getPackageName()));
            } else if (viewBean.image.resName.equals("default_image")) {
                ((ImageView) view).setImageResource(R.drawable.default_image);
            } else {
                try {
                    String imagelocation = resourcesManager.f(viewBean.image.resName);
                    File file = new File(imagelocation);
                    if (file.exists() && file.length() > 0) {
                        int round3 = Math.round(getResources().getDisplayMetrics().density / 2.0f);
                        if (imagelocation.endsWith(".xml")) {
                            FilePathUtil fpu = new FilePathUtil();
                            svgUtils.loadScaledSvgIntoImageView((ImageView) view, fpu.getSvgFullPath(sc_id, viewBean.image.resName), round3);
                        } else {
                            Bitmap decodeFile3 = BitmapFactory.decodeFile(imagelocation);
                            ((ImageView) view).setImageBitmap(Bitmap.createScaledBitmap(decodeFile3, decodeFile3.getWidth() * round3, decodeFile3.getHeight() * round3, true));
                        }
                    } else {
                        VectorDrawableLoader vectorDrawableLoader = new VectorDrawableLoader();
                        vectorDrawableLoader.setImageVectorFromFile((ImageView) view, vectorDrawableLoader.getVectorFullPath(DesignActivity.sc_id, viewBean.image.resName));
                    }
                } catch (Exception unused2) {
                    crashlytics.recordException(unused2);
                    FileUtil.deleteFile(new VectorDrawableLoader().getVectorFullPath(DesignActivity.sc_id, viewBean.image.resName));
                    viewBean.image.resName = "default_image";
                    ((ImageView) view).setImageResource(R.drawable.default_image);
                }
            }
            if (classInfo.b("CircleImageView")) {
                updateCircleImageView((ItemCircleImageView) view, injectHandler);
            } else {
                ((ImageView) view).setScaleType(ImageView.ScaleType.valueOf(viewBean.image.scaleType));
            }
        }
        if (classInfo.a("CompoundButton")) {
            ((CompoundButton) view).setChecked(viewBean.checked != 0);
        }
        if (classInfo.b("SeekBar")) {
            SeekBar seekBar = (SeekBar) view;
            seekBar.setProgress(viewBean.progress);
            seekBar.setMax(viewBean.max);
        }
        if (classInfo.b("ProgressBar")) {
            ((ItemProgressBar) view).setProgressBarStyle(viewBean.progressStyle);
        }
        if (classInfo.b("CalendarView")) {
            ((CalendarView) view).setFirstDayOfWeek(viewBean.firstDayOfWeek);
        }
        if (classInfo.b("AdView")) {
            ((ItemAdView) view).setAdSize(viewBean.adSize);
        }
        if (classInfo.b("CardView")) {
            var cardView = (ItemCardView) view;
            cardView.setContentPadding(
                    viewBean.layout.paddingLeft,
                    viewBean.layout.paddingTop,
                    viewBean.layout.paddingRight,
                    viewBean.layout.paddingBottom);
            updateCardView(cardView, injectHandler);
        }
        if (classInfo.b("TabLayout")) {
            updateTabLayout((ItemTabLayout) view, injectHandler);
        }
        if (classInfo.b("MaterialButton")) {
            updateMaterialButton((ItemMaterialButton) view, injectHandler);
        }
        if (classInfo.b("SignInButton")) {
            ItemSignInButton button = (ItemSignInButton) view;
            boolean hasButtonSize = false;
            boolean hasColorScheme = false;
            for (String line : viewBean.inject.split("\n")) {
                if (line.contains("buttonSize")) {
                    String buttonSize = extractAttrValue(line, "app:buttonSize");
                    if (!buttonSize.startsWith("@")) {
                        hasButtonSize = true;
                        switch (buttonSize) {
                            case "icon_only":
                                button.setSize(ItemSignInButton.ButtonSize.ICON_ONLY);
                                break;
                            case "wide":
                                button.setSize(ItemSignInButton.ButtonSize.WIDE);
                                break;
                            case "standard":
                            default:
                                button.setSize(ItemSignInButton.ButtonSize.STANDARD);
                                break;
                        }
                    }
                }
                if (line.contains("colorScheme")) {
                    String colorScheme = extractAttrValue(line, "app:colorScheme");
                    if (!colorScheme.startsWith("@")) {
                        hasColorScheme = true;
                        switch (colorScheme) {
                            case "dark":
                                button.setColorScheme(ItemSignInButton.ColorScheme.DARK);
                                break;
                            case "auto":
                            case "light":
                            default:
                                button.setColorScheme(ItemSignInButton.ColorScheme.LIGHT);
                                break;
                        }
                    }
                }
                if (!hasButtonSize) button.setSize(ItemSignInButton.ButtonSize.STANDARD);
                if (!hasColorScheme) button.setColorScheme(ItemSignInButton.ColorScheme.LIGHT);
            }
        }
        var elevation = injectHandler.getAttributeValueOf("elevation");
        if (!elevation.isEmpty()) {
            view.setElevation(PropertiesUtil.resolveSize(elevation, 0));
        }
        applyInjectedAttributes(view, viewBean);
        view.setVisibility(VISIBLE);
        if (relationsOverlay != null) relationsOverlay.invalidate();
        if (view instanceof EditorListItem listItem) {
            String listitem = injectHandler.getAttributeValueOf("listitem");
            String itemCount = injectHandler.getAttributeValueOf("itemCount");
            if (!TextUtils.isEmpty(listitem)) {
                //lmao use simple_list_item_1 for now
                listItem.setListItem(android.R.layout.simple_list_item_1);
            }
            crashlytics.log("ViewPane: setting item count to EditorListItem");
            if (!TextUtils.isEmpty(itemCount)) {
                if (TextUtils.isEmpty(listitem)) {
                    try {
                        listItem.setItemCount(Integer.parseInt(itemCount));
                    } catch (Exception exception) {
                        crashlytics.recordException(exception);
                    }
                }
            }
        }
    }

    /**
     * Shows in the editor the attributes edited from the property panel. Views marked invisible or gone
     * are drawn faded instead of hidden, so they can still be selected.
     */
    private void applyInjectedAttributes(View view, ViewBean viewBean) {
        java.util.Map<String, String> attrs = InjectAttributes.parse(viewBean.inject);
        String visibility = attrs.get("android:visibility");
        if ("gone".equals(visibility) || "invisible".equals(visibility)) {
            view.setAlpha(viewBean.alpha * 0.35f);
        }
        // Editor items have their own minimum size (an empty layout stays droppable, an include
        // shows its placeholder); android:minWidth/minHeight replace it only when they are set.
        int[] editorMinimum = editorMinimums.computeIfAbsent(view, v -> new int[]{v.getMinimumWidth(), v.getMinimumHeight()});
        view.setMinimumWidth(dimenToPx(attrs.get("android:minWidth"), editorMinimum[0]));
        view.setMinimumHeight(dimenToPx(attrs.get("android:minHeight"), editorMinimum[1]));
        Integer backgroundTint = resolveAttrColor(attrs.get("android:backgroundTint"));
        androidx.core.view.ViewCompat.setBackgroundTintList(view, backgroundTint == null ? null : android.content.res.ColorStateList.valueOf(backgroundTint));

        if (view instanceof TextView textView) {
            String family = attrs.get("android:fontFamily");
            if (family != null && !family.startsWith("@")) {
                textView.setTypeface(Typeface.create(family, viewBean.text.textType));
            }
            String allCaps = attrs.get("android:textAllCaps");
            if (allCaps != null) textView.setAllCaps(Boolean.parseBoolean(allCaps));
            Integer maxLines = parseInt(attrs.get("android:maxLines"));
            if (maxLines != null && maxLines > 0) textView.setMaxLines(maxLines);
            Integer minLines = parseInt(attrs.get("android:minLines"));
            if (minLines != null && minLines > 0) textView.setMinLines(minLines);
            String ellipsize = attrs.get("android:ellipsize");
            textView.setEllipsize(switch (ellipsize == null ? "" : ellipsize) {
                case "start" -> TextUtils.TruncateAt.START;
                case "middle" -> TextUtils.TruncateAt.MIDDLE;
                case "end" -> TextUtils.TruncateAt.END;
                case "marquee" -> TextUtils.TruncateAt.MARQUEE;
                default -> null;
            });
            textView.setTextAlignment(switch (attrs.getOrDefault("android:textAlignment", "")) {
                case "inherit" -> View.TEXT_ALIGNMENT_INHERIT;
                case "textStart" -> View.TEXT_ALIGNMENT_TEXT_START;
                case "textEnd" -> View.TEXT_ALIGNMENT_TEXT_END;
                case "center" -> View.TEXT_ALIGNMENT_CENTER;
                case "viewStart" -> View.TEXT_ALIGNMENT_VIEW_START;
                case "viewEnd" -> View.TEXT_ALIGNMENT_VIEW_END;
                default -> View.TEXT_ALIGNMENT_GRAVITY;
            });
            Float letterSpacing = parseFloat(attrs.get("android:letterSpacing"));
            textView.setLetterSpacing(letterSpacing == null ? 0f : letterSpacing);
            Float multiplier = parseFloat(attrs.get("android:lineSpacingMultiplier"));
            textView.setLineSpacing(dimenToPx(attrs.get("android:lineSpacingExtra"), 0), multiplier == null ? 1f : multiplier);
            String includeFontPadding = attrs.get("android:includeFontPadding");
            textView.setIncludeFontPadding(includeFontPadding == null || Boolean.parseBoolean(includeFontPadding));
            int maxWidth = dimenToPx(attrs.get("android:maxWidth"), -1);
            textView.setMaxWidth(maxWidth < 0 ? Integer.MAX_VALUE : maxWidth);
            int maxHeight = dimenToPx(attrs.get("android:maxHeight"), -1);
            textView.setMaxHeight(maxHeight < 0 ? Integer.MAX_VALUE : maxHeight);
            textView.setCompoundDrawablePadding(dimenToPx(attrs.get("android:drawablePadding"), 0));
        }
        if (view instanceof ImageView imageView) {
            Integer tint = resolveAttrColor(attrs.get("android:tint"));
            androidx.core.widget.ImageViewCompat.setImageTintList(imageView, tint == null ? null : android.content.res.ColorStateList.valueOf(tint));
            imageView.setAdjustViewBounds(Boolean.parseBoolean(attrs.get("android:adjustViewBounds")));
            int maxWidth = dimenToPx(attrs.get("android:maxWidth"), -1);
            imageView.setMaxWidth(maxWidth < 0 ? Integer.MAX_VALUE : maxWidth);
            int maxHeight = dimenToPx(attrs.get("android:maxHeight"), -1);
            imageView.setMaxHeight(maxHeight < 0 ? Integer.MAX_VALUE : maxHeight);
        }
        if (view instanceof CompoundButton compoundButton) {
            Integer tint = resolveAttrColor(attrs.get("android:buttonTint"));
            androidx.core.widget.CompoundButtonCompat.setButtonTintList(compoundButton, tint == null ? null : android.content.res.ColorStateList.valueOf(tint));
        }
        if (view instanceof android.widget.Switch switchView) {
            Integer thumb = resolveAttrColor(attrs.get("android:thumbTint"));
            switchView.setThumbTintList(thumb == null ? null : android.content.res.ColorStateList.valueOf(thumb));
            Integer track = resolveAttrColor(attrs.get("android:trackTint"));
            switchView.setTrackTintList(track == null ? null : android.content.res.ColorStateList.valueOf(track));
        }
        if (view instanceof android.widget.ProgressBar progressBar) {
            Integer progress = resolveAttrColor(attrs.get("android:progressTint"));
            progressBar.setProgressTintList(progress == null ? null : android.content.res.ColorStateList.valueOf(progress));
            Integer background = resolveAttrColor(attrs.get("android:progressBackgroundTint"));
            progressBar.setProgressBackgroundTintList(background == null ? null : android.content.res.ColorStateList.valueOf(background));
            Integer indeterminate = resolveAttrColor(attrs.get("android:indeterminateTint"));
            progressBar.setIndeterminateTintList(indeterminate == null ? null : android.content.res.ColorStateList.valueOf(indeterminate));
            if (view instanceof SeekBar seekBar) {
                Integer thumb = resolveAttrColor(attrs.get("android:thumbTint"));
                seekBar.setThumbTintList(thumb == null ? null : android.content.res.ColorStateList.valueOf(thumb));
            }
        }
    }

    private int dimenToPx(String value, int fallback) {
        if (value == null || value.isEmpty()) return fallback;
        Matcher matcher = Pattern.compile("(-?\\d+(?:\\.\\d+)?)(dp|dip|sp|px)?").matcher(value.trim());
        if (!matcher.matches()) return fallback;
        float number = Float.parseFloat(matcher.group(1));
        String unit = matcher.group(2);
        if ("px".equals(unit)) return Math.round(number);
        if ("sp".equals(unit)) {
            return Math.round(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, number, getResources().getDisplayMetrics()));
        }
        return Math.round(wB.a(getContext(), number));
    }

    private Integer resolveAttrColor(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            if (value.startsWith("#")) return PropertiesUtil.parseColor(value);
            return PropertiesUtil.parseColor(colorsEditorManager.getColorValue(context, value, 3, material3LibraryManager.canUseNightVariantColors()));
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer parseInt(String value) {
        try {
            return value == null ? null : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Float parseFloat(String value) {
        try {
            return value == null ? null : Float.parseFloat(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public ItemView findItemViewByTag(String str) {
        View findViewWithTag = null;
        if (str.charAt(0) == '_') {
            findViewWithTag = findViewWithTag(str);
        } else {
            if (rootLayout != null) {
                findViewWithTag = rootLayout.findViewWithTag(str);
            }
        }
        if (findViewWithTag instanceof ItemView) {
            return (ItemView) findViewWithTag;
        }
        return null;
    }

    /**
     * Writes into the bean where a widget dropped at the current preview goes: parent, index, parent
     * type and the position model of that parent (RelativeLayout rules and ConstraintLayout
     * constraints at the drop point, FrameLayout gravity). Returns false when there is no valid place,
     * e.g. outside the layout or on a single-child container that is already full.
     */
    public boolean updateViewBeanProperties(ViewBean viewBean, int x, int y) {
        ViewInfo target = viewInfo;
        if (target == null) {
            // Outside every target: the end of the root layout, if the root can take another child.
            if (rootLayout == null || !acceptsAnotherChild(rootLayout, viewBean)) return false;
            viewBean.preIndex = viewBean.index;
            viewBean.preParent = viewBean.parent;
            viewBean.parent = "root";
            viewBean.preParentType = viewBean.parentType;
            if (rootLayout instanceof ItemView sy) {
                viewBean.parentType = sy.getBean().type;
            } else {
                viewBean.parentType = ViewBean.VIEW_TYPE_LAYOUT_LINEAR;
            }
            viewBean.index = -1;
            prepareRelationsForDrop(viewBean);
            return true;
        }
        View view = target.view();
        if (view.getTag() == null) return false;
        String newParent = view.getTag().toString();
        // Only a widget that is already a child of the target is moved inside it.
        boolean sameParent = view instanceof ViewGroup group && childWithTag(group, viewBean.id) != null;
        viewBean.preIndex = viewBean.index;
        viewBean.preParent = viewBean.parent;
        viewBean.parent = newParent;
        viewBean.preParentType = viewBean.parentType;
        viewBean.index = target.index();
        if (view instanceof LinearLayout) {
            viewBean.parentType = ViewBean.VIEW_TYPE_LAYOUT_LINEAR;
        } else if (view instanceof ItemVerticalScrollView) {
            viewBean.parentType = ViewBean.VIEW_TYPE_LAYOUT_VSCROLLVIEW;
            viewBean.layout.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        } else if (view instanceof ItemHorizontalScrollView) {
            viewBean.parentType = ViewBean.VIEW_TYPE_LAYOUT_HSCROLLVIEW;
            viewBean.layout.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        } else if (view instanceof ItemCardView) {
            viewBean.parentType = ViewBeans.VIEW_TYPE_LAYOUT_CARDVIEW;
            viewBean.layout.width = ViewGroup.LayoutParams.MATCH_PARENT;
        } else if (view instanceof ItemRelativeLayout) {
            viewBean.parentType = ViewBean.VIEW_TYPE_LAYOUT_RELATIVE;
        } else if (view instanceof ItemConstraintLayout) {
            viewBean.parentType = ViewBeans.VIEW_TYPE_LAYOUT_CONSTRAINTLAYOUT;
        } else if (view instanceof ItemFrameLayout) {
            viewBean.parentType = ViewBeans.VIEW_TYPE_LAYOUT_FRAMELAYOUT;
        }
        prepareRelationsForDrop(viewBean);
        if (view instanceof ItemRelativeLayout || view instanceof ItemConstraintLayout || view instanceof ItemFrameLayout) {
            // In these containers the order is only the drawing order: a move inside the same
            // container keeps it, a new widget goes on top.
            if (sameParent && viewBean.preIndex >= 0) viewBean.index = viewBean.preIndex;
        }
        if (view instanceof ItemRelativeLayout || view instanceof ItemConstraintLayout) {
            placeDroppedBean(viewBean, view);
        } else if (view instanceof ItemFrameLayout) {
            // FrameLayout has no relations: only layout_gravity (and the margins) position a child.
            viewBean.parentAttributes = new HashMap<>();
            if (dropGroup == view && dropGravity >= 0) {
                viewBean.layout.layoutGravity = dropGravity;
            }
        }
        return true;
    }

    /** Whether a single-child container (ScrollView, CardView) still has room for {@code bean}. */
    private static boolean acceptsAnotherChild(ViewGroup group, ViewBean bean) {
        if (!(group instanceof ItemVerticalScrollView) && !(group instanceof ItemHorizontalScrollView)
                && !(group instanceof ItemCardView)) {
            return true;
        }
        for (int i = 0; i < group.getChildCount(); i++) {
            if (isDropNeighbour(group.getChildAt(i), bean)) return false;
        }
        return true;
    }

    public View addFab(ViewBean viewBean) {
        View findViewWithTag = findViewWithTag("_fab");
        if (findViewWithTag != null) {
            return findViewWithTag;
        }
        ItemFloatingActionButton itemFloatingActionButton = new ItemFloatingActionButton(context);
        itemFloatingActionButton.setTag("_fab");
        itemFloatingActionButton.setLayoutParams(new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        itemFloatingActionButton.setMainColor(ProjectFile.getColor(sc_id, ProjectFile.COLOR_ACCENT));
        itemFloatingActionButton.setFixed(true);
        if (viewBean == null) {
            ViewBean viewBean2 = new ViewBean("_fab", ViewBean.VIEW_TYPE_WIDGET_FAB);
            LayoutBean layoutBean = viewBean2.layout;
            layoutBean.marginLeft = 16;
            layoutBean.marginTop = 16;
            layoutBean.marginRight = 16;
            layoutBean.marginBottom = 16;
            layoutBean.layoutGravity = Gravity.RIGHT | Gravity.BOTTOM;
            itemFloatingActionButton.setBean(viewBean2);
        } else {
            itemFloatingActionButton.setBean(viewBean);
        }
        addView(itemFloatingActionButton);
        updateItemView(itemFloatingActionButton, itemFloatingActionButton.getBean());
        return itemFloatingActionButton;
    }

    public void resetView(boolean shouldClearViewInfo) {
        highlightedTextView.setVisibility(View.GONE);
        ViewParent parent = highlightedTextView.getParent();
        if (parent != null) {
            ((ViewGroup) parent).removeView(highlightedTextView);
        }
        if (shouldClearViewInfo) {
            viewInfo = null;
            dropPoint = null;
            dropGravity = -1;
            dropGroup = null;
            if (relationsOverlay != null) relationsOverlay.setDropTarget(null, -1);
        }
    }

    /** LayoutParams of the drop preview for the container it's shown in. */
    private static ViewGroup.LayoutParams previewParams(ViewGroup group, int width, int height) {
        if (group instanceof LinearLayout) return new LinearLayout.LayoutParams(width, height);
        if (group instanceof ConstraintLayout) return new ConstraintLayout.LayoutParams(width, height);
        if (group instanceof RelativeLayout) return new RelativeLayout.LayoutParams(width, height);
        if (group instanceof FrameLayout) return new FrameLayout.LayoutParams(width, height);
        return new ViewGroup.MarginLayoutParams(width, height);
    }

    /** Shows the drop preview for a widget dragged to (x, y) on screen. */
    public void updateView(int x, int y, int width, int height) {
        ViewInfo target = getViewInfo(x, y);
        if (target == null) {
            resetView(true);
            return;
        }
        ViewGroup group = (ViewGroup) target.view();
        if (this.viewInfo != target || highlightedTextView.getParent() != group) {
            resetView(true);
            highlightedTextView.setLayoutParams(previewParams(group, width, height));
            // The container maps the widget index to a valid child position (see EditorChildren).
            group.addView(highlightedTextView, target.index());
            highlightedTextView.setVisibility(View.VISIBLE);
            this.viewInfo = target;
        }
        positionHighlight(group, x, y, width, height);
        if (relationsOverlay != null) relationsOverlay.setDropTarget(group, dropGravity);
    }

    /** Thirds of a length: start, middle, end. */
    private static int third(float position, int length, int start, int middle, int end) {
        if (length <= 0) return start;
        float fraction = position / length;
        return fraction < 1f / 3f ? start : fraction > 2f / 3f ? end : middle;
    }

    /**
     * In RelativeLayout and ConstraintLayout a widget lands where the finger is, so the drop preview
     * follows the finger and the position is kept for {@link #updateViewBeanProperties}. A FrameLayout
     * places children only with layout_gravity, so the finger picks one of its nine gravity zones.
     */
    private void positionHighlight(ViewGroup group, int x, int y, int width, int height) {
        dropPoint = null;
        dropGravity = -1;
        dropGroup = group;
        boolean frame = group instanceof ItemFrameLayout;
        if (!frame && !(group instanceof ItemRelativeLayout) && !(group instanceof ItemConstraintLayout)) {
            return;
        }
        int[] location = new int[2];
        group.getLocationOnScreen(location);
        float scale = getScaleX() <= 0 ? 1f : getScaleX();
        float localX = (x - location[0]) / scale;
        float localY = (y - location[1]) / scale;
        int contentWidth = group.getWidth() - group.getPaddingLeft() - group.getPaddingRight();
        int contentHeight = group.getHeight() - group.getPaddingTop() - group.getPaddingBottom();
        if (frame) {
            int horizontal = third(localX - group.getPaddingLeft(), contentWidth, Gravity.LEFT, Gravity.CENTER_HORIZONTAL, Gravity.RIGHT);
            int vertical = third(localY - group.getPaddingTop(), contentHeight, Gravity.TOP, Gravity.CENTER_VERTICAL, Gravity.BOTTOM);
            int gravity = horizontal | vertical;
            // top|left is where FrameLayout puts a child without layout_gravity.
            dropGravity = gravity == (Gravity.LEFT | Gravity.TOP) ? LayoutBean.GRAVITY_NONE : gravity;
            highlightedTextView.setLayoutParams(new FrameLayout.LayoutParams(width, height, gravity));
            return;
        }
        int left = width > 0 ? Math.round(localX - width / 2f) - group.getPaddingLeft() : 0;
        int top = height > 0 ? Math.round(localY - height / 2f) - group.getPaddingTop() : 0;
        left = Math.max(0, Math.min(left, Math.max(0, contentWidth - Math.max(width, 0))));
        top = Math.max(0, Math.min(top, Math.max(0, contentHeight - Math.max(height, 0))));
        ViewGroup.MarginLayoutParams params;
        if (group instanceof ItemConstraintLayout) {
            ConstraintLayout.LayoutParams constraintParams = new ConstraintLayout.LayoutParams(width, height);
            constraintParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
            constraintParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
            params = constraintParams;
        } else {
            params = new LayoutParams(width, height);
        }
        params.leftMargin = left;
        params.topMargin = top;
        highlightedTextView.setLayoutParams(params);
        float density = getResources().getDisplayMetrics().density;
        dropPoint = new android.graphics.Point(Math.round(left / density), Math.round(top / density));
        dropGroup = group;
    }

    /** Positions a widget dropped into a RelativeLayout/ConstraintLayout at the drop point. */
    private void placeDroppedBean(ViewBean viewBean, View group) {
        if (dropPoint == null || dropGroup != group || !(group instanceof ViewGroup viewGroup)) return;
        boolean sameParent = viewBean.preParent != null && viewBean.preParent.equals(viewBean.parent);
        View existing = sameParent ? viewGroup.findViewWithTag(viewBean.id) : null;
        if (existing != null) {
            // Moved inside the same parent: keep its relations, change margins or bias.
            float density = getResources().getDisplayMetrics().density;
            int currentLeft = Math.round((existing.getLeft() - viewGroup.getPaddingLeft()) / density);
            int currentTop = Math.round((existing.getTop() - viewGroup.getPaddingTop()) / density);
            int freeX = Math.round((viewGroup.getWidth() - viewGroup.getPaddingLeft() - viewGroup.getPaddingRight() - existing.getWidth()) / density);
            int freeY = Math.round((viewGroup.getHeight() - viewGroup.getPaddingTop() - viewGroup.getPaddingBottom() - existing.getHeight()) / density);
            LayoutRelations.moveBy(viewBean, dropPoint.x - currentLeft, dropPoint.y - currentTop, currentLeft, currentTop, freeX, freeY);
        } else {
            LayoutRelations.placeAt(viewBean, dropPoint.x, dropPoint.y);
        }
        dropPoint = null;
    }

    private ViewInfo getViewInfo(int x, int y) {
        ViewInfo result = null;
        int highestPriority = -1;
        for (ViewInfo viewInfo : viewInfos) {
            if (viewInfo.rect().contains(x, y) && highestPriority < viewInfo.depth()) {
                highestPriority = viewInfo.depth();
                result = viewInfo;
            }
        }
        return result;
    }

    /** Screen rectangle of a view inside the (scaled) pane. */
    private Rect screenRect(View view) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        float scaleX = getScaleX() <= 0 ? 1f : getScaleX();
        float scaleY = getScaleY() <= 0 ? 1f : getScaleY();
        return new Rect(location[0], location[1],
                location[0] + Math.round(view.getWidth() * scaleX),
                location[1] + Math.round(view.getHeight() * scaleY));
    }

    /** A child that can receive a drop or be dropped next to: a visible widget other than the dragged one. */
    private static boolean isDropNeighbour(View child, ViewBean dragged) {
        return child instanceof ItemView && child.getTag() != null && child.getVisibility() == View.VISIBLE
                && (dragged == null || dragged.id == null || !child.getTag().equals(dragged.id));
    }

    /** Adds the drop targets inside a child container, whatever its kind. */
    private void addNestedTargets(ViewBean dragged, View child) {
        if (child instanceof ItemLinearLayout linearLayout) {
            a(dragged, linearLayout);
        } else if (child instanceof ItemHorizontalScrollView || child instanceof ItemVerticalScrollView
                || child instanceof ItemCardView) {
            a(dragged, (ViewGroup) child);
        } else if (child instanceof ItemRelativeLayout || child instanceof ItemConstraintLayout
                || child instanceof ItemFrameLayout) {
            addDroppableForViewGroup(dragged, (ViewGroup) child);
        }
    }

    /**
     * Drop targets of a LinearLayout, as Android Studio places widgets in one: the widget goes before
     * the first child whose middle is past the finger, or at the end. Each zone runs from the middle
     * of the previous child to the middle of the next one, measured on the children as they are laid
     * out, so gravity, margins, padding and zoom are all taken into account.
     */
    private void a(ViewBean dragged, ItemLinearLayout linearLayout) {
        Rect bounds = screenRect(linearLayout);
        int depth = calculateViewDepth(linearLayout);
        // Anywhere past the last middle: append.
        addViewInfo(bounds, linearLayout, -1, depth);
        boolean vertical = linearLayout.getOrientation() == LinearLayout.VERTICAL;
        int previousEdge = vertical ? bounds.top : bounds.left;
        int index = 0;
        for (int i = 0; i < linearLayout.getChildCount(); i++) {
            View child = linearLayout.getChildAt(i);
            if (!isDropNeighbour(child, dragged)) continue;
            Rect childRect = screenRect(child);
            int middle = vertical ? childRect.centerY() : childRect.centerX();
            if (middle > previousEdge) {
                Rect zone = vertical
                        ? new Rect(bounds.left, previousEdge, bounds.right, middle)
                        : new Rect(previousEdge, bounds.top, middle, bounds.bottom);
                addViewInfo(zone, linearLayout, index, depth + 1);
            }
            previousEdge = Math.max(previousEdge, middle);
            index++;
            addNestedTargets(dragged, child);
        }
    }

    /**
     * RelativeLayout, ConstraintLayout and FrameLayout accept a drop anywhere inside them; the drop
     * position is worked out while the finger moves (see {@link #positionHighlight}).
     */
    private void addDroppableForViewGroup(ViewBean viewBean, ViewGroup viewGroup) {
        addViewInfo(screenRect(viewGroup), viewGroup, -1, calculateViewDepth(viewGroup));
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            if (isDropNeighbour(child, viewBean)) {
                addNestedTargets(viewBean, child);
            }
        }
    }

    /** ScrollView, HorizontalScrollView and CardView hold a single child: they're a target only when empty. */
    private void a(ViewBean viewBean, ViewGroup viewGroup) {
        int children = 0;
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            if (isDropNeighbour(child, viewBean)) {
                children++;
                addNestedTargets(viewBean, child);
            }
        }
        if (children < 1) {
            addViewInfo(screenRect(viewGroup), viewGroup, -1, calculateViewDepth(viewGroup));
        }
    }

    private void addViewInfo(Rect rect, View view, int i, int i2) {
        viewInfos.add(new ViewInfo(rect, view, i, i2));
    }

    /**
     * Adds a widget's view to the container named by its bean, at its bean index. The view is
     * detached first if it's still attached somewhere, the index is mapped by the container (see
     * {@link EditorChildren}), and a parent that doesn't exist or lies inside the view itself makes the
     * widget fall back to the root layout instead of failing.
     */
    public void addViewAndUpdateIndex(View view) {
        if (rootLayout == null || !(view instanceof ItemView item) || view == rootLayout) return;
        ViewBean bean = item.getBean();
        ViewGroup parent = findContainer(bean.parent);
        if (parent == null || containsOrIs(view, parent)) {
            Log.w(TAG, "Widget " + bean.id + " has no valid parent '" + bean.parent + "', placing it in the root layout");
            parent = rootLayout;
            bean.parent = "root";
            if (rootLayout instanceof ItemView root && root.getBean() != null) {
                bean.parentType = root.getBean().type;
            }
        }
        if (view.getParent() instanceof ViewGroup current) {
            current.removeView(view);
        }
        if (!(view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) {
            updateLayout(view, bean, parent);
        }
        parent.addView(view, bean.index);
        if (parent instanceof ScrollContainer scrollContainer) {
            scrollContainer.reindexChildren();
        }
        refreshPositionRules(parent);
        if (relationsOverlay != null) relationsOverlay.invalidate();
    }

    private int getActualParentType(View view, int defaultValue) {
        var parent = (ViewGroup) view.getParent();
        if (parent != null) {
            if (parent instanceof ItemLinearLayout) {
                return ViewBean.VIEW_TYPE_LAYOUT_LINEAR;
            } else if (parent instanceof ItemRelativeLayout) {
                return ViewBean.VIEW_TYPE_LAYOUT_RELATIVE;
            } else if (parent instanceof ItemConstraintLayout) {
                return ViewBeans.VIEW_TYPE_LAYOUT_CONSTRAINTLAYOUT;
            } else if (parent instanceof ItemFrameLayout) {
                return ViewBeans.VIEW_TYPE_LAYOUT_FRAMELAYOUT;
            } else if (parent instanceof ItemCardView) {
                return ViewBeans.VIEW_TYPE_LAYOUT_CARDVIEW;
            } else if (parent instanceof ItemHorizontalScrollView) {
                return ViewBean.VIEW_TYPE_LAYOUT_HSCROLLVIEW;
            } else if (parent instanceof ItemVerticalScrollView) {
                return ViewBean.VIEW_TYPE_LAYOUT_VSCROLLVIEW;
            }
        }
        return defaultValue;
    }

    /**
     * The editor container that holds (or will hold) this widget. Views being created aren't attached
     * yet, so their bean's parent id is used.
     */
    private ViewGroup parentContainerOf(View view, ViewBean viewBean) {
        if (view.getParent() instanceof ViewGroup parent && parent != this) return parent;
        return findContainer(viewBean.parent);
    }

    /**
     * The positioning model of a container, from the container itself: the LayoutParams class must
     * match the real parent view or its onMeasure fails. The bean's parent type is only used while
     * the parent isn't known.
     */
    private static Container containerKind(ViewGroup container, ViewBean viewBean) {
        if (container instanceof LinearLayout) return Container.LINEAR;
        if (container instanceof ConstraintLayout) return Container.CONSTRAINT;
        if (container instanceof RelativeLayout) return Container.RELATIVE;
        if (container instanceof FrameLayout) return Container.FRAME;
        return switch (viewBean.parentType) {
            case ViewBean.VIEW_TYPE_LAYOUT_LINEAR, ViewBeans.VIEW_TYPE_LAYOUT_RADIOGROUP,
                 ViewBeans.VIEW_TYPE_LAYOUT_TEXTINPUTLAYOUT -> Container.LINEAR;
            case ViewBean.VIEW_TYPE_LAYOUT_RELATIVE -> Container.RELATIVE;
            case ViewBeans.VIEW_TYPE_LAYOUT_CONSTRAINTLAYOUT -> Container.CONSTRAINT;
            default -> Container.FRAME;
        };
    }

    /**
     * Builds the LayoutParams of a widget from its bean for the container it lives in. Only size,
     * margins, padding, weight and layout_gravity come from here; RelativeLayout rules and constraints
     * are applied on top by {@link #refreshPositionRules}. Scale, rotation and translation are separate
     * view properties and are never touched by the layout.
     */
    private Container updateLayout(View view, ViewBean viewBean, ViewGroup container) {
        crashlytics.log("ViewPane: Updating layout");
        LayoutBean layoutBean = viewBean.layout;
        int width = layoutBean.width;
        int height = layoutBean.height;
        if (width > 0) {
            width = (int) wB.a(getContext(), (float) viewBean.layout.width);
        }
        if (height > 0) {
            height = (int) wB.a(getContext(), (float) viewBean.layout.height);
        }

        int leftMargin = (int) wB.a(getContext(), (float) viewBean.layout.marginLeft);
        int topMargin = (int) wB.a(getContext(), (float) viewBean.layout.marginTop);
        int rightMargin = (int) wB.a(getContext(), (float) viewBean.layout.marginRight);
        int bottomMargin = (int) wB.a(getContext(), (float) viewBean.layout.marginBottom);

        if (viewBean.layout.backgroundResColor == null) {
            view.setBackgroundColor(viewBean.layout.backgroundColor);
        } else {
            view.setBackgroundColor(PropertiesUtil.parseColor(colorsEditorManager.getColorValue(context, viewBean.layout.backgroundResColor, 3, material3LibraryManager.canUseNightVariantColors())));
        }
        view.setPadding(layoutBean.paddingLeft, layoutBean.paddingTop, layoutBean.paddingRight, layoutBean.paddingBottom);
        int layoutGravity = viewBean.layout.layoutGravity;
        Container kind = view == rootLayout ? Container.RELATIVE : containerKind(container, viewBean);
        ViewGroup.MarginLayoutParams params;
        switch (kind) {
            case LINEAR -> {
                LinearLayout.LayoutParams linearParams = new LinearLayout.LayoutParams(width, height);
                if (layoutGravity != LayoutBean.GRAVITY_NONE) {
                    linearParams.gravity = layoutGravity;
                }
                linearParams.weight = viewBean.layout.weight;
                params = linearParams;
            }
            case RELATIVE -> params = new RelativeLayout.LayoutParams(width, height);
            // 0dp is "match constraint" in a ConstraintLayout.
            case CONSTRAINT -> params = new ConstraintLayout.LayoutParams(
                    viewBean.layout.width == 0 ? 0 : width, viewBean.layout.height == 0 ? 0 : height);
            default -> {
                FrameLayout.LayoutParams frameParams = new FrameLayout.LayoutParams(width, height);
                if (layoutGravity != LayoutBean.GRAVITY_NONE) {
                    frameParams.gravity = layoutGravity;
                }
                params = frameParams;
            }
        }
        params.setMargins(leftMargin, topMargin, rightMargin, bottomMargin);
        view.setLayoutParams(params);
        return kind;
    }

    /** Rules like layout_below or constraints point at the old siblings; after a move they don't apply. */
    private void prepareRelationsForDrop(ViewBean viewBean) {
        if (viewBean.preParent != null && !viewBean.preParent.isEmpty() && !viewBean.preParent.equals(viewBean.parent)) {
            viewBean.parentAttributes = new HashMap<>();
        }
    }

    /**
     * Re-applies the RelativeLayout rules or the constraints of every child of {@code parent}, e.g.
     * after a sibling they point to was added, moved or removed.
     */
    private void refreshPositionRules(ViewGroup parent) {
        if (parent instanceof ItemRelativeLayout) {
            java.util.Map<String, java.util.Map<String, String>> rulesById = new java.util.HashMap<>();
            for (int i = 0; i < parent.getChildCount(); i++) {
                if (parent.getChildAt(i) instanceof ItemView item && item.getBean() != null) {
                    rulesById.put(item.getBean().id, relativeRules(item.getBean()));
                }
            }
            java.util.Set<String> cyclic = LayoutRelations.cyclicRules(rulesById);
            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                if (child instanceof ItemView item && item.getBean() != null) {
                    applyRelativeRules(child, item.getBean().id, rulesById.get(item.getBean().id), cyclic, parent);
                }
            }
        } else if (parent instanceof ItemConstraintLayout) {
            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                if (child instanceof ItemView editorItem && editorItem.getBean() != null) {
                    updateConstraints(child, editorItem.getBean(), parent);
                }
            }
        }
    }

    /**
     * RelativeLayout rules of a widget, "android:layout_below" → "sibling_id", from the attributes
     * typed in the inject field and from its parent attributes (which win, as they're written last).
     */
    private static java.util.Map<String, String> relativeRules(ViewBean bean) {
        java.util.Map<String, String> rules = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String, String> entry : InjectAttributes.parse(bean.inject).entrySet()) {
            addRelativeRule(rules, entry.getKey(), entry.getValue());
        }
        for (java.util.Map.Entry<String, String> entry : bean.parentAttributes.entrySet()) {
            addRelativeRule(rules, entry.getKey(), entry.getValue());
        }
        return rules;
    }

    private static void addRelativeRule(java.util.Map<String, String> rules, String key, String value) {
        if (key == null || value == null || !key.startsWith("android:")) return;
        if (!RELATIVE_RULES.containsKey(key.substring("android:".length()))) return;
        rules.put(key, LayoutRelations.referenceId(value));
    }

    /**
     * Rebuilds the rules of a RelativeLayout child on its existing LayoutParams (so size and margins
     * are kept). A rule that points at a missing view or at a non-sibling is ignored, as RelativeLayout
     * does, and rules on a circular dependency are left out instead of letting RelativeLayout throw.
     */
    private static void applyRelativeRules(View view, String id, java.util.Map<String, String> rules,
                                           java.util.Set<String> cyclic, ViewGroup parent) {
        if (!(view.getLayoutParams() instanceof RelativeLayout.LayoutParams params)) return;
        for (int verb : RELATIVE_RULES.values()) {
            params.removeRule(verb);
        }
        if (rules != null) {
            for (java.util.Map.Entry<String, String> rule : rules.entrySet()) {
                Integer verb = RELATIVE_RULES.get(rule.getKey().substring("android:".length()));
                if (verb == null) continue;
                if (RELATIVE_BOOLEAN_RULES.contains(verb)) {
                    if ("true".equalsIgnoreCase(rule.getValue())) params.addRule(verb);
                } else if (!cyclic.contains(id + " " + rule.getKey())) {
                    View anchor = childWithTag(parent, rule.getValue());
                    if (anchor != null && anchor != view) params.addRule(verb, anchor.getId());
                }
            }
        }
        view.setLayoutParams(params);
    }

    /** The direct child of {@code parent} with this widget id, or null. */
    private static View childWithTag(ViewGroup parent, String id) {
        if (id == null) return null;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (id.equals(child.getTag())) return child;
        }
        return null;
    }

    /** Applies app:layout_constraint* parent attributes to a child of a ConstraintLayout. */
    private void updateConstraints(View view, ViewBean bean, ViewGroup parent) {
        if (!(view.getLayoutParams() instanceof ConstraintLayout.LayoutParams params)) return;
        params.leftToLeft = params.leftToRight = params.rightToLeft = params.rightToRight = ConstraintLayout.LayoutParams.UNSET;
        params.topToTop = params.topToBottom = params.bottomToTop = params.bottomToBottom = ConstraintLayout.LayoutParams.UNSET;
        params.startToStart = params.startToEnd = params.endToStart = params.endToEnd = ConstraintLayout.LayoutParams.UNSET;
        params.baselineToBaseline = ConstraintLayout.LayoutParams.UNSET;
        params.horizontalBias = 0.5f;
        params.verticalBias = 0.5f;
        params.dimensionRatio = null;
        params.matchConstraintDefaultWidth = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT_SPREAD;
        params.matchConstraintDefaultHeight = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT_SPREAD;
        for (java.util.Map.Entry<String, String> entry : bean.parentAttributes.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || value == null || !key.startsWith("app:layout_constraint")) continue;
            String attr = key.substring("app:layout_constraint".length());
            switch (attr) {
                case "Horizontal_bias" -> params.horizontalBias = parseBias(value);
                case "Vertical_bias" -> params.verticalBias = parseBias(value);
                case "DimensionRatio" -> params.dimensionRatio = value;
                case "Width_percent" -> {
                    params.matchConstraintDefaultWidth = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT_PERCENT;
                    params.matchConstraintPercentWidth = parseBias(value);
                }
                case "Height_percent" -> {
                    params.matchConstraintDefaultHeight = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT_PERCENT;
                    params.matchConstraintPercentHeight = parseBias(value);
                }
                default -> {
                    int target = resolveConstraintTarget(value, view, parent);
                    if (target == ConstraintLayout.LayoutParams.UNSET) continue;
                    switch (attr) {
                        case "Left_toLeftOf" -> params.leftToLeft = target;
                        case "Left_toRightOf" -> params.leftToRight = target;
                        case "Right_toLeftOf" -> params.rightToLeft = target;
                        case "Right_toRightOf" -> params.rightToRight = target;
                        case "Top_toTopOf" -> params.topToTop = target;
                        case "Top_toBottomOf" -> params.topToBottom = target;
                        case "Bottom_toTopOf" -> params.bottomToTop = target;
                        case "Bottom_toBottomOf" -> params.bottomToBottom = target;
                        case "Start_toStartOf" -> params.startToStart = target;
                        case "Start_toEndOf" -> params.startToEnd = target;
                        case "End_toStartOf" -> params.endToStart = target;
                        case "End_toEndOf" -> params.endToEnd = target;
                        case "Baseline_toBaselineOf" -> params.baselineToBaseline = target;
                    }
                }
            }
        }
        view.setLayoutParams(params);
    }

    /** "parent" or a sibling id; constraints to anything else are ignored, as ConstraintLayout does. */
    private static int resolveConstraintTarget(String value, View view, ViewGroup parent) {
        if ("parent".equals(value)) return ConstraintLayout.LayoutParams.PARENT_ID;
        if (parent == null) return ConstraintLayout.LayoutParams.UNSET;
        View target = childWithTag(parent, LayoutRelations.referenceId(value));
        return target == null || target == view ? ConstraintLayout.LayoutParams.UNSET : target.getId();
    }

    private static float parseBias(String value) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return 0.5f;
        }
    }

    private void updateTextView(TextView textView, ViewBean viewBean) {
        String str = viewBean.text.text;
        if (str != null && str.contains("\\n")) {
            str = viewBean.text.text.replaceAll("\\\\n", "\n");
        }
        textView.setText(str.startsWith(stringsStart) ? getXmlString(str) : str);
        String textFont = new InjectAttributeHandler(viewBean).getAttributeValueOf("fontFamily");
        if (textFont != null && !textFont.isEmpty()) {
            if (textFont.startsWith("@font/")) {
                textFont = textFont.substring(6);
                String textFontPath =
                        new ResourceUtil(sc_id, "font").getResourcePathFromName(textFont);
                textView.setTypeface(
                        textFontPath != null
                                && !textFontPath.isEmpty()
                                && new File(textFontPath).exists()
                                ? Typeface.createFromFile(textFontPath)
                                : null,
                        viewBean.text.textType);
            } else {
                textView.setTypeface(null, viewBean.text.textType);
            }
        } else {
            textView.setTypeface(null, viewBean.text.textType);
        }
        if (defaultTextColor == 0) {
            defaultTextColor = textView.getTextColors().getDefaultColor();
        }
        if (viewBean.text.resTextColor == null) {
            textView.setTextColor(
                    viewBean.text.textColor == 0xffffff ? defaultTextColor : viewBean.text.textColor
            );
        } else {
            textView.setTextColor(PropertiesUtil.parseColor(colorsEditorManager.getColorValue(context, viewBean.text.resTextColor, 3, material3LibraryManager.canUseNightVariantColors())));
        }
        textView.setTextSize(viewBean.text.textSize);
        textView.setLines(viewBean.text.line);
        textView.setSingleLine(viewBean.text.singleLine != 0);
    }

    public String getXmlString(String key) {
        if (sc_id == null) {
            return key;
        }
        HashMap<String, String> strings = getProjectStrings();
        String value = strings.get(key.substring(stringsStart.length()).trim());
        if (value != null) {
            return value;
        }
        if (key.equals("@string/app_name")) {
            return yB.c(lC.b(sc_id), "my_app_name");
        }
        return key;
    }

    /** strings.xml parsed once per change instead of once per widget. */
    private HashMap<String, String> getProjectStrings() {
        File file = new File(wq.b(sc_id) + "/files/resource/values/strings.xml");
        long stamp = (file.exists() ? file.lastModified() * 31 + file.length() : 0) * 31 + ProjectStrings.version;
        if (cachedStrings == null || stamp != cachedStringsStamp) {
            ArrayList<HashMap<String, Object>> stringsListMap = new ArrayList<>();
            new StringsEditorManager().convertXmlStringsToListMap(FileUtil.readFileIfExist(file.getAbsolutePath()), stringsListMap);
            HashMap<String, String> strings = new HashMap<>();
            for (HashMap<String, Object> map : stringsListMap) {
                strings.put(String.valueOf(map.get("key")).trim(), String.valueOf(map.get("text")));
            }
            cachedStrings = strings;
            cachedStringsStamp = stamp;
        }
        return cachedStrings;
    }

    private void updateEditText(EditText editText, ViewBean viewBean) {
        String str = viewBean.text.hint;
        editText.setHint(str.startsWith(stringsStart) ? getXmlString(str) : str);
        if (defaultHintColor == 0) {
            defaultHintColor = editText.getHintTextColors().getDefaultColor();
        }
        if (viewBean.text.resHintColor == null) {
            editText.setHintTextColor(
                    viewBean.text.hintColor == 0xffffff ? defaultHintColor : viewBean.text.hintColor
            );
        } else {
            editText.setHintTextColor(PropertiesUtil.parseColor(colorsEditorManager.getColorValue(context, viewBean.text.resHintColor, 3, material3LibraryManager.canUseNightVariantColors())));
        }
    }

    private void updateCardView(ItemCardView cardView, InjectAttributeHandler handler) {
        var bean = handler.getBean();
        String cardBackgroundColor = handler.getAttributeValueOf("cardBackgroundColor");
        String cardElevation = handler.getAttributeValueOf("cardElevation");
        String cardCornerRadius = handler.getAttributeValueOf("cardCornerRadius");
        String compatPadding = handler.getAttributeValueOf("cardUseCompatPadding");
        String strokeColor = handler.getAttributeValueOf("strokeColor");
        String strokeWidth = handler.getAttributeValueOf("strokeWidth");

        if (cardBackgroundColor.isEmpty()) {
            if (bean.layout.backgroundResColor == null) {
                cardView.setCardBackgroundColor(bean.layout.backgroundColor);
            } else {
                cardView.setCardBackgroundColor(PropertiesUtil.parseColor(colorsEditorManager.getColorValue(context, bean.layout.backgroundResColor, 3, material3LibraryManager.canUseNightVariantColors())));
            }
        } else {
            cardView.setCardBackgroundColor(PropertiesUtil.parseColor(colorsEditorManager.getColorValue(context, cardBackgroundColor, 3, material3LibraryManager.canUseNightVariantColors())));
        }

        cardView.setCardElevation(PropertiesUtil.resolveSize(cardElevation, 4));
        cardView.setRadius(PropertiesUtil.resolveSize(cardCornerRadius, 8));
        cardView.setUseCompatPadding(Boolean.parseBoolean(TextUtils.isEmpty(compatPadding) ? "false" : compatPadding));
        cardView.setStrokeWidth(PropertiesUtil.resolveSize(strokeWidth, 0));
        cardView.setStrokeColor(PropertiesUtil.isHexColor(strokeColor) ? PropertiesUtil.parseColor(strokeColor) : Color.WHITE);
    }

    private void updateCircleImageView(ItemCircleImageView imageView, InjectAttributeHandler handler) {
        String borderColor = handler.getAttributeValueOf("civ_border_color");
        String backgroundColor = handler.getAttributeValueOf("civ_circle_background_color");
        String borderWidth = handler.getAttributeValueOf("civ_border_width");
        String borderOverlay = handler.getAttributeValueOf("civ_border_overlay");

        imageView.setBorderColor(PropertiesUtil.isHexColor(borderColor) ? PropertiesUtil.parseColor(borderColor) : 0xff008dcd);
        imageView.setCircleBackgroundColor(PropertiesUtil.isHexColor(backgroundColor) ? PropertiesUtil.parseColor(backgroundColor) : 0xff008dcd);
        imageView.setBorderWidth(PropertiesUtil.resolveSize(borderWidth, 3));
        imageView.setBorderOverlay(Boolean.parseBoolean(TextUtils.isEmpty(borderOverlay) ? "false" : borderOverlay));
    }

    private void updateTabLayout(ItemTabLayout tabLayout, InjectAttributeHandler handler) {
        String gravity = handler.getAttributeValueOf("tabGravity");
        String mode = handler.getAttributeValueOf("tabMode");
        String indicatorHeight = handler.getAttributeValueOf("tabIndicatorHeight");
        String indicatorColor = handler.getAttributeValueOf("tabIndicatorColor");
        String textColor = handler.getAttributeValueOf("tabTextColor");
        String selectedTextColor = handler.getAttributeValueOf("tabSelectedTextColor");

        tabLayout.setTabGravity(switch (gravity) {
            case "center" -> TabLayout.GRAVITY_CENTER;
            case "start" -> TabLayout.GRAVITY_START;
            default -> TabLayout.GRAVITY_FILL;
        });
        tabLayout.setTabMode(switch (mode) {
            case "auto" -> TabLayout.MODE_AUTO;
            case "scrollable" -> TabLayout.MODE_SCROLLABLE;
            default -> TabLayout.MODE_FIXED;
        });
        tabLayout.setSelectedTabIndicatorHeight(PropertiesUtil.resolveSize(indicatorHeight, 3));
        tabLayout.setSelectedTabIndicatorColor(PropertiesUtil.isHexColor(indicatorColor) ? PropertiesUtil.parseColor(indicatorColor) : 0xffffc107);
        int tabTextColor = PropertiesUtil.isHexColor(textColor) ? PropertiesUtil.parseColor(textColor) : 0xff57beee;
        int tabSelectedTextColor = PropertiesUtil.isHexColor(selectedTextColor) ? PropertiesUtil.parseColor(selectedTextColor) : Color.WHITE;
        tabLayout.setTabTextColors(tabTextColor, tabSelectedTextColor);
    }

    private void updateMaterialButton(ItemMaterialButton materialButton, InjectAttributeHandler handler) {
        String radius = handler.getAttributeValueOf("cornerRadius");
        String stroke = handler.getAttributeValueOf("strokeWidth");
        materialButton.setStrokeWidth(PropertiesUtil.resolveSize(stroke, 0));
        materialButton.setCornerRadius(PropertiesUtil.resolveSize(radius, 8));
    }

    private String extractAttrValue(String line, String attribute) {
        Matcher matcher = Pattern.compile("=\"([^\"]*)\"").matcher(line);
        return matcher.find() ? matcher.group(1) : "";
    }

    @NonNull
    @Override
    public String toString() {
        return getClass().getName() + "@" + Integer.toHexString(hashCode());
    }

    private record ViewInfo(Rect rect, View view, int index, int depth) {
    }
}
