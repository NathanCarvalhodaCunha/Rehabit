package com.rehabit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rehabit.model.Clinica;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O SSE do goniômetro recebia o token de sessão na URL — 30 dias de acesso à
 * API inteira gravados em log de proxy e no histórico do navegador. Agora a
 * URL leva só um ingresso de uso único, que vence em 60 segundos e só abre o
 * stream da clínica para a qual foi pedido.
 */
class IngressoDoStreamTest extends TesteDeIntegracao {

    static final RelogioDeTeste RELOGIO = new RelogioDeTeste(Instant.parse("2026-10-13T12:00:00Z"));

    @TestConfiguration
    static class RelogioControlado {
        @Bean
        @Primary
        Clock relogioDoIngresso() {
            return RELOGIO;
        }
    }

    private final ObjectMapper json = new ObjectMapper();

    private String pedirIngresso(Clinica clinica) throws Exception {
        String corpo = mvc.perform(post("/api/goniometro/stream/ingresso")
                        .param("idClinica", String.valueOf(clinica.getId()))
                        .header("Authorization", tokenDe(clinica)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(corpo).get("ingresso").asText();
    }

    private org.springframework.test.web.servlet.ResultActions abrirStream(Clinica clinica, String ingresso)
            throws Exception {
        return mvc.perform(get("/api/goniometro/stream")
                .param("idClinica", String.valueOf(clinica.getId()))
                .param("ingresso", ingresso));
    }

    @Test
    void oIngressoAbreOStreamUmaVezSo() throws Exception {
        Clinica clinica = novaClinica();
        String ingresso = pedirIngresso(clinica);

        abrirStream(clinica, ingresso).andExpect(request().asyncStarted());
        // Copiado de um log depois de usado, já não abre nada.
        abrirStream(clinica, ingresso).andExpect(status().isUnauthorized());
    }

    @Test
    void oIngressoVenceEmUmMinuto() throws Exception {
        Clinica clinica = novaClinica();
        String ingresso = pedirIngresso(clinica);

        RELOGIO.avancar(Duration.ofSeconds(61));
        abrirStream(clinica, ingresso).andExpect(status().isUnauthorized());
    }

    @Test
    void oIngressoSoServeParaAClinicaPedida() throws Exception {
        Clinica clinica = novaClinica();
        Clinica outra = novaClinica();
        String ingresso = pedirIngresso(clinica);

        abrirStream(outra, ingresso).andExpect(status().isUnauthorized());
    }

    @Test
    void oTokenDeSessaoNaUrlNaoAbreMaisOStream() throws Exception {
        Clinica clinica = novaClinica();
        String tokenDeSessao = tokenDe(clinica).substring("Bearer ".length());

        mvc.perform(get("/api/goniometro/stream")
                        .param("idClinica", String.valueOf(clinica.getId()))
                        .param("token", tokenDeSessao))
                .andExpect(status().isUnauthorized());
        abrirStream(clinica, tokenDeSessao).andExpect(status().isUnauthorized());
    }

    @Test
    void pedirIngressoParaOutraClinicaNaoDaAcessoAoStreamDela() throws Exception {
        Clinica clinica = novaClinica();
        Clinica outra = novaClinica();
        String corpo = mvc.perform(post("/api/goniometro/stream/ingresso")
                        .param("idClinica", String.valueOf(outra.getId()))
                        .header("Authorization", tokenDe(clinica)))
                .andReturn().getResponse().getContentAsString();
        String ingresso = json.readTree(corpo).path("ingresso").asText("");

        // O ingresso só leva a identidade de quem pediu: abrir o stream da
        // outra clínica esbarra na mesma checagem de dono de sempre.
        abrirStream(outra, ingresso).andExpect(status().isForbidden());
    }
}
