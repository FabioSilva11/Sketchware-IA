package pro.sketchware.activities.main.fragments.projects_store.adapters;

import static pro.sketchware.utility.UI.loadImageFromUrl;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;
import pro.sketchware.activities.main.fragments.projects_store.api.ProjectModel;
import pro.sketchware.databinding.ViewStoreProjectReviewItemBinding;

public class ReviewsAdapter extends RecyclerView.Adapter<ReviewsAdapter.ViewHolder> {
    private final List<ProjectModel.Review> reviews = new ArrayList<>();

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ViewStoreProjectReviewItemBinding binding = ViewStoreProjectReviewItemBinding.inflate(inflater, parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ProjectModel.Review review = reviews.get(position);
        holder.binding.userName.setText(review.getUserName());
        holder.binding.reviewDate.setText(review.getCreatedAt());
        holder.binding.reviewBody.setText(review.getBody());
        holder.binding.reviewRatingBar.setRating(review.getRatingValue());
        if (!review.getUserAvatar().isEmpty()) {
            loadImageFromUrl(holder.binding.userAvatar, review.getUserAvatar());
        } else {
            holder.binding.userAvatar.setImageResource(R.drawable.ic_mtrl_profile);
        }
    }

    @Override
    public int getItemCount() {
        return reviews.size();
    }

    public void setReviews(List<ProjectModel.Review> reviews) {
        this.reviews.clear();
        if (reviews != null) {
            this.reviews.addAll(reviews);
        }
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ViewStoreProjectReviewItemBinding binding;

        ViewHolder(ViewStoreProjectReviewItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
