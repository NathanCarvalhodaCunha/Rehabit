// Cores do gráfico seguem o tema atual (claro/escuro), mesmo par de azul
// usado no resto do app.
function coresGrafico() {
  const escuro = document.body.classList.contains("dark");
  return {
    marca: escuro ? "#2F80FF" : "#1565D8",
    preenchimento: escuro ? "rgba(47,128,255,.14)" : "rgba(21,101,216,.08)",
    dor: escuro ? "#FF8E8E" : "#E57373",
    grade: escuro ? "#1B2647" : "#E5E7EB",
    texto: escuro ? "#98A2BC" : "#6B7280",
  };
}

/**
 * O Chart.js vem de CDN, e CDN falha (rede bloqueando o domínio, internet
 * caindo). Sem esta checagem, o "new Chart" estourava e derrubava o resto
 * da montagem da tela do paciente — o histórico e os dados sumiam junto
 * com o gráfico. Devolve true quando avisou, para quem chamou parar aí.
 */
function semBibliotecaDeGrafico(cartao) {
  if (typeof Chart !== "undefined") return false;
  cartao.insertAdjacentHTML(
    "beforeend",
    '<p style="color:var(--ink-muted);font-size:13px;margin-top:8px;">' +
      "Não foi possível carregar o gráfico. Verifique sua conexão." +
      "</p>"
  );
  return true;
}

/**
 * Amplitude e dor no mesmo gráfico, em eixos separados: a leitura que
 * importa é a amplitude subindo enquanto a dor cai.
 */
function construirGraficoAmplitudeXDor(cartao, pontos) {
  const comDor = pontos.filter((p) => p.dor != null);
  if (pontos.length === 0 || comDor.length === 0) {
    return false;
  }
  if (semBibliotecaDeGrafico(cartao)) return true;
  const cores = coresGrafico();
  const wrap = document.createElement("div");
  wrap.className = "chart-canvas-wrap";
  const canvas = document.createElement("canvas");
  canvas.className = "chart-canvas";
  wrap.appendChild(canvas);
  cartao.appendChild(wrap);

  new Chart(canvas, {
    data: {
      labels: pontos.map((p) => p.rotulo),
      datasets: [
        {
          type: "line",
          label: "Amplitude",
          data: pontos.map((p) => p.valor),
          borderColor: cores.marca,
          backgroundColor: cores.preenchimento,
          borderWidth: 2,
          pointRadius: 4,
          pointBackgroundColor: cores.marca,
          tension: 0.3,
          fill: true,
          yAxisID: "y",
        },
        {
          type: "line",
          label: "Dor",
          data: pontos.map((p) => p.dor),
          borderColor: cores.dor,
          borderWidth: 2,
          borderDash: [5, 4],
          pointRadius: 4,
          pointBackgroundColor: cores.dor,
          tension: 0.3,
          fill: false,
          yAxisID: "yDor",
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: { mode: "index", intersect: false },
      plugins: {
        legend: { display: true, labels: { color: cores.texto, boxWidth: 12, usePointStyle: true } },
        tooltip: {
          callbacks: {
            label: (ctx) =>
              ctx.dataset.label === "Dor"
                ? `Dor: ${ctx.parsed.y}/10`
                : `Amplitude: ${ctx.parsed.y}°`,
          },
        },
      },
      scales: {
        y: {
          position: "left",
          grid: { color: cores.grade },
          ticks: { color: cores.texto, callback: (v) => `${v}°` },
        },
        yDor: {
          position: "right",
          min: 0,
          max: 10,
          grid: { display: false },
          ticks: { color: cores.dor, stepSize: 2 },
        },
        x: { grid: { display: false }, ticks: { color: cores.texto } },
      },
    },
  });
  return true;
}

function construirGraficoLinha(cartao, pontos) {
  if (pontos.length === 0) {
    cartao.insertAdjacentHTML(
      "beforeend",
      '<p style="color:var(--ink-muted);font-size:13px;margin-top:8px;">Ainda não há sessões registradas.</p>'
    );
    return;
  }
  if (semBibliotecaDeGrafico(cartao)) return;
  const cores = coresGrafico();
  const wrap = document.createElement("div");
  wrap.className = "chart-canvas-wrap";
  const canvas = document.createElement("canvas");
  canvas.className = "chart-canvas";
  wrap.appendChild(canvas);
  cartao.appendChild(wrap);

  new Chart(canvas, {
    type: "line",
    data: {
      labels: pontos.map((p) => p.rotulo),
      datasets: [
        {
          data: pontos.map((p) => p.valor),
          borderColor: cores.marca,
          backgroundColor: cores.preenchimento,
          borderWidth: 2,
          pointRadius: 4,
          pointHoverRadius: 6,
          pointBackgroundColor: cores.marca,
          tension: 0.3,
          fill: true,
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: { callbacks: { label: (ctx) => `${ctx.parsed.y}°` } },
      },
      scales: {
        y: {
          grid: { color: cores.grade },
          ticks: { color: cores.texto, callback: (v) => `${v}°` },
        },
        x: { grid: { display: false }, ticks: { color: cores.texto } },
      },
    },
  });
}

function construirGraficoBarras(cartao, pontos) {
  if (pontos.length === 0) {
    cartao.insertAdjacentHTML(
      "beforeend",
      '<p style="color:var(--ink-muted);font-size:13px;margin-top:8px;">Ainda não há sessões registradas.</p>'
    );
    return;
  }
  if (semBibliotecaDeGrafico(cartao)) return;
  const cores = coresGrafico();
  const wrap = document.createElement("div");
  wrap.className = "chart-canvas-wrap";
  const canvas = document.createElement("canvas");
  canvas.className = "chart-canvas";
  wrap.appendChild(canvas);
  cartao.appendChild(wrap);

  new Chart(canvas, {
    type: "bar",
    data: {
      labels: pontos.map((p) => p.rotulo),
      datasets: [
        {
          data: pontos.map((p) => p.valor),
          backgroundColor: cores.marca,
          borderRadius: 4,
          maxBarThickness: 36,
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: { callbacks: { label: (ctx) => `${ctx.parsed.y} min` } },
      },
      scales: {
        y: {
          grid: { color: cores.grade },
          ticks: { color: cores.texto, callback: (v) => `${v} min` },
        },
        x: { grid: { display: false }, ticks: { color: cores.texto } },
      },
    },
  });
}

function formatarDataCurta(dataIso) {
  const [, mes, dia] = dataIso.split("-");
  return `${dia}/${mes}`;
}

// O prontuário é texto livre digitado pelo profissional e vai para dentro
// de um template de HTML — precisa ser escapado.
function escaparHtml(texto) {
  const div = document.createElement("div");
  div.textContent = texto;
  return div.innerHTML;
}

function formatarDataLonga(dataIso) {
  const [ano, mes, dia] = dataIso.split("-");
  return `${dia}/${mes}/${ano}`;
}

(function carregarPaciente() {
  const header = document.querySelector(".patient-header");
  if (!header) return;

  const sessao = getSessao();
  if (!sessao) return;

  const params = new URLSearchParams(window.location.search);
  const idPaciente = params.get("id");
  if (!idPaciente) {
    header.querySelector("h1").textContent = "Paciente não informado.";
    return;
  }

  document.querySelectorAll(".tabs a.tab").forEach((a) => {
    a.href = `${paginaTema("cadastrar-sessao")}?id=${idPaciente}`;
  });
  document.querySelectorAll(".edit-patient-btn").forEach((btn) => {
    btn.addEventListener("click", () => {
      window.location.href = `${paginaTema("editar-paciente")}?id=${idPaciente}`;
    });
  });
  const addSessionBtn = document.querySelector('[data-action="add-session"]');
  if (addSessionBtn) {
    addSessionBtn.addEventListener("click", (e) => {
      e.stopPropagation();
      window.location.href = `${paginaTema("cadastrar-sessao")}?id=${idPaciente}`;
    });
  }

  Promise.all([apiGet(`/pacientes/${idPaciente}`), apiGet(`/pacientes/${idPaciente}/sessoes`)])
    .then(([paciente, sessoes]) => {
      const idadeTexto = paciente.idade != null ? `${paciente.idade} anos` : "Idade não informada";
      const sexoTexto = paciente.sexo || "Não informado";
      const situacaoTexto = paciente.situacao || "Sem situação registrada";
      const inicioTexto = paciente.dataInicioTratamento ? formatarDataLonga(paciente.dataInicioTratamento) : "-";
      const fisioTexto = paciente.nomeFisioterapeuta || "-";

      const fotoPaciente = urlFoto(paciente.foto);
      if (fotoPaciente) {
        const avatarEl = header.querySelector(".avatar-lg");
        if (avatarEl) {
          avatarEl.style.backgroundImage = `url("${fotoPaciente}")`;
          avatarEl.style.backgroundSize = "cover";
          avatarEl.style.backgroundPosition = "center";
        }
      }

      header.querySelector("h1").textContent = paciente.nome;
      header.querySelector(".patient-meta.desktop-only").innerHTML =
        `${idadeTexto} – ${sexoTexto} – ${situacaoTexto}<br/>` +
        `Início do tratamento: <strong>${inicioTexto}</strong> – Fisioterapia <strong>${fisioTexto}</strong>`;
      header.querySelector(".patient-meta.mobile-only").innerHTML = `${idadeTexto} – ${sexoTexto}<br/>${situacaoTexto}`;

      const infoValores = document.querySelectorAll(".info-strip .v");
      if (infoValores[0]) infoValores[0].textContent = inicioTexto;
      if (infoValores[1]) infoValores[1].textContent = fisioTexto;

      const cronologicas = sessoes.slice(0, 5).reverse();
      const pontosAmplitude = cronologicas
        .filter((s) => s.amplitudeMedia != null)
        .map((s) => ({
          rotulo: formatarDataCurta(s.data),
          valor: Number(s.amplitudeMedia),
          dor: s.dor != null ? Number(s.dor) : null,
        }));
      const pontosDuracao = cronologicas
        .filter((s) => s.duracao != null)
        .map((s) => ({ rotulo: formatarDataCurta(s.data), valor: s.duracao }));

      const cartoes = document.querySelectorAll(".chart-card");
      if (cartoes[0]) {
        const ultima = pontosAmplitude.length ? pontosAmplitude[pontosAmplitude.length - 1].valor : null;
        const anterior = pontosAmplitude.length > 1 ? pontosAmplitude[pontosAmplitude.length - 2].valor : null;
        cartoes[0].querySelector(".big-num").textContent = ultima != null ? `${ultima}°` : "-";
        const deltaAmplitudeEl = cartoes[0].querySelector(".delta");
        const diferencaAmplitude = ultima != null && anterior != null ? ultima - anterior : null;
        deltaAmplitudeEl.textContent =
          diferencaAmplitude != null
            ? `${diferencaAmplitude >= 0 ? "+" : ""}${diferencaAmplitude.toFixed(0)}° desde a última sessão`
            : "Sem histórico suficiente";
        deltaAmplitudeEl.classList.toggle("negative", diferencaAmplitude != null && diferencaAmplitude < 0);
        // Havendo dor registrada, mostra os dois juntos; senão, só amplitude.
        if (!construirGraficoAmplitudeXDor(cartoes[0], pontosAmplitude)) {
          construirGraficoLinha(cartoes[0], pontosAmplitude);
        } else {
          const sub = cartoes[0].querySelector(".sub");
          if (sub) sub.textContent = "Amplitude e dor relatada";
        }
      }
      if (cartoes[1]) {
        const ultima = pontosDuracao.length ? pontosDuracao[pontosDuracao.length - 1].valor : null;
        cartoes[1].querySelector(".big-num").textContent = ultima != null ? `${ultima} min` : "-";
        cartoes[1].querySelector(".delta").textContent = pontosDuracao.length
          ? `${pontosDuracao.length} sessões recentes`
          : "Sem histórico ainda";
        construirGraficoBarras(cartoes[1], pontosDuracao);
      }

      const tbody = document.querySelector(".sessions-table tbody");
      if (tbody) {
        tbody.innerHTML = sessoes.length
          ? sessoes
              .map(
                (s) => `
            <tr>
              <td>${formatarDataLonga(s.data)}${
                  s.observacoes
                    ? `<div class="sessao-obs">${escaparHtml(s.observacoes)}</div>`
                    : ""
                }</td>
              <td>${s.duracao != null ? s.duracao + " min" : "-"}</td>
              <td>${s.amplitudeMedia != null ? s.amplitudeMedia + "°" : "-"}</td>
            </tr>`
              )
              .join("")
          : '<tr><td colspan="3">Ainda não há sessões registradas.</td></tr>';
        RehabitAnim.staggerList(tbody);
      }
    })
    .catch((err) => {
      header.querySelector("h1").textContent = err.message;
    });
})();
