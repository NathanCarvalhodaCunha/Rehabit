/* Rehabit — curva do movimento de uma sessão.

   A captura do goniômetro guarda não só a amplitude, mas o caminho inteiro
   que a articulação percorreu. Este módulo abre esse traçado por cima do
   histórico: dá para ver se o paciente chegou ao máximo de uma vez ou aos
   poucos, se travou no meio, se compensou voltando.

   Uso: RehabitCurva.abrir(idPaciente, idSessao, rotuloDaData)
   Reaproveita a marcação e o CSS do cartão de resumo do paciente. */
window.RehabitCurva = (function () {
  "use strict";

  var overlay = null;
  var corpo = null;
  var grafico = null;

  function montar() {
    overlay = document.createElement("div");
    overlay.className = "resumo-overlay is-hidden";
    overlay.innerHTML =
      '<div class="resumo-caixa curva-larga" role="dialog" aria-modal="true" aria-label="Curva do movimento">' +
      '<button type="button" class="resumo-fechar" aria-label="Fechar">' +
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>' +
      "</button>" +
      '<div class="resumo-corpo"></div>' +
      "</div>";
    document.body.appendChild(overlay);
    corpo = overlay.querySelector(".resumo-corpo");

    overlay.addEventListener("mousedown", function (e) {
      if (e.target === overlay) fechar();
    });
    overlay.querySelector(".resumo-fechar").addEventListener("click", fechar);
    document.addEventListener("keydown", function (e) {
      if (e.key === "Escape" && overlay && !overlay.classList.contains("is-hidden")) fechar();
    });
  }

  function fechar() {
    if (!overlay) return;
    overlay.classList.add("is-hidden");
    document.body.style.overflow = "";
    // O Chart.js segura o canvas e os listeners; sem destruir, cada abertura
    // deixa um gráfico órfão vivo na memória.
    if (grafico) {
      grafico.destroy();
      grafico = null;
    }
  }

  function escapar(valor) {
    var div = document.createElement("div");
    div.textContent = valor == null ? "" : String(valor);
    return div.innerHTML;
  }

  function grau(valor) {
    return Number(valor).toFixed(1).replace(".", ",") + "°";
  }

  function desenhar(pontos, rotulo) {
    var angulos = pontos.map(function (p) {
      return p[1];
    });
    var minimo = Math.min.apply(null, angulos);
    var maximo = Math.max.apply(null, angulos);
    var duracao = pontos.length ? pontos[pontos.length - 1][0] / 1000 : 0;

    corpo.innerHTML =
      '<div class="curva-topo"><h3>Curva do movimento</h3><span>' + escapar(rotulo) + "</span></div>" +
      '<div class="curva-indicadores">' +
      "<div><strong>" + grau(minimo) + "</strong><span>Mínimo</span></div>" +
      "<div><strong>" + grau(maximo) + "</strong><span>Máximo</span></div>" +
      "<div><strong>" + grau(maximo - minimo) + "</strong><span>Amplitude</span></div>" +
      "<div><strong>" + duracao.toFixed(1).replace(".", ",") + " s</strong><span>Duração</span></div>" +
      "</div>" +
      '<div class="curva-caixa"><canvas></canvas></div>' +
      '<p class="curva-legenda">Ângulo da articulação ao longo da gravação.</p>';

    if (typeof Chart === "undefined") {
      corpo.querySelector(".curva-caixa").innerHTML =
        '<p class="curva-legenda">Não foi possível carregar o gráfico. Verifique sua conexão.</p>';
      return;
    }

    var cores = coresGrafico();
    grafico = new Chart(corpo.querySelector("canvas"), {
      type: "line",
      data: {
        labels: pontos.map(function (p) {
          return (p[0] / 1000).toFixed(1);
        }),
        datasets: [
          {
            data: angulos,
            borderColor: cores.marca,
            backgroundColor: cores.preenchimento,
            borderWidth: 2,
            fill: true,
            tension: 0.25,
            // Centenas de pontos com marcador viram uma mancha; a linha só
            // ganha o ponto sob o cursor.
            pointRadius: 0,
            pointHoverRadius: 4,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: false,
        interaction: { intersect: false, mode: "index" },
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              title: function (ctx) {
                return ctx[0].label + " s";
              },
              label: function (ctx) {
                return grau(ctx.parsed.y);
              },
            },
          },
        },
        scales: {
          x: {
            title: { display: true, text: "segundos", color: cores.texto },
            grid: { color: cores.grade },
            ticks: { color: cores.texto, maxTicksLimit: 8 },
          },
          y: {
            title: { display: true, text: "graus", color: cores.texto },
            grid: { color: cores.grade },
            ticks: { color: cores.texto },
          },
        },
      },
    });
  }

  function abrir(idPaciente, idSessao, rotulo) {
    if (!overlay) montar();
    corpo.innerHTML = '<p class="resumo-carregando">Carregando a curva…</p>';
    overlay.classList.remove("is-hidden");
    document.body.style.overflow = "hidden";

    apiGet("/pacientes/" + idPaciente + "/sessoes/" + idSessao + "/curva")
      .then(function (pontos) {
        if (!Array.isArray(pontos) || !pontos.length) {
          corpo.innerHTML = '<p class="resumo-vazio">Esta sessão não tem curva gravada.</p>';
          return;
        }
        desenhar(pontos, rotulo);
      })
      .catch(function (err) {
        corpo.innerHTML = '<p class="resumo-vazio">' + escapar(err.message) + "</p>";
      });
  }

  return { abrir: abrir, fechar: fechar };
})();
