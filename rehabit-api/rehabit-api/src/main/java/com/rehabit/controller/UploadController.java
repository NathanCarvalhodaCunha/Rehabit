package com.rehabit.controller;

import com.rehabit.storage.EnvioDeFoto;
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

    public UploadController(EnvioDeFoto envioDeFoto) {
        this.envioDeFoto = envioDeFoto;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> enviar(@RequestParam("arquivo") MultipartFile arquivo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("url", envioDeFoto.salvar(arquivo)));
    }
}
