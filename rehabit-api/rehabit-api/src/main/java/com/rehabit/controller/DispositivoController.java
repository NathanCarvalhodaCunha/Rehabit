package com.rehabit.controller;

import com.rehabit.dto.CodigoPareamentoDTO;
import com.rehabit.dto.DispositivoDTO;
import com.rehabit.dto.PareamentoRequestDTO;
import com.rehabit.dto.PareamentoRespostaDTO;
import com.rehabit.security.AuthContext;
import com.rehabit.service.DispositivoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dispositivos")
@CrossOrigin(origins = "*")
public class DispositivoController {

    private final DispositivoService dispositivoService;

    public DispositivoController(DispositivoService dispositivoService) {
        this.dispositivoService = dispositivoService;
    }

    /** Código que a clínica mostra na tela para digitar no portal do aparelho. */
    @PostMapping("/pareamento")
    public ResponseEntity<CodigoPareamentoDTO> gerarCodigo(@RequestParam Integer idClinica,
                                                              HttpServletRequest request) {
        return ResponseEntity.ok(dispositivoService.gerarCodigo(
                idClinica, AuthContext.id(request), AuthContext.tipo(request)));
    }

    /**
     * Único endpoint público do módulo: é chamado pelo goniômetro, que ainda
     * não tem token. Ver JwtAuthenticationFilter.isPublico().
     */
    @PostMapping("/parear")
    public ResponseEntity<PareamentoRespostaDTO> parear(@Valid @RequestBody PareamentoRequestDTO dados) {
        return ResponseEntity.ok(dispositivoService.parear(dados));
    }

    @GetMapping
    public ResponseEntity<List<DispositivoDTO>> listar(@RequestParam Integer idClinica,
                                                         HttpServletRequest request) {
        return ResponseEntity.ok(dispositivoService.listar(
                idClinica, AuthContext.id(request), AuthContext.tipo(request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revogar(@PathVariable Integer id, HttpServletRequest request) {
        dispositivoService.revogar(id, AuthContext.id(request), AuthContext.tipo(request));
        return ResponseEntity.noContent().build();
    }
}
