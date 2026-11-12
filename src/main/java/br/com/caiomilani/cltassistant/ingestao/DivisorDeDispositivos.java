package br.com.caiomilani.cltassistant.ingestao;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Divide o texto de um artigo de lei em dispositivos: o caput (com seus incisos e alíneas) e
 * cada parágrafo (com os seus). Motivação: na primeira avaliação, as duas perguntas que a busca
 * errou tinham a resposta num parágrafo cujo caput trata de outro assunto (Art. 59, § 5º e
 * Art. 58, § 1º); com um vetor por artigo inteiro, o tema do parágrafo fica diluído (D18).
 */
public final class DivisorDeDispositivos {

    private static final Pattern PARAGRAFO = Pattern.compile("^(§\\s*\\d+º?|Par[áa]grafo [úu]nico)", Pattern.CASE_INSENSITIVE);

    public record Dispositivo(String rotulo, String texto) {
    }

    private DivisorDeDispositivos() {
    }

    /** O texto vem com um dispositivo por linha, como produzido pelo parser da CLT. */
    public static List<Dispositivo> dividir(String textoDoArtigo) {
        List<Dispositivo> dispositivos = new ArrayList<>();
        String rotulo = "caput";
        StringBuilder atual = new StringBuilder();
        for (String linha : textoDoArtigo.split("\n")) {
            Matcher m = PARAGRAFO.matcher(linha);
            boolean novoParagrafo = m.lookingAt();
            if (novoParagrafo && !atual.isEmpty()) {
                dispositivos.add(new Dispositivo(rotulo, atual.toString()));
                atual.setLength(0);
            }
            if (novoParagrafo) {
                rotulo = m.group(1).replaceAll("\\s+", " ");
            }
            if (!atual.isEmpty()) {
                atual.append('\n');
            }
            atual.append(linha);
        }
        if (!atual.isEmpty()) {
            dispositivos.add(new Dispositivo(rotulo, atual.toString()));
        }
        return dispositivos;
    }
}
