package br.com.caiomilani.cltassistant.rag;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extrai os artigos da CLT citados numa resposta: "(Art. 130, I)", "art. 59-A", "artigo 617". */
public final class Citacoes {

    /**
     * "Art. 130", "art 59-A", "artigo 617". Referências a outras leis dentro do texto da CLT
     * ("art. 18 da Lei nº 8.036") não contam como citação de artigo da CLT.
     */
    private static final Pattern ARTIGO = Pattern.compile(
            "(?i:art(?:igo)?)\\.?\\s*(\\d{1,4}+)(?:\\s*[º°])?+(?:-([A-Z]))?+"
                    // não pode ser seguido de "da Lei…", "do Decreto…", "da Constituição…",
                    // nem com um parágrafo ou inciso no meio ("art. 7º, XVII, da Constituição")
                    + "(?!\\s*,?\\s*(?:(?:§\\s*\\d+º?|[IVXLC]+)\\s*,?\\s*)?(?i:d[aoe]s?\\s+(?:Lei|Decreto|Medida|Constitui)))");

    private Citacoes() {
    }

    /** Números dos artigos citados, sem repetição, na ordem em que aparecem (ex.: "130", "59-A"). */
    public static List<String> extrair(String texto) {
        if (texto == null) {
            return List.of();
        }
        Set<String> artigos = new LinkedHashSet<>();
        Matcher m = ARTIGO.matcher(texto);
        while (m.find()) {
            artigos.add(m.group(2) == null ? m.group(1) : m.group(1) + "-" + m.group(2));
        }
        return List.copyOf(artigos);
    }
}
