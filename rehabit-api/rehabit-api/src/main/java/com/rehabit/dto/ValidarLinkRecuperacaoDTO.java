package com.rehabit.dto;

import jakarta.validation.constraints.Size;

/** O token do link de recuperação, no corpo (veja AuthController). */
public class ValidarLinkRecuperacaoDTO {

    @Size(max = 200, message = "Link inválido.")
    private String token;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
