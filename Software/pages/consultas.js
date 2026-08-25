/* Rehabit — tela de Consultas da clínica: o que já foi realizado e o que
   está agendado, somando todos os profissionais da instituição. */
(function carregarConsultas() {
  const lista = document.querySelector("[data-consultas]");
  if (!lista) return;

  const sessao = getSessao();
  if (!sessao || sessao.tipo !== "CLINICA") return;

  const abas = Array.from(document.querySelectorAll("[data-aba]"));
  const filtroProfissional = document.getElementById("filtro-profissional");
  const contador = document.querySelector("[data-contador]");
  const containerCalendario = document.querySelector("[data-calendario]");
  const resumoEl = document.querySelector("[data-resumo-mes]");

  // Cada aba é carregada uma vez e guardada aqui, para alternar sem rede.
  const cache = { agendadas: null, realizadas: null };
  let abaAtual = "agendadas";
  let calendario = null;

  function formatarData(dataIso) {
    const [ano, mes, dia] = dataIso.split("-");
    return `${dia}/${mes}/${ano}`;
  }

  function escaparHtml(texto) {
    const div = document.createElement("div");
    div.textContent = texto;
    return div.innerHTML;
  }

  function filtradasPorProfissional(consultas) {
    const profissional = filtroProfissional ? filtroProfissional.value : "";
    return profissional ? consultas.filter((c) => c.nomeFisioterapeuta === profissional) : consultas;
  }

  function preencherProfissionais(consultas) {
    if (!filtroProfissional || filtroProfissional.dataset.pronto) return;
    const nomes = [...new Set(consultas.map((c) => c.nomeFisioterapeuta).filter(Boolean))].sort((a, b) =>
      a.localeCompare(b, "pt-BR")
    );
    filtroProfissional.innerHTML =
      '<option value="">Todos os profissionais</option>' +
      nomes.map((n) => `<option value="${escaparHtml(n)}">${escaparHtml(n)}</option>`).join("");
    filtroProfissional.dataset.pronto = "1";
  }

  /** Painel abaixo do calendário: o que aquele mês concentra. */
  function atualizarResumo() {
    if (!resumoEl || !calendario) return;

    const { ano, mes } = calendario.mesVisivel();
    const prefixo = `${ano}-${String(mes + 1).padStart(2, "0")}`;
    const doMes = (cache[abaAtual] ? filtradasPorProfissional(cache[abaAtual]) : []).filter((c) =>
      c.data.startsWith(prefixo)
    );

    if (!doMes.length) {
      resumoEl.innerHTML =
        '<p class="cal-resumo-vazio">Nenhuma consulta neste mês.</p>';
      return;
    }

    const profissionais = new Set(doMes.map((c) => c.nomeFisioterapeuta).filter(Boolean));
    const pacientes = new Set(doMes.map((c) => c.nomePaciente).filter(Boolean));
    const porDia = doMes.reduce((acc, c) => {
      acc[c.data] = (acc[c.data] || 0) + 1;
      return acc;
    }, {});
    const diaMaisCheio = Object.entries(porDia).sort((a, b) => b[1] - a[1])[0];

    resumoEl.innerHTML =
      '<div class="cal-resumo-linha"><span>Consultas no mês</span><strong>' + doMes.length + "</strong></div>" +
      '<div class="cal-resumo-linha"><span>Pacientes atendidos</span><strong>' + pacientes.size + "</strong></div>" +
      '<div class="cal-resumo-linha"><span>Profissionais envolvidos</span><strong>' + profissionais.size + "</strong></div>" +
      '<div class="cal-resumo-linha"><span>Dia mais cheio</span><strong>' +
      formatarData(diaMaisCheio[0]).slice(0, 5) + " · " + diaMaisCheio[1] + "</strong></div>";
  }

  function renderizar() {
    const consultas = cache[abaAtual] || [];
    const diaSelecionado = calendario ? calendario.diaSelecionado() : null;

    let visiveis = filtradasPorProfissional(consultas);
    if (diaSelecionado) {
      visiveis = visiveis.filter((c) => c.data === diaSelecionado);
    }

    if (contador) {
      const base = visiveis.length === 1 ? "1 consulta" : `${visiveis.length} consultas`;
      contador.textContent = diaSelecionado ? `${base} em ${formatarData(diaSelecionado)}` : base;
    }

    if (calendario) {
      // O ponto do dia acompanha a aba: azul para agendadas, verde para realizadas.
      const filtradas = filtradasPorProfissional(consultas);
      calendario.marcarDias(
        abaAtual === "agendadas" ? filtradas : [],
        abaAtual === "realizadas" ? filtradas : []
      );
    }
    atualizarResumo();

    if (!visiveis.length) {
      lista.innerHTML = `<li style="padding:18px;color:var(--ink-muted);">${
        diaSelecionado
          ? "Nenhuma consulta neste dia."
          : abaAtual === "agendadas"
          ? "Nenhuma consulta agendada."
          : "Nenhuma consulta realizada ainda."
      }</li>`;
      return;
    }

    lista.innerHTML = visiveis
      .map(
        (c) => `
      <li class="consulta-item">
        <div class="consulta-quando">
          <strong>${formatarData(c.data)}</strong>
          <span>${(c.hora || "").slice(0, 5)}</span>
        </div>
        <div class="consulta-info">
          <div class="consulta-paciente">${escaparHtml(c.nomePaciente || "Paciente")}</div>
          <div class="consulta-profissional">${escaparHtml(c.nomeFisioterapeuta || "-")}</div>
        </div>
        <div class="consulta-extra">${escaparHtml(c.observacao || "")}</div>
      </li>`
      )
      .join("");
    if (typeof RehabitAnim !== "undefined") RehabitAnim.staggerList(lista);
  }

  function carregarAba(aba) {
    abaAtual = aba;
    abas.forEach((b) => b.classList.toggle("is-ativa", b.dataset.aba === aba));

    if (cache[aba]) {
      renderizar();
      return;
    }

    lista.innerHTML = '<li style="padding:18px;color:var(--ink-muted);">Carregando...</li>';
    const caminho =
      aba === "agendadas"
        ? `/agendamentos?idClinica=${sessao.id}`
        : `/agendamentos/realizadas?idClinica=${sessao.id}`;

    apiGet(caminho)
      .then((consultas) => {
        cache[aba] = consultas;
        preencherProfissionais(consultas);
        // Abre o calendário no mês da consulta mais próxima, senão o usuário
        // cai num mês vazio e acha que não há nada.
        if (calendario && consultas.length) {
          calendario.irPara(consultas[0].data);
        }
        renderizar();
      })
      .catch((err) => {
        lista.innerHTML = `<li style="padding:18px;color:var(--ink-muted);">${escaparHtml(err.message)}</li>`;
      });
  }

  if (containerCalendario && typeof RehabitCalendario !== "undefined") {
    calendario = RehabitCalendario.criar(containerCalendario, renderizar);
    calendario.aoMudarDeMes(atualizarResumo);
  }

  abas.forEach((botao) => {
    botao.addEventListener("click", () => {
      if (calendario) calendario.limparSelecao();
      carregarAba(botao.dataset.aba);
    });
  });
  if (filtroProfissional) {
    filtroProfissional.addEventListener("change", renderizar);
  }

  carregarAba("agendadas");
})();
