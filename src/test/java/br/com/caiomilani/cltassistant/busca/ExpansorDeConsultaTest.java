package br.com.caiomilani.cltassistant.busca;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ExpansorDeConsultaTest {

    private final ExpansorDeConsulta expansor = new ExpansorDeConsulta(new ExpansorDeConsulta.Glossario("teste", List.of(
            new ExpansorDeConsulta.Entrada(List.of("12x36", "12 x 36"),
                    "doze horas seguidas por trinta e seis horas ininterruptas de descanso", "Q23"),
            new ExpansorDeConsulta.Entrada(List.of("escritorio"),
                    "regime presencial", "Q25"),
            new ExpansorDeConsulta.Entrada(List.of("home office"),
                    "teletrabalho", "Q25"))));

    @Test
    void acrescentaAExpansaoQuandoOTermoAparece() {
        ExpansorDeConsulta.Expansao e = expansor.expandir("Posso adotar a escala 12x36 com o funcionário?");

        assertThat(e.termos()).containsExactly("12x36");
        assertThat(e.expansoes()).containsExactly("doze horas seguidas por trinta e seis horas ininterruptas de descanso");
        assertThat(e.consulta()).isEqualTo("Posso adotar a escala 12x36 com o funcionário?\n"
                + "doze horas seguidas por trinta e seis horas ininterruptas de descanso.");
    }

    @Test
    void ignoraAcentosEMaiusculas() {
        // "Escritório" na pergunta, "escritorio" no glossário
        assertThat(expansor.expandir("Trazer de volta ao Escritório quem está em Home Office").termos())
                .containsExactly("escritorio", "home office");
    }

    @Test
    void naoExpandeSemTermo() {
        ExpansorDeConsulta.Expansao e = expansor.expandir("Qual o prazo para pagar as férias?");

        assertThat(e.termos()).isEmpty();
        assertThat(e.expansoes()).isEmpty();
        assertThat(e.consulta()).isEqualTo("Qual o prazo para pagar as férias?");
    }

    @Test
    void exigePalavraInteira() {
        // "12x360" não é "12x36"
        assertThat(expansor.expandir("Código 12x360").termos()).isEmpty();
    }

    @Test
    void mesmaExpansaoEntraUmaVezSo() {
        assertThat(expansor.expandir("12x36 ou 12 x 36?").consulta())
                .isEqualTo("12x36 ou 12 x 36?\ndoze horas seguidas por trinta e seis horas ininterruptas de descanso.");
    }
}
