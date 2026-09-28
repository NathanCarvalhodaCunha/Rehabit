package com.rehabit.controller;

import com.rehabit.dto.ClinicaPerfilDTO;
import com.rehabit.dto.ClinicaUpdateDTO;
import com.rehabit.dto.ExclusaoContaDTO;
import com.rehabit.dto.ResumoExclusaoContaDTO;
import com.rehabit.security.AuthContext;
import com.rehabit.service.ClinicaService;
import com.rehabit.service.ExclusaoDeContaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clinicas")
@CrossOrigin(origins = "*")
public class ClinicaController {

    private final ClinicaService clinicaService;
    private final ExclusaoDeContaService exclusaoDeContaService;

    public ClinicaController(ClinicaService clinicaService, ExclusaoDeContaService exclusaoDeContaService) {
        this.clinicaService = clinicaService;
        this.exclusaoDeContaService = exclusaoDeContaService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClinicaPerfilDTO> buscar(@PathVariable Integer id, HttpServletRequest request) {
        return ResponseEntity.ok(
                clinicaService.buscarPerfil(id, AuthContext.id(request), AuthContext.tipo(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClinicaPerfilDTO> atualizar(@PathVariable Integer id,
                                                        @Valid @RequestBody ClinicaUpdateDTO dados,
                                                        HttpServletRequest request) {
        return ResponseEntity.ok(
                clinicaService.atualizar(id, dados, AuthContext.id(request), AuthContext.tipo(request)));
    }

    @PutMapping("/{id}/tutorial-visto")
    public ResponseEntity<Void> marcarTutorialVisto(@PathVariable Integer id) {
        clinicaService.marcarTutorialVisto(id);
        return ResponseEntity.ok().build();
    }

    /** O que a exclusão da conta vai apagar, para a tela mostrar antes da confirmação. */
    @GetMapping("/{id}/exclusao")
    public ResponseEntity<ResumoExclusaoContaDTO> resumoDaExclusao(@PathVariable Integer id,
                                                                    HttpServletRequest request) {
        return ResponseEntity.ok(
                exclusaoDeContaService.resumo(id, AuthContext.id(request), AuthContext.tipo(request)));
    }

    /**
     * Exclui a conta e tudo que pertence a ela. POST, e não DELETE, porque
     * leva a senha no corpo: há cliente e proxy HTTP que descartam o corpo de
     * um DELETE — e o Render tem proxy na frente desta API.
     */
    @PostMapping("/{id}/exclusao")
    public ResponseEntity<Void> excluirConta(@PathVariable Integer id,
                                             @Valid @RequestBody ExclusaoContaDTO dados,
                                             HttpServletRequest request) {
        exclusaoDeContaService.excluir(id, dados.senha(), AuthContext.id(request), AuthContext.tipo(request));
        return ResponseEntity.noContent().build();
    }
}
