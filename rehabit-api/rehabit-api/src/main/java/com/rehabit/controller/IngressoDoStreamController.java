package com.rehabit.controller;

import com.rehabit.dto.IngressoDoStreamDTO;
import com.rehabit.security.AuthContext;
import com.rehabit.security.IngressoDoStream;
import com.rehabit.security.Limite;
import com.rehabit.security.LimiteDeTentativas;
import com.rehabit.service.GoniometroService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Troca o token de sessão, que vem no cabeçalho, por um ingresso de uso
 * único para a URL do SSE do goniômetro (veja IngressoDoStream).
 *
 * Confere a posse da clínica já aqui, e o stream confere de novo ao abrir: o
 * ingresso só abre o stream como este mesmo usuário, mas não há por que
 * emitir um para a clínica de outro.
 */
@RestController
@RequestMapping("/api/goniometro/stream")
@CrossOrigin(origins = "*")
public class IngressoDoStreamController {

    private final IngressoDoStream ingressos;
    private final LimiteDeTentativas limites;
    private final GoniometroService goniometroService;

    public IngressoDoStreamController(IngressoDoStream ingressos, LimiteDeTentativas limites,
                                      GoniometroService goniometroService) {
        this.ingressos = ingressos;
        this.limites = limites;
        this.goniometroService = goniometroService;
    }

    @PostMapping("/ingresso")
    public ResponseEntity<IngressoDoStreamDTO> emitir(@RequestParam Integer idClinica, HttpServletRequest request) {
        Integer usuarioId = AuthContext.id(request);
        String usuarioTipo = AuthContext.tipo(request);
        limites.consumir(Limite.INGRESSO_POR_USUARIO, usuarioTipo + ":" + usuarioId);
        goniometroService.exigirAcesso(idClinica, usuarioId, usuarioTipo);
        String ingresso = ingressos.emitir(usuarioId, usuarioTipo, idClinica);
        return ResponseEntity.ok(new IngressoDoStreamDTO(ingresso, IngressoDoStream.VALIDADE.toSeconds()));
    }
}
