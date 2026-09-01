/* Rehabit — cartão de informações do paciente.

   Abre por cima da agenda (ou de qualquer lista de consultas) quando o
   profissional clica no nome do paciente: dá o essencial na hora — contato,
   situação, meta, última sessão — sem tirar a pessoa de onde ela estava.
   Quem precisar do prontuário inteiro segue pelo botão do rodapé.

   Uso: RehabitPacienteResumo.abrir(idPaciente) */
window.RehabitPacienteResumo = (function () {
  "use strict";

  var overlay = null;
  var corpo = null;
  var idAberto = null;
  // Guarda o que já foi buscado: reabrir o mesmo paciente é comum e não
  // precisa de nova viagem à API.
  var cache = {};

  function escapar(valor) {
    var div = document.createElement("div");
    div.textContent = valor == null ? "" : String(valor);
    return div.innerHTML;
  }

  function formatarData(dataIso) {
    if (!dataIso) return null;
    var partes = String(dataIso).slice(0, 10).split("-");
    return partes.length === 3 ? partes[2] + "/" + partes[1] + "/" + partes[0] : String(dataIso);
  }

  function montar() {
    overlay = document.createElement("div");
    overlay.className = "resumo-overlay is-hidden";
    overlay.innerHTML =
      '<div class="resumo-caixa" role="dialog" aria-modal="true" aria-label="Informações do paciente">' +
      '<button type="button" class="resumo-fechar" aria-label="Fechar">' +
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>' +
      "</button>" +
      '<div class="resumo-corpo"></div>' +
      "</div>";
    document.body.appendChild(overlay);
    corpo = overlay.querySelector(".resumo-corpo");

    overlay.addEventListener("mousedown", function (e) {
      if (e.target === overlay) fechar();
    });
    overlay.querySelector(".resumo-fechar").addEventListener("click", fechar);
    document.addEventListener("keydown", function (e) {
      if (e.key === "Escape" && overlay && !overlay.classList.contains("is-hidden")) fechar();
    });
  }

  function fechar() {
    if (!overlay) return;
    overlay.classList.add("is-hidden");
    document.body.style.overflow = "";
    idAberto = null;
  }

  function linhaDado(rotulo, valor) {
    if (!valor) return "";
    return (
      '<div class="resumo-dado"><span class="k">' + escapar(rotulo) + "</span>" +
      '<span class="v">' + escapar(valor) + "</span></div>"
    );
  }

  var SELOS_TRATAMENTO = {
    Alta: '<span class="badge-status alta">Alta</span>',
    Inativo: '<span class="badge-status inativo">Inativo</span>',
  };

  function desenhar(paciente, sessoes) {
    var foto = typeof urlFoto === "function" ? urlFoto(paciente.foto) : null;
    var estiloAvatar = foto
      ? ' style="background-image:url(\'' + foto + "');background-size:cover;background-position:center;\""
      : "";

    var ultima = sessoes && sessoes.length ? sessoes[0] : null;
    var comMedicao = (sessoes || []).filter(function (s) {
      return s.amplitudeMedia != null;
    });
    var amplitudeAtual = comMedicao.length ? Number(comMedicao[0].amplitudeMedia) : null;

    var indicadores =
      '<div class="resumo-indicadores">' +
      '<div><strong>' + (sessoes ? sessoes.length : 0) + "</strong><span>Sessões</span></div>" +
      '<div><strong>' + (amplitudeAtual != null ? amplitudeAtual + "°" : "—") + "</strong><span>Amplitude</span></div>" +
      '<div><strong>' +
      (ultima && ultima.dor != null ? ultima.dor + "/10" : "—") +
      "</strong><span>Dor relatada</span></div>" +
      "</div>";

    var dados =
      linhaDado("Telefone", paciente.telefone) +
      linhaDado("E-mail", paciente.email) +
      linhaDado("Idade", paciente.idade != null ? paciente.idade + " anos" : null) +
      linhaDado("Sexo", paciente.sexo) +
      linhaDado("Início do tratamento", formatarData(paciente.dataInicioTratamento)) +
      linhaDado("Profissional", paciente.nomeFisioterapeuta) +
      linhaDado("Última sessão", ultima ? formatarData(ultima.data) : "Nenhuma ainda") +
      linhaDado(
        "Meta de amplitude",
        paciente.metaAmplitude != null
          ? Number(paciente.metaAmplitude) + "°" + (paciente.metaData ? " até " + formatarData(paciente.metaData) : "")
          : null
      );

    var queixa = paciente.queixaPrincipal
      ? '<div class="resumo-bloco"><h4>Queixa principal</h4><p>' + escapar(paciente.queixaPrincipal) + "</p></div>"
      : "";
    var contra = paciente.contraindicacoes
      ? '<div class="resumo-bloco alerta"><h4>Contraindicações</h4><p>' +
        escapar(paciente.contraindicacoes) +
        "</p></div>"
      : "";

    var destinoFicha =
      (typeof paginaTema === "function" ? paginaTema("paciente") : "./paciente.html") + "?id=" + paciente.id;

    corpo.innerHTML =
      '<header class="resumo-topo">' +
      '<div class="avatar-lg"' + estiloAvatar + " aria-hidden=\"true\"></div>" +
      "<div>" +
      "<h3>" + escapar(paciente.nome) + (SELOS_TRATAMENTO[paciente.status] || "") + "</h3>" +
      '<p class="resumo-situacao">' + escapar(paciente.situacao || "Sem situação registrada") + "</p>" +
      "</div>" +
      "</header>" +
      indicadores +
      '<div class="resumo-dados">' + (dados || '<p class="resumo-vazio">Sem dados cadastrais.</p>') + "</div>" +
      queixa +
      contra +
      '<div class="resumo-acoes">' +
      '<a class="btn-primary" href="' + destinoFicha + '">Abrir prontuário completo</a>' +
      "</div>";
  }

  function abrir(idPaciente) {
    if (!idPaciente) return;
    if (!overlay) montar();

    idAberto = String(idPaciente);
    overlay.classList.remove("is-hidden");
    document.body.style.overflow = "hidden";

    var guardado = cache[idAberto];
    if (guardado) {
      desenhar(guardado.paciente, guardado.sessoes);
      return;
    }

    corpo.innerHTML = '<p class="resumo-carregando">Carregando informações...</p>';
    var idPedido = idAberto;

    Promise.all([apiGet("/pacientes/" + idPedido), apiGet("/pacientes/" + idPedido + "/sessoes").catch(function () {
      return [];
    })])
      .then(function (resposta) {
        cache[idPedido] = { paciente: resposta[0], sessoes: resposta[1] };
        // Entre o clique e a resposta o usuário pode ter fechado ou aberto
        // outro paciente; só pinta se ainda for este o cartão na tela.
        if (idAberto === idPedido) desenhar(resposta[0], resposta[1]);
      })
      .catch(function (err) {
        if (idAberto === idPedido) {
          corpo.innerHTML = '<p class="resumo-carregando">' + escapar(err.message) + "</p>";
        }
      });
  }

  return { abrir: abrir, fechar: fechar };
})();
