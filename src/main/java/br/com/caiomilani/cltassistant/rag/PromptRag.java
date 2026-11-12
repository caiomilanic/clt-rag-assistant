package br.com.caiomilani.cltassistant.rag;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Prompt do RAG. Cada regra existe por causa de uma falha observada no modelo sem RAG
 * (ver docs/exemplos e D17 em docs/decisoes.md).
 */
public final class PromptRag {

    /** Resposta fixa quando os trechos não respondem; usada também para detectar a recusa. */
    public static final String NAO_ENCONTREI =
            "Não encontrei a resposta nos trechos da CLT recuperados para esta pergunta.";

    /** Início da frase de recusa, usado para detectá-la mesmo com pequenas variações. */
    static final String MARCA_NAO_ENCONTREI = "Não encontrei a resposta nos trechos";

    public static final String SISTEMA = """
            Você é um assistente de normas trabalhistas para analistas de RH e Departamento Pessoal no Brasil.
            Responda à pergunta usando SOMENTE os trechos da CLT fornecidos entre <trechos> e </trechos>.
            Não use conhecimento próprio sobre leis, números, prazos ou percentuais.

            Regras:
            1. Toda afirmação deve indicar, entre parênteses, o dispositivo de onde saiu, no formato \
            (Art. 130, I) ou (Art. 134, § 1º). Cite somente artigos que aparecem nos trechos.
            2. Use números, prazos e percentuais exatamente como estão escritos no trecho. \
            Não converta nem arredonde: "dias corridos" não vira "dias úteis".
            3. Se os trechos responderem só parte da pergunta, responda essa parte e diga claramente \
            o que os trechos não cobrem.
            4. Se nenhum trecho responder à pergunta, escreva apenas esta frase, sem citar artigos: \
            "%s"
            5. Se a pergunta envolver convenção ou acordo coletivo, explique o que a lei permite \
            ou proíbe negociar, citando o artigo.
            6. Seja direto: comece pela resposta, em no máximo 5 frases curtas ou uma lista curta. \
            Não repita a pergunta. Não inclua aviso jurídico: o sistema já o adiciona.
            """.formatted(NAO_ENCONTREI);

    private PromptRag() {
    }

    /** Monta a mensagem do usuário com os trechos numerados e a pergunta. */
    public static String usuario(String pergunta, List<String> trechos) {
        String blocos = IntStream.range(0, trechos.size())
                .mapToObj(i -> "[" + (i + 1) + "] " + trechos.get(i))
                .collect(Collectors.joining("\n\n"));
        return "<trechos>\n" + blocos + "\n</trechos>\n\nPergunta: " + pergunta;
    }
}
