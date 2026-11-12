package br.com.caiomilani.cltassistant.rag;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import br.com.caiomilani.cltassistant.shared.Disclaimer;

/**
 * Pipeline de RAG explícito (D17): busca os k trechos mais parecidos, monta o prompt com as
 * regras de citação, chama o LLM e confere se os artigos citados estão entre os trechos.
 */
@Service
public class RagService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final String modelo;

    public RagService(VectorStore vectorStore, ChatModel chatModel,
                      @Value("${app.llm.model-label}") String modelo) {
        this.vectorStore = vectorStore;
        // Cliente próprio, sem o prompt de sistema padrão do endpoint de fumaça.
        this.chatClient = ChatClient.create(chatModel);
        this.modelo = modelo;
    }

    public record Fonte(String referencia, String documento, String contexto, String link, Double score) {
    }

    /**
     * @param citados         artigos citados na resposta
     * @param foraDosTrechos  artigos citados que NÃO estavam entre os trechos recuperados:
     *                        sinal de alucinação, que o modelo tirou da própria memória
     */
    public record Citacao(List<String> citados, List<String> foraDosTrechos) {
    }

    public record Metricas(String modelo, long latenciaBuscaMs, long latenciaLlmMs, long latenciaTotalMs,
                           Integer tokensEntrada, Integer tokensSaida) {
    }

    public record Resposta(String pergunta, String resposta, boolean encontrou, List<Fonte> fontes,
                           Citacao citacao, Metricas metricas, String aviso) {
    }

    public Resposta perguntar(String pergunta, int k) {
        long inicio = System.nanoTime();
        List<Document> trechos = vectorStore.similaritySearch(
                SearchRequest.builder().query(pergunta).topK(k).build());
        long fimBusca = System.nanoTime();

        ChatResponse chat = chatClient.prompt()
                .system(PromptRag.SISTEMA)
                .user(PromptRag.usuario(pergunta, trechos))
                .call()
                .chatResponse();
        long fim = System.nanoTime();

        String texto = chat.getResult().getOutput().getText().strip();
        List<Fonte> fontes = trechos.stream().map(RagService::fonte).toList();
        List<String> recuperados = fontes.stream().map(f -> f.referencia().replace("Art. ", "")).toList();
        List<String> citados = Citacoes.extrair(texto);
        List<String> foraDosTrechos = citados.stream().filter(a -> !recuperados.contains(a)).toList();

        Usage uso = chat.getMetadata().getUsage();
        Metricas metricas = new Metricas(modelo,
                (fimBusca - inicio) / 1_000_000,
                (fim - fimBusca) / 1_000_000,
                (fim - inicio) / 1_000_000,
                uso != null ? uso.getPromptTokens() : null,
                uso != null ? uso.getCompletionTokens() : null);

        // Recusa = a resposta começa com a frase combinada, ou a contém sem citar nenhum artigo.
        // (O modelo às vezes responde citando artigos e acrescenta a frase de recusa no fim.)
        boolean recusou = texto.startsWith(PromptRag.MARCA_NAO_ENCONTREI)
                || texto.startsWith("\"" + PromptRag.MARCA_NAO_ENCONTREI)
                || (texto.contains(PromptRag.MARCA_NAO_ENCONTREI) && citados.isEmpty());
        boolean encontrou = !recusou;
        return new Resposta(pergunta, texto, encontrou, fontes,
                new Citacao(citados, foraDosTrechos), metricas, Disclaimer.TEXT);
    }

    private static Fonte fonte(Document d) {
        Map<String, Object> m = d.getMetadata();
        return new Fonte(String.valueOf(m.get("referencia")), String.valueOf(m.get("documento")),
                String.valueOf(m.get("contexto")), String.valueOf(m.get("link")), d.getScore());
    }
}
