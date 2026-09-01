/* Rehabit — calendário mensal da Agenda e exportação de consultas.

   Exportação sem OAuth de propósito: o link do Google Agenda abre o
   formulário já preenchido (o usuário só confirma) e o .ics é o formato
   padrão que Google, Outlook e Apple importam. Nenhum dos dois exige
   projeto no Google Cloud nem login. */
window.RehabitCalendario = (function () {
  "use strict";

  const MESES = [
    "janeiro", "fevereiro", "março", "abril", "maio", "junho",
    "julho", "agosto", "setembro", "outubro", "novembro", "dezembro",
  ];
  const DIAS_SEMANA = ["dom", "seg", "ter", "qua", "qui", "sex", "sáb"];
  const DURACAO_PADRAO_MIN = 45;

  function iso(ano, mes, dia) {
    return `${ano}-${String(mes + 1).padStart(2, "0")}-${String(dia).padStart(2, "0")}`;
  }

  /** "2026-09-15" + "14:30" -> "20260915T143000" (horário local, como o Google espera). */
  function carimboLocal(dataIso, horaIso, minutosASomar) {
    const [ano, mes, dia] = dataIso.split("-").map(Number);
    const [hora, minuto] = horaIso.slice(0, 5).split(":").map(Number);
    const d = new Date(ano, mes - 1, dia, hora, minuto);
    if (minutosASomar) d.setMinutes(d.getMinutes() + minutosASomar);
    const p = (n) => String(n).padStart(2, "0");
    return (
      d.getFullYear() + p(d.getMonth() + 1) + p(d.getDate()) +
      "T" + p(d.getHours()) + p(d.getMinutes()) + "00"
    );
  }

  function tituloDoEvento(agendamento) {
    return `Sessão de fisioterapia — ${agendamento.nomePaciente || "Paciente"}`;
  }

  function linkGoogle(agendamento, duracaoMin) {
    const inicio = carimboLocal(agendamento.data, agendamento.hora);
    const fim = carimboLocal(agendamento.data, agendamento.hora, duracaoMin || DURACAO_PADRAO_MIN);
    const params = new URLSearchParams({
      action: "TEMPLATE",
      text: tituloDoEvento(agendamento),
      dates: `${inicio}/${fim}`,
      details: agendamento.observacao || "Agendado pelo Rehabit.",
    });
    return `https://calendar.google.com/calendar/render?${params.toString()}`;
  }

  /** Quebra de linha CRLF e escape de vírgula/ponto-e-vírgula são exigências do formato iCalendar. */
  function escaparIcs(texto) {
    return String(texto || "").replace(/([,;\\])/g, "\\$1").replace(/\n/g, "\\n");
  }

  function montarIcs(agendamentos, duracaoMin) {
    const linhas = [
      "BEGIN:VCALENDAR",
      "VERSION:2.0",
      "PRODID:-//Rehabit//Agenda//PT-BR",
      "CALSCALE:GREGORIAN",
    ];
    agendamentos.forEach((a) => {
      linhas.push(
        "BEGIN:VEVENT",
        `UID:rehabit-${a.id}@rehabit`,
        `DTSTART:${carimboLocal(a.data, a.hora)}`,
        `DTEND:${carimboLocal(a.data, a.hora, duracaoMin || DURACAO_PADRAO_MIN)}`,
        `SUMMARY:${escaparIcs(tituloDoEvento(a))}`,
        `DESCRIPTION:${escaparIcs(a.observacao || "Agendado pelo Rehabit.")}`,
        "END:VEVENT"
      );
    });
    linhas.push("END:VCALENDAR");
    return linhas.join("\r\n");
  }

  function baixarIcs(agendamentos, duracaoMin) {
    if (!agendamentos.length) {
      RehabitToast.info("Não há consultas para exportar.");
      return;
    }
    const blob = new Blob([montarIcs(agendamentos, duracaoMin)], {
      type: "text/calendar;charset=utf-8",
    });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = "agenda-rehabit.ics";
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    RehabitToast.sucesso("Agenda exportada. Importe o arquivo no Google Agenda.");
  }

  /**
   * Monta o calendário dentro de `container`.
   * `aoSelecionarDia(dataIso | null)` roda a cada clique num dia — clicar
   * no dia já selecionado limpa a seleção.
   */
  function criar(container, aoSelecionarDia) {
    const hoje = new Date();
    let anoAtual = hoje.getFullYear();
    let mesAtual = hoje.getMonth();
    let diasComConsulta = new Set();
    // Categoria opcional: quando preenchida, o ponto do dia muda de cor
    // (ex.: consultas já realizadas x ainda agendadas).
    let diasAlternativos = new Set();
    let selecionado = null;
    let aoTrocarMes = null;

    container.innerHTML =
      '<div class="cal-head">' +
      '<h3 class="cal-mes"></h3>' +
      '<div class="cal-nav">' +
      '<button type="button" data-cal="anterior" aria-label="Mês anterior">' +
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"/></svg>' +
      "</button>" +
      '<button type="button" data-cal="proximo" aria-label="Próximo mês">' +
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"/></svg>' +
      "</button>" +
      "</div></div>" +
      '<div class="cal-semana">' + DIAS_SEMANA.map((d) => `<span>${d}</span>`).join("") + "</div>" +
      '<div class="cal-dias"></div>' +
      '<p class="cal-legenda"><i></i> agendada <span class="sep">·</span> ' +
      '<i class="realizada"></i> já realizada</p>';

    const tituloEl = container.querySelector(".cal-mes");
    const diasEl = container.querySelector(".cal-dias");

    function desenhar() {
      tituloEl.textContent = `${MESES[mesAtual]} de ${anoAtual}`;

      const primeiroDiaSemana = new Date(anoAtual, mesAtual, 1).getDay();
      const totalDias = new Date(anoAtual, mesAtual + 1, 0).getDate();
      const hojeIso = iso(hoje.getFullYear(), hoje.getMonth(), hoje.getDate());

      let html = "";
      for (let i = 0; i < primeiroDiaSemana; i++) {
        html += '<button type="button" class="cal-dia is-vazio" tabindex="-1"></button>';
      }
      for (let dia = 1; dia <= totalDias; dia++) {
        const dataIso = iso(anoAtual, mesAtual, dia);
        const classes = ["cal-dia"];
        // Dias que já passaram ficam apagados: a agenda não aceita marcar
        // para trás, então o calendário não deve convidar a tentar.
        if (dataIso < hojeIso) classes.push("is-passado");
        if (dataIso === hojeIso) classes.push("is-hoje");
        if (dataIso === selecionado) classes.push("is-selecionado");
        if (diasComConsulta.has(dataIso)) classes.push("tem-consulta");
        if (diasAlternativos.has(dataIso)) classes.push("tem-realizada");
        html += `<button type="button" class="${classes.join(" ")}" data-data="${dataIso}">${dia}</button>`;
      }
      diasEl.innerHTML = html;
    }

    container.addEventListener("click", (e) => {
      const nav = e.target.closest("[data-cal]");
      if (nav) {
        const passo = nav.dataset.cal === "anterior" ? -1 : 1;
        mesAtual += passo;
        if (mesAtual < 0) {
          mesAtual = 11;
          anoAtual--;
        } else if (mesAtual > 11) {
          mesAtual = 0;
          anoAtual++;
        }
        desenhar();
        if (aoTrocarMes) aoTrocarMes(anoAtual, mesAtual);
        return;
      }

      const diaBtn = e.target.closest(".cal-dia[data-data]");
      if (!diaBtn) return;
      selecionado = selecionado === diaBtn.dataset.data ? null : diaBtn.dataset.data;
      desenhar();
      if (aoSelecionarDia) aoSelecionarDia(selecionado);
    });

    desenhar();

    return {
      /**
       * Marca os dias com consulta. `alternativos` é opcional e recebe um
       * ponto de outra cor — usado para separar realizadas de agendadas.
       */
      marcarDias(agendamentos, alternativos) {
        diasComConsulta = new Set(agendamentos.map((a) => a.data));
        diasAlternativos = new Set((alternativos || []).map((a) => a.data));
        desenhar();
      },
      diaSelecionado() {
        return selecionado;
      },
      limparSelecao() {
        selecionado = null;
        desenhar();
      },
      mesVisivel() {
        return { ano: anoAtual, mes: mesAtual };
      },
      aoMudarDeMes(callback) {
        aoTrocarMes = callback;
      },
      /** Leva o calendário para o mês de uma data ISO, sem selecioná-la. */
      irPara(dataIso) {
        if (!dataIso) return;
        const [ano, mes] = dataIso.split("-").map(Number);
        anoAtual = ano;
        mesAtual = mes - 1;
        desenhar();
      },
    };
  }

  return { criar, linkGoogle, baixarIcs };
})();
