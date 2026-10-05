package pro.sketchware.chat.provider;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import pro.sketchware.chat.KelivoModelIconResolver;
import pro.sketchware.R;
import pro.sketchware.chat.port.VoidPortRefreshModelService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Searchable model picker aligned with Kelivo's provider model sheet. */
final class ProviderModelFetchSheet {
    interface Listener {
        void onModelsChanged(List<String> selectedModels);
    }

    private ProviderModelFetchSheet() {
    }

    static void show(Context context, String providerId, String providerName,
                     List<String> fetchedModels, List<String> selectedModels,
                     Listener listener) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(roundedTopBackground(
                ContextCompat.getColor(context, R.color.chat_surface), dp(context, 24)));

        View handle = new View(context);
        handle.setBackgroundResource(R.drawable.bg_bottom_sheet_handle);
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(
                dp(context, 40), dp(context, 4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.setMargins(0, dp(context, 8), 0, dp(context, 12));
        root.addView(handle, handleParams);

        LinearLayout searchBar = new LinearLayout(context);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setPadding(dp(context, 14), 0, dp(context, 8), 0);
        searchBar.setBackground(roundedBackground(Color.rgb(246, 244, 248), dp(context, 16)));

        ImageView searchIcon = new ImageView(context);
        searchIcon.setImageResource(R.drawable.ic_mtrl_search);
        searchIcon.setColorFilter(ContextCompat.getColor(context, R.color.chat_text_secondary));
        searchBar.addView(searchIcon, new LinearLayout.LayoutParams(dp(context, 28), dp(context, 28)));

        EditText search = new EditText(context);
        search.setSingleLine(true);
        search.setHint(R.string.ia_filter_models_hint);
        search.setTextSize(14);
        search.setTextColor(ContextCompat.getColor(context, R.color.chat_text_primary));
        search.setHintTextColor(ContextCompat.getColor(context, R.color.chat_text_secondary));
        search.setBackgroundColor(Color.TRANSPARENT);
        search.setPadding(dp(context, 14), 0, dp(context, 6), 0);
        searchBar.addView(search, new LinearLayout.LayoutParams(0, dp(context, 62), 1f));

        ImageButton selectAll = iconButton(context, R.drawable.ic_mtrl_checkbox);
        selectAll.setContentDescription(context.getString(R.string.ia_action_multi_select));
        searchBar.addView(selectAll, new LinearLayout.LayoutParams(dp(context, 48), dp(context, 48)));

        ImageButton refresh = iconButton(context, R.drawable.ic_mtrl_sync);
        refresh.setContentDescription(context.getString(R.string.ia_fetch_models));
        searchBar.addView(refresh, new LinearLayout.LayoutParams(dp(context, 48), dp(context, 48)));

        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 64));
        searchParams.setMargins(dp(context, 16), 0, dp(context, 16), dp(context, 12));
        root.addView(searchBar, searchParams);

        RecyclerView recycler = new RecyclerView(context);
        recycler.setLayoutManager(new LinearLayoutManager(context));
        recycler.setClipToPadding(false);
        recycler.setPadding(0, 0, 0, dp(context, 24));
        recycler.setOverScrollMode(View.OVER_SCROLL_NEVER);

        Set<String> selected = new LinkedHashSet<>(selectedModels);
        FetchAdapter[] adapterRef = new FetchAdapter[1];
        FetchAdapter adapter = new FetchAdapter(context, providerName, fetchedModels,
                selected, listener, () -> updateSelectAllIcon(selectAll, adapterRef[0]));
        adapterRef[0] = adapter;
        recycler.setAdapter(adapter);
        root.addView(recycler, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s == null ? "" : s.toString());
                updateSelectAllIcon(selectAll, adapter);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        selectAll.setOnClickListener(v -> {
            adapter.toggleAllVisible();
            updateSelectAllIcon(selectAll, adapter);
        });
        refresh.setOnClickListener(v -> {
            refresh.setEnabled(false);
            refresh.setAlpha(0.45f);
            VoidPortRefreshModelService.refreshProviderAsync(context, providerId, false, result -> {
                refresh.setEnabled(true);
                refresh.setAlpha(1f);
                if (result.state == VoidPortRefreshModelService.RefreshState.FINISHED) {
                    adapter.replaceModels(result.models);
                    updateSelectAllIcon(selectAll, adapter);
                } else {
                    Toast.makeText(context,
                            context.getString(R.string.ia_fetch_models_failed, result.error),
                            Toast.LENGTH_LONG).show();
                }
            });
        });

        dialog.setContentView(root);
        dialog.setOnShowListener(ignored -> {
            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundColor(Color.TRANSPARENT);
                ViewGroup.LayoutParams params = bottomSheet.getLayoutParams();
                params.height = (int) (context.getResources().getDisplayMetrics().heightPixels * 0.80f);
                bottomSheet.setLayoutParams(params);
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        dialog.show();
        updateSelectAllIcon(selectAll, adapter);
    }

    private static void updateSelectAllIcon(ImageButton button, FetchAdapter adapter) {
        if (button == null || adapter == null) return;
        button.setImageResource(adapter.areAllVisibleSelected()
                ? R.drawable.ic_mtrl_check : R.drawable.ic_mtrl_checkbox);
    }

    private static ImageButton iconButton(Context context, int icon) {
        ImageButton button = new ImageButton(context);
        button.setImageResource(icon);
        button.setColorFilter(ContextCompat.getColor(context, R.color.chat_text_primary));
        button.setBackgroundResource(android.R.drawable.list_selector_background);
        button.setPadding(dp(context, 11), dp(context, 11), dp(context, 11), dp(context, 11));
        return button;
    }

    private static final class FetchAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_GROUP = 0;
        private static final int TYPE_MODEL = 1;

        private final Context context;
        private final String providerName;
        private final Set<String> selected;
        private final Listener listener;
        private final Runnable selectionChanged;
        private final Set<String> collapsedGroups = new LinkedHashSet<>();
        private final List<String> all = new ArrayList<>();
        private final List<String> visibleModels = new ArrayList<>();
        private final List<Row> rows = new ArrayList<>();
        private String currentQuery = "";

        FetchAdapter(Context context, String providerName, List<String> models,
                     Set<String> selected, Listener listener, Runnable selectionChanged) {
            this.context = context;
            this.providerName = providerName;
            this.selected = selected;
            this.listener = listener;
            this.selectionChanged = selectionChanged;
            replaceModels(models);
        }

        void replaceModels(List<String> models) {
            all.clear();
            all.addAll(new LinkedHashSet<>(models));
            filter(currentQuery);
        }

        void filter(String query) {
            currentQuery = query == null ? "" : query;
            String normalized = currentQuery.trim().toLowerCase(Locale.US);
            Map<String, List<String>> grouped = new LinkedHashMap<>();
            grouped.put(context.getString(R.string.ia_model_group_embeddings), new ArrayList<>());
            grouped.put("Gemini", new ArrayList<>());
            visibleModels.clear();
            for (String model : all) {
                if (!normalized.isEmpty()
                        && !model.toLowerCase(Locale.US).contains(normalized)
                        && !displayName(model).toLowerCase(Locale.US).contains(normalized)) {
                    continue;
                }
                visibleModels.add(model);
                String group = groupFor(model);
                if (!grouped.containsKey(group)) grouped.put(group, new ArrayList<>());
                grouped.get(group).add(model);
            }
            rows.clear();
            for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
                if (entry.getValue().isEmpty()) continue;
                rows.add(Row.group(entry.getKey(), entry.getValue()));
                if (!collapsedGroups.contains(entry.getKey())) {
                    for (String model : entry.getValue()) rows.add(Row.model(entry.getKey(), model));
                }
            }
            notifyDataSetChanged();
        }

        private String groupFor(String model) {
            String id = model.toLowerCase(Locale.US);
            if (id.contains("embed")) return context.getString(R.string.ia_model_group_embeddings);
            if (id.contains("gemini") || "Gemini".equalsIgnoreCase(providerName)) return "Gemini";
            if (id.contains("claude")) return "Claude";
            return providerName == null || providerName.trim().isEmpty()
                    ? context.getString(R.string.ia_model_group_chat) : providerName;
        }

        boolean areAllVisibleSelected() {
            return !visibleModels.isEmpty() && selected.containsAll(visibleModels);
        }

        void toggleAllVisible() {
            if (areAllVisibleSelected()) selected.removeAll(visibleModels);
            else selected.addAll(visibleModels);
            notifyDataSetChanged();
            dispatch();
        }

        @Override public int getItemViewType(int position) {
            return rows.get(position).groupHeader ? TYPE_GROUP : TYPE_MODEL;
        }

        @NonNull
        @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_GROUP) return new GroupHolder(createGroupView());
            return new ModelHolder(createModelView());
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Row row = rows.get(position);
            if (holder instanceof GroupHolder) {
                GroupHolder groupHolder = (GroupHolder) holder;
                groupHolder.title.setText(row.group);
                groupHolder.chevron.setRotation(collapsedGroups.contains(row.group) ? -90f : 0f);
                groupHolder.add.setImageResource(selected.containsAll(row.groupModels)
                        ? R.drawable.ic_mtrl_check : R.drawable.ic_mtrl_add);
                groupHolder.itemView.setOnClickListener(v -> {
                    if (!collapsedGroups.add(row.group)) collapsedGroups.remove(row.group);
                    filter(currentQuery);
                });
                groupHolder.add.setOnClickListener(v -> {
                    if (selected.containsAll(row.groupModels)) selected.removeAll(row.groupModels);
                    else selected.addAll(row.groupModels);
                    notifyDataSetChanged();
                    dispatch();
                });
            } else if (holder instanceof ModelHolder) {
                ModelHolder modelHolder = (ModelHolder) holder;
                modelHolder.title.setText(displayName(row.model));
                boolean embedding = row.model.toLowerCase(Locale.US).contains("embed");
                modelHolder.type.setText(embedding ? "Embedding" : "Chat");
                modelHolder.modality.setText(modalityLabel(row.model, embedding));
                modelHolder.ability.setVisibility(embedding ? View.GONE : View.VISIBLE);
                modelHolder.ability.setText("⚒");
                int icon = KelivoModelIconResolver.resolveProvider("gemini", providerName);
                if (icon != 0) modelHolder.icon.setImageResource(icon);
                boolean checked = selected.contains(row.model);
                modelHolder.add.setImageResource(checked
                        ? R.drawable.ic_mtrl_check : R.drawable.ic_mtrl_add);
                View.OnClickListener toggle = v -> {
                    if (!selected.add(row.model)) selected.remove(row.model);
                    notifyDataSetChanged();
                    dispatch();
                };
                modelHolder.itemView.setOnClickListener(toggle);
                modelHolder.add.setOnClickListener(toggle);
            }
        }

        private String modalityLabel(String model, boolean embedding) {
            if (embedding) return "T  ›  T";
            String id = model.toLowerCase(Locale.US);
            if (id.contains("tts") || id.contains("audio")) return "T  ›  Áudio";
            if (id.contains("image") || id.contains("banana")) return "T  ▧  ›  ▧";
            return "T  ▧  ›  T";
        }

        private void dispatch() {
            if (listener != null) listener.onModelsChanged(new ArrayList<>(selected));
            if (selectionChanged != null) selectionChanged.run();
        }

        @Override public int getItemCount() {
            return rows.size();
        }

        private View createGroupView() {
            LinearLayout row = new LinearLayout(context);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(context, 14), 0, dp(context, 8), 0);
            row.setBackground(roundedBackground(Color.rgb(246, 244, 248), dp(context, 16)));
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 68));
            params.setMargins(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 6));
            row.setLayoutParams(params);

            ImageView chevron = new ImageView(context);
            chevron.setId(android.R.id.icon1);
            chevron.setImageResource(R.drawable.ic_expand_more_grey600_24dp);
            row.addView(chevron, new LinearLayout.LayoutParams(dp(context, 28), dp(context, 28)));

            TextView title = new TextView(context);
            title.setId(android.R.id.text1);
            title.setTextColor(ContextCompat.getColor(context, R.color.chat_text_primary));
            title.setTextSize(16);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            title.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            titleParams.setMargins(dp(context, 12), 0, 0, 0);
            row.addView(title, titleParams);

            ImageButton add = iconButton(context, R.drawable.ic_mtrl_add);
            add.setId(android.R.id.button1);
            row.addView(add, new LinearLayout.LayoutParams(dp(context, 48), dp(context, 48)));
            return row;
        }

        private View createModelView() {
            LinearLayout row = new LinearLayout(context);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(context, 28), dp(context, 10), dp(context, 16), dp(context, 10));
            row.setMinimumHeight(dp(context, 90));
            row.setBackgroundResource(android.R.drawable.list_selector_background);
            row.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            FrameLayout avatar = new FrameLayout(context);
            avatar.setBackground(roundedBackground(Color.rgb(245, 243, 250), dp(context, 24)));
            ImageView icon = new ImageView(context);
            icon.setId(android.R.id.icon);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            avatar.addView(icon, new FrameLayout.LayoutParams(dp(context, 28), dp(context, 28), Gravity.CENTER));
            row.addView(avatar, new LinearLayout.LayoutParams(dp(context, 48), dp(context, 48)));

            LinearLayout labels = new LinearLayout(context);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            labelsParams.setMargins(dp(context, 14), 0, dp(context, 6), 0);

            TextView title = new TextView(context);
            title.setId(android.R.id.text1);
            title.setTextColor(ContextCompat.getColor(context, R.color.chat_text_primary));
            title.setTextSize(16);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            title.setMaxLines(2);
            labels.addView(title);

            LinearLayout chips = new LinearLayout(context);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            chips.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams chipsParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 28));
            chipsParams.setMargins(0, dp(context, 5), 0, 0);
            labels.addView(chips, chipsParams);

            TextView type = chip(context, android.R.id.text2,
                    Color.rgb(232, 238, 252), Color.rgb(73, 96, 145));
            chips.addView(type);
            TextView modality = chip(context, android.R.id.hint,
                    Color.rgb(241, 234, 243), Color.rgb(120, 91, 123));
            LinearLayout.LayoutParams modalityParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 26));
            modalityParams.setMargins(dp(context, 6), 0, 0, 0);
            chips.addView(modality, modalityParams);
            TextView ability = chip(context, android.R.id.summary,
                    Color.rgb(230, 235, 247), Color.rgb(80, 101, 150));
            LinearLayout.LayoutParams abilityParams = new LinearLayout.LayoutParams(
                    dp(context, 38), dp(context, 26));
            abilityParams.setMargins(dp(context, 6), 0, 0, 0);
            chips.addView(ability, abilityParams);

            row.addView(labels, labelsParams);
            ImageButton add = iconButton(context, R.drawable.ic_mtrl_add);
            add.setId(android.R.id.button1);
            row.addView(add, new LinearLayout.LayoutParams(dp(context, 48), dp(context, 48)));
            return row;
        }

        private TextView chip(Context context, int id, int backgroundColor, int textColor) {
            TextView chip = new TextView(context);
            chip.setId(id);
            chip.setGravity(Gravity.CENTER);
            chip.setTextSize(12);
            chip.setTextColor(textColor);
            chip.setPadding(dp(context, 10), 0, dp(context, 10), 0);
            chip.setBackground(roundedBackground(backgroundColor, dp(context, 13)));
            chip.setMinHeight(dp(context, 26));
            return chip;
        }

        private static String displayName(String id) {
            String[] words = id.replace('_', ' ').replace('-', ' ').split("\\s+");
            StringBuilder out = new StringBuilder();
            for (String word : words) {
                if (word.isEmpty()) continue;
                if (out.length() > 0) out.append(' ');
                out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
            return out.length() == 0 ? id : out.toString();
        }
    }

    private static final class GroupHolder extends RecyclerView.ViewHolder {
        final ImageView chevron;
        final TextView title;
        final ImageButton add;

        GroupHolder(@NonNull View itemView) {
            super(itemView);
            chevron = itemView.findViewById(android.R.id.icon1);
            title = itemView.findViewById(android.R.id.text1);
            add = itemView.findViewById(android.R.id.button1);
        }
    }

    private static final class ModelHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title;
        final TextView type;
        final TextView modality;
        final TextView ability;
        final ImageButton add;

        ModelHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(android.R.id.icon);
            title = itemView.findViewById(android.R.id.text1);
            type = itemView.findViewById(android.R.id.text2);
            modality = itemView.findViewById(android.R.id.hint);
            ability = itemView.findViewById(android.R.id.summary);
            add = itemView.findViewById(android.R.id.button1);
        }
    }

    private static final class Row {
        final boolean groupHeader;
        final String group;
        final String model;
        final List<String> groupModels;

        private Row(boolean groupHeader, String group, String model, List<String> groupModels) {
            this.groupHeader = groupHeader;
            this.group = group;
            this.model = model;
            this.groupModels = groupModels;
        }

        static Row group(String group, List<String> models) {
            return new Row(true, group, "", new ArrayList<>(models));
        }

        static Row model(String group, String model) {
            return new Row(false, group, model, new ArrayList<>());
        }
    }

    private static GradientDrawable roundedBackground(int color, int radiusPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radiusPx);
        return drawable;
    }

    private static GradientDrawable roundedTopBackground(int color, int radiusPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadii(new float[]{radiusPx, radiusPx, radiusPx, radiusPx, 0, 0, 0, 0});
        return drawable;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
