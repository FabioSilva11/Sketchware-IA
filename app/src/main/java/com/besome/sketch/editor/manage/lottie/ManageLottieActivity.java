package com.besome.sketch.editor.manage.lottie;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.besome.sketch.lib.base.BaseAppCompatActivity;

import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.databinding.ManageLottieBinding;

public class ManageLottieActivity extends BaseAppCompatActivity implements ViewPager.OnPageChangeListener {

    private String sc_id;
    private ManageLottieBinding binding;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ManageLottieBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.topAppBar);
        binding.topAppBar.setTitle(Helper.getResString(R.string.design_drawer_menu_title_lottie));
        binding.topAppBar.setNavigationOnClickListener(v -> onBackPressed());

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
    public void onPageScrollStateChanged(int state) {
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
    }

    @Override
    public void onPageSelected(int position) {
        binding.layoutBtnGroup.setVisibility(android.view.View.GONE);
        binding.layoutBtnImport.setVisibility(android.view.View.GONE);

        if (position == 0) {
            binding.fab.animate().translationY(0F).setDuration(200L).start();
            binding.fab.show();
        } else {
            binding.fab.animate().translationY(400F).setDuration(200L).start();
            binding.fab.hide();
        }
    }

    @Override
    protected void onSaveInstanceState(@Nullable Bundle outState) {
        if (outState != null) {
            outState.putString("sc_id", sc_id);
        }
        super.onSaveInstanceState(outState);
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
        public CharSequence getPageTitle(int position) {
            return labels[position];
        }

        @Override
        @NonNull
        public Fragment getItem(int position) {
            return position == 0 ? new ProjectLottiesFragment() : new CollectionLottiesFragment();
        }
    }
}
