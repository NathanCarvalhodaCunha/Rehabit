package com.rehabit;

import com.rehabit.model.VerificacaoEmail;
import com.rehabit.repository.VerificacaoEmailRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.util.FileSystemUtils;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A foto do cadastro sobe antes de a conta existir. Com o /api/uploads
 * fechado, ela tem a própria rota, que só aceita quem já confirmou o e-mail
 * pelo código — e só algumas vezes por e-mail.
 */
@TestPropertySource(properties = {
        "rehabit.email.confirmar-cadastro=true",
        "rehabit.limites.ativo=true",
        "rehabit.rede.proxies-confiaveis=1"
})
class FotoDoCadastroTest extends TesteDeIntegracao {

    @Autowired private VerificacaoEmailRepository verificacoes;

    @Value("${app.upload-dir}")
    private String pastaDeUploads;

    @AfterEach
    void limparPasta() {
        FileSystemUtils.deleteRecursively(Path.of(pastaDeUploads).toFile());
    }

    private ResultActions enviar(String email, String ip) throws Exception {
        return mvc.perform(multipart("/api/auth/cadastro/foto")
                .file(new MockMultipartFile("arquivo", "f.png", "image/png", Imagens.png(3, 3)))
                .param("email", email)
                .header("X-Forwarded-For", ip));
    }

    private String emailConfirmado() {
        String email = "cadastro" + proximo() + "@teste.rehabit";
        VerificacaoEmail v = new VerificacaoEmail();
        v.setEmail(email);
        v.setCodigoHash("x".repeat(64));
        v.setCriadoEm(LocalDateTime.now());
        v.setExpiraEm(LocalDateTime.now().plusMinutes(15));
        v.setVerificadoEm(LocalDateTime.now());
        verificacoes.save(v);
        return email;
    }

    @Test
    void semEmailConfirmadoNaoSobe() throws Exception {
        enviar("ninguem" + proximo() + "@teste.rehabit", "10.20.0.1").andExpect(status().isForbidden());
    }

    @Test
    void comEmailConfirmadoSobeSemLogin() throws Exception {
        enviar(emailConfirmado(), "10.20.0.2").andExpect(status().isCreated());
    }

    @Test
    void cadaEmailConfirmadoSobePoucasFotos() throws Exception {
        String email = emailConfirmado();
        for (int i = 0; i < 5; i++) {
            enviar(email, "10.20.1." + i).andExpect(status().isCreated());
        }
        enviar(email, "10.20.1.99").andExpect(status().isTooManyRequests());
    }
}
