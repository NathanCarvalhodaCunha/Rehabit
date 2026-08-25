package com.rehabit.storage;

import com.rehabit.exception.AuthException;
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

        return "/uploads/" + nomeArquivo;
    }
}
