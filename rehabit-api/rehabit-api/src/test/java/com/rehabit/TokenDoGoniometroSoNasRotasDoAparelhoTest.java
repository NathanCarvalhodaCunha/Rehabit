package com.rehabit;

import com.rehabit.model.Clinica;
import com.rehabit.model.Dispositivo;
import com.rehabit.model.Fisioterapeuta;
import com.rehabit.model.Paciente;
import com.rehabit.repository.DispositivoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Qualquer clínica ganha um token de goniômetro pareando o próprio aparelho, e
 * o id do dispositivo sai de uma sequência que não tem nada a ver com a dos
 * profissionais. Nas rotas de tela, esse id caía no ramo "profissional" e
 * abria os pacientes, alertas e estatísticas de quem tivesse o mesmo número
 * em outra clínica — e até a troca de senha dele. O token do aparelho agora
 * só vale para enviar leituras.
 */
class TokenDoGoniometroSoNasRotasDoAparelhoTest extends TesteDeIntegracao {

    @Autowired private DispositivoRepository dispositivos;

    /** O token que o atacante consegue: da clínica dele, com o número do profissional de outra. */
    private String tokenDeAparelhoComOIdDe(Fisioterapeuta vitima, Clinica doAtacante) {
        return "Bearer " + jwtService.gerarTokenDeDispositivo(vitima.getId(), doAtacante.getId(), 60_000);
    }

    @Test
    void naoLeDadosClinicosDoProfissionalDeMesmoNumeroEmOutraClinica() throws Exception {
        Clinica doAtacante = novaClinica();
        Fisioterapeuta vitima = novoProfissional(novaClinica());
        Paciente paciente = novoPaciente(vitima);
        String token = tokenDeAparelhoComOIdDe(vitima, doAtacante);

        mvc.perform(get("/api/busca").param("q", paciente.getNome()).header("Authorization", token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/alertas").header("Authorization", token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/estatisticas").header("Authorization", token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/configuracoes").header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    @Test
    void naoTrocaASenhaDoProfissionalDeMesmoNumero() throws Exception {
        Clinica doAtacante = novaClinica();
        Fisioterapeuta vitima = novoProfissional(novaClinica());
        String senhaGuardada = vitima.getSenha();

        mvc.perform(put("/api/configuracoes/senha")
                        .header("Authorization", tokenDeAparelhoComOIdDe(vitima, doAtacante))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"" + SENHA + "\",\"novaSenha\":\"tomada-pelo-atacante\"}"))
                .andExpect(status().isForbidden());

        assertThat(fisioterapeutas.findById(vitima.getId()).orElseThrow().getSenha()).isEqualTo(senhaGuardada);
    }

    @Test
    void oAparelhoContinuaMandandoTelemetria() throws Exception {
        Clinica clinica = novaClinica();
        Dispositivo aparelho = new Dispositivo();
        aparelho.setNome("Goniômetro da parede");
        aparelho.setIdClinica(clinica.getId());
        aparelho.setAtivo(true);
        aparelho.setCriadoEm(LocalDateTime.now());
        aparelho = dispositivos.save(aparelho);
        String token = "Bearer " + jwtService.gerarTokenDeDispositivo(aparelho.getId(), clinica.getId(), 60_000);

        mvc.perform(post("/api/goniometro/telemetria").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"angulo\":42.5}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/goniometro/leitura").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"angulo\":42.5}"))
                .andExpect(status().isOk());
        // Ler o estado é coisa da tela, não do aparelho.
        mvc.perform(get("/api/goniometro/estado").param("idClinica", String.valueOf(clinica.getId()))
                        .header("Authorization", token))
                .andExpect(status().isForbidden());
    }
}
