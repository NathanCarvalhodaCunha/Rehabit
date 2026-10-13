package com.rehabit.security;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ingresso para abrir o SSE do goniômetro (GET /api/goniometro/stream).
 *
 * O EventSource do navegador não manda cabeçalho, então a credencial do
 * stream tem de ir na URL — e URL acaba em log de proxy e de provedor. Lá
 * ia o próprio token de sessão, que vale 30 dias e abre a API inteira. O
 * ingresso não serve para mais nada: é um valor aleatório, preso ao usuário
 * e à clínica, que abre aquele stream uma única vez e vence em 60 segundos.
 * Não é JWT, então não há como confundi-lo com um token de sessão.
 *
 * Fica em memória: vive menos de um minuto e a API roda numa instância só.
 */
@Component
public class IngressoDoStream {

    public static final Duration VALIDADE = Duration.ofSeconds(60);

    private record Reserva(Integer usuarioId, String usuarioTipo, Integer idClinica, Instant emitidoEm) {
        boolean vencido(Instant agora) {
            return !agora.isBefore(emitidoEm.plus(VALIDADE));
        }
    }

    private final Map<String, Reserva> reservas = new ConcurrentHashMap<>();
    private final Clock relogio;

    public IngressoDoStream(Clock relogio) {
        this.relogio = relogio;
    }

    public String emitir(Integer usuarioId, String usuarioTipo, Integer idClinica) {
        Instant agora = relogio.instant();
        // Os não usados vencem aqui mesmo; quanto pode haver de uma vez é o
        // Limite.INGRESSO_POR_USUARIO que segura.
        reservas.values().removeIf(reserva -> reserva.vencido(agora));
        String ingresso = CodigoSeguro.gerarToken();
        reservas.put(ingresso, new Reserva(usuarioId, usuarioTipo, idClinica, agora));
        return ingresso;
    }

    /**
     * Uso único: o ingresso sai da lista na primeira tentativa, sirva ou não.
     * Um ingresso copiado de um log já não abre nada depois que o site o usou.
     *
     * @return o dono, como se viesse de um token (sem clínica de dispositivo),
     *         ou null quando não existe, venceu ou é de outra clínica
     */
    public JwtService.TokenDados resgatar(String ingresso, String idClinica) {
        if (ingresso == null || ingresso.isBlank()) {
            return null;
        }
        Reserva reserva = reservas.remove(ingresso);
        if (reserva == null || reserva.vencido(relogio.instant())
                || !String.valueOf(reserva.idClinica()).equals(idClinica)) {
            return null;
        }
        return new JwtService.TokenDados(reserva.usuarioId(), reserva.usuarioTipo(), null, reserva.emitidoEm());
    }
}
