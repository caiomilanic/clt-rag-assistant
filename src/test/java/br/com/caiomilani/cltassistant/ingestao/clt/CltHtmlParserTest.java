package br.com.caiomilani.cltassistant.ingestao.clt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

/**
 * Cada teste reproduz um problema real encontrado no HTML do Planalto (ver tools/README.md).
 */
class CltHtmlParserTest {

    private final CltHtmlParser parser = new CltHtmlParser();

    private Map<String, ArtigoClt> parse(String corpoHtml) {
        List<ArtigoClt> artigos = parser.parse(Jsoup.parse("<html><body>" + corpoHtml + "</body></html>"));
        return artigos.stream().collect(Collectors.toMap(ArtigoClt::numero, Function.identity()));
    }

    @Test
    void descartaTextoRiscado() {
        var artigos = parse("""
                <p><strike>Art. 130. O direito a férias é adquirido após cada período de doze meses.</strike></p>
                <p><a name="art130"></a>Art. 130 - Após cada período de 12 (doze) meses de vigência do contrato de trabalho, o empregado terá direito a férias, na seguinte proporção:</p>
                <p>I - 30 (trinta) dias corridos, quando não houver faltado ao serviço mais de 5 (cinco) vezes;</p>
                """);

        assertThat(artigos).containsOnlyKeys("130");
        assertThat(artigos.get("130").texto())
                .startsWith("Art. 130 - Após cada período")
                .contains("I - 30 (trinta) dias corridos")
                .doesNotContain("doze meses.");
    }

    @Test
    void removeNotasDoTextoEGuardaComoMetadado() {
        var artigos = parse("""
                <p>Art. 143 - É facultado ao empregado converter 1/3 (um terço) do período de férias. <a href="x">(Redação dada pelo Decreto-lei nº 1.535, de 13.4.1977)</a></p>
                """);

        ArtigoClt art = artigos.get("143");
        assertThat(art.texto()).isEqualTo("Art. 143 - É facultado ao empregado converter 1/3 (um terço) do período de férias.");
        assertThat(art.notas()).containsExactly("(Redação dada pelo Decreto-lei nº 1.535, de 13.4.1977)");
    }

    @Test
    void descartaVersaoDeMedidaProvisoriaQueCaducou() {
        var artigos = parse("""
                <p>Art. 59-A. Em exceção ao disposto no art. 59 desta Consolidação, é facultado às partes, mediante acordo individual escrito, estabelecer horário de doze por trinta e seis. (Incluído pela Lei nº 13.467, de 2017)</p>
                <p>Art. 59-A. Em exceção ao disposto no art. 59 e em leis específicas, é facultado às partes, por meio de convenção coletiva, estabelecer horário de doze por trinta e seis. (Redação dada pela Medida Provisória nº 808, de 2017) (Vigência encerrada)</p>
                """);

        assertThat(artigos.get("59-A").texto()).contains("mediante acordo individual escrito");
    }

    @Test
    void ignoraArtigosDoDecretoLeiDeAprovacao() {
        var artigos = parse("""
                <p>Art. 1º Fica aprovada a Consolidação das Leis do Trabalho, que a este decreto-lei acompanha.</p>
                <p>Art. 2º O presente decreto-lei entrará em vigor em 10 de novembro de 1943.</p>
                <p>Art. 1º - Esta Consolidação estatui as normas que regulam as relações individuais e coletivas de trabalho.</p>
                <p>Art. 2º - Considera-se empregador a empresa, individual ou coletiva.</p>
                """);

        assertThat(artigos.get("1").texto()).contains("Esta Consolidação estatui");
        assertThat(artigos.get("2").texto()).contains("Considera-se empregador");
    }

    @Test
    void naoConfundeHifenSeguidoDePalavraComSufixoDeLetra() {
        var artigos = parse("""
                <p>Art. 58 - A duração normal do trabalho não excederá de 8 (oito) horas diárias.</p>
                <p>Art. 58-A. Considera-se trabalho em regime de tempo parcial aquele cuja duração não exceda a trinta horas semanais.</p>
                """);

        assertThat(artigos).containsOnlyKeys("58", "58-A");
        assertThat(artigos.get("58").texto()).contains("duração normal do trabalho");
    }

    @Test
    void normalizaSobrescritoQuebrado() {
        var artigos = parse("""
                <p>Art. 58 - A duração normal do trabalho não excederá de 8 (oito) horas diárias.</p>
                <p>§ 1<sup><u>o</u></sup> Não serão descontadas as variações de horário no registro de ponto, na forma da Lei no 13.467.</p>
                """);

        assertThat(artigos.get("58").texto())
                .contains("§ 1º Não serão descontadas")
                .contains("Lei nº 13.467");
    }

    @Test
    void descartaSobrasDeDispositivosRevogados() {
        var artigos = parse("""
                <p>Art. 477. Na extinção do contrato de trabalho, o empregador deverá realizar o pagamento das verbas rescisórias.</p>
                <p>§ 1º . (Revogado pela Lei nº 13.467, de 2017)</p>
                <p>I - ; (Revogado)</p>
                <p>§ 6º A entrega ao empregado de documentos deverá ser efetuada até dez dias contados a partir do término do contrato.</p>
                """);

        assertThat(artigos.get("477").texto().split("\n"))
                .containsExactly(
                        "Art. 477. Na extinção do contrato de trabalho, o empregador deverá realizar o pagamento das verbas rescisórias.",
                        "§ 6º A entrega ao empregado de documentos deverá ser efetuada até dez dias contados a partir do término do contrato.");
    }

    @Test
    void reconheceArtigoSemPontoDepoisDeArt() {
        var artigos = parse("""
                <p>Art. 553 - As infrações ao disposto neste Capítulo serão punidas com as seguintes penalidades:</p>
                <p>Art 554. Destituída a administração, o ministro nomeará um delegado para dirigir a associação.</p>
                """);

        assertThat(artigos).containsOnlyKeys("553", "554");
    }

    @Test
    void preencheContextoDeCapituloEUsaAncoraDoArtigo() {
        var artigos = parse("""
                <p>CAPÍTULO IV</p>
                <p>DAS FÉRIAS ANUAIS</p>
                <p><a name="art936"></a><a name="art130"></a>Art. 130 - Após cada período de 12 (doze) meses de vigência do contrato de trabalho, o empregado terá direito a férias.</p>
                """);

        ArtigoClt art = artigos.get("130");
        assertThat(art.capitulo()).isEqualTo("CAPÍTULO IV - DAS FÉRIAS ANUAIS");
        assertThat(art.ancora()).isEqualTo("art130");
    }

    /** Roda contra o arquivo real, quando presente (o teste é pulado se o corpus não estiver no disco). */
    @Test
    void extraiOsArtigosDoCorpusReal() throws IOException {
        Path corpus = Path.of("corpus/clt/DEL5452.html");
        assumeTrue(Files.isRegularFile(corpus), "corpus/clt/DEL5452.html ausente");

        List<ArtigoClt> artigos;
        try (InputStream in = Files.newInputStream(corpus)) {
            artigos = parser.parse(in, CltAdapter.URL_OFICIAL);
        }
        Map<String, ArtigoClt> porNumero = artigos.stream()
                .collect(Collectors.toMap(ArtigoClt::numero, Function.identity()));

        assertThat(artigos).hasSizeBetween(820, 850);
        assertThat(porNumero.get("130").texto())
                .startsWith("Art. 130 - Após cada período de 12 (doze) meses")
                .contains("I - 30 (trinta) dias corridos, quando não houver faltado ao serviço mais de 5 (cinco) vezes;");
        assertThat(porNumero.get("130").capitulo()).isEqualTo("CAPÍTULO IV - DAS FÉRIAS ANUAIS");
        assertThat(porNumero.get("611-A").texto()).contains("III - intervalo intrajornada");
        assertThat(porNumero.get("620").texto()).contains("sempre prevalecerão");
        assertThat(porNumero.get("59-A").texto()).contains("mediante acordo individual escrito");
        assertThat(artigos).allSatisfy(a -> assertThat(a.texto())
                .doesNotContainIgnoringCase("vigência encerrada")
                .doesNotContain("(Redação dada"));
    }
}
