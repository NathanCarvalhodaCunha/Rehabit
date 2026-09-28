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

    // O logo e o item "Home" da sidebar também são [data-action="go-list"],
    // e vêm antes no HTML: um querySelector solto pegava o logo e deixava o
    // botão cair no atalho global, que leva a clínica de volta para a home.
    // Só o botão desta tela é <button>; os do menu são <a>.
    document.querySelectorAll('button[data-action="go-list"]').forEach((botao) => {
      botao.addEventListener("click", (e) => {
        e.preventDefault();
        e.stopPropagation();
        window.location.href = `${paginaTema("profissional")}?idFisioterapeuta=${idAlvo}`;
      });
    });

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
      excluirCard.querySelector(".btn-danger").addEventListener("click", abrirExclusao);
    }
  }

  /* Excluir não apaga o registro: o profissional perde o acesso e sai das
     listas, mas as sessões que ele atendeu continuam no histórico. Se ele
     tiver pacientes, eles — e a agenda de hoje em diante — precisam ir para
     outro profissional da clínica antes. */
  async function abrirExclusao() {
    const escapar = RehabitConfirmarExclusao.escapar;
    const nome = header.querySelector("h1").textContent.trim() || "este profissional";

    let seusPacientes;
    let colegas;
    try {
      [seusPacientes, colegas] = await Promise.all([
        apiGet(`/pacientes?idFisioterapeuta=${idAlvo}`),
        apiGet(`/fisioterapeutas?idClinica=${sessao.id}`),
      ]);
    } catch (err) {
      RehabitToast.erro(err.message);
      return;
    }
    colegas = colegas.filter((f) => String(f.id) !== String(idAlvo));

    const total = seusPacientes.length;
    const pacientesTexto = total === 1 ? "1 paciente" : `${total} pacientes`;
    const efeito =
      `<p class="dialogo-texto"><strong>${escapar(nome)}</strong> perde o acesso ao Rehabit e sai das listas. ` +
      "As sessões que já atendeu continuam no histórico, identificadas como conta excluída.</p>";

    if (total === 0) {
      RehabitConfirmarExclusao.abrir({
        titulo: `Excluir ${nome}`,
        corpo: efeito,
        rotuloConfirmar: "Excluir profissional",
        aoConfirmar: () => excluir(null, nome, null),
      });
      return;
    }

    if (colegas.length === 0) {
      RehabitConfirmarExclusao.abrir({
        titulo: `Excluir ${nome}`,
        corpo:
          `<p class="dialogo-texto"><strong>${escapar(nome)}</strong> tem ${pacientesTexto} e não há outro ` +
          "profissional na clínica para recebê-los.</p>" +
          '<p class="dialogo-texto">Cadastre outro profissional primeiro; depois volte aqui para excluir.</p>',
        rotuloConfirmar: null,
      });
      return;
    }

    const opcoes = colegas
      .map((f) => `<option value="${escapar(f.id)}">${escapar(f.nome)}</option>`)
      .join("");
    RehabitConfirmarExclusao.abrir({
      titulo: `Excluir ${nome}`,
      corpo:
        efeito +
        '<div class="field">' +
        `<label for="dlg-destino">${escapar(nome)} tem ${pacientesTexto}. Para quem eles vão?</label>` +
        `<select id="dlg-destino" required><option value="">Escolha um profissional</option>${opcoes}</select>` +
        "</div>" +
        '<p class="field-hint">Os pacientes seguem com todo o histórico de sessões, e a agenda de hoje em diante passa junto.</p>',
      rotuloConfirmar: "Transferir e excluir",
      podeConfirmar: (caixa) => !!caixa.querySelector("#dlg-destino").value,
      aoConfirmar: (caixa) => {
        const select = caixa.querySelector("#dlg-destino");
        return excluir(select.value, nome, select.options[select.selectedIndex].text);
      },
    });
  }

  async function excluir(idDestino, nome, nomeDestino) {
    const consulta = idDestino ? `?transferirPara=${encodeURIComponent(idDestino)}` : "";
    const resultado = await apiDelete(`/fisioterapeutas/${idAlvo}${consulta}`);

    const transferidos = resultado && resultado.pacientesTransferidos ? resultado.pacientesTransferidos : 0;
    RehabitToast.sucesso(
      transferidos
        ? `A conta de ${nome} foi excluída. ${transferidos === 1 ? "1 paciente passou" : `${transferidos} pacientes passaram`} para ${nomeDestino}.`
        : `A conta de ${nome} foi excluída.`
    );
    const colisoes = resultado && resultado.colisoesDeAgenda ? resultado.colisoesDeAgenda : 0;
    if (colisoes) {
      RehabitToast.info(
        `${colisoes === 1 ? "1 consulta transferida caiu" : `${colisoes} consultas transferidas caíram`} em horário ` +
          `já ocupado na agenda de ${nomeDestino}. Vale conferir a agenda.`
      );
    }
    setTimeout(() => {
      window.location.href = paginaTema("instituicao");
    }, colisoes ? 3500 : 1600);
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
