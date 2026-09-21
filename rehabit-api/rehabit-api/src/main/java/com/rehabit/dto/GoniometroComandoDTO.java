package com.rehabit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Comando que a tela enfileira para o aparelho (site -> ESP32). */
public class GoniometroComandoDTO {

    @NotNull(message = "A clínica é obrigatória.")
    private Integer idClinica;

    @NotBlank(message = "O comando é obrigatório.")
    private String comando;

    public GoniometroComandoDTO() {
    }

    public Integer getIdClinica() {
        return idClinica;
    }

    public void setIdClinica(Integer idClinica) {
        this.idClinica = idClinica;
    }

    public String getComando() {
        return comando;
    }

    public void setComando(String comando) {
        this.comando = comando;
    }
}
