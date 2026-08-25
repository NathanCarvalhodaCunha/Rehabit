package com.rehabit.dto;

/**
 * Um cartão do painel de indicadores. O serviço decide quais cartões existem
 * conforme o tipo de conta, e o front só renderiza a lista — assim a tela não
 * precisa saber se está mostrando dados de clínica ou de profissional.
 */
public class EstatisticaCardDTO {

    private String rotulo;
    private String valor;
    private String detalhe;

    public EstatisticaCardDTO() {
    }

    public EstatisticaCardDTO(String rotulo, String valor, String detalhe) {
        this.rotulo = rotulo;
        this.valor = valor;
        this.detalhe = detalhe;
    }

    public String getRotulo() {
        return rotulo;
    }

    public void setRotulo(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getValor() {
        return valor;
    }

    public void setValor(String valor) {
        this.valor = valor;
    }

    public String getDetalhe() {
        return detalhe;
    }

    public void setDetalhe(String detalhe) {
        this.detalhe = detalhe;
    }
}
