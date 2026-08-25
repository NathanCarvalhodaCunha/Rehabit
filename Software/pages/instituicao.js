(function carregarInstituicao() {
  const listaEl = document.querySelector(".fisio-list");
  if (!listaEl) return;

  const sessao = getSessao();
  if (!sessao || sessao.tipo !== "CLINICA") return;

  document.querySelectorAll(".inst-greeting h1").forEach((el) => {
    el.textContent = `Olá, ${sessao.nome}`;
  });
  document.querySelectorAll(".inst-greeting p").forEach((el) => {
    el.textContent = dataAtualPorExtenso();
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
    const item = e.target.closest(".fisio-item[data-id]");
    if (item) window.location.href = `${paginaTema("perfil-profissional")}?id=${item.dataset.id}`;
  });

  apiGet(`/fisioterapeutas?idClinica=${sessao.id}`)
    .then((fisioterapeutas) => {
      if (fisioterapeutas.length === 0) {
        listaEl.innerHTML =
          '<li style="padding:16px;color:var(--ink-muted);">Nenhum profissional cadastrado ainda.</li>';
        return;
      }
      listaEl.innerHTML = fisioterapeutas
        .map((f) => {
          const foto = urlFoto(f.foto);
          const estiloAvatar = foto
            ? ` style="background-image:url('${foto}');background-size:cover;background-position:center;"`
            : "";
          return `
          <li class="fisio-item" data-id="${f.id}" style="cursor:pointer;">
            <div class="avatar-sm"${estiloAvatar} aria-hidden="true"></div>
            <div class="fisio-name">${f.nome}</div>
            <div class="fisio-spec">${f.especialidade || "Fisioterapeuta"}</div>
            <div class="fisio-count"><span class="num">${f.pacientesAtivos}</span><span class="lbl mobile-only"> pacientes ativos</span></div>
          </li>`;
        })
        .join("");
      RehabitAnim.staggerList(listaEl);
    })
    .catch((err) => {
      listaEl.innerHTML = `<li style="padding:16px;color:var(--ink-muted);">${err.message}</li>`;
    });
})();
