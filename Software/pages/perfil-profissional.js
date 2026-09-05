(function carregarPerfilProfissional() {
  const header = document.querySelector(".profile-header");
  const statsEl = document.querySelector(".stats.cols-3");
  if (!header || !statsEl) return;

  const sessao = getSessao();
  if (!sessao) return;

  // Uma clínica pode abrir esta tela para consultar (somente leitura) o
  // perfil de um dos seus profissionais, vindo da lista em instituicao.html.
  const params = new URLSearchParams(window.location.search);
  const idParam = params.get("id");
  const somenteLeitura = sessao.tipo === "CLINICA" && !!idParam;
  if (sessao.tipo !== "FISIOTERAPEUTA" && !somenteLeitura) return;
  const idAlvo = somenteLeitura ? idParam : sessao.id;

  if (somenteLeitura) {
    document.querySelector(".mobile-bottomnav")?.remove();
    document.querySelectorAll('.sidebar .nav a[href="./dispositivo.html"], .sidebar .nav a[href="./configuracoes.html"]')
      .forEach((el) => el.remove());
    const homeLink = document.querySelector('.sidebar .nav a[href="./profissional.html"]');
    if (homeLink) homeLink.href = paginaTema("instituicao");

    // "Editar perfil" (topbar mobile + botão do cabeçalho) passa a editar
    // o profissional visitado, não a conta da própria clínica.
    document.querySelectorAll('[data-action="edit-profile"]').forEach((btn) => {
      btn.addEventListener("click", (e) => {
        e.preventDefault();
        e.stopPropagation();
        window.location.href = `${paginaTema("editar-perfil-profissional")}?id=${idAlvo}`;
      });
    });

    // Escopado ao cartão: o logo da sidebar e o item "Home" também são
    // data-action="go-list", e um querySelector solto pegava o logo — o botão
    // do cartão caía no handler global e levava a clínica para a home dela.
    const goListBtn = document.querySelector('.access-card [data-action="go-list"]');
    if (goListBtn) {
      goListBtn.addEventListener("click", (e) => {
        e.preventDefault();
        e.stopPropagation();
        window.location.href = `${paginaTema("profissional")}?idFisioterapeuta=${idAlvo}`;
      });
    }

    const accessCard = document.querySelector(".access-card");
    if (accessCard) {
      const excluirCard = document.createElement("section");
      excluirCard.className = "card access-card";
      excluirCard.innerHTML = `
        <p class="t">Excluir profissional</p>
        <p class="s">Remove definitivamente o acesso e os dados deste profissional</p>
        <button class="btn-danger" type="button">Excluir profissional</button>
      `;
      accessCard.insertAdjacentElement("afterend", excluirCard);
      excluirCard.querySelector(".btn-danger").addEventListener("click", async () => {
        const confirmado = window.confirm(
          "Tem certeza que deseja excluir este profissional? Essa ação não pode ser desfeita."
        );
        if (!confirmado) return;
        try {
          await apiDelete(`/fisioterapeutas/${idAlvo}`);
          RehabitToast.sucesso("Profissional excluído com sucesso.");
          setTimeout(() => {
            window.location.href = paginaTema("instituicao");
          }, 1200);
        } catch (err) {
          RehabitToast.erro(err.message);
        }
      });
    }
  }

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

  apiGet(`/fisioterapeutas/${idAlvo}`)
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
    .catch((err) => RehabitToast.erro(err.message));
})();
