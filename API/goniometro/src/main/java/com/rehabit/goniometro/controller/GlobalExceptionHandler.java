package com.rehabit.goniometro.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;


@ControllerAdvice
public class GlobalExceptionHandler {
	
	@ExceptionHandler(DispositivoInvalidoException.class)
    public ResponseEntity<Erro> handleDispositivoInvalido(DispositivoInvalidoException ex) {
        Erro erro = new Erro(
                HttpStatus.BAD_REQUEST.value(),
                "Requisição Inválida",
                ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(erro);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Erro> handleArgumentoInvalido(MethodArgumentTypeMismatchException ex) {
        Erro erro = new Erro(
                HttpStatus.BAD_REQUEST.value(),
                "Erro de Parâmetro",
                "O parâmetro '" + ex.getName() + "' recebeu um valor inválido."
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(erro);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Erro> handleErroGeral(Exception ex) {
        Erro erro = new Erro(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Erro Interno no Servidor",
                "Ocorreu um erro inesperado. Tente novamente mais tarde."
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(erro);
    }
}
