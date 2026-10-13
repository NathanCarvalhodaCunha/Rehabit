package com.rehabit;

import com.rehabit.model.Clinica;
import com.rehabit.model.Dispositivo;
import com.rehabit.model.Fisioterapeuta;
import com.rehabit.model.Paciente;
import com.rehabit.repository.DispositivoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Os números que chegam do aparelho e das telas. Um "angulo": 1e999999999
 * passava pela validação (só @NotNull) e o arredondamento montava um número
 * de um bilhão de dígitos: um pacote travava a CPU da API. Um ângulo de 999°
 * ia para o estado, o gráfico e a curva do prontuário. E uma meta com escala
 * absurda virava 500.
 */
class AnguloDoGoniometroTest extends TesteDeIntegracao {

    @Autowired private DispositivoRepository dispositivos;

    private String tokenDeAparelho(Clinica clinica) {
        Dispositivo aparelho = new Dispositivo();
        aparelho.setNome("Goniômetro");
        aparelho.setIdClinica(clinica.getId());
        aparelho.setAtivo(true);
        aparelho.setCriadoEm(LocalDateTime.now());
        aparelho = dispositivos.save(aparelho);
        return "Bearer " + jwtService.gerarTokenDeDispositivo(aparelho.getId(), clinica.getId(), 60_000);
    }

    private ResultActions telemetria(String token, String corpo) throws Exception {
        return mvc.perform(post("/api/goniometro/telemetria")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));
    }

    /** Responde 400, e rápido: o número não chega a ser materializado. */
    private void recusadoNaHora(ResultActions resposta, long inicio) throws Exception {
        resposta.andExpect(status().isBadRequest());
        assertThat(System.nanoTime() - inicio).as("tempo de resposta").isLessThan(2_000_000_000L);
    }

    @Test
    void anguloComExpoenteGiganteERecusadoSemTravar() throws Exception {
        Clinica clinica = novaClinica();
        String aparelho = tokenDeAparelho(clinica);

        long t = System.nanoTime();
        recusadoNaHora(telemetria(aparelho, "{\"angulo\":1e999999999}"), t);
        t = System.nanoTime();
        recusadoNaHora(telemetria(aparelho, "{\"angulo\":1e-999999999}"), t);
        t = System.nanoTime();
        recusadoNaHora(telemetria(aparelho, "{\"angulo\":45,\"anguloBruto\":1e999999999}"), t);
        t = System.nanoTime();
        recusadoNaHora(mvc.perform(post("/api/goniometro/leitura")
                .header("Authorization", aparelho)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"angulo\":-1e999999999}")), t);
        // A clínica testando sem hardware passa pelo mesmo crivo.
        t = System.nanoTime();
        recusadoNaHora(telemetria(tokenDe(clinica),
                "{\"idClinica\":" + clinica.getId() + ",\"angulo\":1e999999999}"), t);
    }

    @Test
    void anguloForaDaFaixaNaoMexeNoEstado() throws Exception {
        Clinica clinica = novaClinica();
        String aparelho = tokenDeAparelho(clinica);

        telemetria(aparelho, "{\"angulo\":45.25,\"anguloBruto\":-12.5}").andExpect(status().isOk());
        telemetria(aparelho, "{\"angulo\":999}").andExpect(status().isBadRequest());
        telemetria(aparelho, "{\"angulo\":-720}").andExpect(status().isBadRequest());
        telemetria(aparelho, "{\"angulo\":90,\"anguloBruto\":500}").andExpect(status().isBadRequest());

        mvc.perform(get("/api/goniometro/estado").param("idClinica", String.valueOf(clinica.getId()))
                        .header("Authorization", tokenDe(clinica)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.angulo").value(45.3));
    }

    @Test
    void oQueOFirmwareMandaContinuaAceito() throws Exception {
        Clinica clinica = novaClinica();
        // Formato do goniometro-esp32.ino: String(angulo, 2), de 0 a 180.
        telemetria(tokenDeAparelho(clinica),
                "{\"angulo\":0.00,\"anguloBruto\":179.99,\"bateria\":87,\"rssi\":-61,"
                        + "\"numeroSerie\":\"A1B2C3D4E5F6\",\"firmware\":\"2.3.0\",\"ip\":\"192.168.0.23\",\"calibrado\":true}")
                .andExpect(status().isOk());
    }

    @Test
    void textoGiganteNaTelemetriaERecusado() throws Exception {
        Clinica clinica = novaClinica();
        telemetria(tokenDeAparelho(clinica), "{\"angulo\":10,\"numeroSerie\":\"" + "A".repeat(10_000) + "\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void metaDeAmplitudeComEscalaAbsurdaDa400ENao500() throws Exception {
        Fisioterapeuta profissional = novoProfissional(novaClinica());
        Paciente paciente = novoPaciente(profissional);

        for (String meta : new String[] {"1e-1000000", "99999", "1234.56", "-1", "90.123"}) {
            mvc.perform(put("/api/pacientes/{id}", paciente.getId())
                            .header("Authorization", tokenDe(profissional))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nome\":\"" + paciente.getNome() + "\",\"metaAmplitude\":" + meta + "}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(put("/api/pacientes/{id}", paciente.getId())
                        .header("Authorization", tokenDe(profissional))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + paciente.getNome() + "\",\"metaAmplitude\":120.5}"))
                .andExpect(status().isOk());
    }

    @Test
    void amplitudeDaSessaoComEscalaAbsurdaERecusada() throws Exception {
        Fisioterapeuta profissional = novoProfissional(novaClinica());
        Paciente paciente = novoPaciente(profissional);

        long t = System.nanoTime();
        recusadoNaHora(mvc.perform(post("/api/pacientes/{id}/sessoes", paciente.getId())
                .header("Authorization", tokenDe(profissional))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"duracao\":30,\"amplitudeMedia\":1e-999999999,\"idFisioterapeuta\":"
                        + profissional.getId() + "}")), t);
    }
}
