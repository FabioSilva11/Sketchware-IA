package pro.sketchware.activities.main.fragments.projects_store.adapters;

import static pro.sketchware.utility.UI.loadImageFromUrl;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;
import pro.sketchware.activities.main.fragments.projects_store.ProjectPreviewActivity;
import pro.sketchware.activities.main.fragments.projects_store.api.ProjectModel;
import pro.sketchware.databinding.ViewStoreProjectRecommendationItemBinding;

public class StoreRecommendationAdapter extends RecyclerView.Adapter<StoreRecommendationAdapter.ViewHolder> {

    private final List<ProjectModel.Project> projects = new ArrayList<>();
    private final FragmentActivity context;
    private final Gson gson = new Gson();

    public StoreRecommendationAdapter(List<ProjectModel.Project> projects, FragmentActivity context) {
        if (projects != null) {
            this.projects.addAll(projects);
        }
        this.context = context;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ViewStoreProjectRecommendationItemBinding binding = ViewStoreProjectRecommendationItemBinding.inflate(inflater, parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ProjectModel.Project project = projects.get(position);
        holder.binding.title.setText(project.getTitle());
        holder.binding.rating.setText(project.getPriceLabel());
        holder.binding.icon.setImageResource(R.drawable.default_image);
        if (!project.getIcon().isEmpty()) {
            loadImageFromUrl(holder.binding.icon, project.getIcon());
        }
        holder.binding.getRoot().setOnClickListener(v -> openProject(project));
    }

    @Override
    public int getItemCount() {
        return projects.size();
    }

    public void setProjects(List<ProjectModel.Project> projects) {
        this.projects.clear();
        if (projects != null) {
            this.projects.addAll(projects);
        }
        notifyDataSetChanged();
    }

    private void openProject(ProjectModel.Project project) {
        Bundle bundle = new Bundle();
        bundle.putString("project_json", gson.toJson(project));

        Intent intent = new Intent(context, ProjectPreviewActivity.class);
        intent.putExtras(bundle);
        context.startActivity(intent);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final ViewStoreProjectRecommendationItemBinding binding;

        public ViewHolder(ViewStoreProjectRecommendationItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
