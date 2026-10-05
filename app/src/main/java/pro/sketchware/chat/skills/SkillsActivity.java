package pro.sketchware.chat.skills;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import pro.sketchware.chat.BaseAppCompatActivity;
import pro.sketchware.R;
import pro.sketchware.chat.workspace.Workspace;
import pro.sketchware.chat.workspace.WorkspaceManager;
import pro.sketchware.chat.workspace.WorkspaceRepository;

import java.util.Collections;
import java.util.List;

/**
 * Tela onde o usuário cadastra suas próprias "skills": blocos reutilizáveis de
 * instruções/conhecimento que o runtime pode selecionar e aplicar durante a
 * conversa. A identidade das skills é o id persistente (nunca o nome); o
 * escopo define disponibilidade por projeto e a política define se a skill
 * entra automaticamente ou só por pedido explícito.
 */
public class SkillsActivity extends BaseAppCompatActivity {

    private RecyclerView recyclerSkills;
    private View emptyState;
    private SkillsAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_skills);

        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.skills_toolbar);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        recyclerSkills = findViewById(R.id.recycler_skills);
        emptyState = findViewById(R.id.skills_empty_state);
        ExtendedFloatingActionButton fabAdd = findViewById(R.id.fab_add_skill);

        adapter = new SkillsAdapter(new SkillsAdapter.Listener() {
            @Override
            public void onEdit(@androidx.annotation.NonNull Skill skill) {
                showEditDialog(skill);
            }

            @Override
            public void onDelete(@androidx.annotation.NonNull Skill skill) {
                confirmDelete(skill);
            }

            @Override
            public void onToggleEnabled(@androidx.annotation.NonNull Skill skill, boolean enabled) {
                SkillManager.setEnabled(SkillsActivity.this, skill.id, enabled);
            }
        });
        recyclerSkills.setLayoutManager(new LinearLayoutManager(this));
        recyclerSkills.setAdapter(adapter);

        fabAdd.setOnClickListener(v -> showEditDialog(null));

        refreshList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshList();
    }

    private void refreshList() {
        List<Skill> skills = SkillManager.getAll(this);
        adapter.updateList(skills);
        boolean empty = skills.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerSkills.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void showEditDialog(@Nullable Skill existing) {
        View content = getLayoutInflater().inflate(R.layout.dialog_edit_skill, null);
        EditText inputName = content.findViewById(R.id.input_skill_name);
        EditText inputDescription = content.findViewById(R.id.input_skill_description);
        EditText inputContent = content.findViewById(R.id.input_skill_content);
        RadioGroup groupInvocation = content.findViewById(R.id.group_invocation);
        RadioGroup groupScope = content.findViewById(R.id.group_scope);
        TextView scopeProjectHint = content.findViewById(R.id.scope_project_hint);

        Workspace current = currentWorkspace();
        if (current != null && current.getId() != null && !current.getId().isEmpty()) {
            scopeProjectHint.setText(getString(R.string.skill_scope_project_hint, current.getName()));
        } else {
            scopeProjectHint.setVisibility(View.GONE);
        }

        groupScope.setOnCheckedChangeListener((group, checkedId) -> {
            boolean projectScope = checkedId == R.id.radio_scope_project;
            scopeProjectHint.setVisibility(projectScope && currentWorkspace() != null ? View.VISIBLE : View.GONE);
        });

        boolean isEdit = existing != null;
        if (isEdit) {
            inputName.setText(existing.name);
            inputDescription.setText(existing.getDescription());
            inputContent.setText(existing.content);
            checkRadio(groupInvocation,
                    existing.invocationPolicy == SkillInvocationPolicy.EXPLICIT_ONLY
                            ? R.id.radio_invocation_explicit : R.id.radio_invocation_automatic);
            checkRadio(groupScope,
                    existing.scope == SkillScope.PROJECT
                            ? R.id.radio_scope_project : R.id.radio_scope_user);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(isEdit ? R.string.skill_dialog_title_edit : R.string.skill_dialog_title_new)
                .setView(content)
                .setNegativeButton(R.string.common_word_cancel, null)
                .setPositiveButton(R.string.common_word_ok, null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = inputName.getText().toString().trim();
            String description = inputDescription.getText().toString().trim();
            String skillContent = inputContent.getText().toString().trim();

            if (name.isEmpty()) {
                Toast.makeText(this, R.string.skill_error_name_required, Toast.LENGTH_SHORT).show();
                return;
            }
            if (skillContent.isEmpty()) {
                Toast.makeText(this, R.string.skill_error_content_required, Toast.LENGTH_SHORT).show();
                return;
            }

            SkillInvocationPolicy policy = groupInvocation.getCheckedRadioButtonId() == R.id.radio_invocation_explicit
                    ? SkillInvocationPolicy.EXPLICIT_ONLY
                    : SkillInvocationPolicy.AUTOMATIC;
            boolean projectScope = groupScope.getCheckedRadioButtonId() == R.id.radio_scope_project;
            String projectId = "";
            if (projectScope) {
                Workspace target = currentWorkspace();
                if (target == null || target.getId() == null || target.getId().isEmpty()) {
                    Toast.makeText(this, R.string.skill_error_project_required, Toast.LENGTH_SHORT).show();
                    return;
                }
                projectId = target.getId();
            }

            if (isEdit) {
                existing.name = name;
                existing.description = description;
                existing.shortDescription = "";
                existing.content = skillContent;
                existing.invocationPolicy = policy;
                existing.scope = projectScope ? SkillScope.PROJECT : SkillScope.USER;
                existing.projectId = projectId;
                existing.version = Skill.VERSION_V2;
                existing.updatedAt = System.currentTimeMillis();
                SkillManager.upsert(this, existing);
            } else {
                Skill created = Skill.createFull(name, description, "", skillContent,
                        policy, projectScope ? SkillScope.PROJECT : SkillScope.USER,
                        projectId, Collections.<SkillResource>emptyList());
                SkillManager.upsert(this, created);
            }
            refreshList();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void checkRadio(RadioGroup group, int id) {
        if (group.findViewById(id) != null) {
            group.check(id);
        }
    }

    /** Projeto atual: workspace ativo, senão o mais recente (fixos primeiro). */
    private Workspace currentWorkspace() {
        Workspace active = WorkspaceManager.getActiveWorkspace();
        if (active != null) {
            return active;
        }
        try {
            List<Workspace> recent = new WorkspaceRepository(this).getAll();
            if (!recent.isEmpty()) {
                return recent.get(0);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void confirmDelete(Skill skill) {
        String name = skill.name.trim().isEmpty() ? "-" : skill.name.trim();
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.skill_delete_title)
                .setMessage(getString(R.string.skill_delete_message, name))
                .setNegativeButton(R.string.common_word_cancel, null)
                .setPositiveButton(R.string.main_delete_project_confirm, (dialog, which) -> {
                    SkillManager.delete(this, skill.id);
                    refreshList();
                })
                .show();
    }
}