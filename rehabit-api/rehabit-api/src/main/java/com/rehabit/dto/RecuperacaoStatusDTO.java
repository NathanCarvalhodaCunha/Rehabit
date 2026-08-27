package com.rehabit.dto;

/** Resposta da conferência do link de recuperação, antes de mostrar o formulário. */
public class RecuperacaoStatusDTO {

    private boolean valido;
    /** E-mail parcialmente escondido ("nat***@gmail.com"), só para a pessoa se reconhecer. */
    private String email;
    private String mensagem;

    public RecuperacaoStatusDTO(boolean valido, String email, String mensagem) {
        this.valido = valido;
        this.email = email;
        this.mensagem = mensagem;
    }

    public boolean isValido() {
        return valido;
    }

    public String getEmail() {
        return email;
    }

    public String getMensagem() {
        return mensagem;
    }
}
