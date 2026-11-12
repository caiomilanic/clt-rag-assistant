package br.com.caiomilani.cltassistant.busca;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import br.com.caiomilani.cltassistant.ingestao.Granularidade;

/**
 * Encontra os artigos mais relevantes para uma pergunta (D18).
 *
 * <ul>
 *   <li><b>Granularidade</b>: busca entre artigos inteiros ou entre dispositivos (caput/parágrafos).
 *       Em ambos os casos o resultado é por artigo, com o texto do artigo inteiro, porque a citação
 *       e o contexto do LLM são por artigo ("small-to-big").</li>
 *   <li><b>Estratégia</b>: só vetorial, ou híbrida: vetorial + busca textual do Postgres
 *       ({@code to_tsvector('portuguese', …)}), combinadas por Reciprocal Rank Fusion.</li>
 * </ul>
 */
@Service
public class Recuperador {

    /** Constante usual do Reciprocal Rank Fusion: suaviza a vantagem das primeiras posições. */
    static final int RRF_K = 60;

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbc;
    private final String tabela;
    private final Granularidade granularidadePadrao;
    private final EstrategiaBusca estrategiaPadrao;

    public Recuperador(VectorStore vectorStore, JdbcTemplate jdbc,
                       @Value("${spring.ai.vectorstore.pgvector.table-name}") String tabela,
                       @Value("${app.busca.granularidade}") String granularidade,
                       @Value("${app.busca.estrategia}") String estrategia) {
        this.vectorStore = vectorStore;
        this.jdbc = jdbc;
        this.tabela = tabela;
        this.granularidadePadrao = granularidade(granularidade);
        this.estrategiaPadrao = estrategia(estrategia);
    }

    /**
     * @param dispositivo     dispositivo que trouxe o artigo (ex.: "§ 5º"), ou "artigo"
     * @param scoreVetorial   similaridade do melhor candidato do artigo na busca vetorial; null se só veio da textual
     * @param posicaoVetorial posição do artigo na lista vetorial (1 = primeiro); null se não apareceu
     * @param posicaoTextual  posição do artigo na lista textual; null se não apareceu ou se a busca foi só vetorial
     */
    public record ArtigoRecuperado(String referencia, String documento, String contexto, String link,
                                   String texto, String dispositivo, Double scoreVetorial,
                                   Integer posicaoVetorial, Integer posicaoTextual) {
    }

    record Candidato(String referencia, String documento, String contexto, String link,
                     String textoArtigo, String dispositivo, Double score) {
    }

    public Granularidade granularidadePadrao() {
        return granularidadePadrao;
    }

    public EstrategiaBusca estrategiaPadrao() {
        return estrategiaPadrao;
    }

    public List<ArtigoRecuperado> buscar(String pergunta, int k) {
        return buscar(pergunta, k, granularidadePadrao, estrategiaPadrao);
    }

    public List<ArtigoRecuperado> buscar(String pergunta, int k, Granularidade granularidade, EstrategiaBusca estrategia) {
        int candidatos = Math.max(k * 4, 20);
        List<Candidato> vetorial = buscaVetorial(pergunta, candidatos, granularidade);
        List<Candidato> textual = estrategia == EstrategiaBusca.HIBRIDA
                ? buscaTextual(pergunta, candidatos, granularidade)
                : List.of();
        return fundir(vetorial, textual, estrategia, k);
    }

    /** Agrupa os candidatos por artigo e ordena: pela posição vetorial ou pelo RRF das duas listas. */
    static List<ArtigoRecuperado> fundir(List<Candidato> vetorial, List<Candidato> textual,
                                         EstrategiaBusca estrategia, int k) {
        Map<String, Candidato> melhorCandidato = new LinkedHashMap<>();
        Map<String, Integer> posVetorial = posicoesPorArtigo(vetorial, melhorCandidato);
        Map<String, Integer> posTextual = posicoesPorArtigo(textual, melhorCandidato);

        Comparator<String> ordem = estrategia == EstrategiaBusca.VETORIAL
                ? Comparator.comparing(ref -> posVetorial.getOrDefault(ref, Integer.MAX_VALUE))
                : Comparator.comparing((String ref) -> rrf(posVetorial.get(ref), posTextual.get(ref))).reversed();

        return melhorCandidato.keySet().stream()
                .filter(ref -> estrategia == EstrategiaBusca.HIBRIDA || posVetorial.containsKey(ref))
                .sorted(ordem)
                .limit(k)
                .map(ref -> {
                    Candidato c = melhorCandidato.get(ref);
                    Candidato v = vetorial.stream().filter(x -> x.referencia().equals(ref)).findFirst().orElse(null);
                    return new ArtigoRecuperado(c.referencia(), c.documento(), c.contexto(), c.link(),
                            c.textoArtigo(), c.dispositivo(), v == null ? null : v.score(),
                            posVetorial.get(ref), posTextual.get(ref));
                })
                .toList();
    }

    static double rrf(Integer posicaoVetorial, Integer posicaoTextual) {
        return (posicaoVetorial == null ? 0 : 1.0 / (RRF_K + posicaoVetorial))
                + (posicaoTextual == null ? 0 : 1.0 / (RRF_K + posicaoTextual));
    }

    /** Posição de cada artigo (1 = primeiro), contando cada artigo uma vez só, pelo seu melhor candidato. */
    private static Map<String, Integer> posicoesPorArtigo(List<Candidato> lista, Map<String, Candidato> melhorCandidato) {
        Map<String, Integer> posicoes = new LinkedHashMap<>();
        for (Candidato c : lista) {
            if (!posicoes.containsKey(c.referencia())) {
                posicoes.put(c.referencia(), posicoes.size() + 1);
                melhorCandidato.putIfAbsent(c.referencia(), c);
            }
        }
        return posicoes;
    }

    private List<Candidato> buscaVetorial(String pergunta, int n, Granularidade granularidade) {
        List<Document> docs = vectorStore.similaritySearch(SearchRequest.builder()
                .query(pergunta)
                .topK(n)
                .filterExpression("granularidade == '" + granularidade.valor() + "'")
                .build());
        List<Candidato> candidatos = new ArrayList<>();
        for (Document d : docs) {
            Map<String, Object> m = d.getMetadata();
            Object textoArtigo = m.get("texto_artigo");
            candidatos.add(new Candidato(String.valueOf(m.get("referencia")), String.valueOf(m.get("documento")),
                    String.valueOf(m.get("contexto")), String.valueOf(m.get("link")),
                    textoArtigo != null ? textoArtigo.toString() : d.getText(),
                    String.valueOf(m.get("dispositivo")), d.getScore()));
        }
        return candidatos;
    }

    /**
     * Busca textual com o dicionário português do Postgres (stemming e stopwords). Os termos da
     * pergunta entram com OU, para que perguntas em linguagem natural ainda encontrem resultados;
     * o ts_rank_cd favorece quem tem mais termos e mais próximos.
     */
    private List<Candidato> buscaTextual(String pergunta, int n, Granularidade granularidade) {
        String consulta = consultaTextual(pergunta);
        if (consulta.isEmpty()) {
            return List.of();
        }
        String sql = """
                SELECT metadata->>'referencia' AS referencia, metadata->>'documento' AS documento,
                       metadata->>'contexto' AS contexto, metadata->>'link' AS link,
                       metadata->>'texto_artigo' AS texto_artigo, metadata->>'dispositivo' AS dispositivo
                FROM %s, to_tsquery('portuguese', ?) consulta
                WHERE metadata->>'granularidade' = ?
                  AND to_tsvector('portuguese', content) @@ consulta
                ORDER BY ts_rank_cd(to_tsvector('portuguese', content), consulta) DESC
                LIMIT ?
                """.formatted(tabela);
        return jdbc.query(sql, (rs, i) -> new Candidato(rs.getString("referencia"), rs.getString("documento"),
                        rs.getString("contexto"), rs.getString("link"), rs.getString("texto_artigo"),
                        rs.getString("dispositivo"), null),
                consulta, granularidade.valor(), n);
    }

    /** "Posso fazer banco de horas?" -> "posso | fazer | banco | horas". Só letras e números: seguro para o to_tsquery. */
    static String consultaTextual(String pergunta) {
        Set<String> termos = new LinkedHashSet<>();
        for (String t : pergunta.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (t.length() >= 3) {
                termos.add(t);
            }
        }
        return String.join(" | ", termos.stream().limit(20).toList());
    }

    public static Granularidade granularidade(String valor) {
        return valor == null || valor.isBlank() ? null : Granularidade.valueOf(valor.trim().toUpperCase(Locale.ROOT));
    }

    public static EstrategiaBusca estrategia(String valor) {
        return valor == null || valor.isBlank() ? null : EstrategiaBusca.valueOf(valor.trim().toUpperCase(Locale.ROOT));
    }
}
