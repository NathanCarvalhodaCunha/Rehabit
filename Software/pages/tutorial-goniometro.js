(function tutorialGoniometro() {
  const forearm = document.querySelector(".forearm-rotate");
  const angleValueEl = document.querySelector(".angle-value");
  if (!forearm || !angleValueEl) return;

  // Ângulos em graus "de tela" (0° = eixo +x, sentido horário), medidos a
  // partir do pivô do cotovelo. 45° = braço totalmente estendido (antebraço
  // na mesma linha do braço); -100° = flexão confortável de ~145°.
  const ANGULO_ESTENDIDO = 45;
  const ANGULO_FLETIDO = -100;
  const DURACAO_MS = 2200;
  const PAUSA_MS = 700;

  function flexaoAPartirDoAngulo(anguloTela) {
    return Math.round(ANGULO_ESTENDIDO - anguloTela);
  }

  function aplicar(anguloTela) {
    forearm.style.transform = `rotate(${anguloTela}deg)`;
    angleValueEl.textContent = String(flexaoAPartirDoAngulo(anguloTela));
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

    tramo(ANGULO_ESTENDIDO, ANGULO_FLETIDO, () => {
      setTimeout(() => {
        tramo(ANGULO_FLETIDO, ANGULO_ESTENDIDO, () => {
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
    aplicar((ANGULO_ESTENDIDO + ANGULO_FLETIDO) / 2);
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
