package pro.sketchware.chat.skills;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;

/**
 * Identidade e semântica de criação de Skills (o campo identidade é sempre
 * um id persistente estável — JAMAIS o nome):
 *
 * <ol>
 *   <li>Criar skill com name e content mínimos.</li>
 *   <li>Identidade não é o nome: renomear preserva o id.</li>
 *   <li>Chave canônica estável {@code user:USER:<uuid>}.</li>
 *   <li>Duas skills com o mesmo nome têm identidades distintas.</li>
 *   <li>Escopo PROJECT produz {@code user:PROJECT:<uuid>}.</li>
 *   <li>packageName derivado do nome (rótulo lógico).</li>
 *   <li>equals/hashCode consistentes por id+source+scope.</li>
 *   <li>Versão padrão VERSION_V2; createFull preserva política.</li>
 *   <li>Migração: fromJson legado (só trigger) vira USER + description.</li>
 *   <li>fromJson PROJECT com projectId preserva escopo.</li>
 *   <li>Defensivo: PROJECT sem projectId rebaixa para USER.</li>
 *   <li>Descrição exibível usa getDescription() (description → shortDesc).</li>
 * </ol>
 */
public class SkillIdentityTest {

    @Test
    public void createMinimal_generatesIdentityAndDefaults() {
        Skill skill = Skill.create("android-debug", "Fluxo de debug", "Usar logcat e ariadne");
        assertNotNull(skill.id);
        assertFalse(skill.id.isEmpty());
        assertEquals("android-debug", skill.name);
        assertEquals("Fluxo de debug", skill.description);
        assertEquals("Usar logcat e ariadne", skill.content);
        assertTrue(skill.enabled);
        SkillIdentity identity = skill.identity();
        assertEquals("user", identity.source());
        assertEquals(SkillScope.USER, identity.scope());
        assertTrue(identity.id().equals(skill.id));
    }

    @Test
    public void identity_isNotTheName_renameKeepsId() {
        Skill skill = Skill.create("android-debug", "d", "c");
        String idBefore = skill.id;
        SkillIdentity before = skill.identity();
        skill.name = "Totalmente Novo";
        assertTrue("o id nunca deve depender do nome", before.id().equals(skill.id));
        assertEquals(idBefore, skill.id);
        assertTrue(before.equals(skill.identity()));
    }

    @Test
    public void canonicalKey_isStableUserScope() {
        Skill skill = Skill.create("android-debug", "d", "c");
        assertEquals("user:USER:" + skill.id, skill.identity().canonicalKey());
    }

    @Test
    public void sameName_distinctIdentities() {
        Skill a = Skill.create("iguais", "d", "c1");
        Skill b = Skill.create("iguais", "d", "c2");
        assertNotEquals(a.id, b.id);
        assertNotEquals(a.identity(), b.identity());
        assertNotEquals(a.identity().canonicalKey(), b.identity().canonicalKey());
    }

    @Test
    public void projectScope_canonicalKeyUsesScope() {
        Skill skill = Skill.createFull("android-debug", "d", "s", "c",
                SkillInvocationPolicy.AUTOMATIC, SkillScope.PROJECT, "proj-1",
                Collections.<SkillResource>emptyList());
        assertEquals("user:PROJECT:" + skill.id, skill.identity().canonicalKey());
        assertEquals(SkillScope.PROJECT, skill.identity().scope());
    }

    @Test
    public void packageName_derivedFromVisualName() {
        assertEquals("convenções-do-projeto", Skill.packageName("Convenções do projeto"));
        assertEquals("android-debug", Skill.packageName("Android Debug"));
        assertEquals("", Skill.packageName("   "));
        assertEquals("a", Skill.packageName("   a  !  "));
    }

    @Test
    public void equalsHashCode_consistent() {
        Skill skill = Skill.create("x", "d", "c");
        SkillIdentity i1 = skill.identity();
        SkillIdentity i2 = skill.identity();
        assertEquals(i1, i2);
        assertEquals(i1.hashCode(), i2.hashCode());
        SkillIdentity otherScope = new SkillIdentity(skill.id, "user", SkillScope.PROJECT, "x");
        assertNotEquals(i1, otherScope);
        assertNotEquals(i1.hashCode(), otherScope.hashCode());
    }

    @Test
    public void version_defaultsToV2_andCreateFullPreservesPolicy() {
        Skill simple = Skill.create("x", "d", "c");
        assertEquals(Skill.VERSION_V2, simple.version);
        Skill explicit = Skill.createFull("x", "d", "s", "c",
                SkillInvocationPolicy.EXPLICIT_ONLY, SkillScope.USER, "",
                Collections.<SkillResource>emptyList());
        assertEquals(SkillInvocationPolicy.EXPLICIT_ONLY, explicit.invocationPolicy);
        assertEquals(Skill.VERSION_V2, explicit.version);
        Skill automatic = Skill.createFull("y", "d", "s", "c",
                SkillInvocationPolicy.AUTOMATIC, SkillScope.USER, "",
                Collections.<SkillResource>emptyList());
        assertEquals(SkillInvocationPolicy.AUTOMATIC, automatic.invocationPolicy);
    }

    @Test
    public void fromJson_legacyTrigger_isMigratedToDescriptionUser() throws Exception {
        org.json.JSONObject legacy = new org.json.JSONObject()
                .put("id", "legacy-1").put("name", "Convenções XML")
                .put("trigger", "Ao criar novas telas XML")
                .put("content", "Usar ViewBinding sem findViewById.")
                .put("enabled", true);
        Skill migrated = Skill.fromJson(legacy);
        assertEquals("Ao criar novas telas XML", migrated.description);
        assertEquals("Ao criar novas telas XML", Skill.fromJson(legacy).getDescription());
        assertEquals(SkillScope.USER, migrated.scope);
        assertEquals(SkillInvocationPolicy.AUTOMATIC, migrated.invocationPolicy);
        assertTrue("conteúdo não pode se perder na migração",
                migrated.content.contains("ViewBinding"));
    }

    @Test
    public void fromJson_projectScope_withProjectId() throws Exception {
        org.json.JSONObject json = new org.json.JSONObject()
                .put("id", "p-1").put("name", "Regras do app")
                .put("description", "Regras")
                .put("content", "Subir release às sextas.")
                .put("scope", "PROJECT").put("projectId", "proj-9");
        Skill skill = Skill.fromJson(json);
        assertEquals(SkillScope.PROJECT, skill.scope);
        assertEquals("proj-9", skill.projectId);
        assertEquals(SkillScope.PROJECT, skill.identity().scope());
    }

    @Test
    public void fromJson_projectWithoutId_defendsToUser() throws Exception {
        org.json.JSONObject json = new org.json.JSONObject()
                .put("id", "p-2").put("name", "Órfã")
                .put("description", "d").put("content", "c")
                .put("scope", "PROJECT").put("projectId", "");
        Skill skill = Skill.fromJson(json);
        assertEquals(SkillScope.USER, skill.scope);
        assertEquals("", skill.projectId);
    }

    @Test
    public void getDescription_fallsBackToShortDescription() {
        Skill skill = Skill.create("x", "   ", "c");
        skill.description = "   ";
        skill.shortDescription = "Resumo curto";
        assertEquals("Resumo curto", skill.getDescription());
        Skill full = Skill.create("y", "Descrição real", "c");
        assertEquals("Descrição real", full.getDescription());
    }
}