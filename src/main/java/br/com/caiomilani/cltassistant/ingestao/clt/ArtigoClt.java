package br.com.caiomilani.cltassistant.ingestao.clt;

import java.util.List;

/**
 * Um artigo vigente da CLT, já limpo de texto revogado e de notas de alteração.
 *
 * @param numero    identificador do artigo, ex.: "130", "59-A"
 * @param ancora    âncora no HTML do Planalto, para montar o link ("art130", "art59a")
 * @param titulo    título da CLT em que o artigo está, quando identificado
 * @param capitulo  capítulo, quando identificado
 * @param secao     seção, quando identificada
 * @param subtitulo subtítulo sem número que antecede o artigo, ex.: "Trabalho aos domingos"
 * @param texto     texto vigente; uma linha por dispositivo (caput, parágrafos, incisos)
 * @param notas     notas de alteração removidas do texto, ex.: "(Redação dada pela Lei nº 13.467, de 2017)"
 * @param revogado  true quando sobrou só o número do artigo ou "(Revogado)"
 */
public record ArtigoClt(
        String numero,
        String ancora,
        String titulo,
        String capitulo,
        String secao,
        String subtitulo,
        String texto,
        List<String> notas,
        boolean revogado) {
}
