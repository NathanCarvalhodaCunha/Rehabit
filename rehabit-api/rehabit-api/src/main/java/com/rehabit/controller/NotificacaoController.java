package com.rehabit.controller;

import com.rehabit.dto.MarcarLidasRequestDTO;
import com.rehabit.dto.NotificacaoDTO;
import com.rehabit.service.NotificacaoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notificacoes")
@CrossOrigin(origins = "*")
public class NotificacaoController {

    private final NotificacaoService notificacaoService;

    public NotificacaoController(NotificacaoService notificacaoService) {
        this.notificacaoService = notificacaoService;
    }

    @GetMapping
    public ResponseEntity<List<NotificacaoDTO>> listar(@RequestParam Integer idClinica) {
        return ResponseEntity.ok(notificacaoService.listarPorClinica(idClinica));
    }

    @PutMapping("/marcar-lidas")
    public ResponseEntity<Void> marcarLidas(@Valid @RequestBody MarcarLidasRequestDTO dados) {
        notificacaoService.marcarTodasComoLidas(dados.getIdClinica());
        return ResponseEntity.ok().build();
    }
}
