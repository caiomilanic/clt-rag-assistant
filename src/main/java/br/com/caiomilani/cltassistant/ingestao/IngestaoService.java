package br.com.caiomilani.cltassistant.ingestao;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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

    /**
     * @param trechos      unidades de citação (artigos, no caso da CLT)
     * @param documentos   total gravado no banco: um por trecho + um por dispositivo (D18)
     */
    public record Resultado(String fonte, int trechos, int documentos, long duracaoMs) {
    }

    public Resultado ingerir(String fonte) throws IOException {
        AdaptadorFonte adaptador = adaptadores.stream()
                .filter(a -> a.nome().equalsIgnoreCase(fonte))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Fonte desconhecida: " + fonte));

        long inicio = System.nanoTime();
        List<TrechoNormativo> trechos = adaptador.extrair();
        List<Document> documentos = new ArrayList<>();
        for (TrechoNormativo t : trechos) {
            documentos.addAll(paraDocumentos(t));
        }
        log.info("Ingestão '{}': {} trechos extraídos, {} documentos a gravar", fonte, trechos.size(), documentos.size());

        vectorStore.delete(documentos.stream().map(Document::getId).toList());
        for (int i = 0; i < documentos.size(); i += LOTE) {
            int fim = Math.min(i + LOTE, documentos.size());
            vectorStore.add(documentos.subList(i, fim));
            log.info("Ingestão '{}': {}/{} documentos gravados", fonte, fim, documentos.size());
        }

        long duracaoMs = (System.nanoTime() - inicio) / 1_000_000;
        log.info("Ingestão '{}' concluída em {} ms", fonte, duracaoMs);
        return new Resultado(fonte, trechos.size(), documentos.size(), duracaoMs);
    }

    /**
     * Cada trecho vira dois níveis de documento, separados pelo metadado {@code granularidade}:
     * <ul>
     *   <li>{@code artigo}: o trecho inteiro (um vetor por artigo, como na primeira versão);</li>
     *   <li>{@code dispositivo}: um documento por caput/parágrafo, com o texto do artigo inteiro
     *       guardado em {@code texto_artigo}, para a busca achar pelo parágrafo e o LLM receber o
     *       artigo completo (D18).</li>
     * </ul>
     */
    static List<Document> paraDocumentos(TrechoNormativo t) {
        String cabecalho = cabecalho(t, null);
        String textoArtigo = cabecalho + "\n" + t.texto();

        List<Document> docs = new ArrayList<>();
        docs.add(documento(t.id(), textoArtigo, metadados(t, Granularidade.ARTIGO, "artigo", textoArtigo)));

        List<DivisorDeDispositivos.Dispositivo> dispositivos = DivisorDeDispositivos.dividir(t.texto());
        for (int i = 0; i < dispositivos.size(); i++) {
            DivisorDeDispositivos.Dispositivo d = dispositivos.get(i);
            docs.add(documento(t.id() + "#" + i, cabecalho(t, d.rotulo()) + "\n" + d.texto(),
                    metadados(t, Granularidade.DISPOSITIVO, d.rotulo(), textoArtigo)));
        }
        return docs;
    }

    /**
     * O texto que vira embedding leva um cabeçalho com documento, referência e contexto
     * ("CLT, Art. 59, § 5º | CAPÍTULO II - DA DURAÇÃO DO TRABALHO"), para que perguntas sobre o
     * tema encontrem o trecho mesmo quando ele não repete a palavra-chave.
     */
    private static String cabecalho(TrechoNormativo t, String rotuloDispositivo) {
        String ref = t.referencia()
                + (rotuloDispositivo == null || rotuloDispositivo.equals("caput") ? "" : ", " + rotuloDispositivo);
        return t.documento() + ", " + ref + (t.contexto().isBlank() ? "" : " | " + t.contexto());
    }

    private static Map<String, Object> metadados(TrechoNormativo t, Granularidade granularidade,
                                                 String dispositivo, String textoArtigo) {
        Map<String, Object> m = new HashMap<>();
        m.put("trecho_id", t.id());
        m.put("tipo_fonte", t.tipoFonte().name());
        m.put("documento", t.documento());
        m.put("referencia", t.referencia());
        m.put("contexto", t.contexto());
        m.put("link", t.link());
        m.put("granularidade", granularidade.valor());
        m.put("dispositivo", dispositivo);
        m.put("texto_artigo", textoArtigo);
        return m;
    }

    private static Document documento(String id, String texto, Map<String, Object> metadados) {
        return Document.builder()
                .id(UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8)).toString())
                .text(texto)
                .metadata(metadados)
                .build();
    }
}
