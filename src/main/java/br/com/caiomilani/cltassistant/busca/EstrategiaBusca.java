package br.com.caiomilani.cltassistant.busca;

/** Como os candidatos são encontrados (D18). */
public enum EstrategiaBusca {
    /** Só similaridade de embeddings (pgvector). */
    VETORIAL,
    /** Embeddings + busca textual do Postgres, combinadas por Reciprocal Rank Fusion. */
    HIBRIDA
}
