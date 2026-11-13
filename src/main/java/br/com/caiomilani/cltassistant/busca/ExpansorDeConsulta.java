package br.com.caiomilani.cltassistant.busca;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/**
 * Traduz o jargão do RH para o vocabulário da lei antes da busca (D22).
 *
 * <p>A pergunta "Posso adotar a escala 12x36?" não chega, por similaridade de embeddings, ao texto
 * "doze horas seguidas por trinta e seis horas ininterruptas de descanso" (Art. 59-A). Quando a pergunta
 * contém um termo do glossário, a expansão correspondente é acrescentada à consulta da busca. O LLM
 * continua recebendo a pergunta original.
 *
 * <p>O glossário é um arquivo JSON fora do código ({@code app.busca.glossario}), para que cada empresa
 * acrescente os próprios termos sem recompilar. Sem o arquivo, a busca funciona sem expansão.
 */
@Component
public class ExpansorDeConsulta {

    private static final Logger log = LoggerFactory.getLogger(ExpansorDeConsulta.class);

    /** @param origem de onde veio a entrada (ex.: a pergunta de desenvolvimento que a motivou) */
    public record Entrada(List<String> termos, String expansao, String origem) {
    }

    public record Glossario(String descricao, List<Entrada> entradas) {
    }

    /**
     * @param consulta texto usado na busca: a pergunta, mais as expansões, se houver
     * @param termos   termos do glossário encontrados na pergunta (vazio = nada expandido)
     */
    public record Expansao(String consulta, List<String> termos) {

        public boolean expandiu() {
            return !termos.isEmpty();
        }
    }

    private record Regra(List<Pattern> padroes, List<String> termos, String expansao) {
    }

    private final List<Regra> regras;

    public ExpansorDeConsulta(@Value("${app.busca.glossario}") String arquivo) {
        this(carregar(Path.of(arquivo)));
    }

    ExpansorDeConsulta(Glossario glossario) {
        List<Regra> r = new ArrayList<>();
        for (Entrada e : glossario.entradas()) {
            List<Pattern> padroes = e.termos().stream()
                    .map(t -> Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(normalizar(t)) + "(?![\\p{L}\\p{N}])"))
                    .toList();
            r.add(new Regra(padroes, e.termos(), e.expansao()));
        }
        this.regras = List.copyOf(r);
    }

    public Expansao expandir(String pergunta) {
        String normalizada = normalizar(pergunta);
        Set<String> termos = new LinkedHashSet<>();
        Set<String> expansoes = new LinkedHashSet<>();
        for (Regra regra : regras) {
            for (int i = 0; i < regra.padroes().size(); i++) {
                if (regra.padroes().get(i).matcher(normalizada).find()) {
                    termos.add(regra.termos().get(i));
                    expansoes.add(regra.expansao());
                }
            }
        }
        if (expansoes.isEmpty()) {
            return new Expansao(pergunta, List.of());
        }
        return new Expansao(pergunta + "\n" + String.join(". ", expansoes) + ".", List.copyOf(termos));
    }

    public int tamanho() {
        return regras.size();
    }

    /** Minúsculas e sem acentos, para "Escritório" casar com "escritorio". */
    static String normalizar(String texto) {
        return Normalizer.normalize(texto.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private static Glossario carregar(Path arquivo) {
        if (!Files.exists(arquivo)) {
            log.warn("Glossário {} não encontrado: a busca roda sem expansão de consulta.", arquivo.toAbsolutePath());
            return new Glossario("", List.of());
        }
        try (InputStream in = Files.newInputStream(arquivo)) {
            Glossario g = JsonMapper.builder().build().readValue(in, Glossario.class);
            log.info("Glossário carregado de {}: {} entradas.", arquivo, g.entradas().size());
            return g;
        } catch (IOException e) {
            throw new IllegalStateException("Glossário inválido: " + arquivo, e);
        }
    }
}
