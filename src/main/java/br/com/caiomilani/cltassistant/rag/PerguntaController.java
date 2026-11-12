package br.com.caiomilani.cltassistant.rag;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Endpoint principal do assistente: pergunta em linguagem natural, resposta com fontes. */
@RestController
@RequestMapping("/api/perguntas")
public class PerguntaController {

    private static final int K_PADRAO = 5;

    private final RagService rag;

    public PerguntaController(RagService rag) {
        this.rag = rag;
    }

    /** @param k quantos trechos recuperar; opcional, padrão 5 */
    public record PerguntaRequest(@NotBlank @Size(max = 1000) String pergunta, @Min(1) @Max(10) Integer k) {
    }

    @PostMapping
    public RagService.Resposta perguntar(@Valid @RequestBody PerguntaRequest request) {
        return rag.perguntar(request.pergunta(), request.k() == null ? K_PADRAO : request.k());
    }
}
