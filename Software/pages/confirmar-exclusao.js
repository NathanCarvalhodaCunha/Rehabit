/* Rehabit — diálogo de confirmação das exclusões (de profissional e de conta).

   Substitui o window.confirm: uma exclusão precisa mostrar o que vai levar
   junto e, às vezes, pedir algo antes (para quem vão os pacientes, a senha).
   Se a API recusar, o diálogo continua aberto com a mensagem dentro dele —
   um toast sumiria enquanto a pessoa ainda está lendo o que errou.

   Uso:
     RehabitConfirmarExclusao.abrir({
       titulo: "Excluir Ana Souza",
       corpo: "<p>...</p>",             // HTML já escapado por quem chama
       rotuloConfirmar: "Excluir",      // null: só "Voltar" (nada a confirmar)
       podeConfirmar: function (caixa) { return true; },  // reavaliado a cada digitação
       aoConfirmar: function (caixa) { return promessa; }
     });
   A promessa resolvida fecha o diálogo; rejeitada, mostra a mensagem do erro. */
window.RehabitConfirmarExclusao = (function () {
  "use strict";

  var overlay = null;
  var contexto = null;

  function escapar(valor) {
    var div = document.createElement("div");
    div.textContent = valor == null ? "" : String(valor);
    return div.innerHTML;
  }

  function montar() {
    overlay = document.createElement("div");
    overlay.className = "dialogo-overlay is-hidden";
    overlay.innerHTML =
      '<form class="dialogo-caixa" role="alertdialog" aria-modal="true" aria-labelledby="dlg-exclusao-titulo" novalidate>' +
      '<h3 id="dlg-exclusao-titulo"></h3>' +
      '<div data-corpo></div>' +
      '<p class="dialogo-erro" data-erro hidden></p>' +
      '<div class="dialogo-acoes">' +
      '<button type="button" class="btn-outline" data-cancelar>Voltar</button>' +
      '<button type="submit" class="btn-perigo" data-confirmar></button>' +
      "</div>" +
      "</form>";
    document.body.appendChild(overlay);

    overlay.addEventListener("mousedown", function (e) {
      if (e.target === overlay) fechar();
    });
    overlay.querySelector("[data-cancelar]").addEventListener("click", fechar);
    overlay.querySelector("form").addEventListener("submit", confirmar);
    overlay.querySelector("form").addEventListener("input", atualizarBotao);
    overlay.querySelector("form").addEventListener("change", atualizarBotao);
    document.addEventListener("keydown", function (e) {
      if (e.key === "Escape" && overlay && !overlay.classList.contains("is-hidden")) fechar();
    });
  }

  function caixa() {
    return overlay.querySelector(".dialogo-caixa");
  }

  function mostrarErro(mensagem) {
    var erro = overlay.querySelector("[data-erro]");
    erro.textContent = mensagem;
    erro.hidden = !mensagem;
  }

  function atualizarBotao() {
    if (!contexto) return;
    var botao = overlay.querySelector("[data-confirmar]");
    botao.disabled = contexto.podeConfirmar ? !contexto.podeConfirmar(caixa()) : false;
    // Mexer num campo depois de um erro significa que a pessoa está
    // corrigindo: a mensagem antiga só atrapalharia.
    mostrarErro("");
  }

  function abrir(opcoes) {
    if (!overlay) montar();
    contexto = opcoes;

    overlay.querySelector("h3").textContent = opcoes.titulo;
    overlay.querySelector("[data-corpo]").innerHTML = opcoes.corpo;
    var botao = overlay.querySelector("[data-confirmar]");
    botao.hidden = !opcoes.rotuloConfirmar;
    botao.textContent = opcoes.rotuloConfirmar || "";
    mostrarErro("");
    atualizarBotao();

    overlay.classList.remove("is-hidden");
    document.body.style.overflow = "hidden";
    var primeiroCampo = overlay.querySelector("[data-corpo] input, [data-corpo] select");
    (primeiroCampo || overlay.querySelector("[data-cancelar]")).focus();
  }

  function fechar() {
    if (!overlay) return;
    overlay.classList.add("is-hidden");
    document.body.style.overflow = "";
    contexto = null;
  }

  function confirmar(e) {
    e.preventDefault();
    if (!contexto || !contexto.aoConfirmar) return;
    var botao = overlay.querySelector("[data-confirmar]");
    if (botao.disabled) return;

    var rotulo = botao.textContent;
    botao.disabled = true;
    botao.textContent = "Excluindo…";
    mostrarErro("");

    Promise.resolve(contexto.aoConfirmar(caixa()))
      .then(function () {
        fechar();
      })
      .catch(function (err) {
        mostrarErro(err && err.message ? err.message : "Não foi possível excluir.");
      })
      .finally(function () {
        botao.textContent = rotulo;
        botao.disabled = false;
      });
  }

  return { abrir: abrir, fechar: fechar, escapar: escapar };
})();
