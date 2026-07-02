package pro.sketchware.activities.main.fragments.projects_store.adapters;

import static pro.sketchware.utility.UI.loadImageFromUrl;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.activities.main.fragments.projects_store.api.ProjectModel;
import pro.sketchware.databinding.ViewStoreProjectPreviewCommentBinding;

public class CommentsAdapter extends RecyclerView.Adapter<CommentsAdapter.ViewHolder> {
    private final List<ProjectModel.Comment> comments = new ArrayList<>();

    @NonNull
    @Override
    public CommentsAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ViewStoreProjectPreviewCommentBinding binding = ViewStoreProjectPreviewCommentBinding.inflate(inflater, parent, false);
        return new CommentsAdapter.ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull CommentsAdapter.ViewHolder holder, int position) {
        ProjectModel.Comment comment = comments.get(position);
        holder.binding.userName.setText(comment.getUserName());
        holder.binding.userComment.setText(comment.getBody());
        if (!comment.getUserAvatar().isEmpty()) {
            loadImageFromUrl(holder.binding.userAvatar, comment.getUserAvatar());
        }
    }

    @Override
    public int getItemCount() {
        return comments.size();
    }

    public void setComments(List<ProjectModel.Comment> comments) {
        this.comments.clear();
        if (comments != null) {
            this.comments.addAll(comments);
        }
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final ViewStoreProjectPreviewCommentBinding binding;

        public ViewHolder(ViewStoreProjectPreviewCommentBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

}
