package br.com.caiomilani.cltassistant.rag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CitacoesTest {

    @Test
    void extraiArtigosEmFormatosVariados() {
        String resposta = "São 30 dias corridos (Art. 130, I). O pagamento vai até 2 dias antes (art. 145). "
                + "A jornada 12x36 está no Art 59-A; ver também Art. 130, II e Art. 611-B, XI.";

        assertThat(Citacoes.extrair(resposta)).containsExactly("130", "145", "59-A", "611-B");
    }

    @Test
    void naoConfundeParagrafoComArtigo() {
        assertThat(Citacoes.extrair("Pode ser dividido em até três períodos (Art. 134, § 1º).")).containsExactly("134");
    }

    @Test
    void respostaSemCitacao() {
        assertThat(Citacoes.extrair(PromptRag.NAO_ENCONTREI)).isEmpty();
        assertThat(Citacoes.extrair(null)).isEmpty();
    }

    @Test
    void ignoraSobrescritoDoNumero() {
        assertThat(Citacoes.extrair("conforme o Art. 1º desta Consolidação")).containsExactly("1");
    }

    @Test
    void reconhecePalavraArtigoPorExtenso() {
        assertThat(Citacoes.extrair("pois o artigo 617 da CLT exige ciência ao sindicato")).containsExactly("617");
    }

    /** Caso real (Q17): o trecho do Art. 484-A cita a Lei do FGTS, e isso não é alucinação. */
    @Test
    void ignoraArtigoDeOutraLei() {
        String resposta = "a indenização prevista no § 1º do art. 18 da Lei nº 8.036, de 1990; "
                + "saque de até 80%, conforme o § 1º do art. 484-A. Ver também art. 7º, XVII, da Constituição.";
        assertThat(Citacoes.extrair(resposta)).containsExactly("484-A");
    }

    /** A letra de um artigo descartado ("20-A da Lei") não pode grudar no artigo seguinte. */
    @Test
    void letraDeArtigoDescartadoNaoVazaParaOSeguinte() {
        assertThat(Citacoes.extrair("na forma do art. 20-A da Lei nº 8.036 e do Art. 477"))
                .containsExactly("477");
    }

    /** Caso real (V23, validação): o Art. 391-A remete ao art. 10 do ADCT, que não é artigo da CLT. */
    @Test
    void ignoraArtigoDoAdct() {
        assertThat(Citacoes.extrair("prevista na alínea b do inciso II do art. 10 do Ato das Disposições "
                + "Constitucionais Transitórias (Art. 391-A)")).containsExactly("391-A");
    }
}
