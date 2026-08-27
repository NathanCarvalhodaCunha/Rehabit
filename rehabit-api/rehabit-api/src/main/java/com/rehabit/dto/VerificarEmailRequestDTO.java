package com.rehabit.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Pedido do código de confirmação de cadastro para um e-mail. */
public class VerificarEmailRequestDTO {

    @NotBlank(message = "Informe um e-mail.")
    @Email(message = "Informe um e-mail válido.")
    private String email;

    public VerificarEmailRequestDTO() {
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
