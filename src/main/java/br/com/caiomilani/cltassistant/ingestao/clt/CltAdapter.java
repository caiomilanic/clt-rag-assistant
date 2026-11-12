package br.com.caiomilani.cltassistant.ingestao.clt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import br.com.caiomilani.cltassistant.ingestao.AdaptadorFonte;
import br.com.caiomilani.cltassistant.ingestao.TipoFonte;
import br.com.caiomilani.cltassistant.ingestao.TrechoNormativo;

/** Adaptador da CLT: um trecho por artigo vigente (D7, D13). */
@Component
public class CltAdapter implements AdaptadorFonte {

    public static final String URL_OFICIAL = "https://www.planalto.gov.br/ccivil_03/decreto-lei/del5452.htm";

    private final Path arquivo;
    private final CltHtmlParser parser = new CltHtmlParser();

    public CltAdapter(@Value("${app.corpus.clt}") String arquivo) {
        this.arquivo = Path.of(arquivo);
    }

    @Override
    public String nome() {
        return "clt";
    }

    @Override
    public List<TrechoNormativo> extrair() throws IOException {
        if (!Files.isRegularFile(arquivo)) {
            throw new IllegalStateException("HTML da CLT não encontrado em " + arquivo.toAbsolutePath()
                    + ". Confira a propriedade app.corpus.clt e o diretório de onde a aplicação foi iniciada.");
        }
        try (InputStream in = Files.newInputStream(arquivo)) {
            return parser.parse(in, URL_OFICIAL).stream()
                    .filter(a -> !a.revogado())
                    .map(CltAdapter::paraTrecho)
                    .toList();
        }
    }

    static TrechoNormativo paraTrecho(ArtigoClt a) {
        String contexto = Stream.of(a.titulo(), a.capitulo(), a.secao())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" > "));
        return new TrechoNormativo(
                "clt:art-" + a.numero(),
                TipoFonte.LEI,
                "CLT",
                "Art. " + a.numero(),
                contexto,
                a.texto(),
                URL_OFICIAL + "#" + a.ancora(),
                a.notas());
    }
}
