package br.com.caiomilani.cltassistant.busca;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class RecuperadorTest {

    private static Recuperador.Candidato c(String ref, String dispositivo, Double score) {
        return new Recuperador.Candidato(ref, "CLT", "", "link", "texto do " + ref, dispositivo, score);
    }

    @Test
    void agrupaDispositivosDoMesmoArtigoPeloMelhorCandidato() {
        var vetorial = List.of(c("Art. 59", "§ 5º", 0.71), c("Art. 59", "caput", 0.69), c("Art. 58", "§ 1º", 0.65));

        var r = Recuperador.fundir(vetorial, List.of(), EstrategiaBusca.VETORIAL, 5);

        assertThat(r).extracting(Recuperador.ArtigoRecuperado::referencia).containsExactly("Art. 59", "Art. 58");
        assertThat(r.get(0).dispositivo()).isEqualTo("§ 5º");
        assertThat(r.get(0).texto()).isEqualTo("texto do Art. 59");
        assertThat(r.get(1).posicaoVetorial()).isEqualTo(2);
    }

    @Test
    void hibridaPromoveArtigoQueApareceNasDuasListas() {
        var vetorial = List.of(c("Art. 617", "caput", 0.53), c("Art. 224", "caput", 0.52), c("Art. 59", "§ 5º", 0.51));
        var textual = List.of(c("Art. 59", "§ 5º", null), c("Art. 59-A", "caput", null));

        var r = Recuperador.fundir(vetorial, textual, EstrategiaBusca.HIBRIDA, 3);

        assertThat(r.get(0).referencia()).isEqualTo("Art. 59");
        assertThat(r.get(0).posicaoVetorial()).isEqualTo(3);
        assertThat(r.get(0).posicaoTextual()).isEqualTo(1);
        assertThat(r.get(0).scoreVetorial()).isEqualTo(0.51);
    }

    @Test
    void hibridaIncluiArtigoQueSoApareceuNaBuscaTextual() {
        var r = Recuperador.fundir(List.of(c("Art. 617", "caput", 0.53)), List.of(c("Art. 58", "§ 1º", null)),
                EstrategiaBusca.HIBRIDA, 5);

        assertThat(r).extracting(Recuperador.ArtigoRecuperado::referencia).containsExactlyInAnyOrder("Art. 617", "Art. 58");
        assertThat(r).filteredOn(a -> a.referencia().equals("Art. 58")).first()
                .satisfies(a -> assertThat(a.scoreVetorial()).isNull());
    }

    @Test
    void consultaTextualSoComTermosSegurosParaOTsquery() {
        assertThat(Recuperador.consultaTextual("Posso fazer banco de horas? (sem sindicato) & 'teste' | 12"))
                .isEqualTo("posso | fazer | banco | horas | sem | sindicato | teste");
        assertThat(Recuperador.consultaTextual("? !")).isEmpty();
    }

    /** Caso Q12: a expansão de "almoço" não pode tirar o artigo que a pergunta original já trazia. */
    @Test
    void fundirListasMantemOQueAPerguntaOriginalTrazia() {
        var original = List.of(c("Art. 71", "caput", 0.70), c("Art. 611-A", "III", 0.68), c("Art. 230", "caput", 0.60),
                c("Art. 235-C", "§ 2º", 0.59), c("Art. 59-A", "caput", 0.58));
        var expansao = List.of(c("Art. 71", "caput", 0.80), c("Art. 383", "caput", 0.75), c("Art. 382", "caput", 0.74),
                c("Art. 412", "caput", 0.73), c("Art. 308", "caput", 0.72));

        var r = Recuperador.fundir(Recuperador.fundirListas(List.of(original, expansao)), List.of(),
                EstrategiaBusca.VETORIAL, 5);

        assertThat(r).extracting(Recuperador.ArtigoRecuperado::referencia).startsWith("Art. 71").contains("Art. 611-A");
    }

    /** Caso Q23/Q24: o artigo que só a busca da expansão encontra entra no top-k. */
    @Test
    void fundirListasTrazOQueSoAExpansaoEncontrou() {
        var original = List.of(c("Art. 239", "caput", 0.62), c("Art. 73", "§ 1º", 0.61), c("Art. 296", "caput", 0.60),
                c("Art. 235", "caput", 0.59), c("Art. 234", "caput", 0.58));
        var expansao12x36 = List.of(c("Art. 59-A", "caput", 0.82), c("Art. 235-F", "caput", 0.70));
        var expansaoNoturno = List.of(c("Art. 73", "§ 2º", 0.85), c("Art. 381", "caput", 0.70));

        var r = Recuperador.fundir(Recuperador.fundirListas(List.of(original, expansao12x36, expansaoNoturno)),
                List.of(), EstrategiaBusca.VETORIAL, 5);

        assertThat(r).extracting(Recuperador.ArtigoRecuperado::referencia).contains("Art. 59-A", "Art. 73");
        // o candidato e o score do Art. 73 vêm da busca pela pergunta original
        assertThat(r).filteredOn(a -> a.referencia().equals("Art. 73")).first()
                .satisfies(a -> assertThat(a.dispositivo()).isEqualTo("§ 1º"));
    }
}
