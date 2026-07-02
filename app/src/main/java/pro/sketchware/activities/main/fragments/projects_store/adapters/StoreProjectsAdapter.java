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
import java.util.List;

import pro.sketchware.R;
import pro.sketchware.activities.main.fragments.projects_store.ProjectPreviewActivity;
import pro.sketchware.activities.main.fragments.projects_store.api.ProjectModel;
import pro.sketchware.databinding.ViewStoreProjectItemBinding;

public class StoreProjectsAdapter extends RecyclerView.Adapter<StoreProjectsAdapter.ViewHolder> {

    private final List<ProjectModel.Project> projects = new ArrayList<>();
    private final FragmentActivity context;
    private final Gson gson = new Gson();

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
        holder.binding.category.setText(project.getCategory());
        String tags = project.getTagsLabel();
        holder.binding.tags.setText(tags.isEmpty() ? project.getPriceLabel() : tags);
        holder.binding.tags.setVisibility(View.VISIBLE);
        holder.binding.ratingBar.setRating(project.getRatingValue());
        holder.binding.rating.setText(project.getRating());
        holder.binding.likes.setText(project.getLikes());
        holder.binding.downloads.setText(project.getDownloads());
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

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final ViewStoreProjectItemBinding binding;

        public ViewHolder(ViewStoreProjectItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
