package br.com.caiomilani.cltassistant.ingestao;

import java.io.IOException;
import java.util.List;

/**
 * Converte um documento de origem (HTML da CLT, PDF de convenção coletiva…) em trechos citáveis.
 * Cada tipo de fonte tem o seu adaptador; o resto do pipeline só conhece {@link TrechoNormativo}.
 */
public interface AdaptadorFonte {

    /** Nome usado na API de ingestão, ex.: "clt". */
    String nome();

    List<TrechoNormativo> extrair() throws IOException;
}
