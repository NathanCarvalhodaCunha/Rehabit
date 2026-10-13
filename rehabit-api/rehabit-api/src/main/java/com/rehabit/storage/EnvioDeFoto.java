package com.rehabit.storage;

import com.rehabit.exception.AuthException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Recebe a foto que o site manda (perfil, profissional, paciente, cadastro) e
 * a entrega ao armazenamento. O que vale é o conteúdo: o tipo sai dos
 * primeiros bytes, a extensão sai do tipo, e os metadados (GPS, câmera) saem
 * antes de gravar. Content-Type e nome do arquivo, que o cliente escolhe, não
 * decidem nada.
 */
@Component
public class EnvioDeFoto {

    private final FileStorageService armazenamento;
    private final DataSize limite;

    public EnvioDeFoto(FileStorageService armazenamento,
                       @Value("${spring.servlet.multipart.max-file-size:5MB}") DataSize limite) {
        this.armazenamento = armazenamento;
        this.limite = limite;
    }

    public String salvar(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new AuthException("Nenhum arquivo enviado.", HttpStatus.BAD_REQUEST);
        }
        // O Tomcat já barra acima do limite; isto vale para onde ele não está
        // na frente (testes, outro servidor) e cai no mesmo 413.
        if (arquivo.getSize() > limite.toBytes()) {
            throw new MaxUploadSizeExceededException(limite.toBytes());
        }

        byte[] conteudo;
        try {
            conteudo = arquivo.getBytes();
        } catch (IOException ex) {
            throw new AuthException("Não foi possível ler o arquivo enviado.", HttpStatus.BAD_REQUEST);
        }

        TipoDeImagem tipo = TipoDeImagem.detectar(conteudo)
                .orElseThrow(() -> new AuthException(
                        "Envie uma foto em JPG, PNG, GIF ou WEBP.", HttpStatus.BAD_REQUEST));
        byte[] limpo;
        try {
            limpo = MetadadosDaImagem.remover(conteudo, tipo);
        } catch (IllegalArgumentException ex) {
            throw new AuthException("A imagem parece estar corrompida. Tente outra foto.", HttpStatus.BAD_REQUEST);
        }
        return armazenamento.salvar(limpo, tipo);
    }
}
