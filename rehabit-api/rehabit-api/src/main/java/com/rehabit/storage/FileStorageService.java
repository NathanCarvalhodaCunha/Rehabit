package com.rehabit.storage;

public interface FileStorageService {

    /**
     * Salva uma imagem já conferida (veja EnvioDeFoto) e devolve a URL pra
     * acessá-la depois: relativa (ex.: "/uploads/x.png") no modo local,
     * absoluta (ex.: "https://res.cloudinary.com/...") no modo nuvem. A
     * extensão vem do tipo detectado nos bytes, nunca do nome do arquivo.
     */
    String salvar(byte[] conteudo, TipoDeImagem tipo);

    /**
     * Apaga um arquivo que {@link #salvar} guardou. Melhor esforço, e nunca
     * lança: um arquivo que já não existe, ou uma URL que não é deste
     * armazenamento, simplesmente não faz nada. Quem chama não deve depender
     * de ter dado certo.
     */
    void excluir(String url);

    /**
     * A URL tem o formato exato do que {@link #salvar} devolve hoje, e aponta
     * para este armazenamento? É o que o campo "foto" de clínica, profissional
     * e paciente aceita: antes ia qualquer texto para o banco e de lá para um
     * style="background-image:url(...)" nas telas.
     */
    boolean guardou(String url);
}
