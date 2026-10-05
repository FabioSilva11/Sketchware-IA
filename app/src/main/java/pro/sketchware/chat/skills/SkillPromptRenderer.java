package pro.sketchware.chat.skills;

import java.util.List;

/**
 * Renderiza os blocos de Skills que são anexados ao system prompt:
 *
 * <ul>
 *   <li><b>&lt;available_skills&gt;</b>: APENAS metadata (nome + descrição) das
 *       Skills visíveis/ativas — o modelo conhece a oferta sem ver conteúdo.</li>
 *   <li><b>&lt;selected_skill name="" id=""&gt;</b>: nome + id estável e o
 *       conteúdo completo da Skill selecionada (com aviso de truncamento).</li>
 * </ul>
 *
 * <p>O formato separa identidade da apresentação: {@code name} é o rótulo; a
 * {@code id} é a chave canônica estável que o modelo JAMAIS deve tratar como
 * nome.</p>
 */
public final class SkillPromptRenderer {

    /** Teto de Skills no bloco <available_skills> (metadata é barato, mas o livro não morre). */
    public static final int MAX_AVAILABLE_ENTRIES = 200;
    /** Teto por linha de descrição no catálogo. */
    private static final int MAX_DESC_CHARS = 200;

    private SkillPromptRenderer() {
    }

    public static String renderAvailable(List<SkillMetadata> skills) {
        StringBuilder builder = new StringBuilder("<available_skills>\n");
        int shown = 0;
        for (SkillMetadata skill : skills) {
            if (shown >= MAX_AVAILABLE_ENTRIES) {
                break;
            }
            shown++;
            String name = skill.name().trim().isEmpty() ? "Untitled skill" : skill.name().trim();
            builder.append("- ").append(escape(name)).append(": ")
                    .append(oneLine(skill.displayDescription(), MAX_DESC_CHARS))
                    .append('\n');
        }
        builder.append("</available_skills>");
        return builder.toString();
    }

    public static String renderSelected(String name, String id, String content,
                                        boolean truncated) {
        if (content == null || content.trim().isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("<selected_skill name=\"").append(escapeAttr(name)).append("\" id=\"")
                .append(escapeAttr(id)).append("\">\n");
        builder.append(content.trim());
        if (truncated) {
            builder.append("\n[skill content truncated to fit the context budget]");
        }
        builder.append("\n</selected_skill>");
        return builder.toString();
    }

    public static String escapeAttr(String value) {
        return String.valueOf(value)
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    public static String escape(String value) {
        return escapeAttr(value);
    }

    static String oneLine(String value, int max) {
        String collapsed = String.valueOf(value)
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        if (collapsed.length() > max) {
            return collapsed.substring(0, max) + "…";
        }
        return collapsed;
    }
}