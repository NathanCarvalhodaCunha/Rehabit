/* Rehabit — painel de indicadores da Home.
   O backend decide quais cartões existem (clínica e profissional têm
   indicadores diferentes), então aqui só renderizamos a lista que vier. */
(function carregarIndicadores() {
  const grid = document.querySelector(".kpi-grid");
  if (!grid) return;

  const sessao = getSessao();
  if (!sessao) return;

  function classeDoDetalhe(detalhe) {
    if (!detalhe) return "";
    if (/^\+[1-9]/.test(detalhe)) return " pos";
    if (/^-[1-9]/.test(detalhe)) return " neg";
    return "";
  }

  apiGet("/estatisticas")
    .then((cards) => {
      if (!cards.length) {
        grid.style.display = "none";
        return;
      }
      grid.innerHTML = cards
        .map(
          (c) => `
        <div class="card kpi-card">
          <p class="kpi-rotulo">${c.rotulo}</p>
          <p class="kpi-valor">${c.valor}</p>
          <p class="kpi-detalhe${classeDoDetalhe(c.detalhe)}">${c.detalhe || ""}</p>
        </div>`
        )
        .join("");
      if (typeof RehabitAnim !== "undefined") RehabitAnim.staggerList(grid);
    })
    .catch(() => {
      // Indicador é informação secundária: se falhar, some em vez de
      // poluir a tela com erro sobre a lista de pacientes, que é o principal.
      grid.style.display = "none";
    });
})();
