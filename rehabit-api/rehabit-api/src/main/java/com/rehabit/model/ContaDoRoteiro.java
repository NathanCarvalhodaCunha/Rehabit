package com.rehabit.model;

import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * Qual clínica é a de um roteiro (a conta da apresentação ou a da
 * demonstração), e até onde a agenda dela já foi estendida.
 *
 * O id fixa a identidade: na primeira vez, a clínica só é aceita se tiver o
 * e-mail E o CNPJ do roteiro — o e-mail é do domínio reservado .example, que
 * o cadastro pela tela não aceita (ele confere se o domínio recebe e-mail e
 * manda um código para lá). Daí em diante a manutenção segue
 * esse id, mesmo que a equipe troque o e-mail ou o CNPJ pela tela, e nunca
 * passa para outra clínica que adote um deles (o CNPJ está num repositório
 * público).
 *
 * "agendaAte" é até onde a agenda já foi estendida: só os dias depois dele
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

    public LocalDate getAgendaAte() {
        return agendaAte;
    }

    public void setAgendaAte(LocalDate agendaAte) {
        this.agendaAte = agendaAte;
    }

    /** A clínica do roteiro foi excluída e criada de novo: recomeça do zero. */
    public void fixarEm(Integer idClinica) {
        this.idClinica = idClinica;
        this.agendaAte = null;
    }
}
