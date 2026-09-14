/* Rehabit — controle do loader
   API global:
     RehabitLoader.show("Carregando")  -> exibe (cria o overlay se não existir)
     RehabitLoader.hide()              -> esconde
     RehabitLoader.during(promise)     -> exibe enquanto a promise roda
   Marcação opcional já no HTML: <div class="rh-loader" data-rh-loader> ... </div>
   Atributos no <body> (opcional):
     data-rh-loader-dark="true"        -> força tema escuro
     data-rh-loader-icon="assets/..."  -> caminho do ícone
*/
(function () {
  "use strict";

  var OVERLAY_SELECTOR = "[data-rh-loader]";

  function isDark() {
    if (document.body.dataset.rhLoaderDark === "true") return true;
    // heurística: telas "-escuro.html" ou body com classe dark
    return (
      document.body.classList.contains("dark") ||
      /-escuro\.html/i.test(location.pathname)
    );
  }

  function iconPath() {
    if (document.body.dataset.rhLoaderIcon) {
      return document.body.dataset.rhLoaderIcon;
    }
    return isDark()
      ? "assets/rehabit-icon-dark.png"
      : "assets/rehabit-icon.png";
  }

  function build(text) {
    var overlay = document.createElement("div");
    overlay.className = "rh-loader" + (isDark() ? " rh-loader--dark" : "");
    overlay.setAttribute("data-rh-loader", "");
    overlay.setAttribute("role", "status");
    overlay.setAttribute("aria-live", "polite");
    overlay.innerHTML =
      '<div class="rh-loader__box">' +
      '<div class="rh-loader__ring">' +
      '<img class="rh-loader__icon" src="' +
      iconPath() +
      '" alt="">' +
      "</div>" +
      '<p class="rh-loader__text">' +
      (text || "Carregando") +
      '<span class="rh-dots"></span></p>' +
      "</div>";
    document.body.appendChild(overlay);
    return overlay;
  }

  function get(text) {
    var overlay = document.querySelector(OVERLAY_SELECTOR);
    if (!overlay) overlay = build(text);
    else if (text) {
      var label = overlay.querySelector(".rh-loader__text");
      if (label) {
        label.innerHTML = text + '<span class="rh-dots"></span>';
      }
    }
    return overlay;
  }

  // --- Aviso de espera longa -----------------------------------------------
  // A API roda no plano gratuito do Render, que derruba a instância depois de
  // ~15 min sem tráfego. A chamada que a acorda passa de dois minutos (146 s
  // medidos, contra 0,48 s com ela quente) e quem quase sempre cai nisso é o
  // aparelho novo: nos já usados a sessão está no localStorage e o login, a
  // única chamada que a tela de entrada faz, nunca acontece. Um loader parado
  // todo esse tempo sem dizer nada parece travado, e a pessoa fecha a aba
  // justo quando faltavam segundos.
  //
  // O aviso pode ser automático porque toda espera longa deste app é espera
  // de API: passou do tempo em que uma resposta normal já teria voltado, é
  // religamento de instância e não outra coisa.
  var AVISO_MS = 5000;
  var AVISO_LONGO_MS = 25000;
  var AVISO = "O servidor hiberna quando fica sem uso, e está acordando agora.";
  var AVISO_LONGO =
    "Isso acontece só no primeiro acesso depois de um tempo parado e pode " +
    "levar até 2 minutos. Pode deixar a página aberta.";

  var temporizadores = [];

  function escreverAviso(texto) {
    var overlay = document.querySelector(OVERLAY_SELECTOR);
    if (!overlay) return;
    var box = overlay.querySelector(".rh-loader__box");
    if (!box) return;
    var aviso = overlay.querySelector(".rh-loader__hint");
    if (!aviso) {
      aviso = document.createElement("p");
      aviso.className = "rh-loader__hint";
      box.appendChild(aviso);
    }
    aviso.textContent = texto;
  }

  function agendarAvisos() {
    temporizadores.push(
      setTimeout(function () {
        escreverAviso(AVISO);
      }, AVISO_MS)
    );
    temporizadores.push(
      setTimeout(function () {
        escreverAviso(AVISO_LONGO);
      }, AVISO_LONGO_MS)
    );
  }

  function limparAvisos() {
    temporizadores.forEach(clearTimeout);
    temporizadores = [];
    var aviso = document.querySelector(".rh-loader__hint");
    if (aviso && aviso.parentNode) aviso.parentNode.removeChild(aviso);
  }

  // Contador de chamadas simultâneas: com várias requisições em paralelo
  // (ex.: Promise.all de dois apiGet), o loader só some quando a última
  // delas terminar, em vez de sumir assim que a primeira resolve.
  var pendentes = 0;

  var RehabitLoader = {
    show: function (text) {
      pendentes++;
      var overlay = get(text);
      overlay.classList.remove("is-hidden");
      // Só o primeiro show agenda o aviso: com requisições em paralelo, o
      // relógio que interessa é o da espera inteira, não o da última chamada
      // a entrar na fila.
      if (pendentes === 1) agendarAvisos();
      return overlay;
    },
    hide: function () {
      pendentes = Math.max(0, pendentes - 1);
      if (pendentes > 0) return;
      limparAvisos();
      var overlay = document.querySelector(OVERLAY_SELECTOR);
      if (overlay) overlay.classList.add("is-hidden");
    },
    during: function (promise, text) {
      this.show(text);
      var self = this;
      return Promise.resolve(promise).finally(function () {
        self.hide();
      });
    },
  };

  window.RehabitLoader = RehabitLoader;

  // Se o overlay já existe no HTML, esconde automaticamente quando a página carregar.
  window.addEventListener("load", function () {
    var overlay = document.querySelector(OVERLAY_SELECTOR);
    if (overlay && overlay.dataset.rhLoaderAuto !== "false") {
      setTimeout(function () {
        overlay.classList.add("is-hidden");
      }, 600);
    }
  });
})();
