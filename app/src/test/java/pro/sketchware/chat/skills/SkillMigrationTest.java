package pro.sketchware.chat.skills;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

/**
 * Migração trigger → description: o JSON antigo (campo {@code trigger}, sem
 * scope/policy) deve continuar funcionando SEM perder conteúdo, e o
 * round-trip v2 não pode descartar novos campos.
 */
public class SkillMigrationTest {

    @Test
    public void legacyTrigger_becomesDescription_withoutLoss() throws Exception {
        JSONObject legacy = legacyJson("Ao revisar código Android",
                "Seguir a StyleGuide; evitar TODOs em produção.");
        Skill skill = Skill.fromJson(legacy);
        assertEquals("Ao revisar código Android", skill.description);
        assertEquals("Seguir a StyleGuide; evitar TODOs em produção.", skill.content);
        assertEquals("Convenções de código", skill.name);
        assertTrue(skill.enabled);
        assertEquals(Skill.VERSION_V2, skill.version);
    }

    @Test
    public void legacyTrigger_roundTripKeepsContentInJson() throws Exception {
        JSONObject legacy = legacyJson("Ao revisar código Android",
                "Seguir a StyleGuide; evitar TODOs em produção.");
        JSONObject reloaded = Skill.fromJson(legacy).toJson();
        assertTrue("novo JSON deve gravar description", reloaded.has("description"));
        assertFalse("trigger não deve sobreviver no novo formato", reloaded.has("trigger"));
        assertTrue(reloaded.getString("content").contains("StyleGuide"));
    }

    @Test
    public void v2Shape_roundTripIsLossless() throws Exception {
        Skill original = Skill.createFull("Regras de release", "Ao publicar build",
                "Resumo release", "Só publicar na sexta.",
                SkillInvocationPolicy.EXPLICIT_ONLY, SkillScope.PROJECT, "proj-7",
                Collections.singletonList(
                        new SkillResource(SkillResourceType.REFERENCE, "runbook", "docs/runbook.md")));
        Skill reloaded = Skill.fromJson(original.toJson());
        assertEquals(original.id, reloaded.id);
        assertEquals("Ao publicar build", reloaded.description);
        assertEquals("Resumo release", reloaded.shortDescription);
        assertEquals("Só publicar na sexta.", reloaded.content);
        assertEquals(SkillInvocationPolicy.EXPLICIT_ONLY, reloaded.invocationPolicy);
        assertEquals(SkillScope.PROJECT, reloaded.scope);
        assertEquals("proj-7", reloaded.projectId);
        assertEquals(Skill.VERSION_V2, reloaded.version);
        assertEquals(1, reloaded.resources.size());
        assertEquals(SkillResourceType.REFERENCE, reloaded.resources.get(0).type());
    }

    @Test
    public void legacyEntry_insideStore_stillServesContentToLoader() throws Exception {
        Skill legacy = Skill.fromJson(legacyJson("Ao criar telas XML", "Usar ViewBinding."));
        InMemorySkillStore store = new InMemorySkillStore().add(legacy);
        SkillLoader loader = new SkillLoader(store, SkillLoader.DEFAULT_MAX_CONTENT_CHARS,
                SkillDebugSink.NOOP);
        List<SkillLoader.LoadedSkill> loaded = loader.load("", Collections.singletonList(legacy.id));
        assertEquals(1, loaded.size());
        assertEquals("Usar ViewBinding.", loaded.get(0).usedContent());
        assertFalse(loaded.get(0).truncated());
        assertFalse(loaded.get(0).skill().identity().id().isEmpty());
    }

    @Test
    public void legacyEmptyTrigger_stillUsable() throws Exception {
        JSONObject legacy = new JSONObject()
                .put("id", "no-trigger").put("name", "Técnica mínima")
                .put("content", "Usar lint antes de commit.")
                .put("enabled", true)
                .put("createdAt", 1L).put("updatedAt", 2L);
        Skill skill = Skill.fromJson(legacy);
        assertNotNull(skill);
        assertEquals("", skill.description);
        assertEquals("Usar lint antes de commit.", skill.content);
        assertEquals("Técnica mínima", skill.description.isBlank() ? skill.name : skill.description);
    }

    private static JSONObject legacyJson(String trigger, String content) throws Exception {
        return new JSONObject()
                .put("id", "legacy-xml")
                .put("name", "Convenções de código")
                .put("trigger", trigger)
                .put("content", content)
                .put("enabled", true)
                .put("createdAt", 1_700_000_000_000L)
                .put("updatedAt", 1_700_000_001_000L);
    }
}