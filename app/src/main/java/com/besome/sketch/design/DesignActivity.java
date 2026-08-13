package com.besome.sketch.design;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.GravityCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import com.besome.sketch.adapters.JavaFileAdapter;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ProjectResourceBean;
import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.common.SrcViewerActivity;
import com.besome.sketch.editor.manage.ManageCollectionActivity;
import com.besome.sketch.editor.manage.ViewSelectorActivity;
import com.besome.sketch.editor.manage.font.ManageFontActivity;
import com.besome.sketch.editor.manage.image.ManageImageActivity;
import com.besome.sketch.editor.manage.lottie.ManageLottieActivity;
import com.besome.sketch.editor.manage.library.ManageLibraryActivity;
import com.besome.sketch.editor.manage.sound.ManageSoundActivity;
import com.besome.sketch.editor.manage.view.ManageViewActivity;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.besome.sketch.lib.ui.CustomViewPager;
import com.besome.sketch.tools.CompileLogActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.topjohnwu.superuser.Shell;

import de.hdodenhof.circleimageview.CircleImageView;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import a.a.a.DB;
import a.a.a.GB;
import a.a.a.Ox;
import a.a.a.ProjectBuilder;
import a.a.a.ViewEditorFragment;
import a.a.a.bB;
import a.a.a.bC;
import a.a.a.br;
import a.a.a.cC;
import a.a.a.eC;
import a.a.a.jC;
import a.a.a.kC;
import a.a.a.lC;
import a.a.a.mB;
import a.a.a.rs;
import a.a.a.wq;
import a.a.a.yB;
import a.a.a.yq;
import a.a.a.zy;
import dev.chrisbanes.insetter.Insetter;
import mod.agus.jcoderz.editor.manage.permission.ManagePermissionActivity;
import mod.agus.jcoderz.editor.manage.resource.ManageResourceActivity;
import mod.hey.studios.activity.managers.assets.ManageAssetsActivity;
import mod.hey.studios.activity.managers.java.ManageJavaActivity;
import mod.hey.studios.compiler.kotlin.KotlinCompilerBridge;
import mod.hey.studios.project.custom_blocks.CustomBlocksDialog;
import mod.hey.studios.project.proguard.ManageProguardActivity;
import mod.hey.studios.project.proguard.ProguardHandler;
import mod.hey.studios.project.stringfog.ManageStringFogFragment;
import mod.hey.studios.project.stringfog.StringfogHandler;
import mod.hey.studios.util.Helper;
import mod.hey.studios.util.ProjectMapUtils;
import mod.hey.studios.util.SystemLogPrinter;
import mod.hilal.saif.activities.android_manifest.AndroidManifestInjection;
import mod.hilal.saif.activities.tools.ConfigActivity;
import mod.jbk.build.BuildProgressReceiver;
import mod.jbk.build.BuiltInLibraries;
import mod.jbk.diagnostic.CompileErrorSaver;
import mod.jbk.diagnostic.MissingFileException;
import mod.jbk.util.LogUtil;
import mod.khaled.logcat.LogReaderActivity;
import pro.sketchware.R;
import pro.sketchware.utility.AdManager;
import pro.sketchware.activities.appcompat.ManageAppCompatActivity;
import pro.sketchware.activities.editor.command.ManageXMLCommandActivity;
import pro.sketchware.activities.editor.view.CodeViewerActivity;
import pro.sketchware.activities.editor.view.ViewCodeEditorActivity;
import pro.sketchware.activities.resourceseditor.ResourcesEditorActivity;
import pro.sketchware.dialogs.BuildSettingsBottomSheet;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;
import pro.sketchware.utility.apk.ApkSignatures;
import com.besome.sketch.beans.HistoryViewBean;
import pro.sketchware.managers.inject.InjectRootLayoutManager;
import pro.sketchware.tools.ViewBeanParser;
import pro.sketchware.utility.TranslationFunction;

public class DesignActivity extends BaseAppCompatActivity implements View.OnClickListener {
    public static String sc_id;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
    private ImageView xmlLayoutOrientation;
    private boolean B;
    private int currentTabNumber;
    private CustomViewPager viewPager;
    private CoordinatorLayout coordinatorLayout;
    private DrawerLayout drawer;
    private yq q;
    private DB r;
    private DB t;
    private Menu bottomMenu;
    private PopupMenu bottomPopupMenu;
    private MaterialButton btnRun;
    private MaterialButton btnOptions;
    private ProjectFileBean projectFile;
    private TextView fileName;
    private String currentJavaFileName;
    private AlertDialog aiLayoutLoadingDialog;
    private TextView aiLayoutLoadingTitle;
    private TextView aiLayoutLoadingSubtitle;
    private TextView aiLayoutReferenceImageLabel;
    private LinearLayout aiLayoutReferenceImagePreviewList;
    private final ArrayList<Uri> aiLayoutReferenceImageUris = new ArrayList<>();
    private ViewEditorFragment viewTabAdapter;
    private StringsTabFragment stringsTabAdapter;
    private final ActivityResultLauncher<Intent> openCollectionManager = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK) {
            if (viewTabAdapter != null) {
                viewTabAdapter.j();
            }
        }
    });
    private final ActivityResultLauncher<Intent> openResourcesManager = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK) {
            if (viewTabAdapter != null && viewPager.getCurrentItem() == 0) {
                viewTabAdapter.i();
                refreshViewTabAdapter();
            }
        }
    });
    private final ActivityResultLauncher<Intent> openViewCodeEditor = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK) {
            if (viewTabAdapter != null) {
                viewTabAdapter.i();
            }
        }
    });
    private rs eventTabAdapter;
    private br componentTabAdapter;
    private final ActivityResultLauncher<Intent> openImageManager = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK) {
            refresh();
        }
    });
    public final ActivityResultLauncher<Intent> changeOpenFile = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK) {
            assert result.getData() != null;
            projectFile = result.getData().getParcelableExtra("project_file");
            refresh();
        }
    });
    private final ActivityResultLauncher<Intent> openLibraryManager = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK) {
            refresh();
            if (viewTabAdapter != null) {
                viewTabAdapter.n();
            }
        }
    });
    private final ActivityResultLauncher<Intent> openViewManager = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK) {
            refresh();
        }
    });
    private final ActivityResultLauncher<String> selectAiLayoutReferenceImages = registerForActivityResult(new ActivityResultContracts.GetMultipleContents(), uris -> {
        if (uris != null && !uris.isEmpty()) {
            aiLayoutReferenceImageUris.clear();
            aiLayoutReferenceImageUris.addAll(uris);
            updateAiLayoutReferenceImagePreview();
        }
    });
    private BuildTask currentBuildTask;
    private final BroadcastReceiver buildCancelReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BuildTask.ACTION_CANCEL_BUILD.equals(intent.getAction())) {
                if (currentBuildTask != null) {
                    currentBuildTask.cancelBuild();
                }
            }
        }
    };

    /**
     * Saves the app's version information to the currently opened Sketchware project file.
     */
    private void saveVersionCodeInformationToProject() {
        HashMap<String, Object> projectMetadata = lC.b(sc_id);
        if (projectMetadata != null) {
            projectMetadata.put("sketchware_ver", GB.d(getApplicationContext()));
            lC.b(sc_id, projectMetadata);
        }
    }

    private void loadProject(boolean haveSavedState) {
        projectFile = getDefaultProjectFile();
        jC.a(sc_id, haveSavedState);
        jC.b(sc_id, haveSavedState);
        kC var2 = jC.d(sc_id, haveSavedState);
        jC.c(sc_id, haveSavedState);
        cC.c(sc_id);
        bC.d(sc_id);
        if (!haveSavedState) {
            var2.f();
            var2.g();
            var2.e();
        }
    }

    private ProjectFileBean getDefaultProjectFile() {
        return jC.b(sc_id).b(ProjectFileBean.DEFAULT_XML_NAME);
    }

    private void refreshFileSelector() {
        if (projectFile == null) {
            projectFile = getDefaultProjectFile();
        }

        String javaFileName = projectFile.getJavaName();
        String xmlFileName = projectFile.getXmlName();

        if (!javaFileName.isEmpty()) {
            currentJavaFileName = javaFileName;
        }

        if (viewPager.getCurrentItem() == 0) {
            if (!ProjectFileBean.DEFAULT_XML_NAME.equals(xmlFileName) && jC.b(sc_id).b(xmlFileName) == null) {
                projectFile = getDefaultProjectFile();
                xmlFileName = ProjectFileBean.DEFAULT_XML_NAME;
            }
            fileName.setText(xmlFileName);
        } else if (viewPager.getCurrentItem() == 1 || viewPager.getCurrentItem() == 2) {
            if (!ProjectFileBean.DEFAULT_JAVA_NAME.equals(currentJavaFileName) && jC.b(sc_id).a(currentJavaFileName) == null) {
                projectFile = getDefaultProjectFile();
                currentJavaFileName = ProjectFileBean.DEFAULT_JAVA_NAME;
            }
            fileName.setText(currentJavaFileName);
        } else if (viewPager.getCurrentItem() == 3) {
            fileName.setText("strings.xml");
        }
    }

    private void refreshViewTabAdapter() {
        if (viewTabAdapter != null && projectFile != null) {
            int orientation = projectFile.orientation;
            if (orientation == ProjectFileBean.ORIENTATION_PORTRAIT) {
                xmlLayoutOrientation.setImageResource(R.drawable.ic_screen_portrait_grey600_24dp);
            } else if (orientation == ProjectFileBean.ORIENTATION_LANDSCAPE) {
                xmlLayoutOrientation.setImageResource(R.drawable.ic_screen_landscape_grey600_24dp);
            } else {
                xmlLayoutOrientation.setImageResource(R.drawable.ic_screen_rotation_grey600_24dp);
            }
            viewTabAdapter.initialize(projectFile);
        }
    }

    private void refreshEventTabAdapter() {
        if (eventTabAdapter != null && projectFile != null) {
            eventTabAdapter.setCurrentActivity(projectFile);
            eventTabAdapter.refreshEvents();
        }
    }

    private void refreshComponentTabAdapter() {
        if (componentTabAdapter != null && projectFile != null) {
            componentTabAdapter.setProjectFile(projectFile);
            componentTabAdapter.refreshData();
        }
    }

    private void refresh() {
        refreshFileSelector();
        int tab = viewPager.getCurrentItem();
        if (tab == 0) {
            refreshViewTabAdapter();
        } else if (tab == 1) {
            refreshEventTabAdapter();
        } else if (tab == 2) {
            refreshComponentTabAdapter();
        } else if (tab == 3) {
            refreshStringsTabAdapter();
        }
    }

    private void refreshStringsTabAdapter() {
        if (stringsTabAdapter != null) {
            stringsTabAdapter.refreshList();
        }
    }

    public void setTouchEventEnabled(boolean touchEventEnabled) {
        if (touchEventEnabled) {
            viewPager.enableTouchEvent();
        } else {
            viewPager.disableTouchEvent();
        }
    }

    /**
     * Shows a Snackbar indicating that a problem occurred while compiling. The user can click on "SHOW" to get to {@link CompileLogActivity}.
     *
     * @param error The error, to be later displayed as text in {@link CompileLogActivity}
     */
    private void indicateCompileErrorOccurred(String error) {
        new CompileErrorSaver(sc_id).appendLog("BUILD FAILED:\n" + error);
        Snackbar snackbar = Snackbar.make(coordinatorLayout, "Show compile log", Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction(Helper.getResString(R.string.common_word_show), v -> {
            if (!mB.a()) {
                snackbar.dismiss();
                Intent intent = new Intent(getApplicationContext(), CompileLogActivity.class);
                intent.putExtra("sc_id", sc_id);
                intent.putExtra("showingLastError", true);
                intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            }
        });
        snackbar.show();
    }

    @Override
    public void finish() {
        jC.a();
        cC.a();
        bC.a();
        setResult(RESULT_CANCELED, getIntent());
        super.finish();
    }

    private void checkForUnsavedProjectData() {
        if (jC.c(sc_id).g() || jC.b(sc_id).g() || jC.d(sc_id).q() || jC.a(sc_id).d() || jC.a(sc_id).c()) {
            askIfToRestoreOldUnsavedProjectData();
        }
    }

    /**
     * Opens the debug APK to install.
     */
    private void installBuiltApk() {
        if (!ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_ROOT_AUTO_INSTALL_PROJECTS)) {
            requestPackageInstallerInstall();
        } else {
            File apkUri = new File(q.finalToInstallApkPath);
            long length = apkUri.length();
            Shell.getShell(shell -> {
                if (shell.isRoot()) {
                    List<String> stdout = new LinkedList<>();
                    List<String> stderr = new LinkedList<>();

                    Shell.cmd("cat " + apkUri + " | pm install -S " + length).to(stdout, stderr).submit(result -> {
                        if (result.isSuccess()) {
                            SketchwareUtil.toast("Package installed successfully!");
                            if (ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_ROOT_AUTO_OPEN_AFTER_INSTALLING)) {
                                Intent launcher = getPackageManager().getLaunchIntentForPackage(q.packageName);
                                if (launcher != null) {
                                    startActivity(launcher);
                                } else {
                                    SketchwareUtil.toastError("Couldn't launch project, either not installed or not with launcher activity.");
                                }
                            }
                        } else {
                            String sharedErrorMessage = "Failed to install package, result code: " + result.getCode() + ". ";
                            SketchwareUtil.toastError(sharedErrorMessage + "Logs are available in /Internal storage/.sketchware/debug.txt", Toast.LENGTH_LONG);
                            LogUtil.e("DesignActivity", sharedErrorMessage + "stdout: " + stdout + ", stderr: " + stderr);
                        }
                    });
                } else {
                    SketchwareUtil.toastError("No root access granted. Continuing using default package install prompt.");
                    requestPackageInstallerInstall();
                }
            });
        }
    }

    private void requestPackageInstallerInstall() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri apkUri = FileProvider.getUriForFile(getApplicationContext(), getApplicationContext().getPackageName() + ".provider", new File(q.finalToInstallApkPath));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");

        startActivity(intent);
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.END)) {
            drawer.closeDrawer(GravityCompat.END);
        } else if (viewTabAdapter.isPropertyViewVisible()) {
            hideViewPropertyView();
        } else {
            if (currentTabNumber > 0) {
                currentTabNumber--;
                viewPager.setCurrentItem(currentTabNumber);
            } else if (t.c("P12I2")) {
                k();
                saveChangesAndCloseProject();
            } else {
                showSaveBeforeQuittingDialog();
            }
        }
    }

    public void hideViewPropertyView() {
        viewTabAdapter.a(false);
    }

    private void saveChangesAndCloseProject() {
        k();
        SaveChangesProjectCloser saveChangesProjectCloser = new SaveChangesProjectCloser(this);
        saveChangesProjectCloser.execute();
    }

    private void saveProject() {
        k();
        ProjectSaver projectSaver = new ProjectSaver(this);
        projectSaver.execute();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.design);
        if (!isStoragePermissionGranted()) {
            finish();
        }

        if (savedInstanceState == null) {
            sc_id = getIntent().getStringExtra("sc_id");
        } else {
            sc_id = savedInstanceState.getString("sc_id");
        }

        r = new DB(getApplicationContext(), "P1");
        t = new DB(getApplicationContext(), "P12");

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setSubtitle(sc_id);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        drawer = findViewById(R.id.drawer_layout);
        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

        Insetter.builder().margin(WindowInsetsCompat.Type.navigationBars()).applyToView(findViewById(R.id.container));

        coordinatorLayout = findViewById(R.id.layout_coordinator);
        fileName = findViewById(R.id.file_name);

        findViewById(R.id.file_name_container).setOnClickListener(this);

        btnRun = findViewById(R.id.btn_run);
        btnRun.setOnClickListener(v -> {
            if (currentBuildTask != null && !currentBuildTask.canceled && !currentBuildTask.isBuildFinished) {
                currentBuildTask.cancelBuild();
                return;
            }

            BuildTask buildTask = new BuildTask(this);
            currentBuildTask = buildTask;
            buildTask.execute();
        });

        btnOptions = findViewById(R.id.btn_options);
        btnOptions.setOnClickListener(v -> bottomPopupMenu.show());

        bottomPopupMenu = new PopupMenu(this, btnOptions);
        bottomMenu = bottomPopupMenu.getMenu();
        bottomMenu.add(Menu.NONE, 1, Menu.NONE, "Build Settings").setOnMenuItemClickListener(item -> {
            BuildSettingsBottomSheet sheet = BuildSettingsBottomSheet.newInstance(sc_id);
            sheet.show(getSupportFragmentManager(), BuildSettingsBottomSheet.TAG);
            return true;
        });
        bottomMenu.add(Menu.NONE, 2, Menu.NONE, "Clean temporary files").setVisible(false).setOnMenuItemClickListener(item -> {
            new Thread(() -> {
                FileUtil.deleteFile(q.projectMyscPath);
                updateBottomMenu();
                runOnUiThread(() -> SketchwareUtil.toast("Done cleaning temporary files!"));
            }).start();
            return true;
        });
        bottomMenu.add(Menu.NONE, 3, Menu.NONE, "Show last compile error").setOnMenuItemClickListener(item -> {
            new CompileErrorSaver(sc_id).showLastErrors(this);
            return true;
        });
        bottomMenu.add(Menu.NONE, 5, Menu.NONE, "Show source code").setOnMenuItemClickListener(item -> {
            showCurrentActivitySrcCode();
            return true;
        });
        bottomMenu.add(Menu.NONE, 4, Menu.NONE, "Install last built APK").setVisible(false).setOnMenuItemClickListener(item -> {
            if (FileUtil.isExistFile(q.finalToInstallApkPath)) {
                installBuiltApk();
            } else SketchwareUtil.toast("APK doesn't exist anymore");
            return true;
        });
        bottomMenu.add(Menu.NONE, 6, Menu.NONE, "Show Apk signatures").setVisible(false).setOnMenuItemClickListener(item -> {
            ApkSignatures apkSignatures = new ApkSignatures(this, q.finalToInstallApkPath);
            apkSignatures.showSignaturesDialog();
            return true;
        });
        bottomMenu.add(Menu.NONE, 7, Menu.NONE, "Direct XML editor").setOnMenuItemClickListener(item -> {
            toViewCodeEditor();
            return true;
        });
        bottomMenu.add(Menu.NONE, 8, Menu.NONE, "Reset Root Layout").setOnMenuItemClickListener(item -> {
            resetRootLayout();
            return true;
        });
        bottomMenu.add(Menu.NONE, 9, Menu.NONE, "Transcribe to Material 3").setOnMenuItemClickListener(item -> {
            transcribeToMaterial3();
            return true;
        });
        bottomPopupMenu.setOnDismissListener(menu -> btnOptions.setChecked(false));

        xmlLayoutOrientation = findViewById(R.id.img_orientation);
        viewPager = findViewById(R.id.viewpager);
        viewPager.setAdapter(new ViewPagerAdapter(getSupportFragmentManager()));
        viewPager.setOffscreenPageLimit(4);
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {

            @Override
            public void onPageScrollStateChanged(int state) {
            }

            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                if (currentTabNumber == 1) {
                    if (eventTabAdapter != null) {
                        eventTabAdapter.c();
                    }
                } else if (currentTabNumber == 2 && componentTabAdapter != null) {
                    componentTabAdapter.unselectAll();
                }
                if (position == 0) {
                    bottomMenu.findItem(7).setVisible(true);
                    if (viewTabAdapter != null) {
                        viewTabAdapter.showHidePropertyView(true);
                        xmlLayoutOrientation.setImageResource(R.drawable.ic_mtrl_screen);
                    }
                } else if (position == 1) {
                    bottomMenu.findItem(7).setVisible(false);
                    if (viewTabAdapter != null) {
                        xmlLayoutOrientation.setImageResource(R.drawable.ic_mtrl_code);
                        viewTabAdapter.showHidePropertyView(false);
                        if (eventTabAdapter != null) {
                            eventTabAdapter.refreshEvents();
                        }
                    }
                } else if (position == 2) {
                    bottomMenu.findItem(7).setVisible(false);
                    if (viewTabAdapter != null) {
                        xmlLayoutOrientation.setImageResource(R.drawable.ic_mtrl_code);
                        viewTabAdapter.showHidePropertyView(false);
                        if (componentTabAdapter != null) {
                            componentTabAdapter.refreshData();
                        }
                    }
                } else if (position == 3) {
                    bottomMenu.findItem(7).setVisible(false);
                    if (viewTabAdapter != null) {
                        xmlLayoutOrientation.setImageResource(R.drawable.ic_mtrl_code);
                        viewTabAdapter.showHidePropertyView(false);
                    }
                    if (stringsTabAdapter != null) {
                        stringsTabAdapter.refreshList();
                    }
                }
                refresh();
                currentTabNumber = position;
                invalidateOptionsMenu();
            }
        });
        viewPager.getAdapter().notifyDataSetChanged();
        ((TabLayout) findViewById(R.id.tab_layout)).setupWithViewPager(viewPager);

        IntentFilter filter = new IntentFilter(BuildTask.ACTION_CANCEL_BUILD);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(buildCancelReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(buildCancelReceiver, filter);
        }

        AdManager.loadBanner(this, findViewById(R.id.ad_container), "ca-app-pub-6598765502914364/4929466111");
    }

    private boolean isDebugApkExists() {
        if (q != null) {
            return FileUtil.isExistFile(q.finalToInstallApkPath);
        }
        return false;
    }

    private void updateBottomMenu() {
        if (bottomMenu != null) {
            handler.post(() -> {
                bottomMenu.findItem(2).setVisible(q != null && FileUtil.isExistFile(q.projectMyscPath));
                var isDebugApkExists = isDebugApkExists();
                bottomMenu.findItem(4).setVisible(isDebugApkExists);
                bottomMenu.findItem(6).setVisible(isDebugApkExists);
            });
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(buildCancelReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.design_menu, menu);
        MenuItem searchItem = menu.findItem(R.id.design_option_menu_search);
        if (searchItem != null) {
            searchItem.setVisible(currentTabNumber == 1);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.design_actionbar_titleopen_drawer) {
            if (!drawer.isDrawerOpen(GravityCompat.END)) {
                drawer.openDrawer(GravityCompat.END);
            }
        } else if (itemId == R.id.design_option_menu_title_save_project) {
            saveProject();
        } else if (itemId == R.id.design_option_menu_ai_generate_layout) {
            launchAiGenerateLayout();
        }

        return super.onOptionsItemSelected(item);
    }

    private void launchAiGenerateLayout() {
        try {
            View dialogView = getLayoutInflater().inflate(R.layout.dialog_ai_layout_generation, null);
            TextInputEditText promptInput = dialogView.findViewById(R.id.input_text);
            TextInputLayout promptContainer = dialogView.findViewById(R.id.input_layout_container);
            TextInputEditText referenceNotesInput = dialogView.findViewById(R.id.reference_image_notes);
            aiLayoutReferenceImageLabel = dialogView.findViewById(R.id.reference_image_label);
            aiLayoutReferenceImagePreviewList = dialogView.findViewById(R.id.reference_image_preview_list);
            MaterialButton selectReferenceImageButton = dialogView.findViewById(R.id.button_select_reference_image);
            aiLayoutReferenceImageUris.clear();
            updateAiLayoutReferenceImagePreview();
            if (selectReferenceImageButton != null) {
                selectReferenceImageButton.setOnClickListener(v -> selectAiLayoutReferenceImages.launch("image/*"));
            }

            var dialog = new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.ai_layout_generator_title)
                    .setView(dialogView)
                    .setPositiveButton(R.string.common_word_generate, null)
                    .setNegativeButton(R.string.common_word_cancel, null)
                    .create();

            dialog.setOnShowListener(unused -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String prompt = promptInput != null && promptInput.getText() != null
                        ? promptInput.getText().toString().trim()
                        : "";

                if (prompt.isEmpty()) {
                    if (promptContainer != null) {
                        promptContainer.setError(getString(R.string.ai_layout_generator_empty_prompt));
                    }
                    if (promptInput != null) {
                        promptInput.requestFocus();
                    }
                    return;
                }

                if (promptContainer != null) {
                    promptContainer.setError(null);
                }

                dialog.dismiss();
                showAiLayoutLoadingDialog(
                        R.string.ai_layout_generator_loading_title,
                        getString(R.string.ai_layout_generator_loading_subtitle)
                );
                String referenceNotes = referenceNotesInput != null && referenceNotesInput.getText() != null
                        ? referenceNotesInput.getText().toString().trim()
                        : "";
                generateAndApplyLayoutAsync(prompt, isIncludeCurrentLayoutEnabled(dialogView), referenceNotes, new ArrayList<>(aiLayoutReferenceImageUris));
            }));

            dialog.show();
        } catch (Exception e) {
            e.printStackTrace();
            showAiLayoutErrorDialog(getString(
                    R.string.ai_layout_generator_error_generate,
                    getAiLayoutErrorDetail(e)
            ));
        }
    }

    private boolean isIncludeCurrentLayoutEnabled(View dialogView) {
        View checkboxView = dialogView.findViewById(R.id.checkbox_include_current);
        return checkboxView instanceof CheckBox checkBox && checkBox.isChecked();
    }

    private void showAiLayoutLoadingDialog(int titleResId, String subtitle) {
        dismissAiLayoutLoadingDialog();

        View loadingView = getLayoutInflater().inflate(R.layout.dialog_ai_layout_loading, null);
        aiLayoutLoadingTitle = loadingView.findViewById(R.id.text_loading_title);
        aiLayoutLoadingSubtitle = loadingView.findViewById(R.id.text_loading_subtitle);
        if (aiLayoutLoadingTitle != null) {
            aiLayoutLoadingTitle.setText(titleResId);
        }
        if (aiLayoutLoadingSubtitle != null) {
            aiLayoutLoadingSubtitle.setText(subtitle);
        }

        aiLayoutLoadingDialog = new MaterialAlertDialogBuilder(this)
                .setView(loadingView)
                .setCancelable(false)
                .create();

        if (!isFinishing() && !isDestroyed()) {
            aiLayoutLoadingDialog.show();
        }
    }

    private void updateAiLayoutLoadingDialog(String subtitle) {
        runOnUiThread(() -> {
            if (aiLayoutLoadingSubtitle != null) {
                aiLayoutLoadingSubtitle.setText(subtitle);
            }
        });
    }

    private void dismissAiLayoutLoadingDialog() {
        runOnUiThread(() -> {
            try {
                if (aiLayoutLoadingDialog != null && aiLayoutLoadingDialog.isShowing()) {
                    aiLayoutLoadingDialog.dismiss();
                }
            } catch (Exception ignored) {
            } finally {
                aiLayoutLoadingDialog = null;
                aiLayoutLoadingTitle = null;
                aiLayoutLoadingSubtitle = null;
            }
        });
    }

    private void transcribeToMaterial3() {
        showAiLayoutLoadingDialog(
                R.string.ai_layout_generator_m3_loading_title,
                getString(R.string.ai_layout_generator_m3_loading_subtitle)
        );
        generateAndApplyLayoutAsync("Refactor this layout into a modern Material 3 design. " +
                "CRITICAL: You must preserve the original hierarchy and architecture of the user's layout. " +
                "DO NOT add Toolbars, ActionBars, or any outer components. " +
                "Only swap individual widgets for their modern Material 3 equivalents (e.g., Button to MaterialButton, Switch to MaterialSwitch, EditText to TextInputEditText/TextInputLayout) " +
                "while maintaining all properties. Ensure every MaterialButton has centered text (android:gravity=\"center\"). " +
                "Refine the design to look premium and responsive.", true, "", new ArrayList<>());
    }

    private void resetRootLayout() {
        if (projectFile == null) return;
        String xmlName = projectFile.getXmlName();
        try {
            var rootLayoutManager = new InjectRootLayoutManager(sc_id);
            rootLayoutManager.set(xmlName, InjectRootLayoutManager.getDefaultRootLayout());
            
            if (viewTabAdapter != null) {
                viewTabAdapter.i();
                refreshViewTabAdapter();
            }
            SketchwareUtil.toast("Root layout reset to default");
        } catch (Exception e) {
            SketchwareUtil.toastError("Failed to reset root: " + e.getMessage());
        }
    }

    private void generateAndApplyLayoutAsync(String prompt, boolean includeCurrentLayout, String referenceNotes, List<Uri> referenceImageUris) {
        if (projectFile == null) {
            dismissAiLayoutLoadingDialog();
            showAiLayoutErrorDialog(getString(R.string.ai_layout_generator_error_no_file));
            return;
        }
        List<Uri> referenceImageUrisSnapshot = referenceImageUris == null ? new ArrayList<>() : new ArrayList<>(referenceImageUris);
        new Thread(() -> {
            try {
                String currentLayoutXml = null;
                
                String xmlName = projectFile.getXmlName();
                
                // Obter histórico de conversas anteriores
                pro.sketchware.ia.LayoutHistoryManager historyManager =
                        new pro.sketchware.ia.LayoutHistoryManager(getApplicationContext());
                List<pro.sketchware.ia.LayoutHistoryManager.HistoryEntry> history =
                        historyManager.getHistory(sc_id, xmlName);
                
                // Se solicitado, obter o layout atual
                if (includeCurrentLayout) {
                    updateAiLayoutLoadingDialog(getString(R.string.ai_layout_generator_reading_context));
                    try {
                        // Usar q.N que é o jq necessário para Ox (mesmo padrão usado na linha 1021)
                        Ox ox = new Ox(q.N, projectFile);
                        ox.a(jC.a(sc_id).d(xmlName), jC.a(sc_id).h(xmlName));
                        currentLayoutXml = ox.b();
                    } catch (Exception e) {
                        Log.e("DesignActivity", "Erro ao obter layout atual", e);
                        // Continua sem o layout atual se houver erro
                    }
                }
                
                // Criar gerador com histórico
                String referenceContext = buildReferenceContext(referenceNotes, referenceImageUrisSnapshot);
                List<String> availableDrawables = getAvailableProjectDrawables();
                List<String> referenceImageDataUrls = buildReferenceImageDataUrls(referenceImageUrisSnapshot);

                updateAiLayoutLoadingDialog(getString(R.string.ai_layout_generator_loading_subtitle));
                pro.sketchware.ia.GeradorDeLayout gerador = new pro.sketchware.ia.GeradorDeLayout(
                        prompt, currentLayoutXml, history, referenceContext,
                        availableDrawables, referenceImageDataUrls);
                final String cleanXml = gerador.gerarLayout();
                ParsedGeneratedLayout generatedLayout = prepareGeneratedLayout(cleanXml);

                updateAiLayoutLoadingDialog(getString(R.string.ai_layout_generator_loading_applying));

                runOnUiThread(() -> {
                    try {
                        var rootLayoutManager = new InjectRootLayoutManager(sc_id);
                        rootLayoutManager.set(xmlName, InjectRootLayoutManager.toRoot(generatedLayout.rootAttributes));

                        var bean = new HistoryViewBean();
                        bean.actionOverride(generatedLayout.parsedLayout, jC.a(sc_id).d(xmlName));
                        var cc = cC.c(sc_id);
                        if (!cc.c.containsKey(xmlName)) {
                            cc.e(xmlName);
                        }
                        cc.a(xmlName);
                        cc.a(xmlName, bean);

                        jC.a(sc_id).c.put(xmlName, generatedLayout.parsedLayout);

                        if (viewTabAdapter != null) {
                            viewTabAdapter.i();
                            refreshViewTabAdapter();
                        }

                        SketchwareUtil.toast(getString(R.string.ai_layout_generator_success, xmlName));
                        
                        // Salvar no histórico após sucesso
                        try {
                            historyManager.saveHistoryEntry(sc_id, xmlName, prompt, cleanXml);
                        } catch (Exception e) {
                            Log.e("DesignActivity", "Erro ao salvar histórico", e);
                            // Não interrompe o fluxo se falhar ao salvar histórico
                        }
                    } catch (Exception parseExp) {
                        showAiLayoutErrorDialog(getString(
                                R.string.ai_layout_generator_error_apply,
                                getAiLayoutErrorDetail(parseExp)
                        ));
                    } finally {
                        dismissAiLayoutLoadingDialog();
                    }
                });
            } catch (Exception e) {
                dismissAiLayoutLoadingDialog();
                showAiLayoutErrorDialog(getString(
                        R.string.ai_layout_generator_error_generate,
                        getAiLayoutErrorDetail(e)
                ));
            }
        }).start();
    }

    private void showAiLayoutErrorDialog(String message) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            String safeMessage = message == null || message.trim().isEmpty()
                    ? getString(R.string.common_error_an_error_occurred)
                    : message;
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.ai_layout_generator_error_dialog_title)
                    .setMessage(safeMessage)
                    .setPositiveButton(R.string.common_word_copy, (dialog, which) -> {
                        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (clipboardManager != null) {
                            clipboardManager.setPrimaryClip(ClipData.newPlainText(
                                    getString(R.string.ai_layout_generator_error_dialog_title),
                                    safeMessage
                            ));
                        }
                    })
                    .setNegativeButton(R.string.common_word_close, null)
                    .show();
        });
    }

    private String getAiLayoutErrorDetail(Throwable throwable) {
        if (throwable == null) {
            return getString(R.string.common_error_an_error_occurred);
        }

        String message = throwable.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return getString(R.string.common_error_an_error_occurred);
        }

        String trimmed = message.trim();
        if ("A resposta da IA não retornou XML utilizável.".equals(trimmed)
                || "The AI response did not return usable XML.".equals(trimmed)) {
            return getString(R.string.ai_layout_generator_error_unusable_xml);
        }

        return trimmed;
    }

    private String getReferenceImageDisplayName(Uri uri) {
        if (uri == null) {
            return "";
        }
        String path = uri.getLastPathSegment();
        if (path == null || path.trim().isEmpty()) {
            return uri.toString();
        }
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private void updateAiLayoutReferenceImagePreview() {
        if (aiLayoutReferenceImageLabel != null) {
            if (aiLayoutReferenceImageUris.isEmpty()) {
                aiLayoutReferenceImageLabel.setText(R.string.ai_layout_generator_reference_image_none);
            } else if (aiLayoutReferenceImageUris.size() == 1) {
                aiLayoutReferenceImageLabel.setText(getString(
                        R.string.ai_layout_generator_reference_image_selected,
                        getReferenceImageDisplayName(aiLayoutReferenceImageUris.get(0))
                ));
            } else {
                aiLayoutReferenceImageLabel.setText(getString(
                        R.string.ai_layout_generator_reference_images_selected,
                        aiLayoutReferenceImageUris.size()
                ));
            }
        }

        if (aiLayoutReferenceImagePreviewList == null) {
            return;
        }

        aiLayoutReferenceImagePreviewList.removeAllViews();
        aiLayoutReferenceImagePreviewList.setVisibility(aiLayoutReferenceImageUris.isEmpty() ? View.GONE : View.VISIBLE);
        for (Uri uri : aiLayoutReferenceImageUris) {
            CircleImageView imageView = new CircleImageView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(50), dp(50));
            params.setMarginEnd(dp(8));
            imageView.setLayoutParams(params);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageView.setContentDescription(getReferenceImageDisplayName(uri));
            imageView.setImageURI(uri);
            aiLayoutReferenceImagePreviewList.addView(imageView);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String buildReferenceContext(String referenceNotes, List<Uri> referenceImageUris) {
        StringBuilder context = new StringBuilder();
        if (referenceImageUris != null && !referenceImageUris.isEmpty()) {
            context.append("Selected reference image URIs:\n");
            for (int i = 0; i < referenceImageUris.size(); i++) {
                Uri uri = referenceImageUris.get(i);
                context.append(i + 1)
                        .append(". ")
                        .append(uri)
                        .append(" (display name: ")
                        .append(getReferenceImageDisplayName(uri))
                        .append(")\n");
            }
            context.append("The provider receives this as text context, so prioritize the user's written notes when visual details are needed.");
        }
        if (referenceNotes != null && !referenceNotes.trim().isEmpty()) {
            if (context.length() > 0) {
                context.append('\n');
            }
            context.append("User reference notes: ").append(referenceNotes.trim());
        }
        return context.toString();
    }

    private List<String> buildReferenceImageDataUrls(List<Uri> uris) {
        List<String> images = new ArrayList<>();
        if (uris == null) return images;
        for (Uri uri : uris) {
            if (images.size() >= 3 || uri == null) break;
            try (InputStream input = getContentResolver().openInputStream(uri);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                if (input == null) continue;
                byte[] buffer = new byte[8192];
                int read;
                int total = 0;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > 4 * 1024 * 1024) {
                        throw new java.io.IOException("Reference image exceeds 4 MB");
                    }
                    output.write(buffer, 0, read);
                }
                String mime = getContentResolver().getType(uri);
                if (mime == null || !mime.startsWith("image/")) mime = "image/jpeg";
                images.add("data:" + mime + ";base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP));
            } catch (Exception e) {
                Log.e("DesignActivity", "Failed to prepare AI reference image", e);
            }
        }
        return images;
    }

    private List<String> getAvailableProjectDrawables() {
        Set<String> drawables = new HashSet<>();
        try {
            ArrayList<ProjectResourceBean> resources = jC.d(sc_id).b;
            if (resources != null) {
                for (ProjectResourceBean resource : resources) {
                    if (resource != null && resource.resName != null && !resource.resName.trim().isEmpty()) {
                        drawables.add("@drawable/" + resource.resName.trim());
                    }
                }
            }
        } catch (Exception e) {
            Log.e("DesignActivity", "Failed to read project drawable resources", e);
        }
        ArrayList<String> sorted = new ArrayList<>(drawables);
        java.util.Collections.sort(sorted);
        if (sorted.size() > 80) {
            return new ArrayList<>(sorted.subList(0, 80));
        }
        return sorted;
    }

    private android.util.Pair<String, java.util.Map<String, String>> sanitizeGeneratedRoot(android.util.Pair<String, java.util.Map<String, String>> rootAttributes) {
        if (rootAttributes == null || rootAttributes.first == null || rootAttributes.first.trim().isEmpty()) {
            InjectRootLayoutManager.Root defaultRoot = InjectRootLayoutManager.getDefaultRootLayout();
            return android.util.Pair.create("LinearLayout", new LinkedHashMap<>(defaultRoot.getAttributes()));
        }

        Map<String, String> sanitized = new LinkedHashMap<>();
        Map<String, String> attrs = rootAttributes.second != null ? rootAttributes.second : new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : attrs.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            String normalized = key.trim();
            if (normalized.startsWith("android:padding")
                    || normalized.startsWith("android:layout_margin")
                    || normalized.equals("android:background")
                    || normalized.equals("app:cardBackgroundColor")
                    || normalized.equals("app:strokeColor")
                    || normalized.equals("app:strokeWidth")) {
                continue;
            }
            sanitized.put(normalized, entry.getValue());
        }
        sanitized.put("android:layout_width", "match_parent");
        sanitized.put("android:layout_height", "match_parent");
        if (!sanitized.containsKey("android:orientation") && "LinearLayout".equals(ViewBeanParser.getNameFromTag(rootAttributes.first))) {
            sanitized.put("android:orientation", "vertical");
        }
        return android.util.Pair.create(rootAttributes.first, sanitized);
    }

    private static class ParsedGeneratedLayout {
        ArrayList<com.besome.sketch.beans.ViewBean> parsedLayout;
        android.util.Pair<String, java.util.Map<String, String>> rootAttributes;

        ParsedGeneratedLayout(ArrayList<com.besome.sketch.beans.ViewBean> parsedLayout, android.util.Pair<String, java.util.Map<String, String>> rootAttributes) {
            this.parsedLayout = parsedLayout;
            this.rootAttributes = rootAttributes;
        }
    }

    private ParsedGeneratedLayout prepareGeneratedLayout(String xml) throws Exception {
        pro.sketchware.tools.ViewBeanParser parser = new pro.sketchware.tools.ViewBeanParser(xml);
        parser.setSkipRoot(true);
        ArrayList<com.besome.sketch.beans.ViewBean> parsedLayout = parser.parse();
        if (parsedLayout == null || parsedLayout.isEmpty()) {
            throw new java.io.IOException("The AI response did not contain editable views.");
        }
        android.util.Pair<String, java.util.Map<String, String>> rootAttributes = sanitizeGeneratedRoot(parser.getRootAttributes());
        return new ParsedGeneratedLayout(parsedLayout, rootAttributes);
    }

    @Override
    public void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        k();

        HashMap<String, Object> projectInfo = lC.b(sc_id);
        getSupportActionBar().setTitle(yB.c(projectInfo, "my_ws_name"));
        q = new yq(getApplicationContext(), wq.d(sc_id), projectInfo);

        try {
            ProjectLoader projectLoader = new ProjectLoader(this, savedInstanceState);
            projectLoader.execute();
        } catch (Exception e) {
            crashlytics.log("ProjectLoader failed");
            crashlytics.recordException(e);
        } finally {
            SystemLogPrinter.stop();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isStoragePermissionGranted()) {
            finish();
        }

        long freeMegabytes = GB.c();
        if (freeMegabytes < 100L && freeMegabytes > 0L) {
            warnAboutInsufficientStorageSpace();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString("sc_id", sc_id);
        super.onSaveInstanceState(outState);
        if (!isStoragePermissionGranted()) {
            finish();
        }

        if (!B) {
            UnsavedChangesSaver unsavedChangesSaver = new UnsavedChangesSaver(this);
            unsavedChangesSaver.execute();
        }
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.file_name_container) {
            if (viewPager.getCurrentItem() == 0) {
                showAvailableViews();
            } else {
                showAvailableJavaFiles();
            }
        }
    }

    /**
     * Show a dialog asking about saving the project before quitting.
     */
    private void showSaveBeforeQuittingDialog() {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setTitle(Helper.getResString(R.string.design_quit_title_exit_projet));
        dialog.setIcon(R.drawable.ic_mtrl_exit);
        dialog.setMessage(Helper.getResString(R.string.design_quit_message_confirm_save));
        dialog.setPositiveButton(Helper.getResString(R.string.design_quit_button_save_and_exit), (v, which) -> {
            if (!mB.a()) {
                v.dismiss();
                try {
                    saveChangesAndCloseProject();
                } catch (Exception e) {
                    crashlytics.recordException(e);
                    h();
                }
            }
        });
        dialog.setNegativeButton(Helper.getResString(R.string.common_word_exit), (v, which) -> {
            if (!mB.a()) {
                v.dismiss();
                try {
                    k();
                    DiscardChangesProjectCloser discardChangesProjectCloser = new DiscardChangesProjectCloser(this);
                    discardChangesProjectCloser.execute();
                } catch (Exception e) {
                    crashlytics.recordException(e);
                    h();
                }
            }
        });
        dialog.setNeutralButton(Helper.getResString(R.string.common_word_cancel), null);
        dialog.show();
    }

    /**
     * Show a dialog warning the user about low free space.
     */
    private void warnAboutInsufficientStorageSpace() {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setTitle(Helper.getResString(R.string.common_word_warning));
        dialog.setIcon(R.drawable.break_warning_96_red);
        dialog.setMessage(Helper.getResString(R.string.common_message_insufficient_storage_space));
        dialog.setPositiveButton(Helper.getResString(R.string.common_word_ok), null);
        dialog.show();
    }

    /**
     * Extrai o valor de um atributo XML do layout
     * @param xml O código XML do layout
     * @param attribute O nome do atributo a ser extraído (ex: "android:textColor")
     * @return O valor do atributo ou null se não encontrado
     */
    private String extractAttribute(String xml, String attribute) {
        try {
            int start = xml.indexOf(attribute + "=\"");
            if (start < 0) return null;
            
            start += attribute.length() + 2;
            int end = xml.indexOf("\"", start);
            if (end < 0) return null;
            
            return xml.substring(start, end);
        } catch (Exception e) {
            return null;
        }
    }

    private void askIfToRestoreOldUnsavedProjectData() {
        B = true;
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setIcon(R.drawable.ic_mtrl_history);
        dialog.setTitle(Helper.getResString(R.string.design_restore_data_title));
        dialog.setMessage(Helper.getResString(R.string.design_restore_data_message_confirm));
        dialog.setPositiveButton(Helper.getResString(R.string.common_word_restore), (v, which) -> {
            if (!mB.a()) {
                boolean g = jC.c(sc_id).g();
                boolean g2 = jC.b(sc_id).g();
                boolean q = jC.d(sc_id).q();
                boolean d = jC.a(sc_id).d();
                boolean c = jC.a(sc_id).c();
                if (g) {
                    jC.c(sc_id).h();
                }
                if (g2) {
                    jC.b(sc_id).h();
                }
                if (q) {
                    jC.d(sc_id).r();
                }
                if (d) {
                    jC.a(sc_id).h();
                }
                if (c) {
                    jC.a(sc_id).f();
                }
                if (g) {
                    jC.b(sc_id).a(jC.c(sc_id));
                    jC.a(sc_id).a(jC.c(sc_id).d());
                }
                if (g2 || g) {
                    jC.a(sc_id).a(jC.b(sc_id));
                }
                if (q) {
                    jC.a(sc_id).c(jC.d(sc_id));
                    jC.a(sc_id).a(jC.d(sc_id));
                }
                refresh();
                B = false;
                v.dismiss();
            }
        });
        dialog.setNegativeButton(Helper.getResString(R.string.common_word_no), (v, which) -> {
            B = false;
            v.dismiss();
        });
        dialog.setCancelable(false);
        dialog.show();
    }

    private void showCurrentActivitySrcCode() {
        if (projectFile == null) return;
        k();
        new Thread(() -> {
            var filename = Helper.getText(fileName);
            var code = new yq(getApplicationContext(), sc_id).getFileSrc(filename, jC.b(sc_id), jC.a(sc_id), jC.c(sc_id));
            runOnUiThread(() -> {
                if (isFinishing()) return;
                h();
                if (code.isEmpty()) {
                    SketchwareUtil.toast("Failed to generate source.");
                    return;
                }
                var scheme = filename.endsWith(".xml") ? CodeViewerActivity.SCHEME_XML : CodeViewerActivity.SCHEME_JAVA;
                launchActivity(CodeViewerActivity.class, null, new Pair<>("code", code), new Pair<>("sc_id", sc_id), new Pair<>("scheme", scheme));
            });
        }).start();
    }

    private void showAvailableJavaFiles() {
        var dialog = new MaterialAlertDialogBuilder(this).create();
        dialog.setTitle(R.string.design_file_selector_title_java);
        dialog.setIcon(R.drawable.ic_mtrl_java);
        View customView = a.a.a.wB.a(this, R.layout.file_selector_popup_select_java);
        RecyclerView recyclerView = customView.findViewById(R.id.file_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(getApplicationContext(), RecyclerView.VERTICAL, false));
        var adapter = new JavaFileAdapter(sc_id);
        adapter.setOnItemClickListener(projectFileBean -> {
            projectFile = projectFileBean;
            refreshFileSelector();
            refreshEventTabAdapter();
            refreshComponentTabAdapter();
            dialog.dismiss();
        });
        recyclerView.setAdapter(adapter);
        dialog.setView(customView);
        dialog.show();
    }

    private void showAvailableViews() {
        Intent intent = new Intent(getApplicationContext(), ViewSelectorActivity.class);
        intent.putExtra("sc_id", sc_id);
        intent.putExtra("current_xml", projectFile.getXmlName());
        intent.putExtra("is_custom_view", projectFile.fileType == 1 || projectFile.fileType == 2);
        changeOpenFile.launch(intent);
    }

    /**
     * Opens {@link ViewCodeEditorActivity}.
     */
    void toViewCodeEditor() {
        if (projectFile == null) return;
        k();
        new Thread(() -> {
            String filename = Helper.getText(fileName);
            // var yq = new yq(getApplicationContext(), sc_id);
            var xmlGenerator = new Ox(q.N, projectFile);
            var projectDataManager = jC.a(sc_id);
            var viewBeans = projectDataManager.d(filename);
            var viewFab = projectDataManager.h(filename);
            xmlGenerator.setExcludeAppCompat(true);
            xmlGenerator.a(eC.a(viewBeans), viewFab);
            String content = xmlGenerator.b();
            runOnUiThread(() -> {
                if (isFinishing()) return;
                h();
                launchActivity(ViewCodeEditorActivity.class, openViewCodeEditor, new Pair<>("title", filename), new Pair<>("content", content));
            });
        }).start();
    }

    /**
     * Opens {@link LogReaderActivity}.
     */
    void toLogReader() {
        Intent intent = new Intent(getApplicationContext(), LogReaderActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("sc_id", sc_id);
        startActivity(intent);
    }

    /**
     * Opens {@link ManageCollectionActivity}.
     */
    void toCollectionManager() {
        launchActivity(ManageCollectionActivity.class, openCollectionManager);
    }

    /**
     * Opens {@link AndroidManifestInjection}.
     */
    void toAndroidManifestManager() {
        if (projectFile == null) return;
        launchActivity(AndroidManifestInjection.class, null, new Pair<>("file_name", currentJavaFileName));
    }

    /**
     * Opens {@link ManageAppCompatActivity}.
     */
    void toAppCompatInjectionManager() {
        if (projectFile == null) return;
        launchActivity(ManageAppCompatActivity.class, null, new Pair<>("file_name", projectFile.getXmlName()));
    }

    /**
     * Opens {@link ManageAssetsActivity}.
     */
    void toAssetManager() {
        launchActivity(ManageAssetsActivity.class, null);
    }

    /**
     * Shows a {@link CustomBlocksDialog}.
     */
    void toCustomBlocksViewer() {
        new CustomBlocksDialog().show(this, sc_id);
    }

    /**
     * Opens {@link ManageJavaActivity}.
     */
    void toJavaManager() {
        launchActivity(ManageJavaActivity.class, null, new Pair<>("pkgName", q.packageName));
    }

    /**
     * Opens {@link ManagePermissionActivity}.
     */
    void toPermissionManager() {
        launchActivity(ManagePermissionActivity.class, null);
    }

    /**
     * Opens {@link ManageProguardActivity}.
     */
    void toProguardManager() {
        launchActivity(ManageProguardActivity.class, null);
    }

    /**
     * Opens {@link ManageResourceActivity}.
     */
    void toResourceManager() {
        launchActivity(ManageResourceActivity.class, openResourcesManager);
    }

    /**
     * Opens {@link ResourcesEditorActivity}.
     */
    void toResourceEditor() {
        launchActivity(ResourcesEditorActivity.class, openResourcesManager);
    }

    /**
     * Opens {@link ManageStringFogFragment}.
     */
    void toStringFogManager() {
        var fragmentManager = getSupportFragmentManager();
        if (fragmentManager.findFragmentByTag("stringFogFragment") == null) {
            var bottomSheet = new ManageStringFogFragment();
            bottomSheet.show(fragmentManager, "stringFogFragment");
        }
    }

    /**
     * Opens {@link ManageFontActivity}.
     */
    void toFontManager() {
        launchActivity(ManageFontActivity.class, null);
    }

    /**
     * Opens {@link ManageImageActivity}.
     */
    void toImageManager() {
        launchActivity(ManageImageActivity.class, openImageManager);
    }

    /**
     * Opens {@link ManageLottieActivity}.
     */
    void toLottieManager() {
        launchActivity(ManageLottieActivity.class, null);
    }

    /**
     * Opens {@link ManageLibraryActivity}.
     */
    void toLibraryManager() {
        launchActivity(ManageLibraryActivity.class, openLibraryManager);
    }

    /**
     * Opens {@link ManageViewActivity}.
     */
    void toViewManager() {
        launchActivity(ManageViewActivity.class, openViewManager);
    }

    /**
     * Opens {@link ManageSoundActivity}.
     */
    void toSoundManager() {
        launchActivity(ManageSoundActivity.class, null);
    }

    /**
     * Opens {@link SrcViewerActivity}.
     */
    void toSourceCodeViewer() {
        launchActivity(SrcViewerActivity.class, null, new Pair<>("current", Helper.getText(fileName)));
    }

    /**
     * Opens {@link ManageXMLCommandActivity}.
     */
    void toXMLCommandManager() {
        launchActivity(ManageXMLCommandActivity.class, null);
    }

    @SafeVarargs
    private void launchActivity(Class<? extends Activity> toLaunch, ActivityResultLauncher<Intent> optionalLauncher, Pair<String, String>... extras) {
        Intent intent = new Intent(getApplicationContext(), toLaunch);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("sc_id", sc_id);
        for (Pair<String, String> extra : extras) {
            intent.putExtra(extra.first, extra.second);
        }

        if (optionalLauncher == null) {
            startActivity(intent);
        } else {
            optionalLauncher.launch(intent);
        }
    }

    private abstract static class BaseTask {
        protected final WeakReference<DesignActivity> activityRef;

        protected BaseTask(DesignActivity activity) {
            activityRef = new WeakReference<>(activity);
        }

        protected DesignActivity getActivity() {
            return activityRef.get();
        }
    }

    private static class BuildTask extends BaseTask implements BuildProgressReceiver {
        public static final String ACTION_CANCEL_BUILD = "com.besome.sketch.design.ACTION_CANCEL_BUILD";
        private static final String CHANNEL_ID = "build_notification_channel";
        private final ExecutorService executorService = Executors.newSingleThreadExecutor();
        private final NotificationManager notificationManager;
        private final int notificationId = 1;
        private final MaterialButton btnRun;
        private final MaterialButton btnOptions;
        private final LinearLayout progressContainer;
        private final TextView progressText;
        private final LinearProgressIndicator progressBar;
        public volatile boolean canceled;
        private volatile boolean isBuildFinished;
        private boolean isShowingNotification = false;

        public BuildTask(DesignActivity activity) {
            super(activity);
            notificationManager = (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);
            btnRun = activity.btnRun;
            btnOptions = activity.btnOptions;
            progressContainer = activity.findViewById(R.id.progress_container);
            progressText = activity.findViewById(R.id.progress_text);
            progressBar = activity.findViewById(R.id.progress);
        }

        public void execute() {
            onPreExecute();
            executorService.execute(this::doInBackground);
        }

        private void onPreExecute() {
            DesignActivity activity = getActivity();
            if (activity == null) return;

            activity.runOnUiThread(() -> {
                updateRunButton(true);
                activity.r.a("P1I10", true);
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

                maybeShowNotification();
            });
        }

        private void doInBackground() {
            DesignActivity activity = getActivity();
            if (activity == null) return;

            try {
                var q = activity.q;
                var sc_id = DesignActivity.sc_id;
                new CompileErrorSaver(sc_id).startBuildLog();
                onProgress("Deleting temporary files...", 1);
                FileUtil.deleteFile(q.projectMyscPath);

                q.c(activity.getApplicationContext());
                q.a();
                q.a(activity.getApplicationContext(), wq.e("600"));
                if (ProjectMapUtils.getBoolean(lC.b(sc_id), "custom_icon")) {
                    q.aa(wq.e() + File.separator + sc_id + File.separator + "mipmaps");
                    if (ProjectMapUtils.getBoolean(lC.b(sc_id), "isIconAdaptive", false)) {
                        q.createLauncherIconXml("""
                                <?xml version="1.0" encoding="utf-8"?>
                                <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android" >
                                <background android:drawable="@mipmap/ic_launcher_background"/>
                                <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
                                <monochrome android:drawable="@mipmap/ic_launcher_monochrome"/>
                                </adaptive-icon>""");
                    } else {
                        q.a(wq.e() + File.separator + sc_id + File.separator + "icon.png");
                    }
                }

                onProgress("Generating source code...", 2);
                kC kC = jC.d(sc_id);
                kC.b(q.resDirectoryPath + File.separator + "drawable-xhdpi");
                kC = jC.d(sc_id);
                kC.c(q.resDirectoryPath + File.separator + "raw");
                kC = jC.d(sc_id);
                kC.a(q.assetsPath + File.separator + "fonts");

                ProjectBuilder builder = new ProjectBuilder(this, activity.getApplicationContext(), q);

                var fileManager = jC.b(sc_id);
                var dataManager = jC.a(sc_id);
                var libraryManager = jC.c(sc_id);
                q.a(libraryManager, fileManager, dataManager);
                builder.buildBuiltInLibraryInformation();
                q.b(fileManager, dataManager, libraryManager, builder.getBuiltInLibraryManager());
                q.f();
                q.e();

                builder.maybeExtractAapt2();
                if (canceled) {
                    return;
                }

                onProgress("Extracting built-in libraries...", 3);
                BuiltInLibraries.extractCompileAssets(this);
                if (canceled) {
                    return;
                }

                onProgress("AAPT2 is running...", 8);
                builder.compileResources();
                if (canceled) {
                    return;
                }

                onProgress("Generating view binding...", 11);
                builder.generateViewBinding();
                if (canceled) {
                    return;
                }

                KotlinCompilerBridge.compileKotlinCodeIfPossible(this, builder);
                if (canceled) {
                    return;
                }

                onProgress("Java is compiling...", 13);
                builder.compileJavaCode();
                if (canceled) {
                    return;
                }

                StringfogHandler stringfogHandler = new StringfogHandler(sc_id);
                stringfogHandler.start(this, builder);
                if (canceled) {
                    return;
                }

                ProguardHandler proguardHandler = new ProguardHandler(sc_id);
                proguardHandler.start(this, builder);
                if (canceled) {
                    return;
                }

                onProgress(builder.getDxRunningText(), 17);
                builder.createDexFilesFromClasses();
                if (canceled) {
                    return;
                }

                onProgress("Merging DEX files...", 18);
                builder.getDexFilesReady();
                if (canceled) {
                    return;
                }

                onProgress("Building APK...", 19);
                builder.buildApk();
                if (canceled) {
                    return;
                }

                onProgress("Signing APK...", 20);
                builder.signDebugApk();
                if (canceled) {
                    return;
                }

                activity.installBuiltApk();
                isBuildFinished = true;
            } catch (MissingFileException e) {
                isBuildFinished = true;
                activity.runOnUiThread(() -> {
                    boolean isMissingDirectory = e.isMissingDirectory();

                    MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(activity);
                    if (isMissingDirectory) {
                        dialog.setTitle("Missing directory detected");
                        dialog.setMessage("A directory important for building is missing. " +
                                "Sketchware Pro can try creating " + e.getMissingFile().getAbsolutePath() +
                                " if you'd like to.");
                        dialog.setNeutralButton("Create", (v, which) -> {
                            v.dismiss();
                            if (!e.getMissingFile().mkdirs()) {
                                SketchwareUtil.toastError("Failed to create directory / directories!");
                            }
                        });
                    } else {
                        dialog.setTitle("Missing file detected");
                        dialog.setMessage("A file needed for building is missing. " +
                                "Put the correct file back to " + e.getMissingFile().getAbsolutePath() +
                                " and try building again.");
                    }
                    dialog.setPositiveButton("Dismiss", null);
                    dialog.show();
                });
            } catch (zy zy) {
                isBuildFinished = true;
                activity.indicateCompileErrorOccurred(zy.getMessage());
            } catch (Throwable tr) {
                isBuildFinished = true;
                LogUtil.e("DesignActivity$BuildTask", "Failed to build project", tr);
                activity.indicateCompileErrorOccurred(Log.getStackTraceString(tr));
            } finally {
                activity.runOnUiThread(this::onPostExecute);
            }
        }

        @Override
        public void onProgress(String progress, int step) {
            int totalSteps = 20;

            DesignActivity activity = getActivity();
            if (activity == null) return;

            new CompileErrorSaver(DesignActivity.sc_id).appendLog("STEP " + step + "/20: " + progress);

            activity.runOnUiThread(() -> {
                progressBar.setIndeterminate(step == -1);
                if (!canceled) {
                    updateNotification(progress + " (" + step + " / " + totalSteps + ")");
                }
                progressText.setText(progress);
                var progressInt = (step * 100) / totalSteps;
                progressBar.setProgress(progressInt, true);
                Log.d("DesignActivity$BuildTask", step + " / " + totalSteps);
            });
        }

        private void onPostExecute() {
            DesignActivity activity = getActivity();
            if (activity == null) return;

            activity.runOnUiThread(() -> {
                if (!activity.isDestroyed()) {
                    if (isShowingNotification) {
                        notificationManager.cancel(notificationId);
                        isShowingNotification = false;
                    }
                    updateRunButton(false);
                    activity.updateBottomMenu();
                    activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            });
        }

        public void cancelBuild() {
            canceled = true;
            onProgress("Canceling build...", -1);
            if (isShowingNotification) {
                notificationManager.cancel(notificationId);
                isShowingNotification = false;
            }
            DesignActivity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(() -> {
                    activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                });
            }
        }

        private void maybeShowNotification() {
            DesignActivity activity = getActivity();
            if (activity == null) return;

            if (!isShowingNotification) {
                createNotificationChannelIfNeeded();

                NotificationCompat.Builder builder = new NotificationCompat.Builder(activity, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_mtrl_code)
                        .setContentTitle("Building project")
                        .setContentText("Starting build...")
                        .setOngoing(true)
                        .setProgress(0, 0, true)
                        .addAction(R.drawable.ic_cancel_white_96dp, "Cancel build", getCancelPendingIntent());

                notificationManager.notify(notificationId, builder.build());
                isShowingNotification = true;
            }
        }

        private void updateNotification(String progress) {
            DesignActivity activity = getActivity();
            if (activity == null) return;

            NotificationCompat.Builder builder = new NotificationCompat.Builder(activity, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_mtrl_code)
                    .setContentTitle("Building project")
                    .setContentText(progress)
                    .setOngoing(true)
                    .setProgress(0, 0, true)
                    .addAction(R.drawable.ic_cancel_white_96dp, "Cancel Build", getCancelPendingIntent());

            notificationManager.notify(notificationId, builder.build());
        }

        private PendingIntent getCancelPendingIntent() {
            DesignActivity activity = getActivity();
            if (activity == null) return null;

            Intent cancelIntent = new Intent(BuildTask.ACTION_CANCEL_BUILD);
            return PendingIntent.getBroadcast(activity, 0, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        }

        private void createNotificationChannelIfNeeded() {
            DesignActivity activity = getActivity();
            if (activity == null) return;

            CharSequence name = "Build Notifications";
            String description = "Notifications for build progress";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            notificationManager.createNotificationChannel(channel);
        }

        private void updateRunButton(boolean isRunning) {
            var context = getActivity();
            btnRun.setBackgroundTintList(ColorStateList.valueOf(ThemeUtils.getColor(context, isRunning ? R.attr.colorErrorContainer : R.attr.colorPrimary)));
            btnRun.setIcon(ContextCompat.getDrawable(context, isRunning ? R.drawable.ic_mtrl_stop : R.drawable.ic_mtrl_run));
            btnRun.setIconTint(ColorStateList.valueOf(ThemeUtils.getColor(context, isRunning ? R.attr.colorOnErrorContainer : R.attr.colorSurfaceContainerLowest)));
            btnRun.setTextColor(ColorStateList.valueOf(ThemeUtils.getColor(context, isRunning ? R.attr.colorOnErrorContainer : R.attr.colorSurfaceContainerLowest)));
            btnRun.setText(isRunning ? "Stop" : "Run");
            btnOptions.setEnabled(!isRunning);
            progressContainer.setVisibility(isRunning ? View.VISIBLE : View.GONE);
        }
    }

    private static class ProjectLoader extends BaseTask {
        private final Bundle savedInstanceState;
        private final ExecutorService executorService = Executors.newSingleThreadExecutor();

        public ProjectLoader(DesignActivity activity, Bundle savedInstanceState) {
            super(activity);
            this.savedInstanceState = savedInstanceState;
        }

        public void execute() {
            getActivity().k();
            executorService.execute(this::doInBackground);
        }

        private void doInBackground() {
            DesignActivity activity = getActivity();
            if (activity != null) {
                activity.loadProject(savedInstanceState != null);
                activity.runOnUiThread(() -> {
                    activity.updateBottomMenu();
                    activity.refresh();
                    activity.h();
                    if (savedInstanceState == null) {
                        activity.checkForUnsavedProjectData();
                    }
                });
            }
        }
    }

    private static class DiscardChangesProjectCloser extends BaseTask {
        private final ExecutorService executorService = Executors.newSingleThreadExecutor();

        public DiscardChangesProjectCloser(DesignActivity activity) {
            super(activity);
        }

        public void execute() {
            getActivity().k();
            executorService.execute(this::doInBackground);
        }

        private void doInBackground() {
            DesignActivity activity = getActivity();
            if (activity != null) {
                var sc_id = DesignActivity.sc_id;
                jC.d(sc_id).v();
                jC.d(sc_id).w();
                jC.d(sc_id).u();
                activity.runOnUiThread(() -> {
                    activity.h();
                    activity.finish();
                });
            }
        }
    }

    private static class ProjectSaver extends BaseTask {
        private final ExecutorService executorService = Executors.newSingleThreadExecutor();

        public ProjectSaver(DesignActivity activity) {
            super(activity);
        }

        public void execute() {
            getActivity().k();
            executorService.execute(this::doInBackground);
        }

        private void doInBackground() {
            DesignActivity activity = getActivity();
            if (activity != null) {
                var sc_id = DesignActivity.sc_id;
                jC.d(sc_id).a();
                jC.b(sc_id).m();
                jC.a(sc_id).j();
                jC.d(sc_id).x();
                jC.c(sc_id).l();
                activity.runOnUiThread(() -> {
                    bB.a(activity.getApplicationContext(), Helper.getResString(R.string.common_message_complete_save), bB.TOAST_NORMAL).show();
                    activity.saveVersionCodeInformationToProject();
                    activity.h();
                    jC.d(sc_id).f();
                    jC.d(sc_id).g();
                    jC.d(sc_id).e();
                });
            }
        }
    }

    private static class SaveChangesProjectCloser extends BaseTask {
        private final ExecutorService executorService = Executors.newSingleThreadExecutor();

        public SaveChangesProjectCloser(DesignActivity activity) {
            super(activity);
        }

        public void execute() {
            getActivity().k();
            executorService.execute(this::doInBackground);
        }

        private void doInBackground() {
            DesignActivity activity = getActivity();
            if (activity != null) {
                var sc_id = DesignActivity.sc_id;
                jC.d(sc_id).a();
                jC.b(sc_id).m();
                jC.a(sc_id).j();
                jC.d(sc_id).x();
                jC.c(sc_id).l();
                jC.d(sc_id).h();
                activity.runOnUiThread(() -> {
                    bB.a(activity.getApplicationContext(), Helper.getResString(R.string.common_message_complete_save), bB.TOAST_NORMAL).show();
                    activity.saveVersionCodeInformationToProject();
                    activity.h();
                    activity.finish();
                });
            }
        }
    }

    private static class UnsavedChangesSaver extends BaseTask {
        private final ExecutorService executorService = Executors.newSingleThreadExecutor();

        public UnsavedChangesSaver(DesignActivity activity) {
            super(activity);
        }

        public void execute() {
            executorService.execute(this::doInBackground);
        }

        private void doInBackground() {
            DesignActivity activity = getActivity();
            if (activity != null) {
                eC ecInstance = jC.a(sc_id);
                synchronized (ecInstance) {
                    ecInstance.k();
                }
            }
        }
    }

    private class ViewPagerAdapter extends FragmentPagerAdapter {
        private final String[] labels;

        public ViewPagerAdapter(FragmentManager fragmentManager) {
            super(fragmentManager);
            labels = new String[]{
                    Helper.getResString(R.string.design_tab_title_view),
                    Helper.getResString(R.string.design_tab_title_event),
                    Helper.getResString(R.string.design_tab_title_component),
                    Helper.getResString(R.string.design_tab_title_strings)};
        }

        @Override
        public int getCount() {
            return 4;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return labels[position];
        }

        @Override
        @NonNull
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            Fragment fragment = (Fragment) super.instantiateItem(container, position);
            if (position == 0) {
                viewTabAdapter = (ViewEditorFragment) fragment;
            } else if (position == 1) {
                eventTabAdapter = (rs) fragment;
            } else if (position == 2) {
                componentTabAdapter = (br) fragment;
            } else if (position == 3) {
                stringsTabAdapter = (StringsTabFragment) fragment;
            }

            return fragment;
        }

        @Override
        @NonNull
        public Fragment getItem(int position) {
            if (position == 0) {
                return new ViewEditorFragment();
            } else if (position == 1) {
                return new rs();
            } else if (position == 2) {
                return new br();
            } else {
                return new StringsTabFragment();
            }
        }
    }
}
