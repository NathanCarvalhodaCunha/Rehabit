// Rehabit — página inicial (index.html). Tudo aqui é enfeite: sem JavaScript
// a página continua inteira, os links das telas abrem a imagem e a simulação
// fica parada no primeiro quadro.

// ---- Tema claro / escuro ------------------------------------------------
// O script do <head> já pôs data-tema no <html> antes da primeira pintura.
// Aqui ficam o botão de tema e o que o CSS sozinho não troca: as imagens com
// versão escura, a cor da barra do navegador e os links do login (o sistema
// tem páginas -escuro separadas, então quem está no escuro entra nelas).
const CHAVE_TEMA = "rehabit_tema";
const raizDoc = document.documentElement;

function temaAtual() {
  return raizDoc.getAttribute("data-tema") === "escuro" ? "escuro" : "claro";
}

function aplicarTema(tema) {
  const escuro = tema === "escuro";
  raizDoc.setAttribute("data-tema", tema);

  // As fontes escuras das imagens nascem com media="(prefers-color-scheme:
  // dark)" (vale sem JavaScript); data-tema-escuro guarda a condição sem o
  // tema, que passa a valer quando o tema escolhido é o escuro.
  document.querySelectorAll("source[data-tema-escuro]").forEach(function (fonte) {
    const media = escuro ? fonte.getAttribute("data-tema-escuro") : "not all";
    if (fonte.getAttribute("media") !== media) fonte.setAttribute("media", media);
  });

  document.querySelectorAll('meta[name="theme-color"]').forEach(function (meta) {
    meta.setAttribute("content", escuro ? "#060d22" : "#ffffff");
  });

  document.querySelectorAll('a[href^="Login/"]').forEach(function (a) {
    const claro = a.getAttribute("href").replace(/-escuro\.html/, ".html");
    a.setAttribute("href", escuro ? claro.replace(/\.html(?=$|[?#])/, "-escuro.html") : claro);
  });

  // O botão mostra para onde o clique leva: no claro, lua e "Tema escuro";
  // no escuro, sol e "Tema claro".
  document.querySelectorAll("[data-tema-botao]").forEach(function (botao) {
    const destino = escuro ? "claro" : "escuro";
    const icone = botao.querySelector("use[data-icone-tema]");
    if (icone) icone.setAttribute("href", escuro ? "#i-sol" : "#i-lua");
    const rotulo = botao.querySelector("[data-rotulo-tema]");
    if (rotulo) rotulo.textContent = "Tema " + destino;
    if (botao.hasAttribute("aria-label")) {
      botao.setAttribute("aria-label", "Mudar para o tema " + destino);
      botao.setAttribute("title", "Mudar para o tema " + destino);
    }
  });
}

function temaSalvo() {
  try {
    const tema = localStorage.getItem(CHAVE_TEMA);
    return tema === "claro" || tema === "escuro" ? tema : null;
  } catch (e) {
    return null;
  }
}

aplicarTema(temaAtual());

document.querySelectorAll("[data-tema-botao]").forEach(function (botao) {
  botao.addEventListener("click", function () {
    const novo = temaAtual() === "escuro" ? "claro" : "escuro";
    try { localStorage.setItem(CHAVE_TEMA, novo); } catch (e) {}
    aplicarTema(novo);
  });
});

// Sem escolha salva, a página acompanha o aparelho se ele trocar de tema.
if (window.matchMedia) {
  const midiaEscura = matchMedia("(prefers-color-scheme: dark)");
  const aoMudar = function (e) { if (!temaSalvo()) aplicarTema(e.matches ? "escuro" : "claro"); };
  if (midiaEscura.addEventListener) midiaEscura.addEventListener("change", aoMudar);
}

// ---- Menu: destaca a seção que está na tela ------------------------------
// Como a barra lateral do sistema marca a página atual, aqui o item da seção
// visível fica azul (na barra lateral e no menu inferior do celular).
(function menuAtivo() {
  const secoes = Array.from(document.querySelectorAll("main .secao[id]"));
  const ids = secoes.map(function (s) { return s.id; });
  const lateral = Array.from(document.querySelectorAll('.nav a[href^="#"]'));
  const inferior = Array.from(document.querySelectorAll('.mobile-bottomnav a[href^="#"]'));
  if (!secoes.length || !("IntersectionObserver" in window)) return;

  function ativar(a, ativo) {
    a.classList.toggle("active", ativo);
    if (ativo) a.setAttribute("aria-current", "true");
    else a.removeAttribute("aria-current");
  }

  function marcar(id) {
    // Barra lateral: tem um item para cada seção.
    lateral.forEach(function (a) { ativar(a, a.getAttribute("href") === "#" + id); });
    // Menu inferior: só 5 itens. Seção sem item próprio (Como funciona,
    // Goniômetro, Documentação) acende o último item que vem antes dela.
    const atual = ids.indexOf(id);
    let escolhido = null;
    inferior.forEach(function (a) {
      const posicao = ids.indexOf(a.getAttribute("href").slice(1));
      if (posicao !== -1 && posicao <= atual) escolhido = a;
    });
    inferior.forEach(function (a) { ativar(a, a === escolhido); });
  }

  // A seção "atual" é a que cruza uma faixa fina perto do topo da tela.
  const observador = new IntersectionObserver(function (entradas) {
    entradas.forEach(function (entrada) {
      if (entrada.isIntersecting) marcar(entrada.target.id);
    });
  }, { rootMargin: "-20% 0px -75% 0px" });
  secoes.forEach(function (secao) { observador.observe(secao); });

  // A última seção é curta e às vezes nunca chega à faixa: no fim da página,
  // ela é a atual.
  window.addEventListener("scroll", function () {
    const noFim = window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 4;
    if (noFim) marcar(secoes[secoes.length - 1].id);
  }, { passive: true });
})();

// ---- Telas ampliáveis --------------------------------------------------
(function telasAmpliaveis() {
  const dialogo = document.querySelector(".ampliada");
  if (!dialogo || typeof dialogo.showModal !== "function") return;
  const imagem = dialogo.querySelector("img");
  const legenda = dialogo.querySelector(".ampliada-legenda");

  // Título e descrição do cartão, sem os links que estejam no cabeçalho.
  function textoDaLegenda(link, miniatura) {
    const cabeca = link.closest("figure") && link.closest("figure").querySelector("figcaption");
    if (!cabeca) return miniatura.alt;
    const partes = Array.from(cabeca.children)
      .filter(function (el) { return el.tagName !== "A"; })
      .map(function (el) { return el.textContent.trim(); })
      .filter(Boolean);
    return partes.length ? partes.join(" — ") : miniatura.alt;
  }

  document.querySelectorAll("a.ampliar").forEach(function (link) {
    link.addEventListener("click", function (e) {
      e.preventDefault();
      const miniatura = link.querySelector("img");
      const textoLegenda = textoDaLegenda(link, miniatura);
      imagem.src = (temaAtual() === "escuro" && link.dataset.escuro) || link.getAttribute("href");
      imagem.alt = miniatura.alt;
      legenda.textContent = textoLegenda;
      dialogo.showModal();
    });
  });

  // Clique fora da imagem (no fundo escurecido) também fecha.
  dialogo.addEventListener("click", function (e) {
    if (e.target === dialogo) dialogo.close();
  });
  dialogo.addEventListener("close", function () {
    imagem.removeAttribute("src");
  });
})();

// ---- Simulação da tela Dispositivo ---------------------------------------
// Reproduz o que a tela mostra com o aparelho ligado: o mostrador segue o
// ângulo, e cada captura (um movimento de subir e descer o braço) guarda o
// mínimo e o máximo — a amplitude é a diferença. Os dados são inventados,
// por isso o selo "Simulação".
(function simulacao() {
  const raiz = document.querySelector("[data-simulacao]");
  if (!raiz) return;
  const el = function (nome) { return raiz.querySelector('[data-sim="' + nome + '"]'); };
  const ponteiro = raiz.querySelector(".sim-ponteiro");
  const arco = raiz.querySelector(".sim-arco");
  const curva = el("curva");
  const botao = el("pausar");

  const HZ = 10;                // leituras por segundo, como o aparelho gravando
  const JANELA = 6 * HZ;        // pontos no gráfico (6 s)
  const CICLO = 6;              // segundos de um movimento completo
  const historico = [];
  let minimo = Infinity;
  let maximo = -Infinity;
  let cicloAtual = -1;
  let n = 0;                    // leituras desde o início; o tempo é n / HZ
  let relogio = null;

  // Repouso perto de 0°, sobe até ~140°, segura, desce. O alvo varia um
  // pouco de um movimento para o outro, como num paciente de verdade.
  function suave(x) { return x * x * (3 - 2 * x); }
  function anguloEm(segundos) {
    const ciclo = Math.floor(segundos / CICLO);
    const fase = (segundos % CICLO) / CICLO;
    const alvo = 128 + 14 * Math.sin(ciclo * 1.7);
    let a;
    if (fase < 0.15) a = 0;
    else if (fase < 0.5) a = suave((fase - 0.15) / 0.35);
    else if (fase < 0.62) a = 1;
    else if (fase < 0.95) a = 1 - suave((fase - 0.62) / 0.33);
    else a = 0;
    const ruido = Math.sin(segundos * 23) * 0.6 + Math.sin(segundos * 7.3) * 0.4;
    return Math.max(0, 6 + a * (alvo - 6) + ruido);
  }

  function passo() {
    const t = n / HZ;
    const angulo = anguloEm(t);
    const ciclo = Math.floor(t / CICLO);
    if (ciclo !== cicloAtual) { cicloAtual = ciclo; minimo = Infinity; maximo = -Infinity; }
    minimo = Math.min(minimo, angulo);
    maximo = Math.max(maximo, angulo);
    historico.push(angulo);
    if (historico.length > JANELA) historico.shift();
    n++;
    desenhar(angulo);
  }

  function desenhar(angulo) {
    const limitado = Math.min(180, angulo);
    ponteiro.setAttribute("transform", "rotate(" + limitado.toFixed(1) + " 100 100)");
    arco.setAttribute("stroke-dasharray", limitado.toFixed(1) + " 180");
    el("angulo").textContent = Math.round(angulo);
    el("minimo").textContent = Math.round(minimo) + "°";
    el("maximo").textContent = Math.round(maximo) + "°";
    el("amplitude").textContent = Math.round(maximo - minimo) + "°";
    const pontos = historico.map(function (a, i) {
      const x = (i / (JANELA - 1)) * 300;
      const y = 76 - (Math.min(180, a) / 180) * 72;
      return x.toFixed(1) + "," + y.toFixed(1);
    });
    curva.setAttribute("points", pontos.join(" "));
  }

  // Primeiro quadro já com o gráfico cheio, no meio de uma subida.
  for (let i = 0; i < JANELA; i++) passo();
  while ((n / HZ) % CICLO < CICLO * 0.45) passo();

  const reduzirMovimento = window.matchMedia && matchMedia("(prefers-reduced-motion: reduce)").matches;
  let pausadoPeloUsuario = reduzirMovimento;
  let visivel = false;

  function atualizarRelogio() {
    const rodar = visivel && !pausadoPeloUsuario && !document.hidden;
    if (rodar && !relogio) relogio = setInterval(passo, 1000 / HZ);
    if (!rodar && relogio) { clearInterval(relogio); relogio = null; }
    raiz.classList.toggle("pausada", pausadoPeloUsuario);
    botao.textContent = pausadoPeloUsuario ? "Continuar" : "Pausar";
    botao.setAttribute("aria-pressed", String(pausadoPeloUsuario));
  }

  botao.addEventListener("click", function () {
    pausadoPeloUsuario = !pausadoPeloUsuario;
    atualizarRelogio();
  });
  document.addEventListener("visibilitychange", atualizarRelogio);

  // Só anima enquanto aparece na tela: fora dela é gasto de bateria à toa.
  if ("IntersectionObserver" in window) {
    new IntersectionObserver(function (entradas) {
      visivel = entradas[0].isIntersecting;
      atualizarRelogio();
    }).observe(raiz);
  } else {
    visivel = true;
    atualizarRelogio();
  }
  atualizarRelogio();
})();
