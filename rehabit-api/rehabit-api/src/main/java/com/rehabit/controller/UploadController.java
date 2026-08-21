package com.rehabit.controller;

import com.rehabit.exception.AuthException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/uploads")
@CrossOrigin(origins = "*")
public class UploadController {

    @Value("${app.upload-dir}")
    private String uploadDir;

    @PostMapping
    public ResponseEntity<Map<String, String>> enviar(@RequestParam("arquivo") MultipartFile arquivo) {
        if (arquivo.isEmpty()) {
            throw new AuthException("Nenhum arquivo enviado.", HttpStatus.BAD_REQUEST);
        }

        String tipo = arquivo.getContentType();
        if (tipo == null || !tipo.startsWith("image/")) {
            throw new AuthException("Apenas imagens são permitidas.", HttpStatus.BAD_REQUEST);
        }

        String extensao = "";
        String nomeOriginal = arquivo.getOriginalFilename();
        if (nomeOriginal != null && nomeOriginal.contains(".")) {
            extensao = nomeOriginal.substring(nomeOriginal.lastIndexOf('.'));
        }
        String nomeArquivo = UUID.randomUUID() + extensao;

        try {
            Path pasta = Path.of(uploadDir).toAbsolutePath();
            Files.createDirectories(pasta);
            Path destino = pasta.resolve(nomeArquivo).normalize();
            arquivo.transferTo(new File(destino.toString()));
        } catch (IOException ex) {
            throw new AuthException("Falha ao salvar o arquivo.", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        Map<String, String> corpo = new HashMap<>();
        corpo.put("url", "/uploads/" + nomeArquivo);
        return ResponseEntity.status(HttpStatus.CREATED).body(corpo);
    }
}
