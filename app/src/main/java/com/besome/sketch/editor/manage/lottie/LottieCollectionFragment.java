package com.besome.sketch.editor.manage.lottie;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.beans.ProjectResourceBean;
import com.besome.sketch.editor.manage.lottie.ManageLottieActivity;
import com.besome.sketch.editor.manage.lottie.ManageLottieImportActivity;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.BitmapImageViewTarget;
import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;

import pro.sketchware.R;
import pro.sketchware.databinding.FrManageLottieListBinding;
import pro.sketchware.databinding.ManageLottieListItemBinding;

import a.a.a.mB;
import a.a.a.Op;
import a.a.a.wq;
import a.a.a.qA;

public class LottieCollectionFragment extends qA implements View.OnClickListener {

    private final ActivityResultLauncher<Intent> openImageImportDetails = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        var data = result.getData();
        if (result.getResultCode() == Activity.RESULT_OK && data != null) {
            ArrayList<ProjectResourceBean> importedImages;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                importedImages = data.getParcelableArrayListExtra("results", ProjectResourceBean.class);
            } else {
                importedImages = data.getParcelableArrayListExtra("results");
            }
            ArrayList<ProjectResourceBean> newImportedImages = new ArrayList<>();
            for (ProjectResourceBean image : importedImages) {
                newImportedImages.add(new ProjectResourceBean(ProjectResourceBean.PROJECT_RES_TYPE_FILE, image.resName, image.resFullName));
            }
            if (!newImportedImages.isEmpty()) {
                ((ManageLottieActivity) requireActivity()).m().a(newImportedImages);
                ((ManageLottieActivity) requireActivity()).f(0);
            }
        }
    });

    private FrManageLottieListBinding binding;
    private ArrayList<ProjectResourceBean> collectionLotties;
    private Adapter adapter;
    private String sc_id;

    private Button btnImport;
    private MaterialCardView layoutBtnImport;

    private boolean isValidLottieJson(String json) {
        if (json == null || json.isEmpty()) {
            return false;
        }
        try {
            // Verifica se o JSON contém elementos básicos de uma animação Lottie
            return json.contains("\"v\"") && 
                   (json.contains("\"assets\"") || json.contains("\"layers\""));
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public void refreshData() {
        collectionLotties = new ArrayList<>();

        // Lista diretamente os arquivos .json dentro de .sketchware/collection/assets
        File assetsDir = new File(wq.a() + File.separator + "assets");
        if (!assetsDir.exists()) {
            // Caso a pasta não exista ainda, evita NullPointer e apenas atualiza a UI
            adapter.notifyDataSetChanged();
            updateGuideVisibility();
            return;
        }

        File[] files = assetsDir.listFiles((dir, name) -> name != null && name.toLowerCase().endsWith(".json"));
        if (files != null) {
            for (File file : files) {
                String jsonContent = readFileContents(file.getAbsolutePath());
                if (isValidLottieJson(jsonContent)) {
                    String resFullName = file.getName();
                    String resName = resFullName.substring(0, Math.max(0, resFullName.lastIndexOf('.')));
                    collectionLotties.add(new ProjectResourceBean(ProjectResourceBean.PROJECT_RES_TYPE_FILE, resName, resFullName));
                }
            }
        }

        adapter.notifyDataSetChanged();
        updateGuideVisibility();
    }

    private String readFileContents(String filePath) {
        try (FileInputStream fis = new FileInputStream(filePath)) {
            byte[] buffer = new byte[8192];
            int read;
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            while ((read = fis.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            return baos.toString("UTF-8"); // Especifica UTF-8 para correta leitura de caracteres
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void unselectAll() {
        for (ProjectResourceBean lottie : collectionLotties) {
            lottie.isSelected = false;
        }
        adapter.notifyDataSetChanged();
    }

    public boolean isSelecting() {
        for (ProjectResourceBean lottie : collectionLotties) {
            if (lottie.isSelected) return true;
        }
        return false;
    }

    public void updateGuideVisibility() {
        boolean isEmpty = collectionLotties.isEmpty();
        binding.tvGuide.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        binding.imageList.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    public void importLotties() {
        ArrayList<ProjectResourceBean> selectedCollections = new ArrayList<>();
        for (ProjectResourceBean lottie : collectionLotties) {
            if (lottie.isSelected) {
                selectedCollections.add(new ProjectResourceBean(ProjectResourceBean.PROJECT_RES_TYPE_FILE, lottie.resName, wq.a() + File.separator + "assets" + File.separator + lottie.resFullName));
            }
        }
        if (!selectedCollections.isEmpty()) {
            Intent intent = new Intent(requireActivity(), ManageLottieImportActivity.class);
            intent.putParcelableArrayListExtra("project_lotties", ((ManageLottieActivity) requireActivity()).m().d());
            intent.putParcelableArrayListExtra("selected_collections", selectedCollections);
            openImageImportDetails.launch(intent);
        }
        unselectAll();
        adapter.notifyDataSetChanged();
    }

    private void onItemSelected() {
        int count = 0;
        for (ProjectResourceBean lottie : collectionLotties) {
            if (lottie.isSelected) {
                count += 1;
            }
        }
        if (count > 0) {
            btnImport.setText(getString(R.string.common_word_import_count, count).toUpperCase());
            layoutBtnImport.setVisibility(View.VISIBLE);
        } else {
            layoutBtnImport.setVisibility(View.GONE);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        sc_id = savedInstanceState != null ? savedInstanceState.getString("sc_id")
                : requireActivity().getIntent().getStringExtra("sc_id");
        refreshData();
    }

    @Override
    public void onClick(View v) {
        if (!mB.a() && v.getId() == R.id.btn_import) {
            layoutBtnImport.setVisibility(View.GONE);
            importLotties();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (binding.imageList.getLayoutManager() instanceof GridLayoutManager manager) {
            manager.setSpanCount(ManageLottieActivity.getLottieGridColumnCount(requireContext()));
        }
        binding.imageList.requestLayout();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FrManageLottieListBinding.inflate(inflater, container, false);
        binding.imageList.setHasFixedSize(true);
        binding.imageList.setLayoutManager(new GridLayoutManager(requireActivity(), ManageLottieActivity.getLottieGridColumnCount(requireContext())));
        adapter = new Adapter();
        binding.imageList.setAdapter(adapter);
        binding.tvGuide.setText(R.string.design_manager_lottie_description_guide_add_animation);
        btnImport = requireActivity().findViewById(R.id.btn_import);
        layoutBtnImport = requireActivity().findViewById(R.id.layout_btn_import);
        btnImport.setOnClickListener(this);
        layoutBtnImport.setVisibility(View.GONE);
        return binding.getRoot();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString("sc_id", sc_id);
        super.onSaveInstanceState(outState);
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.ViewHolder> {

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ProjectResourceBean lottie = collectionLotties.get(position);
            holder.binding.chkSelect.setVisibility(View.VISIBLE);
            holder.binding.lottieAnimationIndicator.setVisibility(View.VISIBLE);
            
            String jsonPath = wq.a() + File.separator + "assets" + File.separator + lottie.resFullName;
            String json = readFileContents(jsonPath);
            
            try {
                if (json != null && isValidLottieJson(json)) {
                    holder.binding.lottie.setAnimationFromJson(json, lottie.resFullName);
                    holder.binding.lottie.setRepeatCount(3); // Limita repetições da animação
                    holder.binding.lottie.playAnimation();
                    holder.binding.lottieAnimationIndicator.setVisibility(View.GONE);
                } else {
                    holder.binding.lottie.cancelAnimation();
                    holder.binding.lottie.setImageResource(R.drawable.ic_mtrl_animation);
                    holder.binding.lottieAnimationIndicator.setVisibility(View.VISIBLE);
                }
            } catch (Exception e) {
                e.printStackTrace();
                holder.binding.lottie.cancelAnimation();
                holder.binding.lottie.setImageResource(R.drawable.ic_mtrl_animation);
                holder.binding.lottieAnimationIndicator.setVisibility(View.VISIBLE);
            }
            
            holder.binding.tvLottieName.setText(lottie.resName);
            holder.binding.chkSelect.setChecked(lottie.isSelected);
        }

        @Override
        @NonNull
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(ManageLottieListItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public int getItemCount() {
            return collectionLotties.size();
        }

        private class ViewHolder extends RecyclerView.ViewHolder {
            public final ManageLottieListItemBinding binding;

        public ViewHolder(@NonNull ManageLottieListItemBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
                binding.chkSelect.setVisibility(View.VISIBLE);
                binding.lottie.setOnClickListener(v -> {
                    binding.chkSelect.setChecked(!binding.chkSelect.isChecked());
                    collectionLotties.get(getLayoutPosition()).isSelected = binding.chkSelect.isChecked();
                    onItemSelected();
                    notifyItemChanged(getLayoutPosition());
                });
            }
        }
    }
}
