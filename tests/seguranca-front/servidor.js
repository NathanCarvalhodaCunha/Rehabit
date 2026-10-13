// Servidor estático local que imita a Vercel no que importa para estes testes:
// serve os arquivos da raiz do site e aplica os cabeçalhos declarados no
// vercel.json DESSA raiz. Sem isso a CSP nunca seria exercitada fora da
// produção — e o teste tem de provar que nenhuma página quebra com ela.
//
// Uso avulso: node servidor.js [raiz] [porta]  (padrão: raiz do repositório, 8090)
"use strict";

const fs = require("fs");
const http = require("http");
const path = require("path");

const TIPOS = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".webmanifest": "application/manifest+json",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".webp": "image/webp",
  ".ico": "image/x-icon",
  ".txt": "text/plain; charset=utf-8",
  ".xml": "application/xml",
  ".woff2": "font/woff2",
};

/**
 * Converte o "source" da Vercel (sintaxe do path-to-regexp) em RegExp. Os
 * grupos "(...)" já são expressão regular; ":nome" vira um segmento.
 */
function regexDaOrigem(origem) {
  let re = "";
  let i = 0;
  while (i < origem.length) {
    const c = origem[i];
    if (c === "(") {
      let nivel = 0;
      let j = i;
      for (; j < origem.length; j++) {
        if (origem[j] === "(") nivel++;
        else if (origem[j] === ")" && --nivel === 0) break;
      }
      re += origem.slice(i, j + 1);
      i = j + 1;
    } else if (c === ":") {
      const nome = /^:\w+/.exec(origem.slice(i))[0];
      re += "([^/]+)";
      i += nome.length;
    } else {
      re += c.replace(/[.*+?^${}|[\]\\]/g, "\\$&");
      i++;
    }
  }
  return new RegExp("^" + re + "$");
}

function lerRegras(raiz) {
  const arquivo = path.join(raiz, "vercel.json");
  if (!fs.existsSync(arquivo)) return [];
  const config = JSON.parse(fs.readFileSync(arquivo, "utf8"));
  return (config.headers || []).map((regra) => ({ re: regexDaOrigem(regra.source), cabecalhos: regra.headers }));
}

/** Cabeçalhos que a Vercel mandaria para este caminho (a regra posterior vence). */
function cabecalhosPara(regras, caminho) {
  const saida = {};
  regras.forEach((regra) => {
    if (regra.re.test(caminho)) regra.cabecalhos.forEach((h) => (saida[h.key] = h.value));
  });
  return saida;
}

function iniciarServidor(raiz, porta = 0) {
  const raizAbsoluta = path.resolve(raiz);
  const regras = lerRegras(raizAbsoluta);

  const servidor = http.createServer((req, res) => {
    let caminho;
    try {
      caminho = decodeURIComponent(new URL(req.url, "http://local").pathname);
    } catch (err) {
      res.writeHead(400).end();
      return;
    }
    let arquivo = path.join(raizAbsoluta, caminho);
    if (!arquivo.startsWith(raizAbsoluta)) {
      res.writeHead(403).end();
      return;
    }
    if (fs.existsSync(arquivo) && fs.statSync(arquivo).isDirectory()) arquivo = path.join(arquivo, "index.html");
    if (!fs.existsSync(arquivo)) {
      res.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" }).end("não encontrado");
      return;
    }
    const tipo = TIPOS[path.extname(arquivo).toLowerCase()] || "application/octet-stream";
    res.writeHead(200, Object.assign({ "Content-Type": tipo }, cabecalhosPara(regras, caminho)));
    res.end(fs.readFileSync(arquivo));
  });

  return new Promise((resolver) => {
    servidor.listen(porta, "127.0.0.1", () => {
      resolver({
        origem: `http://127.0.0.1:${servidor.address().port}`,
        regras,
        fechar: () => new Promise((ok) => servidor.close(ok)),
      });
    });
  });
}

module.exports = { iniciarServidor, cabecalhosPara, lerRegras };

if (require.main === module) {
  const raiz = process.argv[2] || path.join(__dirname, "..", "..");
  const porta = Number(process.argv[3] || 8090);
  iniciarServidor(raiz, porta).then(({ origem }) => console.log(`Servindo ${raiz} em ${origem}`));
}
