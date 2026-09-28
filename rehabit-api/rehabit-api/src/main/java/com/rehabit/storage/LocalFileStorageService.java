package com.rehabit.storage;

import com.rehabit.exception.AuthException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Guarda o arquivo no disco local. Usado como alternativa quando o
 * Cloudinary não está configurado (ver FileStorageConfig); em serviços
 * sem disco persistente (ex.: Render sem um "Disk" contratado) os
 * arquivos daqui são perdidos a cada novo deploy/restart.
 */
public class LocalFileStorageService implements FileStorageService {

    private static final Logger logger = LoggerFactory.getLogger(LocalFileStorageService.class);
    private static final String PREFIXO = "/uploads/";

    private final String uploadDir;

    public LocalFileStorageService(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    @Override
    public String salvar(MultipartFile arquivo) {
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

        return PREFIXO + nomeArquivo;
    }

    @Override
    public void excluir(String url) {
        if (url == null || !url.startsWith(PREFIXO)) {
            return;
        }
        Path pasta = Path.of(uploadDir).toAbsolutePath().normalize();
        Path alvo = pasta.resolve(url.substring(PREFIXO.length())).normalize();

        // salvar() só grava direto na pasta. Qualquer outra coisa é uma URL
        // forjada ("/uploads/../algo") tentando apagar fora dela.
        if (!pasta.equals(alvo.getParent())) {
            logger.warn("Recusei apagar um arquivo fora da pasta de uploads: {}", url);
            return;
        }
        try {
            Files.deleteIfExists(alvo);
        } catch (IOException ex) {
            logger.warn("Falha ao apagar o arquivo {}", alvo, ex);
        }
    }
}
