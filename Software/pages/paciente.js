function construirGraficoLinha(pontos) {
  if (pontos.length === 0) {
    return '<p style="color:var(--ink-muted);font-size:13px;margin-top:8px;">Ainda não há sessões registradas.</p>';
  }
  const valores = pontos.map((p) => p.valor);
  const max = Math.max(...valores);
  const min = Math.min(...valores);
  const amplitude = max - min || 1;
  const esquerda = 50, direita = 300, topo = 14, base = 162;
  const passoX = pontos.length > 1 ? (direita - esquerda) / (pontos.length - 1) : 0;

  const coords = pontos.map((p, i) => ({
    x: esquerda + passoX * i,
    y: base - ((p.valor - min) / amplitude) * (base - topo),
    rotulo: p.rotulo,
  }));

  const linha = coords.map((c) => `${c.x.toFixed(1)},${c.y.toFixed(1)}`).join(" ");
  const pontosSvg = coords.map((c) => `<circle cx="${c.x.toFixed(1)}" cy="${c.y.toFixed(1)}" r="3.5"/>`).join("");
  const labels = coords
    .map((c) => `<text x="${c.x.toFixed(1)}" y="180" text-anchor="middle">${c.rotulo}</text>`)
    .join("");

  return `
    <svg class="chart-svg" viewBox="0 0 320 190" preserveAspectRatio="none" aria-hidden="true">
      <polyline points="${linha}" fill="none" stroke="#1565D8" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
      <g fill="#1565D8">${pontosSvg}</g>
      <g font-family="Inter, sans-serif" font-size="10" fill="#5F6C7B" text-anchor="middle">${labels}</g>
    </svg>`;
}

function construirGraficoBarras(pontos) {
  if (pontos.length === 0) {
    return '<p style="color:var(--ink-muted);font-size:13px;margin-top:8px;">Ainda não há sessões registradas.</p>';
  }
  const max = Math.max(...pontos.map((p) => p.valor), 1);
  const topo = 20, base = 162, larguraBarra = 36, espaco = 50, inicioX = 52;

  const barras = pontos
    .map((p, i) => {
      const x = inicioX + i * espaco;
      const alturaBarra = (p.valor / max) * (base - topo);
      const y = base - alturaBarra;
      return `<rect x="${x}" y="${y.toFixed(1)}" width="${larguraBarra}" height="${alturaBarra.toFixed(1)}" rx="4" fill="#1565D8"/>`;
    })
    .join("");
  const labels = pontos
    .map((p, i) => {
      const x = inicioX + i * espaco + larguraBarra / 2;
      return `<text x="${x}" y="180" text-anchor="middle">${p.rotulo}</text>`;
    })
    .join("");

  return `
    <svg class="chart-svg" viewBox="0 0 320 190" preserveAspectRatio="none" aria-hidden="true">
      <g>${barras}</g>
      <g font-family="Inter, sans-serif" font-size="10" fill="#5F6C7B" text-anchor="middle">${labels}</g>
    </svg>`;
}

function formatarDataCurta(dataIso) {
  const [, mes, dia] = dataIso.split("-");
  return `${dia}/${mes}`;
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
        .map((s) => ({ rotulo: formatarDataCurta(s.data), valor: Number(s.amplitudeMedia) }));
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
        cartoes[0].insertAdjacentHTML("beforeend", construirGraficoLinha(pontosAmplitude));
      }
      if (cartoes[1]) {
        const ultima = pontosDuracao.length ? pontosDuracao[pontosDuracao.length - 1].valor : null;
        cartoes[1].querySelector(".big-num").textContent = ultima != null ? `${ultima} min` : "-";
        cartoes[1].querySelector(".delta").textContent = pontosDuracao.length
          ? `${pontosDuracao.length} sessões recentes`
          : "Sem histórico ainda";
        cartoes[1].insertAdjacentHTML("beforeend", construirGraficoBarras(pontosDuracao));
      }

      const tbody = document.querySelector(".sessions-table tbody");
      if (tbody) {
        tbody.innerHTML = sessoes.length
          ? sessoes
              .map(
                (s) => `
            <tr>
              <td>${formatarDataLonga(s.data)}</td>
              <td>${s.duracao != null ? s.duracao + " min" : "-"}</td>
              <td>${s.amplitudeMedia != null ? s.amplitudeMedia + "°" : "-"}</td>
            </tr>`
              )
              .join("")
          : '<tr><td colspan="3">Ainda não há sessões registradas.</td></tr>';
      }
    })
    .catch((err) => {
      header.querySelector("h1").textContent = err.message;
    });
})();
