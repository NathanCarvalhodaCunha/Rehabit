package com.rehabit.storage;

import com.rehabit.exception.AuthException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Guarda o arquivo no disco local. Usado como alternativa quando o
 * Cloudinary não está configurado (ver FileStorageConfig); em serviços
 * sem disco persistente (ex.: Render sem um "Disk" contratado) os
 * arquivos daqui são perdidos a cada novo deploy/restart.
 */
public class LocalFileStorageService implements FileStorageService {

    private static final Logger logger = LoggerFactory.getLogger(LocalFileStorageService.class);
    private static final String PREFIXO = "/uploads/";
    private static final Pattern URL_SALVA = Pattern.compile(
            "/uploads/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|gif|webp)");

    private final String uploadDir;

    public LocalFileStorageService(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    @Override
    public String salvar(byte[] conteudo, TipoDeImagem tipo) {
        String nomeArquivo = UUID.randomUUID() + "." + tipo.extensao();

        try {
            Path pasta = Path.of(uploadDir).toAbsolutePath();
            Files.createDirectories(pasta);
            Files.write(pasta.resolve(nomeArquivo), conteudo);
        } catch (IOException ex) {
            throw new AuthException("Falha ao salvar o arquivo.", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return PREFIXO + nomeArquivo;
    }

    @Override
    public boolean guardou(String url) {
        return url != null
                && URL_SALVA.matcher(url).matches()
                && Files.isRegularFile(Path.of(uploadDir).toAbsolutePath().resolve(url.substring(PREFIXO.length())));
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
