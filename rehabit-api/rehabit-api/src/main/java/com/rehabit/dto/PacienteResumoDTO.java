package com.rehabit.dto;

public class PacienteResumoDTO {

    private Integer id;
    private String nome;
    private String situacao;
    private String ultimaSessao;
    private String selo;
    private String foto;
    private String status;

    public PacienteResumoDTO() {
    }

    public PacienteResumoDTO(Integer id, String nome, String situacao, String ultimaSessao, String selo, String foto) {
        this(id, nome, situacao, ultimaSessao, selo, foto, null);
    }

    public PacienteResumoDTO(Integer id, String nome, String situacao, String ultimaSessao, String selo,
                              String foto, String status) {
        this.id = id;
        this.nome = nome;
        this.situacao = situacao;
        this.ultimaSessao = ultimaSessao;
        this.selo = selo;
        this.foto = foto;
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public String getSituacao() {
        return situacao;
    }

    public void setSituacao(String situacao) {
        this.situacao = situacao;
    }

    public String getUltimaSessao() {
        return ultimaSessao;
    }

    public void setUltimaSessao(String ultimaSessao) {
        this.ultimaSessao = ultimaSessao;
    }

    public String getSelo() {
        return selo;
    }

    public void setSelo(String selo) {
        this.selo = selo;
    }

    public String getFoto() {
        return foto;
    }

    public void setFoto(String foto) {
        this.foto = foto;
    }
}
