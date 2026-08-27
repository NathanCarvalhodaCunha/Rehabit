package com.rehabit.dto;

/** Resposta do envio do código de cadastro: para onde foi e por quanto tempo vale. */
public class VerificacaoEnviadaDTO {

    private String email;
    private int expiraEmMinutos;
    private String mensagem;

    public VerificacaoEnviadaDTO(String email, int expiraEmMinutos, String mensagem) {
        this.email = email;
        this.expiraEmMinutos = expiraEmMinutos;
        this.mensagem = mensagem;
    }

    public String getEmail() {
        return email;
    }

    public int getExpiraEmMinutos() {
        return expiraEmMinutos;
    }

    public String getMensagem() {
        return mensagem;
    }
}
