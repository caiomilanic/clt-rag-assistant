package br.com.caiomilani.cltassistant.ingestao;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class DivisorDeDispositivosTest {

    /** Caso real (Q10 do eval): o banco de horas está no § 5º; o caput fala de horas extras. */
    @Test
    void separaCaputEParagrafosMantendoIncisosJuntos() {
        String art59 = String.join("\n",
                "Art. 59. A duração diária do trabalho poderá ser acrescida de horas extras, em número não excedente de duas.",
                "§ 1º A remuneração da hora extra será, pelo menos, 50% (cinquenta por cento) superior à da hora normal.",
                "§ 5º O banco de horas de que trata o § 2º deste artigo poderá ser pactuado por acordo individual escrito.",
                "§ 6º É lícito o regime de compensação de jornada estabelecido por acordo individual.");

        List<DivisorDeDispositivos.Dispositivo> d = DivisorDeDispositivos.dividir(art59);

        assertThat(d).extracting(DivisorDeDispositivos.Dispositivo::rotulo).containsExactly("caput", "§ 1º", "§ 5º", "§ 6º");
        assertThat(d.get(2).texto()).startsWith("§ 5º O banco de horas");
    }

    @Test
    void incisosFicamComODispositivoAQuePertencem() {
        String art130 = String.join("\n",
                "Art. 130 - Após cada período de 12 (doze) meses, o empregado terá direito a férias, na seguinte proporção:",
                "I - 30 (trinta) dias corridos, quando não houver faltado ao serviço mais de 5 (cinco) vezes;",
                "II - 24 (vinte e quatro) dias corridos, quando houver tido de 6 (seis) a 14 (quatorze) faltas;",
                "§ 1º - É vedado descontar, do período de férias, as faltas do empregado ao serviço.",
                "Parágrafo único - Texto ilustrativo.");

        List<DivisorDeDispositivos.Dispositivo> d = DivisorDeDispositivos.dividir(art130);

        assertThat(d).extracting(DivisorDeDispositivos.Dispositivo::rotulo).containsExactly("caput", "§ 1º", "Parágrafo único");
        assertThat(d.get(0).texto()).contains("I - 30 (trinta)").contains("II - 24");
    }

    @Test
    void artigoSoComCaput() {
        assertThat(DivisorDeDispositivos.dividir("Art. 620. As condições estabelecidas em acordo coletivo sempre prevalecerão."))
                .extracting(DivisorDeDispositivos.Dispositivo::rotulo).containsExactly("caput");
    }
}
