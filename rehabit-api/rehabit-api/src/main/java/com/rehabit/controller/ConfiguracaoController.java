package com.rehabit.controller;

import com.rehabit.dto.ConfiguracaoDTO;
import com.rehabit.dto.TrocarSenhaDTO;
import com.rehabit.security.AuthContext;
import com.rehabit.service.ConfiguracaoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/configuracoes")
@CrossOrigin(origins = "*")
public class ConfiguracaoController {

    private final ConfiguracaoService configuracaoService;

    public ConfiguracaoController(ConfiguracaoService configuracaoService) {
        this.configuracaoService = configuracaoService;
    }

    /** Sempre do próprio usuário do token — não há id na URL. */
    @GetMapping
    public ResponseEntity<ConfiguracaoDTO> buscar(HttpServletRequest request) {
        return ResponseEntity.ok(configuracaoService.buscar(
                AuthContext.id(request), AuthContext.tipo(request)));
    }

    @PutMapping
    public ResponseEntity<ConfiguracaoDTO> salvar(@RequestBody ConfiguracaoDTO dados,
                                                    HttpServletRequest request) {
        return ResponseEntity.ok(configuracaoService.salvar(
                dados, AuthContext.id(request), AuthContext.tipo(request)));
    }

    @PutMapping("/senha")
    public ResponseEntity<Void> trocarSenha(@Valid @RequestBody TrocarSenhaDTO dados,
                                              HttpServletRequest request) {
        configuracaoService.trocarSenha(dados, AuthContext.id(request), AuthContext.tipo(request));
        return ResponseEntity.noContent().build();
    }
}
