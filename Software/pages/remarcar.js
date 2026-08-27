/* Rehabit — janela de remarcação de consulta.

   O botão "Remarcar" da agenda só trocava o selo da consulta; a data
   continuava a mesma, então quem remarcava tinha de cancelar e criar tudo de
   novo. Aqui a pessoa escolhe a nova data e o novo horário na hora, com as
   mesmas regras do agendamento (nada no passado, nada fora do expediente).

   Uso:
     RehabitRemarcar.abrir({
       agendamento: { id, data, hora, observacao, nomePaciente },
       limites: { minimoIso, horaAbertura, horaFechamento, duracao },
       aoConfirmar: function (dados) { return promessa; }
     }) */
window.RehabitRemarcar = (function () {
  "use strict";

  var overlay = null;
  var contexto = null;

  function escapar(valor) {
    var div = document.createElement("div");
    div.textContent = valor == null ? "" : String(valor);
    return div.innerHTML;
  }

  function formatarData(dataIso) {
    if (!dataIso) return "";
    var p = String(dataIso).slice(0, 10).split("-");
    return p.length === 3 ? p[2] + "/" + p[1] + "/" + p[0] : String(dataIso);
  }

  function montar() {
    overlay = document.createElement("div");
    overlay.className = "remarcar-overlay is-hidden";
    overlay.innerHTML =
      '<form class="remarcar-caixa" role="dialog" aria-modal="true" aria-label="Remarcar consulta" novalidate>' +
      '<h3>Remarcar consulta</h3>' +
      '<p class="remarcar-atual" data-atual></p>' +
      '<div class="two-col">' +
      '<div class="field"><label for="rm-data">Nova data</label><input id="rm-data" type="date" required /></div>' +
      '<div class="field"><label for="rm-hora">Novo horário</label><input id="rm-hora" type="time" required /></div>' +
      "</div>" +
      '<p class="field-hint" data-limite></p>' +
      '<div class="field"><label for="rm-obs">Observação</label>' +
      '<input id="rm-obs" type="text" placeholder="Ex: paciente pediu para adiar" /></div>' +
      '<p class="remarcar-erro" data-erro hidden></p>' +
      '<div class="remarcar-acoes">' +
      '<button type="button" class="btn-outline" data-cancelar>Voltar</button>' +
      '<button type="submit" class="btn-primary">Confirmar</button>' +
      "</div>" +
      "</form>";
    document.body.appendChild(overlay);

    overlay.addEventListener("mousedown", function (e) {
      if (e.target === overlay) fechar();
    });
    overlay.querySelector("[data-cancelar]").addEventListener("click", fechar);
    overlay.querySelector("form").addEventListener("submit", confirmar);
    document.addEventListener("keydown", function (e) {
      if (e.key === "Escape" && overlay && !overlay.classList.contains("is-hidden")) fechar();
    });

    // Mudar o dia muda o piso do horário: hoje não aceita hora que já passou.
    overlay.querySelector("#rm-data").addEventListener("change", ajustarLimiteDeHora);
  }

  function campos() {
    return {
      data: overlay.querySelector("#rm-data"),
      hora: overlay.querySelector("#rm-hora"),
      obs: overlay.querySelector("#rm-obs"),
      erro: overlay.querySelector("[data-erro]"),
      enviar: overlay.querySelector('button[type="submit"]'),
    };
  }

  function horaAgora() {
    var agora = new Date();
    return String(agora.getHours()).padStart(2, "0") + ":" + String(agora.getMinutes()).padStart(2, "0");
  }

  function ajustarLimiteDeHora() {
    var limites = (contexto && contexto.limites) || {};
    var c = campos();
    var abertura = limites.horaAbertura || "";
    var ehHoje = c.data.value && c.data.value === (limites.minimoIso || "");
    c.hora.min = ehHoje && abertura < horaAgora() ? horaAgora() : abertura;
    c.hora.max = limites.horaFechamento || "";
  }

  function mostrarErro(mensagem) {
    var c = campos();
    c.erro.textContent = mensagem;
    c.erro.hidden = !mensagem;
  }

  function confirmar(e) {
    e.preventDefault();
    if (!contexto) return;

    var c = campos();
    var data = c.data.value;
    var hora = c.hora.value;

    if (!data || !hora) {
      mostrarErro("Escolha a nova data e o novo horário.");
      return;
    }
    var problema = contexto.validar ? contexto.validar(data, hora) : null;
    if (problema) {
      mostrarErro(problema);
      return;
    }

    mostrarErro("");
    c.enviar.disabled = true;
    var textoOriginal = c.enviar.textContent;
    c.enviar.textContent = "Remarcando...";

    Promise.resolve(
      contexto.aoConfirmar({
        id: contexto.agendamento.id,
        data: data,
        hora: hora,
        observacao: c.obs.value.trim(),
      })
    )
      .then(function () {
        fechar();
      })
      .catch(function (err) {
        mostrarErro(err && err.message ? err.message : "Não foi possível remarcar.");
      })
      .finally(function () {
        c.enviar.disabled = false;
        c.enviar.textContent = textoOriginal;
      });
  }

  function fechar() {
    if (!overlay) return;
    overlay.classList.add("is-hidden");
    document.body.style.overflow = "";
    contexto = null;
  }

  function abrir(opcoes) {
    if (!opcoes || !opcoes.agendamento) return;
    if (!overlay) montar();

    contexto = opcoes;
    var limites = opcoes.limites || {};
    var agendamento = opcoes.agendamento;
    var c = campos();

    overlay.querySelector("[data-atual]").innerHTML =
      "<strong>" + escapar(agendamento.nomePaciente || "Paciente") + "</strong> · hoje marcada para " +
      escapar(formatarData(agendamento.data)) + " às " + escapar(String(agendamento.hora || "").slice(0, 5));

    overlay.querySelector("[data-limite]").textContent =
      limites.horaAbertura && limites.horaFechamento
        ? "Atendimento das " + limites.horaAbertura + " às " + limites.horaFechamento +
          (limites.duracao ? " · sessão de " + limites.duracao + " min" : "")
        : "";

    c.data.min = limites.minimoIso || "";
    // Uma consulta futura já começa com a própria data preenchida; uma que
    // ficou para trás começa vazia, para não sugerir um dia inválido.
    c.data.value = agendamento.data >= (limites.minimoIso || "") ? agendamento.data : "";
    c.hora.value = String(agendamento.hora || "").slice(0, 5);
    c.obs.value = agendamento.observacao || "";
    ajustarLimiteDeHora();
    mostrarErro("");

    overlay.classList.remove("is-hidden");
    document.body.style.overflow = "hidden";
    setTimeout(function () {
      c.data.focus();
    }, 30);
  }

  return { abrir: abrir, fechar: fechar };
})();
