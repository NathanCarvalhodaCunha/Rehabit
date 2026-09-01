package com.rehabit.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Conferência do código de confirmação que chegou por e-mail. */
public class ConfirmarEmailRequestDTO {

    @NotBlank(message = "Informe um e-mail.")
    @Email(message = "Informe um e-mail válido.")
    private String email;

    @NotBlank(message = "Informe o código enviado para o seu e-mail.")
    @Pattern(regexp = "\\d{6}", message = "O código tem 6 dígitos.")
    private String codigo;

    public ConfirmarEmailRequestDTO() {
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }
}
