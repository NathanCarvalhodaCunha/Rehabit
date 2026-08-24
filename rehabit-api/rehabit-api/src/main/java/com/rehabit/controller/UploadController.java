package com.rehabit.controller;

import com.rehabit.exception.AuthException;
import com.rehabit.storage.FileStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/uploads")
@CrossOrigin(origins = "*")
public class UploadController {

    private final FileStorageService fileStorageService;

    public UploadController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> enviar(@RequestParam("arquivo") MultipartFile arquivo) {
        if (arquivo.isEmpty()) {
            throw new AuthException("Nenhum arquivo enviado.", HttpStatus.BAD_REQUEST);
        }

        String tipo = arquivo.getContentType();
        if (tipo == null || !tipo.startsWith("image/")) {
            throw new AuthException("Apenas imagens são permitidas.", HttpStatus.BAD_REQUEST);
        }

        String url = fileStorageService.salvar(arquivo);

        Map<String, String> corpo = new HashMap<>();
        corpo.put("url", url);
        return ResponseEntity.status(HttpStatus.CREATED).body(corpo);
    }
}
