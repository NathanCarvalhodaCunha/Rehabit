package com.rehabit;

import com.rehabit.model.Clinica;
import com.rehabit.model.Dispositivo;
import com.rehabit.repository.DispositivoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A resposta da telemetria é o único canal do servidor para o aparelho. Ela
 * diz se há captura aberta para o goniômetro poder encerrar a dele quando o
 * servidor já fechou — o caso da conexão que cai no meio de uma gravação, em
 * que o PARAR_CAPTURA nunca chega.
 */
class RespostaDaTelemetriaTest extends TesteDeIntegracao {

    @Autowired DispositivoRepository dispositivos;

    @Test
    void aRespostaAcompanhaOEstadoDaCaptura() throws Exception {
        Clinica clinica = novaClinica();
        String aparelho = tokenDeAparelho(clinica);
        String corpoClinica = "{\"idClinica\":" + clinica.getId() + "}";

        enviarTelemetria(aparelho)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capturando").value(false));

        mvc.perform(post("/api/goniometro/captura/iniciar")
                        .header("Authorization", tokenDe(clinica))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoClinica))
                .andExpect(status().isOk());

        enviarTelemetria(aparelho)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comando").value("INICIAR_CAPTURA"))
                .andExpect(jsonPath("$.capturando").value(true));

        mvc.perform(post("/api/goniometro/captura/parar")
                        .header("Authorization", tokenDe(clinica))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoClinica))
                .andExpect(status().isOk());

        enviarTelemetria(aparelho)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comando").value("PARAR_CAPTURA"))
                .andExpect(jsonPath("$.capturando").value(false));
    }

    private ResultActions enviarTelemetria(String tokenDoAparelho) throws Exception {
        return mvc.perform(post("/api/goniometro/telemetria")
                .header("Authorization", tokenDoAparelho)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"angulo\":42.5}"));
    }

    private String tokenDeAparelho(Clinica clinica) {
        Dispositivo d = new Dispositivo();
        d.setNome("Goniômetro de teste");
        d.setIdClinica(clinica.getId());
        d.setAtivo(true);
        d.setCriadoEm(LocalDateTime.now());
        Dispositivo salvo = dispositivos.save(d);
        return "Bearer " + jwtService.gerarTokenDeDispositivo(salvo.getId(), clinica.getId(), 60_000);
    }
}
