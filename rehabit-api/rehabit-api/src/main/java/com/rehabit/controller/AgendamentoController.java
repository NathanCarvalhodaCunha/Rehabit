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

    /**
     * Aceita {@code idFisioterapeuta} (agenda de um profissional) ou
     * {@code idClinica} (agenda somada da instituição). Com {@code idClinica},
     * {@code apenasFuturos=false} inclui o que já passou.
     */
    @GetMapping
    public ResponseEntity<List<AgendamentoDTO>> listar(
            @RequestParam(required = false) Integer idFisioterapeuta,
            @RequestParam(required = false) Integer idClinica,
            @RequestParam(required = false, defaultValue = "true") boolean apenasFuturos,
            HttpServletRequest request) {
        Integer usuarioId = AuthContext.id(request);
        String usuarioTipo = AuthContext.tipo(request);

        if (idClinica != null) {
            return ResponseEntity.ok(
                    agendamentoService.listarDaClinica(idClinica, apenasFuturos, usuarioId, usuarioTipo));
        }
        if (idFisioterapeuta == null) {
            throw new com.rehabit.exception.AuthException(
                    "Informe idFisioterapeuta ou idClinica.", HttpStatus.BAD_REQUEST);
        }
        return ResponseEntity.ok(agendamentoService.listarProximos(idFisioterapeuta, usuarioId, usuarioTipo));
    }

    /** Consultas já realizadas (sessões) de toda a clínica. */
    @GetMapping("/realizadas")
    public ResponseEntity<List<AgendamentoDTO>> listarRealizadas(@RequestParam Integer idClinica,
                                                                    HttpServletRequest request) {
        return ResponseEntity.ok(agendamentoService.listarRealizadasDaClinica(
                idClinica, AuthContext.id(request), AuthContext.tipo(request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelar(@PathVariable Integer id, HttpServletRequest request) {
        agendamentoService.cancelar(id, AuthContext.id(request), AuthContext.tipo(request));
        return ResponseEntity.noContent().build();
    }
}
