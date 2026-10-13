(function carregarProfissional() {
  const listaEl = document.querySelector(".pat-list");
  if (!listaEl) return;

  const sessao = getSessao();
  if (!sessao) return;

  // Uma clínica pode abrir esta tela para ver a lista de pacientes de um
  // dos seus profissionais, vinda de perfil-profissional.html.
  const idParam = idDaUrl("idFisioterapeuta");
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
            <div class="agenda-when">${escaparHtml(`${dia}/${mes}/${ano}`)}<br/><span class="hora">${escaparHtml(hora)}</span></div>
            <div>
              <div class="agenda-patient">${escaparHtml(a.nomePaciente || "Paciente")}</div>
              ${a.observacao ? `<div class="agenda-obs">${escaparHtml(a.observacao)}</div>` : ""}
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

  if (somenteLeitura) {
    // A clínica está olhando a tela de um profissional: o cabeçalho tem que
    // dizer de quem são os pacientes, não saudar quem está logado.
    document.querySelectorAll(".pro-greeting p").forEach((el) => {
      el.textContent = dataAtualPorExtenso();
    });
    apiGet(`/fisioterapeutas/${idFisioterapeuta}`)
      .then((f) => {
        document.querySelectorAll(".pro-greeting h1").forEach((el) => {
          el.textContent = `Pacientes de ${f.nome}`;
        });
        document.querySelectorAll(".inst-user-meta .name").forEach((el) => {
          el.textContent = f.nome;
        });
        document.querySelectorAll(".inst-avatar, .mobile-avatar").forEach((el) => aplicarFoto(el, f.foto));
      })
      .catch(() => {
        document.querySelectorAll(".pro-greeting h1").forEach((el) => {
          el.textContent = "Pacientes do profissional";
        });
      });
  } else {
    document.querySelectorAll(".pro-greeting h1").forEach((el) => {
      el.textContent = `${saudacao}, ${sessao.nome}!`;
    });
    document.querySelectorAll(".pro-greeting p").forEach((el) => {
      el.textContent = dataAtualPorExtenso();
    });
    document.querySelectorAll(".inst-user-meta .name").forEach((el) => {
      el.textContent = sessao.nome;
    });
    document.querySelectorAll(".inst-avatar, .mobile-avatar").forEach((el) => aplicarFoto(el, sessao.foto));
  }

  listaEl.addEventListener("click", (e) => {
    const item = e.target.closest(".pat-item[data-id]");
    if (item) window.location.href = `${paginaTema("paciente")}?id=${item.dataset.id}`;
  });

  // dd/MM/yyyy -> timestamp (ou null se vazio/inválido), para poder ordenar
  // por "Última sessão" sem depender de string.
  function dataBrParaOrdenacao(dataBr) {
    if (!dataBr) return null;
    const [dia, mes, ano] = dataBr.split("/");
    const tempo = new Date(`${ano}-${mes}-${dia}T00:00:00`).getTime();
    return Number.isNaN(tempo) ? null : tempo;
  }

  const ORDEM_STATUS = { Instavel: 0, Estavel: 1, Evoluindo: 2 };

  function ordenarPacientes(lista, criterio) {
    const copia = lista.slice();
    if (criterio === "Última sessão" || criterio === "Última Sessão") {
      copia.sort((a, b) => {
        const ta = dataBrParaOrdenacao(a.ultimaSessao);
        const tb = dataBrParaOrdenacao(b.ultimaSessao);
        if (ta == null && tb == null) return 0;
        if (ta == null) return 1;
        if (tb == null) return -1;
        return tb - ta;
      });
    } else if (criterio === "Status") {
      copia.sort((a, b) => {
        const oa = a.selo in ORDEM_STATUS ? ORDEM_STATUS[a.selo] : 99;
        const ob = b.selo in ORDEM_STATUS ? ORDEM_STATUS[b.selo] : 99;
        return oa - ob || a.nome.localeCompare(b.nome, "pt-BR");
      });
    } else {
      copia.sort((a, b) => a.nome.localeCompare(b.nome, "pt-BR"));
    }
    return copia;
  }

  function renderizarPacientes(pacientes) {
    if (pacientes.length === 0) {
      listaEl.innerHTML =
        '<li style="padding:16px;color:var(--ink-muted);">Nenhum paciente encontrado.</li>';
      return;
    }
    listaEl.innerHTML = pacientes
      .map((p) => {
        const info = SELO_INFO[p.selo];
        const badge = info
          ? `<span class="badge ${info.classe}">${info.texto}</span>`
          : `<span class="badge estavel">Sem sessões</span>`;
        const ultimaSessao = escaparHtml(p.ultimaSessao || "-");
        // Alta e Inativo saem do fluxo normal, então ganham um selo próprio
        // ao lado do nome; "Ativo" é o caso comum e não precisa de marca.
        const selosTratamento = {
          Alta: '<span class="badge-status alta">Alta</span>',
          Inativo: '<span class="badge-status inativo">Inativo</span>',
        };
        const seloTratamento = selosTratamento[p.status] || "";
        return `
          <li class="pat-item" data-id="${escaparHtml(p.id)}" style="cursor:pointer;">
            <div class="avatar-sm" aria-hidden="true"></div>
            <div class="pat-name">${escaparHtml(p.nome)}${seloTratamento}</div>
            <div class="pat-meta-m mobile-only">Última sessão<br/>${ultimaSessao}</div>
            <div class="pat-date desktop-only">${ultimaSessao}</div>
            <div class="pat-status">${badge}</div>
          </li>`;
      })
      .join("");
    // A foto entra pelo CSSOM, nunca colada num style="" do template (SEC-02).
    listaEl.querySelectorAll(".avatar-sm").forEach((el, i) => aplicarFoto(el, pacientes[i].foto));
    RehabitAnim.staggerList(listaEl);
  }

  let todosOsPacientes = [];

  function statusAtual() {
    const campo = document.querySelector("[data-filtro-status]");
    return campo ? campo.value : "";
  }

  function aplicarFiltroEOrdenacao() {
    const termo = (buscaAtual() || "").trim().toLowerCase();
    const criterio = filtroAtual() || "Alfabética";
    const status = statusAtual();

    let filtrados = todosOsPacientes;
    if (termo) {
      filtrados = filtrados.filter((p) => p.nome.toLowerCase().includes(termo));
    }
    if (status) {
      // Pacientes antigos podem estar sem status gravado; tratamos como "Ativo".
      filtrados = filtrados.filter((p) => (p.status || "Ativo") === status);
    }
    renderizarPacientes(ordenarPacientes(filtrados, criterio));
  }

  const camposBusca = Array.from(document.querySelectorAll(".list-search"));
  const camposFiltro = Array.from(document.querySelectorAll(".list-filter"));

  function buscaAtual() {
    const ativo = camposBusca.find((el) => document.activeElement === el);
    return (ativo || camposBusca[0] || {}).value;
  }
  function filtroAtual() {
    const ativo = camposFiltro.find((el) => document.activeElement === el);
    return (ativo || camposFiltro[0] || {}).value;
  }

  camposBusca.forEach((campo) => {
    campo.addEventListener("input", () => {
      camposBusca.forEach((outro) => {
        if (outro !== campo) outro.value = campo.value;
      });
      aplicarFiltroEOrdenacao();
    });
  });
  camposFiltro.forEach((campo) => {
    campo.addEventListener("change", () => {
      camposFiltro.forEach((outro) => {
        if (outro !== campo) outro.value = campo.value;
      });
      aplicarFiltroEOrdenacao();
    });
  });
  document.querySelectorAll("[data-filtro-status]").forEach((campo) => {
    campo.addEventListener("change", aplicarFiltroEOrdenacao);
  });

  apiGet(`/pacientes?idFisioterapeuta=${idFisioterapeuta}`)
    .then((pacientes) => {
      todosOsPacientes = pacientes;
      if (pacientes.length === 0) {
        listaEl.innerHTML =
          '<li style="padding:16px;color:var(--ink-muted);">Nenhum paciente cadastrado ainda.</li>';
        return;
      }
      aplicarFiltroEOrdenacao();
    })
    .catch((err) => {
      listaEl.innerHTML = `<li style="padding:16px;color:var(--ink-muted);">${escaparHtml(err.message)}</li>`;
    });
})();
