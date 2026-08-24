package com.rehabit.storage;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    /**
     * Salva o arquivo e devolve a URL pra acessá-lo depois: relativa
     * (ex.: "/uploads/x.png") no modo local, absoluta (ex.:
     * "https://res.cloudinary.com/...") no modo nuvem.
     */
    String salvar(MultipartFile arquivo);
}
