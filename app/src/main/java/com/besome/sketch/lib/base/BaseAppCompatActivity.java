package com.besome.sketch.lib.base;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface.OnCancelListener;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.AsyncTask.Status;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsCompat;

import com.besome.sketch.lib.ui.LoadingDialog;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.util.ArrayList;

import a.a.a.MA;
import a.a.a.lC;
import dev.chrisbanes.insetter.Insetter;
import pro.sketchware.dialogs.ProgressDialog;
import pro.sketchware.utility.StoragePermission;
import pro.sketchware.utility.SystemBarInsets;
import pro.sketchware.utility.TranslationFunction;

public abstract class BaseAppCompatActivity extends AppCompatActivity {
    /**
     * Whether this screen handles the system bar insets itself. Since Sketchware targets API 35+,
     * Android 15+ draws every Activity edge-to-edge; the others get padded by the system bars.
     */
    private boolean laysOutEdgeToEdge;

    public FirebaseAnalytics mAnalytics;

    @Deprecated
    public Context e;
    public Activity parent;
    protected ProgressDialog progressDialog;
    private LoadingDialog lottieDialog;
    private ArrayList<MA> taskList;

    public void a(MA var1) {
        taskList.add(var1);
    }

    public void addTask(MA task) {
        taskList.add(task);
    }

    public void a(OnCancelListener cancelListener) {
        if (progressDialog != null && !progressDialog.isShowing()) {
            progressDialog.setOnCancelListener(cancelListener);
            progressDialog.show();
        }
    }

    public void a(String var1) {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.setMessage(var1);
        }
    }

    public void g() {
        for (MA task : taskList) {
            if (task.getStatus() != Status.FINISHED && !task.isCancelled()) {
                task.cancel(true);
            }
        }
        taskList.clear();
    }

    public void h() {
        try {
            if (lottieDialog != null && lottieDialog.isShowing()) {
                lottieDialog.dismiss();
            }
        } catch (Exception var2) {
            lottieDialog = null;
            lottieDialog = new LoadingDialog(this);
        }
    }

    public void i() {
        try {
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
        } catch (Exception var2) {
            progressDialog = null;
            progressDialog = new ProgressDialog(this);
        }

    }

    public boolean isStoragePermissionGranted() {
        return StoragePermission.isGranted(this);
    }

    public boolean j() {
        return isStoragePermissionGranted();
    }

    public void k() {
        if (lottieDialog != null && !lottieDialog.isShowing() && !isFinishing()) {
            lottieDialog.show();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        e = getApplicationContext();
        taskList = new ArrayList<>();
        lottieDialog = new LoadingDialog(this);
        lC.a(getApplicationContext(), false);
        progressDialog = new ProgressDialog(this);
        mAnalytics = FirebaseAnalytics.getInstance(this);
    }

    @Override
    public void onDestroy() {
        g();
        if (lottieDialog != null && lottieDialog.isShowing()) {
            lottieDialog.cancelAnimation();
        }
        super.onDestroy();
    }

    @Override
    public void onPause() {
        if (lottieDialog != null && lottieDialog.isShowing()) {
            lottieDialog.pauseAnimation();
        }
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (lottieDialog != null && lottieDialog.isShowing()) {
            lottieDialog.resumeAnimation();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (parent != null) {
            return parent.onCreateOptionsMenu(menu);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (parent != null) {
            return parent.onOptionsItemSelected(item);
        }
        return false;
    }

    public void handleInsetts(View root) {
        Insetter.builder()
                .padding(WindowInsetsCompat.Type.navigationBars())
                .applyToView(root);
    }

    @Override
    public Resources getResources() {
        return TranslationFunction.wrapResources(this, super.getResources());
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (!laysOutEdgeToEdge) {
            SystemBarInsets.keepContentClear(this);
        }
    }

    protected void enableEdgeToEdgeNoContrast() {
        laysOutEdgeToEdge = true;
        SystemBarStyle systemBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT);
        EdgeToEdge.enable(this, systemBarStyle);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }
    }
}
