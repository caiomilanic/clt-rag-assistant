package br.com.caiomilani.cltassistant.busca;

import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Busca semântica pura, sem LLM: devolve os trechos mais parecidos com a pergunta.
 * Serve para inspecionar e medir a recuperação (eval) antes e depois do RAG.
 */
@RestController
@RequestMapping("/api/busca")
@Validated
public class BuscaController {

    private final VectorStore vectorStore;

    public BuscaController(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public record Resultado(String referencia, String documento, String contexto, String link,
                            Double score, String trecho) {
    }

    @GetMapping
    public List<Resultado> buscar(@RequestParam @NotBlank String q,
                                  @RequestParam(defaultValue = "5") @Min(1) @Max(20) int k) {
        List<Document> documentos = vectorStore.similaritySearch(
                SearchRequest.builder().query(q).topK(k).build());
        return documentos.stream().map(BuscaController::paraResultado).toList();
    }

    private static Resultado paraResultado(Document d) {
        Map<String, Object> m = d.getMetadata();
        String texto = d.getText() == null ? "" : d.getText();
        return new Resultado(
                String.valueOf(m.get("referencia")),
                String.valueOf(m.get("documento")),
                String.valueOf(m.get("contexto")),
                String.valueOf(m.get("link")),
                d.getScore(),
                texto.length() > 400 ? texto.substring(0, 400) + "…" : texto);
    }
}
