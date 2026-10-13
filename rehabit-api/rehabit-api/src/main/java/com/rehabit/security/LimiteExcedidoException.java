package com.rehabit.security;

import com.rehabit.exception.AuthException;
import org.springframework.http.HttpStatus;

/** 429: passou do {@link Limite}. Leva quanto falta, que vira o cabeçalho Retry-After. */
public class LimiteExcedidoException extends AuthException {

    private final long segundosParaTentarDeNovo;

    public LimiteExcedidoException(String mensagem, long segundosParaTentarDeNovo) {
        super(mensagem, HttpStatus.TOO_MANY_REQUESTS);
        this.segundosParaTentarDeNovo = segundosParaTentarDeNovo;
    }

    public long getSegundosParaTentarDeNovo() {
        return segundosParaTentarDeNovo;
    }
}
