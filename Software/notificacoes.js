// Rehabit — sino de notificações da clínica (sidebar desktop).
// Só aparece pra sessão do tipo CLINICA. Usa fetch() direto (não apiGet/apiPut
// de script.js) pra não disparar o overlay de tela cheia numa atualização
// passiva de badge.
(function () {
  "use strict";

  function escaparHtml(texto) {
    var div = document.createElement("div");
    div.textContent = texto;
    return div.innerHTML;
  }

  function tempoRelativo(isoDateTime) {
    var diffMs = Date.now() - new Date(isoDateTime).getTime();
    var minutos = Math.round(diffMs / 60000);
    if (minutos < 1) return "agora";
    if (minutos < 60) return "há " + minutos + "min";
    var horas = Math.round(minutos / 60);
    if (horas < 24) return "há " + horas + "h";
    var dias = Math.round(horas / 24);
    return "há " + dias + "d";
  }

  // Ícone por tipo, para bater o olho e saber do que se trata sem ler.
  var ICONES = {
    NOVO_PACIENTE:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="9" cy="8" r="4"/><path d="M2 21c0-3.5 3.1-6 7-6"/><line x1="18" y1="12" x2="18" y2="18"/><line x1="15" y1="15" x2="21" y2="15"/></svg>',
    NOVA_SESSAO:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 12h4l3 8 4-16 3 8h4"/></svg>',
  };

  function iconeDe(tipo) {
    return (
      ICONES[tipo] ||
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>'
    );
  }

  function montarCabecalho(naoLidas) {
    return (
      '<div class="notif-head">' +
      "<h4>Notificações</h4>" +
      (naoLidas > 0 ? '<span class="notif-novas">' + naoLidas + " nova" + (naoLidas > 1 ? "s" : "") + "</span>" : "") +
      "</div>"
    );
  }

  function montarLista(notificacoes) {
    if (!notificacoes.length) {
      return (
        '<div class="notif-vazio">' +
        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0"/></svg>' +
        "<p>Tudo em dia por aqui</p>" +
        "<span>As novidades dos seus profissionais aparecem aqui.</span>" +
        "</div>"
      );
    }
    return (
      '<div class="notif-lista">' +
      notificacoes
        .map(function (n) {
          return (
            '<div class="notif-item' + (n.lida ? "" : " is-unread") + '">' +
            '<span class="notif-icone">' + iconeDe(n.tipo) + "</span>" +
            '<span class="notif-corpo">' +
            '<span class="msg">' + escaparHtml(n.mensagem) + "</span>" +
            '<span class="quando">' + tempoRelativo(n.criadaEm) + "</span>" +
            "</span>" +
            "</div>"
          );
        })
        .join("") +
      "</div>"
    );
  }

  function montarDropdown(notificacoes, naoLidas) {
    return montarCabecalho(naoLidas) + montarLista(notificacoes);
  }

  function iniciar() {
    var sessao = getSessao();
    if (!sessao || sessao.tipo !== "CLINICA") return;

    var sidebarLogo = document.querySelector(".sidebar .logo");
    if (!sidebarLogo) return;

    var bell = document.createElement("button");
    bell.type = "button";
    bell.className = "notif-bell";
    bell.setAttribute("aria-label", "Notificações");
    bell.innerHTML =
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0"/></svg>' +
      '<span class="notif-badge is-hidden" data-notif-badge></span>';

    var dropdown = document.createElement("div");
    dropdown.className = "notif-dropdown is-hidden";
    dropdown.setAttribute("data-notif-dropdown", "");
    dropdown.innerHTML = montarCabecalho(0) + '<div class="notif-vazio"><p>Carregando...</p></div>';

    var wrap = document.createElement("div");
    wrap.style.position = "relative";
    wrap.appendChild(bell);
    wrap.appendChild(dropdown);
    sidebarLogo.insertAdjacentElement("afterend", wrap);

    var badge = bell.querySelector("[data-notif-badge]");
    var naoLidas = 0;

    fetch(API_BASE_URL + "/notificacoes?idClinica=" + sessao.id, {
      headers: { Authorization: "Bearer " + sessao.token },
    })
      .then(function (r) { return r.json(); })
      .then(function (notificacoes) {
        naoLidas = notificacoes.filter(function (n) { return !n.lida; }).length;
        if (naoLidas > 0) {
          badge.textContent = String(naoLidas);
          badge.classList.remove("is-hidden");
        }
        dropdown.innerHTML = montarDropdown(notificacoes, naoLidas);
        var lista = dropdown.querySelector(".notif-lista");
        if (typeof RehabitAnim !== "undefined" && lista) {
          RehabitAnim.staggerList(lista);
        }
      })
      .catch(function () {
        dropdown.innerHTML =
          montarCabecalho(0) +
          '<div class="notif-vazio"><p>Não foi possível carregar</p><span>Verifique sua conexão e tente de novo.</span></div>';
      });

    bell.addEventListener("click", function (e) {
      e.stopPropagation();
      var abrindo = dropdown.classList.contains("is-hidden");
      dropdown.classList.toggle("is-hidden");
      if (abrindo && naoLidas > 0) {
        badge.classList.add("is-hidden");
        naoLidas = 0;
        dropdown.querySelectorAll(".notif-item.is-unread").forEach(function (item) {
          item.classList.remove("is-unread");
        });
        var contador = dropdown.querySelector(".notif-novas");
        if (contador) contador.remove();
        fetch(API_BASE_URL + "/notificacoes/marcar-lidas", {
          method: "PUT",
          headers: {
            "Content-Type": "application/json",
            Authorization: "Bearer " + sessao.token,
          },
          body: JSON.stringify({ idClinica: sessao.id }),
        }).catch(function () {});
      }
    });

    document.addEventListener("click", function (e) {
      if (!wrap.contains(e.target)) dropdown.classList.add("is-hidden");
    });
  }

  document.addEventListener("DOMContentLoaded", iniciar);
})();
