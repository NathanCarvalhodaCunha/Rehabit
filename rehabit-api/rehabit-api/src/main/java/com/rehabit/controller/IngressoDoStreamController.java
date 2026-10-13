package com.rehabit.controller;

import com.rehabit.dto.IngressoDoStreamDTO;
import com.rehabit.security.AuthContext;
import com.rehabit.security.IngressoDoStream;
import com.rehabit.security.Limite;
import com.rehabit.security.LimiteDeTentativas;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Troca o token de sessão, que vem no cabeçalho, por um ingresso de uso
 * único para a URL do SSE do goniômetro (veja IngressoDoStream).
 *
 * Não confere a posse da clínica: o ingresso só abre o stream como este
 * mesmo usuário, e o stream confere a posse ao abrir, como sempre fez.
 */
@RestController
@RequestMapping("/api/goniometro/stream")
@CrossOrigin(origins = "*")
public class IngressoDoStreamController {

    private final IngressoDoStream ingressos;
    private final LimiteDeTentativas limites;

    public IngressoDoStreamController(IngressoDoStream ingressos, LimiteDeTentativas limites) {
        this.ingressos = ingressos;
        this.limites = limites;
    }

    @PostMapping("/ingresso")
    public ResponseEntity<IngressoDoStreamDTO> emitir(@RequestParam Integer idClinica, HttpServletRequest request) {
        Integer usuarioId = AuthContext.id(request);
        String usuarioTipo = AuthContext.tipo(request);
        limites.consumir(Limite.INGRESSO_POR_USUARIO, usuarioTipo + ":" + usuarioId);
        String ingresso = ingressos.emitir(usuarioId, usuarioTipo, idClinica);
        return ResponseEntity.ok(new IngressoDoStreamDTO(ingresso, IngressoDoStream.VALIDADE.toSeconds()));
    }
}
