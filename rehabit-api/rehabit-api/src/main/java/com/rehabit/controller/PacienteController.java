package com.rehabit.controller;

import com.rehabit.dto.PacienteCreateDTO;
import com.rehabit.dto.PacienteDetalheDTO;
import com.rehabit.dto.PacienteResumoDTO;
import com.rehabit.dto.PacienteUpdateDTO;
import com.rehabit.dto.SessaoCreateDTO;
import com.rehabit.dto.SessaoDTO;
import com.rehabit.security.AuthContext;
import com.rehabit.service.PacienteService;
import com.rehabit.service.SessaoService;
import jakarta.servlet.http.HttpServletRequest;
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
    private final SessaoService sessaoService;

    public PacienteController(PacienteService pacienteService, SessaoService sessaoService) {
        this.pacienteService = pacienteService;
        this.sessaoService = sessaoService;
    }

    @PostMapping
    public ResponseEntity<PacienteDetalheDTO> cadastrar(@Valid @RequestBody PacienteCreateDTO dados,
                                                            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                pacienteService.cadastrar(dados, AuthContext.id(request), AuthContext.tipo(request)));
    }

    @GetMapping
    public ResponseEntity<List<PacienteResumoDTO>> listar(@RequestParam Integer idFisioterapeuta,
                                                              HttpServletRequest request) {
        return ResponseEntity.ok(pacienteService.listarPorFisioterapeuta(
                idFisioterapeuta, AuthContext.id(request), AuthContext.tipo(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PacienteDetalheDTO> buscar(@PathVariable Integer id, HttpServletRequest request) {
        return ResponseEntity.ok(pacienteService.buscar(
                id, AuthContext.id(request), AuthContext.tipo(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PacienteDetalheDTO> atualizar(@PathVariable Integer id,
                                                           @Valid @RequestBody PacienteUpdateDTO dados,
                                                           HttpServletRequest request) {
        return ResponseEntity.ok(pacienteService.atualizar(
                id, dados, AuthContext.id(request), AuthContext.tipo(request)));
    }

    @PostMapping("/{id}/sessoes")
    public ResponseEntity<SessaoDTO> cadastrarSessao(@PathVariable Integer id,
                                                        @Valid @RequestBody SessaoCreateDTO dados,
                                                        HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sessaoService.cadastrar(
                id, dados, AuthContext.id(request), AuthContext.tipo(request)));
    }

    @GetMapping("/{id}/sessoes")
    public ResponseEntity<List<SessaoDTO>> listarSessoes(@PathVariable Integer id, HttpServletRequest request) {
        return ResponseEntity.ok(sessaoService.listarPorPaciente(
                id, AuthContext.id(request), AuthContext.tipo(request)));
    }

    @DeleteMapping("/{id}/sessoes/{idSessao}")
    public ResponseEntity<Void> excluirSessao(@PathVariable Integer id, @PathVariable Integer idSessao,
                                                 HttpServletRequest request) {
        sessaoService.excluir(id, idSessao, AuthContext.id(request), AuthContext.tipo(request));
        return ResponseEntity.noContent().build();
    }
}
