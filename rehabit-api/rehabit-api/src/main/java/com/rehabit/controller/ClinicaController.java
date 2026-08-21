package com.rehabit.controller;

import com.rehabit.dto.ClinicaPerfilDTO;
import com.rehabit.dto.ClinicaUpdateDTO;
import com.rehabit.service.ClinicaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clinicas")
@CrossOrigin(origins = "*")
public class ClinicaController {

    private final ClinicaService clinicaService;

    public ClinicaController(ClinicaService clinicaService) {
        this.clinicaService = clinicaService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClinicaPerfilDTO> buscar(@PathVariable Integer id) {
        return ResponseEntity.ok(clinicaService.buscarPerfil(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClinicaPerfilDTO> atualizar(@PathVariable Integer id,
                                                        @Valid @RequestBody ClinicaUpdateDTO dados) {
        return ResponseEntity.ok(clinicaService.atualizar(id, dados));
    }
}
