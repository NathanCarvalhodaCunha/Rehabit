package com.rehabit;

import com.rehabit.demo.ContaDeDemonstracao;
import com.rehabit.model.Agendamento;
import com.rehabit.model.Paciente;
import com.rehabit.model.Sessao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {"rehabit.demo.ativar=true", "rehabit.demo.senha=senha-da-demo"})
class ContaDeDemonstracaoTest extends TesteDeIntegracao {

    /** Quarta-feira, 23/09/2026, 10h em Brasília. */
    private static final LocalDate HOJE = LocalDate.of(2026, 9, 23);

    @TestConfiguration
    static class RelogioParado {
        @Bean
        @Primary
        Clock relogioDeTeste() {
            return Clock.fixed(Instant.parse("2026-09-23T13:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired ContaDeDemonstracao demonstracao;

    @Test
    void criaAContaUmaVezSo() {
        // A base zera o banco antes de cada teste, apagando o que a subida criou.
        assertThat(demonstracao.criarSeNaoExistir()).isTrue();
        long pacientesAntes = pacientes.count();

        assertThat(demonstracao.criarSeNaoExistir()).isFalse();

        assertThat(clinicas.count()).isEqualTo(1);
        assertThat(fisioterapeutas.count()).isEqualTo(5);
        assertThat(pacientes.count()).isEqualTo(pacientesAntes).isEqualTo(16);
    }

    @Test
    void aClinicaEOsProfissionaisEntramComASenhaConfigurada() throws Exception {
        demonstracao.criarSeNaoExistir();

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + ContaDeDemonstracao.EMAIL_CLINICA + "\",\"senha\":\"senha-da-demo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("CLINICA"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana.ribeiro@movimento.example\",\"senha\":\"senha-da-demo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("FISIOTERAPEUTA"));
    }

    @Test
    void oHistoricoTerminaAntesDeHojeEFicaNoExpediente() {
        demonstracao.criarSeNaoExistir();

        List<Sessao> todas = sessoes.findAll();
        assertThat(todas).hasSizeGreaterThan(150);
        assertThat(todas).allSatisfy(s -> {
            assertThat(s.getDataSessao()).isBefore(HOJE);
            assertThat(s.getDataSessao().getDayOfWeek()).isNotIn(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
            assertThat(s.getHoraSessao()).isBetween(LocalTime.of(7, 0), LocalTime.of(19, 0));
            assertThat(medicoes.findByIdSessao(s.getId())).isNotNull();
        });
        // Todo paciente começa o tratamento no dia da primeira sessão.
        for (Paciente p : pacientes.findAll()) {
            LocalDate primeira = todas.stream().filter(s -> s.getIdPaciente().equals(p.getId()))
                    .map(Sessao::getDataSessao).min(LocalDate::compareTo).orElseThrow();
            assertThat(p.getDataInicioTratamento()).isEqualTo(primeira);
        }
    }

    @Test
    void aAgendaNaoMarcaDoisPacientesNoMesmoHorarioDoMesmoProfissional() {
        demonstracao.criarSeNaoExistir();

        List<Agendamento> agenda = agendamentos.findAll();
        assertThat(agenda).extracting(Agendamento::getStatus).contains("AGENDADA", "FALTOU", "REMARCADA");
        Set<String> ocupados = new HashSet<>();
        for (Agendamento a : agenda) {
            String chave = a.getIdFisioterapeuta() + "|" + a.getDataAgendamento() + "|" + a.getHoraAgendamento();
            assertThat(ocupados.add(chave)).as("horário repetido: %s", chave).isTrue();
            if ("FALTOU".equals(a.getStatus())) {
                assertThat(a.getDataAgendamento()).isBefore(HOJE);
            } else {
                assertThat(a.getDataAgendamento()).isAfter(HOJE);
            }
        }
    }
}
