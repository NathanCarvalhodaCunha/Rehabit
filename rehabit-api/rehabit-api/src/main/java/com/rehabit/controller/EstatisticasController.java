package com.rehabit.controller;

import com.rehabit.dto.EstatisticaCardDTO;
import com.rehabit.security.AuthContext;
import com.rehabit.service.EstatisticasService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/estatisticas")
@CrossOrigin(origins = "*")
public class EstatisticasController {

    private final EstatisticasService estatisticasService;

    public EstatisticasController(EstatisticasService estatisticasService) {
        this.estatisticasService = estatisticasService;
    }

    /** Os indicadores saem do próprio token — não há id na URL para adulterar. */
    @GetMapping
    public ResponseEntity<List<EstatisticaCardDTO>> resumo(HttpServletRequest request) {
        return ResponseEntity.ok(estatisticasService.resumo(
                AuthContext.id(request), AuthContext.tipo(request)));
    }
}
