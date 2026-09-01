package com.rehabit.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class EsqueciSenhaRequestDTO {

    @NotBlank(message = "Informe o e-mail da sua conta.")
    @Email(message = "Informe um e-mail válido.")
    private String email;

    public EsqueciSenhaRequestDTO() {
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
