package br.com.caiomilani.cltassistant.ingestao;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dispara a ingestão de uma fonte. Operação administrativa: na camada 1 fica aberta para uso
 * local; ganha proteção junto com os guardrails (passo 8).
 */
@RestController
@RequestMapping("/api/ingestao")
public class IngestaoController {

    private final IngestaoService service;

    public IngestaoController(IngestaoService service) {
        this.service = service;
    }

    @PostMapping("/{fonte}")
    public IngestaoService.Resultado ingerir(@PathVariable String fonte) throws IOException {
        return service.ingerir(fonte);
    }

    public record Erro(String erro) {
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Erro fonteDesconhecida(IllegalArgumentException e) {
        return new Erro(e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public Erro corpusAusente(IllegalStateException e) {
        return new Erro(e.getMessage());
    }
}
