package pro.sketchware.activities.game.runtime;

import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;

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

        runtimeView = new GameRuntimeView(this, scId, project, sceneId);
        setContentView(runtimeView);
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
        if (runtimeView != null) {
            runtimeView.setPaused(false);
        }
    }

    @Override
    public void onPause() {
        if (runtimeView != null) {
            runtimeView.setPaused(true);
        }
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (runtimeView != null) {
            runtimeView.releaseResources();
            runtimeView = null;
        }
        super.onDestroy();
    }

}
