package com.rehabit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Relógio que só anda quando o teste manda. Serve para atravessar a janela de
 * um limite ou o minuto de validade de um ingresso sem esperar de verdade.
 */
public class RelogioDeTeste extends Clock {

    private volatile Instant agora;

    public RelogioDeTeste(Instant inicio) {
        this.agora = inicio;
    }

    public void avancar(Duration quanto) {
        agora = agora.plus(quanto);
    }

    public void irPara(Instant instante) {
        agora = instante;
    }

    @Override
    public Instant instant() {
        return agora;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    /**
     * O mesmo relógio visto de outro fuso, e que continua obedecendo ao teste.
     * Devolver "this" deixava em UTC quem pede o dia de Brasília: às 21h30 de
     * lá, o dia já seria o seguinte.
     */
    @Override
    public Clock withZone(ZoneId zone) {
        RelogioDeTeste origem = this;
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return zone;
            }

            @Override
            public Clock withZone(ZoneId outro) {
                return origem.withZone(outro);
            }

            @Override
            public Instant instant() {
                return origem.instant();
            }
        };
    }
}
