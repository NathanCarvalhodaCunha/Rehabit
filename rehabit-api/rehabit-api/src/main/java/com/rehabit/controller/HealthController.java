package com.rehabit.controller;

import com.rehabit.service.ManterApiAcordada;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sinal de vida da API: sem token, sem banco, resposta imediata.
 *
 * É o alvo do auto-ping (ManterApiAcordada), que impede o plano gratuito do
 * Render de hibernar a instância, e do workflow manter-api-acordada.yml, que
 * fica de rede de segurança.
 *
 * De propósito não encosta no banco: a função é gerar tráfego, e uma consulta
 * a cada cinco minutos para sempre seria peso sem retorno.
 *
 * Os campos além do status existem para responder de fora, sem acesso aos
 * logs do Render, se o mecanismo está funcionando: um uptime de poucos
 * minutos quer dizer que a instância acabou de subir (deploy ou religamento),
 * e ultimoAutoPingSegundosAtras acima de ~300 quer dizer que o auto-ping parou
 * de voltar. Da primeira vez faltou justamente isso — o ping falhava havia
 * dias e nada na resposta mostrava.
 *
 * "versao" é o commit no ar (RENDER_GIT_COMMIT). O site, na Vercel, publica
 * cada merge em segundos; a API depende do deploy do Render, que de 14 a
 * 21/10/2026 não aconteceu — e uma conta nova não entrava no site sem que
 * nada dissesse por quê. O workflow compara este commit com o último da API
 * no main. O repositório é público, então dizer qual commit roda não revela
 * nada que o código já não mostre.
 */
@RestController
@RequestMapping("/api/health")
@CrossOrigin(origins = "*")
public class HealthController {

    private final ManterApiAcordada manterApiAcordada;
    private final String versao;

    public HealthController(ManterApiAcordada manterApiAcordada, @Value("${rehabit.versao:}") String versao) {
        this.manterApiAcordada = manterApiAcordada;
        this.versao = versao == null || versao.isBlank() ? null : versao.trim();
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("status", "ok");
        corpo.put("uptimeSegundos", ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
        corpo.put("autoPing", manterApiAcordada.ativo());
        Instant ultimo = manterApiAcordada.ultimoOk();
        corpo.put("ultimoAutoPingSegundosAtras",
                ultimo == null ? null : Duration.between(ultimo, Instant.now()).toSeconds());
        corpo.put("versao", versao);
        return ResponseEntity.ok(corpo);
    }
}
