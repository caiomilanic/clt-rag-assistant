package br.com.caiomilani.cltassistant.avaliacao;

import java.io.IOException;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/avaliacao")
@Validated
public class AvaliacaoController {

    private final AvaliacaoRecuperacaoService recuperacao;

    public AvaliacaoController(AvaliacaoRecuperacaoService recuperacao) {
        this.recuperacao = recuperacao;
    }

    /** Roda o eval set contra a busca vetorial e devolve o acerto de recuperação no top-k. */
    @PostMapping("/recuperacao")
    public AvaliacaoRecuperacaoService.Relatorio recuperacao(
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int k) throws IOException {
        return recuperacao.avaliar(k);
    }
}
