package pro.sketchware.activities.splash;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.TextView;

import pro.sketchware.R;
import pro.sketchware.activities.main.activities.MainActivity;

public class SplashActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            if (getActionBar() != null) getActionBar().hide();
        } catch (Exception ignored) {}
        setContentView(R.layout.activity_splash);

        View root = findViewById(R.id.splash_root);
        ImageView logo = findViewById(R.id.app_logo);
        TextView title = findViewById(R.id.app_title);
        TextView subtitle = findViewById(R.id.app_subtitle);

        Animation fadeIn = new AlphaAnimation(0f, 1f);
        fadeIn.setInterpolator(new android.view.animation.DecelerateInterpolator());
        fadeIn.setDuration(300);

        logo.startAnimation(fadeIn);
        title.startAnimation(fadeIn);
        subtitle.startAnimation(fadeIn);

        // No fixed wait: the project list starts once the splash is on screen, and the splash stays visible
        // until the list's first frame.
        root.post(() -> {
            Intent intent = new Intent(SplashActivity.this, MainActivity.class);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        });
    }
}


