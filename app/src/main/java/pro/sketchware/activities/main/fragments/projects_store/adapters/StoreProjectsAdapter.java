package pro.sketchware.activities.main.fragments.projects_store.adapters;

import static pro.sketchware.utility.UI.loadImageFromUrl;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdView;
import com.google.gson.Gson;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import pro.sketchware.R;
import pro.sketchware.activities.main.fragments.projects_store.ProjectPreviewActivity;
import pro.sketchware.activities.main.fragments.projects_store.api.ProjectModel;
import pro.sketchware.databinding.ViewStoreProjectItemBinding;
import pro.sketchware.utility.AdManager;

public class StoreProjectsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_PROJECT = 0;
    private static final int VIEW_TYPE_NATIVE_AD = 1;
    private static final int NATIVE_AD_INTERVAL = 5;

    private final List<ProjectModel.Project> projects = new ArrayList<>();
    private final FragmentActivity context;
    private final Gson gson = new Gson();
    private WeakReference<NativeAd> loadedAd;
    private final AtomicBoolean loadingAd = new AtomicBoolean(false);

    public StoreProjectsAdapter(List<ProjectModel.Project> projects, FragmentActivity context) {
        if (projects != null) {
            this.projects.addAll(projects);
        }
        this.context = context;
        if (projectCount() >= NATIVE_AD_INTERVAL && context != null) {
            AdManager.preloadNativeAd(context);
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_NATIVE_AD) {
            View adView = inflater.inflate(R.layout.item_native_ad, parent, false);
            return new NativeAdViewHolder(adView);
        }
        ViewStoreProjectItemBinding binding = ViewStoreProjectItemBinding.inflate(inflater, parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof NativeAdViewHolder) {
            NativeAdViewHolder adHolder = (NativeAdViewHolder) holder;
            NativeAd cached = loadedAd != null ? loadedAd.get() : null;
            if (cached != null) {
                AdManager.populateNativeAdView(cached, adHolder.adView);
            } else {
                final Context binderContext = context != null ? context : adHolder.itemView.getContext();
                AdManager.NativeAdContainerBinder binder = new AdManager.NativeAdContainerBinder() {
                    @Override
                    public void bind(@NonNull NativeAd nativeAd) {
                        loadedAd = new WeakReference<>(nativeAd);
                        AdManager.populateNativeAdView(nativeAd, adHolder.adView);
                    }

                    @Override
                    public Context getContext() {
                        return binderContext;
                    }
                };
                AdManager.NativeAdLoadCallback fallback = new AdManager.NativeAdLoadCallback() {
                    @Override
                    public void onNativeAdLoaded(@NonNull NativeAd nativeAd) {
                    }

                    @Override
                    public void onNativeAdFailedToLoad(@NonNull LoadAdError error) {
                        if (binderContext != null && loadingAd.compareAndSet(false, true)) {
                            AdManager.preloadNativeAd(binderContext, AdManager.NATIVE_AD_UNIT_ID, new AdManager.NativeAdLoadCallback() {
                                @Override
                                public void onNativeAdLoaded(@NonNull NativeAd nativeAd) {
                                    loadingAd.set(false);
                                    loadedAd = new WeakReference<>(nativeAd);
                                    AdManager.populateNativeAdView(nativeAd, adHolder.adView);
                                }

                                @Override
                                public void onNativeAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                                    loadingAd.set(false);
                                }
                            });
                        }
                    }
                };
                AdManager.consumeCachedNativeAd(binder, fallback);
            }
            if (loadingAd.get() == false && (loadedAd == null || loadedAd.get() == null)) {
                final Context ctx = context != null ? context : holder.itemView.getContext();
                if (ctx != null) {
                    loadingAd.compareAndSet(false, true);
                    AdManager.preloadNativeAd(ctx, AdManager.NATIVE_AD_UNIT_ID, new AdManager.NativeAdLoadCallback() {
                        @Override
                        public void onNativeAdLoaded(@NonNull NativeAd nativeAd) {
                            loadingAd.set(false);
                            loadedAd = new WeakReference<>(nativeAd);
                            if (holder instanceof NativeAdViewHolder) {
                                AdManager.populateNativeAdView(nativeAd, ((NativeAdViewHolder) holder).adView);
                            }
                        }

                        @Override
                        public void onNativeAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                            loadingAd.set(false);
                        }
                    });
                }
            }
        } else if (holder instanceof ViewHolder) {
            ViewHolder projectHolder = (ViewHolder) holder;
            int projectPos = translateToProjectPosition(position);
            ProjectModel.Project project = projects.get(projectPos);
            projectHolder.binding.getRoot().setTag(project.getSlug());

            projectHolder.binding.title.setText(project.getTitle());
            projectHolder.binding.kind.setText(project.getTypeLabel());
            projectHolder.binding.category.setText(categoryLine(project));
            String tags = project.getTagsLabel();
            String secondary = tags.isEmpty() ? project.getShortDescription() : tags.replace(" / ", " - ");
            projectHolder.binding.tags.setText(secondary);
            projectHolder.binding.tags.setVisibility(secondary.isEmpty() ? View.GONE : View.VISIBLE);
            projectHolder.binding.rating.setText(project.getRating());
            projectHolder.binding.downloads.setText(project.getPriceLabel());
            projectHolder.binding.likes.setText(compactNumber(project.getDownloads()) + " downloads");
            projectHolder.binding.icon.setImageResource(R.drawable.default_image);
            if (!project.getIcon().isEmpty()) {
                loadImageFromUrl(projectHolder.binding.icon, project.getIcon());
            }

            projectHolder.binding.getRoot().setOnClickListener(v -> openProject(project));
        }
    }

    @Override
    public int getItemCount() {
        int numProjects = projectCount();
        return numProjects + getNativeAdCountForTotalProjects(numProjects);
    }

    @Override
    public int getItemViewType(int position) {
        return isNativeAdPosition(position) ? VIEW_TYPE_NATIVE_AD : VIEW_TYPE_PROJECT;
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
    }

    public void setProjects(List<ProjectModel.Project> projects) {
        this.projects.clear();
        if (projects != null) {
            this.projects.addAll(projects);
        }
        notifyDataSetChanged();
        if (projectCount() >= NATIVE_AD_INTERVAL && context != null) {
            AdManager.preloadNativeAd(context);
        }
    }

    public void addProjects(List<ProjectModel.Project> projects) {
        if (projects == null || projects.isEmpty()) {
            return;
        }
        int start = this.projects.size();
        this.projects.addAll(projects);
        notifyItemRangeInserted(start, projects.size());
    }

    private int projectCount() {
        return projects.size();
    }

    private int getNativeAdCountForTotalProjects(int numProjects) {
        if (numProjects < NATIVE_AD_INTERVAL) {
            return 0;
        }
        return numProjects / NATIVE_AD_INTERVAL;
    }

    private boolean isNativeAdPosition(int virtualPosition) {
        int numProjects = projectCount();
        if (numProjects < NATIVE_AD_INTERVAL) {
            return false;
        }
        int adjustedPos = virtualPosition + 1;
        return adjustedPos % (NATIVE_AD_INTERVAL + 1) == 0;
    }

    private int translateToProjectPosition(int virtualPosition) {
        int adsBefore = virtualPosition / (NATIVE_AD_INTERVAL + 1);
        return virtualPosition - adsBefore;
    }

    private void openProject(ProjectModel.Project project) {
        var bundle = new Bundle();
        bundle.putString("project_json", gson.toJson(project));

        var intent = new Intent(context, ProjectPreviewActivity.class);
        intent.putExtras(bundle);
        context.startActivity(intent);
    }

    private String categoryLine(ProjectModel.Project project) {
        String category = project.getCategory();
        String type = project.getTypeLabel();
        if (category.isEmpty()) {
            return type;
        }
        return category + " - " + type;
    }

    private String compactNumber(String value) {
        try {
            int number = Integer.parseInt(value);
            if (number >= 1_000_000) {
                return (number / 1_000_000) + "M";
            }
            if (number >= 1_000) {
                return (number / 1_000) + "K";
            }
        } catch (NumberFormatException ignored) {
        }
        return value;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final ViewStoreProjectItemBinding binding;

        public ViewHolder(ViewStoreProjectItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    public static class NativeAdViewHolder extends RecyclerView.ViewHolder {
        final NativeAdView adView;

        public NativeAdViewHolder(View itemView) {
            super(itemView);
            adView = (NativeAdView) itemView;
        }
    }
}
