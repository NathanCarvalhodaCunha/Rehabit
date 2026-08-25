(function carregarAgenda() {
  const listaEl = document.querySelector(".agenda-list");
  const form = document.getElementById("agendamentoForm");
  if (!listaEl || !form) return;

  const sessao = getSessao();
  if (!sessao || sessao.tipo !== "FISIOTERAPEUTA") return;

  const selectPaciente = document.getElementById("ag-paciente");

  function formatarDataCurta(dataIso) {
    const [ano, mes, dia] = dataIso.split("-");
    return `${dia}/${mes}/${ano}`;
  }

  function formatarHoraCurta(horaIso) {
    return horaIso.slice(0, 5);
  }

  function carregarLista() {
    apiGet(`/agendamentos?idFisioterapeuta=${sessao.id}`)
      .then((agendamentos) => {
        if (agendamentos.length === 0) {
          listaEl.innerHTML =
            '<li style="padding:16px;color:var(--ink-muted);">Nenhum agendamento futuro.</li>';
          return;
        }
        listaEl.innerHTML = agendamentos
          .map(
            (a) => `
          <li class="agenda-item" data-id="${a.id}">
            <div class="agenda-when">${formatarDataCurta(a.data)}<br/><span class="hora">${formatarHoraCurta(a.hora)}</span></div>
            <div>
              <div class="agenda-patient">${a.nomePaciente || "Paciente"}</div>
              ${a.observacao ? `<div class="agenda-obs">${a.observacao}</div>` : ""}
            </div>
            <button type="button" class="agenda-cancel" data-id="${a.id}">Cancelar</button>
          </li>`
          )
          .join("");
        RehabitAnim.staggerList(listaEl);
      })
      .catch((err) => {
        listaEl.innerHTML = `<li style="padding:16px;color:var(--ink-muted);">${err.message}</li>`;
      });
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
    const data = document.getElementById("ag-data").value;
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
      carregarLista();
    } catch (err) {
      RehabitToast.erro(err.message);
    } finally {
      submitBtn.disabled = false;
      submitBtn.textContent = textoOriginal;
    }
  });
})();
