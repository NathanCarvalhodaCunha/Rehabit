package com.rehabit;

import com.rehabit.model.Clinica;
import com.rehabit.security.TurnstileService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * As rotas de entrada com a verificação anti-robô ligada, contra uma
 * Cloudflare falsa que só aprova o token "token-bom".
 *
 * Os outros testes rodam sem TURNSTILE_SECRET_KEY e por isso entram sem
 * token nenhum — é o que garante que a verificação desligada não muda nada.
 */
@TestPropertySource(properties = "rehabit.turnstile.secret=segredo-de-teste")
class AntiRoboNasRotasDeEntradaTest extends TesteDeIntegracao {

    private static HttpServer cloudflare;

    @DynamicPropertySource
    static void apontarParaACloudflareFalsa(DynamicPropertyRegistry propriedades) throws IOException {
        cloudflare = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        cloudflare.createContext("/siteverify", troca -> {
            String pedido = new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            boolean aprovado = pedido.contains("response=token-bom");
            byte[] corpo = (aprovado
                    ? "{\"success\":true}"
                    : "{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}")
                    .getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(200, corpo.length);
            try (OutputStream saida = troca.getResponseBody()) {
                saida.write(corpo);
            }
        });
        cloudflare.start();
        propriedades.add("rehabit.turnstile.url",
                () -> "http://127.0.0.1:" + cloudflare.getAddress().getPort() + "/siteverify");
    }

    @AfterAll
    static void derrubarCloudflareFalsa() {
        cloudflare.stop(0);
    }

    private static String login(Clinica clinica) {
        return "{\"email\":\"" + clinica.getEmail() + "\",\"senha\":\"" + SENHA + "\"}";
    }

    @Test
    void loginComTokenAprovadoEntra() throws Exception {
        Clinica clinica = novaClinica();

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .header(TurnstileService.CABECALHO, "token-bom")
                        .content(login(clinica)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("CLINICA"));
    }

    @Test
    void loginSemTokenEBarradoMesmoComASenhaCerta() throws Exception {
        Clinica clinica = novaClinica();

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(login(clinica)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensagem").value(containsString("robô")));
    }

    @Test
    void loginComTokenRecusadoEBarrado() throws Exception {
        Clinica clinica = novaClinica();

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .header(TurnstileService.CABECALHO, "token-de-robo")
                        .content(login(clinica)))
                .andExpect(status().isForbidden());
    }

    @Test
    void codigoDoCadastroSoSaiComToken() throws Exception {
        mvc.perform(post("/api/auth/verificar-email/enviar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alguem@teste.rehabit\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void emailDeRecuperacaoSoSaiComToken() throws Exception {
        mvc.perform(post("/api/auth/esqueci-senha").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alguem@teste.rehabit\"}"))
                .andExpect(status().isForbidden());
    }

    /**
     * O navegador pergunta antes (preflight) se pode mandar o cabeçalho do
     * token. É também o que deixa o site novo falar com a API antiga, que
     * ainda não confere o token: o @CrossOrigin já aceita qualquer cabeçalho.
     */
    @Test
    void oNavegadorPodeMandarOCabecalhoDoToken() throws Exception {
        mvc.perform(options("/api/auth/login")
                        .header("Origin", "https://nathancarvalhodacunha.github.io")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,x-turnstile-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("x-turnstile-token")));
    }
}
