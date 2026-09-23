package com.rehabit;

import com.rehabit.model.Clinica;
import com.rehabit.model.Fisioterapeuta;
import com.rehabit.model.Medicao;
import com.rehabit.model.Paciente;
import com.rehabit.model.Sessao;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A sessão é registrada enquanto acontece: quem data o registro é o servidor,
 * no fuso das clínicas, e não o formulário. Uma data digitada (ou o relógio
 * errado de um computador) não pode mais deslocar a evolução do paciente.
 */
class SessaoDatadaPeloServidorTest extends TesteDeIntegracao {

    /**
     * 01:30 de 23/09 em UTC são 22:30 de 22/09 em Brasília. É a faixa em que
     * um servidor no fuso UTC (o do Render) gravaria o dia seguinte.
     */
    private static final Instant AGORA = Instant.parse("2026-09-23T01:30:15Z");

    @TestConfiguration
    static class RelogioParado {
        @Bean
        @Primary
        Clock relogioDeTeste() {
            return Clock.fixed(AGORA, ZoneOffset.UTC);
        }
    }

    @Test
    void ignoraADataEnviadaEUsaOMomentoDoRegistroEmBrasilia() throws Exception {
        Clinica clinica = novaClinica();
        Fisioterapeuta ana = novoProfissional(clinica);
        Paciente paciente = novoPaciente(ana);

        mvc.perform(post("/api/pacientes/" + paciente.getId() + "/sessoes")
                        .header("Authorization", tokenDe(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"2026-01-10\",\"duracao\":45,\"amplitudeMedia\":92.5,"
                                + "\"idFisioterapeuta\":" + ana.getId() + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data").value("2026-09-22"))
                .andExpect(jsonPath("$.hora").value("22:30:15"));

        List<Sessao> gravadas = sessoes.findAll();
        assertThat(gravadas).hasSize(1);
        assertThat(gravadas.get(0).getDataSessao()).isEqualTo(LocalDate.of(2026, 9, 22));
        assertThat(gravadas.get(0).getHoraSessao()).isEqualTo(LocalTime.of(22, 30, 15));

        Medicao medicao = medicoes.findByIdSessao(gravadas.get(0).getId());
        assertThat(medicao.getDataMedicao()).isEqualTo(LocalDate.of(2026, 9, 22));
        assertThat(medicao.getHoraMedicao()).isEqualTo(LocalTime.of(22, 30, 15));
    }

    @Test
    void registraSemQueOFormularioMandeData() throws Exception {
        Clinica clinica = novaClinica();
        Fisioterapeuta ana = novoProfissional(clinica);
        Paciente paciente = novoPaciente(ana);

        mvc.perform(post("/api/pacientes/" + paciente.getId() + "/sessoes")
                        .header("Authorization", tokenDe(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"duracao\":30,\"idFisioterapeuta\":" + ana.getId() + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data").value("2026-09-22"));
    }
}
