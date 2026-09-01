package com.rehabit.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Um goniômetro pareado com uma clínica. */
@Entity
@Table(name = "tb09_dispositivo")
public class Dispositivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tb09_id_dispositivo")
    private Integer id;

    @Column(name = "tb09_nome", nullable = false, length = 80)
    private String nome;

    @Column(name = "tb09_id_clinica", nullable = false)
    private Integer idClinica;

    /**
     * Revogação: o token é um JWT e não dá para invalidar sozinho, então cada
     * leitura confere esta flag. Desmarcar aqui corta o acesso na hora.
     */
    @Column(name = "tb09_ativo", columnDefinition = "boolean not null default true")
    private boolean ativo = true;

    @Column(name = "tb09_criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "tb09_ultimo_contato")
    private LocalDateTime ultimoContato;

    public Dispositivo() {
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

    public Integer getIdClinica() {
        return idClinica;
    }

    public void setIdClinica(Integer idClinica) {
        this.idClinica = idClinica;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(LocalDateTime criadoEm) {
        this.criadoEm = criadoEm;
    }

    public LocalDateTime getUltimoContato() {
        return ultimoContato;
    }

    public void setUltimoContato(LocalDateTime ultimoContato) {
        this.ultimoContato = ultimoContato;
    }
}
