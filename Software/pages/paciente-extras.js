/* Rehabit — complementos da tela do paciente: meta, anamnese, linha do
   tempo, lembrete no WhatsApp e relatório em PDF.

   Fica separado de paciente.js porque aquele arquivo já cuida do cabeçalho,
   dos gráficos e da tabela; misturar tudo tornaria os dois difíceis de ler. */
(function complementosDoPaciente() {
  const abas = Array.from(document.querySelectorAll("[data-painel]"));
  if (!abas.length) return;

  const sessao = getSessao();
  if (!sessao) return;

  const idPaciente = new URLSearchParams(window.location.search).get("id");
  if (!idPaciente) return;

  function escapar(texto) {
    const div = document.createElement("div");
    div.textContent = texto;
    return div.innerHTML;
  }

  function formatarData(dataIso) {
    if (!dataIso) return "";
    const [ano, mes, dia] = dataIso.split("-");
    return `${dia}/${mes}/${ano}`;
  }

  // --- Abas ---
  function mostrarPainel(nome) {
    abas.forEach((b) => b.classList.toggle("is-ativa", b.dataset.painel === nome));
    document.querySelectorAll("[data-conteudo]").forEach((el) => {
      el.hidden = el.dataset.conteudo !== nome;
    });
  }
  abas.forEach((b) => b.addEventListener("click", () => mostrarPainel(b.dataset.painel)));

  // --- Meta de tratamento ---
  function montarMeta(paciente, sessoes) {
    const card = document.querySelector("[data-meta]");
    if (!card || paciente.metaAmplitude == null) return;

    const comMedicao = sessoes.filter((s) => s.amplitudeMedia != null);
    const atual = comMedicao.length ? Number(comMedicao[0].amplitudeMedia) : null;
    const inicial = comMedicao.length ? Number(comMedicao[comMedicao.length - 1].amplitudeMedia) : null;
    const meta = Number(paciente.metaAmplitude);

    card.hidden = false;

    if (atual == null) {
      card.querySelector("[data-meta-rotulo]").textContent = `Meta: ${meta}°`;
      card.querySelector("[data-meta-detalhe]").textContent = "Sem medições registradas ainda.";
      return;
    }

    // Progresso conta a partir do ponto de partida, não do zero absoluto —
    // senão um paciente que começou em 60° já apareceria quase pronto.
    const base = inicial != null ? inicial : 0;
    const total = meta - base;
    const andado = atual - base;
    const percentual = total <= 0 ? 100 : Math.max(0, Math.min(100, Math.round((andado / total) * 100)));

    card.querySelector("[data-meta-rotulo]").textContent = `${atual}° de ${meta}°`;
    card.querySelector("[data-meta-progresso]").style.width = `${percentual}%`;
    card.classList.toggle("is-atingida", atual >= meta);

    const prazo = paciente.metaData ? ` · prazo ${formatarData(paciente.metaData)}` : "";
    card.querySelector("[data-meta-detalhe]").textContent =
      atual >= meta
        ? `Meta atingida${prazo}`
        : `${percentual}% do caminho, partindo de ${base}°${prazo}`;
  }

  // --- Anamnese ---
  function montarAnamnese(paciente) {
    document.querySelectorAll("[data-an]").forEach((el) => {
      const valor = paciente[el.dataset.an];
      el.textContent = valor && String(valor).trim() ? valor : "—";
      el.classList.toggle("vazio", !valor);
    });
  }

  // --- Linha do tempo ---
  function montarTimeline(paciente, sessoes, agendamentos) {
    const lista = document.querySelector("[data-timeline]");
    if (!lista) return;

    const eventos = [];

    if (paciente.dataInicioTratamento) {
      eventos.push({
        data: paciente.dataInicioTratamento,
        tipo: "inicio",
        titulo: "Início do tratamento",
        detalhe: paciente.situacao || "",
      });
    }

    sessoes.forEach((s) => {
      const partes = [];
      if (s.duracao != null) partes.push(`${s.duracao} min`);
      if (s.amplitudeMedia != null) partes.push(`${s.amplitudeMedia}°`);
      if (s.dor != null) partes.push(`dor ${s.dor}/10`);
      eventos.push({
        data: s.data,
        tipo: "sessao",
        titulo: "Sessão realizada",
        detalhe: partes.join(" · ") + (s.observacoes ? ` — ${s.observacoes}` : ""),
      });
    });

    (agendamentos || []).forEach((a) => {
      eventos.push({
        data: a.data,
        tipo: "agendamento",
        titulo: "Consulta agendada",
        detalhe: `${(a.hora || "").slice(0, 5)}${a.observacao ? " — " + a.observacao : ""}`,
      });
    });

    if (!eventos.length) {
      lista.innerHTML = '<li class="timeline-vazio">Nada registrado ainda.</li>';
      return;
    }

    eventos.sort((a, b) => (a.data < b.data ? 1 : a.data > b.data ? -1 : 0));

    lista.innerHTML = eventos
      .map(
        (e) => `
      <li class="timeline-item is-${e.tipo}">
        <span class="timeline-marca"></span>
        <div class="timeline-corpo">
          <span class="timeline-data">${formatarData(e.data)}</span>
          <strong>${escapar(e.titulo)}</strong>
          ${e.detalhe ? `<span>${escapar(e.detalhe)}</span>` : ""}
        </div>
      </li>`
      )
      .join("");
  }

  // --- WhatsApp ---
  function ligarWhatsapp(paciente, agendamentos) {
    const botao = document.querySelector('[data-acao="whatsapp"]');
    if (!botao) return;

    const telefone = (paciente.telefone || "").replace(/\D/g, "");
    if (!telefone) return;

    const proxima = (agendamentos || [])[0];
    const quando = proxima
      ? `sua sessão está marcada para ${formatarData(proxima.data)} às ${(proxima.hora || "").slice(0, 5)}`
      : "estamos à disposição para agendar sua próxima sessão";
    const texto = `Olá, ${paciente.nome}! Aqui é da ${sessao.nome}. Lembrando que ${quando}.`;

    // 55 = Brasil. Se o número já vier com o país, não duplica.
    const numero = telefone.startsWith("55") ? telefone : "55" + telefone;
    botao.hidden = false;
    botao.addEventListener("click", () => {
      window.open(`https://wa.me/${numero}?text=${encodeURIComponent(texto)}`, "_blank", "noopener");
    });
  }

  // --- Relatório em PDF (via impressão do navegador) ---
  function ligarRelatorio() {
    const botao = document.querySelector('[data-acao="relatorio"]');
    if (!botao) return;
    botao.addEventListener("click", () => {
      // Mostra tudo antes de imprimir: o que está escondido numa aba não sai
      // no papel, e o relatório precisa do histórico e da anamnese juntos.
      document.body.classList.add("modo-impressao");
      document.querySelectorAll("[data-conteudo]").forEach((el) => (el.hidden = false));
      window.print();
      // Só volta ao normal depois que o diálogo fecha.
      window.addEventListener(
        "afterprint",
        () => {
          document.body.classList.remove("modo-impressao");
          mostrarPainel(document.querySelector("[data-painel].is-ativa")?.dataset.painel || "historico");
        },
        { once: true }
      );
    });
  }

  Promise.all([
    apiGet(`/pacientes/${idPaciente}`),
    apiGet(`/pacientes/${idPaciente}/sessoes`),
  ])
    .then(([paciente, sessoes]) => {
      montarMeta(paciente, sessoes);
      montarAnamnese(paciente);
      ligarRelatorio();

      // A agenda é do profissional; a clínica não tem esse endpoint por
      // paciente, então a linha do tempo dela mostra só o que já aconteceu.
      const agendaPromessa =
        sessao.tipo === "FISIOTERAPEUTA"
          ? apiGet(`/agendamentos?idFisioterapeuta=${sessao.id}`).catch(() => [])
          : Promise.resolve([]);

      return agendaPromessa.then((agendamentos) => {
        const doPaciente = agendamentos.filter((a) => String(a.idPaciente) === String(idPaciente));
        montarTimeline(paciente, sessoes, doPaciente);
        ligarWhatsapp(paciente, doPaciente);
      });
    })
    .catch(() => {
      /* paciente.js já mostra o erro principal da tela */
    });
})();
