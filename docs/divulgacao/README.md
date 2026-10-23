# Panfletos de divulgação

Dois panfletos A4 retrato, prontos para imprimir, com o QR code do site
(https://rehabit.com.br):

| Arquivo | O que tem |
| --- | --- |
| [`panfleto-rehabit-qrcode.pdf`](panfleto-rehabit-qrcode.pdf) | QR code e domínio, com o aviso para passar o panfleto à próxima pessoa depois de escanear. |
| [`panfleto-rehabit-qrcode-e-nfc.pdf`](panfleto-rehabit-qrcode-e-nfc.pdf) | QR code e domínio, com a alternativa do cartão por aproximação (NFC): encostado na parte de trás ou na frente do celular, ele abre o site. |

Os dois têm, no canto de cima, o selo "Aproveite o adesivo de brinde!".

**Para imprimir:** tamanho real (100%), sem "ajustar à página", para o QR
code sair na medida certa (6,4 cm no primeiro, 5,4 cm no segundo). Nada
encosta na borda da folha, então funciona em impressora comum.

## Para mudar os panfletos

O texto e o layout dos dois estão em
[`fonte/gerar-panfletos.js`](fonte/gerar-panfletos.js), que monta as páginas e
grava os PDFs nesta pasta:

```bash
node docs/divulgacao/fonte/gerar-panfletos.js
```

Precisa de Node 18+ e Playwright, como os testes (veja `tests/README.md`). Se
o conteúdo de um panfleto não couber na folha, o script avisa e não grava o
PDF.

- `fonte/qr.svg`: o QR code em vetor, que aponta para https://rehabit.com.br.
- `fonte/fontes/`: a Inter, a fonte do site, em TrueType (licença OFL em
  `OFL.txt`). Com ela o PDF sai com a fonte embutida no padrão que as gráficas
  aceitam.
- O logo vem de `Login/assets/rehabit-logo.png`.
