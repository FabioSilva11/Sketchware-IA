package com.besome.sketch.editor.manage.lottie;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.beans.ProjectResourceBean;
import com.besome.sketch.editor.manage.lottie.AddLottieActivity;
import com.besome.sketch.editor.manage.lottie.ManageLottieActivity;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import pro.sketchware.R;
import pro.sketchware.activities.importicon.ImportIconActivity;
import pro.sketchware.databinding.FrManageLottieListBinding;
import pro.sketchware.databinding.ManageLottieListItemBinding;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.SvgUtils;
import com.airbnb.lottie.LottieDrawable;
import a.a.a.bB;
import a.a.a.iB;
import a.a.a.jC;
import a.a.a.kC;
import a.a.a.oB;
import a.a.a.qA;

public class LottieProjectFragment extends qA {

    private final FilePathUtil fpu = new FilePathUtil();
    public boolean isSelecting = false;
    public SvgUtils svgUtils;
    Map<Integer, Map<String, Object>> colorMap = new HashMap<>();
    private FrManageLottieListBinding binding;
    private String sc_id;
    private ArrayList<ProjectResourceBean> lotties;
    private MaterialCardView actionButtonContainer;
    private FloatingActionButton fab;
    private String projectLottiesDirectory = "";
    private Adapter adapter = null;
    private final ActivityResultLauncher<Intent> openImportIconActivity = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK) {
            var data = result.getData();
            assert data != null;
            ProjectResourceBean icon = new ProjectResourceBean(
                    ProjectResourceBean.PROJECT_RES_TYPE_FILE,
                    data.getStringExtra("iconName"), data.getStringExtra("iconPath")
            );
            icon.savedPos = 2;
            icon.isNew = true;

            int selectedColor = data.getIntExtra("iconColor", -1);
            String selectedColorHex = data.getStringExtra("iconColorHex");
            addNewColorFilterInfo(selectedColorHex, selectedColor, lotties.size());

            addImage(icon);
            bB.a(requireActivity(), getString(R.string.design_manager_message_add_complete), bB.TOAST_NORMAL).show();
        }
    });
    // Web search functionality removed for Lottie files
    /*
    private final ActivityResultLauncher<Intent> openImageWebSearchActivity = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK) {
            assert result.getData() != null;
            ArrayList<ProjectResourceBean> addedImages;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                addedImages = result.getData().getParcelableArrayListExtra("lotties", ProjectResourceBean.class);
            } else {
                addedImages = result.getData().getParcelableArrayListExtra("lotties");
            }
            if (addedImages != null && !addedImages.isEmpty()) {
                lotties.addAll(addedImages);
                adapter.notifyItemRangeInserted(lotties.size() - addedImages.size(), addedImages.size());
                updateGuideVisibility();
                ((ManageLottieActivity) requireActivity()).l().refreshData();
                bB.a(requireActivity(), getString(R.string.design_manager_message_add_complete), bB.TOAST_NORMAL).show();
            }
        }
    });
    */
    private final ActivityResultLauncher<Intent> showAddImageDialog = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK) {
            assert result.getData() != null;
            ArrayList<ProjectResourceBean> addedImages;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                addedImages = result.getData().getParcelableArrayListExtra("lotties", ProjectResourceBean.class);
            } else {
                addedImages = result.getData().getParcelableArrayListExtra("lotties");
            }
            lotties.addAll(addedImages);
            adapter.notifyItemRangeInserted(lotties.size() - addedImages.size(), addedImages.size());
            updateGuideVisibility();
            ((ManageLottieActivity) requireActivity()).l().refreshData();
            bB.a(requireActivity(), getString(R.string.design_manager_message_add_complete), bB.TOAST_NORMAL).show();
        }
    });
    private final ActivityResultLauncher<Intent> showImageDetailsDialog = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK) {
            assert result.getData() != null;
            ProjectResourceBean editedImage;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                editedImage = result.getData().getParcelableExtra("image", ProjectResourceBean.class);
            } else {
                editedImage = result.getData().getParcelableExtra("image");
            }
            kC.z();
            for (ProjectResourceBean image : lotties) {
                if (image.resName.equals(editedImage.resName)) {
                    image.copy(editedImage);
                    adapter.notifyItemChanged(lotties.indexOf(image));
                    break;
                }
            }
            updateGuideVisibility();
            ((ManageLottieActivity) requireActivity()).l().refreshData();
            bB.a(requireActivity(), getString(R.string.design_manager_message_edit_complete), bB.TOAST_NORMAL).show();
        }
    });

    public static void copyFile(String srcPath, String destPath) throws IOException {
        File srcFile = new File(srcPath);
        File destFile = new File(destPath);

        try (FileInputStream fis = new FileInputStream(srcFile);
             FileOutputStream fos = new FileOutputStream(destFile)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }
        }
    }

    public ArrayList<ProjectResourceBean> d() {
        return lotties;
    }

    private void initialize() {
        sc_id = requireActivity().getIntent().getStringExtra("sc_id");
        // Use project assets directory
        projectLottiesDirectory = fpu.getPathAssets(sc_id);
        // Ensure directory exists
        new oB().f(projectLottiesDirectory);

        // Initialize list by scanning existing .json files in assets/lottie
        File dir = new File(projectLottiesDirectory);
        File[] files = dir.listFiles((d, name) -> name != null && name.toLowerCase().endsWith(".json"));
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                String base = name.substring(0, Math.max(0, name.lastIndexOf('.')));
                ProjectResourceBean bean = new ProjectResourceBean(ProjectResourceBean.PROJECT_RES_TYPE_FILE, base, name);
                bean.savedPos = 0; // inside project
                lotties.add(bean);
            }
        }
    }

    private void unselectAll() {
        for (ProjectResourceBean projectResourceBean : lotties) {
            projectResourceBean.isSelected = false;
        }
    }

    private void deleteSelected() {
        for (int i = lotties.size() - 1; i >= 0; i--) {
            if (lotties.get(i).isSelected) {
                lotties.remove(i);
            }
        }
        adapter.notifyDataSetChanged();
    }

    public void saveImages() {
        // Ensure target directory exists
        new oB().f(projectLottiesDirectory);

        // Save Lottie JSONs into assets/lottie
        for (int i = 0; i < lotties.size(); i++) {
            ProjectResourceBean lottie = lotties.get(i);
            if (lottie.isNew || lottie.isEdited) {
                try {
                    String srcPath = lottie.savedPos == 0 ? a(lottie) : lottie.resFullName;
                    String destPath = projectLottiesDirectory + File.separator + lottie.resName + ".json";
                    copyFile(srcPath, destPath);

                    // Normalize bean to point to the project-local filename
                    lotties.set(i, new ProjectResourceBean(
                            ProjectResourceBean.PROJECT_RES_TYPE_FILE,
                            lottie.resName,
                            lottie.resName + ".json"
                    ));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        // Do NOT touch image manager state here; Lottie has its own directory
    }

    private void updateGuideVisibility() {
        if (lotties.isEmpty()) {
            binding.tvGuide.setVisibility(View.VISIBLE);
            binding.imageList.setVisibility(View.GONE);
        } else {
            binding.imageList.setVisibility(View.VISIBLE);
            binding.tvGuide.setVisibility(View.GONE);
        }
    }

    private void showAddLottieDialog() {
        Intent intent = new Intent(requireContext(), AddLottieActivity.class);
        intent.putParcelableArrayListExtra("lotties", lotties);
        intent.putExtra("sc_id", sc_id);
        intent.putExtra("dir_path", projectLottiesDirectory);
        showAddImageDialog.launch(intent);
    }

    private void openImportIconActivity() {
        Intent intent = new Intent(requireActivity(), ImportIconActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("sc_id", sc_id);
        intent.putStringArrayListExtra("imageNames", getAllImageNames());
        openImportIconActivity.launch(intent);
    }

    // Web search functionality removed for Lottie files
    // private void openImageWebSearchActivity() {
    //     Intent intent = new Intent(requireActivity(), ManageImageWebSearchActivity.class);
    //     intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
    //     openImageWebSearchActivity.launch(intent);
    // }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (savedInstanceState == null) {
            initialize();
        } else {
            sc_id = savedInstanceState.getString("sc_id");
            projectLottiesDirectory = savedInstanceState.getString("dir_path");
            lotties = savedInstanceState.getParcelableArrayList("lotties");
        }
        // Ensure directory exists
        new oB().f(projectLottiesDirectory);
        adapter.notifyDataSetChanged();
        updateGuideVisibility();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration configuration) {
        super.onConfigurationChanged(configuration);
        if (binding.imageList.getLayoutManager() instanceof GridLayoutManager manager) {
            manager.setSpanCount(ManageLottieActivity.getLottieGridColumnCount(requireContext()));
        }
        binding.imageList.requestLayout();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.manage_lottie_menu, menu);
        menu.findItem(R.id.menu_lottie_delete).setVisible(!isSelecting);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FrManageLottieListBinding.inflate(inflater, container, false);
        setHasOptionsMenu(true);
        lotties = new ArrayList<>();
        binding.imageList.setHasFixedSize(true);
        binding.imageList.setLayoutManager(new GridLayoutManager(requireActivity(), ManageLottieActivity.getLottieGridColumnCount(requireContext())));
        adapter = new Adapter(binding.imageList);
        binding.imageList.setAdapter(adapter);
        binding.tvGuide.setText(R.string.design_manager_lottie_description_guide_add_animation);
        actionButtonContainer = requireActivity().findViewById(R.id.layout_btn_group);
        MaterialButton delete = requireActivity().findViewById(R.id.btn_delete);
        MaterialButton cancel = requireActivity().findViewById(R.id.btn_cancel);
        delete.setOnClickListener(view -> {
            if (isSelecting) {
                deleteSelected();
                a(false);
                updateGuideVisibility();
                bB.a(requireActivity(), getString(R.string.common_message_complete_delete), bB.TOAST_WARNING).show();
                fab.show();
            }
        });
        cancel.setOnClickListener(view -> {
            if (isSelecting) {
                a(false);
            }
        });
        fab = requireActivity().findViewById(R.id.fab);
        fab.setVisibility(View.VISIBLE);
        fab.setOnClickListener(view -> {
            a(false);
            showAddLottieDialog();
        });
        kC.z();
        return binding.getRoot();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_lottie_delete) {
            a(!isSelecting);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString("sc_id", sc_id);
        outState.putString("dir_path", projectLottiesDirectory);
        outState.putParcelableArrayList("lotties", lotties);
        super.onSaveInstanceState(outState);
    }

    private void showLottieDetailsDialog(ProjectResourceBean projectResourceBean) {
        Intent intent = new Intent(requireContext(), AddLottieActivity.class);
        intent.putParcelableArrayListExtra("lotties", lotties);
        intent.putExtra("sc_id", sc_id);
        intent.putExtra("dir_path", projectLottiesDirectory);
        intent.putExtra("edit_target", projectResourceBean);
        showImageDetailsDialog.launch(intent);
    }

    private ArrayList<String> getAllImageNames() {
        ArrayList<String> names = new ArrayList<>();
        names.add("app_icon");
        for (ProjectResourceBean projectResourceBean : lotties) {
            names.add(projectResourceBean.resName);
        }
        return names;
    }

    public void a(ArrayList<ProjectResourceBean> arrayList) {
        ArrayList<ProjectResourceBean> imagesToAdd = new ArrayList<>();
        ArrayList<String> duplicateNames = new ArrayList<>();
        for (ProjectResourceBean next : arrayList) {
            String imageName = next.resName;
            if (isImageNameDuplicate(imageName)) {
                duplicateNames.add(imageName);
            } else {
                ProjectResourceBean image = new ProjectResourceBean(ProjectResourceBean.PROJECT_RES_TYPE_FILE, imageName, next.resFullName);
                image.savedPos = 1;
                image.isNew = true;
                image.rotate = 0;
                image.flipVertical = 1;
                image.flipHorizontal = 1;
                imagesToAdd.add(image);
            }
        }
        addImages(imagesToAdd);
        if (!duplicateNames.isEmpty()) {
            bB.a(requireActivity(), getString(R.string.common_message_name_unavailable) + "\n" +
                    "[" + String.join(", ", duplicateNames) + "]", bB.TOAST_WARNING).show();
        } else {
            bB.a(requireActivity(), getString(R.string.design_manager_message_import_complete), bB.TOAST_WARNING).show();
        }
        adapter.notifyDataSetChanged();
        updateGuideVisibility();
    }

    private boolean isImageNameDuplicate(String imageName) {
        for (ProjectResourceBean image : lotties) {
            if (image.resName.equals(imageName)) {
                return true;
            }
        }
        return false;
    }

    private String a(ProjectResourceBean projectResourceBean) {
        return projectLottiesDirectory + File.separator + projectResourceBean.resFullName;
    }

    public void a(boolean isSelecting) {
        this.isSelecting = isSelecting;
        requireActivity().invalidateOptionsMenu();
        unselectAll();
        actionButtonContainer.setVisibility(this.isSelecting ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }

    private void addImage(ProjectResourceBean projectResourceBean) {
        lotties.add(projectResourceBean);
        adapter.notifyDataSetChanged();
        adapter.notifyItemInserted(adapter.getItemCount());
        updateGuideVisibility();
    }

    private void addNewColorFilterInfo(String colorHex, int color, int forPosition) {
        Map<String, Object> colorItem1 = new HashMap<>();
        colorItem1.put("colorHex", colorHex);
        colorItem1.put("color", color);

        colorMap.put(forPosition, colorItem1);
        Log.d("color filter", "new color filter item at " + forPosition + ": " + colorHex + " " + color);
    }

    private void addImages(ArrayList<ProjectResourceBean> arrayList) {
        lotties.addAll(arrayList);
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.ViewHolder> {
        public Adapter(RecyclerView recyclerView) {
            if (recyclerView.getLayoutManager() instanceof LinearLayoutManager) {
                recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                    @Override
                    public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                        super.onScrolled(recyclerView, dx, dy);
                        if (dy > 2) {
                            if (fab.isEnabled()) {
                                fab.hide();
                            }
                        } else if (dy < -2) {
                            if (fab.isEnabled()) {
                                fab.show();
                            }
                        }
                    }
                });
            }
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ProjectResourceBean image = lotties.get(position);

            holder.binding.deleteLottieContainer.setVisibility(isSelecting ? View.VISIBLE : View.GONE);
            // Always show animation indicator for Lottie files
            holder.binding.lottieAnimationIndicator.setVisibility(View.VISIBLE);
            holder.binding.lottieDelete.setImageResource(image.isSelected ? R.drawable.ic_checkmark_green_48dp
                    : R.drawable.ic_trashcan_white_48dp);
            holder.binding.chkSelect.setChecked(image.isSelected);
            holder.binding.tvLottieName.setText(image.resName);

            if (colorMap.get(position) != null) {
                int color = Objects.requireNonNullElse((int) colorMap.get(position).get("color"), 0xFFFFFFFF);
                Log.d("Applying filter to " + position, String.valueOf(color));
                holder.binding.lottie.setColorFilter(color, PorterDuff.Mode.SRC_IN);
            } else {
                holder.binding.lottie.clearColorFilter();
            }

            if (image.resFullName.endsWith(".json")) {
                String path = image.savedPos == 0 ? a(image) : image.resFullName;
                try {
                    String json = readFileContents(path);
                    holder.binding.lottie.setAnimationFromJson(json, path);
                    holder.binding.lottie.setRepeatCount(LottieDrawable.INFINITE);
                    holder.binding.lottie.playAnimation();
                } catch (Exception e) {
                    holder.binding.lottie.cancelAnimation();
                    holder.binding.lottie.setImageResource(R.drawable.ic_remove_grey600_24dp);
                }
            }
        }

        @Override
        @NonNull
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ManageLottieListItemBinding binding = ManageLottieListItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(binding);
        }

        @Override
        public int getItemCount() {
            return lotties.size();
        }

        private class ViewHolder extends RecyclerView.ViewHolder {
            private final ManageLottieListItemBinding binding;

        public ViewHolder(@NonNull ManageLottieListItemBinding binding) {
                super(binding.getRoot());
                this.binding = binding;

                binding.lottie.setOnClickListener(v -> {
                    if (!isSelecting) {
                        if (!(lotties.get(getLayoutPosition()).resFullName.endsWith(".svg") ||
                                lotties.get(getLayoutPosition()).resFullName.endsWith(".xml"))) {
                            showLottieDetailsDialog(lotties.get(getLayoutPosition()));
                        }
                    } else {
                        binding.chkSelect.setChecked(!binding.chkSelect.isChecked());
                        lotties.get(getLayoutPosition()).isSelected = binding.chkSelect.isChecked();
                        notifyItemChanged(getLayoutPosition());
                    }
                });

                binding.lottie.setOnLongClickListener(v -> {
                    a(true);
                    binding.chkSelect.setChecked(!binding.chkSelect.isChecked());
                    lotties.get(getLayoutPosition()).isSelected = binding.chkSelect.isChecked();
                    return true;
                });
            }
        }
    }

    private String readFileContents(String path) throws Exception {
        try (FileInputStream fis = new FileInputStream(new File(path));
             java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                bos.write(buffer, 0, read);
            }
            return bos.toString("UTF-8");
        }
    }
}