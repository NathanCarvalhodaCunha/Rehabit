package com.rehabit.storage;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    /**
     * Salva o arquivo e devolve a URL pra acessá-lo depois: relativa
     * (ex.: "/uploads/x.png") no modo local, absoluta (ex.:
     * "https://res.cloudinary.com/...") no modo nuvem.
     */
    String salvar(MultipartFile arquivo);

    /**
     * Apaga um arquivo que {@link #salvar} guardou. Melhor esforço, e nunca
     * lança: um arquivo que já não existe, ou uma URL que não é deste
     * armazenamento, simplesmente não faz nada. Quem chama não deve depender
     * de ter dado certo.
     */
    void excluir(String url);
}
