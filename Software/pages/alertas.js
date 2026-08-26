/* Rehabit — cartão de alertas na Home: o que o sistema detectou sozinho. */
(function carregarAlertas() {
  const cartao = document.querySelector("[data-alertas]");
  if (!cartao) return;

  const sessao = getSessao();
  if (!sessao) return;

  function escapar(texto) {
    const div = document.createElement("div");
    div.textContent = texto;
    return div.innerHTML;
  }

  const ICONES = {
    ATENCAO:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>',
    BOM:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 11.1V12a10 10 0 1 1-5.9-9.1"/><polyline points="22 4 12 14.01 9 11.01"/></svg>',
  };

  apiGet("/alertas")
    .then((alertas) => {
      if (!alertas.length) {
        // Sem nada a sinalizar o cartão sai da tela em vez de ocupar espaço
        // com uma mensagem vazia.
        cartao.style.display = "none";
        return;
      }

      const atencao = alertas.filter((a) => a.nivel === "ATENCAO").length;
      cartao.querySelector("[data-alertas-contagem]").textContent =
        atencao > 0 ? `${atencao} ${atencao === 1 ? "item precisa" : "itens precisam"} de atenção` : "tudo em ordem";

      cartao.querySelector("[data-alertas-lista]").innerHTML = alertas
        .map(
          (a) => `
        <li class="alerta-item ${a.nivel === "BOM" ? "is-bom" : "is-atencao"}" data-id-paciente="${a.idPaciente}">
          <span class="alerta-icone">${ICONES[a.nivel] || ICONES.ATENCAO}</span>
          <span class="alerta-texto">
            <strong>${escapar(a.titulo)}</strong>
            <span>${escapar(a.descricao)}</span>
          </span>
        </li>`
        )
        .join("");

      if (typeof RehabitAnim !== "undefined") {
        RehabitAnim.staggerList(cartao.querySelector("[data-alertas-lista]"));
      }
    })
    .catch(() => {
      cartao.style.display = "none";
    });

  cartao.addEventListener("click", (e) => {
    const item = e.target.closest("[data-id-paciente]");
    if (item) window.location.href = `${paginaTema("paciente")}?id=${item.dataset.idPaciente}`;
  });
})();
