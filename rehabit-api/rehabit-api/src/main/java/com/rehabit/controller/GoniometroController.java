package com.rehabit.controller;

import com.rehabit.dto.GoniometroDTO;
import com.rehabit.dto.GoniometroSincronizarDTO;
import com.rehabit.security.AuthContext;
import com.rehabit.service.GoniometroService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/goniometro")
@CrossOrigin(origins = "*")
public class GoniometroController {

    private final GoniometroService goniometroService;

    public GoniometroController(GoniometroService goniometroService) {
        this.goniometroService = goniometroService;
    }

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
}
