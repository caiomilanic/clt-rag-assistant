package br.com.caiomilani.cltassistant.ingestao;

/**
 * Tipos de fonte normativa, na ordem da hierarquia trabalhista (ver D10 em docs/decisoes.md).
 * Na camada 1 só existe {@link #LEI} (a CLT); os demais entram nas camadas 2 e 3.
 */
public enum TipoFonte {
    LEI,
    CONVENCAO_COLETIVA,
    ACORDO_COLETIVO,
    POLITICA_INTERNA
}
