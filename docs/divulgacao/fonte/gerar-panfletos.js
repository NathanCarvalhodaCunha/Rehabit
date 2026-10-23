// Panfletos de divulgação do Rehabit (A4 retrato), gerados em PDF:
//  - panfleto-rehabit-qrcode.pdf: QR code e domínio, com o aviso para passar
//    o panfleto à próxima pessoa depois de escanear;
//  - panfleto-rehabit-qrcode-e-nfc.pdf: o mesmo panfleto, com a alternativa
//    do cartão por aproximação (NFC).
//
//   node docs/divulgacao/fonte/gerar-panfletos.js
//
// Precisa de Node 18+ e Playwright, como os testes em tests/. O QR code
// (qr.svg) aponta para https://rehabit.com.br. A fonte é a Inter do site em
// TrueType (fontes/, licença OFL): com ela o PDF sai com a fonte embutida no
// padrão que as gráficas aceitam (a Inter OTF vira Type 3 no PDF do Chromium).
"use strict";

const fs = require("fs");
const path = require("path");

const FONTE = __dirname;
const SAIDA = path.join(__dirname, "..");
const RAIZ_REPO = path.join(__dirname, "..", "..", "..");

// Menos que isso de folga e os blocos encostam um no outro na folha.
const SOBRA_MINIMA_MM = 8;

function carregarPlaywright() {
  const candidatos = [process.env.PLAYWRIGHT_MODULO, "playwright", "/opt/node-tools/node_modules/playwright"];
  for (const candidato of candidatos.filter(Boolean)) {
    try {
      return require(candidato);
    } catch (err) {
      /* tenta o próximo */
    }
  }
  throw new Error("Playwright não encontrado. Instale (npm i -D playwright) ou defina PLAYWRIGHT_MODULO.");
}

/** A página vai inteira no setContent: logo e fontes entram como data: URI. */
function dataUri(arquivo, tipo) {
  return `data:${tipo};base64,${fs.readFileSync(arquivo).toString("base64")}`;
}

const FONTES = [400, 500, 700, 800]
  .map((peso) => {
    const arquivo = path.join(FONTE, "fontes", `inter-latin-${peso}-normal.woff2`);
    return `@font-face { font-family: "Inter Panfleto"; font-weight: ${peso}; src: url("${dataUri(arquivo, "font/woff2")}") format("woff2"); }`;
  })
  .join("\n");

const LOGO = dataUri(path.join(RAIZ_REPO, "Login", "assets", "rehabit-logo.png"), "image/png");
const QR = fs.readFileSync(path.join(FONTE, "qr.svg"), "utf8");

const ESTILO = `
${FONTES}
@page { size: A4 portrait; margin: 0; }
:root {
  --azul: #1565D8;
  --tinta: #111827;
  --texto: #374151;
  --apoio: #4B5563;
  --painel: #EEF4FD;
  --borda: #D3E1F7;
}
* { box-sizing: border-box; }
html, body { margin: 0; padding: 0; background: #FFFFFF; }
body {
  font-family: "Inter Panfleto", sans-serif;
  color: var(--tinta);
  -webkit-print-color-adjust: exact;
  print-color-adjust: exact;
}
/* Nada encosta na borda: impressora comum não imprime até o fim da folha. */
.folha {
  width: 210mm;
  height: 297mm;
  padding: 16mm 15mm 12mm;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: space-between;
  overflow: hidden;
}
.topo { display: flex; flex-direction: column; align-items: center; text-align: center; }
.logo { width: 60mm; height: auto; display: block; }
h1 { margin: 8mm 0 0; font-size: 30pt; line-height: 1.12; font-weight: 800; letter-spacing: -0.02em; }
.sub { margin: 3.5mm 0 0; font-size: 14pt; line-height: 1.35; font-weight: 500; color: var(--texto); }
.meio { width: 100%; display: flex; flex-direction: column; align-items: center; gap: 6mm; }

.acesso {
  width: 100%;
  background: var(--painel);
  border: 0.4mm solid var(--borda);
  border-radius: 7mm;
  padding: 7mm 10mm 8mm;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}
.chamada { margin: 0; display: flex; align-items: center; gap: 2.5mm; font-size: 14pt; font-weight: 700; color: var(--azul); }
.chamada svg { width: 7mm; height: 7mm; flex: none; }
.qr { margin-top: 5mm; background: #FFFFFF; border-radius: 4mm; padding: var(--margem-qr); line-height: 0; }
.qr svg { width: var(--lado-qr); height: var(--lado-qr); display: block; }
.rotulo { margin: 5mm 0 0; font-size: 11pt; font-weight: 700; letter-spacing: 0.14em; text-transform: uppercase; color: var(--apoio); }
.dominio { margin: 1mm 0 0; font-size: 40pt; line-height: 1.1; font-weight: 800; letter-spacing: -0.015em; color: var(--azul); }
.apoio { margin: 3mm 0 0; font-size: 12pt; line-height: 1.4; color: var(--texto); }

.ou { width: 100%; display: flex; align-items: center; gap: 4mm; font-size: 12pt; font-weight: 700; color: var(--apoio); }
.ou::before, .ou::after { content: ""; flex: 1; height: 0.35mm; background: var(--borda); }

.aviso {
  width: 100%;
  border: 0.5mm solid var(--azul);
  border-radius: 7mm;
  padding: 5mm 8mm;
  display: flex;
  align-items: center;
  gap: 6mm;
}
.aviso svg { width: 36mm; height: auto; flex: none; }
.aviso-titulo { margin: 0; font-size: 15pt; line-height: 1.2; font-weight: 800; }
.aviso-texto { margin: 2mm 0 0; font-size: 12.5pt; line-height: 1.4; color: var(--texto); }
.aviso-texto strong { color: var(--tinta); }

h1, .sub, .apoio, .aviso-texto { text-wrap: balance; }
.rodape {
  width: 100%;
  border-top: 0.35mm solid var(--borda);
  padding-top: 4mm;
  text-align: center;
  font-size: 10pt;
  line-height: 1.4;
  color: var(--apoio);
}
`;

const CAMERA = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
  <path d="M4 8h3l2-3h6l2 3h3a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1z"/><circle cx="12" cy="13" r="3.5"/></svg>`;

// O panfleto, com o QR code, indo para a próxima pessoa.
const ILUSTRACAO_PASSAR = `<svg viewBox="0 0 150 100" aria-hidden="true">
  <g transform="rotate(-8 34 47)">
    <rect x="10" y="12" width="48" height="70" rx="6" fill="#FFFFFF" stroke="#111827" stroke-width="4"/>
    <g fill="#1565D8">
      <rect x="19" y="22" width="12" height="12" rx="1.5"/>
      <rect x="37" y="22" width="12" height="12" rx="1.5"/>
      <rect x="19" y="40" width="12" height="12" rx="1.5"/>
      <rect x="37" y="40" width="5" height="5"/>
      <rect x="44" y="47" width="5" height="5"/>
    </g>
    <line x1="19" y1="63" x2="49" y2="63" stroke="#111827" stroke-width="4" stroke-linecap="round"/>
    <line x1="19" y1="72" x2="38" y2="72" stroke="#111827" stroke-width="4" stroke-linecap="round"/>
  </g>
  <g fill="none" stroke="#1565D8" stroke-width="5" stroke-linecap="round" stroke-linejoin="round">
    <path d="M70 50H93"/>
    <path d="M85 41l9 9-9 9"/>
  </g>
  <circle cx="124" cy="34" r="12" fill="#FFFFFF" stroke="#111827" stroke-width="4"/>
  <path d="M102 86c0-16 10-27 22-27s22 11 22 27" fill="none" stroke="#111827" stroke-width="4" stroke-linecap="round"/>
</svg>`;

// Celular, ondas de aproximação e o cartão.
const ILUSTRACAO_NFC = `<svg viewBox="0 0 150 100" aria-hidden="true">
  <rect x="8" y="5" width="54" height="90" rx="11" fill="#FFFFFF" stroke="#111827" stroke-width="4"/>
  <line x1="27" y1="15" x2="43" y2="15" stroke="#111827" stroke-width="4" stroke-linecap="round"/>
  <line x1="28" y1="85" x2="42" y2="85" stroke="#111827" stroke-width="4" stroke-linecap="round"/>
  <g fill="none" stroke="#1565D8" stroke-width="4.5" stroke-linecap="round">
    <path d="M77.7 43.6A10 10 0 0 1 77.7 56.4"/>
    <path d="M83.8 38.4A18 18 0 0 1 83.8 61.6"/>
    <path d="M89.9 33.3A26 26 0 0 1 89.9 66.7"/>
  </g>
  <g transform="rotate(-12 121 50)">
    <rect x="98" y="34" width="46" height="31" rx="5" fill="#1565D8"/>
    <line x1="105" y1="55" x2="125" y2="55" stroke="#FFFFFF" stroke-width="3.2" stroke-linecap="round"/>
    <line x1="105" y1="44" x2="114" y2="44" stroke="#FFFFFF" stroke-width="3.2" stroke-linecap="round"/>
  </g>
</svg>`;

const AVISOS = {
  passar: `
    <section class="aviso">
      ${ILUSTRACAO_PASSAR}
      <div>
        <p class="aviso-titulo">Já escaneou o código?</p>
        <p class="aviso-texto">Passe este panfleto para a <strong>próxima pessoa</strong>.</p>
      </div>
    </section>`,
  nfc: `
    <div class="ou">ou</div>
    <section class="aviso">
      ${ILUSTRACAO_NFC}
      <div>
        <p class="aviso-titulo">Celular com aproximação (NFC)?</p>
        <p class="aviso-texto">Se a aproximação estiver ativada, encoste o <strong>nosso cartão</strong>
          na parte de trás ou na frente do celular para abrir o site.</p>
      </div>
    </section>`,
};

// O QR do panfleto com NFC é menor para o "ou" e o aviso do cartão caberem.
const PANFLETOS = [
  { arquivo: "panfleto-rehabit-qrcode.pdf", titulo: "Panfleto Rehabit", aviso: "passar", ladoQrMm: 64 },
  { arquivo: "panfleto-rehabit-qrcode-e-nfc.pdf", titulo: "Panfleto Rehabit — com aproximação (NFC)", aviso: "nfc", ladoQrMm: 54 },
];

function pagina({ titulo, aviso, ladoQrMm }) {
  // 2,5 módulos de branco em volta do QR (25 módulos de lado), dentro do painel claro.
  const margemQrMm = (ladoQrMm * 2.5) / 25;
  return `<!doctype html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<title>${titulo}</title>
<style>${ESTILO}
:root { --lado-qr: ${ladoQrMm}mm; --margem-qr: ${margemQrMm}mm; }
</style>
</head>
<body>
<main class="folha">
  <header class="topo">
    <img class="logo" src="${LOGO}" alt="Rehabit">
    <h1>A evolução do paciente,<br>medida em graus.</h1>
    <p class="sub">Goniômetro digital e sistema web para clínicas de fisioterapia.</p>
  </header>

  <div class="meio">
    <section class="acesso">
      <p class="chamada">${CAMERA}Aponte a câmera do celular para o código</p>
      <div class="qr">${QR}</div>
      <p class="rotulo">Acesse o site</p>
      <p class="dominio">rehabit.com.br</p>
      <p class="apoio">Conheça o projeto, veja o goniômetro em ação e entre na conta de demonstração.</p>
    </section>
    ${AVISOS[aviso]}
  </div>

  <footer class="rodape">Trabalho de Conclusão de Curso · Etec Prof. Carmine Biagio Tundisi · Atibaia (SP) · 2026</footer>
</main>
</body>
</html>`;
}

async function principal() {
  const { chromium } = carregarPlaywright();
  const navegador = await chromium.launch();
  try {
    const aba = await navegador.newPage();
    for (const panfleto of PANFLETOS) {
      await aba.setContent(pagina(panfleto), { waitUntil: "load" });
      await aba.evaluate(() => document.fonts.ready);
      const sobraMm = await aba.evaluate(() => {
        const folha = document.querySelector(".folha");
        const estilo = getComputedStyle(folha);
        const util = folha.clientHeight - parseFloat(estilo.paddingTop) - parseFloat(estilo.paddingBottom);
        const ocupado = [...folha.children].reduce((soma, el) => soma + el.getBoundingClientRect().height, 0);
        return Math.round(((util - ocupado) * 25.4) / 96);
      });
      if (sobraMm < SOBRA_MINIMA_MM) {
        throw new Error(`${panfleto.arquivo}: o conteúdo não cabe na folha (sobram ${sobraMm} mm; o mínimo é ${SOBRA_MINIMA_MM})`);
      }
      await aba.pdf({ path: path.join(SAIDA, panfleto.arquivo), format: "A4", printBackground: true, preferCSSPageSize: true });
      console.log(`ok   ${panfleto.arquivo} (sobram ${sobraMm} mm na folha)`);
    }
  } finally {
    await navegador.close();
  }
}

principal().catch((err) => {
  console.error(err.message);
  process.exit(1);
});
