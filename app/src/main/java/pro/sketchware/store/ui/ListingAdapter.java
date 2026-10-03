package pro.sketchware.store.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import pro.sketchware.R;
import pro.sketchware.databinding.ItemP2pListingBinding;
import pro.sketchware.store.core.Catalog;

/** Cards for store listings. */
public final class ListingAdapter extends RecyclerView.Adapter<ListingAdapter.Holder> {

    private final List<Catalog.Listing> listings = new ArrayList<>();
    private final Consumer<Catalog.Listing> onClick;

    public ListingAdapter(Consumer<Catalog.Listing> onClick) {
        this.onClick = onClick;
    }

    public void submit(List<Catalog.Listing> items) {
        listings.clear();
        listings.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemP2pListingBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Catalog.Listing listing = listings.get(position);
        Context context = holder.itemView.getContext();
        holder.binding.title.setText(listing.title);
        holder.binding.author.setText(context.getString(R.string.p2p_store_by,
                listing.authorName.isEmpty() ? StoreUi.shortId(listing.author) : listing.authorName));
        holder.binding.kind.setText(StoreUi.kindLabel(context, listing.kind));
        holder.binding.metrics.setText(context.getString(R.string.p2p_store_metrics_line,
                listing.metrics.likes, listing.metrics.downloads, listing.metrics.views));
        StoreUi.load(holder.binding.icon, listing.icon, R.drawable.sketch_app_icon);
        holder.itemView.setOnClickListener(v -> onClick.accept(listing));
    }

    @Override
    public int getItemCount() {
        return listings.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemP2pListingBinding binding;

        Holder(ItemP2pListingBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
