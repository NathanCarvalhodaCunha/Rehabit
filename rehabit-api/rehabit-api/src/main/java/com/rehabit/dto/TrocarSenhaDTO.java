package com.rehabit.dto;

import com.rehabit.security.SenhaForte;
import jakarta.validation.constraints.NotBlank;

public class TrocarSenhaDTO {

    @NotBlank(message = "Informe a senha atual.")
    private String senhaAtual;

    @NotBlank(message = "Informe a nova senha.")
    @SenhaForte
    private String novaSenha;

    public TrocarSenhaDTO() {
    }

    public String getSenhaAtual() {
        return senhaAtual;
    }

    public void setSenhaAtual(String senhaAtual) {
        this.senhaAtual = senhaAtual;
    }

    public String getNovaSenha() {
        return novaSenha;
    }

    public void setNovaSenha(String novaSenha) {
        this.novaSenha = novaSenha;
    }
}
