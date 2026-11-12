package br.com.caiomilani.cltassistant.avaliacao;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Estrutura de docs/eval/eval-set.json (só os campos usados na avaliação). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EvalSet(String versao, List<Pergunta> perguntas) {

    public static final String FORA_DO_ESCOPO = "fora_do_escopo";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pergunta(String id, String tipo, String pergunta,
                           @JsonProperty("artigos_esperados") List<String> artigosEsperados) {

        public boolean foraDoEscopo() {
            return FORA_DO_ESCOPO.equals(tipo);
        }
    }
}
