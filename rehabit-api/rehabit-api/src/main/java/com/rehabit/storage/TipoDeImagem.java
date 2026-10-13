package com.rehabit.storage;

import java.util.Arrays;
import java.util.Optional;

/**
 * Os formatos de foto que a API aceita, reconhecidos pelos primeiros bytes do
 * arquivo — nunca pelo Content-Type nem pelo nome que o navegador manda, que
 * são do cliente e podem dizer qualquer coisa. Antes um HTML com
 * "Content-Type: image/png" e nome "x.html" era gravado como x.html e servido
 * pela própria API como página: XSS na origem dela.
 *
 * SVG fica de fora de propósito: é XML, pode carregar script e não é foto.
 */
public enum TipoDeImagem {

    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    GIF("gif", "image/gif"),
    WEBP("webp", "image/webp");

    private static final byte[] ASSINATURA_PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private final String extensao;
    private final String mime;

    TipoDeImagem(String extensao, String mime) {
        this.extensao = extensao;
        this.mime = mime;
    }

    public String extensao() {
        return extensao;
    }

    public String mime() {
        return mime;
    }

    /** O formato dos bytes, ou vazio se não for nenhum dos aceitos. */
    public static Optional<TipoDeImagem> detectar(byte[] conteudo) {
        if (conteudo == null) {
            return Optional.empty();
        }
        if (comecaCom(conteudo, 0, (byte) 0xFF, (byte) 0xD8, (byte) 0xFF)) {
            return Optional.of(JPEG);
        }
        if (comecaCom(conteudo, 0, ASSINATURA_PNG)) {
            return Optional.of(PNG);
        }
        if (comecaCom(conteudo, 0, "GIF87a".getBytes()) || comecaCom(conteudo, 0, "GIF89a".getBytes())) {
            return Optional.of(GIF);
        }
        if (comecaCom(conteudo, 0, "RIFF".getBytes()) && comecaCom(conteudo, 8, "WEBP".getBytes())) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    private static boolean comecaCom(byte[] conteudo, int posicao, byte... esperado) {
        return conteudo.length >= posicao + esperado.length
                && Arrays.equals(conteudo, posicao, posicao + esperado.length, esperado, 0, esperado.length);
    }
}
