package com.rehabit;

import com.rehabit.model.Clinica;
import com.rehabit.model.Dispositivo;
import com.rehabit.repository.DispositivoRepository;
import com.rehabit.service.GoniometroService;
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
    @Autowired GoniometroService goniometroService;

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

    /**
     * O caso que motivou o campo: o aparelho some no meio da gravação, o vigia
     * do servidor fecha a captura depois de 8 s sem pacote e não tem como
     * mandar o PARAR_CAPTURA. Quando o aparelho volta, a primeira resposta já
     * diz que não há captura aberta.
     */
    @Test
    void capturaFechadaPeloVigiaChegaComoFalseQuandoOAparelhoVolta() throws Exception {
        Clinica clinica = novaClinica();
        String aparelho = tokenDeAparelho(clinica);

        enviarTelemetria(aparelho).andExpect(status().isOk());
        mvc.perform(post("/api/goniometro/captura/iniciar")
                        .header("Authorization", tokenDe(clinica))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idClinica\":" + clinica.getId() + "}"))
                .andExpect(status().isOk());
        enviarTelemetria(aparelho).andExpect(jsonPath("$.capturando").value(true));

        // Passa do limite de 8 s sem pacote e roda o vigia na hora, sem
        // esperar o agendamento (se ele já tiver rodado, esta chamada não faz nada).
        Thread.sleep(8_500);
        goniometroService.vigiarConexoes();

        enviarTelemetria(aparelho)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comando").value("NENHUM"))
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
