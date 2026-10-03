package pro.sketchware.utility;

import android.app.Activity;
import android.graphics.Insets;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;

/**
 * Android 15+ draws every Activity of an app that targets API 35+ edge-to-edge, behind the
 * status and navigation bars. Screens that weren't laid out for that use this to keep looking
 * like they did before.
 */
public final class SystemBarInsets {

    private SystemBarInsets() {
    }

    /**
     * Pads the Activity's content by the system bars and display cutout (and the keyboard, for
     * adjustResize windows), then consumes the insets so views with fitsSystemWindows don't
     * pad themselves a second time. Call it after setContentView.
     */
    public static void keepContentClear(Activity activity) {
        if (Build.VERSION.SDK_INT < 35) {
            return;
        }
        View content = activity.findViewById(android.R.id.content);
        content.setOnApplyWindowInsetsListener((view, insets) -> {
            int types = WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout();
            int adjust = activity.getWindow().getAttributes().softInputMode & WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST;
            if (adjust == WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE) {
                types |= WindowInsets.Type.ime();
            }
            Insets bars = insets.getInsets(types);
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsets.CONSUMED;
        });
        content.requestApplyInsets();
    }
}
