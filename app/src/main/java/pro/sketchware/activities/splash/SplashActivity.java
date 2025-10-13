package pro.sketchware.activities.splash;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.TextView;

import pro.sketchware.R;
import pro.sketchware.activities.main.activities.MainActivity;

public class SplashActivity extends Activity {

    private static final int SPLASH_DELAY = 1000; // 1 segundos

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
        fadeIn.setDuration(800);

        logo.startAnimation(fadeIn);
        title.startAnimation(fadeIn);
        subtitle.startAnimation(fadeIn);

        new Handler().postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, MainActivity.class);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }, SPLASH_DELAY);
    }
}


