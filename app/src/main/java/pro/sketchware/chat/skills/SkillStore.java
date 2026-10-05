package pro.sketchware.chat.skills;

import java.util.List;

/**
 * Contrato de persistência do catálogo de Skills. O runtime e a UI dependem
 * desta interface (testes usam {@code InMemorySkillStore}; produção usa o
 * armazenamento SharedPreferences do {@link SkillManager}). A leitura é
 * tolerante: uma Skill corrompida é ignorada, nunca quebra a carga.
 */
public interface SkillStore {

    /** Carrega todas as Skills persistidas (ordem irrelevante). */
    List<Skill> loadAll();

    /** Substitui o conjunto persistido pelas Skills fornecidas. */
    void saveAll(List<Skill> skills);

    /** Cria/atualiza uma Skill pelo id. */
    default void upsert(Skill skill) {
        if (skill == null) {
            return;
        }
        List<Skill> all = loadAll();
        boolean replaced = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(skill.id)) {
                all.set(i, skill);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            all.add(skill);
        }
        saveAll(all);
    }

    /** Remove a Skill com o id informado (no-op quando ausente). */
    default void deleteById(String skillId) {
        if (skillId == null || skillId.isEmpty()) {
            return;
        }
        List<Skill> all = loadAll();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(skillId)) {
                all.remove(i);
                break;
            }
        }
        saveAll(all);
    }
}