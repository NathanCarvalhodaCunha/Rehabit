package com.rehabit;

import com.rehabit.model.Clinica;
import com.rehabit.repository.VerificacaoEmailRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pedir o código de cadastro com um e-mail que já tem conta respondia 409
 * "Este e-mail já está cadastrado": qualquer um descobria quem é cliente. Agora
 * a resposta é a mesma de um e-mail novo, e quem tem conta recebe um aviso por
 * e-mail no lugar do código.
 */
@TestPropertySource(properties = "rehabit.email.confirmar-cadastro=true")
class CodigoDeCadastroNaoRevelaContaTest extends TesteDeIntegracao {

    @Autowired private VerificacaoEmailRepository verificacoes;

    private String pedirCodigo(String email) throws Exception {
        return mvc.perform(post("/api/auth/verificar-email/enviar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void emailComContaRecebeAMesmaRespostaDeUmNovo() throws Exception {
        Clinica clinica = novaClinica();
        String novo = "ainda-sem-conta" + proximo() + "@teste.rehabit";

        String comConta = pedirCodigo(clinica.getEmail());
        String semConta = pedirCodigo(novo);

        // Mesmos campos e a mesma frase, trocando só o e-mail mascarado.
        String mascaraComConta = com.rehabit.email.EmailService.mascarar(clinica.getEmail());
        String mascaraSemConta = com.rehabit.email.EmailService.mascarar(novo);
        assertThat(comConta.replace(mascaraComConta, "*")).isEqualTo(semConta.replace(mascaraSemConta, "*"));

        // E nenhum código de cadastro nasce para quem já tem conta.
        assertThat(verificacoes.findByEmail(clinica.getEmail())).isEmpty();
        assertThat(verificacoes.findByEmail(novo)).isPresent();
    }
}
