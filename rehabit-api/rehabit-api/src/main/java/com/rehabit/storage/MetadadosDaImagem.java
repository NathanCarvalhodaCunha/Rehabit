package com.rehabit.storage;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;

/**
 * Tira da foto o que não é a imagem: EXIF (com a localização GPS de onde a
 * foto foi tirada, o modelo e o número de série da câmera e uma miniatura que
 * pode mostrar a foto antes do recorte), XMP, IPTC, comentários e as imagens
 * extras que alguns celulares penduram depois do fim do JPEG.
 *
 * Importa porque as fotos ficam num endereço público — e a do paciente,
 * tirada no consultório ou em casa, levava junto o endereço de onde foi tirada.
 *
 * Só a orientação sobrevive: sem ela a foto de celular em pé aparece deitada.
 * Nada é recomprimido — os bytes da imagem passam intactos.
 *
 * Nos quatro formatos a regra é a mesma: fica só o que se sabe que é imagem
 * (lista do que fica, não do que sai). Um bloco desconhecido — de fabricante,
 * de proveniência (C2PA), o EXIF de um WEBP, o comentário de um GIF — sai.
 */
final class MetadadosDaImagem {

    private static final byte[] EXIF = "Exif\0\0".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] ICC = "ICC_PROFILE\0".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] JFIF = "JFIF\0".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] ADOBE = "Adobe".getBytes(StandardCharsets.ISO_8859_1);

    /** Pedaços do PNG que descrevem a imagem; o resto (textos, EXIF, C2PA, privados) sai. */
    private static final Set<String> PEDACOS_PNG = Set.of(
            "IHDR", "PLTE", "IDAT", "IEND", "tRNS", "gAMA", "cHRM", "sRGB", "iCCP", "sBIT", "pHYs",
            "bKGD", "hIST", "acTL", "fcTL", "fdAT", "cICP", "mDCv", "cLLi");
    /** Pedaços do WEBP que descrevem a imagem; EXIF e XMP saem. */
    private static final Set<String> PEDACOS_WEBP = Set.of("VP8 ", "VP8L", "VP8X", "ALPH", "ANIM", "ANMF", "ICCP");

    private MetadadosDaImagem() {
    }

    /** @throws IllegalArgumentException se a estrutura do arquivo estiver quebrada. */
    static byte[] remover(byte[] conteudo, TipoDeImagem tipo) {
        return switch (tipo) {
            case JPEG -> limparJpeg(conteudo);
            case PNG -> limparPng(conteudo);
            case WEBP -> limparWebp(conteudo);
            case GIF -> limparGif(conteudo);
        };
    }

    // ------------------------------------------------------------------
    // JPEG: segmentos FF xx até o SOS; depois dele, dados da imagem até o EOI
    // ------------------------------------------------------------------

    private static byte[] limparJpeg(byte[] in) {
        ByteArrayOutputStream mantidos = new ByteArrayOutputStream(in.length);
        int orientacao = 1;
        boolean comecaComJfif = false;
        byte[] jfif = null;

        int i = 2;
        while (true) {
            int marcador = lerMarcador(in, i);
            i = posicaoDepoisDoMarcador(in, i);

            if (marcador == 0xD9) {                       // EOI: o resto é lixo ou imagem extra
                mantidos.write(0xFF);
                mantidos.write(0xD9);
                break;
            }
            if (marcador == 0x01 || (marcador >= 0xD0 && marcador <= 0xD7)) {
                mantidos.write(0xFF);
                mantidos.write(marcador);
                continue;
            }

            exigir(i + 2 <= in.length);
            int tamanho = ((in[i] & 0xFF) << 8) | (in[i + 1] & 0xFF);
            int fim = i + tamanho;
            exigir(tamanho >= 2 && fim <= in.length);

            if (marcador == 0xE1 && comecaCom(in, i + 2, EXIF)) {
                orientacao = orientacaoDoExif(in, i + 2 + EXIF.length, fim);
            }

            if (marcador == 0xDA) {                       // SOS: cabeçalho + dados até o próximo marcador
                int fimDosDados = fimDosDadosDaImagem(in, fim);
                mantidos.write(in, i - 2, fimDosDados - (i - 2));
                i = fimDosDados;
                if (i >= in.length) {
                    break;                                // arquivo truncado: fica o que tem
                }
                continue;
            }

            if (marcador == 0xE0) {
                // Só o cabeçalho JFIF, e sem a miniatura que ele pode carregar
                // (a foto antes do recorte). Tem de vir logo depois do SOI; a
                // orientação entra depois dele.
                if (jfif == null && mantidos.size() == 0 && fim - (i + 2) >= 14 && comecaCom(in, i + 2, JFIF)) {
                    comecaComJfif = true;
                    jfif = new byte[18];
                    jfif[0] = (byte) 0xFF;
                    jfif[1] = (byte) 0xE0;
                    jfif[3] = 16;
                    System.arraycopy(in, i + 2, jfif, 4, 12);   // JFIF\0, versão, unidade, densidades
                }
            } else if (marcador == 0xEE) {
                // Adobe: os 12 bytes que dizem como decodificar as cores (CMYK), nada além.
                if (fim - (i + 2) >= 12 && comecaCom(in, i + 2, ADOBE)) {
                    mantidos.write(new byte[] {(byte) 0xFF, (byte) 0xEE, 0, 14}, 0, 4);
                    mantidos.write(in, i + 2, 12);
                }
            } else if (manter(marcador, in, i + 2, fim)) {
                mantidos.write(in, i - 2, fim - (i - 2));
            }
            i = fim;
        }

        ByteArrayOutputStream saida = new ByteArrayOutputStream(in.length);
        saida.write(0xFF);
        saida.write(0xD8);
        if (comecaComJfif) {
            saida.writeBytes(jfif);
        }
        if (orientacao != 1) {
            saida.writeBytes(exifSoComOrientacao(orientacao));
        }
        saida.writeBytes(mantidos.toByteArray());
        return saida.toByteArray();
    }

    /**
     * Ficam as tabelas e cabeçalhos da imagem (SOFn, DHT, DAC, DQT, DNL, DRI,
     * DHP, EXP) e o perfil de cor (ICC, no APP2). JFIF e Adobe são tratados à
     * parte, reescritos no mínimo. Saem EXIF/XMP (APP1), IPTC (APP13), os
     * demais APPn — onde fabricantes guardam metadado próprio e imagens extras
     * (MPF) —, comentários (COM) e qualquer marcador reservado.
     */
    private static boolean manter(int marcador, byte[] in, int inicioDosDados, int fim) {
        if (marcador >= 0xC0 && marcador <= 0xCF) {
            return true;
        }
        if (marcador >= 0xDB && marcador <= 0xDF) {
            return true;
        }
        return marcador == 0xE2 && fim - inicioDosDados >= ICC.length && comecaCom(in, inicioDosDados, ICC);
    }

    private static int lerMarcador(byte[] in, int i) {
        exigir(i < in.length && (in[i] & 0xFF) == 0xFF);
        int j = i;
        while (j < in.length && (in[j] & 0xFF) == 0xFF) {
            j++;                                          // bytes de preenchimento
        }
        exigir(j < in.length);
        return in[j] & 0xFF;
    }

    private static int posicaoDepoisDoMarcador(byte[] in, int i) {
        int j = i;
        while ((in[j] & 0xFF) == 0xFF) {
            j++;
        }
        return j + 1;
    }

    /** Anda pelos dados comprimidos até o próximo marcador de verdade (FF seguido de algo que não é 00 nem RSTn). */
    private static int fimDosDadosDaImagem(byte[] in, int k) {
        while (k < in.length) {
            if ((in[k] & 0xFF) != 0xFF) {
                k++;
                continue;
            }
            if (k + 1 >= in.length) {
                return in.length;
            }
            int proximo = in[k + 1] & 0xFF;
            if (proximo == 0x00 || (proximo >= 0xD0 && proximo <= 0xD7) || proximo == 0xFF) {
                k += (proximo == 0xFF) ? 1 : 2;
                continue;
            }
            return k;
        }
        return in.length;
    }

    /** A orientação (tag 0x0112 do IFD0) do bloco TIFF do EXIF; 1 (normal) se não achar ou se estiver torto. */
    private static int orientacaoDoExif(byte[] in, int tiff, int fim) {
        try {
            boolean intel;
            if (in[tiff] == 'I' && in[tiff + 1] == 'I') {
                intel = true;
            } else if (in[tiff] == 'M' && in[tiff + 1] == 'M') {
                intel = false;
            } else {
                return 1;
            }
            long ifd0 = tiff + ler32(in, tiff + 4, intel);
            if (ifd0 + 2 > fim) {
                return 1;
            }
            int entradas = ler16(in, (int) ifd0, intel);
            for (int e = 0; e < entradas; e++) {
                int entrada = (int) ifd0 + 2 + e * 12;
                if (entrada + 12 > fim) {
                    return 1;
                }
                if (ler16(in, entrada, intel) == 0x0112 && ler16(in, entrada + 2, intel) == 3) {
                    int valor = ler16(in, entrada + 8, intel);
                    return (valor >= 1 && valor <= 8) ? valor : 1;
                }
            }
        } catch (ArrayIndexOutOfBoundsException ex) {
            return 1;
        }
        return 1;
    }

    /** Um APP1 EXIF mínimo (TIFF big-endian, IFD0 com uma entrada só): a orientação. */
    private static byte[] exifSoComOrientacao(int orientacao) {
        return new byte[] {
                (byte) 0xFF, (byte) 0xE1, 0x00, 0x22,
                'E', 'x', 'i', 'f', 0x00, 0x00,
                'M', 'M', 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08,
                0x00, 0x01,
                0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, (byte) orientacao, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00
        };
    }

    // ------------------------------------------------------------------
    // PNG: assinatura + pedaços (tamanho, tipo, dados, CRC) até o IEND
    // ------------------------------------------------------------------

    private static byte[] limparPng(byte[] in) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream(in.length);
        saida.write(in, 0, 8);
        int i = 8;
        while (true) {
            exigir(i + 8 <= in.length);
            long tamanho = ler32(in, i, false);
            exigir(tamanho >= 0 && i + 12 + tamanho <= in.length);
            String tipo = new String(in, i + 4, 4, StandardCharsets.ISO_8859_1);
            int fim = (int) (i + 12 + tamanho);
            if (PEDACOS_PNG.contains(tipo)) {
                saida.write(in, i, fim - i);
            }
            i = fim;
            if (tipo.equals("IEND")) {
                return saida.toByteArray();
            }
        }
    }

    // ------------------------------------------------------------------
    // WEBP: "RIFF", tamanho, "WEBP" e pedaços (tipo, tamanho, dados, byte de
    // alinhamento quando o tamanho é ímpar)
    // ------------------------------------------------------------------

    private static byte[] limparWebp(byte[] in) {
        exigir(in.length >= 12);
        ByteArrayOutputStream pedacos = new ByteArrayOutputStream(in.length);
        int i = 12;
        while (i + 8 <= in.length) {
            String tipo = new String(in, i, 4, StandardCharsets.ISO_8859_1);
            long tamanho = ler32(in, i + 4, true);
            exigir(i + 8 + tamanho <= in.length);
            int fim = (int) Math.min(i + 8 + tamanho + (tamanho & 1), in.length);
            if (PEDACOS_WEBP.contains(tipo)) {
                byte[] pedaco = Arrays.copyOfRange(in, i, fim);
                if (tipo.equals("VP8X") && tamanho >= 1) {
                    pedaco[8] &= (byte) ~0x0C;   // tira os avisos de "tem EXIF" e "tem XMP"
                }
                pedacos.writeBytes(pedaco);
            }
            i = fim;
        }
        byte[] corpo = pedacos.toByteArray();
        return ByteBuffer.allocate(12 + corpo.length).order(ByteOrder.LITTLE_ENDIAN)
                .put(in, 0, 4).putInt(4 + corpo.length).put(in, 8, 4).put(corpo)
                .array();
    }

    // ------------------------------------------------------------------
    // GIF: cabeçalho, tabela de cores, e blocos (extensões e imagens) até o fim
    // ------------------------------------------------------------------

    private static byte[] limparGif(byte[] in) {
        exigir(in.length >= 13);
        ByteArrayOutputStream saida = new ByteArrayOutputStream(in.length);
        int i = 13 + tamanhoDaTabelaDeCores(in[10]);
        exigir(i <= in.length);
        saida.write(in, 0, i);
        while (true) {
            exigir(i < in.length);
            int bloco = in[i] & 0xFF;
            if (bloco == 0x3B) {                          // fim: o que vier depois sai
                saida.write(0x3B);
                return saida.toByteArray();
            }
            int inicio = i;
            if (bloco == 0x21) {
                exigir(i + 2 <= in.length);
                int rotulo = in[i + 1] & 0xFF;
                int fim = depoisDosSubblocos(in, i + 2);
                // Ficam o controle de animação (F9) e a extensão de repetição
                // (NETSCAPE2.0/ANIMEXTS1.0); comentários (FE), texto (01) e as
                // demais extensões de aplicativo (XMP, por exemplo) saem.
                if (rotulo == 0xF9 || (rotulo == 0xFF && ehRepeticao(in, i + 2))) {
                    saida.write(in, inicio, fim - inicio);
                }
                i = fim;
            } else if (bloco == 0x2C) {
                exigir(i + 10 <= in.length);
                int j = i + 10 + tamanhoDaTabelaDeCores(in[i + 9]);
                exigir(j + 1 <= in.length);
                i = depoisDosSubblocos(in, j + 1);       // pula o tamanho mínimo do LZW
                saida.write(in, inicio, i - inicio);
            } else {
                throw new IllegalArgumentException("Estrutura de imagem inválida.");
            }
        }
    }

    private static int tamanhoDaTabelaDeCores(byte empacotado) {
        return (empacotado & 0x80) == 0 ? 0 : 3 * (1 << ((empacotado & 0x07) + 1));
    }

    private static int depoisDosSubblocos(byte[] in, int i) {
        while (true) {
            exigir(i < in.length);
            int tamanho = in[i] & 0xFF;
            i += 1 + tamanho;
            if (tamanho == 0) {
                return i;
            }
        }
    }

    private static boolean ehRepeticao(byte[] in, int i) {
        byte[] netscape = "NETSCAPE2.0".getBytes(StandardCharsets.ISO_8859_1);
        byte[] animexts = "ANIMEXTS1.0".getBytes(StandardCharsets.ISO_8859_1);
        return i < in.length && (in[i] & 0xFF) == 11
                && (comecaCom(in, i + 1, netscape) || comecaCom(in, i + 1, animexts));
    }

    // ------------------------------------------------------------------

    private static int ler16(byte[] in, int i, boolean intel) {
        int a = in[i] & 0xFF;
        int b = in[i + 1] & 0xFF;
        return intel ? (b << 8) | a : (a << 8) | b;
    }

    private static long ler32(byte[] in, int i, boolean intel) {
        long valor = 0;
        for (int k = 0; k < 4; k++) {
            int byteAtual = in[intel ? i + 3 - k : i + k] & 0xFF;
            valor = (valor << 8) | byteAtual;
        }
        return valor;
    }

    private static boolean comecaCom(byte[] in, int posicao, byte[] esperado) {
        return posicao >= 0 && in.length >= posicao + esperado.length
                && Arrays.equals(in, posicao, posicao + esperado.length, esperado, 0, esperado.length);
    }

    private static void exigir(boolean condicao) {
        if (!condicao) {
            throw new IllegalArgumentException("Estrutura de imagem inválida.");
        }
    }
}
