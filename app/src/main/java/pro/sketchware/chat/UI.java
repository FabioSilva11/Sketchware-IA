package pro.sketchware.chat;

import android.app.Activity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsAnimation;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class UI {

    public static void addSystemWindowInsetToPadding(View view, boolean top, boolean left, boolean bottom, boolean right) {
        if (view == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            int topInset = top ? insets.getInsets(WindowInsetsCompat.Type.systemBars()).top : 0;
            int leftInset = left ? insets.getInsets(WindowInsetsCompat.Type.systemBars()).left : 0;
            int bottomInset = bottom ? insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom : 0;
            int rightInset = right ? insets.getInsets(WindowInsetsCompat.Type.systemBars()).right : 0;
            v.setPadding(
                    v.getPaddingLeft() + leftInset,
                    v.getPaddingTop() + topInset,
                    v.getPaddingRight() + rightInset,
                    v.getPaddingBottom() + bottomInset
            );
            return WindowInsetsCompat.CONSUMED;
        });
    }
}


