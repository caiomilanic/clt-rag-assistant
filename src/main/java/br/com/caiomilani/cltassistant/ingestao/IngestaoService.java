package br.com.caiomilani.cltassistant.ingestao;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

/**
 * Leva os trechos de um adaptador para o banco vetorial: gera o embedding de cada trecho
 * (bge-m3 no Ollama, no perfil offline) e grava no pgvector.
 *
 * <p>Pode ser rodada de novo sem duplicar nada: os ids dos documentos são derivados do id do
 * trecho, e os existentes são apagados antes de regravar.
 */
@Service
public class IngestaoService {

    private static final Logger log = LoggerFactory.getLogger(IngestaoService.class);

    /** Trechos por chamada ao modelo de embeddings. */
    private static final int LOTE = 32;

    private final VectorStore vectorStore;
    private final List<AdaptadorFonte> adaptadores;

    public IngestaoService(VectorStore vectorStore, List<AdaptadorFonte> adaptadores) {
        this.vectorStore = vectorStore;
        this.adaptadores = adaptadores;
    }

    public record Resultado(String fonte, int trechos, long duracaoMs) {
    }

    public Resultado ingerir(String fonte) throws IOException {
        AdaptadorFonte adaptador = adaptadores.stream()
                .filter(a -> a.nome().equalsIgnoreCase(fonte))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Fonte desconhecida: " + fonte));

        long inicio = System.nanoTime();
        List<Document> documentos = adaptador.extrair().stream().map(IngestaoService::paraDocumento).toList();
        log.info("Ingestão '{}': {} trechos extraídos", fonte, documentos.size());

        vectorStore.delete(documentos.stream().map(Document::getId).toList());
        for (int i = 0; i < documentos.size(); i += LOTE) {
            int fim = Math.min(i + LOTE, documentos.size());
            vectorStore.add(documentos.subList(i, fim));
            log.info("Ingestão '{}': {}/{} trechos gravados", fonte, fim, documentos.size());
        }

        long duracaoMs = (System.nanoTime() - inicio) / 1_000_000;
        log.info("Ingestão '{}' concluída em {} ms", fonte, duracaoMs);
        return new Resultado(fonte, documentos.size(), duracaoMs);
    }

    /**
     * O texto que vira embedding leva um cabeçalho com documento, referência e contexto
     * ("CLT, Art. 130 | CAPÍTULO IV - DAS FÉRIAS ANUAIS"), para que perguntas sobre o tema
     * encontrem o artigo mesmo quando o caput não repete a palavra-chave.
     */
    static Document paraDocumento(TrechoNormativo t) {
        String cabecalho = t.documento() + ", " + t.referencia()
                + (t.contexto().isBlank() ? "" : " | " + t.contexto());

        Map<String, Object> metadados = new HashMap<>();
        metadados.put("trecho_id", t.id());
        metadados.put("tipo_fonte", t.tipoFonte().name());
        metadados.put("documento", t.documento());
        metadados.put("referencia", t.referencia());
        metadados.put("contexto", t.contexto());
        metadados.put("link", t.link());

        return Document.builder()
                .id(UUID.nameUUIDFromBytes(t.id().getBytes(StandardCharsets.UTF_8)).toString())
                .text(cabecalho + "\n" + t.texto())
                .metadata(metadados)
                .build();
    }
}
