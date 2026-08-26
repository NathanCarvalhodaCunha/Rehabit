(function carregarAgenda() {
  const listaEl = document.querySelector(".agenda-list");
  const form = document.getElementById("agendamentoForm");
  if (!listaEl || !form) return;

  const sessao = getSessao();
  if (!sessao || sessao.tipo !== "FISIOTERAPEUTA") return;

  const selectPaciente = document.getElementById("ag-paciente");
  const campoData = document.getElementById("ag-data");
  const tituloLista = document.querySelector("[data-titulo-lista]");
  const containerCalendario = document.querySelector("[data-calendario]");

  // Fonte única da verdade: a lista completa vinda da API. O calendário e a
  // lista visível são só recortes dela.
  let agendamentos = [];
  let calendario = null;

  function formatarDataCurta(dataIso) {
    const [ano, mes, dia] = dataIso.split("-");
    return `${dia}/${mes}/${ano}`;
  }

  function formatarHoraCurta(horaIso) {
    return horaIso.slice(0, 5);
  }

  const SELOS_PRESENCA = {
    REALIZADA: '<span class="badge-presenca realizada">Compareceu</span>',
    FALTOU: '<span class="badge-presenca faltou">Faltou</span>',
    REMARCADA: '<span class="badge-presenca remarcada">Remarcou</span>',
  };

  function iconeAgenda() {
    return (
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' +
      '<rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/>' +
      '<line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>'
    );
  }

  function renderizar() {
    const diaSelecionado = calendario ? calendario.diaSelecionado() : null;
    const visiveis = diaSelecionado
      ? agendamentos.filter((a) => a.data === diaSelecionado)
      : agendamentos;

    if (tituloLista) {
      tituloLista.textContent = diaSelecionado
        ? `Consultas de ${formatarDataCurta(diaSelecionado)}`
        : "Próximos agendamentos";
    }

    if (!visiveis.length) {
      listaEl.innerHTML = `<li style="padding:16px;color:var(--ink-muted);">${
        diaSelecionado ? "Nenhuma consulta neste dia." : "Nenhum agendamento futuro."
      }</li>`;
      return;
    }

    listaEl.innerHTML = visiveis
      .map(
        (a) => `
      <li class="agenda-item" data-id="${a.id}">
        <div class="agenda-when">${formatarDataCurta(a.data)}<br/><span class="hora">${formatarHoraCurta(a.hora)}</span></div>
        <div>
          <div class="agenda-patient">${a.nomePaciente || "Paciente"}${SELOS_PRESENCA[a.status] || ""}</div>
          ${a.observacao ? `<div class="agenda-obs">${a.observacao}</div>` : ""}
          <div class="agenda-presenca">
            <button type="button" data-presenca="REALIZADA" data-id="${a.id}">Compareceu</button>
            <button type="button" data-presenca="FALTOU" data-id="${a.id}">Faltou</button>
            <button type="button" data-presenca="REMARCADA" data-id="${a.id}">Remarcou</button>
          </div>
          <a class="agenda-export" href="${RehabitCalendario.linkGoogle(a)}" target="_blank" rel="noopener">
            ${iconeAgenda()} Adicionar ao Google Agenda
          </a>
        </div>
        <button type="button" class="agenda-cancel" data-id="${a.id}">Cancelar</button>
      </li>`
      )
      .join("");
    if (typeof RehabitAnim !== "undefined") RehabitAnim.staggerList(listaEl);
  }

  function carregarLista() {
    return apiGet(`/agendamentos?idFisioterapeuta=${sessao.id}`)
      .then((dados) => {
        agendamentos = dados;
        if (calendario) calendario.marcarDias(agendamentos);
        renderizar();
      })
      .catch((err) => {
        listaEl.innerHTML = `<li style="padding:16px;color:var(--ink-muted);">${err.message}</li>`;
      });
  }

  if (containerCalendario && typeof RehabitCalendario !== "undefined") {
    calendario = RehabitCalendario.criar(containerCalendario, (dataIso) => {
      // Clicar num dia já adianta a data do formulário — o caso comum é
      // "quero marcar neste dia".
      if (dataIso && campoData) campoData.value = dataIso;
      renderizar();
    });
  }

  const botaoExportar = document.querySelector('[data-action="exportar-ics"]');
  if (botaoExportar) {
    botaoExportar.addEventListener("click", () => RehabitCalendario.baixarIcs(agendamentos));
  }

  apiGet(`/pacientes?idFisioterapeuta=${sessao.id}`)
    .then((pacientes) => {
      selectPaciente.innerHTML =
        '<option value="">Selecione um paciente</option>' +
        pacientes.map((p) => `<option value="${p.id}">${p.nome}</option>`).join("");
    })
    .catch((err) => RehabitToast.erro(err.message));

  carregarLista();

  listaEl.addEventListener("click", (e) => {
    const presenca = e.target.closest("[data-presenca]");
    if (presenca) {
      const grupo = presenca.parentElement;
      grupo.querySelectorAll("button").forEach((b) => (b.disabled = true));
      apiPut(`/agendamentos/${presenca.dataset.id}/status`, { status: presenca.dataset.presenca })
        .then(() => {
          RehabitToast.sucesso("Presença registrada.");
          carregarLista();
        })
        .catch((err) => {
          RehabitToast.erro(err.message);
          grupo.querySelectorAll("button").forEach((b) => (b.disabled = false));
        });
      return;
    }

    const btn = e.target.closest(".agenda-cancel[data-id]");
    if (!btn) return;
    btn.disabled = true;
    apiDelete(`/agendamentos/${btn.dataset.id}`)
      .then(() => {
        RehabitToast.sucesso("Agendamento cancelado.");
        carregarLista();
      })
      .catch((err) => {
        RehabitToast.erro(err.message);
        btn.disabled = false;
      });
  });

  form.addEventListener("submit", async (e) => {
    e.preventDefault();

    const idPaciente = selectPaciente.value;
    const data = campoData.value;
    const hora = document.getElementById("ag-hora").value;
    const observacao = document.getElementById("ag-obs").value.trim();

    if (!idPaciente || !data || !hora) {
      RehabitToast.erro("Selecione o paciente, a data e o horário.");
      return;
    }

    const submitBtn = form.querySelector(".btn-primary");
    submitBtn.disabled = true;
    const textoOriginal = submitBtn.textContent;
    submitBtn.textContent = "Agendando...";

    try {
      await apiPost("/agendamentos", {
        idPaciente: Number(idPaciente),
        data,
        hora,
        observacao: observacao || null,
      });
      RehabitToast.sucesso("Agendamento criado com sucesso.");
      form.reset();
      await carregarLista();
    } catch (err) {
      RehabitToast.erro(err.message);
    } finally {
      submitBtn.disabled = false;
      submitBtn.textContent = textoOriginal;
    }
  });
})();
