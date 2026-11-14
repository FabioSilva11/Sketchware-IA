package pro.sketchware.workspace;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.editor.manage.view.PresetSettingActivity;

import java.util.ArrayList;

import pro.sketchware.R;
import pro.sketchware.beans.ProjectDataManager;
import pro.sketchware.databinding.FrManageViewListBinding;
import pro.sketchware.databinding.ManageViewCustomListItemBinding;
import pro.sketchware.editor.BaseEditorFragment;

public class ProjectFilesFragment extends BaseEditorFragment {

    public String scId;
    public ArrayList<ProjectFileBean> customViews = new ArrayList<>();
    public int[] viewNameCounters = new int[19];
    Adapter adapter;
    private boolean isSelectionMode = false;

    private FrManageViewListBinding binding;

    public boolean isSelectionMode() {
        return isSelectionMode;
    }

    private String generateUniqueViewId(int beanType, String xmlName) {
        String baseName = WorkspacePathManager.b(beanType);
        StringBuilder nameBuilder = new StringBuilder();
        nameBuilder.append(baseName);
        nameBuilder.append(viewNameCounters[beanType] + 1);
        viewNameCounters[beanType]++;
        String newName = nameBuilder.toString();
        ArrayList<ViewBean> viewBeans = ProjectDataManagerRegistry.a(scId).d(xmlName);

        while (true) {
            boolean nameExists = false;
            for (ViewBean viewBean : viewBeans) {
                if (newName.equals(viewBean.id)) {
                    nameExists = true;
                    break;
                }
            }

            if (!nameExists) {
                return newName;
            }

            nameBuilder = new StringBuilder();
            nameBuilder.append(baseName);
            nameBuilder.append(viewNameCounters[beanType] + 1);
            viewNameCounters[beanType]++;
            newName = nameBuilder.toString();
        }
    }

    private ArrayList<ViewBean> loadPresetViewBeans(String presetName, int requestCode) {
        ArrayList<ViewBean> presetViews = new ArrayList<>();
        if (requestCode == 277) {
            presetViews = ProjectDataProvider.b(presetName);
        } else if (requestCode == 278) {
            presetViews = ProjectDataProvider.d(presetName);
        }

        return presetViews;
    }

    public void a(ProjectFileBean projectFileBean) {
        customViews.add(projectFileBean);
        adapter.notifyDataSetChanged();
    }

    public void a(String fileName) {
        boolean fileAlreadyExists = false;
        for (ProjectFileBean fileBean : customViews) {
            if (fileBean.fileType == 2 && fileBean.fileName.equals(fileName)) {
                fileAlreadyExists = true;
                break;
            }
        }

        if (!fileAlreadyExists) {
            customViews.add(new ProjectFileBean(2, fileName));
            adapter.notifyDataSetChanged();
        }
    }

    public void a(boolean selecting) {
        isSelectionMode = selecting;
        clearSelection();
        adapter.notifyDataSetChanged();
    }

    public void b(String fileName) {
        for (ProjectFileBean fileBean : customViews) {
            if (fileBean.fileType == 2 && fileBean.fileName.equals(fileName)) {
                customViews.remove(fileBean);
                break;
            }
        }
        adapter.notifyDataSetChanged();
    }

    public ArrayList<ProjectFileBean> c() {
        return customViews;
    }

    public void d() {
        ArrayList<ProjectFileBean> files = ProjectDataManagerRegistry.b(scId).c();
        if (files != null) {
            customViews.addAll(files);
        }
    }

    private final void clearSelection() {
        for (ProjectFileBean projectFileBean : customViews) {
            projectFileBean.isSelected = false;
        }
    }

    public void f() {
        int index = customViews.size();

        while (true) {
            index--;
            if (index < 0) {
                adapter.notifyDataSetChanged();
                return;
            }

            if (customViews.get(index).isSelected) {
                customViews.remove(index);
            }
        }
    }

    public void g() {
        if (customViews != null) {
            if (customViews.isEmpty()) {
                binding.tvGuide.setVisibility(View.VISIBLE);
                binding.listActivities.setVisibility(View.GONE);
            } else {
                binding.tvGuide.setVisibility(View.GONE);
                binding.listActivities.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState == null) {
            d();
        } else {
            scId = savedInstanceState.getString("sc_id");
            customViews = savedInstanceState.getParcelableArrayList("custom_views");
        }

        binding.listActivities.getAdapter().notifyDataSetChanged();
        g();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if ((requestCode == 277 || requestCode == 278) && resultCode == -1) {
            ProjectFileBean targetFile = customViews.get(adapter.selectedPosition);
            ArrayList<ViewBean> existingViews = ProjectDataManagerRegistry.a(scId).d(targetFile.getXmlName());

            for (int i = existingViews.size() - 1; i >= 0; --i) {
                ViewBean viewBean = (ViewBean) existingViews.get(i);
                ProjectDataManagerRegistry.a(scId).a(targetFile, viewBean);
            }

            ProjectFileBean returnedPreset = data.getParcelableExtra("preset_data");
            ArrayList<ViewBean> presetViews = loadPresetViewBeans(returnedPreset.presetName, requestCode);
            ProjectDataManagerRegistry.a(scId);

            for (Object presetView : ProjectDataManager.a(presetViews)) {
                ViewBean viewBean = (ViewBean) presetView;
                viewBean.id = generateUniqueViewId(viewBean.type, targetFile.getXmlName());
                ProjectDataManagerRegistry.a(scId).a(targetFile.getXmlName(), viewBean);
                if (viewBean.type == 3 && targetFile.fileType == 0) {
                    ProjectDataManagerRegistry.a(scId).a(targetFile.getJavaName(), 1, viewBean.type, viewBean.id, "onClick");
                }
            }

            adapter.notifyItemChanged(adapter.selectedPosition);
        }
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FrManageViewListBinding.inflate(inflater, container, false);

        binding.listActivities.setHasFixedSize(true);
        binding.listActivities.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new Adapter(binding.listActivities);
        binding.listActivities.setAdapter(adapter);

        if (savedInstanceState == null) {
            scId = getActivity().getIntent().getStringExtra("sc_id");
        } else {
            scId = savedInstanceState.getString("sc_id");
        }

        binding.tvGuide.setText(R.string.design_manager_view_description_guide_create_custom_view);

        return binding.getRoot();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString("sc_id", scId);
        outState.putParcelableArrayList("custom_views", customViews);
        super.onSaveInstanceState(outState);
    }

    public class Adapter extends RecyclerView.Adapter<Adapter.ViewHolder> {

        public int selectedPosition;

        public Adapter(RecyclerView recyclerView) {
            selectedPosition = -1;
            if (recyclerView.getLayoutManager() instanceof LinearLayoutManager) {
                recyclerView.addOnScrollListener(new ProjectFilesScrollListener(this, ProjectFilesFragment.this));
            }
        }

        @Override
        public int getItemCount() {
            return customViews != null ? customViews.size() : 0;
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ManageViewCustomListItemBinding itemBinding = holder.itemBinding;
            if (isSelectionMode) {
                itemBinding.deleteImgContainer.setVisibility(View.VISIBLE);
                itemBinding.imgActivity.setVisibility(View.GONE);
            } else {
                itemBinding.deleteImgContainer.setVisibility(View.GONE);
                itemBinding.imgActivity.setVisibility(View.VISIBLE);
            }

            ProjectFileBean fileBean = customViews.get(position);
            itemBinding.imgActivity.setImageResource(R.drawable.activity_preset_1);
            itemBinding.chkSelect.setChecked(fileBean.isSelected);
            if (fileBean.fileType == 1) {
                itemBinding.tvScreenName.setText(fileBean.getXmlName());
            } else if (fileBean.fileType == 2) {
                itemBinding.chkSelect.setVisibility(View.GONE);
                itemBinding.imgActivity.setImageResource(R.drawable.activity_0110);
                itemBinding.tvScreenName.setText(fileBean.fileName.substring(1));
            }

            if (fileBean.isSelected) {
                itemBinding.imgDelete.setImageResource(R.drawable.ic_checkmark_green_48dp);
            } else {
                itemBinding.imgDelete.setImageResource(R.drawable.ic_trashcan_white_48dp);
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(ManageViewCustomListItemBinding.inflate(getLayoutInflater(), parent, false));
        }

        public class ViewHolder extends RecyclerView.ViewHolder {

            public final ManageViewCustomListItemBinding itemBinding;

            ProjectFilesFragment fragment() {
                return ProjectFilesFragment.this;
            }

            public ViewHolder(ManageViewCustomListItemBinding binding) {
                super(binding.getRoot());
                this.itemBinding = binding;

                binding.chkSelect.setVisibility(View.GONE);
                itemView.setOnClickListener(new ProjectFileRowClickListener(this, Adapter.this));
                itemView.setOnLongClickListener(new ProjectFileRowLongClickListener(this, Adapter.this));
                binding.imgPresetSetting.setOnClickListener(new ProjectFileRowSecondaryClickListener(this, Adapter.this));
            }
        }
    }
}
