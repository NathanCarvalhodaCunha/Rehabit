package com.rehabit;

import com.rehabit.model.Clinica;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sem limite, nada impedia adivinhar senhas no login nem varrer o milhão de
 * códigos de pareamento — o que entregava um token de goniômetro de qualquer
 * clínica com um código ativo. Os limites ficam desligados no resto da
 * suíte (contariam em memória entre as classes); aqui eles ligam, com um
 * proxy de confiança na frente, para cada teste "vir" de um IP diferente.
 */
@TestPropertySource(properties = {
        "rehabit.limites.ativo=true",
        "rehabit.rede.proxies-confiaveis=1"
})
class LimitesDeTentativasTest extends TesteDeIntegracao {

    static final RelogioDeTeste RELOGIO = new RelogioDeTeste(Instant.parse("2026-10-13T12:00:00Z"));

    @TestConfiguration
    static class RelogioControlado {
        @Bean
        @Primary
        Clock relogioDosLimites() {
            return RELOGIO;
        }
    }

    /** Cada teste começa depois de todas as janelas dos anteriores terem acabado. */
    @BeforeEach
    void passarDasJanelasAnteriores() {
        RELOGIO.avancar(Duration.ofHours(2));
    }

    private ResultActions login(String ip, String email, String senha) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}"));
    }

    private ResultActions parear(String ip, String codigo) throws Exception {
        return mvc.perform(post("/api/dispositivos/parear")
                .header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigo\":\"" + codigo + "\"}"));
    }

    @Test
    void senhasErradasNumaContaBloqueiamAteASenhaCerta() throws Exception {
        Clinica clinica = novaClinica();
        // Cada palpite de um IP diferente: o limite é da conta, não da rede.
        for (int i = 0; i < 10; i++) {
            login("10.1.0." + i, clinica.getEmail(), "palpite-" + i).andExpect(status().isUnauthorized());
        }
        // A senha certa também é barrada: senão o 200 depois de um 429 entregaria a senha.
        login("10.1.0.99", clinica.getEmail(), SENHA)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.mensagem", containsString("Esqueci minha senha")));

        RELOGIO.avancar(Duration.ofMinutes(16));
        login("10.1.0.99", clinica.getEmail(), SENHA).andExpect(status().isOk());
    }

    @Test
    void contaQueNaoExisteTemOMesmoLimiteDaQueExiste() throws Exception {
        // Se só as contas reais travassem, o 429 diria quem tem cadastro.
        String inexistente = "ninguem-" + proximo() + "@teste.rehabit";
        for (int i = 0; i < 10; i++) {
            login("10.2.0." + i, inexistente, "palpite").andExpect(status().isUnauthorized());
        }
        login("10.2.0.99", inexistente, "palpite").andExpect(status().isTooManyRequests());
    }

    @Test
    void muitosLoginsDoMesmoIpSaoBarrados() throws Exception {
        String ip = "10.3.0.1";
        for (int i = 0; i < 20; i++) {
            login(ip, "alguem-" + i + "@teste.rehabit", "palpite").andExpect(status().isUnauthorized());
        }
        login(ip, "outra-pessoa@teste.rehabit", "palpite").andExpect(status().isTooManyRequests());
        // Outra rede não é afetada.
        login("10.3.0.2", "outra-pessoa@teste.rehabit", "palpite").andExpect(status().isUnauthorized());
    }

    @Test
    void codigosDePareamentoErradosDoMesmoIpSaoBarrados() throws Exception {
        String ip = "10.4.0.1";
        for (int i = 0; i < 10; i++) {
            parear(ip, String.format("%06d", i)).andExpect(status().isBadRequest());
        }
        parear(ip, "000011")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void palpitesEspalhadosPorMuitosIpsBatemNoTetoGlobal() throws Exception {
        // 30 IPs diferentes, um palpite errado cada: nenhum chega ao limite por IP...
        for (int i = 0; i < 30; i++) {
            parear("10.5.0." + i, String.format("%06d", 100 + i)).andExpect(status().isBadRequest());
        }
        // ...mas o sistema todo para de aceitar palpites até o minuto virar.
        parear("10.5.1.1", "000999").andExpect(status().isTooManyRequests());

        RELOGIO.avancar(Duration.ofMinutes(1));
        parear("10.5.1.1", "000999").andExpect(status().isBadRequest());
    }

    @Test
    void caminhoCodificadoNaoFogeDoLimite() throws Exception {
        // O Spring decodifica "%6F" em "o" antes de rotear: é o mesmo login.
        String ip = "10.7.0.1";
        for (int i = 0; i < 20; i++) {
            mvc.perform(post(URI.create("/api/auth/l%6Fgin"))
                            .header("X-Forwarded-For", ip)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"cod-" + i + "@teste.rehabit\",\"senha\":\"palpite\"}"))
                    .andExpect(status().isUnauthorized());
        }
        login(ip, "outra@teste.rehabit", "palpite").andExpect(status().isTooManyRequests());
        mvc.perform(post(URI.create("/api/auth/l%6Fgin"))
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"mais@teste.rehabit\",\"senha\":\"palpite\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void umaContaNaoEnviaFotosSemFim() throws Exception {
        // Cada envio de um IP diferente: o limite é da conta, não da rede.
        Clinica clinica = novaClinica();
        byte[] png = Imagens.png(2, 2);
        for (int i = 0; i < 100; i++) {
            mvc.perform(multipart("/api/uploads")
                            .file(new MockMultipartFile("arquivo", "f.png", "image/png", png))
                            .header("Authorization", tokenDe(clinica))
                            .header("X-Forwarded-For", "10.8." + (i / 200) + "." + (i % 200)))
                    .andExpect(status().isCreated());
        }
        mvc.perform(multipart("/api/uploads")
                        .file(new MockMultipartFile("arquivo", "f.png", "image/png", png))
                        .header("Authorization", tokenDe(clinica))
                        .header("X-Forwarded-For", "10.8.9.9"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.mensagem", containsString("horas")));
    }

    @Test
    void oXForwardedForInventadoPeloClienteNaoTrocaOIp() throws Exception {
        // O proxy acrescenta o IP real no fim; o que vem antes é do próprio cliente.
        String ipReal = "10.6.0.1";
        for (int i = 0; i < 10; i++) {
            parear("1.2.3." + i + ", " + ipReal, String.format("%06d", 200 + i))
                    .andExpect(status().isBadRequest());
        }
        parear("9.9.9.9, " + ipReal, "000300").andExpect(status().isTooManyRequests());
    }
}
