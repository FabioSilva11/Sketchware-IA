package pro.sketchware.chat.skills;

import java.util.Locale;
import java.util.Objects;

/**
 * Identidade REAL de uma Skill. O nome visual é apenas um rótulo (e não é
 * único); a identidade é sempre composta pelo id persistente, pela origem
 * lógica e pelo escopo — o formato canônico é {@code user:USER:<uuid>} ou
 * {@code user:PROJECT:<uuid>}. Referências explícitas do usuário (ex.
 * <code>$nome</code>) são resolvidas por nome, mas o bloco injetado nunca
 * carrega o id como se ele fosse o nome.
 */
public final class SkillIdentity {
    private final String id;
    private final String source;
    private final SkillScope scope;
    private final String packageName;

    public SkillIdentity(String id, String source, SkillScope scope, String packageName) {
        this.id = id == null ? "" : id;
        this.source = source == null ? "" : source;
        this.scope = scope == null ? SkillScope.USER : scope;
        this.packageName = packageName == null ? "" : packageName;
    }

    /** Chave canônica instável-por-renomeação jamais usada: {@code source:scope:id}. */
    public String canonicalKey() {
        return source + ":" + scope.name() + ":" + id;
    }

    public String id() {
        return id;
    }

    public String source() {
        return source;
    }

    public SkillScope scope() {
        return scope;
    }

    /** Nome lógico normalizado (package) derivado do nome visual. */
    public String packageName() {
        return packageName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SkillIdentity)) {
            return false;
        }
        SkillIdentity that = (SkillIdentity) o;
        return id.equals(that.id)
                && source.equals(that.source)
                && scope == that.scope;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, source, scope);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%s:%s:%s", source, scope.name(), id);
    }
}