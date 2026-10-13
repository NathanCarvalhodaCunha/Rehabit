package com.rehabit;

import com.rehabit.model.Clinica;
import com.rehabit.model.Fisioterapeuta;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Senha nova precisa de 8 caracteres e não pode estar entre as mais comuns.
 * Quem já tem conta com senha antiga, mais curta, continua entrando: a regra
 * vale para o que é criado ou trocado daqui em diante, não para o login.
 */
class PoliticaDeSenhaTest extends TesteDeIntegracao {

    private ResultActions trocarSenha(Clinica clinica, String nova) throws Exception {
        return mvc.perform(put("/api/configuracoes/senha")
                .header("Authorization", tokenDe(clinica))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"senhaAtual\":\"" + SENHA + "\",\"novaSenha\":\"" + nova + "\"}"));
    }

    @Test
    void senhaNovaCurtaOuComumERecusada() throws Exception {
        Clinica clinica = novaClinica();

        trocarSenha(clinica, "1234567")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", containsString("8 caracteres")));
        trocarSenha(clinica, "12345678")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", containsString("comum")));
        trocarSenha(clinica, "Senha-Nova-Do-Teste").andExpect(status().isOk());
    }

    @Test
    void profissionalCriadoPelaClinicaTambemSegueARegra() throws Exception {
        Clinica clinica = novaClinica();
        int n = proximo();
        mvc.perform(post("/api/fisioterapeutas")
                        .header("Authorization", tokenDe(clinica))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Profissional " + n + "\",\"email\":\"prof" + n
                                + "@teste.rehabit\",\"coffito\":\"COFFITO-" + n + "\",\"senha\":\"abc123\","
                                + "\"idClinica\":" + clinica.getId() + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", containsString("8 caracteres")));
    }

    @Test
    void contaComSenhaAntigaCurtaContinuaEntrando() throws Exception {
        Fisioterapeuta antigo = novoProfissional(novaClinica());
        antigo.setSenha(passwordEncoder.encode("abc123"));
        fisioterapeutas.save(antigo);

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + antigo.getEmail() + "\",\"senha\":\"abc123\"}"))
                .andExpect(status().isOk());
    }
}
