package com.rehabit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
// O goniômetro precisa de duas tarefas de fundo: o keep-alive das conexões
// SSE e o vigia que percebe quando o aparelho para de mandar pacotes.
@EnableScheduling
public class RehabitApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(RehabitApiApplication.class, args);
    }
}
