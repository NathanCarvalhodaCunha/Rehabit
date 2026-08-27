package com.rehabit.dto;

/** Resposta simples de sucesso, no mesmo formato ({"mensagem": ...}) usado nos erros. */
public class MensagemDTO {

    private String mensagem;

    public MensagemDTO(String mensagem) {
        this.mensagem = mensagem;
    }

    public String getMensagem() {
        return mensagem;
    }
}
