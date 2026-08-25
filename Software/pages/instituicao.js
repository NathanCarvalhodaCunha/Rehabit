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

  // Card lateral "Próximas consultas": a agenda somada de todos os
  // profissionais da instituição.
  (function carregarProximasConsultas() {
    const agendaEl = document.querySelector(".agenda-list");
    if (!agendaEl) return;

    apiGet(`/agendamentos?idClinica=${sessao.id}`)
      .then((agendamentos) => {
        if (!agendamentos.length) {
          agendaEl.innerHTML =
            '<li style="padding:14px 4px;color:var(--ink-muted);">Nenhuma consulta agendada.</li>';
          return;
        }
        agendaEl.innerHTML = agendamentos
          .slice(0, 6)
          .map(
            (a) => `
          <li class="agenda-item">
            <div class="agenda-when">${formatarDataCurta(a.data)}<br/><span class="hora">${a.hora.slice(0, 5)}</span></div>
            <div>
              <div class="agenda-patient">${a.nomePaciente || "Paciente"}</div>
              <div class="agenda-obs">${a.nomeFisioterapeuta || ""}</div>
            </div>
          </li>`
          )
          .join("");
        if (typeof RehabitAnim !== "undefined") RehabitAnim.staggerList(agendaEl);
      })
      .catch(() => {
        agendaEl.innerHTML =
          '<li style="padding:14px 4px;color:var(--ink-muted);">Não foi possível carregar as consultas.</li>';
      });

    function formatarDataCurta(dataIso) {
      const [ano, mes, dia] = dataIso.split("-");
      return `${dia}/${mes}/${ano}`;
    }
  })();

  function ordenarFisioterapeutas(lista, criterio) {
    const copia = lista.slice();
    if (criterio === "Ordem alfabética") {
      copia.sort((a, b) => a.nome.localeCompare(b.nome, "pt-BR"));
    } else if (criterio === "Mais pacientes") {
      copia.sort((a, b) => b.pacientesAtivos - a.pacientesAtivos);
    }
    return copia;
  }

  function renderizarFisioterapeutas(fisioterapeutas) {
    if (fisioterapeutas.length === 0) {
      listaEl.innerHTML =
        '<li style="padding:16px;color:var(--ink-muted);">Nenhum profissional encontrado.</li>';
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
  }

  let todosOsFisioterapeutas = [];

  function aplicarFiltroEOrdenacao() {
    const termo = (buscaAtual() || "").trim().toLowerCase();
    const criterio = filtroAtual() || "Fisioterapeutas";
    const filtrados = termo
      ? todosOsFisioterapeutas.filter((f) => f.nome.toLowerCase().includes(termo))
      : todosOsFisioterapeutas;
    renderizarFisioterapeutas(ordenarFisioterapeutas(filtrados, criterio));
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

  apiGet(`/fisioterapeutas?idClinica=${sessao.id}`)
    .then((fisioterapeutas) => {
      todosOsFisioterapeutas = fisioterapeutas;
      if (fisioterapeutas.length === 0) {
        listaEl.innerHTML =
          '<li style="padding:16px;color:var(--ink-muted);">Nenhum profissional cadastrado ainda.</li>';
        return;
      }
      aplicarFiltroEOrdenacao();
    })
    .catch((err) => {
      listaEl.innerHTML = `<li style="padding:16px;color:var(--ink-muted);">${err.message}</li>`;
    });
})();
