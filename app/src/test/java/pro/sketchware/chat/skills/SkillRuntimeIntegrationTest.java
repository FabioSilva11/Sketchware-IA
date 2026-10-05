package pro.sketchware.chat.skills;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import pro.sketchware.chat.agentsdk.Agent;
import pro.sketchware.chat.agentsdk.AgentLlmGateway;
import pro.sketchware.chat.agentsdk.AgentRuntime;
import pro.sketchware.chat.agentsdk.FakeAgentLlmGateway;
import pro.sketchware.chat.agentsdk.RunResult;
import pro.sketchware.chat.agentsdk.tools.AxionToolRegistry;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Collections;

/**
 * Testes end-to-end do fluxo de Skills no AgentRuntime REAL
 * (SkillPipeline → AgentRuntime.Builder.skills(...) → resolveSystemPrompt →
 * gateway), com gateway guionizado e assertions no system prompt:
 *
 * <ol>
 *   <li>Seleção explícita injeta APENAS o conteúdo selecionado (discl.
 *       progressivo: só metadata dos demais no &lt;available_skills&gt;).</li>
 *   <li>Seleção por id é estável mesmo após renomear a skill.</li>
 *   <li>Nome ambíguo é REJEITADO, não adivinhado.</li>
 *   <li>Referência repetida não duplica o carregamento.</li>
 *   <li>Skill de projeto só é visível dentro do próprio projeto.</li>
 *   <li>EXPLICIT_ONLY nunca entra automática nem no catálogo oferecido.</li>
 *   <li>Conteúdo muito grande é truncado com registro.</li>
 *   <li>Store corrompido não derruba o runtime.</li>
 *   <li>Skill antiga (trigger) continua funcionando após a migração.</li>
 * </ol>
 */
public class SkillRuntimeIntegrationTest {

    // ------------------------------------------------------------------
    // 1) Seleção explícita: só o conteúdo selecionado entra no prompt
    // ------------------------------------------------------------------
    @Test
    public void explicitSelection_injectsOnlySelectedContent() {
        Skill android = Skill.create("android-debug", "Para depurar bugs Android",
                "CONT-ANDROID-MARKER usar logcat");
        Skill release = Skill.create("regras-release", "Ao publicar build",
                "CONT-RELEASE-MARKER publicar so na sexta");
        InMemorySkillStore store = new InMemorySkillStore().add(android).add(release);
        FakeAgentLlmGateway gateway = textGateway();

        runWithSkills(gateway, store, "Use $android-debug para este bug", "sc_explicit");

        String prompt = firstPrompt(gateway);
        assertTrue(prompt.contains("<available_skills>"));
        assertTrue("metadata (descricao) deve estar no catalogo",
                prompt.contains("Para depurar bugs Android"));
        assertTrue("conteudo selecionado deve entrar na integra",
                prompt.contains("CONT-ANDROID-MARKER"));
        assertTrue(prompt.contains("<selected_skill name=\"android-debug\" id=\"" + android.id + "\">"));
        assertFalse("skill nao selecionada nao pode injetar conteudo",
                prompt.contains("CONT-RELEASE-MARKER"));
    }

    // ------------------------------------------------------------------
    // 2) Selecao por ID: estavel mesmo apos renomear (identidade != nome)
    // ------------------------------------------------------------------
    @Test
    public void explicitById_resolvesAfterRename() {
        Skill skill = Skill.create("nome-antigo", "para depurar", "CORPO-CONTINUIDADE");
        InMemorySkillStore store = new InMemorySkillStore().add(skill);
        FakeAgentLlmGateway gateway = textGateway();
        runWithSkills(gateway, store, "odebugar com $" + skill.id, "sc_id_old");
        assertTrue(firstPrompt(gateway).contains("CORPO-CONTINUIDADE"));

        skill.name = "nome-completamente-novo";
        store.upsert(skill);
        FakeAgentLlmGateway renamedGateway = textGateway();
        runWithSkills(renamedGateway, store, "odebugar com $" + skill.id, "sc_id_new");
        String prompt = firstPrompt(renamedGateway);
        assertTrue("id estavel deve seguir selecionando apos rename", prompt.contains("CORPO-CONTINUIDADE"));
        assertTrue(prompt.contains("id=\"" + skill.id + "\""));
        assertFalse("o nome novo nao deve ser a chave da selecao", prompt.contains("<selected_skill name=\"nome-antigo\""));
    }

    // ------------------------------------------------------------------
    // 3) Nome ambiguo: rejeitado com justificativa, nunca piloto automatico
    // ------------------------------------------------------------------
    @Test
    public void ambiguousName_isRejectedWithReason() {
        Skill a = Skill.create("android", "regras android 1", "CONTEUDO-A");
        Skill b = Skill.create("Android", "regras android 2", "CONTEUDO-B");
        InMemorySkillStore store = new InMemorySkillStore().add(a).add(b);

        SkillPipeline pipeline = pipeline(store);
        SkillContext context = pipeline.resolveFor("sc_ambig", "odebugar $android hoje");
        assertTrue("ambiguidade deve ser registrada", reasons(context).contains("ambiguous"));
        assertFalse(context.result().hasSelected());

        FakeAgentLlmGateway gateway = textGateway();
        runWithSkills(gateway, store, "odebugar $android hoje", "sc_ambig");
        String prompt = firstPrompt(gateway);
        assertFalse("nada deve ser injetado quando o alvo e ambiguo", prompt.contains("CONTEUDO-A"));
        assertFalse(prompt.contains("CONTEUDO-B"));
        assertFalse(prompt.contains("<selected_skill"));
    }

    // ------------------------------------------------------------------
    // 4) Referencia repetida: um resultado de selecao, um carregamento
    // ------------------------------------------------------------------
    @Test
    public void repeatedExplicitRef_loadsOnce() {
        Skill skill = Skill.create("android-debug", "depuracao", "CONTEUDO-UNICO");
        InMemorySkillStore store = new InMemorySkillStore().add(skill);
        String text = "use $android-debug e de novo $android-debug por favor";

        SkillPipeline pipeline = pipeline(store);
        SkillContext context = pipeline.resolveFor("sc_repeat", text);
        assertEquals(1, context.result().selected().size());
        assertEquals(2, context.result().explicitRefs().size());

        FakeAgentLlmGateway gateway = textGateway();
        runWithSkills(gateway, store, text, "sc_repeat");
        String prompt = firstPrompt(gateway);
        assertEquals("conteudo deve aparecer exatamente uma vez",
                1, countOccurrences(prompt, "<selected_skill "));
    }

    // ------------------------------------------------------------------
    // 5) Escopo de projeto: invisivel fora do proprio projeto
    // ------------------------------------------------------------------
    @Test
    public void projectScoped_visibleOnlyInsideOwnProject() {
        Skill projectSkill = Skill.createFull("regras-app", "regras do app", "",
                "SEGREDO-PROJETO-A",
                SkillInvocationPolicy.AUTOMATIC, SkillScope.PROJECT, "proj-a",
                Collections.<SkillResource>emptyList());
        Skill globalSkill = Skill.create("utilitarios", "tarefas gerais", "PUBLICO-USER");
        InMemorySkillStore store = new InMemorySkillStore().add(projectSkill).add(globalSkill);

        FakeAgentLlmGateway inside = textGateway();
        runWithSkills(inside, store, "quero seguir as regras do app hoje", "proj-a");
        String insidePrompt = firstPrompt(inside);
        assertTrue("dentro do projeto a skill esta disponivel",
                insidePrompt.contains("SEGREDO-PROJETO-A"));

        FakeAgentLlmGateway outside = textGateway();
        runWithSkills(outside, store, "quero seguir as regras do app hoje", "proj-b");
        String outsidePrompt = firstPrompt(outside);
        assertFalse("fora do projeto o conteudo nao pode vazar",
                outsidePrompt.contains("SEGREDO-PROJETO-A"));
        assertFalse("nem sequer o metadata deve aparecer no catalogo",
                outsidePrompt.contains("regras-app"));
        assertTrue("skill USER continua visivel", outsidePrompt.contains("PUBLICO-USER")
                || outsidePrompt.contains("utilitarios"));
    }

    // ------------------------------------------------------------------
    // 6) EXPLICIT_ONLY: fora do catalogo automatico, mas selecionavel
    // ------------------------------------------------------------------
    @Test
    public void explicitOnly_neverAutomaticButResolvesOnRequest() {
        Skill ouro = Skill.createFull("regras-ouro", "regras de ouro android", "",
                "CONTEUDO-OURO",
                SkillInvocationPolicy.EXPLICIT_ONLY, SkillScope.USER, "",
                Collections.<SkillResource>emptyList());
        Skill automatic = Skill.create("android-debug", "depuracao android", "CONTEUDO-AUTO");
        InMemorySkillStore store = new InMemorySkillStore().add(ouro).add(automatic);

        FakeAgentLlmGateway automaticGateway = textGateway();
        runWithSkills(automaticGateway, store,
                "preciso seguir regras de ouro android e depurar agora", "sc_auto");
        String automaticPrompt = firstPrompt(automaticGateway);
        assertFalse("EXPLICIT_ONLY nao pode ser escolhida automaticamente",
                automaticPrompt.contains("CONTEUDO-OURO"));
        assertFalse("nem aparecer no catalogo oferecido",
                automaticPrompt.contains("regras-ouro"));
        assertTrue("skill automatica continua no catalogo de metadata",
                automaticPrompt.contains("android-debug"));

        FakeAgentLlmGateway explicitGateway = textGateway();
        runWithSkills(explicitGateway, store, "quero $regras-ouro", "sc_exp");
        assertTrue("EXPLICIT_ONLY deve entrar quando pedida",
                firstPrompt(explicitGateway).contains("CONTEUDO-OURO"));
    }

    // ------------------------------------------------------------------
    // 7) Conteudo gigante: truncado e registrado no bloco injetado
    // ------------------------------------------------------------------
    @Test
    public void oversizedSkill_contentIsTruncatedWithMarker() {
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 400; i++) {
            huge.append("linha-gigante-").append(i).append('\n');
        }
        Skill skill = Skill.create("enorme", "skill grande demais", huge.toString());
        InMemorySkillStore store = new InMemorySkillStore().add(skill);
        FakeAgentLlmGateway gateway = textGateway();
        SkillFlow flow = new SkillPipeline(store, new DefaultSkillSelector(),
                new SkillLoader(store, 400, SkillDebugSink.NOOP), SkillDebugSink.NOOP);

        AgentRuntime runtime = runtime(gateway, flow);
        RunResult result = runtime.run(scriptedAgent(), "use $enorme agora", "sc_big");
        assertTrue(result.isSuccessful());

        String prompt = firstPrompt(gateway);
        assertTrue(prompt.contains("(truncated)"));
        assertTrue(prompt.contains("[skill content truncated to fit the context budget]"));
        assertFalse(prompt.contains("linha-gigante-399"));
    }

    // ------------------------------------------------------------------
    // 8) Store corrompido: runtime completa sem skills, sem excecao
    // ------------------------------------------------------------------
    @Test
    public void corruptedStore_runtimeStillCompletes() {
        InMemorySkillStore store = new InMemorySkillStore()
                .failWith(new IllegalStateException("store corrompido"));
        FakeAgentLlmGateway gateway = textGateway();

        RunResult result = runWithSkills(gateway, store, "odebugar o app", "sc_corrupt");

        assertTrue("a conversa nao pode quebrar por causa de skills",
                result.isSuccessful());
        String prompt = firstPrompt(gateway);
        assertFalse(prompt.contains("<available_skills>"));
        assertFalse(prompt.contains("<selected_skill"));
    }

    // ------------------------------------------------------------------
    // 9) Skill antiga (trigger) segue funcionando pos-migracao no runtime
    // ------------------------------------------------------------------
    @Test
    public void legacyTriggerSkill_worksAfterMigration() throws Exception {
        JSONObject legacy = new JSONObject()
                .put("id", "legacy-xml-1").put("name", "android-legacy")
                .put("trigger", "Ao criar telas XML")
                .put("content", "LEGACY-CONTENT usar ViewBinding")
                .put("enabled", true);
        InMemorySkillStore store = new InMemorySkillStore().add(Skill.fromJson(legacy));

        FakeAgentLlmGateway gateway = textGateway();
        RunResult result = runWithSkills(gateway, store, "odebugar $android-legacy", "sc_legacy");

        assertTrue(result.isSuccessful());
        String prompt = firstPrompt(gateway);
        assertTrue(prompt.contains("LEGACY-CONTENT"));
        assertTrue(prompt.contains("id=\"legacy-xml-1\""));
        assertTrue("descricao migrada deve aparecer como metadata",
                prompt.contains("Ao criar telas XML"));
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private FakeAgentLlmGateway textGateway() {
        return new FakeAgentLlmGateway(FakeAgentLlmGateway.ScriptedTurn.text("ok"));
    }

    private static SkillPipeline pipeline(SkillStore store) {
        return new SkillPipeline(store, new DefaultSkillSelector(),
                new SkillLoader(store), SkillDebugSink.NOOP);
    }

    private static AgentRuntime runtime(AgentLlmGateway gateway, SkillFlow flow) {
        return new AgentRuntime.Builder(gateway)
                .toolRegistry(new AxionToolRegistry())
                .skills(flow)
                .build();
    }

    private static RunResult runWithSkills(AgentLlmGateway gateway, SkillStore store,
                                           String text, String scId) {
        return runtime(gateway, pipeline(store)).run(scriptedAgent(), text, scId);
    }

    private static Agent scriptedAgent() {
        return Agent.Builder.forName("coordinator", "You are a helpful coding agent.").build();
    }

    private static String firstPrompt(FakeAgentLlmGateway gateway) {
        assertFalse("gateway deve ter recebido ao menos uma requisicao",
                gateway.requestedSystemPrompts().isEmpty());
        return gateway.requestedSystemPrompts().get(0);
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = text.indexOf(needle);
        while (index >= 0) {
            count++;
            index = text.indexOf(needle, index + needle.length());
        }
        return count;
    }

    private static String reasons(SkillContext context) {
        assertNotNull(context);
        return String.valueOf(context.result().reasons());
    }
}