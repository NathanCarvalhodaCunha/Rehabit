package com.rehabit;

import com.rehabit.model.Clinica;
import com.rehabit.model.Fisioterapeuta;
import com.rehabit.model.Medicao;
import com.rehabit.model.Paciente;
import com.rehabit.model.Sessao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O cartão de alertas da Home mostra a descrição como a API a escreve. O
 * BigDecimal do banco ia direto para o texto ("caiu de 95.50° para 88.25°"),
 * a data saía no formato ISO ("venceu em 2026-10-01") e o paciente sem sessão
 * aparecia como "cadastrado" mesmo quando era uma paciente.
 */
class TextoDosAlertasTest extends TesteDeIntegracao {

    private Fisioterapeuta profissional;

    @BeforeEach
    void montarClinica() {
        Clinica clinica = novaClinica();
        profissional = novoProfissional(clinica);
    }

    @Test
    void semSessaoNaoSupoeOGeneroDoPaciente() throws Exception {
        Paciente paciente = novoPaciente(profissional);

        mvc.perform(get("/api/alertas").header("Authorization", tokenDe(profissional)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].titulo").value("Sem nenhuma sessão"))
                .andExpect(jsonPath("$[0].descricao")
                        .value(paciente.getNome() + " ainda não teve nenhuma sessão registrada desde o cadastro."));
    }

    @Test
    void regressaoFalaDaAmplitudeComVirgulaDecimal() throws Exception {
        Paciente paciente = novoPaciente(profissional);
        sessaoCom(paciente, LocalDate.now().minusDays(10), "95.50");
        sessaoCom(paciente, LocalDate.now().minusDays(5), "92.00");
        sessaoCom(paciente, LocalDate.now().minusDays(1), "88.25");

        mvc.perform(get("/api/alertas").header("Authorization", tokenDe(profissional)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].titulo").value("Regressão na amplitude"))
                .andExpect(jsonPath("$[0].descricao").value("A amplitude de " + paciente.getNome()
                        + " caiu de 95,5° para 88,25° nas últimas medições."));
    }

    @Test
    void metaAtingidaSemCasasDecimaisSobrando() throws Exception {
        Paciente paciente = novoPaciente(profissional);
        paciente.setMetaAmplitude(new BigDecimal("120.00"));
        pacientes.save(paciente);
        sessaoCom(paciente, LocalDate.now().minusDays(2), "120.00");

        mvc.perform(get("/api/alertas").header("Authorization", tokenDe(profissional)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].titulo").value("Meta atingida"))
                .andExpect(jsonPath("$[0].descricao")
                        .value(paciente.getNome() + " alcançou 120°, a meta era 120°."));
    }

    @Test
    void metaVencidaComADataNoFormatoBrasileiro() throws Exception {
        LocalDate prazo = LocalDate.now().minusDays(30);
        Paciente paciente = novoPaciente(profissional);
        paciente.setMetaAmplitude(new BigDecimal("120.00"));
        paciente.setMetaData(prazo);
        pacientes.save(paciente);
        sessaoCom(paciente, LocalDate.now().minusDays(2), "90.50");

        mvc.perform(get("/api/alertas").header("Authorization", tokenDe(profissional)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].titulo").value("Meta vencida"))
                .andExpect(jsonPath("$[0].descricao").value(paciente.getNome() + " está em 90,5° e a meta de 120° venceu em "
                        + prazo.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + "."));
    }

    /** Sessão realizada com a amplitude pedida (a da base sai sempre com 90°). */
    private void sessaoCom(Paciente paciente, LocalDate data, String amplitude) {
        Sessao sessao = novaSessao(paciente, profissional, data);
        Medicao medicao = medicoes.findByIdSessao(sessao.getId());
        medicao.setAmplitudeMedia(new BigDecimal(amplitude));
        medicoes.save(medicao);
    }
}
