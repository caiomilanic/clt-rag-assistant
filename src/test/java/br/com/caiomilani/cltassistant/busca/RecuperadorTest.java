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
}
