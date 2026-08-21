package com.rehabit.goniometro.controller;

public class DispositivoInvalidoException extends RuntimeException {
	
    public DispositivoInvalidoException(String mensagem) {
        super(mensagem);
    }
    
}