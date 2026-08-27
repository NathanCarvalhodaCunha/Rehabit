/* Rehabit — busca global. Abre com Ctrl+K (ou ⌘K) e pelo botão da sidebar.
   Navega com as setas e Enter, fecha com Esc. */
(function () {
  "use strict";

  var ATRASO_MS = 250;
  var overlay = null;
  var campo = null;
  var listaEl = null;
  var resultados = [];
  var indice = 0;
  var timer = null;

  function escapar(texto) {
    var div = document.createElement("div");
    div.textContent = texto;
    return div.innerHTML;
  }

  /**
   * A API já devolve só o que a conta enxerga: uma clínica acha profissionais
   * e pacientes, um profissional acha apenas os próprios pacientes. O texto do
   * campo tem de dizer a mesma coisa, senão promete uma busca que não existe.
   */
  function textoDoCampo() {
    var sessao = getSessao();
    return sessao && sessao.tipo === "CLINICA"
      ? "Buscar paciente ou profissional..."
      : "Buscar paciente...";
  }

  function montar() {
    overlay = document.createElement("div");
    overlay.className = "busca-overlay is-hidden";
    overlay.innerHTML =
      '<div class="busca-caixa" role="dialog" aria-modal="true" aria-label="Busca">' +
      '<div class="busca-campo">' +
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="7"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>' +
      '<input type="text" placeholder="' + textoDoCampo() + '" aria-label="Buscar" />' +
      '<kbd>esc</kbd>' +
      "</div>" +
      '<div class="busca-resultados"></div>' +
      "</div>";
    document.body.appendChild(overlay);

    overlay.querySelector(".busca-caixa").setAttribute("aria-label", textoDoCampo().replace("...", ""));

    campo = overlay.querySelector("input");
    listaEl = overlay.querySelector(".busca-resultados");
    vazio("Digite ao menos 2 letras para buscar.");

    campo.addEventListener("input", function () {
      clearTimeout(timer);
      timer = setTimeout(buscar, ATRASO_MS);
    });
    campo.addEventListener("keydown", navegar);
    overlay.addEventListener("mousedown", function (e) {
      if (e.target === overlay) fechar();
    });
    listaEl.addEventListener("click", function (e) {
      var item = e.target.closest("[data-indice]");
      if (item) abrir(resultados[Number(item.dataset.indice)]);
    });
  }

  function vazio(mensagem) {
    listaEl.innerHTML = '<p class="busca-vazio">' + escapar(mensagem) + "</p>";
  }

  function iconeDe(tipo) {
    return tipo === "PROFISSIONAL"
      ? '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="8" r="4"/><path d="M4 21c0-4 4-7 8-7s8 3 8 7"/></svg>'
      : '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20.8 8.6a5 5 0 0 0-8.8-3 5 5 0 0 0-8.8 3c0 5 8.8 10.4 8.8 10.4s8.8-5.4 8.8-10.4Z"/></svg>';
  }

  function desenhar() {
    if (!resultados.length) {
      vazio("Nada encontrado.");
      return;
    }
    listaEl.innerHTML = resultados
      .map(function (r, i) {
        var foto = typeof urlFoto === "function" ? urlFoto(r.foto) : null;
        var avatar = foto
          ? '<span class="busca-avatar" style="background-image:url(\'' + foto + "');\"></span>"
          : '<span class="busca-avatar busca-avatar-icone">' + iconeDe(r.tipo) + "</span>";
        return (
          '<button type="button" class="busca-item' + (i === indice ? " is-ativo" : "") + '" data-indice="' + i + '">' +
          avatar +
          '<span class="busca-texto"><strong>' + escapar(r.nome) + "</strong>" +
          "<span>" + escapar(r.detalhe || "") + "</span></span>" +
          '<span class="busca-tag">' + (r.tipo === "PROFISSIONAL" ? "Profissional" : "Paciente") + "</span>" +
          "</button>"
        );
      })
      .join("");
  }

  function buscar() {
    var termo = campo.value.trim();
    if (termo.length < 2) {
      resultados = [];
      vazio("Digite ao menos 2 letras para buscar.");
      return;
    }
    // fetch direto: o overlay de carregamento em tela cheia atrapalharia
    // a digitação a cada tecla.
    fetch(API_BASE_URL + "/busca?q=" + encodeURIComponent(termo), {
      headers: cabecalhosAutenticados(),
    })
      .then(function (r) { return r.ok ? r.json() : []; })
      .then(function (dados) {
        resultados = dados;
        indice = 0;
        desenhar();
      })
      .catch(function () {
        vazio("Não foi possível buscar agora.");
      });
  }

  function navegar(e) {
    if (e.key === "Escape") {
      fechar();
      return;
    }
    if (!resultados.length) return;

    if (e.key === "ArrowDown") {
      e.preventDefault();
      indice = (indice + 1) % resultados.length;
      desenhar();
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      indice = (indice - 1 + resultados.length) % resultados.length;
      desenhar();
    } else if (e.key === "Enter") {
      e.preventDefault();
      abrir(resultados[indice]);
    }
  }

  function abrir(resultado) {
    if (!resultado) return;
    var pagina = resultado.tipo === "PROFISSIONAL" ? "perfil-profissional" : "paciente";
    window.location.href = paginaTema(pagina) + "?id=" + resultado.id;
  }

  function abrirBusca() {
    if (!overlay) montar();
    overlay.classList.remove("is-hidden");
    campo.value = "";
    resultados = [];
    vazio("Digite ao menos 2 letras para buscar.");
    campo.focus();
  }

  function fechar() {
    if (overlay) overlay.classList.add("is-hidden");
  }

  function iniciar() {
    if (!getSessao()) return;

    document.addEventListener("keydown", function (e) {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        abrirBusca();
      }
    });

    // Botão na sidebar, logo abaixo do logo, para quem não conhece o atalho.
    var logo = document.querySelector(".sidebar .logo");
    if (!logo) return;
    var botao = document.createElement("button");
    botao.type = "button";
    botao.className = "busca-abrir";
    botao.innerHTML =
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="7"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>' +
      "<span>Buscar</span><kbd>Ctrl K</kbd>";
    botao.addEventListener("click", abrirBusca);
    logo.insertAdjacentElement("afterend", botao);
  }

  document.addEventListener("DOMContentLoaded", iniciar);
})();
