package com.rehabit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * De onde a API tira "agora" quando carimba um registro.
 *
 * O container do Render roda em UTC: sem um fuso explícito, uma sessão
 * registrada às 22h em São Paulo saía gravada com a hora de Londres e, depois
 * das 21h, com a data do dia seguinte. Quem precisa do momento atual pede este
 * relógio e converte para FUSO — e os testes trocam o relógio por um parado.
 */
@Configuration
public class RelogioConfig {

    /** Fuso das clínicas. Brasília não tem mais horário de verão desde 2019. */
    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @Bean
    public Clock relogio() {
        return Clock.system(FUSO);
    }
}
