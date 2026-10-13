package com.rehabit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rehabit.model.Clinica;
import com.rehabit.model.Fisioterapeuta;
import com.rehabit.model.RecuperacaoSenha;
import com.rehabit.repository.RecuperacaoSenhaRepository;
import com.rehabit.security.CodigoSeguro;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O token vale 30 dias. Quem troca a senha depois de perceber uma invasão
 * precisa que isso tire o invasor: todo token emitido antes da troca deixa de
 * valer, por qualquer um dos caminhos de troca.
 */
class SessaoEncerradaAoTrocarASenhaTest extends TesteDeIntegracao {

    private static final String SENHA_NOVA = "outra-senha-bem-guardada";

    @Autowired private RecuperacaoSenhaRepository recuperacoes;
    private final ObjectMapper json = new ObjectMapper();

    private void tokenVale(String token) throws Exception {
        mvc.perform(get("/api/configuracoes").header("Authorization", token)).andExpect(status().isOk());
    }

    private void tokenNaoVale(String token) throws Exception {
        mvc.perform(get("/api/configuracoes").header("Authorization", token)).andExpect(status().isUnauthorized());
    }

    @Test
    void trocarASenhaDerrubaOTokenAntigoEEntregaUmNovo() throws Exception {
        Clinica clinica = novaClinica();
        String antigo = tokenDe(clinica);

        String resposta = mvc.perform(put("/api/configuracoes/senha").header("Authorization", antigo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"" + SENHA + "\",\"novaSenha\":\"" + SENHA_NOVA + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        tokenNaoVale(antigo);
        // Emitido no mesmo segundo da troca: o iat (só segundos) diria que é
        // anterior a ela. Tem de valer, senão quem troca a senha cai.
        tokenVale("Bearer " + json.readTree(resposta).get("token").asText());
    }

    @Test
    void loginLogoDepoisDaTrocaFuncionaNaHora() throws Exception {
        Fisioterapeuta ana = novoProfissional(novaClinica());

        mvc.perform(put("/api/configuracoes/senha").header("Authorization", tokenDe(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"" + SENHA + "\",\"novaSenha\":\"" + SENHA_NOVA + "\"}"))
                .andExpect(status().isOk());

        String resposta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + ana.getEmail() + "\",\"senha\":\"" + SENHA_NOVA + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        tokenVale("Bearer " + json.readTree(resposta).get("token").asText());
    }

    @Test
    void redefinirPorEmailDerrubaAsSessoes() throws Exception {
        Fisioterapeuta ana = novoProfissional(novaClinica());
        String antigo = tokenDe(ana);
        RecuperacaoSenha pedido = new RecuperacaoSenha();
        pedido.setEmail(ana.getEmail());
        pedido.setTipoConta("FISIOTERAPEUTA");
        pedido.setTokenHash(CodigoSeguro.hash("link-" + proximo()));
        pedido.setCodigoHash(CodigoSeguro.hash("123456"));
        pedido.setTentativas(0);
        pedido.setCriadoEm(LocalDateTime.now());
        pedido.setExpiraEm(LocalDateTime.now().plusMinutes(30));
        recuperacoes.save(pedido);

        mvc.perform(post("/api/auth/recuperar-senha").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + ana.getEmail() + "\",\"codigo\":\"123456\",\"novaSenha\":\""
                                + SENHA_NOVA + "\"}"))
                .andExpect(status().isOk());

        tokenNaoVale(antigo);
    }

    @Test
    void trocarPeloPerfilDaClinicaDerrubaAsSessoes() throws Exception {
        Clinica clinica = novaClinica();
        String antigo = tokenDe(clinica);

        mvc.perform(put("/api/clinicas/{id}", clinica.getId()).header("Authorization", antigo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + clinica.getNome() + "\",\"cnpj\":\"" + clinica.getCnpj()
                                + "\",\"email\":\"" + clinica.getEmail() + "\",\"senhaAtual\":\"" + SENHA
                                + "\",\"novaSenha\":\"" + SENHA_NOVA + "\"}"))
                .andExpect(status().isOk());

        tokenNaoVale(antigo);
    }

    @Test
    void trocarPeloPerfilDoProfissionalDerrubaAsSessoes() throws Exception {
        Fisioterapeuta ana = novoProfissional(novaClinica());
        String antigo = tokenDe(ana);

        mvc.perform(put("/api/fisioterapeutas/{id}", ana.getId()).header("Authorization", antigo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + ana.getNome() + "\",\"email\":\"" + ana.getEmail()
                                + "\",\"senhaAtual\":\"" + SENHA + "\",\"novaSenha\":\"" + SENHA_NOVA + "\"}"))
                .andExpect(status().isOk());

        tokenNaoVale(antigo);
    }

    @Test
    void editarOPerfilSemMexerNaSenhaNaoDerrubaNinguem() throws Exception {
        Clinica clinica = novaClinica();
        String token = tokenDe(clinica);

        mvc.perform(put("/api/clinicas/{id}", clinica.getId()).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Nome novo\",\"cnpj\":\"" + clinica.getCnpj()
                                + "\",\"email\":\"" + clinica.getEmail() + "\"}"))
                .andExpect(status().isOk());

        tokenVale(token);
    }
}
