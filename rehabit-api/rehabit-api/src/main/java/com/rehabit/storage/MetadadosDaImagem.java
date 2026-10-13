package com.rehabit.storage;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

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
 * GIF e WEBP passam como vieram: câmera de celular não gera nenhum dos dois.
 */
final class MetadadosDaImagem {

    private static final byte[] EXIF = "Exif\0\0".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] ICC = "ICC_PROFILE\0".getBytes(StandardCharsets.ISO_8859_1);

    private MetadadosDaImagem() {
    }

    /** @throws IllegalArgumentException se a estrutura do arquivo estiver quebrada. */
    static byte[] remover(byte[] conteudo, TipoDeImagem tipo) {
        return switch (tipo) {
            case JPEG -> limparJpeg(conteudo);
            case PNG -> limparPng(conteudo);
            default -> conteudo;
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

            if (manter(marcador, in, i + 2, fim)) {
                if (marcador == 0xE0 && mantidos.size() == 0 && jfif == null) {
                    // O APP0 (JFIF) tem de vir logo depois do SOI; a orientação entra depois dele.
                    comecaComJfif = true;
                    jfif = Arrays.copyOfRange(in, i - 2, fim);
                } else {
                    mantidos.write(in, i - 2, fim - (i - 2));
                }
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
     * JFIF, perfil de cor (ICC, no APP2) e Adobe (APP14, necessário para
     * decodificar CMYK) ficam; EXIF/XMP (APP1), IPTC (APP13), os demais APPn
     * — onde fabricantes guardam metadado próprio e imagens extras (MPF) —
     * e comentários (COM) saem. Tabelas e cabeçalhos da imagem (DQT, DHT,
     * SOF, DRI...) ficam todos.
     */
    private static boolean manter(int marcador, byte[] in, int inicioDosDados, int fim) {
        if (marcador == 0xFE) {
            return false;
        }
        if (marcador >= 0xE0 && marcador <= 0xEF) {
            return marcador == 0xE0
                    || marcador == 0xEE
                    || (marcador == 0xE2 && fim - inicioDosDados >= ICC.length && comecaCom(in, inicioDosDados, ICC));
        }
        return true;
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
            if (!tipo.equals("eXIf") && !tipo.equals("tEXt") && !tipo.equals("zTXt")
                    && !tipo.equals("iTXt") && !tipo.equals("tIME")) {
                saida.write(in, i, fim - i);
            }
            i = fim;
            if (tipo.equals("IEND")) {
                return saida.toByteArray();
            }
        }
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
