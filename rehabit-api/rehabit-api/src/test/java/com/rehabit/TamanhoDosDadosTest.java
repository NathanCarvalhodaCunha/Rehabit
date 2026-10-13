package com.rehabit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rehabit.model.Clinica;
import com.rehabit.model.Fisioterapeuta;
import com.rehabit.model.Paciente;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Os campos de texto livre (anamnese, descrição, observações) iam para
 * colunas TEXT sem limite nenhum, e o corpo da requisição não tinha teto: dava
 * para gravar megabytes por campo e encher o banco. Agora cada campo tem
 * tamanho máximo, e o corpo inteiro também.
 */
class TamanhoDosDadosTest extends TesteDeIntegracao {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void anamneseGiganteERecusadaComMensagem() throws Exception {
        Fisioterapeuta profissional = novoProfissional(novaClinica());
        Paciente paciente = novoPaciente(profissional);

        mvc.perform(put("/api/pacientes/{id}", paciente.getId())
                        .header("Authorization", tokenDe(profissional))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "nome", paciente.getNome(), "queixaPrincipal", "a".repeat(10_001)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", containsString("10000 caracteres")));

        mvc.perform(put("/api/pacientes/{id}", paciente.getId())
                        .header("Authorization", tokenDe(profissional))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "nome", paciente.getNome(), "queixaPrincipal", "Dor no ombro direito. ".repeat(10)))))
                .andExpect(status().isOk());
    }

    @Test
    void descricaoDaClinicaEObservacoesTemTeto() throws Exception {
        Clinica clinica = novaClinica();
        mvc.perform(put("/api/clinicas/{id}", clinica.getId())
                        .header("Authorization", tokenDe(clinica))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "nome", clinica.getNome(), "cnpj", clinica.getCnpj(), "email", clinica.getEmail(),
                                "descricao", "d".repeat(5_001)))))
                .andExpect(status().isBadRequest());

        Fisioterapeuta profissional = novoProfissional(clinica);
        Paciente paciente = novoPaciente(profissional);
        mvc.perform(post("/api/pacientes/{id}/sessoes", paciente.getId())
                        .header("Authorization", tokenDe(profissional))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "duracao", 30, "observacoes", "o".repeat(5_001),
                                "idFisioterapeuta", profissional.getId()))))
                .andExpect(status().isBadRequest());
        assertThat(sessoes.count()).isZero();

        mvc.perform(post("/api/agendamentos")
                        .header("Authorization", tokenDe(profissional))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "idPaciente", paciente.getId(), "data", LocalDate.now().plusDays(1).toString(),
                                "hora", "10:00", "observacao", "x".repeat(256)))))
                .andExpect(status().isBadRequest());
        assertThat(agendamentos.count()).isZero();
    }

    @Test
    void remarcarTambemConfereAObservacao() throws Exception {
        Fisioterapeuta profissional = novoProfissional(novaClinica());
        Paciente paciente = novoPaciente(profissional);
        var agendamento = novoAgendamento(paciente, profissional, LocalDate.now().plusDays(2), java.time.LocalTime.of(10, 0));

        mvc.perform(put("/api/agendamentos/{id}/remarcar", agendamento.getId())
                        .header("Authorization", tokenDe(profissional))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "data", LocalDate.now().plusDays(3).toString(), "hora", "11:00",
                                "observacao", "x".repeat(256)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", containsString("255 caracteres")));
    }

    @Test
    void dizerQueEMultipartNaoTiraOTetoDasRotasJson() throws Exception {
        // Só as duas rotas de foto ficam com os 5 MB do multipart.
        mvc.perform(post("/api/auth/login")
                        .contentType("multipart/form-data; boundary=x")
                        .content("x".repeat(300 * 1024)))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void corpoDeFormularioNaoELidoAntesDaAutenticacao() {
        // O FormContentFilter lia corpos form-urlencoded de PUT/PATCH/DELETE
        // de qualquer um; acima do teto, estourava como 500.
        assertThat(contexto.getBeanNamesForType(org.springframework.web.filter.FormContentFilter.class)).isEmpty();
    }

    @Test
    void corpoAcimaDoTetoNemELido() throws Exception {
        Fisioterapeuta profissional = novoProfissional(novaClinica());
        Paciente paciente = novoPaciente(profissional);
        String enorme = "{\"nome\":\"x\",\"historicoClinico\":\"" + "h".repeat(300 * 1024) + "\"}";

        mvc.perform(put("/api/pacientes/{id}", paciente.getId())
                        .header("Authorization", tokenDe(profissional))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(enorme))
                .andExpect(status().isPayloadTooLarge());
        assertThat(pacientes.findById(paciente.getId()).orElseThrow().getNome()).isEqualTo(paciente.getNome());
    }
}
