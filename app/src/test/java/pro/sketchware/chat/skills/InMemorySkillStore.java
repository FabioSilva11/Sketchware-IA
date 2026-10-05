package pro.sketchware.chat.skills;

import java.util.ArrayList;
import java.util.List;

/**
 * SkillStore em memória para testes JVM: não toca em Android/SharedPreferences
 * e permite simular corrupção (ex.: loadAll lançando) para validar a
 * tolerância a falhas do pipeline.
 */
public final class InMemorySkillStore implements SkillStore {

    private final List<Skill> skills = new ArrayList<Skill>();
    private RuntimeException failure;

    @Override
    public List<Skill> loadAll() {
        if (failure != null) {
            throw failure;
        }
        return new ArrayList<Skill>(skills);
    }

    @Override
    public void saveAll(List<Skill> newSkills) {
        if (failure != null) {
            throw failure;
        }
        skills.clear();
        if (newSkills != null) {
            skills.addAll(newSkills);
        }
    }

    public InMemorySkillStore add(Skill skill) {
        if (skill != null) {
            skills.add(skill);
        }
        return this;
    }

    /** Simula repositório corrompido/inacessível (carga passa a falhar). */
    public InMemorySkillStore failWith(RuntimeException error) {
        this.failure = error;
        return this;
    }
}