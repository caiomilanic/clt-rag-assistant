package br.com.caiomilani.cltassistant.ingestao;

import java.util.List;

/**
 * Unidade de citação, comum a todas as fontes: um artigo da CLT, uma cláusula de convenção,
 * uma seção de política interna. Cada trecho vira um chunk no banco vetorial.
 *
 * @param id         identificador estável e legível, ex.: "clt:art-130"
 * @param tipoFonte  lei, convenção, acordo ou política
 * @param documento  nome curto do documento, ex.: "CLT"
 * @param referencia como o trecho é citado, ex.: "Art. 130"
 * @param contexto   localização dentro do documento, ex.: "TÍTULO II > CAPÍTULO IV - DAS FÉRIAS ANUAIS"; pode ser vazio
 * @param texto      texto vigente do trecho
 * @param link       URL do texto oficial, apontando para o trecho quando possível
 * @param notas      notas de alteração legislativa, fora do texto
 */
public record TrechoNormativo(
        String id,
        TipoFonte tipoFonte,
        String documento,
        String referencia,
        String contexto,
        String texto,
        String link,
        List<String> notas) {
}
