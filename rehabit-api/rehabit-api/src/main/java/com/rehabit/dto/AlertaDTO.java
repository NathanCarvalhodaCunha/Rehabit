package com.rehabit.dto;

/** Aviso que o sistema levanta sozinho olhando os dados do paciente. */
public class AlertaDTO {

    /** ATENCAO (algo precisa de ação) ou BOM (conquista, vale destacar). */
    private String nivel;
    private String titulo;
    private String descricao;
    private Integer idPaciente;
    private String nomePaciente;

    public AlertaDTO() {
    }

    public AlertaDTO(String nivel, String titulo, String descricao, Integer idPaciente, String nomePaciente) {
        this.nivel = nivel;
        this.titulo = titulo;
        this.descricao = descricao;
        this.idPaciente = idPaciente;
        this.nomePaciente = nomePaciente;
    }

    public String getNivel() {
        return nivel;
    }

    public void setNivel(String nivel) {
        this.nivel = nivel;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public Integer getIdPaciente() {
        return idPaciente;
    }

    public void setIdPaciente(Integer idPaciente) {
        this.idPaciente = idPaciente;
    }

    public String getNomePaciente() {
        return nomePaciente;
    }

    public void setNomePaciente(String nomePaciente) {
        this.nomePaciente = nomePaciente;
    }
}
