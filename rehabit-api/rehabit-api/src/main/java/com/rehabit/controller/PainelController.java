package com.rehabit.controller;

import com.rehabit.dto.AlertaDTO;
import com.rehabit.dto.BuscaResultadoDTO;
import com.rehabit.security.AuthContext;
import com.rehabit.service.AlertaService;
import com.rehabit.service.BuscaService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Recursos transversais: alertas e busca global. Ambos derivam do token. */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class PainelController {

    private final AlertaService alertaService;
    private final BuscaService buscaService;

    public PainelController(AlertaService alertaService, BuscaService buscaService) {
        this.alertaService = alertaService;
        this.buscaService = buscaService;
    }

    @GetMapping("/alertas")
    public ResponseEntity<List<AlertaDTO>> alertas(HttpServletRequest request) {
        return ResponseEntity.ok(alertaService.listar(
                AuthContext.id(request), AuthContext.tipo(request)));
    }

    @GetMapping("/busca")
    public ResponseEntity<List<BuscaResultadoDTO>> buscar(@RequestParam(name = "q") String termo,
                                                            HttpServletRequest request) {
        return ResponseEntity.ok(buscaService.buscar(
                termo, AuthContext.id(request), AuthContext.tipo(request)));
    }
}
