package com.rehabit.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sinal de vida da API: sem token, sem banco, resposta imediata.
 *
 * Existe por causa do plano gratuito do Render, que derruba o container
 * depois de ~15 min sem tráfego. A chamada seguinte paga o religamento
 * inteiro — medimos 146 s de espera contra 0,48 s com a instância quente.
 * Quem pagava essa conta era sempre o aparelho novo: nos já usados a sessão
 * está no localStorage e o login, única chamada que a tela de entrada faz,
 * nunca acontece.
 *
 * O workflow .github/workflows/manter-api-acordada.yml bate aqui de dez em
 * dez minutos para que a instância não chegue nos quinze de ociosidade.
 *
 * De propósito não encosta no banco: a função é impedir o spin-down, e uma
 * consulta a cada dez minutos para sempre seria peso sem retorno. O uptime
 * vai junto porque responde de graça a pergunta que importa quando algo
 * parece fora do ar — se a instância acabou de reiniciar ou está de pé há
 * dias.
 */
@RestController
@RequestMapping("/api/health")
@CrossOrigin(origins = "*")
public class HealthController {

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("status", "ok");
        corpo.put("uptimeSegundos", ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
        return ResponseEntity.ok(corpo);
    }
}
