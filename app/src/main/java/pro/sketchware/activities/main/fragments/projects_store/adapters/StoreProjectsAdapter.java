package pro.sketchware.activities.main.fragments.projects_store.adapters;

import static pro.sketchware.utility.UI.loadImageFromUrl;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pro.sketchware.R;
import pro.sketchware.activities.main.fragments.projects_store.ProjectPreviewActivity;
import pro.sketchware.activities.main.fragments.projects_store.api.ProjectModel;
import pro.sketchware.activities.main.fragments.projects_store.api.SketchwareStoreApi;
import pro.sketchware.databinding.ViewStoreProjectItemBinding;

public class StoreProjectsAdapter extends RecyclerView.Adapter<StoreProjectsAdapter.ViewHolder> {

    private final List<ProjectModel.Project> projects = new ArrayList<>();
    private final Map<String, ProjectModel.Project> detailCache = new HashMap<>();
    private final FragmentActivity context;
    private final Gson gson = new Gson();
    private final SketchwareStoreApi storeApi = new SketchwareStoreApi();

    public StoreProjectsAdapter(List<ProjectModel.Project> projects, FragmentActivity context) {
        if (projects != null) {
            this.projects.addAll(projects);
        }
        this.context = context;
    }

    @NonNull
    @Override
    public StoreProjectsAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ViewStoreProjectItemBinding binding = ViewStoreProjectItemBinding.inflate(inflater, parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(StoreProjectsAdapter.ViewHolder holder, int position) {
        ProjectModel.Project project = projects.get(position);
        holder.binding.getRoot().setTag(project.getSlug());

        holder.binding.title.setText(project.getTitle());
        holder.binding.kind.setText(project.getTypeLabel());
        holder.binding.authorName.setText(project.getUserName().isEmpty() ? "Unknown developer" : project.getUserName());
        holder.binding.category.setText(project.getCategory());
        holder.binding.version.setText(project.getCurrentVersion().isEmpty() ? project.getPriceLabel() : "v" + project.getCurrentVersion());
        String tags = project.getTagsLabel();
        holder.binding.tags.setText(tags.isEmpty() ? project.getProjectSize() : tags);
        holder.binding.tags.setVisibility(View.VISIBLE);
        holder.binding.ratingBar.setRating(project.getRatingValue());
        holder.binding.rating.setText(project.getRating());
        holder.binding.likes.setText(project.getLikes());
        holder.binding.downloads.setText(project.getDownloads());
        holder.binding.comments.setText(project.getReviews());
        holder.binding.icon.setImageResource(R.drawable.default_image);
        holder.binding.authorAvatar.setImageResource(R.drawable.ic_mtrl_profile);
        if (!project.getIcon().isEmpty()) {
            loadImageFromUrl(holder.binding.icon, project.getIcon());
        }
        if (!project.getUserProfilePic().isEmpty()) {
            loadImageFromUrl(holder.binding.authorAvatar, project.getUserProfilePic());
        } else {
            loadDeveloperDetails(project.getSlug(), holder.binding);
        }

        holder.binding.getRoot().setOnClickListener(v -> openProject(project));
    }

    @Override
    public int getItemCount() {
        return projects.size();
    }

    public void setProjects(List<ProjectModel.Project> projects) {
        this.projects.clear();
        detailCache.clear();
        if (projects != null) {
            this.projects.addAll(projects);
        }
        notifyDataSetChanged();
    }

    public void addProjects(List<ProjectModel.Project> projects) {
        if (projects == null || projects.isEmpty()) {
            return;
        }
        int start = this.projects.size();
        this.projects.addAll(projects);
        notifyItemRangeInserted(start, projects.size());
    }

    private void openProject(ProjectModel.Project project) {
        var bundle = new Bundle();
        bundle.putString("project_json", gson.toJson(project));

        var intent = new Intent(context, ProjectPreviewActivity.class);
        intent.putExtras(bundle);
        context.startActivity(intent);
    }

    private void loadDeveloperDetails(String slug, ViewStoreProjectItemBinding binding) {
        if (slug.isEmpty()) {
            return;
        }

        ProjectModel.Project cached = detailCache.get(slug);
        if (cached != null) {
            bindDeveloperDetails(binding, slug, cached);
            return;
        }

        storeApi.getProjectDetails(slug, detailedProject -> {
            if (detailedProject == null) {
                return;
            }
            detailCache.put(slug, detailedProject);
            bindDeveloperDetails(binding, slug, detailedProject);
        });
    }

    private void bindDeveloperDetails(ViewStoreProjectItemBinding binding, String slug, ProjectModel.Project detailedProject) {
        if (!slug.equals(binding.getRoot().getTag())) {
            return;
        }
        if (!detailedProject.getUserName().isEmpty()) {
            binding.authorName.setText(detailedProject.getUserName());
        }
        if (!detailedProject.getUserProfilePic().isEmpty()) {
            loadImageFromUrl(binding.authorAvatar, detailedProject.getUserProfilePic());
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final ViewStoreProjectItemBinding binding;

        public ViewHolder(ViewStoreProjectItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
