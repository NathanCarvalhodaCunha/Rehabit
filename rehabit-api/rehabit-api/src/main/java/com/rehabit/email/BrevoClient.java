package com.rehabit.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Envio de e-mail pela API HTTP do Brevo.
 *
 * Existe porque o plano gratuito do Render bloqueia a saída nas portas de
 * SMTP (25, 465 e 587) desde setembro de 2025 — com SMTP a conexão nem
 * chega ao servidor de e-mail. Esta API responde em HTTPS na 443, que não
 * é bloqueada.
 *
 * Usa o HttpClient do próprio Java 17 e o Jackson que já vem com o Spring
 * Web: nenhuma dependência nova no projeto.
 */
class BrevoClient {

    static final String ENDERECO_PADRAO = "https://api.brevo.com/v3/smtp/email";

    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final String chaveApi;
    private final URI endereco;

    BrevoClient(String chaveApi, String endereco) {
        this.chaveApi = chaveApi;
        this.endereco = URI.create(endereco == null || endereco.isBlank() ? ENDERECO_PADRAO : endereco.trim());
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * @throws IOException quando a chamada falha ou o Brevo recusa o envio;
     *                     a mensagem traz o motivo devolvido por ele.
     */
    void enviar(String remetente, String nomeRemetente, String destinatario,
                String assunto, String corpoHtml, String corpoTexto) throws IOException {

        ObjectNode corpo = json.createObjectNode();
        ObjectNode quemEnvia = corpo.putObject("sender");
        quemEnvia.put("name", nomeRemetente);
        quemEnvia.put("email", remetente);
        corpo.putArray("to").addObject().put("email", destinatario);
        corpo.put("subject", assunto);
        corpo.put("htmlContent", corpoHtml);
        corpo.put("textContent", corpoTexto);

        HttpRequest requisicao = HttpRequest.newBuilder(endereco)
                .header("api-key", chaveApi)
                .header("accept", "application/json")
                .header("content-type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(
                        json.writeValueAsString(corpo), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resposta;
        try {
            resposta = http.send(requisicao, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException ex) {
            // Repõe a marca de interrupção: engoli-la esconde do resto da
            // aplicação que a thread foi mandada parar.
            Thread.currentThread().interrupt();
            throw new IOException("Envio interrompido antes de terminar.", ex);
        }

        if (resposta.statusCode() / 100 != 2) {
            // O corpo do erro do Brevo é curto e diz o motivo ("sender not
            // valid", chave errada etc.). Não há segredo nosso nele — a
            // chave vai no cabeçalho, que não é registrado aqui.
            throw new IOException("Brevo respondeu " + resposta.statusCode() + ": " + resposta.body()
                    + dica(resposta.statusCode()));
        }
    }

    /**
     * Traduz os erros que aparecem na configuração inicial. Sem isso, o log
     * mostra só o código do Brevo e quem lê fica sem saber onde mexer.
     */
    private static String dica(int status) {
        return switch (status) {
            case 401 -> " | Chave recusada. Confira dois pontos: (1) tem de ser a chave de API v3, "
                    + "que começa com \"xkeysib-\" — a chave de SMTP (\"xsmtpsib-\") serve só para o "
                    + "relay SMTP e é recusada aqui; (2) o Brevo mostra a chave inteira uma única vez, "
                    + "então uma versão mascarada, copiada da tela depois, não funciona: gere outra.";
            case 400 -> " | Se a mensagem fala em \"sender\", o endereço em MAIL_FROM precisa estar "
                    + "verificado na conta do Brevo (Senders, Domains & Dedicated IPs > Senders).";
            case 402, 429 -> " | Cota do Brevo esgotada (o plano gratuito envia 300 por dia).";
            default -> "";
        };
    }
}
