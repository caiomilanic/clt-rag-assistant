package br.com.caiomilani.cltassistant.rag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import br.com.caiomilani.cltassistant.busca.EstrategiaBusca;
import br.com.caiomilani.cltassistant.busca.Recuperador;
import br.com.caiomilani.cltassistant.ingestao.Granularidade;
import br.com.caiomilani.cltassistant.shared.Disclaimer;

/**
 * Pipeline de RAG explícito (D17): busca os k trechos mais parecidos, monta o prompt com as
 * regras de citação, chama o LLM e confere se os artigos citados estão entre os trechos.
 */
@Service
public class RagService {

    private final Recuperador recuperador;
    private final ChatClient chatClient;
    private final String modelo;

    public RagService(Recuperador recuperador, ChatModel chatModel,
                      @Value("${app.llm.model-label}") String modelo) {
        this.recuperador = recuperador;
        // Cliente próprio, sem o prompt de sistema padrão do endpoint de fumaça.
        this.chatClient = ChatClient.create(chatModel);
        this.modelo = modelo;
    }

    /**
     * @param dispositivo dispositivo que trouxe o artigo na busca (ex.: "§ 5º"), ou "artigo"
     * @param score       similaridade vetorial; null quando o artigo veio só da busca textual
     */
    /** @param texto texto do artigo inteiro, como foi entregue ao modelo (a interface mostra ao clicar na citação) */
    public record Fonte(String referencia, String documento, String contexto, String link,
                        String dispositivo, Double score, String texto) {
    }

    /**
     * @param citados         artigos citados na resposta
     * @param foraDosTrechos  artigos citados que NÃO estavam entre os trechos recuperados:
     *                        sinal de alucinação, que o modelo tirou da própria memória
     * @param problemasDeDispositivo parágrafos ou incisos citados que não existem no artigo recuperado,
     *                        ou cuja frase traz números que não estão naquele dispositivo (D19). Essas
     *                        citações são rebaixadas para o artigo na resposta entregue (D20)
     * @param respostaOriginal texto do modelo antes do rebaixamento; null quando nada foi alterado
     */
    public record Citacao(List<String> citados, List<String> foraDosTrechos,
                          List<VerificadorDeCitacoes.Problema> problemasDeDispositivo, String respostaOriginal) {
    }

    /** @param termosGlossario termos de RH da pergunta que ganharam o vocabulário da lei na busca (D22) */
    public record Metricas(String modelo, long latenciaBuscaMs, long latenciaLlmMs, long latenciaTotalMs,
                           Integer tokensEntrada, Integer tokensSaida, List<String> termosGlossario) {
    }

    public record Resposta(String pergunta, String resposta, boolean encontrou, List<Fonte> fontes,
                           Citacao citacao, Metricas metricas, String aviso) {
    }

    public Resposta perguntar(String pergunta, int k) {
        return perguntar(pergunta, k, recuperador.granularidadePadrao(), recuperador.estrategiaPadrao());
    }

    public Resposta perguntar(String pergunta, int k, Granularidade granularidade, EstrategiaBusca estrategia) {
        return perguntar(pergunta, k, granularidade, estrategia, recuperador.expansaoPadrao());
    }

    public Resposta perguntar(String pergunta, int k, Granularidade granularidade, EstrategiaBusca estrategia,
                              boolean expandir) {
        long inicio = System.nanoTime();
        List<Recuperador.ArtigoRecuperado> trechos = recuperador.buscar(pergunta, k, granularidade, estrategia, expandir);
        long fimBusca = System.nanoTime();

        ChatResponse chat = chatClient.prompt()
                .system(PromptRag.SISTEMA)
                .user(PromptRag.usuario(pergunta, trechos.stream().map(Recuperador.ArtigoRecuperado::texto).toList()))
                .call()
                .chatResponse();
        long fim = System.nanoTime();

        String texto = chat.getResult().getOutput().getText().strip();
        List<Fonte> fontes = trechos.stream().map(RagService::fonte).toList();
        List<String> recuperados = fontes.stream().map(f -> f.referencia().replace("Art. ", "")).toList();
        List<String> citados = Citacoes.extrair(texto);
        List<String> foraDosTrechos = citados.stream().filter(a -> !recuperados.contains(a)).toList();
        Map<String, String> textoPorArtigo = new LinkedHashMap<>();
        trechos.forEach(t -> textoPorArtigo.putIfAbsent(t.referencia().replace("Art. ", ""), t.texto()));
        VerificadorDeCitacoes.Correcao correcao = VerificadorDeCitacoes.corrigir(texto, pergunta, textoPorArtigo);

        Usage uso = chat.getMetadata().getUsage();
        Metricas metricas = new Metricas(modelo,
                (fimBusca - inicio) / 1_000_000,
                (fim - fimBusca) / 1_000_000,
                (fim - inicio) / 1_000_000,
                uso != null ? uso.getPromptTokens() : null,
                uso != null ? uso.getCompletionTokens() : null,
                recuperador.consulta(pergunta, expandir).termos());

        // Recusa = a resposta começa com a frase combinada, ou a contém sem citar nenhum artigo.
        // (O modelo às vezes responde citando artigos e acrescenta a frase de recusa no fim.)
        boolean recusou = texto.startsWith(PromptRag.MARCA_NAO_ENCONTREI)
                || texto.startsWith("\"" + PromptRag.MARCA_NAO_ENCONTREI)
                || (texto.contains(PromptRag.MARCA_NAO_ENCONTREI) && citados.isEmpty());
        boolean encontrou = !recusou;
        // A resposta entregue sai com as citações de dispositivo marcadas rebaixadas para o artigo.
        return new Resposta(pergunta, correcao.resposta(), encontrou, fontes,
                new Citacao(citados, foraDosTrechos, correcao.problemas(), correcao.alterou() ? texto : null),
                metricas, Disclaimer.TEXT);
    }

    private static Fonte fonte(Recuperador.ArtigoRecuperado a) {
        return new Fonte(a.referencia(), a.documento(), a.contexto(), a.link(), a.dispositivo(), a.scoreVetorial(),
                a.texto());
    }
}
