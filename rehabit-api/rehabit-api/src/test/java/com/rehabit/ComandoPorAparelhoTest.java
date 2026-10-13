package com.rehabit;

import com.rehabit.model.Clinica;
import com.rehabit.model.Dispositivo;
import com.rehabit.repository.DispositivoRepository;
import com.rehabit.service.GoniometroStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Com dois goniômetros pareados na mesma clínica, a fila de comandos era uma
 * só: o primeiro que mandasse leitura levava a tara, a captura ou o
 * REINICIAR que a tela tinha mandado para o outro. Agora o comando vai para o
 * aparelho que estava mandando leituras quando foi dado.
 */
class ComandoPorAparelhoTest extends TesteDeIntegracao {

    @Autowired private DispositivoRepository dispositivos;
    @Autowired private GoniometroStream stream;

    private String tokenDeAparelho(Clinica clinica) {
        Dispositivo aparelho = new Dispositivo();
        aparelho.setNome("Goniômetro " + proximo());
        aparelho.setIdClinica(clinica.getId());
        aparelho.setAtivo(true);
        aparelho.setCriadoEm(LocalDateTime.now());
        aparelho = dispositivos.save(aparelho);
        return "Bearer " + jwtService.gerarTokenDeDispositivo(aparelho.getId(), clinica.getId(), 60_000);
    }

    private ResultActions telemetria(String token) throws Exception {
        return mvc.perform(post("/api/goniometro/telemetria")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"angulo\":30}"));
    }

    @Test
    void oOutroAparelhoDaClinicaNaoLevaOComando() throws Exception {
        Clinica clinica = novaClinica();
        String a = tokenDeAparelho(clinica);
        String b = tokenDeAparelho(clinica);

        telemetria(a).andExpect(status().isOk());
        mvc.perform(post("/api/goniometro/comando")
                        .header("Authorization", tokenDe(clinica))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idClinica\":" + clinica.getId() + ",\"comando\":\"TARAR\"}"))
                .andExpect(status().isOk());

        telemetria(b).andExpect(jsonPath("$.comando").value("NENHUM"));
        telemetria(a).andExpect(jsonPath("$.comando").value("TARAR"));
        telemetria(a).andExpect(jsonPath("$.comando").value("NENHUM"));
    }

    @Test
    void cadaPessoaTemNoMaximoTresTelasDoGoniometro() throws Exception {
        Clinica clinica = novaClinica();
        for (int i = 0; i < 4; i++) {
            String corpo = mvc.perform(post("/api/goniometro/stream/ingresso")
                            .param("idClinica", String.valueOf(clinica.getId()))
                            .header("Authorization", tokenDe(clinica)))
                    .andReturn().getResponse().getContentAsString();
            String ingresso = corpo.replaceAll(".*\"ingresso\":\"([^\"]+)\".*", "$1");
            mvc.perform(get("/api/goniometro/stream")
                            .param("idClinica", String.valueOf(clinica.getId()))
                            .param("ingresso", ingresso))
                    .andExpect(request().asyncStarted());
        }
        assertThat(stream.abertas(clinica.getId())).isEqualTo(3);
    }
}
