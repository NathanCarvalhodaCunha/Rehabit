package com.rehabit;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/** Imagens de verdade para os testes de upload, montadas na hora. */
final class Imagens {

    /** Texto que vai dentro do EXIF de GPS, do comentário e do apêndice — nenhum pode sobrar no arquivo salvo. */
    static final String SEGREDO = "SEGREDO-DA-FOTO";

    private Imagens() {
    }

    static byte[] png(int largura, int altura) throws IOException {
        return codificar("png", largura, altura);
    }

    static byte[] jpeg(int largura, int altura) throws IOException {
        return codificar("jpg", largura, altura);
    }

    private static byte[] codificar(String formato, int largura, int altura) throws IOException {
        BufferedImage imagem = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < largura; x++) {
            for (int y = 0; y < altura; y++) {
                imagem.setRGB(x, y, (x * 40) << 16 | (y * 40) << 8 | 0x80);
            }
        }
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ImageIO.write(imagem, formato, saida);
        return saida.toByteArray();
    }

    /**
     * Um JPEG como o do celular: EXIF com orientação 6 (foto em pé) e um bloco
     * de GPS, um comentário e, depois do fim da imagem, um "apêndice" (como as
     * imagens extras que alguns aparelhos penduram ali). O SEGREDO aparece nos três.
     */
    static byte[] jpegDeCelular(int largura, int altura) throws IOException {
        byte[] base = jpeg(largura, altura);
        int fimDoApp0 = 4 + (((base[4] & 0xFF) << 8) | (base[5] & 0xFF));

        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        saida.write(base, 0, fimDoApp0);
        saida.write(segmento(0xE1, concat("Exif\0\0".getBytes(StandardCharsets.ISO_8859_1), tiffComOrientacaoEGps())));
        saida.write(segmento(0xFE, ("comentario " + SEGREDO).getBytes(StandardCharsets.ISO_8859_1)));
        saida.write(base, fimDoApp0, base.length - fimDoApp0);
        saida.write(("APENDICE " + SEGREDO).getBytes(StandardCharsets.ISO_8859_1));
        return saida.toByteArray();
    }

    /** TIFF little-endian: IFD0 com Orientation=6 e o ponteiro do GPS; o GPS com um texto. */
    private static byte[] tiffComOrientacaoEGps() {
        byte[] metodo = ("ASCII\0\0\0" + SEGREDO).getBytes(StandardCharsets.ISO_8859_1);
        ByteBuffer t = ByteBuffer.allocate(56 + metodo.length).order(ByteOrder.LITTLE_ENDIAN);
        t.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8);
        t.putShort((short) 2);                                                    // IFD0 em 8
        t.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) 6).putShort((short) 0);
        t.putShort((short) 0x8825).putShort((short) 4).putInt(1).putInt(38);
        t.putInt(0);
        t.putShort((short) 1);                                                    // IFD do GPS em 38
        t.putShort((short) 0x001B).putShort((short) 7).putInt(metodo.length).putInt(56);
        t.putInt(0);
        t.put(metodo);                                                            // dados em 56
        return t.array();
    }

    /** Um PNG com um pedaço tEXt carregando o SEGREDO. */
    static byte[] pngComTexto(int largura, int altura) throws IOException {
        byte[] base = png(largura, altura);
        int iend = base.length - 12;
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        saida.write(base, 0, iend);
        saida.write(pedacoPng("tEXt", ("Comment\0" + SEGREDO).getBytes(StandardCharsets.ISO_8859_1)));
        saida.write(base, iend, 12);
        return saida.toByteArray();
    }

    private static byte[] pedacoPng(String tipo, byte[] dados) {
        byte[] tipoBytes = tipo.getBytes(StandardCharsets.ISO_8859_1);
        CRC32 crc = new CRC32();
        crc.update(tipoBytes);
        crc.update(dados);
        return ByteBuffer.allocate(12 + dados.length)
                .putInt(dados.length).put(tipoBytes).put(dados).putInt((int) crc.getValue())
                .array();
    }

    private static byte[] segmento(int marcador, byte[] dados) {
        return ByteBuffer.allocate(4 + dados.length)
                .put((byte) 0xFF).put((byte) marcador).putShort((short) (dados.length + 2)).put(dados)
                .array();
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    static boolean contem(byte[] conteudo, String texto) {
        return new String(conteudo, StandardCharsets.ISO_8859_1).contains(texto);
    }
}
