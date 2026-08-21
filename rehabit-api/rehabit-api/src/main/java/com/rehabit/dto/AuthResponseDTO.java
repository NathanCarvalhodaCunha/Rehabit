package com.rehabit.dto;

public class AuthResponseDTO {

    private Integer id;
    private String tipo;   // "CLINICA" ou "FISIOTERAPEUTA"
    private String nome;
    private String email;
    private String foto;

    public AuthResponseDTO() {
    }

    public AuthResponseDTO(Integer id, String tipo, String nome, String email, String foto) {
        this.id = id;
        this.tipo = tipo;
        this.nome = nome;
        this.email = email;
        this.foto = foto;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFoto() {
        return foto;
    }

    public void setFoto(String foto) {
        this.foto = foto;
    }
}
