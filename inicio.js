// Rehabit — página inicial (index.html). Tudo aqui é enfeite: sem JavaScript
// a página continua inteira, os links das telas abrem a imagem e a simulação
// fica parada no primeiro quadro.

const temaEscuro = window.matchMedia && matchMedia("(prefers-color-scheme: dark)").matches;

// O login tem uma versão escura em página separada; quem usa o aparelho no
// tema escuro entra direto nela, igual ao resto desta página.
if (temaEscuro) {
  document.querySelectorAll('a[href^="Login/"]').forEach(function (a) {
    a.setAttribute("href", a.getAttribute("href").replace(/\.html(?=$|[?#])/, "-escuro.html"));
  });
}

// ---- Telas ampliáveis --------------------------------------------------
(function telasAmpliaveis() {
  const dialogo = document.querySelector(".ampliada");
  if (!dialogo || typeof dialogo.showModal !== "function") return;
  const imagem = dialogo.querySelector("img");
  const legenda = dialogo.querySelector(".ampliada-legenda");

  document.querySelectorAll("a.ampliar").forEach(function (link) {
    link.addEventListener("click", function (e) {
      e.preventDefault();
      const miniatura = link.querySelector("img");
      const textoLegenda = link.closest("figure")?.querySelector("figcaption")?.textContent || miniatura.alt;
      imagem.src = (temaEscuro && link.dataset.escuro) || link.getAttribute("href");
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
