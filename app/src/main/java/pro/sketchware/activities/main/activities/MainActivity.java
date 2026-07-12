package pro.sketchware.activities.main.activities;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.core.app.ActivityCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.besome.sketch.lib.base.BasePermissionAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.FirebaseApp;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.messaging.FirebaseMessaging;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import a.a.a.DB;
import a.a.a.GB;
import a.a.a.lC;
import mod.hey.studios.project.backup.BackupFactory;
import mod.hey.studios.project.backup.BackupRestoreManager;
import mod.hey.studios.util.Helper;
import mod.hey.studios.util.AppUpdateNotifier;
import mod.hilal.saif.activities.tools.ConfigActivity;
import mod.tyron.backup.SingleCopyTask;
import pro.sketchware.R;
import pro.sketchware.activities.about.AboutActivity;
import pro.sketchware.activities.main.fragments.projects.ProjectsFragment;
import pro.sketchware.activities.main.fragments.projects_store.ProjectsStoreFragment;
import pro.sketchware.activities.main.fragments.web_service.WebServiceFragment;
import pro.sketchware.activities.main.fragments.chat.ChatFragment;
import pro.sketchware.databinding.MainBinding;
import pro.sketchware.lib.base.BottomSheetDialogView;
import pro.sketchware.utility.DataResetter;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.UI;
import pro.sketchware.utility.TranslationFunction;

public class MainActivity extends BasePermissionAppCompatActivity {
    private static final String PROJECTS_FRAGMENT_TAG = "projects_fragment";
    private static final String PROJECTS_STORE_FRAGMENT_TAG = "projects_store_fragment";
    private static final String WEB_SERVICE_FRAGMENT_TAG = "web_service_fragment";
    private static final String CHAT_FRAGMENT_TAG = "chat_fragment";
    private static final int PAGE_PROJECTS = 0;
    private static final int PAGE_STORE = 1;
    private static final int PAGE_WEB_SERVICE = 2;
    private static final int PAGE_CHAT = 3;
    private static final int MAIN_PAGE_COUNT = 4;
    private static final int REQUEST_UPDATE_NOTIFICATIONS = 24012;
    private ActionBarDrawerToggle drawerToggle;
    private DB u;
    private Snackbar storageAccessDenied;
    private MainBinding binding;
    private final OnBackPressedCallback closeDrawer = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            setEnabled(false);
            binding.drawerLayout.closeDrawers();
        }
    };
    private ProjectsFragment projectsFragment;
    private ProjectsStoreFragment projectsStoreFragment;
    private WebServiceFragment webServiceFragment;
    private ChatFragment chatFragment;
    private Fragment activeFragment;
    @IdRes
    private int currentNavItemId = R.id.item_projects;
    private static final String PREFS_ADS_NOTICE = "main_prefs";
    private static final String KEY_ADS_NOTICE_SHOWN = "ads_notice_shown";
    private androidx.appcompat.app.AlertDialog adsNoticeDialog;

    private static boolean isFirebaseInitialized(Context context) {
        try {
            return FirebaseApp.getApps(context) != null && !FirebaseApp.getApps(context).isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    // onRequestPermissionsResult but for Storage access only, and only when granted
    public void g(int i) {
        if (i == 9501) {
            allFilesAccessCheck();
            restoreExternalTranslationSupport();
            maybeShowAdsNoticeOnce();

            refreshProjectBackedFragments();
        }
    }

    @Override
    public void h(int i) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getApplicationContext().getPackageName()));
        startActivityForResult(intent, i);
    }

    @Override
    public void l() {
    }

    @Override
    public void m() {
    }

    public void n() {
        refreshProjectBackedFragments();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case 105:
                    DataResetter.a(this, data.getBooleanExtra("onlyConfig", true));
                    break;

                case 111:
                    invalidateOptionsMenu();
                    break;

                case 113:
                    if (data != null && data.getBooleanExtra("not_show_popup_anymore", false)) {
                        u.a("U1I2", (Object) false);
                    }
                    break;

                case 212:
                    if (!(data.getStringExtra("save_as_new_id") == null ? "" : data.getStringExtra("save_as_new_id")).isEmpty() && isStoragePermissionGranted()) {
                        refreshProjectBackedFragments();
                    }
                    break;
            }
        }
    }

    public void refreshChatProjectsList() {
        if (chatFragment != null && chatFragment.isAdded()) {
            chatFragment.refreshProjectsList();
        }
    }

    private void refreshProjectBackedFragments() {
        if (projectsFragment != null && projectsFragment.isAdded()) {
            projectsFragment.refreshProjectsList();
        }
        refreshChatProjectsList();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        drawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        enableEdgeToEdgeNoContrast();
        boolean hasStorageAccess = isStoragePermissionGranted();
        if (hasStorageAccess) {
            restoreExternalTranslationSupport();
        }

        binding = MainBinding.inflate(getLayoutInflater());

        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        binding.statusBarOverlapper.setMinimumHeight(UI.getStatusBarHeight(this));
        UI.addSystemWindowInsetToPadding(binding.appbar, true, false, true, false);

        u = new DB(getApplicationContext(), "U1");
        int u1I0 = u.a("U1I0", -1);
        long u1I1 = u.e("U1I1");
        if (u1I1 <= 0) {
            u.a("U1I1", System.currentTimeMillis());
        }
        if (System.currentTimeMillis() - u1I1 > /* (a day) */ 1000 * 60 * 60 * 24) {
            u.a("U1I0", Integer.valueOf(u1I0 + 1));
        }

        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(null);

        drawerToggle = new ActionBarDrawerToggle(this, binding.drawerLayout, R.string.app_name, R.string.app_name);
        binding.drawerLayout.addDrawerListener(drawerToggle);
        binding.drawerLayout.addDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {
            }

            @Override
            public void onDrawerOpened(@NonNull View drawerView) {
                closeDrawer.setEnabled(true);
                getOnBackPressedDispatcher().addCallback(closeDrawer);
            }

            @Override
            public void onDrawerClosed(@NonNull View drawerView) {
            }

            @Override
            public void onDrawerStateChanged(int newState) {
            }
        });

        if (!hasStorageAccess) {
            showNoticeNeedStorageAccess();
        }
        if (hasStorageAccess) {
            allFilesAccessCheck();
            maybeShowAdsNoticeOnce();
        }

        if (Intent.ACTION_VIEW.equals(getIntent().getAction())) {
            Uri data = getIntent().getData();
            if (data != null) {
                new SingleCopyTask(this, new SingleCopyTask.CallBackTask() {
                    @Override
                    public void onCopyPreExecute() {
                    }

                    @Override
                    public void onCopyProgressUpdate(int progress) {
                    }

                    @Override
                    public void onCopyPostExecute(@NonNull String path, boolean wasSuccessful, @NonNull String reason) {
                        if (wasSuccessful) {
                            BackupRestoreManager manager = new BackupRestoreManager(MainActivity.this, projectsFragment);

                            if (BackupFactory.zipContainsFile(path, "local_libs")) {
                                new MaterialAlertDialogBuilder(MainActivity.this)
                                        .setTitle("Warning")
                                        .setMessage(BackupRestoreManager.getRestoreIntegratedLocalLibrariesMessage(false, -1, -1, null))
                                        .setPositiveButton("Copy", (dialog, which) -> manager.doRestore(path, true))
                                        .setNegativeButton("Don't copy", (dialog, which) -> manager.doRestore(path, false))
                                        .setNeutralButton(R.string.common_word_cancel, null)
                                        .show();
                            } else {
                                manager.doRestore(path, true);
                            }

                            // Clear intent so it doesn't duplicate
                            getIntent().setData(null);
                        } else {
                            SketchwareUtil.toastError("Failed to copy backup file to temporary location: " + reason, Toast.LENGTH_LONG);
                        }
                    }
                }).copyFile(data);
            }
        }

        // Exibir a aba/fragmento de loja na navegação inferior
        binding.bottomNav.getMenu().findItem(R.id.item_store).setVisible(true);

        binding.bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.item_projects) {
                navigateToProjectsFragment();
                return true;
            } else if (id == R.id.item_store) {
                navigateToStoreFragment();
                return true;
            } else if (id == R.id.item_web_service) {
                navigateToWebServiceFragment();
                return true;
            } else if (id == R.id.item_chat) {
                navigateToChatFragment();
                return true;
            }
            return false;
        });

        int initialPage = PAGE_PROJECTS;
        if (savedInstanceState != null) {
            currentNavItemId = savedInstanceState.getInt("selected_tab_id", R.id.item_projects);
            initialPage = navIdToPage(currentNavItemId);
        }
        setupMainPager(initialPage);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_UPDATE_NOTIFICATIONS);
        }
        AppUpdateNotifier.checkForUpdates(this);
    }

    private void maybeShowAdsNoticeOnce() {
        if (adsNoticeDialog != null && adsNoticeDialog.isShowing()) return;
        boolean shown = false;
        if (shown) return;

        View content = getLayoutInflater().inflate(R.layout.bottomsheet_ads_notice, null);
        applyDonationDialogTranslations(content);
        adsNoticeDialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setView(content)
                .create();
        adsNoticeDialog.setCanceledOnTouchOutside(true);
        adsNoticeDialog.setCancelable(true);

        View close = content.findViewById(R.id.close);
        View donate = content.findViewById(R.id.donate);

        close.setOnClickListener(v -> {
            getSharedPreferences(PREFS_ADS_NOTICE, MODE_PRIVATE)
                    .edit().putBoolean(KEY_ADS_NOTICE_SHOWN, true).apply();
            adsNoticeDialog.dismiss();
        });

        donate.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(TranslationFunction.getString(this, R.string.link_donation_url)));
                startActivity(intent);
            } catch (Exception ignored) { }
            getSharedPreferences(PREFS_ADS_NOTICE, MODE_PRIVATE)
                    .edit().putBoolean(KEY_ADS_NOTICE_SHOWN, true).apply();
            adsNoticeDialog.dismiss();
        });

        adsNoticeDialog.show();
    }

    private void applyDonationDialogTranslations(View content) {
        ImageView donationImage = content.findViewById(R.id.donation_image);
        TextView title = content.findViewById(R.id.title);
        TextView body = content.findViewById(R.id.body);
        TextView donate = content.findViewById(R.id.donate);
        TextView close = content.findViewById(R.id.close);

        String translatedTitle = TranslationFunction.getString(this, R.string.donation_dialog_title);
        if (donationImage != null) {
            donationImage.setContentDescription(translatedTitle);
        }
        if (title != null) {
            title.setText(translatedTitle);
        }
        if (body != null) {
            body.setText(TranslationFunction.getString(this, R.string.donation_dialog_message));
        }
        if (donate != null) {
            donate.setText(TranslationFunction.getString(this, R.string.donation_button_donate));
        }
        if (close != null) {
            close.setText(TranslationFunction.getString(this, R.string.donation_button_cancel));
        }
    }

    private void restoreExternalTranslationSupport() {
        lC.d();
        if (TranslationFunction.initialize(this)) {
            SketchwareUtil.toast(getString(R.string.message_strings_xml_loaded));
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("selected_tab_id", currentNavItemId);
    }

    private void navigateToProjectsFragment() {
        selectMainPage(PAGE_PROJECTS);
    }

    private void navigateToStoreFragment() {
        selectMainPage(PAGE_STORE);
    }

    private void navigateToWebServiceFragment() {
        selectMainPage(PAGE_WEB_SERVICE);
    }

    private void navigateToChatFragment() {
        selectMainPage(PAGE_CHAT);
    }

    private void setupMainPager(int initialPage) {
        binding.container.setAdapter(new MainPagerAdapter(this));
        binding.container.setOffscreenPageLimit(MAIN_PAGE_COUNT - 1);
        binding.container.setCurrentItem(initialPage, false);
        updateSelectedPage(initialPage);
        binding.bottomNav.setSelectedItemId(pageToNavId(initialPage));
        binding.container.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateSelectedPage(position);
                binding.bottomNav.setSelectedItemId(pageToNavId(position));
            }
        });
    }

    private void selectMainPage(int page) {
        if (binding.container.getCurrentItem() != page) {
            binding.container.setCurrentItem(page, true);
        } else {
            updateSelectedPage(page);
        }
    }

    private void updateSelectedPage(int page) {
        Fragment fragment = findPagerFragment(page);
        if (fragment == null) {
            fragment = getOrCreateFragment(page);
        }
        if (fragment instanceof ProjectsFragment) {
            projectsFragment = (ProjectsFragment) fragment;
        } else if (fragment instanceof ProjectsStoreFragment) {
            projectsStoreFragment = (ProjectsStoreFragment) fragment;
        } else if (fragment instanceof WebServiceFragment) {
            webServiceFragment = (WebServiceFragment) fragment;
        } else if (fragment instanceof ChatFragment) {
            chatFragment = (ChatFragment) fragment;
        }
        activeFragment = fragment;
        currentNavItemId = pageToNavId(page);
        binding.searchHint.setText(page == PAGE_STORE ? R.string.store_search_hint : R.string.main_search_projects_hint);
        if (page == PAGE_PROJECTS) {
            binding.createNewProject.show();
        } else {
            binding.createNewProject.hide();
        }
    }

    public boolean isStorePageActive() {
        return activeFragment instanceof ProjectsStoreFragment;
    }

    public boolean handleMainSearchQuery(String query) {
        if (activeFragment instanceof ProjectsStoreFragment && projectsStoreFragment != null) {
            projectsStoreFragment.setSearchQuery(query);
            return true;
        }
        return false;
    }

    private Fragment findPagerFragment(int page) {
        return getSupportFragmentManager().findFragmentByTag("f" + page);
    }

    private Fragment getOrCreateFragment(int page) {
        return switch (page) {
            case PAGE_STORE -> {
                if (projectsStoreFragment == null) projectsStoreFragment = new ProjectsStoreFragment();
                yield projectsStoreFragment;
            }
            case PAGE_WEB_SERVICE -> {
                if (webServiceFragment == null) webServiceFragment = new WebServiceFragment();
                yield webServiceFragment;
            }
            case PAGE_CHAT -> {
                if (chatFragment == null) chatFragment = new ChatFragment();
                yield chatFragment;
            }
            default -> {
                if (projectsFragment == null) projectsFragment = new ProjectsFragment();
                yield projectsFragment;
            }
        };
    }

    private int navIdToPage(@IdRes int navItemId) {
        if (navItemId == R.id.item_store) {
            return PAGE_STORE;
        } else if (navItemId == R.id.item_web_service) {
            return PAGE_WEB_SERVICE;
        } else if (navItemId == R.id.item_chat) {
            return PAGE_CHAT;
        }
        return PAGE_PROJECTS;
    }

    @IdRes
    private int pageToNavId(int page) {
        return switch (page) {
            case PAGE_STORE -> R.id.item_store;
            case PAGE_WEB_SERVICE -> R.id.item_web_service;
            case PAGE_CHAT -> R.id.item_chat;
            default -> R.id.item_projects;
        };
    }

    private final class MainPagerAdapter extends FragmentStateAdapter {
        MainPagerAdapter(@NonNull MainActivity activity) {
            super(activity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return getOrCreateFragment(position);
        }

        @Override
        public int getItemCount() {
            return MAIN_PAGE_COUNT;
        }
    }


    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (drawerToggle.onOptionsItemSelected(item)) {
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        drawerToggle.syncState();
        if (isFirebaseInitialized(this)) {
            FirebaseMessaging.getInstance().subscribeToTopic("all");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        /* Check if the device is running low on storage space */
        long freeMegabytes = GB.c();
        if (freeMegabytes < 100 && freeMegabytes > 0) {
            showNoticeNotEnoughFreeStorageSpace();
        }
        if (isStoragePermissionGranted() && storageAccessDenied != null && storageAccessDenied.isShown()) {
            storageAccessDenied.dismiss();
        }
        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.SCREEN_NAME, "MainActivity");
        bundle.putString(FirebaseAnalytics.Param.SCREEN_CLASS, "MainActivity");
        mAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle);
    }

    private void allFilesAccessCheck() {
        if (Build.VERSION.SDK_INT > 29) {
            File optOutFile = new File(getFilesDir(), ".skip_all_files_access_notice");
            boolean granted = Environment.isExternalStorageManager();

            if (!optOutFile.exists() && !granted) {
                MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
                dialog.setIcon(R.drawable.ic_expire_48dp);
                dialog.setTitle("Android 11 storage access");
                dialog.setMessage("Starting with Android 11, Sketchware Pro needs a new permission to avoid " + "taking ages to build projects. Don't worry, we can't do more to storage than " + "with current granted permissions.");
                dialog.setPositiveButton(Helper.getResString(R.string.common_word_settings), (v, which) -> {
                    FileUtil.requestAllFilesAccessPermission(this);
                    v.dismiss();
                });
                dialog.setNegativeButton("Skip", null);
                dialog.setNeutralButton("Don't show anymore", (v, which) -> {
                    try {
                        if (!optOutFile.createNewFile())
                            throw new IOException("Failed to create file " + optOutFile);
                    } catch (IOException e) {
                        Log.e("MainActivity", "Error while trying to create " + "\"Don't show Android 11 hint\" dialog file: " + e.getMessage(), e);
                    }
                    v.dismiss();
                });
                dialog.show();
            }
        }
    }

    private void showNoticeNeedStorageAccess() {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setTitle(TranslationFunction.getString(this, R.string.common_message_permission_title_storage));
        dialog.setIcon(R.drawable.color_about_96);
        dialog.setMessage(TranslationFunction.getString(this, R.string.common_message_permission_need_load_project));
        dialog.setPositiveButton(TranslationFunction.getString(this, R.string.common_word_ok), (v, which) -> {
            v.dismiss();
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 9501);
        });
        dialog.show();
    }

    private void showNoticeNotEnoughFreeStorageSpace() {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setTitle(TranslationFunction.getString(this, R.string.common_message_insufficient_storage_space_title));
        dialog.setIcon(R.drawable.high_priority_96_red);
        dialog.setMessage(TranslationFunction.getString(this, R.string.common_message_insufficient_storage_space));
        dialog.setPositiveButton(TranslationFunction.getString(this, R.string.common_word_ok), null);
        dialog.show();
    }

    public void s() {
        if (storageAccessDenied == null || !storageAccessDenied.isShown()) {
            storageAccessDenied = Snackbar.make(binding.layoutCoordinator, Helper.getResString(R.string.common_message_permission_denied), Snackbar.LENGTH_INDEFINITE);
            storageAccessDenied.setAction(Helper.getResString(R.string.common_word_settings), v -> {
                storageAccessDenied.dismiss();
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 9501);
            });
            storageAccessDenied.setActionTextColor(Color.YELLOW);
            storageAccessDenied.show();
        }
    }

}
