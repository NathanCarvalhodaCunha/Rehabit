/* Rehabit — Desempenho do profissional: indicadores, série de sessões por
   mês e evolução de amplitude paciente a paciente. */
(function carregarDesempenho() {
  const raiz = document.querySelector("[data-desempenho]");
  if (!raiz) return;

  const sessao = getSessao();
  if (!sessao) return;

  const seletorProfissional = document.getElementById("desempenho-profissional");
  const params = new URLSearchParams(window.location.search);

  // A clínica escolhe qual profissional ver; o profissional vê o próprio.
  const ehClinica = sessao.tipo === "CLINICA";
  let idAtual = params.get("id") || (ehClinica ? null : sessao.id);

  let graficoSessoes = null;
  let graficoGanhos = null;
  let dados = null;
  let ordenacao = "ganho";

  function escaparHtml(texto) {
    const div = document.createElement("div");
    div.textContent = texto;
    return div.innerHTML;
  }

  function cores() {
    const escuro = document.body.classList.contains("dark");
    return {
      marca: escuro ? "#2F80FF" : "#1565D8",
      preenchimento: escuro ? "rgba(47,128,255,.16)" : "rgba(21,101,216,.10)",
      positivo: escuro ? "#2BD17E" : "#16A34A",
      negativo: escuro ? "#FF8E8E" : "#E57373",
      grade: escuro ? "#1B2647" : "#E5E7EB",
      texto: escuro ? "#98A2BC" : "#6B7280",
    };
  }

  function formatarGanho(valor) {
    if (valor == null) return "—";
    return `${valor > 0 ? "+" : ""}${valor}°`;
  }

  function preencherIndicadores(d) {
    const cartoes = [
      { rotulo: "Sessões realizadas", valor: d.totalSessoes, detalhe: `${d.sessoesUltimos30Dias} nos últimos 30 dias` },
      { rotulo: "Pacientes atendidos", valor: d.totalPacientes, detalhe: `${d.pacientesAtivos} em tratamento` },
      { rotulo: "Altas concedidas", valor: d.pacientesAlta, detalhe: d.pacientesAlta > 0 ? "tratamentos concluídos" : "nenhuma ainda" },
      {
        rotulo: "Ganho médio",
        valor: d.ganhoMedioGraus != null ? formatarGanho(d.ganhoMedioGraus) : "—",
        detalhe: d.ganhoMedioGraus != null ? "de amplitude por paciente" : "sem medições suficientes",
      },
    ];
    raiz.querySelector("[data-kpis]").innerHTML = cartoes
      .map(
        (c) => `
      <div class="card kpi-card">
        <p class="kpi-rotulo">${c.rotulo}</p>
        <p class="kpi-valor">${c.valor}</p>
        <p class="kpi-detalhe">${c.detalhe}</p>
      </div>`
      )
      .join("");
  }

  function desenharGraficoSessoes(d) {
    const canvas = raiz.querySelector("[data-gr-sessoes]");
    if (!canvas || typeof Chart === "undefined") return;
    if (graficoSessoes) graficoSessoes.destroy();
    const c = cores();

    graficoSessoes = new Chart(canvas, {
      type: "bar",
      data: {
        labels: d.sessoesPorMes.map((p) => p.rotulo),
        datasets: [
          {
            data: d.sessoesPorMes.map((p) => p.sessoes),
            backgroundColor: c.marca,
            borderRadius: 6,
            maxBarThickness: 44,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          tooltip: { callbacks: { label: (ctx) => `${ctx.parsed.y} sessão(ões)` } },
        },
        scales: {
          y: { beginAtZero: true, grid: { color: c.grade }, ticks: { color: c.texto, precision: 0 } },
          x: { grid: { display: false }, ticks: { color: c.texto } },
        },
      },
    });
  }

  function desenharGraficoGanhos(d) {
    const canvas = raiz.querySelector("[data-gr-ganhos]");
    const vazio = raiz.querySelector("[data-ganhos-vazio]");
    if (!canvas || typeof Chart === "undefined") return;
    if (graficoGanhos) graficoGanhos.destroy();

    const comGanho = d.pacientes.filter((p) => p.ganho != null);
    canvas.closest(".chart-canvas-wrap").style.display = comGanho.length ? "" : "none";
    if (vazio) vazio.style.display = comGanho.length ? "none" : "";
    if (!comGanho.length) return;

    const c = cores();
    graficoGanhos = new Chart(canvas, {
      type: "bar",
      data: {
        labels: comGanho.map((p) => p.nome),
        datasets: [
          {
            data: comGanho.map((p) => p.ganho),
            // Verde quem ganhou amplitude, vermelho quem perdeu.
            backgroundColor: comGanho.map((p) => (p.ganho >= 0 ? c.positivo : c.negativo)),
            borderRadius: 6,
            maxBarThickness: 26,
          },
        ],
      },
      options: {
        indexAxis: "y",
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              label: (ctx) => {
                const p = comGanho[ctx.dataIndex];
                return `${formatarGanho(p.ganho)} (de ${p.amplitudeInicial}° para ${p.amplitudeAtual}°)`;
              },
            },
          },
        },
        scales: {
          x: { grid: { color: c.grade }, ticks: { color: c.texto, callback: (v) => `${v}°` } },
          y: { grid: { display: false }, ticks: { color: c.texto } },
        },
      },
    });
  }

  function ordenarPacientes(lista) {
    const copia = lista.slice();
    if (ordenacao === "sessoes") {
      copia.sort((a, b) => b.sessoes - a.sessoes);
    } else if (ordenacao === "nome") {
      copia.sort((a, b) => a.nome.localeCompare(b.nome, "pt-BR"));
    } else {
      // Sem ganho medido vai para o fim, senão polui o topo da tabela.
      copia.sort((a, b) => {
        if (a.ganho == null && b.ganho == null) return 0;
        if (a.ganho == null) return 1;
        if (b.ganho == null) return -1;
        return b.ganho - a.ganho;
      });
    }
    return copia;
  }

  function preencherTabela(d) {
    const corpo = raiz.querySelector("[data-tabela-pacientes]");
    if (!corpo) return;

    if (!d.pacientes.length) {
      corpo.innerHTML = '<tr><td colspan="5">Nenhum paciente atendido ainda.</td></tr>';
      return;
    }

    const selos = {
      Alta: '<span class="badge-status alta">Alta</span>',
      Inativo: '<span class="badge-status inativo">Inativo</span>',
    };

    corpo.innerHTML = ordenarPacientes(d.pacientes)
      .map((p) => {
        const classeGanho = p.ganho == null ? "" : p.ganho >= 0 ? "pos" : "neg";
        return `
        <tr data-id="${p.idPaciente}" style="cursor:pointer;">
          <td>${escaparHtml(p.nome)}${selos[p.status] || ""}</td>
          <td>${p.sessoes}</td>
          <td>${p.amplitudeInicial != null ? p.amplitudeInicial + "°" : "—"}</td>
          <td>${p.amplitudeAtual != null ? p.amplitudeAtual + "°" : "—"}</td>
          <td class="${classeGanho}"><strong>${formatarGanho(p.ganho)}</strong></td>
        </tr>`;
      })
      .join("");
    if (typeof RehabitAnim !== "undefined") RehabitAnim.staggerList(corpo);
  }

  function renderizar(d) {
    dados = d;
    raiz.querySelector("[data-nome-profissional]").textContent = d.nomeFisioterapeuta;
    raiz.querySelector("[data-especialidade]").textContent = d.especialidade || "Fisioterapeuta";

    const avatar = raiz.querySelector("[data-avatar]");
    const foto = urlFoto(d.foto);
    if (avatar && foto) {
      avatar.style.backgroundImage = `url("${foto}")`;
      avatar.style.backgroundSize = "cover";
      avatar.style.backgroundPosition = "center";
    }

    preencherIndicadores(d);
    desenharGraficoSessoes(d);
    desenharGraficoGanhos(d);
    preencherTabela(d);
  }

  function carregar(id) {
    if (!id) return;
    idAtual = id;
    apiGet(`/fisioterapeutas/${id}/desempenho`)
      .then(renderizar)
      .catch((err) => RehabitToast.erro(err.message));
  }

  // Clicar numa linha abre o paciente.
  raiz.addEventListener("click", (e) => {
    const linha = e.target.closest("[data-tabela-pacientes] tr[data-id]");
    if (linha) window.location.href = `${paginaTema("paciente")}?id=${linha.dataset.id}`;
  });

  raiz.querySelectorAll("[data-ordenar]").forEach((botao) => {
    botao.addEventListener("click", () => {
      ordenacao = botao.dataset.ordenar;
      raiz.querySelectorAll("[data-ordenar]").forEach((b) => b.classList.toggle("is-ativa", b === botao));
      if (dados) preencherTabela(dados);
    });
  });

  if (ehClinica && seletorProfissional) {
    apiGet(`/fisioterapeutas?idClinica=${sessao.id}`)
      .then((profissionais) => {
        if (!profissionais.length) {
          raiz.querySelector("[data-nome-profissional]").textContent = "Nenhum profissional cadastrado";
          return;
        }
        seletorProfissional.innerHTML = profissionais
          .map((p) => `<option value="${p.id}">${escaparHtml(p.nome)}</option>`)
          .join("");
        const escolhido = idAtual && profissionais.some((p) => String(p.id) === String(idAtual))
          ? idAtual
          : profissionais[0].id;
        seletorProfissional.value = escolhido;
        carregar(escolhido);
      })
      .catch((err) => RehabitToast.erro(err.message));

    seletorProfissional.addEventListener("change", () => carregar(seletorProfissional.value));
  } else {
    const wrap = seletorProfissional ? seletorProfissional.closest(".select-wrap") : null;
    if (wrap) wrap.style.display = "none";
    carregar(idAtual);
  }
})();
