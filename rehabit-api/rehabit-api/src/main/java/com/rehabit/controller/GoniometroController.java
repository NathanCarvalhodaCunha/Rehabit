package com.rehabit.controller;

import com.rehabit.dto.GoniometroComandoDTO;
import com.rehabit.dto.GoniometroComandoRespostaDTO;
import com.rehabit.dto.GoniometroDTO;
import com.rehabit.dto.GoniometroEstadoDTO;
import com.rehabit.dto.GoniometroLeituraDTO;
import com.rehabit.dto.GoniometroLeituraRespostaDTO;
import com.rehabit.dto.GoniometroSincronizarDTO;
import com.rehabit.dto.GoniometroTelemetriaDTO;
import com.rehabit.security.AuthContext;
import com.rehabit.service.GoniometroService;
import com.rehabit.service.GoniometroStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@RestController
@RequestMapping("/api/goniometro")
@CrossOrigin(origins = "*")
public class GoniometroController {

    private final GoniometroService goniometroService;
    private final GoniometroStream stream;

    public GoniometroController(GoniometroService goniometroService, GoniometroStream stream) {
        this.goniometroService = goniometroService;
        this.stream = stream;
    }

    // ---------------- Cadastro do aparelho ----------------

    @GetMapping
    public ResponseEntity<GoniometroDTO> buscar(@RequestParam Integer idClinica, HttpServletRequest request) {
        return ResponseEntity.ok(goniometroService.buscarUltimo(
                idClinica, AuthContext.id(request), AuthContext.tipo(request)));
    }

    @PostMapping("/sincronizar")
    public ResponseEntity<GoniometroDTO> sincronizar(@Valid @RequestBody GoniometroSincronizarDTO dados,
                                                        HttpServletRequest request) {
        return ResponseEntity.ok(goniometroService.sincronizar(
                dados.getIdClinica(), AuthContext.id(request), AuthContext.tipo(request)));
    }

    // ---------------- Aparelho -> servidor ----------------

    /**
     * Pacote de telemetria do ESP32. A resposta carrega o próximo comando
     * pendente e o intervalo de amostragem — é o único canal de volta que o
     * aparelho tem.
     */
    @PostMapping("/telemetria")
    public ResponseEntity<GoniometroComandoRespostaDTO> telemetria(@Valid @RequestBody GoniometroTelemetriaDTO dados,
                                                                     HttpServletRequest request) {
        return ResponseEntity.ok(goniometroService.registrarTelemetria(
                dados, AuthContext.id(request), AuthContext.tipo(request)));
    }

    // ---------------- Servidor -> site ----------------

    /** Retrato completo (usado no primeiro render e como plano B do SSE). */
    @GetMapping("/estado")
    public ResponseEntity<GoniometroEstadoDTO> estado(@RequestParam Integer idClinica, HttpServletRequest request) {
        return ResponseEntity.ok(goniometroService.estado(
                idClinica, AuthContext.id(request), AuthContext.tipo(request)));
    }

    /**
     * Fluxo de eventos em tempo real.
     *
     * O EventSource do navegador não deixa mandar cabeçalho, então o token vem
     * na query string — o JwtAuthenticationFilter aceita esse formato só neste
     * caminho.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam Integer idClinica, HttpServletRequest request) {
        GoniometroEstadoDTO inicial = goniometroService.estado(
                idClinica, AuthContext.id(request), AuthContext.tipo(request));
        goniometroService.marcarInteresse(idClinica);

        SseEmitter emitter = stream.inscrever(idClinica);
        try {
            // Estado inicial no próprio handshake: a tela já abre preenchida,
            // sem esperar o próximo pacote do aparelho.
            emitter.send(SseEmitter.event().name("estado").data(inicial));
        } catch (IOException erro) {
            emitter.completeWithError(erro);
        }
        return emitter;
    }

    // ---------------- Site -> aparelho ----------------

    @PostMapping("/comando")
    public ResponseEntity<GoniometroEstadoDTO> comando(@Valid @RequestBody GoniometroComandoDTO dados,
                                                         HttpServletRequest request) {
        return ResponseEntity.ok(goniometroService.enfileirarComando(
                dados.getIdClinica(), dados.getComando(), AuthContext.id(request), AuthContext.tipo(request)));
    }

    @PostMapping("/captura/iniciar")
    public ResponseEntity<GoniometroEstadoDTO> iniciarCaptura(@Valid @RequestBody GoniometroSincronizarDTO dados,
                                                                HttpServletRequest request) {
        return ResponseEntity.ok(goniometroService.iniciarCaptura(
                dados.getIdClinica(), AuthContext.id(request), AuthContext.tipo(request)));
    }

    @PostMapping("/captura/parar")
    public ResponseEntity<GoniometroEstadoDTO> pararCaptura(@Valid @RequestBody GoniometroSincronizarDTO dados,
                                                              HttpServletRequest request) {
        return ResponseEntity.ok(goniometroService.pararCaptura(
                dados.getIdClinica(), AuthContext.id(request), AuthContext.tipo(request)));
    }

    // ---------------- Compatibilidade com o firmware/telas antigos ----------------

    @PostMapping("/leitura")
    public ResponseEntity<Void> registrarLeitura(@Valid @RequestBody GoniometroLeituraDTO dados,
                                                    HttpServletRequest request) {
        goniometroService.registrarLeitura(
                dados.getIdClinica(), AuthContext.id(request), AuthContext.tipo(request), dados.getAngulo());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/leitura")
    public ResponseEntity<GoniometroLeituraRespostaDTO> buscarLeitura(@RequestParam Integer idClinica,
                                                                          HttpServletRequest request) {
        var angulo = goniometroService.buscarLeituraAtual(idClinica, AuthContext.id(request), AuthContext.tipo(request));
        return ResponseEntity.ok(new GoniometroLeituraRespostaDTO(angulo));
    }
}
