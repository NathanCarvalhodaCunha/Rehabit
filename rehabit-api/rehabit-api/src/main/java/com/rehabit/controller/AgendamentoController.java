package com.rehabit.controller;

import com.rehabit.dto.AgendamentoCreateDTO;
import com.rehabit.dto.AgendamentoDTO;
import com.rehabit.security.AuthContext;
import com.rehabit.service.AgendamentoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/agendamentos")
@CrossOrigin(origins = "*")
public class AgendamentoController {

    private final AgendamentoService agendamentoService;

    public AgendamentoController(AgendamentoService agendamentoService) {
        this.agendamentoService = agendamentoService;
    }

    @PostMapping
    public ResponseEntity<AgendamentoDTO> agendar(@Valid @RequestBody AgendamentoCreateDTO dados,
                                                     HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(agendamentoService.agendar(
                dados, AuthContext.id(request), AuthContext.tipo(request)));
    }

    @GetMapping
    public ResponseEntity<List<AgendamentoDTO>> listar(@RequestParam Integer idFisioterapeuta,
                                                          HttpServletRequest request) {
        return ResponseEntity.ok(agendamentoService.listarProximos(
                idFisioterapeuta, AuthContext.id(request), AuthContext.tipo(request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelar(@PathVariable Integer id, HttpServletRequest request) {
        agendamentoService.cancelar(id, AuthContext.id(request), AuthContext.tipo(request));
        return ResponseEntity.noContent().build();
    }
}
