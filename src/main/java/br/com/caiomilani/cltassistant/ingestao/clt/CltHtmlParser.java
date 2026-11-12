package br.com.caiomilani.cltassistant.ingestao.clt;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Extrai os artigos vigentes do HTML oficial da CLT publicado no portal do Planalto.
 *
 * <p>O HTML é irregular (gerado pelo FrontPage, com texto revogado misturado ao vigente).
 * As regras abaixo foram levantadas com o protótipo {@code tools/corpus/parse_clt.py}
 * e estão documentadas em {@code tools/README.md}.
 *
 * <p>Classe sem dependência do Spring, para ser testada isoladamente.
 */
public class CltHtmlParser {

    /** Encoding do arquivo publicado pelo Planalto. */
    public static final String CHARSET = "windows-1252";

    private static final int F = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    /**
     * "Art. 130 -", "Art. 59-A.", "Art. 1º" e, em trechos antigos, "Art 554." (sem ponto).
     * O sufixo de letra só vale colado ao hífen ("Art. 58 - A duração" não é 58-A).
     */
    private static final Pattern ARTIGO = Pattern.compile(
            "^Art\\.?\\s*(\\d{1,4}(?:\\.\\d{3})?)\\s*(?:º|°|o\\b)?\\s*(?:-([A-Z]{1,2})\\b)?\\s*[.\\-–:]?\\s*");

    private static final Pattern CABECALHO = Pattern.compile(
            "^(T[ÍI]TULO|CAP[ÍI]TULO|SE[ÇC][ÃA]O|SUBSE[ÇC][ÃA]O)\\s+[IVXLCDM\\-A-Z]+", F);

    /** Notas de alteração entre parênteses, inclusive com um nível de parênteses aninhados. */
    private static final Pattern NOTA = Pattern.compile(
            "\\(\\s*(?:Reda[çc][ãa]o|Declarad|Inclu[íi]d|Inciso|Al[íi]nea|Caput|Artigo|Nova reda|Revogad|Vide|"
                    + "Vig[êe]ncia|Renumerad|Acrescid|Suprimid|Par[áa]grafo|Express[ãa]o|Produ[çc][ãa]o|Regulamento|"
                    + "Promulga|Ret|Vetad|Execu[çc][ãa]o|Convers|Mantid|Restabelecid)"
                    + "[^()]*(?:\\([^()]*\\)[^()]*)*\\)",
            F);

    /** Nota que abriu parêntese e não fechou (quebrada entre elementos do HTML). */
    private static final Pattern NOTA_ABERTA = Pattern.compile(
            "\\((?:Reda[çc]|Inclu[íi]d|Revogad|Vide|Vig[êe]ncia|Acrescid|Renumerad)[^()]*$", F);

    private static final Pattern VIGENCIA_ENCERRADA = Pattern.compile("\\bVig[êe]ncia encerrada\\b", F);

    /** Notas sem parênteses de abertura, quebradas entre elementos: "Redação restabelecida pelo Decreto-lei nº 8.987-A, de 1946". */
    private static final Pattern NOTA_SOLTA = Pattern.compile(
            "\\(?\\s*(?:Reda[çc][ãa]o restabelecida|c?om vig[êe]ncia suspensa)\\s+pel[oa]\\s+[^()]*?,\\s*de\\s+\\d{4}\\s*\\)?", F);

    /** "Lei no 10.406" / "Lei n o 10.406" (sobrescrito quebrado) -> "Lei nº 10.406". */
    private static final Pattern NUMERO_DE_LEI = Pattern.compile(
            "\\b(Lei|Decreto-[Ll]ei|Decreto|Medida Provis[óo]ria|Lei Complementar)\\s+n\\s*[oº°]\\s*(?=\\d)");

    /** Sobras de dispositivos revogados ou vetados: "§ 1º.", "I -;", "a);", ")", "A", ",". */
    private static final Pattern SOBRA = Pattern.compile(
            "^((§\\s*\\d+\\s*º?|Par[áa]grafo [úu]nico|[IVXLC]+|[a-z]\\))\\s*[-–.:;]*\\s*\\.?"
                    + "|[\\p{Punct}–]{1,3}|[A-Z]|Vig[êe]ncia( encerrada)?|Produ[çc][ãa]o de efeitos?)$",
            F);

    /** Subtítulo sem número entre artigos, ex.: "Trabalho aos domingos". */
    private static final Pattern SUBTITULO = Pattern.compile(
            "^(?!§|Par[áa]grafo|[IVXLC]+\\s*[-–.]|[a-z]\\))[A-ZÁÉÍÓÚÂÊÔÃÕÇ][^.;:()]{2,79}$");

    private static final Pattern SO_REVOGADO = Pattern.compile("^\\(?\\s*(revogad|vetad|suprimid)", F);

    private static final Pattern SOBRESCRITO = Pattern.compile("((?:§|Art\\.)\\s*\\d+)\\s*[oº°](?![\\p{L}\\d])");

    /** Fragmentos sem letras que sobram de notas quebradas: ") )", "019)". */
    private static final Pattern FRAGMENTO = Pattern.compile("^[^\\p{L}]{0,6}\\)[^\\p{L}]{0,6}$");

    public List<ArtigoClt> parse(InputStream html, String baseUri) throws IOException {
        Document doc = Jsoup.parse(html, CHARSET, baseUri);
        return parse(doc);
    }

    public List<ArtigoClt> parse(Document doc) {
        // Texto revogado aparece riscado; não é lei vigente.
        doc.select("strike, s, del").remove();
        Set<String> ancoras = new HashSet<>();
        doc.select("a[name]").forEach(a -> ancoras.add(a.attr("name").toLowerCase(Locale.ROOT)));

        List<Builder> artigos = new ArrayList<>();
        Builder atual = null;
        Contexto ctx = new Contexto();
        String cabecalhoPendente = null;

        for (Element el : doc.select("p, h1, h2, h3, h4, h5, h6")) {
            String bruto = normalizar(el.text());
            if (bruto.isEmpty()) {
                continue;
            }
            List<String> notas = new ArrayList<>();
            Matcher m = NOTA.matcher(bruto);
            while (m.find()) {
                notas.add(normalizar(m.group()));
            }
            String texto = NOTA.matcher(bruto).replaceAll("");
            texto = NOTA_SOLTA.matcher(texto).replaceAll("");
            texto = NOTA_ABERTA.matcher(texto).replaceAll("");
            texto = normalizar(VIGENCIA_ENCERRADA.matcher(texto).replaceAll(""));
            if (texto.isEmpty() || SOBRA.matcher(texto).matches() || FRAGMENTO.matcher(texto).matches()) {
                continue;
            }

            Matcher cab = CABECALHO.matcher(texto);
            if (cab.find()) {
                cabecalhoPendente = ctx.definir(cab.group(1), texto);
                continue;
            }
            // A linha seguinte a um cabeçalho costuma ser o nome dele ("DAS FÉRIAS ANUAIS").
            if (cabecalhoPendente != null && maiusculo(texto) && texto.length() < 200) {
                ctx.completar(cabecalhoPendente, texto);
                cabecalhoPendente = null;
                continue;
            }
            cabecalhoPendente = null;

            Matcher art = ARTIGO.matcher(texto);
            if (art.find()) {
                String numero = art.group(1).replace(".", "");
                if (art.group(2) != null) {
                    numero = numero + "-" + art.group(2);
                }
                // O arquivo começa com os arts. 1º e 2º do decreto-lei que aprova a CLT;
                // quando o "Art. 1º" aparece de novo, começa a CLT propriamente dita.
                if (numero.equals("1") && artigos.stream().anyMatch(a -> a.numero.equals("1"))) {
                    artigos.clear();
                }
                String corpo = texto.substring(art.end()).trim();
                atual = new Builder(numero, ancora(el, numero, ancoras), ctx, texto, notas,
                        corpo.isEmpty() || SO_REVOGADO.matcher(corpo).find());
                artigos.add(atual);
            } else if (atual != null && SUBTITULO.matcher(texto).matches()) {
                ctx.subtitulo = texto;
            } else if (atual != null) {
                atual.linhas.add(texto);
                atual.notas.addAll(notas);
            }
        }
        return consolidar(artigos);
    }

    /**
     * Versões de medidas provisórias que caducaram continuam no HTML sem riscado: são descartadas.
     * O critério olha só as notas da linha do caput: notas das linhas seguintes podem ser de
     * dispositivos vizinhos.
     * Artigos que ainda aparecem mais de uma vez ficam com a versão mais longa.
     */
    private List<ArtigoClt> consolidar(List<Builder> artigos) {
        Map<String, ArtigoClt> porNumero = new LinkedHashMap<>();
        for (Builder b : artigos) {
            boolean expirado = b.notasCaput.stream()
                    .anyMatch(n -> n.toLowerCase(Locale.ROOT).contains("encerrada"));
            if (expirado) {
                continue;
            }
            ArtigoClt artigo = b.build();
            porNumero.merge(artigo.numero(), artigo,
                    (antigo, novo) -> novo.texto().length() > antigo.texto().length() ? novo : antigo);
        }
        return List.copyOf(porNumero.values());
    }

    /**
     * Âncora para o link do artigo no site do Planalto. Prefere "art" + número quando ela existe
     * no documento; algumas âncoras do próprio HTML estão erradas (o Art. 553 tem "art936").
     */
    private static String ancora(Element el, String numero, Set<String> ancoras) {
        String esperada = "art" + numero.toLowerCase(Locale.ROOT).replace("-", "");
        if (ancoras.contains(esperada)) {
            return esperada;
        }
        // Variações da própria âncora ("art130.", "art59a.0"); âncoras de outro número são ignoradas.
        for (Element a : el.select("a[name]")) {
            String nome = a.attr("name").toLowerCase(Locale.ROOT);
            if (nome.startsWith(esperada) && ancoras.contains(nome)) {
                return nome;
            }
        }
        return esperada;
    }

    static String normalizar(String texto) {
        String t = texto.replace(' ', ' ').replaceAll("\\s+", " ").trim();
        // "§ 1 o" / "Art. 1 o" (sobrescrito quebrado no HTML) -> "§ 1º"
        t = SOBRESCRITO.matcher(t).replaceAll("$1º");
        t = NUMERO_DE_LEI.matcher(t).replaceAll("$1 nº ");
        return t.replaceAll("\\s+([,.;:])", "$1");
    }

    private static boolean maiusculo(String texto) {
        return texto.chars().anyMatch(Character::isLetter) && texto.equals(texto.toUpperCase(Locale.ROOT));
    }

    private static final class Contexto {
        String titulo;
        String capitulo;
        String secao;
        String subtitulo;

        /** Registra um cabeçalho e devolve qual nível ele preencheu. */
        String definir(String tipo, String texto) {
            String t = tipo.toUpperCase(Locale.ROOT);
            if (t.startsWith("T")) {
                titulo = texto;
                capitulo = null;
                secao = null;
                return "titulo";
            }
            if (t.startsWith("CAP")) {
                capitulo = texto;
                secao = null;
                return "capitulo";
            }
            secao = texto;
            return "secao";
        }

        void completar(String nivel, String nome) {
            switch (nivel) {
                case "titulo" -> titulo = titulo + " - " + nome;
                case "capitulo" -> capitulo = capitulo + " - " + nome;
                default -> secao = secao + " - " + nome;
            }
        }
    }

    private static final class Builder {
        final String numero;
        final String ancora;
        final String titulo;
        final String capitulo;
        final String secao;
        final String subtitulo;
        final List<String> linhas = new ArrayList<>();
        final List<String> notas = new ArrayList<>();
        final List<String> notasCaput;
        final boolean revogado;

        Builder(String numero, String ancora, Contexto ctx, String primeiraLinha, List<String> notas, boolean revogado) {
            this.numero = numero;
            this.ancora = ancora;
            this.titulo = ctx.titulo;
            this.capitulo = ctx.capitulo;
            this.secao = ctx.secao;
            this.subtitulo = ctx.subtitulo;
            this.linhas.add(primeiraLinha);
            this.notas.addAll(notas);
            this.notasCaput = List.copyOf(notas);
            this.revogado = revogado;
        }

        ArtigoClt build() {
            return new ArtigoClt(numero, ancora, titulo, capitulo, secao, subtitulo,
                    String.join("\n", linhas), List.copyOf(notas), revogado);
        }
    }
}
