package br.com.caiomilani.cltassistant.avaliacao;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

/**
 * Mede a recuperação contra o eval set (docs/eval/eval-set.json): para cada pergunta, os
 * artigos esperados aparecem entre os k trechos recuperados? Ainda sem LLM; a avaliação
 * das respostas (citação e fidelidade) entra no passo 6.
 */
@Service
public class AvaliacaoRecuperacaoService {

    private final VectorStore vectorStore;
    private final EvalSetRepositorio evalSet;

    public AvaliacaoRecuperacaoService(VectorStore vectorStore, EvalSetRepositorio evalSet) {
        this.vectorStore = vectorStore;
        this.evalSet = evalSet;
    }

    /**
     * @param posicoes     posição (1 = primeiro) de cada artigo esperado entre os recuperados; 0 = não recuperado
     * @param scoreTop1    similaridade do primeiro resultado; nas perguntas fora do escopo ajuda a calibrar
     *                     um limiar de "não encontrei" (passo 8)
     */
    public record ResultadoPergunta(String id, String tipo, String pergunta, List<String> esperados,
                                    List<String> recuperados, List<Integer> posicoes, boolean acerto,
                                    Double scoreTop1) {
    }

    /**
     * @param acertoRecuperacao fração das perguntas (exceto fora do escopo) com todos os artigos esperados no top-k
     * @param mrr               média de 1/posição do primeiro artigo esperado (1.0 = sempre em primeiro)
     */
    public record Relatorio(String versaoEval, int k, int perguntasAvaliadas, int acertos,
                            double acertoRecuperacao, double mrr, List<ResultadoPergunta> resultados) {
    }

    public Relatorio avaliar(int k) throws IOException {
        EvalSet eval = evalSet.carregar();

        List<ResultadoPergunta> resultados = new ArrayList<>();
        int avaliadas = 0;
        int acertos = 0;
        double somaReciprocos = 0;

        for (EvalSet.Pergunta p : eval.perguntas()) {
            List<Document> docs = vectorStore.similaritySearch(
                    SearchRequest.builder().query(p.pergunta()).topK(k).build());
            List<String> recuperados = docs.stream()
                    .map(d -> String.valueOf(d.getMetadata().get("referencia")).replace("Art. ", ""))
                    .toList();
            List<Integer> posicoes = p.artigosEsperados().stream()
                    .map(a -> recuperados.indexOf(a) + 1)
                    .toList();
            Double scoreTop1 = docs.isEmpty() ? null : docs.get(0).getScore();

            boolean foraDoEscopo = p.foraDoEscopo();
            boolean acerto = !foraDoEscopo && !posicoes.isEmpty() && posicoes.stream().allMatch(i -> i > 0);
            if (!foraDoEscopo) {
                avaliadas++;
                if (acerto) {
                    acertos++;
                }
                int primeira = posicoes.stream().filter(i -> i > 0).min(Integer::compare).orElse(0);
                somaReciprocos += primeira == 0 ? 0 : 1.0 / primeira;
            }
            resultados.add(new ResultadoPergunta(p.id(), p.tipo(), p.pergunta(), p.artigosEsperados(),
                    recuperados, posicoes, acerto, scoreTop1));
        }

        double taxa = avaliadas == 0 ? 0 : (double) acertos / avaliadas;
        double mrr = avaliadas == 0 ? 0 : somaReciprocos / avaliadas;
        return new Relatorio(eval.versao(), k, avaliadas, acertos, arredondar(taxa), arredondar(mrr), resultados);
    }

    private static double arredondar(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
