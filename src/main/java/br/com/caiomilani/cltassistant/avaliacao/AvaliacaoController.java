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
    private final AvaliacaoRespostasService respostas;

    public AvaliacaoController(AvaliacaoRecuperacaoService recuperacao, AvaliacaoRespostasService respostas) {
        this.recuperacao = recuperacao;
        this.respostas = respostas;
    }

    /** Roda o eval set contra a busca vetorial e devolve o acerto de recuperação no top-k. */
    @PostMapping("/recuperacao")
    public AvaliacaoRecuperacaoService.Relatorio recuperacao(
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int k) throws IOException {
        return recuperacao.avaliar(k);
    }

    /**
     * Roda o eval set pelo RAG completo (uma chamada ao LLM por pergunta; leva alguns minutos)
     * e devolve citação, citações fora dos trechos, recusas e latência.
     */
    @PostMapping("/respostas")
    public AvaliacaoRespostasService.Relatorio respostas(
            @RequestParam(defaultValue = "5") @Min(1) @Max(10) int k) throws IOException {
        return respostas.avaliar(k);
    }
}
