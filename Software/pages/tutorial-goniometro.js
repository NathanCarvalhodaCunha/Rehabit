(function tutorialGoniometro() {
  const braco = document.querySelector(".arm-rotate");
  const angleValueEl = document.querySelector(".angle-value");
  if (!braco || !angleValueEl) return;

  const cunha = document.querySelector(".gonio-wedge");

  // O goniômetro é preso ao pulso, mas o pivô da medida é o ombro: o que se
  // lê é a abertura entre o braço e o tronco, de 0° (braço junto ao corpo)
  // até 90° (braço na horizontal, na altura do ombro). No desenho o braço
  // nasce apontando para baixo, então levantar 1° é girar -1° na tela.
  const ABERTURA_INICIAL = 0;
  const ABERTURA_FINAL = 90;
  const RAIO_CUNHA = 56;
  const DURACAO_MS = 2200;
  const PAUSA_MS = 700;

  // Setor entre a referência do tronco e a posição atual do braço — é o
  // ângulo que o profissional lê na tela do Dispositivo.
  function caminhoDaCunha(abertura) {
    const radianos = (abertura * Math.PI) / 180;
    const x = (RAIO_CUNHA * Math.sin(radianos)).toFixed(2);
    const y = (RAIO_CUNHA * Math.cos(radianos)).toFixed(2);
    return `M 0 0 L 0 ${RAIO_CUNHA} A ${RAIO_CUNHA} ${RAIO_CUNHA} 0 0 0 ${x} ${y} Z`;
  }

  function aplicar(abertura) {
    // Atributo transform (e não style.transform) para o giro acontecer em
    // torno do ombro — a origem do grupo — e não do centro do viewBox.
    braco.setAttribute("transform", `rotate(${(-abertura).toFixed(2)})`);
    angleValueEl.textContent = String(Math.round(abertura));
    if (cunha) cunha.setAttribute("d", caminhoDaCunha(abertura));
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

    // O que se mede é a subida do braço (0° → 90°); a descida aqui é só
    // para o loop de demonstração poder repetir.
    tramo(ABERTURA_INICIAL, ABERTURA_FINAL, () => {
      setTimeout(() => {
        tramo(ABERTURA_FINAL, ABERTURA_INICIAL, () => {
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
    // Sem animação, mostra o fim do movimento: é a posição que o tutorial
    // está ensinando a alcançar.
    aplicar(ABERTURA_FINAL);
  } else {
    aplicar(ABERTURA_INICIAL);
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
