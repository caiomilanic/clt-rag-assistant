package br.com.caiomilani.cltassistant.rag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Confere as citações de parágrafo e inciso de uma resposta contra o texto dos artigos recuperados (D19).
 *
 * <p>Duas verificações, sem LLM:
 * <ol>
 *   <li><b>dispositivo inexistente</b>: "(Art. 145, I)" quando o Art. 145 não tem inciso I;</li>
 *   <li><b>número ausente</b>: a frase cita "(Art. 130, I)" e diz "24 dias", mas o inciso I fala de 30;
 *       o 24 está no inciso II. Números escritos por extenso no texto da lei ("trinta minutos") contam.</li>
 * </ol>
 * Limitação conhecida: um inciso que existe, mas é o errado, só é pego quando a frase tem números
 * ("Art. 611-B, I" no lugar de XI, numa frase sem números, passa).
 */
public final class VerificadorDeCitacoes {

    // Sem quantificadores possessivos aqui: no Java, um grupo dentro de um "?+" pode manter o valor
    // capturado numa tentativa que falhou ("Art. 59-B)" sem vírgula) e contaminar a citação seguinte
    // ("Art. 59, § 6º" virava "59-B, § 6º"). O (?!\\d) faz o papel que o possessivo fazia no número.
    private static final String ARTIGO = "(?i:art(?:igo)?)\\.?\\s*(\\d{1,4})(?!\\d)(?:\\s*[º°])?(?:-([A-Z])(?![\\p{L}]))?(?![-º°])";
    private static final String OUTRA_LEI =
            "(?!\\s*,?\\s*(?:(?:§\\s*\\d+º?|[IVXLC]+)\\s*,?\\s*)?(?i:d[aoe]s?\\s+(?:Lei|Decreto|Medida|Constitui|Ato\\s+das\\s+Disposi|ADCT)))";
    private static final String DISPOSITIVO = "(§\\s*\\d+\\s*º?|§\\s*[IVXLC]+\\b|[IVXLC]+\\b|(?i:par[áa]grafo [úu]nico))";

    /** "Art. 130, I", "Art. 134, § 1º", "Art. 487, I e II", "Art. 59-B, parágrafo único". */
    private static final Pattern ARTIGO_DEPOIS = Pattern.compile(
            ARTIGO + "\\s*,\\s*" + DISPOSITIVO + "(?:\\s+e\\s+([IVXLC]+\\b))?");

    /** "§ 6º do Art. 477", "parágrafo único do art. 59-B" (mas não "§ 1º do art. 18 da Lei nº 8.036"). */
    private static final Pattern DISPOSITIVO_ANTES = Pattern.compile(
            "(§\\s*\\d+\\s*º?|(?i:par[áa]grafo [úu]nico))\\s+d[oa]\\s+" + ARTIGO + OUTRA_LEI);

    private static final Pattern NUMERO = Pattern.compile("(?<![\\d.,])(\\d+)(?:[.,]\\d+)?(?![\\d])");
    private static final Pattern REFERENCIA_NO_TEXTO = Pattern.compile(
            "(?i:art(?:igo)?s?)\\.?\\s*\\d+\\S*|§\\s*\\d+\\S*|n[º°o]\\s*[\\d.]+|\\d+\\s*[º°]");
    private static final Pattern FIM_DE_FRASE = Pattern.compile("[.!?](?=\\s+[A-ZÁÉÍÓÚÂÊÔÃÕÇ(])|\\n");

    public record Citacao(String artigo, List<String> dispositivos, int inicio, int fim) {

        String rotulo() {
            return "Art. " + artigo + ", " + String.join(" e ", dispositivos);
        }
    }

    /**
     * @param citacao citação como normalizada, ex.: "Art. 130, I"
     * @param tipo    {@code dispositivo_inexistente} ou {@code numero_ausente}
     */
    public record Problema(String citacao, String tipo, String detalhe) {
    }

    private VerificadorDeCitacoes() {
    }

    /** Citações que indicam parágrafo ou inciso, na ordem em que aparecem. */
    public static List<Citacao> extrair(String texto) {
        List<Citacao> citacoes = new ArrayList<>();
        Matcher m = ARTIGO_DEPOIS.matcher(texto);
        while (m.find()) {
            List<String> disp = new ArrayList<>();
            disp.add(normalizar(m.group(3)));
            if (m.group(4) != null) {
                disp.add(normalizar(m.group(4)));
            }
            citacoes.add(new Citacao(artigo(m.group(1), m.group(2)), disp, m.start(), m.end()));
        }
        Matcher a = DISPOSITIVO_ANTES.matcher(texto);
        while (a.find()) {
            citacoes.add(new Citacao(artigo(a.group(2), a.group(3)), List.of(normalizar(a.group(1))), a.start(), a.end()));
        }
        citacoes.sort((x, y) -> Integer.compare(x.inicio(), y.inicio()));
        return citacoes;
    }

    /**
     * @param textoPorArtigo texto de cada artigo recuperado, chaveado pelo número ("130", "59-A");
     *                       artigos citados que não estão aqui são ignorados (já aparecem em foraDosTrechos)
     */
    public static List<Problema> verificar(String resposta, Map<String, String> textoPorArtigo) {
        return verificar(resposta, "", textoPorArtigo);
    }

    /**
     * @param pergunta números que já estão na pergunta ("8 horas por dia") são contexto, não afirmação da
     *                 resposta, e não são cobrados do dispositivo citado
     */
    public static List<Problema> verificar(String resposta, String pergunta, Map<String, String> textoPorArtigo) {
        return corrigir(resposta, pergunta, textoPorArtigo).problemas();
    }

    /**
     * @param resposta  texto com cada citação marcada rebaixada para o artigo ("(Art. 130, I)" vira
     *                  "(Art. 130)"); igual ao original quando não há problema
     * @param problemas o que foi encontrado, já com a citação original
     */
    public record Correcao(String resposta, List<Problema> problemas) {

        public boolean alterou() {
            return !problemas.isEmpty();
        }
    }

    /**
     * Verifica e corrige: o artigo citado sempre foi conferido contra os trechos (e na avaliação veio
     * certo em 100% das respostas), mas o parágrafo ou inciso não. Quando o dispositivo está errado,
     * o mais seguro para o analista é ficar só com o artigo, que ele abre e lê inteiro (D20).
     */
    public static Correcao corrigir(String resposta, String pergunta, Map<String, String> textoPorArtigo) {
        Set<String> daPergunta = numeros(REFERENCIA_NO_TEXTO.matcher(pergunta == null ? "" : pergunta).replaceAll(" "));
        List<Problema> problemas = new ArrayList<>();
        List<Citacao> marcadas = new ArrayList<>();
        int fimAnterior = 0;
        for (Citacao c : extrair(resposta)) {
            List<Problema> daCitacao = problemas(resposta, c, fimAnterior, daPergunta, textoPorArtigo.get(c.artigo()));
            if (!daCitacao.isEmpty()) {
                problemas.addAll(daCitacao);
                marcadas.add(c);
            }
            fimAnterior = c.fim();
        }
        StringBuilder corrigida = new StringBuilder(resposta);
        for (int i = marcadas.size() - 1; i >= 0; i--) {
            Citacao c = marcadas.get(i);
            Matcher resto = INCISOS_EM_SEGUIDA.matcher(resposta).region(c.fim(), resposta.length());
            int fim = resto.lookingAt() ? resto.end() : c.fim();
            corrigida.replace(c.inicio(), fim, "Art. " + c.artigo());
        }
        return new Correcao(corrigida.toString(), List.copyOf(problemas));
    }

    /** ", III" depois de "Art. 611-A, § 3º": sai junto quando a citação é rebaixada. */
    private static final Pattern INCISOS_EM_SEGUIDA = Pattern.compile("(?:\\s*,\\s*(?:inciso\\s+)?[IVXLC]+\\b)+");

    private static List<Problema> problemas(String resposta, Citacao c, int fimAnterior, Set<String> daPergunta,
                                            String texto) {
        List<Problema> problemas = new ArrayList<>();
        if (texto == null) {
            return problemas;
        }
        List<String> linhas = linhasDoArtigo(texto);
        StringBuilder blocos = new StringBuilder();
        for (String d : c.dispositivos()) {
            String bloco = bloco(linhas, d);
            if (bloco == null) {
                problemas.add(new Problema("Art. " + c.artigo() + ", " + d, "dispositivo_inexistente",
                        "O Art. " + c.artigo() + " não tem " + (d.startsWith("§") || d.startsWith("Par") ? d : "inciso " + d)));
            } else {
                blocos.append(bloco).append('\n');
            }
        }
        if (problemas.isEmpty()) {
            List<String> ausentes = numerosAusentes(frase(resposta, fimAnterior, c), blocos.toString()).stream()
                    .filter(n -> !daPergunta.contains(n)).toList();
            if (!ausentes.isEmpty()) {
                problemas.add(new Problema(c.rotulo(), "numero_ausente",
                        "A frase menciona " + String.join(", ", ausentes) + ", que não aparece(m) em " + c.rotulo()));
            }
        }
        return problemas;
    }

    /** Linhas do artigo sem o cabeçalho de contexto ("CLT, Art. 130 | …"). */
    private static List<String> linhasDoArtigo(String texto) {
        List<String> linhas = new ArrayList<>(List.of(texto.split("\n")));
        if (!linhas.isEmpty() && !linhas.get(0).startsWith("Art")) {
            linhas.remove(0);
        }
        return linhas;
    }

    /** Texto do dispositivo dentro do artigo, ou null se ele não existe. */
    static String bloco(List<String> linhas, String dispositivo) {
        if (dispositivo.startsWith("§") || dispositivo.startsWith("Par")) {
            Pattern inicio = dispositivo.startsWith("§")
                    ? Pattern.compile("^§\\s*" + dispositivo.replaceAll("\\D", "") + "(?!\\d)")
                    : Pattern.compile("^par[áa]grafo [úu]nico", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            StringBuilder b = null;
            for (String l : linhas) {
                boolean novoParagrafo = l.startsWith("§") || l.toLowerCase(Locale.ROOT).startsWith("parágrafo único");
                if (b != null && novoParagrafo) {
                    break;
                }
                if (b == null && inicio.matcher(l).find()) {
                    b = new StringBuilder();
                }
                if (b != null) {
                    b.append(l).append('\n');
                }
            }
            return b == null ? null : b.toString();
        }
        Pattern inciso = Pattern.compile("^" + dispositivo + "\\s*[-–]");
        return linhas.stream().filter(l -> inciso.matcher(l).find()).findFirst().orElse(null);
    }

    /**
     * Trecho da resposta que sustenta a citação. Em geral é a frase que termina nela
     * ("... 30 dias corridos (Art. 130, I)."). Se a citação abre a frase
     * ("(Art. 71, § 1º) menciona um intervalo de 15 minutos..."), é o resto dessa frase.
     */
    static String frase(String resposta, int fimCitacaoAnterior, Citacao c) {
        String antes = resposta.substring(fimCitacaoAnterior, c.inicio()).replaceAll("[(\\s]*$", "");
        boolean abreAFrase = antes.isEmpty() || antes.matches("(?s).*[.!?:]$") && !antes.matches("(?s).*\\d[.,]$");
        if (abreAFrase) {
            String depois = resposta.substring(c.fim());
            Matcher fim = FIM_DE_FRASE.matcher(depois);
            String resto = fim.find() ? depois.substring(0, fim.start()) : depois;
            if (!resto.isBlank() && !resto.strip().matches("[).]*")) {
                return resto;
            }
        }
        if (antes.endsWith(".")) {
            antes = antes.substring(0, antes.length() - 1);
        }
        int corte = 0;
        Matcher m = FIM_DE_FRASE.matcher(antes);
        while (m.find()) {
            corte = m.end();
        }
        return antes.substring(corte);
    }

    static List<String> numerosAusentes(String frase, String bloco) {
        String limpa = REFERENCIA_NO_TEXTO.matcher(frase).replaceAll(" ");
        String blocoMinusculo = bloco.toLowerCase(Locale.ROOT);
        Set<String> ausentes = new LinkedHashSet<>();
        for (String n : numeros(limpa)) {
            boolean comoDigito = Pattern.compile("(?<!\\d)" + n + "(?!\\d)").matcher(bloco).find();
            boolean porExtenso = porExtenso(Integer.parseInt(n)).stream()
                    .anyMatch(p -> Pattern.compile("(?<![\\p{L}])" + p + "(?![\\p{L}])").matcher(blocoMinusculo).find());
            if (!comoDigito && !porExtenso) {
                ausentes.add(n);
            }
        }
        return List.copyOf(ausentes);
    }

    /**
     * Números da frase, em algarismos ou por extenso ("duas horas" -> "2"). "um/uma" fica de fora:
     * na maioria das vezes é artigo indefinido, não número.
     */
    private static final Pattern ACIMA_DE_CEM = Pattern.compile(
            "(?<![\\p{L}])(?:cento|duzent[oa]s|trezent[oa]s|quatrocent[oa]s|quinhent[oa]s|seiscent[oa]s|setecent[oa]s"
                    + "|oitocent[oa]s|novecent[oa]s|mil)(?:\\s+e\\s+[\\p{L}]+){0,2}(?![\\p{L}])");

    static Set<String> numeros(String texto) {
        Set<String> ns = new LinkedHashSet<>();
        Matcher m = NUMERO.matcher(texto);
        while (m.find()) {
            ns.add(m.group(1));
        }
        // Números acima de cem por extenso ("cento e vinte") saem antes, para "vinte" não virar 20.
        String resto = ACIMA_DE_CEM.matcher(texto.toLowerCase(Locale.ROOT)).replaceAll(" ");
        for (int n = 100; n >= 2; n--) {
            for (String forma : porExtenso(n)) {
                Matcher w = Pattern.compile("(?<![\\p{L}])" + forma + "(?![\\p{L}])").matcher(resto);
                if (w.find()) {
                    ns.add(String.valueOf(n));
                    resto = w.replaceAll(" ");
                }
            }
        }
        return ns;
    }

    private static final String[] UNIDADES = {"", "um|uma", "dois|duas", "três", "quatro", "cinco", "seis", "sete", "oito", "nove",
            "dez", "onze", "doze", "treze", "quatorze|catorze", "quinze", "dezesseis", "dezessete", "dezoito", "dezenove"};
    private static final String[] DEZENAS = {"", "", "vinte", "trinta", "quarenta", "cinquenta|cinqüenta", "sessenta", "setenta",
            "oitenta", "noventa"};

    private static final String[] CENTENAS = {"", "cento", "duzentos|duzentas", "trezentos|trezentas",
            "quatrocentos|quatrocentas", "quinhentos|quinhentas", "seiscentos|seiscentas", "setecentos|setecentas",
            "oitocentos|oitocentas", "novecentos|novecentas"};

    /** Formas por extenso de 1 a 999 ("24" -> "vinte e quatro", "120" -> "cento e vinte"); vazio fora dessa faixa. */
    static List<String> porExtenso(int n) {
        if (n <= 0 || n > 999) {
            return List.of();
        }
        if (n == 100) {
            return List.of("cem");
        }
        if (n > 100) {
            List<String> formas = new ArrayList<>();
            for (String c : CENTENAS[n / 100].split("\\|")) {
                if (n % 100 == 0) {
                    formas.add(c);
                } else {
                    porExtenso(n % 100).forEach(r -> formas.add(c + " e " + r));
                }
            }
            return formas;
        }
        if (n < 20) {
            return List.of(UNIDADES[n].split("\\|"));
        }
        List<String> formas = new ArrayList<>();
        for (String d : DEZENAS[n / 10].split("\\|")) {
            if (n % 10 == 0) {
                formas.add(d);
            } else {
                for (String u : UNIDADES[n % 10].split("\\|")) {
                    formas.add(d + " e " + u);
                }
            }
        }
        return formas;
    }

    private static String artigo(String numero, String letra) {
        return letra == null ? numero : numero + "-" + letra;
    }

    private static String normalizar(String dispositivo) {
        String d = dispositivo.replaceAll("\\s+", " ").trim();
        if (d.toLowerCase(Locale.ROOT).startsWith("par")) {
            return "Parágrafo único";
        }
        if (d.startsWith("§")) {
            String resto = d.substring(1).trim();
            // "§ III" (confusão comum do modelo) é tratado como inciso III
            return resto.matches("[IVXLC]+") ? resto : "§ " + resto.replaceAll("\\D", "") + "º";
        }
        return d;
    }
}
