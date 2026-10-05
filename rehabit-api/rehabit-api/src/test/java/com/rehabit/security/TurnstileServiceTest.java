package com.rehabit.security;

import com.rehabit.exception.AuthException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fala com uma Cloudflare falsa, que sobe numa porta livre e responde o que
 * cada teste mandar. A de verdade precisaria de chave e de internet.
 */
class TurnstileServiceTest {

    private static final String SEGREDO = "segredo-de-teste";

    private HttpServer cloudflare;
    private final List<String> pedidos = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private volatile String resposta = "{\"success\":true}";
    private volatile long demoraMs = 0;

    @BeforeEach
    void subirCloudflareFalsa() throws IOException {
        cloudflare = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        cloudflare.createContext("/siteverify", troca -> {
            pedidos.add(new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if (demoraMs > 0) {
                try {
                    Thread.sleep(demoraMs);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] corpo = resposta.getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(status, corpo.length);
            try (OutputStream saida = troca.getResponseBody()) {
                saida.write(corpo);
            }
        });
        cloudflare.start();
    }

    @AfterEach
    void derrubarCloudflareFalsa() {
        cloudflare.stop(0);
    }

    private TurnstileService turnstile(String segredo) {
        String endereco = "http://127.0.0.1:" + cloudflare.getAddress().getPort() + "/siteverify";
        return new TurnstileService(segredo, endereco, 300);
    }

    @Test
    void semSegredoFicaDesligadoENaoPerguntaNada() {
        TurnstileService turnstile = turnstile("");

        assertThat(turnstile.isAtivo()).isFalse();
        assertThatCode(() -> turnstile.verificar(null)).doesNotThrowAnyException();
        assertThat(pedidos).isEmpty();
    }

    @Test
    void tokenAprovadoPassaELevaOSegredoJunto() {
        TurnstileService turnstile = turnstile(SEGREDO);

        assertThat(turnstile.isAtivo()).isTrue();
        assertThatCode(() -> turnstile.verificar("token-bom")).doesNotThrowAnyException();
        assertThat(pedidos).singleElement().satisfies(corpo -> assertThat(corpo)
                .contains("secret=" + SEGREDO)
                .contains("response=token-bom"));
    }

    @Test
    void tokenRecusadoBarraCom403() {
        resposta = "{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}";

        assertThatThrownBy(() -> turnstile(SEGREDO).verificar("token-de-robo"))
                .isInstanceOfSatisfying(AuthException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void semTokenBarraSemPerguntarACloudflare() {
        TurnstileService turnstile = turnstile(SEGREDO);

        assertThatThrownBy(() -> turnstile.verificar(null)).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> turnstile.verificar("   ")).isInstanceOf(AuthException.class);
        assertThat(pedidos).isEmpty();
    }

    // Nos dois testes abaixo a resposta, se fosse lida, recusaria o token: o
    // que tem de fazer o pedido passar é o tempo limite e o status HTTP.

    @Test
    void cloudflareQueNaoRespondeATempoDeixaPassar() {
        demoraMs = 2000;
        resposta = "{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}";

        assertThatCode(() -> turnstile(SEGREDO).verificar("token")).doesNotThrowAnyException();
    }

    @Test
    void cloudflareComErroDoServidorDeixaPassar() {
        status = 500;
        resposta = "{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}";

        assertThatCode(() -> turnstile(SEGREDO).verificar("token")).doesNotThrowAnyException();
    }

    /**
     * Respostas em que a culpa não é de quem está entrando: erro interno da
     * Cloudflare, o nosso segredo recusado (configuração errada no Render) ou
     * algo que nem é JSON. Barrar aqui trancaria todo mundo do lado de fora.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "{\"success\":false,\"error-codes\":[\"internal-error\"]}",
            "{\"success\":false,\"error-codes\":[\"invalid-input-secret\"]}",
            "isto não é json"
    })
    void falhaQueNaoEDeQuemEntraDeixaPassar(String corpo) {
        resposta = corpo;

        assertThatCode(() -> turnstile(SEGREDO).verificar("token")).doesNotThrowAnyException();
    }
}
