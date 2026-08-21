package com.rehabit.dto;

public class FisioterapeutaResumoDTO {

    private Integer id;
    private String nome;
    private String especialidade;
    private String foto;
    private long pacientesAtivos;

    public FisioterapeutaResumoDTO() {
    }

    public FisioterapeutaResumoDTO(Integer id, String nome, String especialidade, String foto, long pacientesAtivos) {
        this.id = id;
        this.nome = nome;
        this.especialidade = especialidade;
        this.foto = foto;
        this.pacientesAtivos = pacientesAtivos;
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

    public String getEspecialidade() {
        return especialidade;
    }

    public void setEspecialidade(String especialidade) {
        this.especialidade = especialidade;
    }

    public String getFoto() {
        return foto;
    }

    public void setFoto(String foto) {
        this.foto = foto;
    }

    public long getPacientesAtivos() {
        return pacientesAtivos;
    }

    public void setPacientesAtivos(long pacientesAtivos) {
        this.pacientesAtivos = pacientesAtivos;
    }
}
