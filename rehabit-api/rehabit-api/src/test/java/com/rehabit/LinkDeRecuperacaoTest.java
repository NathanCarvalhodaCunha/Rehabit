package com.rehabit;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O token do link de recuperação ia na query string da validação, e a URL de
 * cada requisição fica nos logs de acesso da hospedagem. Agora vai no corpo
 * de um POST; o GET antigo continua só para a tela publicada antes.
 */
class LinkDeRecuperacaoTest extends TesteDeIntegracao {

    @Test
    void oTokenVaiNoCorpo() throws Exception {
        mvc.perform(post("/api/auth/recuperar-senha/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"token-que-nao-existe\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valido").value(false));
    }

    @Test
    void tokenGiganteERecusadoSemConsulta() throws Exception {
        mvc.perform(post("/api/auth/recuperar-senha/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + "x".repeat(500) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aRotaAntigaAindaRespondeParaATelaPublicadaAntes() throws Exception {
        mvc.perform(get("/api/auth/recuperar-senha/validar").param("token", "token-que-nao-existe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valido").value(false));
    }
}
