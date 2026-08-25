// Rehabit — tutorial interativo no primeiro acesso.
// Roda só em instituicao.html/profissional.html (e variantes -escuro):
// destaca elementos já existentes da sidebar, sem editar as outras páginas.
// O passo ".notif-bell" depende do notificacoes.js já ter rodado e criado
// o elemento do sino — o <script> dele precisa carregar antes do tutorial.js
// nessas páginas (ordem já correta hoje; não inverter).
(function () {
  "use strict";

  function prefersReducedMotion() {
    return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  }

  function elementoVisivel(el) {
    if (!el) return false;
    var r = el.getBoundingClientRect();
    return r.width > 0 && r.height > 0;
  }

  function primeiroVisivel(seletor) {
    var els = document.querySelectorAll(seletor);
    for (var i = 0; i < els.length; i++) {
      if (elementoVisivel(els[i])) return els[i];
    }
    return null;
  }

  function passosPara(tipo) {
    var passos = [
      {
        seletor: '.nav a[href*="dispositivo"], .mobile-bottomnav a[href*="dispositivo"]',
        titulo: "Dispositivo",
        texto: "Acompanhe as medições do goniômetro aqui.",
      },
    ];
    if (tipo === "CLINICA") {
      passos.push({
        seletor: ".notif-bell",
        titulo: "Notificações",
        texto: "Aqui você vê quando um profissional seu cadastra um paciente ou registra uma sessão.",
      });
      passos.push({
        seletor: '[data-action="add-fisio"]',
        titulo: "Cadastrar profissional",
        texto: "Cadastre um profissional por aqui.",
      });
    } else {
      passos.push({
        seletor: '[data-action="add-patient"]',
        titulo: "Cadastrar paciente",
        texto: "Cadastre um paciente por aqui.",
      });
    }
    passos.push({
      seletor: '.nav a[href*="configuracoes"], .mobile-bottomnav a[href*="configuracoes"]',
      titulo: "Configurações",
      texto: "Personalize o tema e mais aqui.",
    });
    return passos;
  }

  function marcarVistoNoServidor(sessao) {
    var caminho = sessao.tipo === "CLINICA" ? "/clinicas/" : "/fisioterapeutas/";
    // Falha silenciosa proposital: o localStorage já foi atualizado, então a sessão
    // atual segue correta; se essa chamada falhar, o próximo login vai buscar o valor
    // ainda "false" no servidor e o tutorial reaparece — tradeoff aceito em vez de
    // bloquear a UI numa chamada de rede.
    fetch(API_BASE_URL + caminho + sessao.id + "/tutorial-visto", {
      method: "PUT",
      headers: cabecalhosAutenticados(),
    }).catch(function () {});
  }

  function iniciar() {
    var sessao = getSessao();
    if (!sessao || sessao.tutorialVisto) return;

    // Marca como visto imediatamente — "mostra uma vez, sempre", não
    // "mostra até completar". Evita reabrir se a página recarregar no meio.
    sessao.tutorialVisto = true;
    localStorage.setItem("rehabit_usuario", JSON.stringify(sessao));
    marcarVistoNoServidor(sessao);

    var passos = passosPara(sessao.tipo)
      .map(function (p) {
        return { el: primeiroVisivel(p.seletor), titulo: p.titulo, texto: p.texto };
      })
      .filter(function (p) {
        return p.el;
      });
    if (!passos.length) return;

    var indice = 0;

    var overlay = document.createElement("div");
    overlay.className = "tutorial-overlay";
    var spotlight = document.createElement("div");
    spotlight.className = "tutorial-spotlight";
    var tooltip = document.createElement("div");
    tooltip.className = "tutorial-tooltip";
    overlay.appendChild(spotlight);
    overlay.appendChild(tooltip);
    document.body.appendChild(overlay);

    function fechar() {
      overlay.remove();
      window.removeEventListener("resize", posicionar);
    }

    function posicionar() {
      var passo = passos[indice];
      var r = passo.el.getBoundingClientRect();
      var folga = 8;
      spotlight.style.top = r.top - folga + "px";
      spotlight.style.left = r.left - folga + "px";
      spotlight.style.width = r.width + folga * 2 + "px";
      spotlight.style.height = r.height + folga * 2 + "px";

      var ultimo = indice === passos.length - 1;
      tooltip.innerHTML =
        "<h4>" +
        passo.titulo +
        "</h4><p>" +
        passo.texto +
        '</p><div class="tutorial-actions">' +
        '<button type="button" class="tutorial-skip">Pular</button>' +
        '<button type="button" class="tutorial-next">' +
        (ultimo ? "Concluir" : "Próximo") +
        "</button></div>";

      var tooltipTop = r.bottom + 16;
      if (tooltipTop + 140 > window.innerHeight) tooltipTop = Math.max(16, r.top - 140);
      tooltip.style.top = tooltipTop + "px";
      var tooltipLeft = Math.min(Math.max(r.left, 16), window.innerWidth - 296);
      tooltip.style.left = tooltipLeft + "px";

      tooltip.querySelector(".tutorial-skip").addEventListener("click", fechar);
      tooltip.querySelector(".tutorial-next").addEventListener("click", function () {
        if (ultimo) {
          fechar();
          return;
        }
        indice++;
        posicionar();
      });
    }

    window.addEventListener("resize", posicionar);
    posicionar();

    if (typeof gsap !== "undefined" && !prefersReducedMotion()) {
      gsap.from(overlay, { opacity: 0, duration: 0.3 });
    }
  }

  document.addEventListener("DOMContentLoaded", iniciar);
})();
