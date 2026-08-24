package com.rehabit.storage;

import com.rehabit.exception.AuthException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
@Profile("!cloud")
public class LocalFileStorageService implements FileStorageService {

    @Value("${app.upload-dir}")
    private String uploadDir;

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
