// Bibliotecas de CDN para os testes, sem depender do jsDelivr.
//
// O jsDelivr serve /npm/<pacote>@<versão>/<caminho> como o próprio arquivo do
// pacote publicado no npm. Então o tarball do registro do npm é a fonte
// confiável para (1) conferir o hash do integrity="" das páginas e (2) entregar
// ao navegador, no teste, exatamente os bytes que a CDN entregaria.
//
// O tarball é conferido contra o dist.integrity (sha512) do registro antes de
// ser usado, e fica guardado em .cache/ para as próximas execuções.
"use strict";

const crypto = require("crypto");
const fs = require("fs");
const https = require("https");
const path = require("path");
const zlib = require("zlib");

const CACHE = path.join(__dirname, ".cache");
const REGISTRO = "https://registry.npmjs.org";

function baixar(url) {
  return new Promise((resolver, rejeitar) => {
    https
      .get(url, (res) => {
        if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
          res.resume();
          baixar(new URL(res.headers.location, url).href).then(resolver, rejeitar);
          return;
        }
        if (res.statusCode !== 200) {
          res.resume();
          rejeitar(new Error(`HTTP ${res.statusCode} em ${url}`));
          return;
        }
        const partes = [];
        res.on("data", (p) => partes.push(p));
        res.on("end", () => resolver(Buffer.concat(partes)));
      })
      .on("error", rejeitar);
  });
}

function hash(algoritmo, conteudo) {
  return `${algoritmo}-${crypto.createHash(algoritmo).update(conteudo).digest("base64")}`;
}

const metadados = new Map();
async function metadadosDoPacote(pacote) {
  if (!metadados.has(pacote)) {
    const arquivo = path.join(CACHE, `${pacote.replace("/", "__")}.json`);
    let json;
    if (fs.existsSync(arquivo)) {
      json = JSON.parse(fs.readFileSync(arquivo, "utf8"));
    } else {
      json = JSON.parse((await baixar(`${REGISTRO}/${pacote}`)).toString("utf8"));
      fs.mkdirSync(CACHE, { recursive: true });
      fs.writeFileSync(arquivo, JSON.stringify(json));
    }
    metadados.set(pacote, json);
  }
  return metadados.get(pacote);
}

/** Lê um arquivo de dentro de um .tar (o formato do tarball do npm, já sem gzip). */
function extrairDoTar(tar, caminhoNoPacote) {
  const texto = (b, ini, tam) => b.subarray(ini, ini + tam).toString("utf8").replace(/\0.*$/s, "");
  let pos = 0;
  while (pos + 512 <= tar.length) {
    const cabecalho = tar.subarray(pos, pos + 512);
    if (cabecalho.every((b) => b === 0)) break;
    const nome = texto(cabecalho, 0, 100);
    const prefixo = texto(cabecalho, 345, 155);
    const tamanho = parseInt(texto(cabecalho, 124, 12).trim() || "0", 8);
    const tipo = String.fromCharCode(cabecalho[156]);
    const completo = prefixo ? `${prefixo}/${nome}` : nome;
    pos += 512;
    // A pasta de cima do tarball costuma ser "package/", mas não é regra.
    if ((tipo === "0" || tipo === "\0") && completo.replace(/^[^/]+\//, "") === caminhoNoPacote) {
      return Buffer.from(tar.subarray(pos, pos + tamanho));
    }
    pos += Math.ceil(tamanho / 512) * 512;
  }
  return null;
}

const tarballs = new Map();
async function tarballDe(pacote, versao) {
  const chave = `${pacote}@${versao}`;
  if (!tarballs.has(chave)) {
    const meta = await metadadosDoPacote(pacote);
    const dist = meta.versions[versao] && meta.versions[versao].dist;
    if (!dist) throw new Error(`${chave} não existe no registro do npm`);
    const arquivo = path.join(CACHE, `${pacote.replace("/", "__")}-${versao}.tgz`);
    let tgz = fs.existsSync(arquivo) ? fs.readFileSync(arquivo) : await baixar(dist.tarball);
    if (hash("sha512", tgz) !== dist.integrity) throw new Error(`tarball de ${chave} não confere com o registro`);
    fs.mkdirSync(CACHE, { recursive: true });
    fs.writeFileSync(arquivo, tgz);
    tarballs.set(chave, zlib.gunzipSync(tgz));
  }
  return tarballs.get(chave);
}

/**
 * Separa uma URL do jsDelivr em pacote, versão e caminho. Versão ou caminho
 * ausentes (como no código antigo, que pedia "toastify-js" sem nada) ficam
 * null: quem decide o que servir nesse caso é o jsDelivr, não o pacote.
 */
function partesDaUrl(url) {
  const m = /^https:\/\/cdn\.jsdelivr\.net\/npm\/((?:@[^/@]+\/)?[^/@]+)(?:@([^/]+))?(?:\/(.*))?$/.exec(url);
  if (!m) return null;
  return { pacote: m[1], versao: m[2] || null, caminho: m[3] || null };
}

/** Bytes do arquivo exatamente como estão no pacote publicado (ou null). */
async function arquivoExato(pacote, versao, caminho) {
  return extrairDoTar(await tarballDe(pacote, versao), caminho);
}

/**
 * O que entregar ao navegador para uma URL do jsDelivr. Para URL completa é o
 * arquivo do pacote. Para as incompletas do código antigo, imita o jsDelivr:
 * sem versão, a mais recente; sem caminho, o "main" do pacote; ".min.x" que o
 * pacote não tem, o arquivo sem ".min" (o jsDelivr minifica na hora — não é o
 * mesmo byte a byte, mas serve para a página funcionar no teste).
 */
async function conteudoParaUrl(url) {
  const partes = partesDaUrl(url);
  if (!partes) return null;
  const meta = await metadadosDoPacote(partes.pacote);
  const versao = partes.versao || meta["dist-tags"].latest;
  let caminho = partes.caminho;
  if (!caminho) {
    const v = meta.versions[versao];
    caminho = String(v.jsdelivr || v.browser || v.main || "index.js").replace(/^\.\//, "");
  }
  const exato = await arquivoExato(partes.pacote, versao, caminho);
  if (exato) return exato;
  return arquivoExato(partes.pacote, versao, caminho.replace(/\.min\.(js|css)$/, ".$1"));
}

module.exports = { partesDaUrl, arquivoExato, conteudoParaUrl, hash };
