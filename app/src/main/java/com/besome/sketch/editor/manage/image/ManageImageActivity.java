package com.besome.sketch.editor.manage.image;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import java.lang.ref.WeakReference;

import pro.sketchware.core.BackgroundTask;
import pro.sketchware.filemanagers.SoundResourceManager;
import pro.sketchware.filemanagers.ImageResourcePickerFragment;
import pro.sketchware.editor.EditorViewUtils;
import pro.sketchware.filemanagers.ImageResourceLibraryFragment;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.databinding.ManageImageBinding;
import pro.sketchware.utility.TranslationFunction;

public class ManageImageActivity extends BaseAppCompatActivity implements ViewPager.OnPageChangeListener {
    private String sc_id;
    private ImageResourceLibraryFragment projectImagesFragment;
    private ImageResourcePickerFragment collectionImagesFragment;
    private ManageImageBinding binding;


    public static int getImageGridColumnCount(Context context) {
        var displayMetrics = context.getResources().getDisplayMetrics();
        return (int) (displayMetrics.widthPixels / displayMetrics.density) / 100;
    }

    private String getTranslatedString(int resId) {
        // Fallback to standard getString; replace with translation logic if available

        return getString(resId);
    }

    @Override
    public void onPageScrollStateChanged(int state) {
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
    }

    public void f(int i) {
        binding.viewPager.setCurrentItem(i);
    }

    public ImageResourcePickerFragment l() {
        return collectionImagesFragment;
    }

    public ImageResourceLibraryFragment m() {
        return projectImagesFragment;
    }

    @Override
    public void onBackPressed() {
        if (projectImagesFragment.isSelecting) {
            projectImagesFragment.a(false);
        } else if (collectionImagesFragment.isSelecting()) {
            collectionImagesFragment.unselectAll();
            binding.layoutBtnImport.setVisibility(View.GONE);
        } else {
            k();
            new Handler().postDelayed(() -> new SaveImagesAsyncTask(this).execute(), 500L);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ManageImageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (!super.isStoragePermissionGranted()) {
            finish();
        }

        setSupportActionBar(binding.topAppBar);
        binding.topAppBar.setTitle(Helper.getResString(R.string.design_actionbar_title_manager_image));
        binding.topAppBar.setNavigationOnClickListener(v -> {
            if (!EditorViewUtils.a()) {
                onBackPressed();
            }
        });
        if (savedInstanceState == null) {
            sc_id = getIntent().getStringExtra("sc_id");
        } else {
            sc_id = savedInstanceState.getString("sc_id");
        }

        binding.viewPager.setAdapter(new PagerAdapter(getSupportFragmentManager()));
        binding.viewPager.setOffscreenPageLimit(2);
        binding.viewPager.addOnPageChangeListener(this);
        binding.tabLayout.setupWithViewPager(binding.viewPager);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!super.isStoragePermissionGranted()) {
            finish();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString("sc_id", sc_id);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onPageSelected(int position) {
        binding.layoutBtnGroup.setVisibility(View.GONE);
        binding.layoutBtnImport.setVisibility(View.GONE);

        if (position == 0) {
            binding.fab.animate().translationY(0F).setDuration(200L).start();
            binding.fab.show();
            collectionImagesFragment.unselectAll();
        } else {
            binding.fab.animate().translationY(400F).setDuration(200L).start();
            binding.fab.hide();
            projectImagesFragment.a(false);
        }
    }

    private static class SaveImagesAsyncTask extends BackgroundTask {
        private final WeakReference<ManageImageActivity> activity;

        public SaveImagesAsyncTask(ManageImageActivity activity) {
            super(activity);
            this.activity = new WeakReference<>(activity);
            activity.a(this);
        }

        @Override
        public void a() {
            var activity = this.activity.get();
            activity.h();
            activity.setResult(Activity.RESULT_OK);
            activity.finish();
            SoundResourceManager.g().d();
        }

        @Override
        public void b() {
            activity.get().projectImagesFragment.saveImages();
        }

        @Override
        public void a(String str) {
            activity.get().h();
        }
    }

    private class PagerAdapter extends FragmentPagerAdapter {
        private final String[] labels;

        public PagerAdapter(FragmentManager manager) {
            super(manager);
            labels = new String[2];
            labels[0] = Helper.getResString(R.string.design_manager_tab_title_this_project);
            labels[1] = Helper.getResString(R.string.design_manager_tab_title_my_collection);
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        @NonNull
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            Fragment fragment = (Fragment) super.instantiateItem(container, position);
            if (position == 0) {
                projectImagesFragment = (ImageResourceLibraryFragment) fragment;
            } else {
                collectionImagesFragment = (ImageResourcePickerFragment) fragment;
            }
            return fragment;
        }

        @Override
        @NonNull
        public Fragment getItem(int position) {
            if (position != 0) {
                return new ImageResourcePickerFragment();
            }
            return new ImageResourceLibraryFragment();
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return labels[position];
        }
    }
}
