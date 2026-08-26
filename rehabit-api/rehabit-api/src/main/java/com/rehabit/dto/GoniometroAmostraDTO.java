package com.rehabit.dto;

import java.math.BigDecimal;

/** Uma leitura do buffer ao vivo: ângulo + instante em epoch ms (fácil de plotar no JS). */
public class GoniometroAmostraDTO {

    private long t;
    private BigDecimal angulo;

    public GoniometroAmostraDTO() {
    }

    public GoniometroAmostraDTO(long t, BigDecimal angulo) {
        this.t = t;
        this.angulo = angulo;
    }

    public long getT() {
        return t;
    }

    public void setT(long t) {
        this.t = t;
    }

    public BigDecimal getAngulo() {
        return angulo;
    }

    public void setAngulo(BigDecimal angulo) {
        this.angulo = angulo;
    }
}
