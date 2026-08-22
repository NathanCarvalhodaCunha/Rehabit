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

  // Contador de chamadas simultâneas: com várias requisições em paralelo
  // (ex.: Promise.all de dois apiGet), o loader só some quando a última
  // delas terminar, em vez de sumir assim que a primeira resolve.
  var pendentes = 0;

  var RehabitLoader = {
    show: function (text) {
      pendentes++;
      var overlay = get(text);
      overlay.classList.remove("is-hidden");
      return overlay;
    },
    hide: function () {
      pendentes = Math.max(0, pendentes - 1);
      if (pendentes > 0) return;
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
