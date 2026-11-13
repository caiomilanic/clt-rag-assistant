package br.com.caiomilani.cltassistant.avaliacao;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import br.com.caiomilani.cltassistant.busca.EstrategiaBusca;
import br.com.caiomilani.cltassistant.busca.Recuperador;
import br.com.caiomilani.cltassistant.ingestao.Granularidade;

/**
 * Mede a recuperação contra o eval set (docs/eval/eval-set.json): para cada pergunta, os
 * artigos esperados aparecem entre os k trechos recuperados? Ainda sem LLM; a avaliação
 * das respostas (citação e fidelidade) entra no passo 6.
 */
@Service
public class AvaliacaoRecuperacaoService {

    private final Recuperador recuperador;
    private final EvalSetRepositorio evalSet;

    public AvaliacaoRecuperacaoService(Recuperador recuperador, EvalSetRepositorio evalSet) {
        this.recuperador = recuperador;
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
    public record Relatorio(String versaoEval, int k, String granularidade, String estrategia,
                            int perguntasAvaliadas, int acertos,
                            double acertoRecuperacao, double mrr, List<ResultadoPergunta> resultados) {
    }

    public Relatorio avaliar(int k, Granularidade granularidade, EstrategiaBusca estrategia) throws IOException {
        return avaliar(k, granularidade, estrategia, EvalSetRepositorio.DESENVOLVIMENTO);
    }

    /** @param conjunto conjunto de perguntas: desenvolvimento ou validacao (ver {@link EvalSetRepositorio}) */
    public Relatorio avaliar(int k, Granularidade granularidade, EstrategiaBusca estrategia, String conjunto)
            throws IOException {
        EvalSet eval = evalSet.carregar(conjunto);

        List<ResultadoPergunta> resultados = new ArrayList<>();
        int avaliadas = 0;
        int acertos = 0;
        double somaReciprocos = 0;

        for (EvalSet.Pergunta p : eval.perguntas()) {
            List<Recuperador.ArtigoRecuperado> docs = recuperador.buscar(p.pergunta(), k, granularidade, estrategia);
            List<String> recuperados = docs.stream()
                    .map(d -> d.referencia().replace("Art. ", ""))
                    .toList();
            List<Integer> posicoes = p.artigosEsperados().stream()
                    .map(a -> recuperados.indexOf(a) + 1)
                    .toList();
            Double scoreTop1 = docs.isEmpty() ? null : docs.get(0).scoreVetorial();

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
        return new Relatorio(eval.versao(), k, granularidade.valor(), estrategia.name().toLowerCase(),
                avaliadas, acertos, arredondar(taxa), arredondar(mrr), resultados);
    }

    private static double arredondar(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
