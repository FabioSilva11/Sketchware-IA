package pro.sketchware.chat.skills;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Seleção determinística e auditável de Skills:
 *
 * <ol>
 *   <li><b>Explícita</b>: o usuário cita uma skill por nome (ex.
 *       {@code $convencoes} ou {@code skills:convencoes}). Nome ambíguo é
 *       REJEITADO com justificativa (nunca seleção silenciosa); ids estáveis
 *       são aceitos.</li>
 *   <li><b>Automática</b>: apenas Skills {@code AUTOMATIC} habilitadas e
 *       visíveis no projeto. Cada termo do pedido é comparado com
 *       name+description: um termo coletado-por-stemming contido no texto da
 *       skill pontua; o limiar (padrão = 2 termos) decide. O desempate por
 *       order é estável (ordem do catálogo).</li>
 *   <li><b>Rejeição auditável</b>: skills EXPLICIT_ONLY sem referência
 *       explícita nunca entram; skills abaixo do limiar viram candidatos
 *       rejeitados com a razão registrada.</li>
 * </ol>
 */
public final class DefaultSkillSelector implements SkillSelector {

    /** Limiar de correspondência (termos do pedido que casaram, mínimo = 2). */
    static final int DEFAULT_MIN_MATCHES = 2;
    /** Match parcial de termo ("androidmanifest" contém "android") pontua 1/2. */
    private static final double PARTIAL_SCORE = 0.5;

    private static final Pattern EXPLICIT_REF = Pattern.compile(
            "(?:^|[\\s,()])[$]([A-Za-z0-9._-]{2,40})");

    private final int minMatches;
    private final SkillDebugSink sink;

    public DefaultSkillSelector() {
        this(DEFAULT_MIN_MATCHES, SkillDebugSink.NOOP);
    }

    public DefaultSkillSelector(int minMatches, SkillDebugSink sink) {
        this.minMatches = Math.max(1, minMatches);
        this.sink = sink == null ? SkillDebugSink.NOOP : sink;
    }

    @Override
    public SkillSelectionResult select(String userText, String projectId, SkillCatalog catalog) {
        String text = userText == null ? "" : userText.trim();
        List<SkillMetadata> available = catalog == null
                ? Collections.emptyList()
                : catalog.listForProject(projectId);
        Map<String, String> reasons = new LinkedHashMap<String, String>();
        List<String> refs = extractExplicitRefs(text);

        // ---- 1) Seleção explícita --------------------------------------
        List<SkillMetadata> selected = new ArrayList<SkillMetadata>();
        Set<String> selectedIds = new HashSet<String>();
        List<SkillMetadata> rejected = new ArrayList<SkillMetadata>();
        if (!refs.isEmpty()) {
            for (String ref : refs) {
                // Id estável primeiro: resolve mesmo que o nome tenha mudado
                // (identidade nunca é o nome).
                SkillMetadata byId = catalog == null ? null : catalog.findById(ref);
                if (byId != null) {
                    addUnique(selected, selectedIds, byId);
                    reasons.put(key(byId), "explicit:id:" + ref);
                    continue;
                }
                List<SkillMetadata> byName = findByName(available, ref);
                if (byName.isEmpty()) {
                    rejectedReason(reasons, "skill:" + ref, "not-found");
                    continue;
                }
                if (byName.size() > 1) {
                    rejectedReason(reasons, "skill:" + ref, "ambiguous:" + namesOf(byName));
                    continue;
                }
                SkillMetadata match = byName.get(0);
                addUnique(selected, selectedIds, match);
                reasons.put(key(match), "explicit:" + ref);
            }
        }

        // ---- 2) Seleção automática --------------------------------------
        List<SkillMetadata> candidates = new ArrayList<SkillMetadata>();
        for (SkillMetadata skill : available) {
            if (selectedIds.contains(skill.id())) {
                continue;
            }
            if (skill.invocationPolicy() == SkillInvocationPolicy.EXPLICIT_ONLY) {
                rejected.add(skill);
                reasons.put(key(skill), "explicit-only-and-not-requested");
                continue;
            }
            if (!skill.enabled()) {
                rejected.add(skill);
                reasons.put(key(skill), "disabled");
                continue;
            }
            double score = score(text, skill);
            if (score >= minMatches) {
                addUnique(selected, selectedIds, skill);
                reasons.put(key(skill), "auto:score=" + formatScore(score));
            } else {
                candidates.add(skill);
                if (score > 0) {
                    reasons.put(key(skill), "below-threshold:score=" + formatScore(score));
                }
            }
        }

        sinkAudit(text, projectId, selected, candidates, rejected, reasons);
        return new SkillSelectionResult(selected, candidates, rejected, reasons, refs);
    }

    static List<String> extractExplicitRefs(String text) {
        List<String> refs = new ArrayList<String>();
        if (text == null || text.isEmpty()) {
            return refs;
        }
        Matcher matcher = EXPLICIT_REF.matcher(text);
        while (matcher.find()) {
            refs.add(matcher.group(1));
        }
        return refs;
    }

    static boolean hasExplicitRef(String text) {
        return text != null && EXPLICIT_REF.matcher(text).find();
    }

    static double score(String userText, SkillMetadata skill) {
        if (userText == null || userText.trim().isEmpty()) {
            return 0;
        }
        String haystack = (skill.name() + " " + skill.description()).toLowerCase(Locale.ROOT);
        String[] terms = userText.toLowerCase(Locale.ROOT).split("[^a-z0-9]{1,}");
        double score = 0;
        for (String term : terms) {
            if (term.length() < 3) {
                continue;
            }
            String stem = stem(term);
            if (stem.isEmpty()) {
                continue;
            }
            if (haystack.contains(term)) {
                score += 1;
            } else if (haystack.contains(stem)) {
                score += 1;
            } else if (term.length() >= 6 && haystack.contains(prefixOf(term, 4))) {
                score += PARTIAL_SCORE;
            }
        }
        return score;
    }

    /** Stemming mínimo: plural "s" e sufixo "es" (português/inglês óbvio). */
    static String stem(String term) {
        String t = term;
        if (t.endsWith("oes")) {
            t = t.substring(0, t.length() - 2);
        } else if (t.endsWith("es") && t.length() > 4) {
            t = t.substring(0, t.length() - 2);
        } else if (t.endsWith("s") && !t.endsWith("ss") && t.length() > 3) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }

    private static String prefixOf(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length);
    }

    private static List<SkillMetadata> findByName(List<SkillMetadata> skills, String ref) {
        List<SkillMetadata> exact = new ArrayList<SkillMetadata>();
        List<SkillMetadata> fuzzy = new ArrayList<SkillMetadata>();
        String lower = ref.toLowerCase(Locale.ROOT);
        for (SkillMetadata skill : skills) {
            for (String candidate : skill.selectionNames()) {
                if (candidate.equalsIgnoreCase(ref)) {
                    exact.add(skill);
                    break;
                }
            }
            if (skill.name().toLowerCase(Locale.ROOT).contains(lower)
                    || skill.identity().packageName().contains(lower)) {
                fuzzy.add(skill);
            }
        }
        return exact.isEmpty() ? fuzzy : exact;
    }

    private static void addUnique(List<SkillMetadata> list, Set<String> ids, SkillMetadata skill) {
        if (ids.add(skill.id())) {
            list.add(skill);
        }
    }

    private static void rejectedReason(Map<String, String> reasons, String key, String reason) {
        reasons.put(key, reason);
    }

    private static String key(SkillMetadata skill) {
        return "skill:" + skill.id() + ":" + skill.name();
    }

    private static String namesOf(List<SkillMetadata> skills) {
        StringBuilder builder = new StringBuilder();
        for (SkillMetadata skill : skills) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(skill.name());
        }
        return builder.toString();
    }

    private static String formatScore(double score) {
        if (score == Math.rint(score)) {
            return String.valueOf((long) score);
        }
        return String.valueOf(score);
    }

    private void sinkAudit(String text, String projectId, List<SkillMetadata> selected,
                           List<SkillMetadata> candidates, List<SkillMetadata> rejected,
                           Map<String, String> reasons) {
        StringBuilder detail = new StringBuilder();
        detail.append("project=").append(projectId)
                .append(" selected=").append(selected.size())
                .append(" candidates=").append(candidates.size())
                .append(" rejected=").append(rejected.size());
        for (Map.Entry<String, String> entry : reasons.entrySet()) {
            detail.append(' ').append(entry.getKey()).append('=').append(entry.getValue());
        }
        if (!text.isEmpty()) {
            detail.append(" text=").append(truncate(text, 60));
        }
        sink.log("selector:" + detail);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}