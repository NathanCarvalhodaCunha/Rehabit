package com.rehabit.dto;

public class BuscaResultadoDTO {

    /** PACIENTE ou PROFISSIONAL — define para onde o resultado leva. */
    private String tipo;
    private Integer id;
    private String nome;
    private String detalhe;
    private String foto;

    public BuscaResultadoDTO() {
    }

    public BuscaResultadoDTO(String tipo, Integer id, String nome, String detalhe, String foto) {
        this.tipo = tipo;
        this.id = id;
        this.nome = nome;
        this.detalhe = detalhe;
        this.foto = foto;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getDetalhe() {
        return detalhe;
    }

    public void setDetalhe(String detalhe) {
        this.detalhe = detalhe;
    }

    public String getFoto() {
        return foto;
    }

    public void setFoto(String foto) {
        this.foto = foto;
    }
}
