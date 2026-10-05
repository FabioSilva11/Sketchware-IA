package pro.sketchware.chat;


import pro.sketchware.R;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.chat.port.VoidPortSettings;

public class KelivoModelSheetAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    static final int TYPE_HEADER = 0;
    static final int TYPE_MODEL = 1;
    static final int TYPE_PROVIDER = 2;
    static final int TYPE_EMPTY = 3;

    static final class Row {
        final int type;
        final String providerId;
        final String providerLabel;
        final String providerDetails;
        final String modelId;
        final boolean selected;
        final boolean pinned;

        private Row(
                int type,
                String providerId,
                String providerLabel,
                String providerDetails,
                String modelId,
                boolean selected,
                boolean pinned) {
            this.type = type;
            this.providerId = safe(providerId);
            this.providerLabel = safe(providerLabel);
            this.providerDetails = safe(providerDetails);
            this.modelId = safe(modelId);
            this.selected = selected;
            this.pinned = pinned;
        }

        /** Cabeçalho legado de seção. */
        Row(String providerId, String providerLabel) {
            this(TYPE_HEADER, providerId, providerLabel, "", "", false, false);
        }

        Row(String providerId, String providerLabel, String modelId, boolean selected) {
            this(TYPE_MODEL, providerId, providerLabel, "", modelId, selected, false);
        }

        Row(String providerId, String providerLabel, String modelId, boolean selected, boolean pinned) {
            this(TYPE_MODEL, providerId, providerLabel, "", modelId, selected, pinned);
        }

        static Row provider(String providerId, String providerLabel, String details, boolean selected) {
            return new Row(TYPE_PROVIDER, providerId, providerLabel, details, "", selected, false);
        }

        static Row empty(String message) {
            return new Row(TYPE_EMPTY, "", message, "", "", false, false);
        }

        private static String safe(String value) {
            return value == null ? "" : value.trim();
        }
    }

    public interface Listener {
        void onProviderSelected(String providerId);

        void onModelSelected(String providerId, String modelId);

        void onFavoriteToggle(String providerId, String modelId);
    }

    private final List<Row> rows = new ArrayList<>();
    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<Row> items) {
        rows.clear();
        if (items != null) rows.addAll(items);
        notifyDataSetChanged();
    }

    public int findProviderSectionPosition(String providerId) {
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if ((row.type == TYPE_HEADER || row.type == TYPE_PROVIDER)
                    && providerId.equals(row.providerId)) return i;
        }
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (row.type == TYPE_MODEL && providerId.equals(row.providerId)) return i;
        }
        return -1;
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_PROVIDER) {
            return new ProviderHolder(inflater.inflate(R.layout.item_kelivo_provider_row, parent, false));
        }
        if (viewType == TYPE_EMPTY) {
            return new EmptyHolder(inflater.inflate(R.layout.item_kelivo_empty_state, parent, false));
        }
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(inflater.inflate(R.layout.item_kelivo_provider_header, parent, false));
        }
        return new ModelHolder(inflater.inflate(R.layout.item_kelivo_model_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (holder instanceof EmptyHolder) {
            ((EmptyHolder) holder).message.setText(row.providerLabel);
            return;
        }
        if (holder instanceof ProviderHolder) {
            ProviderHolder providerHolder = (ProviderHolder) holder;
            providerHolder.name.setText(row.providerLabel);
            providerHolder.details.setText(row.providerDetails);
            int iconRes = KelivoModelIconResolver.resolveProvider(row.providerId, row.providerLabel);
            if (iconRes != 0) {
                // Brand logos keep their colors; the layout's accent tint is for the generic icon only
                providerHolder.icon.setImageTintList(null);
                providerHolder.icon.setImageResource(iconRes);
            } else {
                providerHolder.icon.setImageTintList(android.content.res.ColorStateList.valueOf(
                        androidx.core.content.ContextCompat.getColor(providerHolder.itemView.getContext(), R.color.chat_accent)));
                providerHolder.icon.setImageResource(R.drawable.ic_kelivo_layers);
            }
            providerHolder.itemView.setBackgroundResource(row.selected
                    ? R.drawable.bg_kelivo_model_selected
                    : android.R.color.transparent);
            providerHolder.itemView.setAlpha(row.selected ? 1f : 0.96f);
            providerHolder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onProviderSelected(row.providerId);
            });
            return;
        }
        if (holder instanceof HeaderHolder) {
            HeaderHolder headerHolder = (HeaderHolder) holder;
            headerHolder.title.setText(row.providerLabel);
            int iconRes = KelivoModelIconResolver.resolveProvider(row.providerId, row.providerLabel);
            if (iconRes != 0) {
                headerHolder.icon.setVisibility(View.VISIBLE);
                headerHolder.icon.setImageResource(iconRes);
            } else {
                headerHolder.icon.setVisibility(View.GONE);
            }
            return;
        }

        ModelHolder modelHolder = (ModelHolder) holder;
        modelHolder.name.setText(row.modelId);
        int iconRes = KelivoModelIconResolver.resolve(row.providerId, row.modelId);
        if (iconRes != 0) {
            modelHolder.icon.setVisibility(View.VISIBLE);
            modelHolder.icon.setImageResource(iconRes);
            modelHolder.avatar.setVisibility(View.GONE);
        } else {
            modelHolder.icon.setVisibility(View.GONE);
            modelHolder.avatar.setVisibility(View.VISIBLE);
            modelHolder.avatar.setImageResource(R.drawable.kelivo_lucide_brain);
        }
        modelHolder.inputImageIcon.setVisibility(
                supportsImageInput(modelHolder.itemView.getContext(), row.providerId, row.modelId)
                        ? View.VISIBLE : View.GONE);
        modelHolder.itemView.setBackgroundResource(row.selected
                ? R.drawable.bg_kelivo_model_selected
                : android.R.color.transparent);
        modelHolder.favorite.setImageResource(row.pinned
                ? R.drawable.ic_kelivo_heart_filled
                : R.drawable.ic_kelivo_heart_outline);
        modelHolder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onModelSelected(row.providerId, row.modelId);
        });
        modelHolder.favorite.setOnClickListener(v -> {
            if (listener != null) listener.onFavoriteToggle(row.providerId, row.modelId);
        });
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    public static boolean supportsImageInput(android.content.Context context, String providerId, String modelId) {
        android.content.SharedPreferences prefs = VoidPortSettings.prefs(context);
        return VoidPortSettings.supportsImageInput(prefs, providerId, modelId);
    }

    static class EmptyHolder extends RecyclerView.ViewHolder {
        final TextView message;

        EmptyHolder(@NonNull View itemView) {
            super(itemView);
            message = itemView.findViewById(R.id.empty_state_message);
        }
    }

    static class ProviderHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name;
        final TextView details;

        ProviderHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.provider_row_icon);
            name = itemView.findViewById(R.id.provider_row_name);
            details = itemView.findViewById(R.id.provider_row_details);
        }
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title;

        HeaderHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.provider_header_icon);
            title = itemView.findViewById(R.id.provider_header_title);
        }
    }

    static class ModelHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final ImageView avatar;
        final TextView name;
        final ImageView inputImageIcon;
        final ImageView favorite;

        ModelHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.model_icon);
            avatar = itemView.findViewById(R.id.model_avatar);
            name = itemView.findViewById(R.id.model_name);
            inputImageIcon = itemView.findViewById(R.id.model_input_image_icon);
            favorite = itemView.findViewById(R.id.model_favorite);
        }
    }
}
