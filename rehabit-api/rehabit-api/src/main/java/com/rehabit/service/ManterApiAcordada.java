package com.rehabit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

/**
 * Impede o plano gratuito do Render de hibernar a API.
 *
 * O Render derruba o container depois de ~15 min sem tráfego de entrada, e a
 * chamada seguinte espera o religamento inteiro (124 a 146 s medidos). A
 * primeira tentativa de evitar isso foi um workflow agendado no GitHub
 * batendo aqui de dez em dez minutos, e não funcionou: o agendador do GitHub
 * é "melhor esforço" e, na prática, entregou 6 execuções por dia em vez de
 * 108, com intervalos de 2 a 7 horas. Todas encontraram a API dormindo.
 *
 * O relógio, então, passa a ser o da própria JVM, que não atrasa. A cada 5 min
 * a API chama o próprio /api/health pela URL PÚBLICA — não por localhost:
 * o que o Render conta como tráfego é o que passa pelo proxy de entrada dele,
 * e uma chamada interna não passaria. Cinco minutos em vez de dez para que um
 * ping perdido sozinho não baste para cruzar os quinze.
 *
 * O que isto não faz é acordar uma instância que já dormiu: parado, o
 * processo não roda nada. Na prática a instância sobe a cada deploy e daí em
 * diante não chega a dormir; o workflow do GitHub fica como rede de segurança
 * para o caso raro de ela parar por outro motivo.
 *
 * A URL vem de RENDER_EXTERNAL_URL, que o Render define sozinho em todo web
 * service. Fora dele a variável não existe e o ping fica desligado — rodar a
 * API local não pode sair batendo na produção.
 */
@Component
public class ManterApiAcordada {

    private static final Logger log = LoggerFactory.getLogger(ManterApiAcordada.class);

    // O agendador do Spring tem uma thread só, dividida com a leitura do
    // goniômetro (a cada 2 s). Um envio síncrono esperando um timeout de 30 s
    // congelaria o aparelho junto; por isso o envio é assíncrono e o método
    // agendado devolve a thread na hora.
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final URI urlHealth;
    private volatile Instant ultimoOk;

    public ManterApiAcordada(@Value("${rehabit.keepalive.ativo:true}") boolean ativo,
                             @Value("${rehabit.keepalive.url:}") String urlBase) {
        String base = urlBase == null ? "" : urlBase.trim().replaceAll("/+$", "");
        if (!ativo || base.isEmpty()) {
            this.urlHealth = null;
            log.info("Auto-ping desligado ({}). Normal fora do Render.",
                    ativo ? "sem RENDER_EXTERNAL_URL nem REHABIT_KEEPALIVE_URL" : "REHABIT_KEEPALIVE_ATIVO=false");
        } else {
            this.urlHealth = URI.create(base + "/api/health");
            log.info("Auto-ping ligado em {}", this.urlHealth);
        }
    }

    @Scheduled(initialDelayString = "${rehabit.keepalive.atraso-inicial-ms:60000}",
               fixedDelayString = "${rehabit.keepalive.intervalo-ms:300000}")
    public void pingar() {
        if (urlHealth == null) {
            return;
        }
        HttpRequest requisicao = HttpRequest.newBuilder(urlHealth)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "rehabit-auto-ping")
                .GET()
                .build();
        http.sendAsync(requisicao, HttpResponse.BodyHandlers.discarding())
                .whenComplete((resposta, erro) -> {
                    if (erro != null) {
                        log.warn("Auto-ping falhou: {}", erro.toString());
                    } else if (resposta.statusCode() != 200) {
                        log.warn("Auto-ping respondeu HTTP {}", resposta.statusCode());
                    } else {
                        ultimoOk = Instant.now();
                    }
                });
    }

    public boolean ativo() {
        return urlHealth != null;
    }

    /** Momento do último ping que voltou 200, ou null se nenhum voltou ainda. */
    public Instant ultimoOk() {
        return ultimoOk;
    }
}
