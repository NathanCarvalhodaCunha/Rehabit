package com.rehabit.controller;

import com.rehabit.dto.GoniometroDTO;
import com.rehabit.dto.GoniometroSincronizarDTO;
import com.rehabit.service.GoniometroService;
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
    public ResponseEntity<GoniometroDTO> buscar(@RequestParam Integer idClinica) {
        return ResponseEntity.ok(goniometroService.buscarUltimo(idClinica));
    }

    @PostMapping("/sincronizar")
    public ResponseEntity<GoniometroDTO> sincronizar(@Valid @RequestBody GoniometroSincronizarDTO dados) {
        return ResponseEntity.ok(goniometroService.sincronizar(dados.getIdClinica()));
    }
}
