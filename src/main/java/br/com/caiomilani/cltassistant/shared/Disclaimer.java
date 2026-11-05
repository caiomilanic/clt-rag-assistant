package br.com.caiomilani.cltassistant.shared;

/**
 * Requisito de produto: toda resposta da API carrega este aviso.
 * Fica centralizado aqui para o frontend e os testes usarem o mesmo texto.
 */
public final class Disclaimer {

    public static final String TEXT =
            "Ferramenta informativa baseada no texto da CLT. Não é aconselhamento jurídico. "
            + "Para o seu caso concreto, consulte um advogado trabalhista, o sindicato da sua "
            + "categoria ou a Defensoria Pública.";

    private Disclaimer() {
    }
}
