// Ponta a ponta LOCAL dos fluxos que a auditoria de segurança mudou: API real
// (o jar) em localhost:8080 + site em localhost:8090 com os cabeçalhos do
// vercel.json (a origem da API na CSP trocada pela local). O navegador não
// resolve nenhum host além de localhost: nada sai para a produção.
//
// Uso (na raiz do repositório):
//   (cd rehabit-api/rehabit-api && mvn -q package -DskipTests)
//   node tests/ponta-a-ponta/e2e.js
// Precisa de Java 17, Node 18+ e Playwright (PLAYWRIGHT_MODULO, se não
// estiver instalado no projeto). Usa as portas 8080 e 8090.
"use strict";
const { spawn } = require("child_process");
const fs = require("fs");
const http = require("http");
const path = require("path");
const crypto = require("crypto");

const os = require("os");
const REPO = path.join(__dirname, "..", "..");
const AQUI = fs.mkdtempSync(path.join(os.tmpdir(), "rehabit-e2e-"));
const cdn = require(REPO + "/tests/seguranca-front/cdn.js");
const { lerRegras, cabecalhosPara } = require(REPO + "/tests/seguranca-front/servidor.js");
function carregarPlaywright() {
  for (const m of [process.env.PLAYWRIGHT_MODULO, "playwright", "/opt/node-tools/node_modules/playwright"].filter(Boolean)) {
    try {
      return require(m);
    } catch (err) {
      /* tenta o próximo */
    }
  }
  throw new Error("Playwright não encontrado. Instale (npm i -D playwright) ou defina PLAYWRIGHT_MODULO.");
}
const { chromium } = carregarPlaywright();

const API = "http://localhost:8080";
const SITE = "http://localhost:8090";
const UPLOADS = path.join(AQUI, "uploads");
const LOG_API = path.join(AQUI, "api.log");
const SEGREDO = "SEGREDO-GPS-E2E";

const resultados = [];
function conferir(nome, ok, detalhe) {
  resultados.push({ nome, ok });
  console.log(`${ok ? "ok   " : "FALHA"} ${nome}${detalhe ? " — " + detalhe : ""}`);
}

/* ---------- foto de "celular": JPEG real + EXIF com GPS + comentário + apêndice ---------- */
function fotoDeCelular() {
  const base = fs.readFileSync(path.join(REPO, "Login/assets/physio.jpg"));
  const metodo = Buffer.from("ASCII\0\0\0" + SEGREDO, "latin1");
  const t = Buffer.alloc(56 + metodo.length);
  t.write("II", 0, "latin1"); t.writeUInt16LE(42, 2); t.writeUInt32LE(8, 4);
  t.writeUInt16LE(2, 8);
  t.writeUInt16LE(0x0112, 10); t.writeUInt16LE(3, 12); t.writeUInt32LE(1, 14); t.writeUInt16LE(6, 18);
  t.writeUInt16LE(0x8825, 22); t.writeUInt16LE(4, 24); t.writeUInt32LE(1, 26); t.writeUInt32LE(38, 30);
  t.writeUInt32LE(0, 34);
  t.writeUInt16LE(1, 38);
  t.writeUInt16LE(0x001b, 40); t.writeUInt16LE(7, 42); t.writeUInt32LE(metodo.length, 44); t.writeUInt32LE(56, 48);
  t.writeUInt32LE(0, 52);
  metodo.copy(t, 56);
  const seg = (m, dados) => Buffer.concat([Buffer.from([0xff, m, (dados.length + 2) >> 8, (dados.length + 2) & 255]), dados]);
  const app1 = seg(0xe1, Buffer.concat([Buffer.from("Exif\0\0", "latin1"), t]));
  const com = seg(0xfe, Buffer.from("comentario " + SEGREDO, "latin1"));
  return Buffer.concat([base.subarray(0, 2), app1, com, base.subarray(2), Buffer.from("APENDICE " + SEGREDO, "latin1")]);
}

/* ---------- site com os cabeçalhos do vercel.json ---------- */
function servirSite() {
  const regras = lerRegras(REPO);
  const tipos = { ".html": "text/html; charset=utf-8", ".js": "text/javascript; charset=utf-8", ".css": "text/css; charset=utf-8",
    ".png": "image/png", ".jpg": "image/jpeg", ".svg": "image/svg+xml", ".webp": "image/webp", ".ico": "image/x-icon",
    ".json": "application/json", ".webmanifest": "application/manifest+json", ".woff2": "font/woff2" };
  const srv = http.createServer((req, res) => {
    const caminho = decodeURIComponent(new URL(req.url, SITE).pathname);
    let arq = path.join(REPO, caminho);
    if (!arq.startsWith(REPO)) return res.writeHead(403).end();
    if (fs.existsSync(arq) && fs.statSync(arq).isDirectory()) arq = path.join(arq, "index.html");
    if (!fs.existsSync(arq)) return res.writeHead(404).end();
    const cab = cabecalhosPara(regras, caminho);
    for (const k of Object.keys(cab)) cab[k] = cab[k].split("https://rehabit-api-4tex.onrender.com").join(API);
    delete cab["Strict-Transport-Security"];
    res.writeHead(200, Object.assign({ "Content-Type": tipos[path.extname(arq)] || "application/octet-stream" }, cab));
    res.end(fs.readFileSync(arq));
  });
  return new Promise((ok) => srv.listen(8090, "127.0.0.1", () => ok(srv)));
}

/* ---------- API ---------- */
async function subirApi() {
  fs.rmSync(path.join(AQUI, "db"), { recursive: true, force: true });
  fs.rmSync(UPLOADS, { recursive: true, force: true });
  const env = Object.assign({}, process.env, { REHABIT_JWT_SECRET: crypto.randomBytes(64).toString("base64") });
  ["BREVO_API_KEY", "MAIL_USERNAME", "MAIL_PASSWORD", "CLOUDINARY_URL", "RENDER", "RENDER_EXTERNAL_URL", "TURNSTILE_SECRET_KEY"].forEach((k) => delete env[k]);
  const log = fs.openSync(LOG_API, "w");
  const proc = spawn("java", ["-jar", REPO + "/rehabit-api/rehabit-api/target/rehabit-api-1.0.0.jar",
    "--server.port=8080",
    "--spring.datasource.url=jdbc:h2:file:" + path.join(AQUI, "db/rehabit"),
    "--app.upload-dir=" + UPLOADS,
    "--rehabit.email.validar-dns=false",
    "--rehabit.email.confirmar-cadastro=true",
    "--rehabit.app.url-login=" + SITE + "/Login",
    "--rehabit.keepalive.ativo=false",
    // As duas contas de exemplo, como no Render: a da demonstração (a Ana, do
    // botão da página inicial) e a da apresentação, com as senhas padrão do
    // application.properties.
    "--rehabit.demo.ativar=true",
    "--rehabit.apresentacao.ativar=true"], { env, stdio: ["ignore", log, log] });
  for (let i = 0; i < 120; i++) {
    try {
      const r = await fetch(API + "/api/health");
      if (r.ok) return proc;
    } catch (e) { /* subindo */ }
    await new Promise((r) => setTimeout(r, 1000));
  }
  throw new Error("API não subiu; veja " + LOG_API);
}

function codigoDoLog(email) {
  const texto = fs.readFileSync(LOG_API, "utf8");
  const i = texto.lastIndexOf(email);
  const m = /\b(\d{6})\b/.exec(texto.slice(i));
  return m && m[1];
}

async function main() {
  console.log("Pasta de trabalho (banco, fotos, log da API): " + AQUI);
  const api = await subirApi();
  const site = await servirSite();
  const navegador = await chromium.launch({ args: ["--host-resolver-rules=MAP * ~NOTFOUND, EXCLUDE localhost, EXCLUDE 127.0.0.1"] });
  const contexto = await navegador.newContext();
  const urls = [];
  const problemas = [];
  await contexto.route("**/*", async (rota) => {
    const url = rota.request().url();
    if (url.startsWith("https://cdn.jsdelivr.net/")) {
      const corpo = await cdn.conteudoParaUrl(url);
      const tipo = url.endsWith(".css") ? "text/css" : "text/javascript";
      return corpo ? rota.fulfill({ status: 200, body: corpo, headers: { "Content-Type": tipo, "Access-Control-Allow-Origin": "*" } }) : rota.abort();
    }
    if (url.startsWith(SITE) || url.startsWith(API)) return rota.continue();
    return rota.abort();
  });
  const pagina = await contexto.newPage();
  pagina.on("request", (r) => urls.push(r.method() + " " + r.url()));
  pagina.on("console", (m) => { if (/Content Security Policy|Refused to|integrity/i.test(m.text())) problemas.push(m.text()); });
  pagina.on("pageerror", (e) => problemas.push("erro JS: " + e.message));
  const toast = async () => (await pagina.locator(".toastify").allTextContents()).join(" | ");

  const email = "clinica.e2e@exemplo.com.br";
  const senha = "Senha-Do-E2E-2026";
  try {
    /* 1. Cadastro com foto e código por e-mail */
    await pagina.goto(SITE + "/Login/register.html");
    await pagina.fill("#name", "Clínica E2E");
    await pagina.fill("#email", email);
    await pagina.fill("#password", senha);
    await pagina.fill("#confirm", senha);
    if (await pagina.locator("#cnpj").count()) await pagina.fill("#cnpj", "12.345.678/0001-90");
    await pagina.click("#registerForm button[type=submit]");
    await pagina.waitForSelector(".rh-overlay.open");
    if (await pagina.locator("#rh-cnpj").count()) await pagina.fill("#rh-cnpj", "12.345.678/0001-90");
    fs.writeFileSync(path.join(AQUI, "celular.jpg"), fotoDeCelular());
    await pagina.setInputFiles(".rh-overlay input[type=file]", path.join(AQUI, "celular.jpg"));
    await pagina.click(".rh-overlay.open .rh-submit");
    await pagina.waitForSelector(".rh-codigo-input", { state: "visible", timeout: 15000 });
    await pagina.waitForTimeout(500);
    const codigo = codigoDoLog(email);
    conferir("código de cadastro chegou (log da API)", !!codigo);
    await pagina.fill(".rh-codigo-input", codigo);
    await pagina.click(".rh-confirmar");
    await pagina.waitForURL(/login\.html/, { timeout: 15000 });
    conferir("cadastro com foto concluído", true);
    const fotoCadastro = urls.find((u) => u.startsWith("POST " + API + "/api/auth/cadastro/foto"));
    conferir("foto do cadastro foi pela rota nova, sem login", !!fotoCadastro);

    /* 2. Login */
    await pagina.fill("#email", email);
    await pagina.fill("#password", senha);
    await pagina.click("#loginForm button[type=submit]");
    await pagina.waitForURL(/Software\//, { timeout: 15000 });
    const sessao = await pagina.evaluate(() => JSON.parse(localStorage.getItem("rehabit_usuario")));
    conferir("login e sessão com foto", !!(sessao && sessao.token && /^\/uploads\/[0-9a-f-]{36}\.jpg$/.test(sessao.foto)), sessao && sessao.foto);
    const arquivoCadastro = path.join(UPLOADS, sessao.foto.replace("/uploads/", ""));
    const salvo = fs.readFileSync(arquivoCadastro);
    conferir("foto do cadastro sem GPS/comentário/apêndice", !salvo.includes(Buffer.from(SEGREDO)));
    const r = await fetch(API + sessao.foto);
    conferir("foto servida como imagem, com sandbox e nosniff",
      r.headers.get("content-type") === "image/jpeg" && /sandbox/.test(r.headers.get("content-security-policy") || "") && r.headers.get("x-content-type-options") === "nosniff");

    /* 3. Upload sem login é recusado */
    const fd = new FormData();
    fd.append("arquivo", new Blob([fs.readFileSync(path.join(AQUI, "celular.jpg"))], { type: "image/jpeg" }), "x.jpg");
    const anonimo = await fetch(API + "/api/uploads", { method: "POST", body: fd });
    conferir("upload sem login → 401", anonimo.status === 401, String(anonimo.status));

    /* 4. Trocar a foto do perfil (upload com login) — a antiga sai do disco */
    await pagina.goto(SITE + "/Software/editar-perfil-instituicao.html");
    await pagina.waitForFunction(() => document.getElementById("i-nome").value !== "");
    await pagina.setInputFiles("#pfoto", path.join(REPO, "Login/assets/physio.jpg"));
    await pagina.click("form.edit-grid .btn-primary");
    await pagina.waitForURL(/\/perfil-instituicao\.html/, { timeout: 15000 });
    const sessao2 = await pagina.evaluate(() => JSON.parse(localStorage.getItem("rehabit_usuario")));
    conferir("foto do perfil trocada", sessao2.foto && sessao2.foto !== sessao.foto, sessao2.foto);
    conferir("foto antiga apagada do disco", !fs.existsSync(arquivoCadastro));

    /* 5. Cadastrar profissional com foto */
    await pagina.goto(SITE + "/Software/cadastrar-profissional.html");
    await pagina.fill("#pid", "COFFITO-3/999999-F");
    await pagina.fill("#pnome", "Profissional E2E");
    await pagina.fill("#pmail", "prof.e2e@exemplo.com.br");
    await pagina.fill("#psenha", "Senha-Do-Prof-2026");
    await pagina.setInputFiles("#pfoto", path.join(REPO, "Login/assets/physio.jpg"));
    await pagina.click("#cadastrarProfissionalForm .btn-primary");
    await pagina.waitForURL(/\/instituicao\.html/, { timeout: 15000 });
    const lista = await (await fetch(API + "/api/fisioterapeutas?idClinica=" + sessao.id,
      { headers: { Authorization: "Bearer " + sessao.token } })).json();
    const prof = Array.isArray(lista) && lista.find((f) => f.nome === "Profissional E2E");
    conferir("profissional cadastrado com foto", !!(prof && /^\/uploads\/[0-9a-f-]{36}\.jpg$/.test(prof.foto || "")), JSON.stringify(prof));

    /* 6. SSE do goniômetro por ingresso */
    urls.length = 0;
    await pagina.goto(SITE + "/Software/dispositivo.html");
    await pagina.waitForTimeout(4000);
    const pediuIngresso = urls.some((u) => u.startsWith("POST " + API + "/api/goniometro/stream/ingresso"));
    const abriuComIngresso = urls.some((u) => u.startsWith("GET " + API + "/api/goniometro/stream?") && /ingresso=/.test(u));
    const tokenNaUrl = urls.some((u) => /[?&]token=/.test(u));
    conferir("SSE: pediu ingresso e abriu o stream com ele", pediuIngresso && abriuComIngresso);
    conferir("nenhuma URL com token de sessão", !tokenNaUrl);
    const usandoPolling = await pagina.evaluate(() => window.RehabitGoniometro && window.RehabitGoniometro.usandoPolling && window.RehabitGoniometro.usandoPolling());
    conferir("SSE ativo (sem cair para polling)", usandoPolling === false, String(usandoPolling));

    /* 7. Trocar a senha em Configurações: continua logado com o token novo */
    await pagina.goto(SITE + "/Software/configuracoes.html");
    await pagina.click('[data-toggle="seguranca"]');
    const tokenAntes = (await pagina.evaluate(() => JSON.parse(localStorage.getItem("rehabit_usuario")))).token;
    await pagina.fill("#cfg-senha-atual", senha);
    await pagina.fill("#cfg-senha-nova", "Outra-Senha-Do-E2E-1");
    await pagina.click("#trocarSenhaForm .btn-primary");
    await pagina.waitForTimeout(2000);
    const tokenDepois = (await pagina.evaluate(() => JSON.parse(localStorage.getItem("rehabit_usuario")))).token;
    conferir("troca de senha guardou token novo", tokenDepois && tokenDepois !== tokenAntes);
    const velho = await fetch(API + "/api/clinicas/" + sessao.id, { headers: { Authorization: "Bearer " + tokenAntes } });
    conferir("token antigo não vale mais", velho.status === 401, String(velho.status));
    await pagina.goto(SITE + "/Software/perfil-instituicao.html");
    await pagina.waitForTimeout(2000);
    conferir("continua logado depois da troca", /\/perfil-instituicao\.html/.test(pagina.url()), pagina.url());

    /* 8. Senha nova pela edição de perfil: volta ao login com aviso */
    await pagina.goto(SITE + "/Software/editar-perfil-instituicao.html");
    await pagina.waitForFunction(() => document.getElementById("i-nome").value !== "");
    await pagina.fill("#i-senha-atual", "Outra-Senha-Do-E2E-1");
    await pagina.fill("#i-senha-nova", "Mais-Uma-Senha-E2E-9");
    await pagina.click("form.edit-grid .btn-primary");
    await pagina.waitForURL(/Login\/login/, { timeout: 15000 });
    conferir("senha trocada no perfil leva ao login", true);

    /* 9. "Esqueci minha senha" pelo link do e-mail: o token sai da URL e vai no corpo */
    await fetch(API + "/api/auth/esqueci-senha", { method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email }) });
    await new Promise((r) => setTimeout(r, 1000));
    const textoLog = fs.readFileSync(LOG_API, "utf8");
    const link = (textoLog.match(/http:\/\/localhost:8090\/Login\/redefinir-senha[^\s"<]*/g) || []).pop();
    conferir("e-mail de recuperação trouxe o link", !!link);
    urls.length = 0;
    await pagina.goto(link);
    await pagina.waitForTimeout(1500);
    conferir("token some da barra de endereço", !/token=/.test(pagina.url()), pagina.url().replace(SITE, ""));
    conferir("link validado por POST, sem token na URL da API",
      urls.some((u) => u.startsWith("POST " + API + "/api/auth/recuperar-senha/validar"))
        && !urls.some((u) => /recuperar-senha\/validar\?token=/.test(u)));
    await pagina.fill("#novaSenha", "Senha-Pelo-Link-E2E-3");
    await pagina.fill("#confirmarSenha", "Senha-Pelo-Link-E2E-3");
    await pagina.click("#redefinirSenhaForm button[type=submit]");
    await pagina.waitForURL(/Login\/login/, { timeout: 15000 });
    const entrou = await fetch(API + "/api/auth/login", { method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, senha: "Senha-Pelo-Link-E2E-3" }) });
    conferir("senha nova pelo link funciona", entrou.status === 200, String(entrou.status));

    /* 10. "Entrar na demonstração": a Ana, com foto e o mês em dia, só de leitura; a Alvorada aceita alterações */
    const loginApi = (e, s) => fetch(API + "/api/auth/login", { method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email: e, senha: s }) });
    for (let i = 0; i < 30 && (await loginApi("ana.ribeiro@movimento.example", "Rehabit@2026")).status !== 200; i++) {
      await new Promise((ok) => setTimeout(ok, 2000)); // a conta é criada logo depois da subida
    }
    await pagina.evaluate(() => localStorage.clear());
    await pagina.goto(SITE + "/index.html");
    await pagina.locator('a[href="Login/login.html?demo"]').first().click();
    await pagina.waitForURL(/Login\/login\.html\?demo/, { timeout: 15000 });
    const preenchido = await pagina.inputValue("#email");
    conferir("botão da demonstração preenche a Ana", preenchido === "ana.ribeiro@movimento.example", preenchido);
    await pagina.click("#loginForm button[type=submit]");
    await pagina.waitForURL(/Software\/profissional/, { timeout: 15000 });
    const daAna = await pagina.evaluate(() => JSON.parse(localStorage.getItem("rehabit_usuario")));
    await pagina.waitForSelector(".aviso-demo", { timeout: 10000 });
    conferir("entrou como a Ana, com o aviso de demonstração", daAna.nome === "Ana Paula Ribeiro");
    const foto = daAna.foto ? await fetch(API + daAna.foto) : null;
    conferir("a Ana tem foto, servida pela API",
      /^\/uploads\/[0-9a-f-]{36}\.png$/.test(daAna.foto || "") && foto.status === 200
        && /^image\/png/.test(foto.headers.get("content-type") || ""), daAna.foto + " " + (foto && foto.status));
    const comToken = (t) => ({ Authorization: "Bearer " + t, "Content-Type": "application/json" });
    // Os cartões do início: a semana sempre tem consulta; e, havendo sessão no
    // mês (nos primeiros dias dele pode ainda não haver), há amplitude média.
    const cartoes = await (await fetch(API + "/api/estatisticas", { headers: comToken(daAna.token) })).json();
    const cartao = (r) => (cartoes.find((c) => c.rotulo === r) || {}).valor;
    conferir("a Ana tem consultas na semana e amplitude no mês",
      Number(cartao("Consultas esta semana")) > 0
        && (Number(cartao("Sessões no mês")) === 0 || /^\d+°$/.test(cartao("Amplitude média") || "")),
      JSON.stringify(cartoes.map((c) => c.rotulo + "=" + c.valor)));
    await pagina.waitForSelector(".kpi-card", { timeout: 10000 });
    const seus = await (await fetch(API + "/api/pacientes?idFisioterapeuta=" + daAna.id, { headers: comToken(daAna.token) })).json();
    const recusa = await fetch(API + "/api/pacientes/" + seus[0].id, { method: "PUT", headers: comToken(daAna.token),
      body: JSON.stringify({ nome: "Nome trocado por um visitante" }) });
    const motivo = (await recusa.json().catch(() => ({}))).mensagem || "";
    conferir("a demonstração não altera nada (403)", recusa.status === 403 && /demonstração/.test(motivo), recusa.status + " " + motivo);
    urls.length = 0;
    await pagina.goto(SITE + "/Software/dispositivo.html");
    await pagina.waitForTimeout(2500);
    const simulado = await pagina.evaluate(() => window.RehabitGoniometro && window.RehabitGoniometro.simulado());
    conferir("goniômetro simulado, sem abrir o stream da clínica",
      simulado === true && !urls.some((u) => u.includes("/api/goniometro/stream")), String(simulado));
    const daMariana = await (await loginApi("mariana.bertolini@alvorada.example", "Rehabit1234@")).json();
    const dela = daMariana.token ? await (await fetch(API + "/api/pacientes?idFisioterapeuta=" + daMariana.id,
      { headers: comToken(daMariana.token) })).json() : [];
    const aceita = dela.length ? await fetch(API + "/api/pacientes/" + dela[0].id, { method: "PUT",
      headers: comToken(daMariana.token), body: JSON.stringify({ nome: dela[0].nome }) }) : { status: 0 };
    conferir("a Mariana, da Alvorada, entra com Rehabit1234@ e altera", aceita.status === 200, String(aceita.status));

    /* 11. Cabeçalho do limite: 429 com mensagem */
    let ultimo;
    for (let i = 0; i < 12; i++) {
      ultimo = await fetch(API + "/api/auth/login", { method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, senha: "errada-" + i }) });
    }
    const corpo = await ultimo.json().catch(() => ({}));
    conferir("muitas senhas erradas → 429 com mensagem", ultimo.status === 429 && /tente de novo/i.test(corpo.mensagem || ""), ultimo.status + " " + corpo.mensagem);
  } catch (err) {
    conferir("fluxo sem exceção", false, err.message + " | toast: " + (await toast().catch(() => "")));
    await pagina.screenshot({ path: path.join(AQUI, "falha.png") }).catch(() => {});
  } finally {
    conferir("nenhuma violação de CSP/SRI nem erro de JS", problemas.length === 0, problemas.slice(0, 5).join(" || "));
    await navegador.close();
    site.close();
    api.kill();
  }
  const falhas = resultados.filter((r) => !r.ok).length;
  console.log(`\n${resultados.length - falhas} de ${resultados.length} verificações passaram.`);
  process.exit(falhas ? 1 : 0);
}
main();
