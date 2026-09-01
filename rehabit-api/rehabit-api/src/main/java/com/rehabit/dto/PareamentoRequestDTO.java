package com.rehabit.dto;

import jakarta.validation.constraints.NotBlank;

/** Corpo que o goniômetro envia para trocar o código por um token. */
public class PareamentoRequestDTO {

    @NotBlank(message = "Informe o código de pareamento.")
    private String codigo;

    /** Opcional: nome amigável do aparelho, para a clínica reconhecer na lista. */
    private String nome;

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
}
