package com.rehabit;

import com.rehabit.model.Agendamento;
import com.rehabit.model.Clinica;
import com.rehabit.model.Fisioterapeuta;
import com.rehabit.model.Paciente;
import com.rehabit.model.RecuperacaoSenha;
import com.rehabit.model.Sessao;
import com.rehabit.repository.RecuperacaoSenhaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A clínica exclui um profissional: ele sai do sistema, mas o registro fica
 * para que o histórico continue dizendo quem de fato atendeu. Os pacientes e
 * a agenda futura passam para outro profissional da mesma clínica.
 */
class ExclusaoDeProfissionalTest extends TesteDeIntegracao {

    private static final LocalDate HOJE = LocalDate.now();

    @Autowired private RecuperacaoSenhaRepository recuperacoes;

    @Test
    void semPacientesEleEMarcadoComoExcluidoENaoApagado() throws Exception {
        Clinica clinica = novaClinica();
        Fisioterapeuta ana = novoProfissional(clinica);

        mvc.perform(delete("/api/fisioterapeutas/{id}", ana.getId())
                        .header(AUTHORIZATION, tokenDe(clinica)))
                .andExpect(status().isOk());

        Fisioterapeuta depois = fisioterapeutas.findById(ana.getId()).orElseThrow();
        assertThat(depois.getExcluidoEm()).isNotNull();
        assertThat(depois.getNome()).isEqualTo(ana.getNome());
    }

    @Test
    void aExclusaoLiberaEmailECoffitoParaUmNovoCadastro() throws Exception {
        Clinica clinica = novaClinica();
        Fisioterapeuta ana = novoProfissional(clinica);

        mvc.perform(delete("/api/fisioterapeutas/{id}", ana.getId())
                        .header(AUTHORIZATION, tokenDe(clinica)))
                .andExpect(status().isOk());

        assertThat(fisioterapeutas.existsByEmail(ana.getEmail())).isFalse();
        assertThat(fisioterapeutas.existsByCoffito(ana.getCoffito())).isFalse();
    }

    @Test
    void aSenhaAntigaDeixaDeValer() throws Exception {
        Clinica clinica = novaClinica();
        Fisioterapeuta ana = novoProfissional(clinica);

        mvc.perform(delete("/api/fisioterapeutas/{id}", ana.getId())
                        .header(AUTHORIZATION, tokenDe(clinica)))
                .andExpect(status().isOk());

        String senhaGuardada = fisioterapeutas.findById(ana.getId()).orElseThrow().getSenha();
        assertThat(passwordEncoder.matches(SENHA, senhaGuardada)).isFalse();
    }

    @Test
    void pacientesEAgendaFuturaPassamParaODestino() throws Exception {
        Clinica clinica = novaClinica();
        Fisioterapeuta ana = novoProfissional(clinica);
        Fisioterapeuta carlos = novoProfissional(clinica);
        Paciente joao = novoPaciente(ana);
        Agendamento futuro = novoAgendamento(joao, ana, HOJE.plusDays(3), LocalTime.of(10, 0));

        mvc.perform(delete("/api/fisioterapeutas/{id}", ana.getId())
                        .param("transferirPara", carlos.getId().toString())
                        .header(AUTHORIZATION, tokenDe(clinica)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pacientesTransferidos").value(1));

        assertThat(pacientes.findById(joao.getId()).orElseThrow().getIdFisioterapeuta()).isEqualTo(carlos.getId());
        assertThat(agendamentos.findById(futuro.getId()).orElseThrow().getIdFisioterapeuta()).isEqualTo(carlos.getId());
    }

    @Test
    void sessoesEAgendaPassadasContinuamComQuemAtendeu() throws Exception {
        Clinica clinica = novaClinica();
        Fisioterapeuta ana = novoProfissional(clinica);
        Fisioterapeuta carlos = novoProfissional(clinica);
        Paciente joao = novoPaciente(ana);
        Sessao passada = novaSessao(joao, ana, HOJE.minusDays(10));
        Agendamento agendaPassada = novoAgendamento(joao, ana, HOJE.minusDays(10), LocalTime.of(9, 0));

        mvc.perform(delete("/api/fisioterapeutas/{id}", ana.getId())
                        .param("transferirPara", carlos.getId().toString())
                        .header(AUTHORIZATION, tokenDe(clinica)))
                .andExpect(status().isOk());

        assertThat(sessoes.findById(passada.getId()).orElseThrow().getIdFisioterapeuta()).isEqualTo(ana.getId());
        assertThat(agendamentos.findById(agendaPassada.getId()).orElseThrow().getIdFisioterapeuta())
                .isEqualTo(ana.getId());
    }

    @Test
    void informaQuantasConsultasTransferidasColidiramComAAgendaDoDestino() throws Exception {
        Clinica clinica = novaClinica();
        Fisioterapeuta ana = novoProfissional(clinica);
        Fisioterapeuta carlos = novoProfissional(clinica);
        LocalDate dia = HOJE.plusDays(5);
        novoAgendamento(novoPaciente(ana), ana, dia, LocalTime.of(10, 0));
        novoAgendamento(novoPaciente(ana), ana, dia, LocalTime.of(15, 0));
        novoAgendamento(novoPaciente(carlos), carlos, dia, LocalTime.of(10, 0));

        mvc.perform(delete("/api/fisioterapeutas/{id}", ana.getId())
                        .param("transferirPara", carlos.getId().toString())
                        .header(AUTHORIZATION, tokenDe(clinica)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.colisoesDeAgenda").value(1));
    }

    @Test
    void comPacientesESemDestinoRecusaENadaMuda() throws Exception {
        Clinica clinica = novaClinica();
        Fisioterapeuta ana = novoProfissional(clinica);
        novoProfissional(clinica);
        Paciente joao = novoPaciente(ana);

        mvc.perform(delete("/api/fisioterapeutas/{id}", ana.getId())
                        .header(AUTHORIZATION, tokenDe(clinica)))
                .andExpect(status().isBadRequest());

        assertThat(fisioterapeutas.findById(ana.getId()).orElseThrow().getExcluidoEm()).isNull();
        assertThat(pacientes.findById(joao.getId()).orElseThrow().getIdFisioterapeuta()).isEqualTo(ana.getId());
    }

    @Test
    void destinoDeOutraClinicaERecusado() throws Exception {
        Clinica clinica = novaClinica();
        Fisioterapeuta ana = novoProfissional(clinica);
        // Um colega ativo na própria clínica: sem ele, o caso vira "ninguém
        // para receber os pacientes", que é outra regra (e outro teste).
        novoProfissional(clinica);
        novoPaciente(ana);
        Fisioterapeuta deFora = novoProfissional(novaClinica());

        mvc.perform(delete("/api/fisioterapeutas/{id}", ana.getId())
                        .param("transferirPara", deFora.getId().toString())
                        .header(AUTHORIZATION, tokenDe(clinica)))
                .andExpect(status().isBadRequest());

        assertThat(fisioterapeutas.findById(ana.getId()).orElseThrow().getExcluidoEm()).isNull();
    }

    @Test
    void naoDaParaTransferirParaOProprioProfissional() throws Exception {
        Clinica clinica = novaClinica();
        Fisioterapeuta ana = novoProfissional(clinica);
        novoProfissional(clinica);
        novoPaciente(ana);

        mvc.perform(delete("/api/fisioterapeutas/{id}", ana.getId())
                        .param("transferirPara", ana.getId().toString())
                        .header(AUTHORIZATION, tokenDe(clinica)))
                .andExpect(status().isBadRequest());

        assertThat(fisioterapeutas.findById(ana.getId()).orElseThrow().getExcluidoEm()).isNull();
    }

    @Test
    void naoDaParaTransferirParaUmProfissionalJaExcluido() throws Exception {
        Clinica clinica = novaClinica();
        Fisioterapeuta ana = novoProfissional(clinica);
        Fisioterapeuta carlos = novoProfissional(clinica);
        novoProfissional(clinica);
        novoPaciente(ana);
        mvc.perform(delete("/api/fisioterapeutas/{id}", carlos.getId())
                        .header(AUTHORIZATION, tokenDe(clinica)))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/fisioterapeutas/{id}", ana.getId())
                        .param("transferirPara", carlos.getId().toString())
                        .header(AUTHORIZATION, tokenDe(clinica)))
                .andExpect(status().isBadRequest());

        assertThat(fisioterapeutas.findById(ana.getId()).orElseThrow().getExcluidoEm()).isNull();
    }

    @Test
    void comPacientesESemOutroProfissionalAtivoPedeParaCadastrarUm() throws Exception {
        Clinica clinica = novaClinica();
        Fisioterapeuta ana = novoProfissional(clinica);
        novoPaciente(ana);

        mvc.perform(delete("/api/fisioterapeutas/{id}", ana.getId())
                        .header(AUTHORIZATION, tokenDe(clinica)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem", containsString("Cadastre outro profissional")));

        assertThat(fisioterapeutas.findById(ana.getId()).orElseThrow().getExcluidoEm()).isNull();
    }

    @Test
    void pedidoPendenteDeRecuperacaoDeSenhaNaoSobreviveAExclusao() throws Exception {
        Clinica clinica = novaClinica();
        Fisioterapeuta ana = novoProfissional(clinica);
        RecuperacaoSenha pedido = new RecuperacaoSenha();
        pedido.setEmail(ana.getEmail());
        pedido.setTipoConta("FISIOTERAPEUTA");
        pedido.setTokenHash("hash-do-link-" + proximo());
        pedido.setCodigoHash("hash-do-codigo");
        pedido.setCriadoEm(LocalDateTime.now());
        pedido.setExpiraEm(LocalDateTime.now().plusMinutes(30));
        recuperacoes.save(pedido);

        mvc.perform(delete("/api/fisioterapeutas/{id}", ana.getId())
                        .header(AUTHORIZATION, tokenDe(clinica)))
                .andExpect(status().isOk());

        assertThat(recuperacoes.findById(pedido.getId())).isEmpty();
    }

    @Test
    void excluirDuasVezesNaoFazNadaNaSegunda() throws Exception {
        Clinica clinica = novaClinica();
        Fisioterapeuta ana = novoProfissional(clinica);
        mvc.perform(delete("/api/fisioterapeutas/{id}", ana.getId())
                        .header(AUTHORIZATION, tokenDe(clinica)))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/fisioterapeutas/{id}", ana.getId())
                        .header(AUTHORIZATION, tokenDe(clinica)))
                .andExpect(status().isNotFound());
    }
}
