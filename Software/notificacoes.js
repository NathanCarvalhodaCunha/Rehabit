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

  function montarDropdown(notificacoes) {
    if (!notificacoes.length) {
      return '<p class="notif-empty">Nenhuma notificação por enquanto.</p>';
    }
    return notificacoes
      .map(function (n) {
        return (
          '<div class="notif-item' + (n.lida ? "" : " is-unread") + '">' +
          '<div class="msg">' + escaparHtml(n.mensagem) + "</div>" +
          '<div class="quando">' + tempoRelativo(n.criadaEm) + "</div>" +
          "</div>"
        );
      })
      .join("");
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
    dropdown.innerHTML = '<p class="notif-empty">Carregando...</p>';

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
        dropdown.innerHTML = montarDropdown(notificacoes);
        if (typeof RehabitAnim !== "undefined" && notificacoes.length) {
          RehabitAnim.staggerList(dropdown);
        }
      })
      .catch(function () {
        dropdown.innerHTML = '<p class="notif-empty">Não foi possível carregar.</p>';
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
