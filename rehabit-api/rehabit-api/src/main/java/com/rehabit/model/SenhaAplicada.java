package com.rehabit.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * A última senha de configuração aplicada às contas de uma clínica de
 * roteiro (a conta da apresentação), guardada só como BCrypt. Serve para
 * aplicar a variável de novo apenas quando ela muda: uma senha trocada pela
 * tela — às vezes justamente porque a da variável vazou — fica valendo nas
 * subidas seguintes.
 */
@Entity
@Table(name = "tb14_senha_aplicada")
public class SenhaAplicada {

    /** O CNPJ da clínica do roteiro. */
    @Id
    @Column(name = "tb14_cnpj", length = 18)
    private String cnpj;

    @Column(name = "tb14_impressao", nullable = false, length = 100)
    private String impressao;

    @Column(name = "tb14_aplicada_em", nullable = false)
    private Instant aplicadaEm;

    public SenhaAplicada() {
    }

    public SenhaAplicada(String cnpj, String impressao, Instant aplicadaEm) {
        this.cnpj = cnpj;
        this.impressao = impressao;
        this.aplicadaEm = aplicadaEm;
    }

    public String getCnpj() {
        return cnpj;
    }

    public String getImpressao() {
        return impressao;
    }

    public Instant getAplicadaEm() {
        return aplicadaEm;
    }
}
