package br.com.caiomilani.cltassistant.avaliacao;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/** Lê o eval set do disco a cada avaliação, para refletir edições sem reiniciar a aplicação. */
@Component
public class EvalSetRepositorio {

    private final Path arquivo;
    private final JsonMapper json = JsonMapper.builder().build();

    public EvalSetRepositorio(@Value("${app.eval.arquivo}") String arquivo) {
        this.arquivo = Path.of(arquivo);
    }

    public EvalSet carregar() throws IOException {
        try (InputStream in = Files.newInputStream(arquivo)) {
            return json.readValue(in, EvalSet.class);
        }
    }
}
