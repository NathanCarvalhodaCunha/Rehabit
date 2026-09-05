/* Rehabit — lista completa dos profissionais da clínica.
   A Home mostra um resumo truncado; esta tela mostra todos, com a mesma
   busca e ordenação, e é o destino do botão "Ir para lista de profissionais". */
(function carregarProfissionais() {
  const listaEl = document.querySelector(".fisio-list");
  if (!listaEl) return;

  const sessao = getSessao();
  if (!sessao || sessao.tipo !== "CLINICA") return;

  const contadores = Array.from(document.querySelectorAll("[data-contador]"));
  const camposBusca = Array.from(document.querySelectorAll(".list-search"));
  const camposFiltro = Array.from(document.querySelectorAll(".list-filter"));

  let todos = [];

  function escaparHtml(texto) {
    const div = document.createElement("div");
    div.textContent = texto == null ? "" : String(texto);
    return div.innerHTML;
  }

  function buscaAtual() {
    const preenchido = camposBusca.find((c) => c.value.trim() !== "");
    return preenchido ? preenchido.value : "";
  }

  function filtroAtual() {
    const campo = camposFiltro[0];
    return campo ? campo.value : "Fisioterapeutas";
  }

  function ordenar(lista, criterio) {
    const copia = lista.slice();
    if (criterio === "Ordem alfabética") {
      copia.sort((a, b) => a.nome.localeCompare(b.nome, "pt-BR"));
    } else if (criterio === "Mais pacientes") {
      copia.sort((a, b) => b.pacientesAtivos - a.pacientesAtivos);
    }
    return copia;
  }

  /** "1 profissional" / "8 profissionais" — sem "(is)". */
  function textoContador(quantidade, filtrando) {
    const base = quantidade === 1 ? "1 profissional" : `${quantidade} profissionais`;
    return filtrando ? `${base} encontrado${quantidade === 1 ? "" : "s"}` : base;
  }

  function renderizar(lista, filtrando) {
    contadores.forEach((el) => {
      el.textContent = textoContador(lista.length, filtrando);
    });

    if (lista.length === 0) {
      listaEl.innerHTML = `<li class="fisio-vazio">${
        filtrando
          ? "Nenhum profissional encontrado. A busca olha o nome e a especialidade."
          : "Nenhum profissional cadastrado ainda. Use o botão acima para cadastrar o primeiro."
      }</li>`;
      return;
    }

    listaEl.innerHTML = lista
      .map((f) => {
        const foto = urlFoto(f.foto);
        const estiloAvatar = foto
          ? ` style="background-image:url('${foto}');background-size:cover;background-position:center;"`
          : "";
        return `
          <li class="fisio-item" data-id="${f.id}" style="cursor:pointer;">
            <div class="avatar-sm"${estiloAvatar} aria-hidden="true"></div>
            <div class="fisio-name">${escaparHtml(f.nome)}</div>
            <div class="fisio-spec">${escaparHtml(f.especialidade || "Fisioterapeuta")}</div>
            <div class="fisio-count"><span class="num">${f.pacientesAtivos}</span><span class="lbl mobile-only"> pacientes ativos</span></div>
          </li>`;
      })
      .join("");

    if (typeof RehabitAnim !== "undefined") RehabitAnim.staggerList(listaEl);
  }

  function aplicar() {
    const termo = buscaAtual().trim().toLowerCase();
    const filtrados = termo
      ? todos.filter(
          (f) =>
            f.nome.toLowerCase().includes(termo) ||
            (f.especialidade || "").toLowerCase().includes(termo)
        )
      : todos;
    renderizar(ordenar(filtrados, filtroAtual()), termo !== "");
  }

  listaEl.addEventListener("click", (e) => {
    const item = e.target.closest(".fisio-item[data-id]");
    if (item) window.location.href = `${paginaTema("perfil-profissional")}?id=${item.dataset.id}`;
  });

  camposBusca.forEach((campo) => {
    // Os dois campos (desktop e mobile) andam juntos, senão trocar de largura
    // no meio de uma busca mostraria um campo vazio sobre a lista filtrada.
    campo.addEventListener("input", () => {
      camposBusca.forEach((outro) => {
        if (outro !== campo) outro.value = campo.value;
      });
      aplicar();
    });
  });

  camposFiltro.forEach((campo) => {
    campo.addEventListener("change", () => {
      camposFiltro.forEach((outro) => {
        if (outro !== campo) outro.value = campo.value;
      });
      aplicar();
    });
  });

  apiGet(`/fisioterapeutas?idClinica=${sessao.id}`)
    .then((fisioterapeutas) => {
      todos = fisioterapeutas;
      aplicar();
    })
    .catch((err) => {
      listaEl.innerHTML = `<li class="fisio-vazio">${escaparHtml(err.message)}</li>`;
    });
})();
