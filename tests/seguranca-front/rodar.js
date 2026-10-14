// Testes de segurança do front (SEC-02, SEC-03, SEC-09, SEC-23, SEC-24,
// SEC-29, SEC-30). Ver tests/README.md para como rodar.
//
// Grupos:
//  estatico  - SRI e versão fixa nas tags de CDN, nada de script inline sem
//              hash na CSP, nada de on*= no HTML, cabeçalhos do vercel.json;
//  xss       - cada tela recebe da API simulada textos e fotos hostis, com a
//              CSP DESLIGADA: o que se prova aqui é o escape, não a CSP;
//  ics       - a exportação .ics com CR/LF, ; , \ e linha longa;
//  csp       - todas as páginas com os cabeçalhos do vercel.json e dados
//              normais: nenhuma violação de CSP, de SRI ou de
//              Permissions-Policy, nenhum erro de JavaScript, e as funções
//              que dependem do que a CSP restringe continuam funcionando.
"use strict";

const fs = require("fs");
const path = require("path");
const crypto = require("crypto");
const { iniciarServidor, cabecalhosPara, lerRegras } = require("./servidor");
const cdn = require("./cdn");
const api = require("./api-simulada");

const RAIZ_REPO = path.join(__dirname, "..", "..");

function argumento(nome, padrao) {
  const i = process.argv.indexOf(nome);
  return i > -1 && process.argv[i + 1] ? process.argv[i + 1] : padrao;
}
const RAIZ = path.resolve(argumento("--raiz", RAIZ_REPO));
const GRUPOS = argumento("--somente", "estatico,xss,ics,csp,ids").split(",");

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

/* ---------------------------------------------------------------------- */
/* Relatório                                                              */
/* ---------------------------------------------------------------------- */

const resultados = [];
function registrar(grupo, nome, problemas) {
  const ok = problemas.length === 0;
  resultados.push({ grupo, nome, ok, problemas });
  console.log(`${ok ? "ok   " : "FALHA"} [${grupo}] ${nome}`);
  problemas.slice(0, 10).forEach((p) => console.log(`        - ${String(p).slice(0, 300)}`));
  if (problemas.length > 10) console.log(`        ... e mais ${problemas.length - 10}`);
}

/* ---------------------------------------------------------------------- */
/* Leitura do HTML                                                        */
/* ---------------------------------------------------------------------- */

/** "../vendor/npm/pacote@1.2.3/arquivo.js" -> a URL equivalente no jsDelivr. */
function urlEquivalenteNoCdn(url) {
  return "https://cdn.jsdelivr.net/npm/" + url.slice(url.indexOf("vendor/npm/") + "vendor/npm/".length);
}

function paginasDoSite() {
  const lista = ["index.html"];
  ["Login", "Software"].forEach((pasta) => {
    fs.readdirSync(path.join(RAIZ, pasta))
      .filter((f) => f.endsWith(".html"))
      .sort()
      .forEach((f) => lista.push(`${pasta}/${f}`));
  });
  return lista;
}

function atributos(texto) {
  const saida = {};
  const re = /([^\s=/>]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+)))?/g;
  let m;
  while ((m = re.exec(texto))) saida[m[1].toLowerCase()] = m[2] ?? m[3] ?? m[4] ?? "";
  return saida;
}

function lerTags(html) {
  const semComentarios = html.replace(/<!--[\s\S]*?-->/g, "");
  const scripts = [];
  const reScript = /<script\b([^>]*)>([\s\S]*?)<\/script>/gi;
  let m;
  while ((m = reScript.exec(semComentarios))) scripts.push({ attrs: atributos(m[1]), corpo: m[2] });
  const links = [];
  const reLink = /<link\b([^>]*)>/gi;
  while ((m = reLink.exec(semComentarios))) links.push(atributos(m[1]));
  const tags = [];
  const reTag = /<([a-z][a-z0-9-]*)\b([^>]*)>/gi;
  while ((m = reTag.exec(semComentarios))) tags.push({ nome: m[1], attrs: atributos(m[2]) });
  return { scripts, links, tags };
}

/** Diretivas da CSP: { "script-src": ["'self'", ...], ... } */
function lerCsp(valor) {
  const saida = {};
  String(valor || "")
    .split(";")
    .map((d) => d.trim())
    .filter(Boolean)
    .forEach((d) => {
      const [nome, ...fontes] = d.split(/\s+/);
      saida[nome.toLowerCase()] = fontes;
    });
  return saida;
}

/* ---------------------------------------------------------------------- */
/* Grupo: estático                                                        */
/* ---------------------------------------------------------------------- */

async function grupoEstatico() {
  const regras = lerRegras(RAIZ);
  const paginas = paginasDoSite();
  const problemasSri = [];
  const problemasInline = [];
  const urlsCdn = new Map(); // url -> Set(integrity)

  for (const pagina of paginas) {
    const html = fs.readFileSync(path.join(RAIZ, pagina), "utf8");
    const { scripts, links, tags } = lerTags(html);
    const csp = lerCsp(cabecalhosPara(regras, `/${pagina}`)["Content-Security-Policy"]);
    const scriptSrc = csp["script-src"] || csp["default-src"] || [];

    const externos = [
      ...scripts.filter((s) => s.attrs.src).map((s) => ({ url: s.attrs.src, attrs: s.attrs, tipo: "script" })),
      ...links
        .filter((l) => /stylesheet/i.test(l.rel || "") && l.href)
        .map((l) => ({ url: l.href, attrs: l, tipo: "folha de estilo" })),
    ].filter((e) => /^(https?:)?\/\//.test(e.url));

    // Script e CSS de terceiros: só o CSS do Google Fonts, que muda conforme
    // o navegador que pede (não tem hash fixo; fica contido pela CSP). As
    // bibliotecas são servidas pelo próprio site, em vendor/npm/, com o
    // caminho do jsDelivr: a CSP não confia em script de nenhum CDN.
    externos.forEach((e) => {
      const host = new URL(e.url, "https://x").hostname;
      if (host === "fonts.googleapis.com" && e.tipo === "folha de estilo") return;
      problemasSri.push(`${pagina}: ${e.tipo} de origem inesperada ${e.url}`);
    });

    const vendorizados = [
      ...scripts.filter((s) => s.attrs.src).map((s) => ({ url: s.attrs.src, attrs: s.attrs, tipo: "script" })),
      ...links
        .filter((l) => /stylesheet/i.test(l.rel || "") && l.href)
        .map((l) => ({ url: l.href, attrs: l, tipo: "folha de estilo" })),
    ].filter((e) => /(^|\/)vendor\/npm\//.test(e.url) && !/^(https?:)?\/\//.test(e.url));

    vendorizados.forEach((e) => {
      const url = urlEquivalenteNoCdn(e.url);
      const partes = cdn.partesDaUrl(url);
      if (!partes || !partes.versao || !/^\d+\.\d+\.\d+$/.test(partes.versao) || !partes.caminho) {
        problemasSri.push(`${pagina}: versão/arquivo não fixados em ${e.url}`);
      }
      if (!/^sha384-/.test(e.attrs.integrity || "")) problemasSri.push(`${pagina}: sem integrity sha384 em ${e.url}`);
      if (!urlsCdn.has(url)) urlsCdn.set(url, new Set());
      urlsCdn.get(url).add(e.attrs.integrity || "(nenhum)");
    });

    scripts
      .filter((s) => !s.attrs.src)
      .filter((s) => !s.attrs.type || /^(text\/javascript|module)$/i.test(s.attrs.type))
      .forEach((s) => {
        const h = `'sha256-${crypto.createHash("sha256").update(s.corpo, "utf8").digest("base64")}'`;
        if (!scriptSrc.includes(h)) problemasInline.push(`${pagina}: <script> inline sem o hash ${h} na CSP`);
      });
    tags.forEach((t) => {
      Object.keys(t.attrs)
        .filter((a) => /^on[a-z]+$/.test(a))
        .forEach((a) => problemasInline.push(`${pagina}: handler inline ${a}="${t.attrs[a]}" em <${t.nome}>`));
      ["href", "src", "action", "formaction"].forEach((a) => {
        if (/^\s*javascript:/i.test(t.attrs[a] || "")) problemasInline.push(`${pagina}: URL javascript: em <${t.nome} ${a}>`);
      });
    });
  }

  // O jsPDF entra por script criado em relatorio.js, fora do HTML.
  const relatorio = fs.readFileSync(path.join(RAIZ, "Software/pages/relatorio.js"), "utf8");
  const urlJspdf = (/URL_JSPDF\s*=\s*"([^"]+)"/.exec(relatorio) || [])[1];
  const integridadeJspdf = (/INTEGRIDADE_JSPDF\s*=\s*"([^"]+)"/.exec(relatorio) || [])[1];
  if (!urlJspdf) problemasSri.push("relatorio.js: URL_JSPDF não encontrada");
  else {
    if (!integridadeJspdf) problemasSri.push("relatorio.js: o jsPDF é injetado sem integrity (INTEGRIDADE_JSPDF)");
    if (!/script\.integrity\s*=\s*INTEGRIDADE_JSPDF/.test(relatorio)) problemasSri.push("relatorio.js: script.integrity não é definido");
    if (!/vendor\/npm\//.test(urlJspdf)) problemasSri.push(`relatorio.js: o jsPDF vem de fora do site (${urlJspdf})`);
    const urlJspdfNoCdn = urlEquivalenteNoCdn(urlJspdf);
    if (!urlsCdn.has(urlJspdfNoCdn)) urlsCdn.set(urlJspdfNoCdn, new Set());
    urlsCdn.get(urlJspdfNoCdn).add(integridadeJspdf || "(nenhum)");
  }
  registrar("estatico", "bibliotecas servidas pelo site, com versão fixa e SRI; nada de CDN (SEC-23)", problemasSri);

  // O arquivo em vendor/ tem de ser, byte a byte, o do pacote publicado no
  // npm, e o integrity declarado tem de ser o hash dele.
  const problemasHash = [];
  for (const [url, integridades] of urlsCdn) {
    const partes = cdn.partesDaUrl(url);
    if (!partes || !partes.versao || !partes.caminho) continue; // já acusado acima
    let arquivo;
    try {
      arquivo = await cdn.arquivoExato(partes.pacote, partes.versao, partes.caminho);
    } catch (err) {
      problemasHash.push(`${url}: não consegui baixar do registro do npm (${err.message})`);
      continue;
    }
    if (!arquivo) {
      problemasHash.push(`${url}: o arquivo não existe no pacote (o jsDelivr geraria um na hora, sem hash fixo)`);
      continue;
    }
    const esperado = cdn.hash("sha384", arquivo);
    integridades.forEach((i) => {
      if (i !== esperado) problemasHash.push(`${url}: integrity ${i} != ${esperado}`);
    });
    const local = path.join(RAIZ, "vendor", "npm", url.slice("https://cdn.jsdelivr.net/npm/".length));
    if (!fs.existsSync(local)) problemasHash.push(`${url}: falta ${path.relative(RAIZ, local)}`);
    else if (!fs.readFileSync(local).equals(arquivo)) problemasHash.push(`${path.relative(RAIZ, local)}: difere do pacote do npm`);
  }
  registrar("estatico", "vendor/ é o arquivo publicado no npm, e o integrity confere", problemasHash);

  registrar("estatico", "sem script inline fora da CSP, sem on*= e sem javascript: no HTML (SEC-09)", problemasInline);

  const problemasCabecalhos = [];
  ["/", "/index.html", "/Login/login.html", "/Software/instituicao.html"].forEach((caminho) => {
    const h = cabecalhosPara(regras, caminho);
    const csp = lerCsp(h["Content-Security-Policy"]);
    if (!h["Content-Security-Policy"]) {
      problemasCabecalhos.push(`${caminho}: sem Content-Security-Policy`);
    } else {
      const scriptSrc = csp["script-src"] || [];
      if (!csp["default-src"]) problemasCabecalhos.push(`${caminho}: CSP sem default-src`);
      if (!scriptSrc.length) problemasCabecalhos.push(`${caminho}: CSP sem script-src`);
      ["'unsafe-inline'", "'unsafe-eval'", "*", "https:", "data:"].forEach((f) => {
        if (scriptSrc.includes(f)) problemasCabecalhos.push(`${caminho}: script-src permite ${f}`);
      });
      if ((csp["object-src"] || []).join(" ") !== "'none'") problemasCabecalhos.push(`${caminho}: object-src não é 'none'`);
      if ((csp["frame-ancestors"] || []).join(" ") !== "'none'") problemasCabecalhos.push(`${caminho}: frame-ancestors não é 'none'`);
      if (!csp["base-uri"]) problemasCabecalhos.push(`${caminho}: CSP sem base-uri`);
      // Fotos do Cloudinary só da conta do Rehabit: com o host inteiro, uma
      // injeção de HTML mandaria dados para a conta de outra pessoa.
      (csp["img-src"] || []).filter((f) => /cloudinary\.com/i.test(f)).forEach((f) => {
        if (!/^https:\/\/res\.cloudinary\.com\/[a-z0-9_-]+\/$/i.test(f)) {
          problemasCabecalhos.push(`${caminho}: img-src aceita o Cloudinary fora da conta do Rehabit (${f})`);
        }
      });
      if (!csp["form-action"]) problemasCabecalhos.push(`${caminho}: CSP sem form-action`);
    }
    const hsts = h["Strict-Transport-Security"] || "";
    const maxAge = Number((/max-age=(\d+)/.exec(hsts) || [])[1] || 0);
    if (maxAge < 63072000) problemasCabecalhos.push(`${caminho}: HSTS ausente ou com max-age menor que 2 anos ("${hsts}")`);
    // Sem includeSubDomains de propósito: ele obrigaria HTTPS em todo
    // subdomínio do domínio do site, e não há como garantir isso daqui.
    if (/preload/i.test(hsts)) problemasCabecalhos.push(`${caminho}: HSTS com preload (não pedido)`);
    const pp = h["Permissions-Policy"] || "";
    ["camera=()", "microphone=()", "geolocation=()", "payment=()", "usb=()"].forEach((f) => {
      if (!pp.includes(f)) problemasCabecalhos.push(`${caminho}: Permissions-Policy sem ${f}`);
    });
    if (h["X-Content-Type-Options"] !== "nosniff") problemasCabecalhos.push(`${caminho}: sem X-Content-Type-Options`);
  });
  registrar("estatico", "cabeçalhos CSP, HSTS e Permissions-Policy no vercel.json (SEC-09)", problemasCabecalhos);
}

/* ---------------------------------------------------------------------- */
/* Navegador: roteamento e coleta                                         */
/* ---------------------------------------------------------------------- */

const PNG_1PX = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=",
  "base64"
);

// O script do Turnstile é da Cloudflare; aqui um dublê que se comporta como
// ele no que o turnstile.js usa, e que abre um iframe na mesma origem do real
// (assim o frame-src da CSP também é exercitado).
const TURNSTILE_SIMULADO = `(function () {
  var n = 0;
  function emitir(opcoes, id) { setTimeout(function () { opcoes.callback && opcoes.callback("token-simulado-" + id); }, 30); }
  var widgets = {};
  window.turnstile = {
    render: function (alvo, opcoes) {
      var el = typeof alvo === "string" ? document.querySelector(alvo) : alvo;
      var quadro = document.createElement("iframe");
      quadro.src = "https://challenges.cloudflare.com/cdn-cgi/challenge-platform/simulado";
      quadro.title = "Turnstile simulado";
      quadro.width = "300";
      quadro.height = "65";
      el.appendChild(quadro);
      var id = "w" + (++n);
      widgets[id] = opcoes;
      emitir(opcoes, id);
      return id;
    },
    reset: function (id) { if (widgets[id]) emitir(widgets[id], id); },
    remove: function () {},
    getResponse: function () { return ""; }
  };
  var m = /[?&]onload=([\\w$]+)/.exec(document.currentScript ? document.currentScript.src : "");
  if (m && typeof window[m[1]] === "function") window[m[1]]();
})();`;

const CSS_FONTES = `@font-face { font-family: "Inter"; src: url(https://fonts.gstatic.com/s/inter/v1/simulado.woff2) format("woff2"); }`;

function novoRegistro() {
  return { csp: [], sri: [], permissoes: [], erros: [], externas: [], api: [] };
}

async function rotear(rota, servidor, responder, registro) {
  const req = rota.request();
  const url = new URL(req.url());
  if (url.origin === servidor.origem) return rota.continue();

  const cors = { "access-control-allow-origin": "*" };
  if (url.origin === api.ORIGEM_API) {
    if (req.method() === "OPTIONS") {
      return rota.fulfill({
        status: 204,
        headers: Object.assign({}, cors, {
          "access-control-allow-methods": "GET, POST, PUT, DELETE, OPTIONS",
          "access-control-allow-headers": "authorization, content-type, x-turnstile-token",
        }),
      });
    }
    if (url.pathname.startsWith("/uploads/")) {
      return rota.fulfill({ status: 200, body: PNG_1PX, headers: Object.assign({}, cors, { "content-type": "image/png" }) });
    }
    if (url.pathname.startsWith("/api/goniometro/stream")) {
      return rota.fulfill({
        status: 200,
        headers: Object.assign({}, cors, { "content-type": "text/event-stream" }),
        body: 'retry: 600000\nevent: estado\ndata: {"conectado":false}\n\n',
      });
    }
    registro.api.push(`${req.method()} ${url.pathname}${url.search}`);
    const r = responder(req.method(), url.pathname, url.search.slice(1));
    return rota.fulfill({
      status: r.status,
      headers: Object.assign({}, cors, { "content-type": "application/json" }),
      body: r.corpo == null ? "" : JSON.stringify(r.corpo),
    });
  }
  if (url.hostname === "cdn.jsdelivr.net") {
    const corpo = await cdn.conteudoParaUrl(req.url()).catch(() => null);
    if (!corpo) return rota.fulfill({ status: 404, headers: cors, body: "" });
    const tipo = /\.css(\?|$)/.test(url.pathname) ? "text/css" : "application/javascript";
    return rota.fulfill({ status: 200, body: corpo, headers: Object.assign({}, cors, { "content-type": tipo }) });
  }
  if (url.hostname === "challenges.cloudflare.com") {
    if (url.pathname.startsWith("/turnstile/")) {
      return rota.fulfill({ status: 200, body: TURNSTILE_SIMULADO, headers: { "content-type": "application/javascript" } });
    }
    return rota.fulfill({ status: 200, body: "<!doctype html><title>Turnstile</title><p>ok</p>", headers: { "content-type": "text/html" } });
  }
  if (url.hostname === "fonts.googleapis.com") {
    return rota.fulfill({ status: 200, body: CSS_FONTES, headers: Object.assign({}, cors, { "content-type": "text/css" }) });
  }
  if (url.hostname === "fonts.gstatic.com") {
    return rota.fulfill({ status: 404, headers: cors, body: "" });
  }
  if (url.hostname === "res.cloudinary.com") {
    return rota.fulfill({ status: 200, body: PNG_1PX, headers: Object.assign({}, cors, { "content-type": "image/png" }) });
  }
  registro.externas.push(req.url());
  return rota.abort("blockedbyclient");
}

async function abrir(navegador, servidor, { cspAtiva, sessao = null, responder }) {
  const registro = novoRegistro();
  const contexto = await navegador.newContext({ bypassCSP: !cspAtiva, acceptDownloads: true, viewport: { width: 1280, height: 900 } });
  await contexto.addInitScript((sessaoInicial) => {
    window.__xss = [];
    window.__csp = [];
    document.addEventListener("securitypolicyviolation", (e) => {
      window.__csp.push(`${e.violatedDirective} bloqueou ${e.blockedURI || "inline"} (${e.sourceFile || "?"}:${e.lineNumber})`);
    });
    if (sessaoInicial && location.protocol === "http:") {
      try {
        localStorage.setItem("rehabit_usuario", JSON.stringify(sessaoInicial));
      } catch (err) {
        /* sem storage neste quadro */
      }
    }
  }, sessao);
  await contexto.route("**/*", (rota) => rotear(rota, servidor, responder, registro));
  const pagina = await contexto.newPage();
  pagina.on("console", (msg) => {
    const texto = msg.text();
    if (/Content Security Policy|Refused to (load|execute|apply|connect|frame|create)/i.test(texto)) registro.csp.push(texto);
    if (/integrity/i.test(texto)) registro.sri.push(texto);
    if (/Permissions-Policy|Permissions policy/i.test(texto)) registro.permissoes.push(texto);
  });
  pagina.on("pageerror", (err) => registro.erros.push(err.message));
  return { contexto, pagina, registro };
}

async function assentar(pagina) {
  await pagina.waitForLoadState("networkidle", { timeout: 8000 }).catch(() => {});
  await pagina.waitForTimeout(500);
}

/** Clique por script: alguns alvos ficam em painéis recolhidos. */
async function clicar(pagina, seletor) {
  await pagina.waitForSelector(seletor, { state: "attached", timeout: 8000 });
  await pagina.evaluate((s) => document.querySelector(s).click(), seletor);
}

/* ---------------------------------------------------------------------- */
/* Grupo: XSS                                                             */
/* ---------------------------------------------------------------------- */

async function checarOpcoesDoFiltro(pagina) {
  const opcoes = await pagina.$$eval("#filtro-profissional option", (lista) =>
    lista.map((o) => ({ atributos: o.getAttributeNames(), valor: o.value }))
  );
  const problemas = [];
  opcoes
    .filter((o) => o.atributos.join(",") !== "value")
    .forEach((o) => problemas.push(`<option> ganhou atributos: ${o.atributos.join(", ")}`));
  if (!opcoes.some((o) => o.valor === api.NOME_QUEBRA_ATRIBUTO)) {
    problemas.push("o nome com aspas não chegou intacto ao value do <option>");
  }
  return problemas;
}

const CENARIOS_XSS = [
  { nome: "Home da clínica: profissionais, agenda, indicadores, alertas, notificações (SEC-03/02)", url: "Software/instituicao.html", sessao: "clinica", fotoValida: true },
  { nome: "Lista de profissionais (SEC-02)", url: "Software/profissionais.html", sessao: "clinica", fotoValida: true },
  { nome: "Home do profissional (SEC-03)", url: "Software/profissional.html", sessao: "profissional", fotoValida: true },
  { nome: "Pacientes de um profissional, visão da clínica (SEC-03)", url: "Software/profissional.html?idFisioterapeuta=6", sessao: "clinica", fotoValida: true },
  { nome: "Consultas: nome com aspas no <option> (SEC-24)", url: "Software/consultas.html", sessao: "clinica", checar: checarOpcoesDoFiltro },
  {
    nome: "Cartão de resumo do paciente (SEC-02)",
    url: "Software/consultas.html",
    sessao: "clinica",
    acao: async (p) => {
      await clicar(p, ".link-paciente");
      await p.waitForSelector(".resumo-topo", { timeout: 8000 });
    },
  },
  { nome: "Agenda do profissional", url: "Software/agenda.html", sessao: "profissional" },
  {
    nome: "Remarcar consulta",
    url: "Software/agenda.html",
    sessao: "profissional",
    acao: async (p) => {
      await clicar(p, "[data-remarcar]");
      await p.waitForSelector(".remarcar-overlay:not(.is-hidden)", { timeout: 8000 });
    },
  },
  { nome: "Ficha do paciente: cabeçalho, sessões, linha do tempo", url: "Software/paciente.html?id=1", sessao: "profissional" },
  {
    nome: "Curva do movimento",
    url: "Software/paciente.html?id=1",
    sessao: "profissional",
    acao: async (p) => {
      await clicar(p, "[data-curva]");
      await p.waitForSelector(".curva-topo", { timeout: 8000 });
    },
  },
  { nome: "Registrar sessão (histórico)", url: "Software/cadastrar-sessao.html?id=1", sessao: "profissional" },
  {
    nome: "Desempenho: indicadores, tabela, comparativo",
    url: "Software/desempenho.html",
    sessao: "clinica",
    acao: async (p) => {
      await p.waitForSelector("[data-tabela-comparativo] tr", { timeout: 8000 }).catch(() => {});
    },
  },
  { nome: "Goniômetros pareados", url: "Software/dispositivo.html", sessao: "clinica" },
  {
    nome: "Excluir profissional: opções do diálogo",
    url: "Software/perfil-profissional.html?id=6",
    sessao: "clinica",
    acao: async (p) => {
      await p.waitForFunction(() => /\S/.test(document.querySelector(".profile-header h1").textContent));
      await p.evaluate(() => {
        const botao = Array.from(document.querySelectorAll(".btn-danger")).find((b) => /Excluir profissional/.test(b.textContent));
        botao.click();
      });
      await p.waitForSelector("#dlg-destino", { timeout: 8000 });
    },
  },
  {
    nome: "Excluir conta: resumo do diálogo",
    url: "Software/configuracoes.html",
    sessao: "clinica",
    acao: async (p) => {
      await clicar(p, ".settings-perigo .btn-danger");
      await p.waitForSelector("#dlg-senha", { state: "attached", timeout: 8000 });
    },
  },
  {
    nome: "Busca global (SEC-02 na foto do resultado)",
    url: "Software/instituicao.html",
    sessao: "clinica",
    fotoValida: true,
    acao: async (p) => {
      await p.keyboard.press("Control+k");
      await p.fill(".busca-campo input", "an");
      await p.waitForSelector(".busca-item", { timeout: 8000 });
    },
  },
  {
    nome: "Mensagem de erro da API na Home da clínica (SEC-29)",
    url: "Software/instituicao.html",
    sessao: "clinica",
    falhas: [{ re: /^\/api\/fisioterapeutas\?idClinica=/, mensagem: api.carga("erroHomeClinica") }],
  },
  {
    nome: "Mensagem de erro da API na Home do profissional (SEC-29)",
    url: "Software/profissional.html",
    sessao: "profissional",
    falhas: [{ re: /^\/api\/pacientes\?idFisioterapeuta=/, mensagem: api.carga("erroHomeProfissional") }],
  },
  { nome: "Perfil da instituição (foto)", url: "Software/perfil-instituicao.html", sessao: "clinica" },
  { nome: "Editar perfil da instituição (foto)", url: "Software/editar-perfil-instituicao.html", sessao: "clinica" },
  { nome: "Perfil do profissional (foto)", url: "Software/perfil-profissional.html", sessao: "profissional" },
  { nome: "Editar perfil do profissional (foto)", url: "Software/editar-perfil-profissional.html", sessao: "profissional" },
  { nome: "Editar paciente (foto)", url: "Software/editar-paciente.html?id=1", sessao: "profissional" },
];

async function grupoXss(navegador, servidor) {
  for (const cenario of CENARIOS_XSS) {
    const responder = api.criarApi({ hostil: true, falhas: cenario.falhas || [] });
    const { contexto, pagina, registro } = await abrir(navegador, servidor, {
      cspAtiva: false,
      sessao: api.sessao(cenario.sessao, true),
      responder,
    });
    const problemas = [];
    try {
      await pagina.goto(`${servidor.origem}/${cenario.url}`, { waitUntil: "load" });
      await assentar(pagina);
      if (cenario.acao) await cenario.acao(pagina);
      await assentar(pagina);

      const estado = await pagina.evaluate(() => ({
        executou: window.__xss.slice(),
        injetados: Array.from(document.querySelectorAll("[data-xss]")).map((el) => el.outerHTML.slice(0, 140)),
        fundos: Array.from(document.querySelectorAll("*"))
          .map((el) => el.style && el.style.backgroundImage)
          .filter(Boolean),
        textoComCarga: document.body.textContent.includes("onerror=\"__xss.push("),
        endereco: location.pathname,
      }));
      estado.executou.forEach((campo) => problemas.push(`JavaScript da carga executou (campo ${campo})`));
      estado.injetados.forEach((html) => problemas.push(`HTML injetado: ${html}`));
      estado.fundos
        .filter((f) => /javascript:|data-xss|onerror/i.test(f))
        .forEach((f) => problemas.push(`foto hostil virou fundo: ${f}`));
      if (cenario.fotoValida && !estado.fundos.some((f) => f.includes(api.FOTO_UPLOAD_ABSOLUTA))) {
        problemas.push(`a foto válida (${api.FOTO_UPLOAD_ABSOLUTA}) não apareceu`);
      }
      if (!/\.html$/.test(estado.endereco) || /login/.test(estado.endereco)) problemas.push(`a tela saiu para ${estado.endereco}`);
      // Sem isto um cenário passaria "de graça" se a tela nem desenhasse.
      if (!estado.textoComCarga) problemas.push("a carga não apareceu como texto: a tela não desenhou os dados?");
      if (cenario.checar) problemas.push(...(await cenario.checar(pagina)));
    } catch (err) {
      problemas.push(`erro no cenário: ${err.message.split("\n")[0]}`);
    }
    registro.externas.forEach((u) => problemas.push(`pedido externo inesperado: ${u}`));
    registrar("xss", cenario.nome, problemas);
    await contexto.close();
  }
}

/* ---------------------------------------------------------------------- */
/* Grupo: .ics                                                            */
/* ---------------------------------------------------------------------- */

function desescaparIcs(valor) {
  return valor.replace(/\\([\\;,nN])/g, (m, c) => (c === "n" || c === "N" ? "\n" : c));
}

async function grupoIcs(navegador, servidor) {
  const titulo = "Maria; Alves, Jr. \\ filha";
  const agendamentosIcs = [
    { id: 21, idPaciente: 1, nomePaciente: titulo, nomeFisioterapeuta: "Ana", data: api.dataRelativa(3), hora: "14:30:00", observacao: api.OBSERVACAO_ICS, status: null },
    { id: 22, idPaciente: 2, nomePaciente: "João", nomeFisioterapeuta: "Ana", data: api.dataRelativa(4), hora: "09:00:00", observacao: null, status: null },
  ];
  const { contexto, pagina, registro } = await abrir(navegador, servidor, {
    cspAtiva: true,
    sessao: api.sessao("profissional", false),
    responder: api.criarApi({ agendamentosIcs }),
  });
  const problemas = [];
  try {
    await pagina.goto(`${servidor.origem}/Software/agenda.html`, { waitUntil: "load" });
    await assentar(pagina);
    const [download] = await Promise.all([pagina.waitForEvent("download", { timeout: 8000 }), clicar(pagina, '[data-action="exportar-ics"]')]);
    const texto = fs.readFileSync(await download.path(), "utf8");

    if (/\r(?!\n)/.test(texto)) problemas.push("há CR solto (sem LF) no arquivo: um leitor leniente abre linha nova ali");
    if (/(^|[^\r])\n/.test(texto)) problemas.push("há LF solto (sem CR) no arquivo");
    texto
      .split("\r\n")
      .filter((l) => Buffer.byteLength(l, "utf8") > 75)
      .forEach((l) => problemas.push(`linha com ${Buffer.byteLength(l, "utf8")} octetos (> 75, sem dobra): ${l.slice(0, 40)}…`));

    const desdobrado = texto.replace(/\r\n[ \t]/g, "");
    // Leitura leniente, como a de calendários que aceitam CR ou LF sozinhos.
    const linhasLenientes = desdobrado.split(/\r\n|\r|\n/);
    linhasLenientes
      .filter((l) => /^(BEGIN:VALARM|END:VALARM|TRIGGER|ACTION)/.test(l))
      .forEach((l) => problemas.push(`propriedade injetada virou linha própria: ${l}`));
    const linhas = desdobrado.split("\r\n");
    const eventos = linhas.filter((l) => l === "BEGIN:VEVENT").length;
    if (eventos !== 2) problemas.push(`esperava 2 eventos, vieram ${eventos}`);

    const descricao = (linhas.find((l) => l.startsWith("DESCRIPTION:")) || "").slice("DESCRIPTION:".length);
    const esperado = api.OBSERVACAO_ICS.replace(/\r\n|\r|\n/g, "\n");
    if (desescaparIcs(descricao) !== esperado) problemas.push(`DESCRIPTION não volta ao texto original: ${JSON.stringify(descricao).slice(0, 160)}`);
    if (/[;,]/.test(descricao.replace(/\\./g, ""))) problemas.push("DESCRIPTION com ; ou , sem escape");
    const resumo = (linhas.find((l) => l.startsWith("SUMMARY:")) || "").slice("SUMMARY:".length);
    if (desescaparIcs(resumo) !== `Sessão de fisioterapia — ${titulo}`) problemas.push(`SUMMARY não volta ao texto original: ${resumo}`);
    if (/[;,]/.test(resumo.replace(/\\./g, ""))) problemas.push("SUMMARY com ; ou , sem escape");
  } catch (err) {
    problemas.push(`erro no cenário: ${err.message.split("\n")[0]}`);
  }
  registro.erros.forEach((e) => problemas.push(`erro de JavaScript: ${e}`));
  registrar("ics", "exportação .ics escapa CR/LF, ; , \\ e dobra linhas longas (SEC-30)", problemas);
  await contexto.close();
}

/* ---------------------------------------------------------------------- */
/* Grupo: CSP                                                             */
/* ---------------------------------------------------------------------- */

const SESSAO_POR_PAGINA = {
  agenda: "profissional",
  "cadastrar-paciente": "profissional",
  "cadastrar-sessao": "profissional",
  "editar-paciente": "profissional",
  "editar-perfil-profissional": "profissional",
  loader: "profissional",
  paciente: "profissional",
  "perfil-profissional": "profissional",
  profissional: "profissional",
  "tutorial-goniometro": "profissional",
};

const PARAMETROS = {
  paciente: "?id=1",
  "cadastrar-sessao": "?id=1",
  "redefinir-senha": "?token=simulado",
};

/** Ações extras: exercitam o que a CSP mais poderia quebrar. */
const ACOES_CSP = {
  "Login/login.html": {
    descricao: "login completo até a Home",
    acao: async (p) => {
      await p.waitForSelector("[data-rh-captcha] iframe", { timeout: 8000 });
      await p.waitForFunction(() => !document.querySelector("#loginForm .btn-primary").classList.contains("rh-captcha-pendente"));
      await p.fill("#email", "ana@teste.example");
      await p.fill("#password", "segredo1");
      await Promise.all([p.waitForURL(/Software\/profissional\.html$/, { timeout: 8000 }), p.click("#loginForm .btn-primary")]);
      await assentar(p);
    },
    verificar: async (p) => ((await p.evaluate(() => location.pathname)).endsWith("/Software/profissional.html") ? [] : ["o login não levou à Home"]),
  },
  "Software/instituicao.html": {
    descricao: "tutorial e busca",
    sessaoExtra: { tutorialVisto: false },
    acao: async (p) => {
      await p.waitForSelector(".tutorial-next", { timeout: 8000 });
      await p.evaluate(() => document.querySelector(".tutorial-skip").click());
      await p.keyboard.press("Control+k");
      await p.fill(".busca-campo input", "an");
      await p.waitForSelector(".busca-item", { timeout: 8000 });
    },
  },
  "Software/agenda.html": {
    descricao: ".ics e relatório em PDF (jsPDF carregado sob demanda)",
    acao: async (p) => {
      await Promise.all([p.waitForEvent("download", { timeout: 8000 }), clicar(p, '[data-action="exportar-ics"]')]);
      await p.waitForFunction(() => !document.querySelector('[data-action="relatorio-consultas"]').disabled, null, { timeout: 8000 });
      await Promise.all([p.waitForEvent("download", { timeout: 15000 }), clicar(p, '[data-action="relatorio-consultas"]')]);
    },
  },
  "Software/paciente.html": {
    descricao: "gráficos, curva e relatório em PDF",
    acao: async (p) => {
      await clicar(p, "[data-curva]");
      await p.waitForSelector(".curva-caixa canvas", { timeout: 8000 });
      await p.keyboard.press("Escape");
      await Promise.all([p.waitForEvent("download", { timeout: 15000 }), clicar(p, '[data-acao="relatorio"]')]);
    },
  },
  "Software/cadastrar-profissional.html": {
    descricao: "prévia da foto escolhida (blob:)",
    acao: async (p) => {
      await p.setInputFiles("#pfoto", { name: "foto.png", mimeType: "image/png", buffer: PNG_1PX });
      await p.waitForFunction(() => /blob:/.test(document.querySelector("[data-avatar-picker]").style.backgroundImage));
    },
  },
  "Software/editar-paciente.html": {
    descricao: "sem ?id o formulário não pode recarregar a página ao enviar",
    acao: async (p) => {
      await p.evaluate(() => document.querySelector("form.edit-grid").requestSubmit());
      await p.waitForTimeout(500);
    },
    verificar: async (p) => ((await p.evaluate(() => location.href)).includes("?") ? ["o envio recarregou a página"] : []),
  },
  "Software/loader.html": {
    descricao: "botões da demonstração",
    acao: async (p) => {
      await p.waitForTimeout(800);
      await p.click("text=Mostrar em tela cheia");
    },
    verificar: async (p) => ((await p.$("[data-rh-loader]:not(.rh-loader--inline):not(.is-hidden)")) ? [] : ["o botão não abriu o loader"]),
  },
  "index.html": {
    descricao: "tema antes da pintura e fonte carregada",
    verificar: async (p) => {
      const r = await p.evaluate(() => {
        const fonte = document.querySelector('link[rel="stylesheet"][href*="fonts.googleapis.com"]');
        return { tema: document.documentElement.getAttribute("data-tema"), fonte: !!(fonte && fonte.sheet) };
      });
      const problemas = [];
      if (!/^(claro|escuro)$/.test(r.tema || "")) problemas.push("o script do tema não rodou");
      if (!r.fonte) problemas.push("a folha da fonte não foi aplicada");
      return problemas;
    },
  },
};

async function grupoCsp(navegador, servidor) {
  for (const arquivo of paginasDoSite()) {
    const base = path.basename(arquivo, ".html").replace(/-escuro$/, "");
    const naArea = arquivo.startsWith("Software/");
    const tipoSessao = naArea ? SESSAO_POR_PAGINA[base] || "clinica" : null;
    const extra = ACOES_CSP[arquivo] || {};
    let sessao = tipoSessao ? api.sessao(tipoSessao, false, extra.sessaoExtra) : null;
    if (arquivo === "Software/editar-paciente.html") sessao = api.sessao("profissional", false);

    const { contexto, pagina, registro } = await abrir(navegador, servidor, { cspAtiva: true, sessao, responder: api.criarApi() });
    const problemas = [];
    try {
      const parametros = arquivo === "Software/editar-paciente.html" ? "" : PARAMETROS[base] || "";
      const resposta = await pagina.goto(`${servidor.origem}/${arquivo}${parametros}`, { waitUntil: "load" });
      if (!resposta.headers()["content-security-policy"]) problemas.push("a página veio sem Content-Security-Policy");
      await assentar(pagina);
      if (extra.acao) await extra.acao(pagina);
      await assentar(pagina);

      const estado = await pagina.evaluate(() => ({
        violacoes: window.__csp.slice(),
        toastify: typeof Toastify === "function",
        gsap: typeof gsap !== "undefined",
        chart: typeof Chart === "function",
        temChart: !!document.querySelector('script[src*="chart.js"]'),
        temToastify: !!document.querySelector('script[src*="toastify"]'),
        temGsap: !!document.querySelector('script[src*="gsap"]'),
        endereco: location.pathname,
      }));
      estado.violacoes.forEach((v) => problemas.push(`violação de CSP: ${v}`));
      if (estado.temToastify && !estado.toastify) problemas.push("Toastify não carregou");
      if (estado.temGsap && !estado.gsap) problemas.push("GSAP não carregou");
      if (estado.temChart && !estado.chart) problemas.push("Chart.js não carregou");
      if (naArea && /Login\//.test(estado.endereco)) problemas.push("a tela mandou para o login");
      if (extra.verificar) problemas.push(...(await extra.verificar(pagina)));
    } catch (err) {
      problemas.push(`erro no cenário: ${err.message.split("\n")[0]}`);
    }
    registro.csp.forEach((m) => problemas.push(`console: ${m}`));
    registro.sri.forEach((m) => problemas.push(`SRI: ${m}`));
    registro.permissoes.forEach((m) => problemas.push(`Permissions-Policy: ${m}`));
    registro.erros.forEach((m) => problemas.push(`erro de JavaScript: ${m}`));
    registro.externas.forEach((u) => problemas.push(`pedido externo inesperado: ${u}`));
    registrar("csp", `${arquivo}${extra.descricao ? ` (${extra.descricao})` : ""}`, [...new Set(problemas)]);
    await contexto.close();
  }
}

/* ---------------------------------------------------------------------- */

/* ---------------------------------------------------------------------- */
/* Ids da URL nos caminhos da API                                         */
/* ---------------------------------------------------------------------- */

// Um ?id= que não é número ia direto para o caminho das chamadas: um link
// com "5&x=/../../dispositivos/3" fazia "Excluir profissional" revogar um
// goniômetro, e "1/../../configuracoes?x=" fazia "Salvar paciente" apagar a
// configuração de atendimento. Agora id que não é número vira "sem id".
const IDS_HOSTIS = [
  { sessao: "clinica", url: "Software/perfil-profissional.html?id=" + encodeURIComponent("5&x=/../../dispositivos/3") },
  { sessao: "profissional", url: "Software/editar-paciente.html?id=" + encodeURIComponent("1/../../configuracoes?x=") },
  { sessao: "clinica", url: "Software/editar-perfil-profissional.html?id=" + encodeURIComponent("2/../../clinicas/9") },
  { sessao: "clinica", url: "Software/desempenho.html?id=" + encodeURIComponent("2/../../clinicas/9") },
  { sessao: "clinica", url: "Software/profissional.html?idFisioterapeuta=" + encodeURIComponent("3/../../dispositivos/1") },
  { sessao: "profissional", url: "Software/paciente.html?id=" + encodeURIComponent("4/../../configuracoes") },
  { sessao: "profissional", url: "Software/cadastrar-sessao.html?id=" + encodeURIComponent("4/../../configuracoes") },
];

async function grupoIds(navegador, servidor) {
  const problemas = [];
  for (const caso of IDS_HOSTIS) {
    const { contexto, pagina, registro } = await abrir(navegador, servidor, {
      cspAtiva: false,
      sessao: api.sessao(caso.sessao, false),
      responder: api.criarApi({ hostil: false }),
    });
    try {
      await pagina.goto(`${servidor.origem}/${caso.url}`, { waitUntil: "load" });
      await assentar(pagina);
      registro.api
        .filter((chamada) => /\.\.|%2e%2e|\/dispositivos\/[0-9]|\/configuracoes\?x|\/clinicas\/9/i.test(chamada))
        .forEach((chamada) => problemas.push(`${caso.url.split("?")[0]}: ${chamada}`));
    } finally {
      await contexto.close();
    }
  }
  registrar("ids", "?id= que não é número não chega aos caminhos da API", problemas);
}

async function principal() {
  console.log(`Raiz testada: ${RAIZ}\n`);
  if (GRUPOS.includes("estatico")) await grupoEstatico();

  const precisaNavegador = GRUPOS.some((g) => ["xss", "ics", "csp", "ids"].includes(g));
  if (precisaNavegador) {
    const { chromium } = carregarPlaywright();
    const servidor = await iniciarServidor(RAIZ);
    // Trava extra: mesmo que uma rota escape do roteamento, nenhum nome de
    // host resolve — o teste nunca fala com a produção.
    const navegador = await chromium.launch({ args: ["--host-resolver-rules=MAP * ~NOTFOUND, EXCLUDE 127.0.0.1"] });
    try {
      if (GRUPOS.includes("xss")) await grupoXss(navegador, servidor);
      if (GRUPOS.includes("ics")) await grupoIcs(navegador, servidor);
      if (GRUPOS.includes("csp")) await grupoCsp(navegador, servidor);
      if (GRUPOS.includes("ids")) await grupoIds(navegador, servidor);
    } finally {
      await navegador.close();
      await servidor.fechar();
    }
  }

  const falhas = resultados.filter((r) => !r.ok);
  console.log(`\n${resultados.length - falhas.length} de ${resultados.length} verificações passaram.`);
  process.exitCode = falhas.length ? 1 : 0;
}

principal().catch((err) => {
  console.error(err);
  process.exitCode = 2;
});
