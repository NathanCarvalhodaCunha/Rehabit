package com.rehabit.controller;

import com.rehabit.security.AuthContext;
import com.rehabit.security.Limite;
import com.rehabit.security.LimiteDeTentativas;
import com.rehabit.storage.EnvioDeFoto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Foto de perfil, de profissional e de paciente, enviada por quem está
 * logado. Já foi pública: qualquer um na internet subia arquivos sem limite
 * para o armazenamento da clínica. Agora passa pelo JwtAuthenticationFilter
 * como qualquer rota; a foto do cadastro, de quem ainda não tem conta, vai
 * por /api/auth/cadastro/foto, que exige o e-mail confirmado.
 */
@RestController
@RequestMapping("/api/uploads")
@CrossOrigin(origins = "*")
public class UploadController {

    private final EnvioDeFoto envioDeFoto;
    private final LimiteDeTentativas limites;

    public UploadController(EnvioDeFoto envioDeFoto, LimiteDeTentativas limites) {
        this.envioDeFoto = envioDeFoto;
        this.limites = limites;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> enviar(@RequestParam("arquivo") MultipartFile arquivo,
                                                      HttpServletRequest request) {
        limites.consumir(Limite.UPLOAD_POR_CONTA, AuthContext.tipo(request) + ":" + AuthContext.id(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("url", envioDeFoto.salvar(arquivo)));
    }
}
