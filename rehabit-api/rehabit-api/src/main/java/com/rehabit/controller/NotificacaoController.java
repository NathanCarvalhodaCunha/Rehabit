package com.rehabit.controller;

import com.rehabit.dto.MarcarLidasRequestDTO;
import com.rehabit.dto.NotificacaoDTO;
import com.rehabit.security.AuthContext;
import com.rehabit.service.NotificacaoService;
import jakarta.servlet.http.HttpServletRequest;
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
    public ResponseEntity<List<NotificacaoDTO>> listar(@RequestParam Integer idClinica, HttpServletRequest request) {
        return ResponseEntity.ok(notificacaoService.listarPorClinica(
                idClinica, AuthContext.id(request), AuthContext.tipo(request)));
    }

    @PutMapping("/marcar-lidas")
    public ResponseEntity<Void> marcarLidas(@Valid @RequestBody MarcarLidasRequestDTO dados, HttpServletRequest request) {
        notificacaoService.marcarTodasComoLidas(
                dados.getIdClinica(), AuthContext.id(request), AuthContext.tipo(request));
        return ResponseEntity.ok().build();
    }
}
