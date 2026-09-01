package com.rehabit.dto;

import java.time.LocalDateTime;

public class DispositivoDTO {

    private Integer id;
    private String nome;
    private boolean ativo;
    private LocalDateTime criadoEm;
    private LocalDateTime ultimoContato;

    public DispositivoDTO() {
    }

    public DispositivoDTO(Integer id, String nome, boolean ativo,
                           LocalDateTime criadoEm, LocalDateTime ultimoContato) {
        this.id = id;
        this.nome = nome;
        this.ativo = ativo;
        this.criadoEm = criadoEm;
        this.ultimoContato = ultimoContato;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }
    public LocalDateTime getUltimoContato() { return ultimoContato; }
    public void setUltimoContato(LocalDateTime ultimoContato) { this.ultimoContato = ultimoContato; }
}
