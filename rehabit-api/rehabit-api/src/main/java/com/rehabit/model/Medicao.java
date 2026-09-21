package com.rehabit.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "tb06_medicao")
public class Medicao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tb06_id_medicao")
    private Integer id;

    @Column(name = "tb06_amplitude_media", precision = 6, scale = 2)
    private BigDecimal amplitudeMedia;

    @Column(name = "tb06_data_medicao")
    private LocalDate dataMedicao;

    @Column(name = "tb06_hora_medicao")
    private LocalTime horaMedicao;

    @Column(name = "tb06_id_sessoes", nullable = false)
    private Integer idSessao;

    /**
     * A curva do movimento gravada na captura, como JSON: uma lista de pares
     * [ms desde o início, ângulo]. Fica aqui, ao lado da amplitude que saiu
     * dela, porque é 1:1 com a medição — e como TEXT, porque são centenas de
     * pontos que só se lê inteiros, nunca por consulta.
     */
    // TEXT, e não @Lob: em produção o banco é PostgreSQL, onde o Hibernate mapeia
    // @Lob String para large object (oid) e a leitura quebra. É também a
    // convenção que o resto do projeto já usa para texto longo (ver Sessao).
    @Column(name = "tb06_curva", columnDefinition = "TEXT")
    private String curva;

    public Medicao() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public BigDecimal getAmplitudeMedia() {
        return amplitudeMedia;
    }

    public void setAmplitudeMedia(BigDecimal amplitudeMedia) {
        this.amplitudeMedia = amplitudeMedia;
    }

    public LocalDate getDataMedicao() {
        return dataMedicao;
    }

    public void setDataMedicao(LocalDate dataMedicao) {
        this.dataMedicao = dataMedicao;
    }

    public LocalTime getHoraMedicao() {
        return horaMedicao;
    }

    public void setHoraMedicao(LocalTime horaMedicao) {
        this.horaMedicao = horaMedicao;
    }

    public Integer getIdSessao() {
        return idSessao;
    }

    public void setIdSessao(Integer idSessao) {
        this.idSessao = idSessao;
    }

    public String getCurva() {
        return curva;
    }

    public void setCurva(String curva) {
        this.curva = curva;
    }
}
