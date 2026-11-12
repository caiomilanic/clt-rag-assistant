package br.com.caiomilani.cltassistant.ingestao;

import java.util.Locale;

/** Nível em que os documentos são indexados e buscados (D18). */
public enum Granularidade {
    /** Um vetor por artigo inteiro. */
    ARTIGO,
    /** Um vetor por caput ou parágrafo; a resposta usa o artigo inteiro. */
    DISPOSITIVO;

    /** Valor gravado no metadado {@code granularidade}. */
    public String valor() {
        return name().toLowerCase(Locale.ROOT);
    }
}
