package com.rehabit.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rehabit.exception.AuthException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Verificação anti-robô do Cloudflare Turnstile.
 *
 * O site gera um token no navegador (o widget da Cloudflare, quase sempre
 * invisível) e manda no cabeçalho {@value #CABECALHO}. Aqui o token é
 * conferido com a Cloudflare antes de a rota fazer qualquer coisa. Fica nas
 * rotas que um robô teria interesse em martelar: o login (adivinhar senha) e
 * as duas que mandam e-mail (cada uma gasta da cota de 300/dia do Brevo).
 *
 * Sem TURNSTILE_SECRET_KEY a verificação fica desligada, como o envio de
 * e-mail sem provedor: rodando local e nos testes, nada muda.
 *
 * Quando quem falha é a Cloudflare (fora do ar, lenta, erro interno) ou a
 * nossa configuração (segredo recusado), o pedido passa e o motivo vai para
 * o log. Barrar nesses casos trancaria todo mundo do lado de fora por um
 * problema que não é de quem está entrando.
 *
 * Usa o HttpClient do Java 17 e o Jackson do Spring Web, como o BrevoClient:
 * nenhuma dependência nova.
 */
@Service
public class TurnstileService {

    private static final Logger log = LoggerFactory.getLogger(TurnstileService.class);

    /** Cabeçalho em que o site manda o token gerado pelo widget. */
    public static final String CABECALHO = "X-Turnstile-Token";

    static final String ENDERECO_PADRAO = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private static final String MENSAGEM_RECUSA =
            "Não conseguimos confirmar que você não é um robô. Recarregue a página e tente de novo.";

    // Códigos de erro da Cloudflare em que a culpa não é do token: o nosso
    // segredo (errado ou ausente) e falha interna dela.
    private static final Set<String> FALHAS_FORA_DO_TOKEN =
            Set.of("invalid-input-secret", "missing-input-secret", "internal-error");

    private final String segredo;
    private final URI endereco;
    private final Duration tempoLimite;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    public TurnstileService(@Value("${rehabit.turnstile.secret:}") String segredo,
                            @Value("${rehabit.turnstile.url:}") String endereco,
                            @Value("${rehabit.turnstile.timeout-ms:5000}") long tempoLimiteMs) {
        this.segredo = segredo == null ? "" : segredo.trim();
        this.endereco = URI.create(endereco == null || endereco.isBlank() ? ENDERECO_PADRAO : endereco.trim());
        this.tempoLimite = Duration.ofMillis(tempoLimiteMs);
        this.http = HttpClient.newBuilder()
                .connectTimeout(tempoLimite)
                .build();

        if (isAtivo()) {
            log.info("Verificação anti-robô (Cloudflare Turnstile) ligada no login, no código de "
                    + "cadastro e na recuperação de senha.");
        } else {
            log.warn("Verificação anti-robô (Cloudflare Turnstile) desligada: sem TURNSTILE_SECRET_KEY.");
        }
    }

    public boolean isAtivo() {
        return !segredo.isEmpty();
    }

    /**
     * @throws AuthException 403 quando falta o token ou a Cloudflare o recusa
     *                       (inválido, vencido ou já usado — cada token vale
     *                       uma vez só).
     */
    public void verificar(String token) {
        if (!isAtivo()) {
            return;
        }
        if (token == null || token.isBlank()) {
            throw new AuthException(MENSAGEM_RECUSA, HttpStatus.FORBIDDEN);
        }

        JsonNode resposta = consultar(token.trim());
        if (resposta == null || resposta.path("success").asBoolean(false)) {
            return;
        }

        List<String> codigos = new ArrayList<>();
        resposta.path("error-codes").forEach(codigo -> codigos.add(codigo.asText()));
        if (codigos.stream().anyMatch(FALHAS_FORA_DO_TOKEN::contains)) {
            log.error("A Cloudflare não conseguiu conferir o token anti-robô {}. Se aparecer "
                    + "invalid-input-secret, a TURNSTILE_SECRET_KEY está errada. Deixando passar.", codigos);
            return;
        }
        throw new AuthException(MENSAGEM_RECUSA, HttpStatus.FORBIDDEN);
    }

    /** A resposta da Cloudflare, ou null quando não deu para perguntar. */
    private JsonNode consultar(String token) {
        String corpo = "secret=" + URLEncoder.encode(segredo, StandardCharsets.UTF_8)
                + "&response=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        HttpRequest requisicao = HttpRequest.newBuilder(endereco)
                .header("content-type", "application/x-www-form-urlencoded")
                .timeout(tempoLimite)
                .POST(HttpRequest.BodyPublishers.ofString(corpo, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> resposta =
                    http.send(requisicao, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resposta.statusCode() / 100 != 2) {
                log.warn("A Cloudflare respondeu {} ao conferir o token anti-robô. Deixando passar.",
                        resposta.statusCode());
                return null;
            }
            return json.readTree(resposta.body());
        } catch (IOException ex) {
            // Inclui o tempo esgotado e a resposta que não é JSON.
            log.warn("Não consegui conferir o token anti-robô com a Cloudflare ({}). Deixando passar.",
                    ex.toString());
            return null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
