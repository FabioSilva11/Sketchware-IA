package pro.sketchware.activities.main.fragments.projects_store;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.fragment.app.Fragment;

import com.besome.sketch.design.DesignActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.tabs.TabLayout;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Locale;

import a.a.a.lC;
import mod.hey.studios.project.ProjectTracker;
import mod.hey.studios.project.backup.BackupFactory;
import pro.sketchware.R;
import pro.sketchware.utility.AdManager;
import pro.sketchware.activities.main.MainSearchable;
import pro.sketchware.activities.studio.AndroidStudioProjectActivity;

public class ProjectsStoreFragment extends Fragment implements MainSearchable {
    private static final String ROOT = "project_templates";
    private record Model(String title, String asset, boolean androidStudio) {}

    private ListView cards;
    private ViewTreeObserver.OnGlobalLayoutListener navigationInsetListener;
    private ArrayAdapter<Model> adapter;
    private TextView empty;
    private boolean studio;
    private boolean importing;
    private boolean waitingForAd;
    private String query = "";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle state) {
        LinearLayout page = column();
        page.setBackgroundColor(requireContext().getColor(R.color.chat_background));
        TextView heading = text("Modelos", 20, R.color.chat_text_primary);
        heading.setPadding(dp(16), dp(12), dp(16), dp(8));
        page.addView(heading);
        TabLayout tabs = new TabLayout(requireContext());
        tabs.setTabTextColors(requireContext().getColor(R.color.chat_text_secondary),
                requireContext().getColor(R.color.chat_accent));
        tabs.setSelectedTabIndicatorColor(requireContext().getColor(R.color.chat_accent));
        tabs.addTab(tabs.newTab().setText("Sketchware"), !studio);
        tabs.addTab(tabs.newTab().setText("Android Studio"), studio);
        page.addView(tabs);
        FrameLayout catalog = new FrameLayout(requireContext());
        cards = new ListView(requireContext());
        cards.setDivider(null);
        cards.setPadding(dp(12), dp(8), dp(12), dp(8));
        catalog.addView(cards, new FrameLayout.LayoutParams(-1, -1));
        empty = text("", 14, R.color.chat_text_secondary);
        empty.setPadding(dp(16), dp(12), dp(16), 0);
        catalog.addView(empty, new FrameLayout.LayoutParams(-1, -2));
        cards.setEmptyView(empty);
        adapter = new ArrayAdapter<>(requireContext(), R.layout.item_project_template) {
            @Override
            public View getView(int position, View reusableRow, ViewGroup parent) {
                View row = reusableRow == null
                        ? inflater.inflate(R.layout.item_project_template, parent, false)
                        : reusableRow;
                TextView title = row.findViewById(R.id.template_title);
                title.setText(getItem(position).title());
                return row;
            }
        };
        cards.setAdapter(adapter);
        cards.setOnItemClickListener((list, row, position, id) -> {
            Model model = adapter.getItem(position);
            showDetails(model.title(), model.asset(), model.androidStudio());
        });
        page.addView(catalog, new LinearLayout.LayoutParams(-1, 0, 1));
        View bottomNav = requireActivity().findViewById(R.id.bottom_nav);
        if (bottomNav != null) {
            int[] listLocation = new int[2];
            int[] navLocation = new int[2];
            navigationInsetListener = () -> {
                cards.getLocationInWindow(listLocation);
                bottomNav.getLocationInWindow(navLocation);
                int overlap = bottomNav.isShown()
                        ? Math.max(0, listLocation[1] + cards.getHeight() - navLocation[1])
                        : 0;
                int padding = dp(8) + overlap;
                if (cards.getPaddingBottom() != padding) {
                    cards.setPadding(dp(12), dp(8), dp(12), padding);
                }
            };
            cards.getViewTreeObserver().addOnGlobalLayoutListener(navigationInsetListener);
        }
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                studio = tab.getPosition() == 1;
                showModels();
                cards.setSelection(0);
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
        showModels();
        return page;
    }

    @Override
    public void onMainSearch(String value) {
        query = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (cards != null) showModels();
    }

    @Override
    public void onDestroyView() {
        if (navigationInsetListener != null) {
            cards.getViewTreeObserver().removeOnGlobalLayoutListener(navigationInsetListener);
            navigationInsetListener = null;
        }
        cards = null;
        adapter = null;
        empty = null;
        super.onDestroyView();
    }

    private void showModels() {
        adapter.clear();
        empty.setText(query.isEmpty() ? "Nenhum modelo disponível nesta categoria."
                : "Nenhum modelo encontrado.");
        String folder = ROOT + (studio ? "/studio" : "/sketchware");
        String extension = studio ? ".zip" : ".swb";
        try {
            String[] files = requireContext().getAssets().list(folder);
            if (files == null) return;
            Arrays.sort(files);
            for (String file : files) {
                if (!file.endsWith(extension)) continue;
                String title = file.substring(0, file.length() - extension.length());
                if (title.toLowerCase(Locale.ROOT).contains(query)) {
                    adapter.add(new Model(title, folder + "/" + file, studio));
                }
            }
        } catch (IOException e) {
            empty.setText("Não foi possível carregar os modelos.");
        }
    }
    private void showDetails(String title, String asset, boolean androidStudio) {
        if (importing || waitingForAd) return;
        var context = requireContext();
        var icon = AppCompatResources.getDrawable(context, R.drawable.ic_mtrl_circle_play).mutate();
        icon.setTint(context.getColor(R.color.chat_accent));
        var background = new MaterialShapeDrawable();
        background.setCornerSize(dp(28));
        background.setTint(context.getColor(R.color.chat_surface));
        String format = getString(androidStudio
                ? R.string.template_format_studio : R.string.template_format_sketchware);
        new MaterialAlertDialogBuilder(context)
                .setBackground(background)
                .setIcon(icon)
                .setTitle(title)
                .setMessage(getString(R.string.template_unlock_message, format))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.template_watch_video,
                        (dialog, which) -> unlockModel(asset, androidStudio))
                .show();
    }

    private void unlockModel(String asset, boolean androidStudio) {
        if (importing || waitingForAd) return;
        waitingForAd = true;
        AdManager.showTemplateReward(requireActivity(), completed -> {
            waitingForAd = false;
            if (!isAdded() || getView() == null || requireActivity().isFinishing()) return;
            if (completed) {
                importModel(asset, androidStudio);
            } else {
                new AlertDialog.Builder(requireContext())
                        .setMessage("O vídeo não foi concluído. Tente novamente para usar o modelo.")
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
        });
    }
    private void importModel(String asset, boolean androidStudio) {
        if (importing || waitingForAd) return;
        importing = true;
        var activity = requireActivity();
        var context = activity.getApplicationContext();
        new Thread(() -> {
            File archive = null;
            try {
                archive = File.createTempFile("template-", androidStudio ? ".zip" : ".swb", context.getCacheDir());
                try (var input = context.getAssets().open(asset);
                     var output = Files.newOutputStream(archive.toPath())) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                }
                String id = lC.b();
                BackupFactory backup = new BackupFactory(id);
                if (androidStudio) backup.restoreAndroidStudioProject(archive);
                else backup.restore(archive);
                if (!backup.isRestoreSuccess()) throw new IOException(backup.getError());
                activity.runOnUiThread(() -> {
                    importing = false;
                    if (!isAdded() || activity.isFinishing()) return;
                    Intent intent;
                    if (androidStudio) {
                        intent = new Intent(activity, AndroidStudioProjectActivity.class)
                                .putExtra(AndroidStudioProjectActivity.EXTRA_SC_ID, id);
                    } else {
                        ProjectTracker.setScId(id);
                        intent = new Intent(activity, DesignActivity.class).putExtra("sc_id", id);
                    }
                    startActivity(intent);
                });
            } catch (IOException | RuntimeException e) {
                activity.runOnUiThread(() -> {
                    importing = false;
                    if (!isAdded() || activity.isFinishing()) return;
                    new AlertDialog.Builder(activity)
                            .setMessage("Não foi possível importar o modelo.")
                            .setPositiveButton(android.R.string.ok, null).show();
                });
            } finally {
                if (archive != null) archive.delete();
            }
        }, "project-template-import").start();
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(requireContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(requireContext().getColor(color));
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
