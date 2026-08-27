(function carregarAgenda() {
  const listaEl = document.querySelector(".agenda-list");
  const form = document.getElementById("agendamentoForm");
  if (!listaEl || !form) return;

  const sessao = getSessao();
  if (!sessao || sessao.tipo !== "FISIOTERAPEUTA") return;

  const selectPaciente = document.getElementById("ag-paciente");
  const campoData = document.getElementById("ag-data");
  const campoHora = document.getElementById("ag-hora");
  const avisoHorario = document.querySelector("[data-limite-horario]");
  const tituloLista = document.querySelector("[data-titulo-lista]");
  const containerCalendario = document.querySelector("[data-calendario]");
  const cartaoForm = form.closest(".form-card");
  const abas = Array.from(document.querySelectorAll("[data-aba]"));
  const filtroPaciente = document.getElementById("filtro-paciente");
  const contador = document.querySelector("[data-contador]");
  const botaoRelatorio = document.querySelector('[data-action="relatorio-consultas"]');

  // Fonte única da verdade: as listas completas vindas da API. O calendário e
  // a lista visível são só recortes delas.
  const listas = { agendadas: [], realizadas: [] };
  let abaAtual = "agendadas";
  let calendario = null;

  // Janela efetiva de atendimento (a da clínica, apertada pela do profissional).
  // O padrão vale só enquanto a resposta da API não chega.
  let expediente = { horaAbertura: "08:00", horaFechamento: "18:00", duracao: 45 };

  function hojeIso() {
    const agora = new Date();
    return (
      agora.getFullYear() +
      "-" +
      String(agora.getMonth() + 1).padStart(2, "0") +
      "-" +
      String(agora.getDate()).padStart(2, "0")
    );
  }

  function agoraHm() {
    const agora = new Date();
    return String(agora.getHours()).padStart(2, "0") + ":" + String(agora.getMinutes()).padStart(2, "0");
  }

  function formatarDataCurta(dataIso) {
    const [ano, mes, dia] = dataIso.split("-");
    return `${dia}/${mes}/${ano}`;
  }

  function formatarHoraCurta(horaIso) {
    return String(horaIso || "").slice(0, 5);
  }

  function escaparHtml(texto) {
    const div = document.createElement("div");
    div.textContent = texto == null ? "" : String(texto);
    return div.innerHTML;
  }

  /** "14:30" + 45 min -> "15:15". Devolve null se passar da meia-noite. */
  function somarMinutos(hm, minutos) {
    const [h, m] = hm.split(":").map(Number);
    const total = h * 60 + m + minutos;
    if (total >= 24 * 60) return null;
    return String(Math.floor(total / 60)).padStart(2, "0") + ":" + String(total % 60).padStart(2, "0");
  }

  /**
   * As mesmas três regras que a API aplica, só que antes de sair o pedido:
   * nada no passado, nada fora do expediente e a sessão inteira tem de caber
   * no horário de atendimento. Devolve a mensagem do problema, ou null.
   */
  function validarQuando(data, hora) {
    if (!data || !hora) return "Selecione a data e o horário.";

    const hoje = hojeIso();
    if (data < hoje) return "Não é possível agendar em uma data que já passou.";
    if (data === hoje && hora < agoraHm()) return "Esse horário de hoje já passou. Escolha um horário futuro.";

    const abertura = expediente.horaAbertura;
    const fechamento = expediente.horaFechamento;
    if (!abertura || !fechamento) return null;

    const fim = somarMinutos(hora, expediente.duracao);
    if (hora < abertura || fim === null || fim > fechamento) {
      return (
        `Fora do horário de atendimento (${abertura} às ${fechamento}). ` +
        `Uma sessão de ${expediente.duracao} min precisa começar e terminar dentro desse intervalo.`
      );
    }
    return null;
  }

  /** Mantém os limites nativos dos campos de acordo com o dia escolhido. */
  function ajustarLimitesDoFormulario() {
    if (campoData) campoData.min = hojeIso();
    if (!campoHora) return;
    const ehHoje = campoData && campoData.value === hojeIso();
    const piso = expediente.horaAbertura || "";
    campoHora.min = ehHoje && piso < agoraHm() ? agoraHm() : piso;
    campoHora.max = expediente.horaFechamento || "";
  }

  const SELOS_PRESENCA = {
    REALIZADA: '<span class="badge-presenca realizada">Compareceu</span>',
    FALTOU: '<span class="badge-presenca faltou">Faltou</span>',
    REMARCADA: '<span class="badge-presenca remarcada">Remarcada</span>',
  };

  function iconeAgenda() {
    return (
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' +
      '<rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/>' +
      '<line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>'
    );
  }

  function filtradaPorPaciente(consultas) {
    const id = filtroPaciente ? filtroPaciente.value : "";
    return id ? consultas.filter((c) => String(c.idPaciente) === id) : consultas;
  }

  function visiveis() {
    const diaSelecionado = calendario ? calendario.diaSelecionado() : null;
    let lista = filtradaPorPaciente(listas[abaAtual] || []);
    if (diaSelecionado) lista = lista.filter((a) => a.data === diaSelecionado);
    return lista;
  }

  function itemHtml(a) {
    const ehHistorico = abaAtual === "realizadas";
    const remarcadaDe = a.dataOriginal
      ? `<div class="agenda-obs">Remarcada de ${formatarDataCurta(a.dataOriginal)}${
          a.horaOriginal ? " às " + formatarHoraCurta(a.horaOriginal) : ""
        }</div>`
      : "";

    return `
      <li class="agenda-item${ehHistorico ? " is-passada" : ""}" data-id="${a.id}">
        <div class="agenda-when">${formatarDataCurta(a.data)}<br/><span class="hora">${formatarHoraCurta(a.hora)}</span></div>
        <div>
          <button type="button" class="agenda-patient link-paciente" data-id-paciente="${a.idPaciente}">
            ${escaparHtml(a.nomePaciente || "Paciente")}
          </button>${SELOS_PRESENCA[a.status] || ""}
          ${a.observacao ? `<div class="agenda-obs">${escaparHtml(a.observacao)}</div>` : ""}
          ${remarcadaDe}
          <div class="agenda-presenca">
            <button type="button" data-presenca="REALIZADA" data-id="${a.id}">Compareceu</button>
            <button type="button" data-presenca="FALTOU" data-id="${a.id}">Faltou</button>
            <button type="button" data-remarcar="${a.id}">Remarcar</button>
          </div>
          ${
            ehHistorico
              ? ""
              : `<a class="agenda-export" href="${RehabitCalendario.linkGoogle(a, expediente.duracao)}" target="_blank" rel="noopener">
            ${iconeAgenda()} Adicionar ao Google Agenda
          </a>`
          }
        </div>
        <button type="button" class="agenda-cancel" data-id="${a.id}">${ehHistorico ? "Excluir" : "Cancelar"}</button>
      </li>`;
  }

  function renderizar() {
    const diaSelecionado = calendario ? calendario.diaSelecionado() : null;
    const lista = visiveis();

    if (tituloLista) {
      tituloLista.textContent = diaSelecionado
        ? `Consultas de ${formatarDataCurta(diaSelecionado)}`
        : abaAtual === "agendadas"
        ? "Próximos agendamentos"
        : "Consultas já realizadas";
    }

    if (contador) {
      const base = lista.length === 1 ? "1 consulta" : `${lista.length} consultas`;
      contador.textContent = diaSelecionado ? `${base} em ${formatarDataCurta(diaSelecionado)}` : base;
    }

    if (calendario) {
      // Ponto azul para o que está marcado, verde para o que já aconteceu.
      calendario.marcarDias(filtradaPorPaciente(listas.agendadas), filtradaPorPaciente(listas.realizadas));
    }

    if (botaoRelatorio) botaoRelatorio.disabled = !listas.realizadas.length;

    if (!lista.length) {
      listaEl.innerHTML = `<li style="padding:16px;color:var(--ink-muted);">${
        diaSelecionado
          ? "Nenhuma consulta neste dia."
          : abaAtual === "agendadas"
          ? "Nenhum agendamento futuro."
          : "Nenhuma consulta realizada ainda."
      }</li>`;
      return;
    }

    listaEl.innerHTML = lista.map(itemHtml).join("");
    if (typeof RehabitAnim !== "undefined") RehabitAnim.staggerList(listaEl);
  }

  function preencherFiltroDePacientes() {
    if (!filtroPaciente) return;
    const todas = listas.agendadas.concat(listas.realizadas);
    const porId = new Map();
    todas.forEach((c) => {
      if (c.idPaciente != null) porId.set(String(c.idPaciente), c.nomePaciente || "Paciente");
    });
    const selecionado = filtroPaciente.value;
    filtroPaciente.innerHTML =
      '<option value="">Todos os pacientes</option>' +
      [...porId.entries()]
        .sort((a, b) => a[1].localeCompare(b[1], "pt-BR"))
        .map(([id, nome]) => `<option value="${id}">${escaparHtml(nome)}</option>`)
        .join("");
    if (selecionado && porId.has(selecionado)) filtroPaciente.value = selecionado;
  }

  function carregarLista() {
    return Promise.all([
      apiGet(`/agendamentos?idFisioterapeuta=${sessao.id}`),
      apiGet(`/agendamentos/historico?idFisioterapeuta=${sessao.id}`).catch(() => []),
    ])
      .then(([agendadas, realizadas]) => {
        listas.agendadas = agendadas;
        listas.realizadas = realizadas;
        preencherFiltroDePacientes();
        renderizar();
      })
      .catch((err) => {
        listaEl.innerHTML = `<li style="padding:16px;color:var(--ink-muted);">${escaparHtml(err.message)}</li>`;
      });
  }

  function trocarAba(aba) {
    abaAtual = aba;
    abas.forEach((b) => b.classList.toggle("is-ativa", b.dataset.aba === aba));
    // O formulário é de agendamento novo; no histórico ele só atrapalharia.
    if (cartaoForm) cartaoForm.hidden = aba !== "agendadas";
    renderizar();
  }

  if (containerCalendario && typeof RehabitCalendario !== "undefined") {
    calendario = RehabitCalendario.criar(containerCalendario, (dataIso) => {
      // Clicar num dia já adianta a data do formulário — o caso comum é
      // "quero marcar neste dia". Dias que já passaram não servem para isso.
      if (dataIso && campoData && dataIso >= hojeIso()) {
        campoData.value = dataIso;
        ajustarLimitesDoFormulario();
      }
      renderizar();
    });
  }

  const botaoExportar = document.querySelector('[data-action="exportar-ics"]');
  if (botaoExportar) {
    botaoExportar.addEventListener("click", () =>
      RehabitCalendario.baixarIcs(listas.agendadas, expediente.duracao)
    );
  }

  if (botaoRelatorio) {
    botaoRelatorio.disabled = true;
    botaoRelatorio.addEventListener("click", () => {
      const consultas = filtradaPorPaciente(listas.realizadas);
      if (!consultas.length) {
        RehabitToast.info("Não há consultas passadas para incluir no relatório.");
        return;
      }
      botaoRelatorio.disabled = true;
      RehabitLoader.show("Gerando relatório");
      RehabitRelatorio.consultas({
        clinica: sessao.nomeClinica || "Rehabit",
        titulo: "Relatório de consultas passadas",
        subtitulo: sessao.nome,
        consultas,
      })
        .then(() => RehabitToast.sucesso("Relatório gerado."))
        .catch((err) => RehabitToast.erro(err.message))
        .finally(() => {
          RehabitLoader.hide();
          botaoRelatorio.disabled = false;
        });
    });
  }

  // /configuracoes traz o que este profissional gravou; /atendimento traz o
  // que vale de verdade — o horário da clínica já apertado pelo dele.
  apiGet("/configuracoes/atendimento")
    .then((cfg) => {
      expediente = {
        horaAbertura: cfg.horaAbertura ? String(cfg.horaAbertura).slice(0, 5) : "",
        horaFechamento: cfg.horaFechamento ? String(cfg.horaFechamento).slice(0, 5) : "",
        duracao: cfg.duracaoPadraoMin || 45,
      };
    })
    .catch(() => {})
    .finally(() => {
      ajustarLimitesDoFormulario();
      if (avisoHorario) {
        avisoHorario.textContent =
          expediente.horaAbertura && expediente.horaFechamento
            ? `Atendimento das ${expediente.horaAbertura} às ${expediente.horaFechamento} · sessão de ${expediente.duracao} min. ` +
              "Datas e horários que já passaram não podem ser agendados."
            : "Datas e horários que já passaram não podem ser agendados.";
      }
    });

  apiGet(`/pacientes?idFisioterapeuta=${sessao.id}`)
    .then((pacientes) => {
      selectPaciente.innerHTML =
        '<option value="">Selecione um paciente</option>' +
        pacientes.map((p) => `<option value="${p.id}">${escaparHtml(p.nome)}</option>`).join("");
    })
    .catch((err) => RehabitToast.erro(err.message));

  carregarLista();

  abas.forEach((botao) => {
    botao.addEventListener("click", () => trocarAba(botao.dataset.aba));
  });
  if (filtroPaciente) filtroPaciente.addEventListener("change", renderizar);
  if (campoData) campoData.addEventListener("change", ajustarLimitesDoFormulario);

  function agendamentoPorId(id) {
    return listas.agendadas.concat(listas.realizadas).find((a) => String(a.id) === String(id));
  }

  listaEl.addEventListener("click", (e) => {
    const paciente = e.target.closest("[data-id-paciente]");
    if (paciente) {
      RehabitPacienteResumo.abrir(paciente.dataset.idPaciente);
      return;
    }

    const remarcar = e.target.closest("[data-remarcar]");
    if (remarcar) {
      const agendamento = agendamentoPorId(remarcar.dataset.remarcar);
      if (!agendamento) return;
      RehabitRemarcar.abrir({
        agendamento,
        limites: {
          minimoIso: hojeIso(),
          horaAbertura: expediente.horaAbertura,
          horaFechamento: expediente.horaFechamento,
          duracao: expediente.duracao,
        },
        validar: validarQuando,
        aoConfirmar: (dados) =>
          apiPut(`/agendamentos/${dados.id}/remarcar`, {
            data: dados.data,
            hora: dados.hora,
            observacao: dados.observacao || null,
          }).then(() => {
            RehabitToast.sucesso("Consulta remarcada.");
            return carregarLista();
          }),
      });
      return;
    }

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
    const hora = campoHora.value;
    const observacao = document.getElementById("ag-obs").value.trim();

    if (!idPaciente) {
      RehabitToast.erro("Selecione o paciente.");
      return;
    }
    const problema = validarQuando(data, hora);
    if (problema) {
      RehabitToast.erro(problema);
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
      ajustarLimitesDoFormulario();
      await carregarLista();
    } catch (err) {
      RehabitToast.erro(err.message);
    } finally {
      submitBtn.disabled = false;
      submitBtn.textContent = textoOriginal;
    }
  });
})();
