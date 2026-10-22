package com.rehabit;

import com.rehabit.model.Agendamento;
import com.rehabit.model.Clinica;
import com.rehabit.model.Configuracao;
import com.rehabit.model.Fisioterapeuta;
import com.rehabit.model.Goniometro;
import com.rehabit.model.Paciente;
import com.rehabit.repository.ConfiguracaoRepository;
import com.rehabit.repository.GoniometroRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Das 21h à meia-noite em Brasília, o servidor (em UTC, no Render) já está no
 * dia seguinte. A agenda, a exclusão de um profissional, o sino da clínica e o
 * goniômetro pedem "agora" ao relógio da API e o convertem para o fuso das
 * clínicas: para eles, ainda é hoje.
 *
 * O relógio para numa noite que já passou. Assim o LocalDate.now() de verdade
 * nunca cai no mesmo dia, e um carimbo tirado da máquina não passa por acaso.
 */
class NoiteEmBrasiliaTest extends TesteDeIntegracao {

    /** Terça, 22/09/2026, 21h30 em Brasília. Em UTC, já é quarta, 00h30. */
    private static final Instant NOITE = Instant.parse("2026-09-23T00:30:00Z");
    private static final LocalDate HOJE = LocalDate.of(2026, 9, 22);

    @TestConfiguration
    static class Relogio {
        @Bean
        @Primary
        RelogioDeTeste relogioDeTeste() {
            return new RelogioDeTeste(NOITE);
        }
    }

    @Autowired private RelogioDeTeste relogio;
    @Autowired private ConfiguracaoRepository configuracoes;
    @Autowired private GoniometroRepository goniometros;

    @BeforeEach
    void voltarParaANoite() {
        relogio.irPara(NOITE);
    }

    /** Clínica que atende até as 23h, para caber uma consulta à noite. */
    private Clinica clinicaAbertaAte23h() {
        Clinica clinica = novaClinica();
        Configuracao horario = new Configuracao();
        horario.setTipoUsuario("CLINICA");
        horario.setIdUsuario(clinica.getId());
        horario.setHoraAbertura(LocalTime.of(8, 0));
        horario.setHoraFechamento(LocalTime.of(23, 0));
        configuracoes.save(horario);
        return clinica;
    }

    private ResultActions marcarHoje(Fisioterapeuta profissional, Paciente paciente, String hora) throws Exception {
        return mvc.perform(post("/api/agendamentos")
                .header(AUTHORIZATION, tokenDe(profissional))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPaciente\":" + paciente.getId() + ",\"data\":\"" + HOJE + "\",\"hora\":\"" + hora + "\"}"));
    }

    @Test
    void aConsultaMaisTardeNaMesmaNoiteAindaPodeSerMarcada() throws Exception {
        Fisioterapeuta ana = novoProfissional(clinicaAbertaAte23h());
        Paciente joao = novoPaciente(ana);

        // 20h30 em Brasília, 23h30 em UTC: a das 21h ainda está por vir.
        relogio.irPara(Instant.parse("2026-09-22T23:30:00Z"));
        marcarHoje(ana, joao, "21:00").andExpect(status().isCreated());

        // 21h30: a das 21h já passou; a das 22h, não.
        relogio.irPara(NOITE);
        marcarHoje(ana, joao, "21:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", containsString("já passou")));
        marcarHoje(ana, joao, "22:00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data").value("2026-09-22"));
    }

    @Test
    void remarcarParaMaisTardeNaMesmaNoiteTambemPassa() throws Exception {
        Fisioterapeuta ana = novoProfissional(clinicaAbertaAte23h());
        Agendamento deAmanha = novoAgendamento(novoPaciente(ana), ana, HOJE.plusDays(1), LocalTime.of(10, 0));

        mvc.perform(put("/api/agendamentos/{id}/remarcar", deAmanha.getId())
                        .header(AUTHORIZATION, tokenDe(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"" + HOJE + "\",\"hora\":\"22:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("2026-09-22"))
                .andExpect(jsonPath("$.status").value("REMARCADA"));
    }

    @Test
    void excluirOProfissionalANoiteLevaAsConsultasDeHojeParaQuemFica() throws Exception {
        Clinica clinica = novaClinica();
        Fisioterapeuta ana = novoProfissional(clinica);
        Fisioterapeuta carlos = novoProfissional(clinica);
        Paciente joao = novoPaciente(ana);
        Agendamento deOntem = novoAgendamento(joao, ana, HOJE.minusDays(1), LocalTime.of(10, 0));
        Agendamento deHoje = novoAgendamento(joao, ana, HOJE, LocalTime.of(19, 0));
        Agendamento deAmanha = novoAgendamento(joao, ana, HOJE.plusDays(1), LocalTime.of(10, 0));

        mvc.perform(delete("/api/fisioterapeutas/{id}", ana.getId())
                        .param("transferirPara", carlos.getId().toString())
                        .header(AUTHORIZATION, tokenDe(clinica)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consultasTransferidas").value(2));

        assertThat(agendamentos.findById(deHoje.getId()).orElseThrow().getIdFisioterapeuta()).isEqualTo(carlos.getId());
        assertThat(agendamentos.findById(deAmanha.getId()).orElseThrow().getIdFisioterapeuta()).isEqualTo(carlos.getId());
        assertThat(agendamentos.findById(deOntem.getId()).orElseThrow().getIdFisioterapeuta()).isEqualTo(ana.getId());
        assertThat(fisioterapeutas.findById(ana.getId()).orElseThrow().getExcluidoEm())
                .isEqualTo(LocalDateTime.of(HOJE, LocalTime.of(21, 30)));
    }

    @Test
    void oSinoDaClinicaMostraAHoraDeBrasilia() throws Exception {
        Clinica clinica = novaClinica();
        Fisioterapeuta ana = novoProfissional(clinica);
        Paciente joao = novoPaciente(ana);

        mvc.perform(post("/api/pacientes/{id}/sessoes", joao.getId())
                        .header(AUTHORIZATION, tokenDe(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"duracao\":45,\"idFisioterapeuta\":" + ana.getId() + "}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/notificacoes").param("idClinica", clinica.getId().toString())
                        .header(AUTHORIZATION, tokenDe(clinica)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("NOVA_SESSAO"))
                .andExpect(jsonPath("$[0].criadaEm").value("2026-09-22T21:30:00"));
    }

    @Test
    void sincronizarOGoniometroANoiteCarimbaODiaEAHoraDeBrasilia() throws Exception {
        Clinica clinica = novaClinica();

        mvc.perform(post("/api/goniometro/sincronizar")
                        .header(AUTHORIZATION, tokenDe(clinica))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idClinica\":" + clinica.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataSincronizacao").value("2026-09-22"))
                .andExpect(jsonPath("$.horaSincronizacao").value("21:30:00"));
        // O estado ao vivo devolve o mesmo carimbo, já formatado.
        mvc.perform(get("/api/goniometro/estado").param("idClinica", clinica.getId().toString())
                        .header(AUTHORIZATION, tokenDe(clinica)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sincronizadoEm").value("22/09/2026 21:30:00"));
    }

    @Test
    void oPrimeiroContatoDoAparelhoANoiteCarimbaODiaDeBrasilia() throws Exception {
        Clinica clinica = novaClinica();

        mvc.perform(post("/api/goniometro/telemetria")
                        .header(AUTHORIZATION, tokenDe(clinica))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idClinica\":" + clinica.getId() + ",\"angulo\":42.5}"))
                .andExpect(status().isOk());

        Goniometro cadastro = goniometros.findFirstByIdClinicaOrderByIdDesc(clinica.getId()).orElseThrow();
        assertThat(cadastro.getDataSincronizacao()).isEqualTo(HOJE);
        assertThat(cadastro.getHoraSincronizacao()).isEqualTo(LocalTime.of(21, 30));
    }
}
