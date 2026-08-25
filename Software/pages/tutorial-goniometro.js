(function tutorialGoniometro() {
  const forearm = document.querySelector(".forearm-rotate");
  const angleValueEl = document.querySelector(".angle-value");
  if (!forearm || !angleValueEl) return;

  // Ângulos em graus "de tela" (0° = eixo +x, sentido horário), medidos a
  // partir do pivô do pulso. Nosso goniômetro fica preso na mão e só mede
  // um movimento: a mão sai de uma posição baixa/fechada (85°, apontando
  // para baixo) e abre para cima (-15°, acima da horizontal) — não é um
  // vaivém de flexão/extensão como num goniômetro de cotovelo genérico.
  const ANGULO_FECHADO = 85;
  const ANGULO_ABERTO = -15;
  const DURACAO_MS = 2200;
  const PAUSA_MS = 700;

  function aberturaAPartirDoAngulo(anguloTela) {
    return Math.round(ANGULO_FECHADO - anguloTela);
  }

  function aplicar(anguloTela) {
    forearm.style.transform = `rotate(${anguloTela}deg)`;
    angleValueEl.textContent = String(aberturaAPartirDoAngulo(anguloTela));
  }

  function easeInOutSine(t) {
    return -(Math.cos(Math.PI * t) - 1) / 2;
  }

  function prefersReducedMotion() {
    return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  }

  let animando = false;

  function animarSweep(onDone) {
    if (animando) return;
    animando = true;

    function tramo(de, para, aoTerminar) {
      const inicio = performance.now();
      function passo(agora) {
        const t = Math.min(1, (agora - inicio) / DURACAO_MS);
        aplicar(de + (para - de) * easeInOutSine(t));
        if (t < 1) {
          requestAnimationFrame(passo);
        } else {
          aoTerminar();
        }
      }
      requestAnimationFrame(passo);
    }

    // O movimento real do dispositivo é só de abertura (baixo → cima); o
    // retorno aqui é somente para o loop de demonstração poder repetir.
    tramo(ANGULO_FECHADO, ANGULO_ABERTO, () => {
      setTimeout(() => {
        tramo(ANGULO_ABERTO, ANGULO_FECHADO, () => {
          animando = false;
          if (onDone) onDone();
        });
      }, PAUSA_MS);
    });
  }

  function loop() {
    animarSweep(() => {
      setTimeout(loop, PAUSA_MS);
    });
  }

  if (prefersReducedMotion()) {
    aplicar((ANGULO_FECHADO + ANGULO_ABERTO) / 2);
  } else {
    loop();
  }

  const replayBtn = document.querySelector('[data-action="replay-tutorial"]');
  if (replayBtn) {
    replayBtn.addEventListener("click", () => {
      // Não interrompe um sweep já em andamento (animarSweep() ignora a
      // chamada nesse caso) — evita duas animações concorrentes disputando
      // o mesmo elemento.
      animarSweep();
    });
  }
})();
