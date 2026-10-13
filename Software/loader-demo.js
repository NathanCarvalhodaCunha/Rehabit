// Rehabit — botões da página de demonstração do loader (loader.html).
// Ficam aqui, e não em onclick="" no HTML, porque a CSP do site não deixa
// rodar script inline.
(function () {
  "use strict";

  var acoes = {
    "tela-cheia": function () {
      RehabitLoader.show("Carregando dados");
    },
    requisicao: function () {
      RehabitLoader.during(
        new Promise(function (resolver) {
          setTimeout(resolver, 2500);
        }),
        "Salvando"
      );
    },
  };

  document.querySelectorAll("[data-demo]").forEach(function (botao) {
    var acao = acoes[botao.getAttribute("data-demo")];
    if (acao) botao.addEventListener("click", acao);
  });
})();
