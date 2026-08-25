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

  // Cada aba é carregada uma vez e guardada aqui, para alternar sem rede.
  const cache = { agendadas: null, realizadas: null };
  let abaAtual = "agendadas";

  function formatarData(dataIso) {
    const [ano, mes, dia] = dataIso.split("-");
    return `${dia}/${mes}/${ano}`;
  }

  function escaparHtml(texto) {
    const div = document.createElement("div");
    div.textContent = texto;
    return div.innerHTML;
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

  function renderizar() {
    const consultas = cache[abaAtual] || [];
    const profissional = filtroProfissional ? filtroProfissional.value : "";
    const visiveis = profissional
      ? consultas.filter((c) => c.nomeFisioterapeuta === profissional)
      : consultas;

    if (contador) {
      contador.textContent = visiveis.length === 1 ? "1 consulta" : `${visiveis.length} consultas`;
    }

    if (!visiveis.length) {
      lista.innerHTML = `<li style="padding:18px;color:var(--ink-muted);">${
        abaAtual === "agendadas" ? "Nenhuma consulta agendada." : "Nenhuma consulta realizada ainda."
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
        renderizar();
      })
      .catch((err) => {
        lista.innerHTML = `<li style="padding:18px;color:var(--ink-muted);">${escaparHtml(err.message)}</li>`;
      });
  }

  abas.forEach((botao) => {
    botao.addEventListener("click", () => carregarAba(botao.dataset.aba));
  });
  if (filtroProfissional) {
    filtroProfissional.addEventListener("change", renderizar);
  }

  carregarAba("agendadas");
})();
