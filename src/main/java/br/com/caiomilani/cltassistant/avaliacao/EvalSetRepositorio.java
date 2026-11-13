package br.com.caiomilani.cltassistant.avaliacao;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/**
 * Lê os conjuntos de perguntas do disco a cada avaliação, para refletir edições sem reiniciar a aplicação.
 *
 * <ul>
 *   <li><b>desenvolvimento</b> (padrão): as perguntas usadas para ajustar busca, prompt e verificador;</li>
 *   <li><b>validacao</b>: perguntas novas, congeladas antes da primeira execução e nunca usadas para ajustar
 *       o sistema. Mostram se as melhorias valem fora das perguntas em que foram feitas (D21).</li>
 * </ul>
 */
@Component
public class EvalSetRepositorio {

    public static final String DESENVOLVIMENTO = "desenvolvimento";
    public static final String VALIDACAO = "validacao";

    private final Path desenvolvimento;
    private final Path validacao;
    private final JsonMapper json = JsonMapper.builder().build();

    public EvalSetRepositorio(@Value("${app.eval.arquivo}") String desenvolvimento,
                              @Value("${app.eval.validacao}") String validacao) {
        this.desenvolvimento = Path.of(desenvolvimento);
        this.validacao = Path.of(validacao);
    }

    public EvalSet carregar() throws IOException {
        return carregar(DESENVOLVIMENTO);
    }

    /** @param conjunto {@value #DESENVOLVIMENTO} (ou null) ou {@value #VALIDACAO} */
    public EvalSet carregar(String conjunto) throws IOException {
        Path arquivo = switch (conjunto == null ? DESENVOLVIMENTO : conjunto) {
            case DESENVOLVIMENTO -> desenvolvimento;
            case VALIDACAO -> validacao;
            default -> throw new IllegalArgumentException("Conjunto desconhecido: " + conjunto);
        };
        try (InputStream in = Files.newInputStream(arquivo)) {
            return json.readValue(in, EvalSet.class);
        }
    }
}
