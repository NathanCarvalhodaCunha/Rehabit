package com.rehabit.controller;

import com.rehabit.dto.PacienteCreateDTO;
import com.rehabit.dto.PacienteDetalheDTO;
import com.rehabit.dto.PacienteResumoDTO;
import com.rehabit.service.PacienteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pacientes")
@CrossOrigin(origins = "*")
public class PacienteController {

    private final PacienteService pacienteService;

    public PacienteController(PacienteService pacienteService) {
        this.pacienteService = pacienteService;
    }

    @PostMapping
    public ResponseEntity<PacienteDetalheDTO> cadastrar(@Valid @RequestBody PacienteCreateDTO dados) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pacienteService.cadastrar(dados));
    }

    @GetMapping
    public ResponseEntity<List<PacienteResumoDTO>> listar(@RequestParam Integer idFisioterapeuta) {
        return ResponseEntity.ok(pacienteService.listarPorFisioterapeuta(idFisioterapeuta));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PacienteDetalheDTO> buscar(@PathVariable Integer id) {
        return ResponseEntity.ok(pacienteService.buscar(id));
    }
}
