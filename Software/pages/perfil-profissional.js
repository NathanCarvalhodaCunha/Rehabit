(function carregarPerfilProfissional() {
  const header = document.querySelector(".profile-header");
  const statsEl = document.querySelector(".stats.cols-3");
  if (!header || !statsEl) return;

  const sessao = getSessao();
  if (!sessao || sessao.tipo !== "FISIOTERAPEUTA") return;

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

  apiGet(`/fisioterapeutas/${sessao.id}`)
    .then((f) => {
      header.querySelector("h1").textContent = f.nome;
      header.querySelector(".role").textContent = f.especialidade || "Fisioterapeuta";
      const idEl = header.querySelector(".id");
      if (idEl) idEl.textContent = `COFFITO ${f.coffito}`;

      const rows = header.querySelectorAll(".contact .row");
      if (rows[0]) definirTextoAposSvg(rows[0], f.email || "-");
      if (rows[1]) definirTextoAposSvg(rows[1], f.telefone || "Não informado");
      if (rows[2]) definirTextoAposSvg(rows[2], f.localidade || "Não informado");

      const foto = urlFoto(f.foto);
      if (foto) {
        const avatarEl = header.querySelector(".avatar");
        avatarEl.style.backgroundImage = `url("${foto}")`;
        avatarEl.style.backgroundSize = "cover";
        avatarEl.style.backgroundPosition = "center";
      }

      const descEl = document.querySelector(".description-card p");
      if (descEl) descEl.textContent = f.descricao || "Sem descrição cadastrada.";

      const mobileVs = document.querySelectorAll(".contact-card-mobile .v");
      if (mobileVs[0]) mobileVs[0].textContent = f.email || "-";
      if (mobileVs[1]) mobileVs[1].textContent = f.telefone || "Não informado";
      if (mobileVs[2]) mobileVs[2].textContent = f.localidade || "Não informado";

      const valores = [
        { valor: String(f.pacientesAtivos), rotulo: "Pacientes ativos" },
        { valor: String(f.sessoesEsteMes), rotulo: "Sessões este mês" },
        {
          valor: f.amplitudeMediaGeral != null ? `${f.amplitudeMediaGeral.toFixed(0)}°` : "-",
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
    .catch((err) => alert(err.message));
})();
