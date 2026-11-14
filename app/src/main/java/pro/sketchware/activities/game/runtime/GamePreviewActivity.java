package pro.sketchware.activities.game.runtime;

import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.besome.sketch.lib.base.BaseAppCompatActivity;

import pro.sketchware.activities.game.model.GameProject;
import pro.sketchware.activities.game.model.GameProjectRepository;

/**
 * Fullscreen immersive activity for live-testing a 2D {@link GameProject} inside Sketchware.
 */
public class GamePreviewActivity extends BaseAppCompatActivity {

    public static final String EXTRA_SC_ID = "sc_id";
    public static final String EXTRA_SCENE_ID = "scene_id";

    private GameRuntimeView runtimeView;
    private TextView fpsBadge;
    private TextView pauseBtn;
    private boolean userPaused = false;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable fpsTicker = new Runnable() {
        @Override
        public void run() {
            if (runtimeView != null && fpsBadge != null) {
                fpsBadge.setText(runtimeView.getCurrentFps() + " FPS");
            }
            uiHandler.postDelayed(this, 500L);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        String scId = getIntent() != null ? getIntent().getStringExtra(EXTRA_SC_ID) : "";
        String sceneId = getIntent() != null ? getIntent().getStringExtra(EXTRA_SCENE_ID) : null;
        if (scId == null) {
            scId = "";
        }

        GameProject project = GameProjectRepository.loadOrCreate(scId);
        setRequestedOrientation(project.landscape
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        applyImmersiveFullscreen();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B0E14);

        runtimeView = new GameRuntimeView(this, scId, project, sceneId);
        root.addView(runtimeView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        View pillToolbar = buildFloatingPillToolbar();
        FrameLayout.LayoutParams pillLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        pillLp.gravity = Gravity.TOP | Gravity.END;
        int margin = dp(14);
        pillLp.setMargins(margin, dp(12), margin, margin);
        root.addView(pillToolbar, pillLp);

        setContentView(root);
    }

    private View buildFloatingPillToolbar() {
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(10), dp(5), dp(8), dp(5));

        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(0xCC121822);
        pillBg.setCornerRadius(dp(24));
        pillBg.setStroke(dp(1), 0x33FFFFFF);
        pill.setBackground(pillBg);
        pill.setElevation(dp(6));

        fpsBadge = new TextView(this);
        fpsBadge.setText("60 FPS");
        fpsBadge.setTextColor(0xFF69F0AE);
        fpsBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        fpsBadge.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        fpsBadge.setPadding(dp(4), dp(2), dp(8), dp(2));
        pill.addView(fpsBadge);

        pill.addView(makeDivider());

        pauseBtn = makePillAction("Pause", 0xFFE2E8F0, v -> togglePause());
        pill.addView(pauseBtn);

        pill.addView(makePillAction("Restart", 0xFF93C5FD, v -> {
            if (runtimeView != null) {
                runtimeView.restartCurrentScene();
                if (userPaused) {
                    userPaused = false;
                    runtimeView.setPaused(false);
                    updatePauseLabel();
                }
            }
        }));

        pill.addView(makePillAction("Exit", 0xFFFCA5A5, v -> finish()));

        return pill;
    }

    private View makeDivider() {
        View divider = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(1), dp(14));
        lp.setMargins(0, 0, dp(4), 0);
        divider.setLayoutParams(lp);
        divider.setBackgroundColor(0x28FFFFFF);
        return divider;
    }

    private TextView makePillAction(String label, int textColor, View.OnClickListener listener) {
        TextView btn = new TextView(this);
        btn.setText(label);
        btn.setTextColor(textColor);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setPadding(dp(10), dp(5), dp(10), dp(5));
        btn.setGravity(Gravity.CENTER);
        btn.setMinHeight(dp(28));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.TRANSPARENT);
        bg.setCornerRadius(dp(16));
        btn.setBackground(bg);

        btn.setOnClickListener(listener);
        return btn;
    }

    private void togglePause() {
        if (runtimeView == null) {
            return;
        }
        userPaused = !userPaused;
        runtimeView.setPaused(userPaused);
        updatePauseLabel();
    }

    private void updatePauseLabel() {
        if (pauseBtn != null) {
            pauseBtn.setText(userPaused ? "Play" : "Pause");
            pauseBtn.setTextColor(userPaused ? 0xFFFDE047 : 0xFFE2E8F0);
        }
    }

    private void applyImmersiveFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsetsCompat.Type.systemBars());
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersiveFullscreen();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        applyImmersiveFullscreen();
        uiHandler.removeCallbacks(fpsTicker);
        uiHandler.post(fpsTicker);
        if (runtimeView != null && !userPaused) {
            runtimeView.setPaused(false);
        }
    }

    @Override
    public void onPause() {
        uiHandler.removeCallbacks(fpsTicker);
        if (runtimeView != null) {
            runtimeView.setPaused(true);
        }
        super.onPause();
    }

    @Override
    public void onDestroy() {
        uiHandler.removeCallbacks(fpsTicker);
        if (runtimeView != null) {
            runtimeView.releaseResources();
            runtimeView = null;
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }
}
