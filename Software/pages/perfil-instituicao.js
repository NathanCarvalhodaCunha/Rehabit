(function carregarPerfilInstituicao() {
  const header = document.querySelector(".profile-header");
  const statsEl = document.querySelector(".stats");
  if (!header || !statsEl || !document.querySelector(".access-card")) return;

  const sessao = getSessao();
  if (!sessao || sessao.tipo !== "CLINICA") return;

  function definirTextoAposSvg(row, texto) {
    const textNode = Array.from(row.childNodes).find(
      (n) => n.nodeType === Node.TEXT_NODE && n.textContent.trim() !== ""
    );
    if (textNode) {
      textNode.textContent = ` ${texto}`;
    } else {
      row.appendChild(document.createTextNode(` ${texto}`));
    }
  }

  apiGet(`/clinicas/${sessao.id}`)
    .then((clinica) => {
      header.querySelector("h1").textContent = clinica.nome;
      header.querySelector(".role").textContent = clinica.subtitulo || "";

      const rows = header.querySelectorAll(".contact .row");
      if (rows[0]) definirTextoAposSvg(rows[0], clinica.email || "-");
      if (rows[1]) definirTextoAposSvg(rows[1], clinica.telefone || "Não informado");
      if (rows[2]) definirTextoAposSvg(rows[2], clinica.endereco || "Não informado");

      const foto = urlFoto(clinica.foto);
      if (foto) {
        const avatarEl = header.querySelector(".avatar");
        avatarEl.style.backgroundImage = `url("${foto}")`;
        avatarEl.style.backgroundSize = "cover";
        avatarEl.style.backgroundPosition = "center";
      }

      const descEl = document.querySelector(".description-card p");
      if (descEl) descEl.textContent = clinica.descricao || "Sem descrição cadastrada.";

      const mobileVs = document.querySelectorAll(".contact-card-mobile .v");
      if (mobileVs[0]) mobileVs[0].textContent = clinica.email || "-";
      if (mobileVs[1]) mobileVs[1].textContent = clinica.telefone || "Não informado";
      if (mobileVs[2]) mobileVs[2].textContent = clinica.endereco || "Não informado";

      const valores = [
        { valor: String(clinica.profissionaisAtivos), rotulo: "Profissionais ativos" },
        { valor: String(clinica.pacientesTotais), rotulo: "Pacientes totais" },
        { valor: String(clinica.sessoesEsteMes), rotulo: "Sessões este mês" },
        {
          valor: clinica.amplitudeMediaGeral != null ? `${clinica.amplitudeMediaGeral.toFixed(0)}°` : "-",
          rotulo: "Amplitude média geral",
        },
      ];
      statsEl.querySelectorAll(".stat").forEach((card, i) => {
        const delta = card.querySelector(".delta");
        if (delta) delta.remove();
        const valorEl = card.querySelector(".value");
        const labelEl = card.querySelector(".label");
        if (valorEl && valores[i]) valorEl.textContent = valores[i].valor;
        if (labelEl && valores[i]) labelEl.textContent = valores[i].rotulo;
      });
    })
    .catch((err) => RehabitToast.erro(err.message));
})();
