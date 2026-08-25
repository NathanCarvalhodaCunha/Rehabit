(function carregarProfissional() {
  const listaEl = document.querySelector(".pat-list");
  if (!listaEl) return;

  const sessao = getSessao();
  if (!sessao) return;

  // Uma clínica pode abrir esta tela para ver a lista de pacientes de um
  // dos seus profissionais, vinda de perfil-profissional.html.
  const params = new URLSearchParams(window.location.search);
  const idParam = params.get("idFisioterapeuta");
  const somenteLeitura = sessao.tipo === "CLINICA" && !!idParam;
  if (sessao.tipo !== "FISIOTERAPEUTA" && !somenteLeitura) return;
  const idFisioterapeuta = somenteLeitura ? idParam : sessao.id;

  if (somenteLeitura) {
    document.querySelectorAll('[data-action="add-patient"]').forEach((el) => el.remove());
    document.querySelector(".agenda-home-card")?.remove();
  } else {
    carregarProximosAgendamentos(idFisioterapeuta);
  }

  function carregarProximosAgendamentos(idFisio) {
    const agendaListEl = document.querySelector(".agenda-home-card .agenda-list");
    if (!agendaListEl) return;
    apiGet(`/agendamentos?idFisioterapeuta=${idFisio}`)
      .then((agendamentos) => {
        if (agendamentos.length === 0) {
          agendaListEl.innerHTML =
            '<li style="padding:16px;color:var(--ink-muted);">Nenhum agendamento futuro.</li>';
          return;
        }
        agendaListEl.innerHTML = agendamentos
          .slice(0, 3)
          .map((a) => {
            const [ano, mes, dia] = a.data.split("-");
            const hora = a.hora.slice(0, 5);
            return `
          <li class="agenda-item">
            <div class="agenda-when">${dia}/${mes}/${ano}<br/><span class="hora">${hora}</span></div>
            <div>
              <div class="agenda-patient">${a.nomePaciente || "Paciente"}</div>
              ${a.observacao ? `<div class="agenda-obs">${a.observacao}</div>` : ""}
            </div>
            <span></span>
          </li>`;
          })
          .join("");
      })
      .catch(() => {
        agendaListEl.innerHTML = '<li style="padding:16px;color:var(--ink-muted);">Não foi possível carregar a agenda.</li>';
      });
  }

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
  document.querySelectorAll(".pro-greeting p").forEach((el) => {
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
    const item = e.target.closest(".pat-item[data-id]");
    if (item) window.location.href = `${paginaTema("paciente")}?id=${item.dataset.id}`;
  });

  apiGet(`/pacientes?idFisioterapeuta=${idFisioterapeuta}`)
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
          const fotoPaciente = urlFoto(p.foto);
          const estiloAvatar = fotoPaciente
            ? ` style="background-image:url('${fotoPaciente}');background-size:cover;background-position:center;"`
            : "";
          return `
          <li class="pat-item" data-id="${p.id}" style="cursor:pointer;">
            <div class="avatar-sm" aria-hidden="true"${estiloAvatar}></div>
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
