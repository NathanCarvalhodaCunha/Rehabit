package com.rehabit;

import com.rehabit.demo.ContaDeDemonstracao;
import com.rehabit.model.Clinica;
import com.rehabit.model.Fisioterapeuta;
import com.rehabit.model.Paciente;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A senha da conta de demonstração está publicada na página inicial: qualquer
 * visitante entra. Por isso ela é só de leitura — senão o primeiro curioso
 * exclui a clínica, troca a senha ou apaga os pacientes, e a demonstração
 * acaba para todo mundo.
 */
class ContaDeDemonstracaoSoLeituraTest extends TesteDeIntegracao {

    private Clinica clinicaDeDemonstracao() {
        Clinica clinica = novaClinica();
        clinica.setEmail(ContaDeDemonstracao.EMAIL_CLINICA);
        return clinicas.save(clinica);
    }

    @Test
    void aDemonstracaoConsultaNormalmente() throws Exception {
        Clinica demo = clinicaDeDemonstracao();

        mvc.perform(get("/api/clinicas/" + demo.getId()).header("Authorization", tokenDe(demo)))
                .andExpect(status().isOk());
    }

    @Test
    void aClinicaDaDemonstracaoNaoSeExclui() throws Exception {
        Clinica demo = clinicaDeDemonstracao();

        mvc.perform(post("/api/clinicas/" + demo.getId() + "/exclusao")
                        .header("Authorization", tokenDe(demo))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senha\":\"" + SENHA + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensagem").value(containsString("demonstração")));

        assertThat(clinicas.existsById(demo.getId())).isTrue();
    }

    @Test
    void oProfissionalDaDemonstracaoNaoAlteraPaciente() throws Exception {
        Fisioterapeuta ana = novoProfissional(clinicaDeDemonstracao());
        Paciente paciente = novoPaciente(ana);

        mvc.perform(put("/api/pacientes/" + paciente.getId())
                        .header("Authorization", tokenDe(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Nome trocado por um visitante\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensagem").value(containsString("demonstração")));

        assertThat(pacientes.findById(paciente.getId()).orElseThrow().getNome()).isEqualTo(paciente.getNome());
    }

    @Test
    void oProfissionalDaDemonstracaoNaoSeExclui() throws Exception {
        Fisioterapeuta ana = novoProfissional(clinicaDeDemonstracao());

        mvc.perform(delete("/api/fisioterapeutas/" + ana.getId()).header("Authorization", tokenDe(ana)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensagem").value(containsString("demonstração")));

        assertThat(fisioterapeutas.findById(ana.getId()).orElseThrow().isExcluido()).isFalse();
    }

    @Test
    void umaClinicaComumContinuaAlterando() throws Exception {
        Clinica comum = novaClinica();

        mvc.perform(put("/api/clinicas/" + comum.getId() + "/tutorial-visto").header("Authorization", tokenDe(comum)))
                .andExpect(status().is2xxSuccessful());
    }
}
