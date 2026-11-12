package br.com.caiomilani.cltassistant.avaliacao;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import br.com.caiomilani.cltassistant.busca.EstrategiaBusca;
import br.com.caiomilani.cltassistant.ingestao.Granularidade;
import br.com.caiomilani.cltassistant.rag.RagService;
import br.com.caiomilani.cltassistant.rag.VerificadorDeCitacoes;

/**
 * Roda o eval set pelo RAG completo e mede o que dá para medir automaticamente:
 * <ul>
 *   <li><b>citação</b>: a resposta cita ao menos um artigo esperado; nas perguntas fora do escopo,
 *       a resposta recusa sem citar artigo;</li>
 *   <li><b>citações fora dos trechos</b>: artigos citados que não foram recuperados (vieram da memória
 *       do modelo);</li>
 *   <li><b>latência</b> e <b>tokens</b>.</li>
 * </ul>
 * A fidelidade (o conteúdo bate com o trecho?) é revisada à mão a partir das respostas devolvidas.
 */
@Service
public class AvaliacaoRespostasService {

    private final RagService rag;
    private final EvalSetRepositorio evalSet;

    public AvaliacaoRespostasService(RagService rag, EvalSetRepositorio evalSet) {
        this.rag = rag;
        this.evalSet = evalSet;
    }

    public record ResultadoPergunta(String id, String tipo, String pergunta, List<String> esperados,
                                    List<String> recuperados, List<String> citados, List<String> citadosForaDosTrechos,
                                    List<VerificadorDeCitacoes.Problema> problemasDeDispositivo,
                                    boolean encontrou, boolean acertoCitacao, long latenciaMs, Integer tokensSaida,
                                    String resposta, String respostaOriginal) {
    }

    /**
     * @param citacao                fração de perguntas com acerto de citação (meta: 1.0)
     * @param respostasComCitacaoForaDosTrechos quantas respostas citaram artigo não recuperado (meta: 0)
     * @param respostasComProblemaDeDispositivo quantas respostas citaram § ou inciso inexistente, ou com
     *                               número que não está no dispositivo citado (meta: 0; D19). Na resposta
     *                               entregue essas citações já saem rebaixadas para o artigo (D20)
     * @param recusasCorretas        perguntas fora do escopo em que o sistema recusou sem citar
     * @param recusasIndevidas       perguntas válidas em que o sistema disse "não encontrei"
     */
    public record Relatorio(String versaoEval, int k, String granularidade, String estrategia, int perguntas,
                            double citacao,
                            int respostasComCitacaoForaDosTrechos, int respostasComProblemaDeDispositivo,
                            String recusasCorretas, int recusasIndevidas,
                            long latenciaMediaMs, long latenciaMaximaMs, List<ResultadoPergunta> resultados) {
    }

    public Relatorio avaliar(int k, Granularidade granularidade, EstrategiaBusca estrategia) throws IOException {
        EvalSet eval = evalSet.carregar();
        List<ResultadoPergunta> resultados = new ArrayList<>();

        for (EvalSet.Pergunta p : eval.perguntas()) {
            RagService.Resposta r = rag.perguntar(p.pergunta(), k, granularidade, estrategia);
            List<String> citados = r.citacao().citados();
            boolean acerto = p.foraDoEscopo()
                    ? !r.encontrou() && citados.isEmpty()
                    : p.artigosEsperados().stream().anyMatch(citados::contains);
            resultados.add(new ResultadoPergunta(p.id(), p.tipo(), p.pergunta(), p.artigosEsperados(),
                    r.fontes().stream().map(f -> f.referencia().replace("Art. ", "")).toList(),
                    citados, r.citacao().foraDosTrechos(), r.citacao().problemasDeDispositivo(), r.encontrou(), acerto,
                    r.metricas().latenciaTotalMs(), r.metricas().tokensSaida(), r.resposta(),
                    r.citacao().respostaOriginal()));
        }

        int total = resultados.size();
        long acertos = resultados.stream().filter(ResultadoPergunta::acertoCitacao).count();
        int comCitacaoFora = (int) resultados.stream().filter(x -> !x.citadosForaDosTrechos().isEmpty()).count();
        int comProblemaDeDispositivo = (int) resultados.stream()
                .filter(x -> !x.problemasDeDispositivo().isEmpty()).count();
        long foraDoEscopo = resultados.stream().filter(x -> EvalSet.FORA_DO_ESCOPO.equals(x.tipo())).count();
        long recusasCorretas = resultados.stream()
                .filter(x -> EvalSet.FORA_DO_ESCOPO.equals(x.tipo()) && x.acertoCitacao()).count();
        int recusasIndevidas = (int) resultados.stream()
                .filter(x -> !EvalSet.FORA_DO_ESCOPO.equals(x.tipo()) && !x.encontrou()).count();
        long latenciaMedia = total == 0 ? 0
                : (long) resultados.stream().mapToLong(ResultadoPergunta::latenciaMs).average().orElse(0);
        long latenciaMaxima = resultados.stream().map(ResultadoPergunta::latenciaMs)
                .max(Comparator.naturalOrder()).orElse(0L);

        return new Relatorio(eval.versao(), k, granularidade.valor(), estrategia.name().toLowerCase(), total,
                total == 0 ? 0 : Math.round(1000.0 * acertos / total) / 1000.0,
                comCitacaoFora, comProblemaDeDispositivo, recusasCorretas + "/" + foraDoEscopo, recusasIndevidas,
                latenciaMedia, latenciaMaxima, resultados);
    }
}
