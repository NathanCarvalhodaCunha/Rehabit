package com.rehabit;

import com.rehabit.demo.ContaDeDemonstracao;
import com.rehabit.model.Clinica;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O site (rehabit.com.br) e a API (onrender.com) ficam em origens diferentes:
 * sem o cabeçalho de CORS, o navegador esconde a resposta e o fetch falha
 * como se a rede tivesse caído. As recusas que saem antes dos controllers —
 * o filtro de autenticação e o teto do corpo — não levavam o cabeçalho que o
 * @CrossOrigin põe nas outras: a sessão vencida não mandava para o login e a
 * conta de demonstração respondia "Não foi possível falar com o servidor".
 */
class RecusaLegivelPeloSiteTest extends TesteDeIntegracao {

    private static final String SITE = "https://rehabit.com.br";

    @Test
    void sessaoInvalidaChegaAoSiteComo401() throws Exception {
        mvc.perform(get("/api/dispositivos?idClinica=1")
                        .header("Origin", SITE)
                        .header("Authorization", "Bearer nao.e.um.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(jsonPath("$.mensagem", notNullValue()));
    }

    @Test
    void semTokenTambem() throws Exception {
        mvc.perform(get("/api/dispositivos?idClinica=1").header("Origin", SITE))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }

    @Test
    void oAvisoDaDemonstracaoChegaAoSite() throws Exception {
        Clinica demo = novaClinica();
        demo.setEmail(ContaDeDemonstracao.EMAIL_CLINICA);
        demo = clinicas.save(demo);

        mvc.perform(put("/api/clinicas/" + demo.getId() + "/tutorial-visto")
                        .header("Origin", SITE)
                        .header("Authorization", tokenDe(demo)))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(jsonPath("$.mensagem").value(containsString("demonstração")));
    }

    @Test
    void corpoGrandeDemaisChegaAoSiteComo413() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .header("Origin", SITE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + "x".repeat(300 * 1024) + "\"}"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }
}
