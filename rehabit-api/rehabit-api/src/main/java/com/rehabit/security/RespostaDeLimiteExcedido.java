package com.rehabit.security;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Responde o 429 no mesmo formato dos outros erros ({"mensagem": ...}) e com
 * o Retry-After, que diz em segundos quando tentar de novo.
 *
 * Fica à frente do GlobalExceptionHandler: lá a exceção cairia no tratamento
 * genérico de AuthException, que não sabe do cabeçalho. E é resposta de
 * controller, não de filtro, para levar os cabeçalhos de CORS do
 * {@code @CrossOrigin} — sem eles o navegador esconderia a mensagem do site.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RespostaDeLimiteExcedido {

    @ExceptionHandler(LimiteExcedidoException.class)
    public ResponseEntity<Map<String, String>> responder(LimiteExcedidoException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getSegundosParaTentarDeNovo()))
                .body(Map.of("mensagem", ex.getMessage()));
    }
}
