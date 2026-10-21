package com.rehabit.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Qual clínica é a de um roteiro (a conta da apresentação), e a última senha
 * de configuração aplicada a ela (só o BCrypt).
 *
 * O id fixa a identidade: na primeira vez, a clínica só é aceita se tiver o
 * e-mail E o CNPJ do roteiro — o e-mail é da equipe, e cadastrar uma clínica
 * com ele exige o código mandado para lá. Daí em diante a manutenção segue
 * esse id, mesmo que a equipe troque o e-mail ou o CNPJ pela tela, e nunca
 * passa para outra clínica que adote um deles (o CNPJ está num repositório
 * público).
 *
 * A senha guardada serve para aplicar a variável de novo só quando ela muda:
 * uma senha trocada pela tela — às vezes porque a da variável vazou — fica.
 * E "agendaAte" é até onde a agenda já foi estendida: só os dias depois dele
 * são criados, então uma consulta que a equipe cancelou não volta, e um
 * paciente reativado não ganha consultas no tempo em que estava de alta.
 */
@Entity
@Table(name = "tb14_conta_do_roteiro")
public class ContaDoRoteiro {

    /** O CNPJ do roteiro (o do código, não necessariamente o atual da clínica). */
    @Id
    @Column(name = "tb14_cnpj_roteiro", length = 18)
    private String cnpjDoRoteiro;

    @Column(name = "tb14_id_clinica", nullable = false)
    private Integer idClinica;

    @Column(name = "tb14_senha_aplicada", length = 100)
    private String senhaAplicada;

    @Column(name = "tb14_senha_aplicada_em")
    private Instant senhaAplicadaEm;

    /** Até que dia a agenda dos pacientes do roteiro já foi estendida. */
    @Column(name = "tb14_agenda_ate")
    private LocalDate agendaAte;

    public ContaDoRoteiro() {
    }

    public ContaDoRoteiro(String cnpjDoRoteiro, Integer idClinica) {
        this.cnpjDoRoteiro = cnpjDoRoteiro;
        this.idClinica = idClinica;
    }

    public String getCnpjDoRoteiro() {
        return cnpjDoRoteiro;
    }

    public Integer getIdClinica() {
        return idClinica;
    }

    public String getSenhaAplicada() {
        return senhaAplicada;
    }

    public Instant getSenhaAplicadaEm() {
        return senhaAplicadaEm;
    }

    public LocalDate getAgendaAte() {
        return agendaAte;
    }

    public void setAgendaAte(LocalDate agendaAte) {
        this.agendaAte = agendaAte;
    }

    /** A clínica do roteiro foi excluída e criada de novo: recomeça do zero. */
    public void fixarEm(Integer idClinica) {
        this.idClinica = idClinica;
        this.senhaAplicada = null;
        this.senhaAplicadaEm = null;
        this.agendaAte = null;
    }

    public void registrarSenha(String senhaCifrada, Instant quando) {
        this.senhaAplicada = senhaCifrada;
        this.senhaAplicadaEm = quando;
    }
}
