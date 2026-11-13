package br.com.caiomilani.cltassistant.busca;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Busca pura, sem LLM: devolve os artigos mais relevantes para a pergunta.
 * Serve para inspecionar a recuperação. Granularidade e estratégia podem ser trocadas na URL
 * para comparar configurações sem reiniciar a aplicação (D18).
 */
@RestController
@RequestMapping("/api/busca")
@Validated
public class BuscaController {

    private final Recuperador recuperador;

    public BuscaController(Recuperador recuperador) {
        this.recuperador = recuperador;
    }

    public record Resultado(String referencia, String documento, String contexto, String link,
                            String dispositivo, Double scoreVetorial, Integer posicaoVetorial,
                            Integer posicaoTextual, String trecho) {
    }

    @GetMapping
    public List<Resultado> buscar(@RequestParam @NotBlank String q,
                                  @RequestParam(defaultValue = "5") @Min(1) @Max(20) int k,
                                  @RequestParam(required = false) String granularidade,
                                  @RequestParam(required = false) String estrategia,
                                  @RequestParam(required = false) Boolean expansao) {
        var g = Recuperador.granularidade(granularidade);
        var e = Recuperador.estrategia(estrategia);
        return recuperador.buscar(q, k,
                        g != null ? g : recuperador.granularidadePadrao(),
                        e != null ? e : recuperador.estrategiaPadrao(),
                        expansao != null ? expansao : recuperador.expansaoPadrao())
                .stream().map(BuscaController::paraResultado).toList();
    }

    private static Resultado paraResultado(Recuperador.ArtigoRecuperado a) {
        String texto = a.texto() == null ? "" : a.texto();
        return new Resultado(a.referencia(), a.documento(), a.contexto(), a.link(), a.dispositivo(),
                a.scoreVetorial(), a.posicaoVetorial(), a.posicaoTextual(),
                texto.length() > 400 ? texto.substring(0, 400) + "…" : texto);
    }
}
