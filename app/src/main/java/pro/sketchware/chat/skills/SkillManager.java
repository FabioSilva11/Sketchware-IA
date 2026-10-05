package pro.sketchware.chat.skills;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import pro.sketchware.chat.ChatFlowLogger;
import pro.sketchware.chat.workspace.Workspace;
import pro.sketchware.chat.workspace.WorkspaceManager;
import pro.sketchware.chat.workspace.WorkspaceRepository;

/**
 * Camada de persistência e montagem do fluxo oficial de Skills.
 *
 * <p>Evolução (código-x): o antigo {@code buildPromptBlock} (lista fixa de
 * até 20 skills / 4000 chars injetada em todo prompt) foi removido — ele não
 * tem mais o papel de único ponto de injeção. O runtime agora resolve as
 * Skills via {@link SkillPipeline} (catálogo → seleção → loader → renderer).
 * Este gerenciador continua dono do armazenamento SharedPreferences
 * ({@code axion_skills}/{@code skills_json}) e das operações da UI
 * (criar/editar/excluir/habilitar), com identidade canônica por id — nunca
 * por nome.</p>
 */
public final class SkillManager {

    private static final String PREFS_NAME = "axion_skills";
    private static final String KEY_SKILLS = "skills_json";
    private static final Object LOCK = new Object();

    private SkillManager() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Store SharedPreferences tolerante a JSON corrompido. */
    public static SkillStore newStore(final Context context) {
        return new SkillStore() {
            @Override
            public List<Skill> loadAll() {
                synchronized (LOCK) {
                    List<Skill> result = new ArrayList<Skill>();
                    String raw = prefs(context).getString(KEY_SKILLS, "");
                    if (TextUtils.isEmpty(raw)) {
                        return result;
                    }
                    try {
                        JSONArray array = new JSONArray(raw);
                        for (int i = 0; i < array.length(); i++) {
                            Skill skill = Skill.fromJson(array.optJSONObject(i));
                            if (skill != null) {
                                result.add(skill);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    return result;
                }
            }

            @Override
            public void saveAll(List<Skill> skills) {
                synchronized (LOCK) {
                    JSONArray array = new JSONArray();
                    List<Skill> values = skills == null ? Collections.emptyList() : skills;
                    for (Skill skill : values) {
                        if (skill != null) {
                            array.put(skill.toJson());
                        }
                    }
                    prefs(context).edit().putString(KEY_SKILLS, array.toString()).apply();
                }
            }
        };
    }

    /** Todas as Skills para a UI, ordem de atualização decrescente. */
    public static List<Skill> getAll(Context context) {
        List<Skill> result = newStore(context).loadAll();
        Collections.sort(result, new Comparator<Skill>() {
            @Override
            public int compare(Skill a, Skill b) {
                return Long.compare(b.updatedAt, a.updatedAt);
            }
        });
        return result;
    }

    public static void upsert(Context context, Skill skill) {
        if (skill != null) {
            newStore(context).upsert(skill);
        }
    }

    public static void delete(Context context, String skillId) {
        newStore(context).deleteById(skillId);
    }

    public static void setEnabled(Context context, String skillId, boolean enabled) {
        List<Skill> all = getAll(context);
        for (Skill skill : all) {
            if (skill.id.equals(skillId)) {
                skill.enabled = enabled;
                skill.updatedAt = System.currentTimeMillis();
                newStore(context).upsert(skill);
                break;
            }
        }
    }

    public static int count(Context context) {
        return newStore(context).loadAll().size();
    }

    /**
     * Id do "projeto atual": o workspace ativo (quando aberto), senão o
     * workspace mais recente
     * (fixos primeiro, depois último acesso — ordem do WorkspaceRepository).
     * Sem workspace conhecido retorna vazio (skills tornam-se USER-only).
     */
    public static String resolveCurrentProjectId(Context context) {
        if (context == null) {
            return "";
        }
        Workspace active = WorkspaceManager.getActiveWorkspace();
        if (active != null && active.getId() != null && !active.getId().isEmpty()) {
            return active.getId();
        }
        try {
            List<Workspace> recent = new WorkspaceRepository(context).getAll();
            if (recent != null && !recent.isEmpty()) {
                String id = recent.get(0).getId();
                return id == null ? "" : id;
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    /**
     * Fluxo oficial de Skills para o runtime de produção: store real +
     * manutenção de catálogo + seleção determinística + loader com teto de
     * conteúdo configurável, com diagnóstico no ChatFlowLogger. Sem contexto
     * (hosts JVM) retorna um fluxo desligado — o runtime simplesmente não
     * injeta Skills.
     */
    public static SkillFlow appFlow(Context context) {
        if (context == null) {
            return SkillFlow.NOOP;
        }
        final SkillDebugSink sink = message -> ChatFlowLogger.event("skills", "skill_flow", message);
        SkillStore store = newStore(context);
        SkillLoader loader = new SkillLoader(store, SkillLoader.DEFAULT_MAX_CONTENT_CHARS, sink);
        return new SkillPipeline(store, new DefaultSkillSelector(), loader, sink);
    }
}