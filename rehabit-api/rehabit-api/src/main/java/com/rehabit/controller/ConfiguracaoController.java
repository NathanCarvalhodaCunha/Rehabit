package com.rehabit.controller;

import com.rehabit.dto.ConfiguracaoDTO;
import com.rehabit.dto.SenhaAlteradaDTO;
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

    /**
     * Janela que a agenda precisa respeitar de fato. Para um profissional, é o
     * horário da clínica apertado pelo dele; para a clínica, o seu próprio.
     * A tela usa isto para travar os campos e explicar o limite.
     */
    @GetMapping("/atendimento")
    public ResponseEntity<ConfiguracaoDTO> janelaDeAtendimento(HttpServletRequest request) {
        Integer usuarioId = AuthContext.id(request);
        String usuarioTipo = AuthContext.tipo(request);
        return ResponseEntity.ok("FISIOTERAPEUTA".equals(usuarioTipo)
                ? configuracaoService.janelaDeAtendimento(usuarioId)
                : configuracaoService.buscar(usuarioId, usuarioTipo));
    }

    @PutMapping
    public ResponseEntity<ConfiguracaoDTO> salvar(@RequestBody ConfiguracaoDTO dados,
                                                    HttpServletRequest request) {
        return ResponseEntity.ok(configuracaoService.salvar(
                dados, AuthContext.id(request), AuthContext.tipo(request)));
    }

    /**
     * A troca derruba os tokens emitidos antes dela, inclusive o desta
     * requisição: o site grava o token da resposta no lugar do antigo.
     */
    @PutMapping("/senha")
    public ResponseEntity<SenhaAlteradaDTO> trocarSenha(@Valid @RequestBody TrocarSenhaDTO dados,
                                                          HttpServletRequest request) {
        String tokenNovo = configuracaoService.trocarSenha(dados, AuthContext.id(request), AuthContext.tipo(request));
        return ResponseEntity.ok(new SenhaAlteradaDTO(
                "Senha alterada. As sessões abertas em outros aparelhos foram encerradas.", tokenNovo));
    }
}
