package com.rehabit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo que o goniômetro envia para trocar o código por um token. */
public class PareamentoRequestDTO {

    @NotBlank(message = "Informe o código de pareamento.")
    @Size(max = 10, message = "O código pode ter no máximo 10 caracteres.")
    private String codigo;

    /** Opcional: nome amigável do aparelho, para a clínica reconhecer na lista. */
    @Size(max = 80, message = "O nome pode ter no máximo 80 caracteres.")
    private String nome;

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
}
