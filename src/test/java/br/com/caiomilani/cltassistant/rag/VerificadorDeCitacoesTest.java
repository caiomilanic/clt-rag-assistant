package br.com.caiomilani.cltassistant.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Casos tirados das respostas reais da avaliação de 2026-11-12 (docs/eval/resultados). */
class VerificadorDeCitacoesTest {

    private static final String ART_130 = """
            CLT, Art. 130 | Título II > Capítulo IV
            Art. 130 - Após cada período de 12 (doze) meses de vigência do contrato de trabalho, o empregado terá direito a férias, na seguinte proporção:
            I - 30 (trinta) dias corridos, quando não houver faltado ao serviço mais de 5 (cinco) vezes;
            II - 24 (vinte e quatro) dias corridos, quando houver tido de 6 (seis) a 14 (quatorze) faltas;
            III - 18 (dezoito) dias corridos, quando houver tido de 15 (quinze) a 23 (vinte e três) faltas;
            IV - 12 (doze) dias corridos, quando houver tido de 24 (vinte e quatro) a 32 (trinta e duas) faltas.
            § 1º - É vedado descontar, do período de férias, as faltas do empregado ao serviço.
            § 2º - O período das férias será computado, para todos os efeitos, como tempo de serviço.""";

    private static final String ART_145 = """
            CLT, Art. 145
            Art. 145 - O pagamento da remuneração das férias e, se for o caso, o do abono referido no art. 143 serão efetuados até 2 (dois) dias antes do início do respectivo período.
            Parágrafo único - O empregado dará quitação do pagamento, com indicação do início e do termo das férias.""";

    private static final String ART_71 = """
            CLT, Art. 71
            Art. 71 - Em qualquer trabalho contínuo, cuja duração exceda de 6 (seis) horas, é obrigatória a concessão de um intervalo para repouso ou alimentação, o qual será, no mínimo, de 1 (uma) hora e, salvo acordo escrito ou contrato coletivo em contrário, não poderá exceder de 2 (duas) horas.
            § 1º - Não excedendo de 6 (seis) horas o trabalho, será, entretanto, obrigatório um intervalo de 15 (quinze) minutos quando a duração ultrapassar 4 (quatro) horas.
            § 4º A não concessão ou a concessão parcial do intervalo intrajornada mínimo, para repouso e alimentação, a empregados urbanos e rurais, implica o pagamento, de natureza indenizatória, apenas do período suprimido, com acréscimo de 50% (cinquenta por cento) sobre o valor da remuneração da hora normal de trabalho.""";

    private static final String ART_611_A = """
            CLT, Art. 611-A
            Art. 611-A. A convenção coletiva e o acordo coletivo de trabalho têm prevalência sobre a lei quando, entre outros, dispuserem sobre:
            I - pacto quanto à jornada de trabalho, observados os limites constitucionais;
            II - banco de horas anual;
            III - intervalo intrajornada, respeitado o limite mínimo de trinta minutos para jornadas superiores a seis horas;""";

    @Test
    void extraiCitacoesComParagrafoEInciso() {
        List<VerificadorDeCitacoes.Citacao> c = VerificadorDeCitacoes.extrair(
                "Texto (Art. 134, § 1º), depois (Art. 487, I e II), o § 6º do Art. 477 e (Art. 59-B, parágrafo único).");

        assertThat(c).extracting(VerificadorDeCitacoes.Citacao::artigo).containsExactly("134", "487", "477", "59-B");
        assertThat(c).extracting(VerificadorDeCitacoes.Citacao::dispositivos).containsExactly(
                List.of("§ 1º"), List.of("I", "II"), List.of("§ 6º"), List.of("Parágrafo único"));
    }

    @Test
    void ignoraCitacaoSemDispositivoEParagrafoDeOutraLei() {
        assertThat(VerificadorDeCitacoes.extrair("Férias de 30 dias (Art. 130)."))
                .isEmpty();
        assertThat(VerificadorDeCitacoes.extrair("conforme o § 1º do art. 18 da Lei nº 8.036"))
                .isEmpty();
    }

    @Test
    void paragrafoComNumeroRomanoViraInciso() {
        // Q12: o modelo escreveu "(Art. 611-A, § III)"
        assertThat(VerificadorDeCitacoes.extrair("(Art. 611-A, § III)").get(0).dispositivos())
                .containsExactly("III");
    }

    @Test
    void q02NumeroQueEstaEmOutroInciso() {
        String resposta = "O funcionário tem 24 dias corridos de férias, pois houve de 6 a 14 faltas (Art. 130, I).";

        List<VerificadorDeCitacoes.Problema> p = VerificadorDeCitacoes.verificar(resposta,
                "Um funcionário completou 12 meses de casa com 8 faltas injustificadas no período.",
                Map.of("130", ART_130));

        assertThat(p).singleElement().satisfies(x -> {
            assertThat(x.tipo()).isEqualTo("numero_ausente");
            assertThat(x.citacao()).isEqualTo("Art. 130, I");
            assertThat(x.detalhe()).contains("24");
        });
    }

    @Test
    void q01IncisoCertoNaoGeraProblema() {
        String resposta = "O trabalhador tem direito a férias de 30 dias corridos por ano, quando não houver "
                + "faltado ao serviço mais de 5 vezes, conforme o Art. 130, I.";

        assertThat(VerificadorDeCitacoes.verificar(resposta, Map.of("130", ART_130))).isEmpty();
    }

    @Test
    void q05IncisoInexistente() {
        String resposta = "O pagamento das férias deve ser feito até 2 dias antes do início (Art. 145, I).";

        assertThat(VerificadorDeCitacoes.verificar(resposta, Map.of("145", ART_145)))
                .singleElement()
                .extracting(VerificadorDeCitacoes.Problema::tipo)
                .isEqualTo("dispositivo_inexistente");
    }

    @Test
    void numeroPorExtensoNaLeiConta() {
        // Q12: "30 minutos" na resposta, "trinta minutos" no inciso III
        String resposta = "Sim, desde que respeitado o limite mínimo de 30 minutos para jornadas superiores "
                + "a seis horas (Art. 611-A, III).";

        assertThat(VerificadorDeCitacoes.verificar(resposta, Map.of("611-A", ART_611_A))).isEmpty();
    }

    @Test
    void percentualNoParagrafoCerto() {
        // Q13
        String resposta = "A empresa paga o período suprimido com acréscimo de 50% sobre a hora normal (Art. 71, § 4º).";

        assertThat(VerificadorDeCitacoes.verificar(resposta, Map.of("71", ART_71))).isEmpty();
    }

    @Test
    void citacaoNoInicioDaFraseUsaORestoDaFrase() {
        // Q11: a citação abre a frase; "8 horas" vem da pergunta
        String resposta = "O intervalo para quem trabalha 8 horas não é especificado. (Art. 71, § 1º) menciona "
                + "um intervalo de 15 minutos quando a duração excede 4 horas.";

        assertThat(VerificadorDeCitacoes.verificar(resposta,
                "Qual o intervalo mínimo de almoço para quem trabalha 8 horas por dia?", Map.of("71", ART_71)))
                .isEmpty();
    }

    @Test
    void artigoNaoRecuperadoEhIgnorado() {
        // já aparece em foraDosTrechos; o verificador não tem o texto para conferir
        assertThat(VerificadorDeCitacoes.verificar("Prazo de 10 dias (Art. 477, § 6º).", Map.of("130", ART_130)))
                .isEmpty();
    }

    @Test
    void letraDeArtigoNaoVazaParaACitacaoSeguinte() {
        // Q10, rodada 6b: "(Art. 59-B)" sem dispositivo, seguido de "(Art. 59, § 6º)", virava "59-B, § 6º"
        String resposta = "Não exige o sindicato (Art. 59-B). Compensação no mesmo mês (Art. 59, § 6º).";

        assertThat(VerificadorDeCitacoes.extrair(resposta))
                .extracting(VerificadorDeCitacoes.Citacao::artigo)
                .containsExactly("59");
    }

    @Test
    void numeroPorExtensoNaRespostaTambemConta() {
        // Q09, rodada 6b: "duas horas" está no caput do Art. 59; o § 1º fala de 50%
        String art59 = """
                CLT, Art. 59
                Art. 59. A duração diária do trabalho poderá ser acrescida de horas extras, em número não excedente de duas, por acordo individual, convenção coletiva ou acordo coletivo de trabalho.
                § 1º A remuneração da hora extra será, pelo menos, 50% (cinquenta por cento) superior à da hora normal.""";
        String resposta = "Um funcionário pode fazer até duas horas extras por dia, conforme o Art. 59, § 1º.";

        assertThat(VerificadorDeCitacoes.verificar(resposta, "Quantas horas extras por dia um funcionário pode fazer?",
                Map.of("59", art59)))
                .singleElement()
                .satisfies(p -> assertThat(p.detalhe()).contains("2"));
    }

    @Test
    void corrigirRebaixaCitacaoMarcadaParaOArtigo() {
        // Q02, rodada anterior: o 24 está no inciso II, não no I
        String resposta = "O funcionário tem 24 dias corridos de férias, pois houve de 6 a 14 faltas (Art. 130, I).";

        VerificadorDeCitacoes.Correcao c = VerificadorDeCitacoes.corrigir(resposta, "", Map.of("130", ART_130));

        assertThat(c.resposta()).isEqualTo(
                "O funcionário tem 24 dias corridos de férias, pois houve de 6 a 14 faltas (Art. 130).");
        assertThat(c.alterou()).isEqualTo(true);
    }

    @Test
    void corrigirLevaJuntoOIncisoQueVemDepoisDoParagrafo() {
        // Q12, rodada 6b: "Art. 611-A, § 3º, III", com o § 3º errado
        String art611A = ART_611_A + "\n§ 3º Se for pactuada cláusula que reduza o salário ou a jornada, a convenção "
                + "coletiva ou o acordo coletivo de trabalho deverão prever a proteção dos empregados contra dispensa imotivada.";
        String resposta = "Sim, desde que respeite o limite mínimo de trinta minutos para jornadas superiores a seis horas, "
                + "conforme estabelecido no Art. 611-A, § 3º, III. Outra frase.";

        VerificadorDeCitacoes.Correcao c = VerificadorDeCitacoes.corrigir(resposta,
                "A convenção coletiva da categoria pode reduzir o intervalo de almoço para 30 minutos?",
                Map.of("611-A", art611A));

        assertThat(c.resposta()).isEqualTo("Sim, desde que respeite o limite mínimo de trinta minutos para jornadas "
                + "superiores a seis horas, conforme estabelecido no Art. 611-A. Outra frase.");
    }

    @Test
    void corrigirParagrafoAntesDoArtigo() {
        String resposta = "O prazo é de 10 dias, conforme o § 2º do Art. 145.";

        assertThat(VerificadorDeCitacoes.corrigir(resposta, "", Map.of("145", ART_145)).resposta())
                .isEqualTo("O prazo é de 10 dias, conforme o Art. 145.");
    }

    @Test
    void corrigirNaoMexeNaCitacaoCerta() {
        String resposta = "Até 2 dias antes (Art. 145). Paga o período com acréscimo de 50% (Art. 71, § 4º).";

        VerificadorDeCitacoes.Correcao c = VerificadorDeCitacoes.corrigir(resposta, "",
                Map.of("145", ART_145, "71", ART_71));

        assertThat(c.resposta()).isEqualTo(resposta);
        assertThat(c.alterou()).isEqualTo(false);
    }

    @Test
    void porExtenso() {
        assertThat(VerificadorDeCitacoes.porExtenso(24)).containsExactly("vinte e quatro");
        assertThat(VerificadorDeCitacoes.porExtenso(2)).containsExactly("dois", "duas");
        assertThat(VerificadorDeCitacoes.porExtenso(50)).containsExactly("cinquenta", "cinqüenta");
    }
}
