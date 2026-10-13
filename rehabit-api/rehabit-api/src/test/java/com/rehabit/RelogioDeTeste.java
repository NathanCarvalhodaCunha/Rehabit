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

    @Override
    public Instant instant() {
        return agora;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
