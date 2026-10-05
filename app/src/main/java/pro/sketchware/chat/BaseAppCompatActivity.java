package pro.sketchware.chat;

import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Base activity that keeps the app usable under targetSdk 35 (Android 15),
 * where edge-to-edge is mandatory: the window draws behind the status bar and
 * the gesture/navigation bar.
 *
 * Instead of letting content sit under the system bars, the root view receives
 * the bars' sizes (and the keyboard height, reproducing adjustResize) as
 * padding — applied centrally so every screen inherits it, while the system
 * bars stay transparent and the app background extends to the screen edges.
 */
public class BaseAppCompatActivity extends AppCompatActivity {

    private boolean edgeToEdgeApplied = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyEdgeToEdgePadding();
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        applyEdgeToEdgePadding();
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        applyEdgeToEdgePadding();
    }

    /**
     * Pads the activity's content view by the system bar insets. Safe to call
     * multiple times; only the first call after setContentView takes effect.
     */
    protected void applyEdgeToEdgePadding() {
        if (edgeToEdgeApplied) {
            return;
        }
        View root = findViewById(android.R.id.content);
        if (root == null) {
            return;
        }
        edgeToEdgeApplied = true;
        // Draw behind the bars but keep them transparent so the app background
        // extends to the screen edges.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        boolean lightTheme = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES;
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(lightTheme);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightNavigationBars(lightTheme);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            // The keyboard (IME) must push the content up like adjustResize did;
            // on API 30+ decorFitsSystemWindows=false disables that automatic
            // behaviour, so the IME height is folded into the bottom padding.
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
    }
}
