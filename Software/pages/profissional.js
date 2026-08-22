(function carregarProfissional() {
  const listaEl = document.querySelector(".pat-list");
  if (!listaEl) return;

  const sessao = getSessao();
  if (!sessao || sessao.tipo !== "FISIOTERAPEUTA") return;

  const SELO_INFO = {
    Evoluindo: { classe: "evoluindo", texto: "Evoluindo" },
    Estavel: { classe: "estavel", texto: "Estável" },
    Instavel: { classe: "instavel", texto: "Instável" },
  };

  const hora = new Date().getHours();
  const saudacao = hora < 12 ? "Bom dia" : hora < 18 ? "Boa tarde" : "Boa noite";
  document.querySelectorAll(".pro-greeting h1").forEach((el) => {
    el.textContent = `${saudacao}, ${sessao.nome}!`;
  });
  document.querySelectorAll(".inst-user-meta .name").forEach((el) => {
    el.textContent = sessao.nome;
  });
  const fotoSessao = urlFoto(sessao.foto);
  if (fotoSessao) {
    document.querySelectorAll(".inst-avatar, .mobile-avatar").forEach((el) => {
      el.style.backgroundImage = `url("${fotoSessao}")`;
      el.style.backgroundSize = "cover";
      el.style.backgroundPosition = "center";
    });
  }

  listaEl.addEventListener("click", (e) => {
    const item = e.target.closest(".pat-item[data-id]");
    if (item) window.location.href = `${paginaTema("paciente")}?id=${item.dataset.id}`;
  });

  apiGet(`/pacientes?idFisioterapeuta=${sessao.id}`)
    .then((pacientes) => {
      if (pacientes.length === 0) {
        listaEl.innerHTML =
          '<li style="padding:16px;color:var(--ink-muted);">Nenhum paciente cadastrado ainda.</li>';
        return;
      }
      listaEl.innerHTML = pacientes
        .map((p) => {
          const info = SELO_INFO[p.selo];
          const badge = info
            ? `<span class="badge ${info.classe}">${info.texto}</span>`
            : `<span class="badge estavel">Sem sessões</span>`;
          const ultimaSessao = p.ultimaSessao || "-";
          return `
          <li class="pat-item" data-id="${p.id}" style="cursor:pointer;">
            <div class="avatar-sm" aria-hidden="true"></div>
            <div class="pat-name">${p.nome}</div>
            <div class="pat-meta-m mobile-only">Última sessão<br/>${ultimaSessao}</div>
            <div class="pat-date desktop-only">${ultimaSessao}</div>
            <div class="pat-status">${badge}</div>
          </li>`;
        })
        .join("");
      RehabitAnim.staggerList(listaEl);
    })
    .catch((err) => {
      listaEl.innerHTML = `<li style="padding:16px;color:var(--ink-muted);">${err.message}</li>`;
    });
})();
