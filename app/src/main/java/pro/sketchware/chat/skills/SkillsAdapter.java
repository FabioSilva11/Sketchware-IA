package pro.sketchware.chat.skills;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.materialswitch.MaterialSwitch;

import pro.sketchware.R;

import java.util.ArrayList;
import java.util.List;

public class SkillsAdapter extends RecyclerView.Adapter<SkillsAdapter.SkillViewHolder> {

    public interface Listener {
        void onEdit(@NonNull Skill skill);

        void onDelete(@NonNull Skill skill);

        void onToggleEnabled(@NonNull Skill skill, boolean enabled);
    }

    private final List<Skill> items = new ArrayList<>();
    private final Listener listener;

    public SkillsAdapter(Listener listener) {
        this.listener = listener;
    }

    public void updateList(List<Skill> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SkillViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_skill, parent, false);
        return new SkillViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SkillViewHolder holder, int position) {
        Skill skill = items.get(position);
        Context context = holder.itemView.getContext();
        holder.name.setText(skill.name.trim().isEmpty() ? "-" : skill.name.trim());
        String description = skill.getDescription();
        holder.description.setText(description.trim().isEmpty()
                ? skill.content.trim()
                : description.trim());
        holder.description.setVisibility(
                (description.trim().isEmpty() && skill.content.trim().isEmpty()) ? View.GONE : View.VISIBLE);
        holder.meta.setText(badge(context, skill));

        holder.switchEnabled.setOnCheckedChangeListener(null);
        holder.switchEnabled.setChecked(skill.enabled);
        holder.switchEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (listener != null) listener.onToggleEnabled(skill, isChecked);
        });

        holder.edit.setOnClickListener(v -> {
            if (listener != null) listener.onEdit(skill);
        });
        holder.delete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(skill);
        });
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onEdit(skill);
        });
    }

    private static String badge(Context context, Skill skill) {
        String policy = skill.invocationPolicy == SkillInvocationPolicy.EXPLICIT_ONLY
                ? context.getString(R.string.skill_badge_explicit)
                : context.getString(R.string.skill_badge_automatic);
        String scope = skill.scope == SkillScope.PROJECT
                ? context.getString(R.string.skill_badge_project)
                : context.getString(R.string.skill_badge_user);
        return policy + " · " + scope;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class SkillViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView description;
        final TextView meta;
        final TextView edit;
        final TextView delete;
        final MaterialSwitch switchEnabled;

        SkillViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.skill_name);
            description = itemView.findViewById(R.id.skill_description);
            meta = itemView.findViewById(R.id.skill_meta);
            edit = itemView.findViewById(R.id.skill_edit);
            delete = itemView.findViewById(R.id.skill_delete);
            switchEnabled = itemView.findViewById(R.id.skill_switch);
        }
    }
}