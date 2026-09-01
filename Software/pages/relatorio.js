/* Rehabit — geração dos relatórios em PDF.

   Antes o "Relatório em PDF" era só um window.print() da própria tela, e o
   navegador carimbava no rodapé a URL da página — incluindo o "?id=" do
   paciente. Aqui o PDF é montado do zero com o jsPDF: sai um arquivo limpo,
   assinado pela clínica, sem endereço nenhum impresso.

   Se a biblioteca não carregar (sem internet, por exemplo), quem chamou
   recebe a rejeição e pode cair de volta na impressão do navegador. */
window.RehabitRelatorio = (function () {
  "use strict";

  var URL_JSPDF = "https://cdn.jsdelivr.net/npm/jspdf@2.5.2/dist/jspdf.umd.min.js";

  // A4 em milímetros.
  var LARGURA = 210;
  var ALTURA = 297;
  var MARGEM = 15;
  var LARGURA_UTIL = LARGURA - MARGEM * 2;
  var RODAPE = 16; // altura reservada no pé da página

  var COR_TITULO = [17, 24, 39];
  var COR_TEXTO = [55, 65, 81];
  var COR_SUAVE = [107, 114, 128];
  var COR_MARCA = [21, 101, 216];
  var COR_LINHA = [226, 232, 240];
  var COR_FUNDO_CABECALHO = [241, 245, 249];

  var promessaBiblioteca = null;

  function carregarBiblioteca() {
    if (window.jspdf && window.jspdf.jsPDF) return Promise.resolve(window.jspdf.jsPDF);
    if (promessaBiblioteca) return promessaBiblioteca;

    promessaBiblioteca = new Promise(function (resolver, rejeitar) {
      var script = document.createElement("script");
      script.src = URL_JSPDF;
      script.onload = function () {
        if (window.jspdf && window.jspdf.jsPDF) resolver(window.jspdf.jsPDF);
        else rejeitar(new Error("Não foi possível preparar o gerador de PDF."));
      };
      script.onerror = function () {
        promessaBiblioteca = null;
        rejeitar(new Error("Não foi possível baixar o gerador de PDF. Verifique sua conexão."));
      };
      document.head.appendChild(script);
    });
    return promessaBiblioteca;
  }

  function texto(valor) {
    if (valor === null || valor === undefined) return "";
    return String(valor);
  }

  function formatarData(dataIso) {
    if (!dataIso) return "";
    var partes = texto(dataIso).slice(0, 10).split("-");
    if (partes.length !== 3) return texto(dataIso);
    return partes[2] + "/" + partes[1] + "/" + partes[0];
  }

  function formatarHora(horaIso) {
    return horaIso ? texto(horaIso).slice(0, 5) : "";
  }

  function hojePorExtenso() {
    var agora = new Date();
    return (
      agora.toLocaleDateString("pt-BR", { day: "2-digit", month: "long", year: "numeric" }) +
      " às " +
      agora.toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" })
    );
  }

  /** Nome de arquivo sem acento, espaço ou barra — seguro em qualquer sistema. */
  function nomeDeArquivo(base) {
    var limpo = texto(base)
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .replace(/[^a-zA-Z0-9]+/g, "-")
      .replace(/^-+|-+$/g, "")
      .toLowerCase();
    return (limpo || "relatorio") + ".pdf";
  }

  /**
   * Folha em branco com cabeçalho e rodapé próprios. Todo desenho passa por
   * aqui, então quebra de página, numeração e margens ficam num lugar só.
   */
  function criarFolha(jsPDF, cabecalho) {
    var doc = new jsPDF({ unit: "mm", format: "a4", compress: true });
    var y = 0;

    function novaPagina(primeira) {
      if (!primeira) doc.addPage();
      y = MARGEM;
      desenharCabecalho();
    }

    function desenharCabecalho() {
      doc.setFillColor.apply(doc, COR_FUNDO_CABECALHO);
      doc.rect(0, 0, LARGURA, 30, "F");
      doc.setFillColor.apply(doc, COR_MARCA);
      doc.rect(0, 30, LARGURA, 1.2, "F");

      doc.setFont("helvetica", "bold");
      doc.setFontSize(14);
      doc.setTextColor.apply(doc, COR_TITULO);
      doc.text(texto(cabecalho.clinica) || "Rehabit", MARGEM, 13);

      doc.setFont("helvetica", "normal");
      doc.setFontSize(10);
      doc.setTextColor.apply(doc, COR_SUAVE);
      doc.text(texto(cabecalho.titulo), MARGEM, 20);

      doc.setFontSize(8);
      doc.text("Emitido em " + hojePorExtenso(), LARGURA - MARGEM, 20, { align: "right" });

      y = 40;
    }

    function garantirEspaco(altura) {
      if (y + altura > ALTURA - RODAPE) novaPagina(false);
    }

    function avancar(mm) {
      y += mm;
    }

    function tituloSecao(rotulo) {
      garantirEspaco(14);
      doc.setFont("helvetica", "bold");
      doc.setFontSize(11);
      doc.setTextColor.apply(doc, COR_MARCA);
      doc.text(texto(rotulo).toUpperCase(), MARGEM, y);
      y += 2.5;
      doc.setDrawColor.apply(doc, COR_LINHA);
      doc.setLineWidth(0.3);
      doc.line(MARGEM, y, LARGURA - MARGEM, y);
      y += 6;
    }

    function paragrafo(conteudo, opcoes) {
      var config = opcoes || {};
      var tamanho = config.tamanho || 10;
      doc.setFont("helvetica", config.negrito ? "bold" : "normal");
      doc.setFontSize(tamanho);
      doc.setTextColor.apply(doc, config.cor || COR_TEXTO);

      var linhas = doc.splitTextToSize(texto(conteudo) || "—", LARGURA_UTIL);
      var alturaLinha = tamanho * 0.42 + 1.2;
      for (var i = 0; i < linhas.length; i++) {
        garantirEspaco(alturaLinha);
        doc.text(linhas[i], MARGEM, y);
        y += alturaLinha;
      }
      y += config.espacoDepois === undefined ? 2 : config.espacoDepois;
    }

    /** Pares "rótulo: valor" em duas colunas — a ficha de identificação. */
    function fichaDeDados(pares) {
      var larguraColuna = LARGURA_UTIL / 2;
      for (var i = 0; i < pares.length; i += 2) {
        garantirEspaco(11);
        for (var c = 0; c < 2; c++) {
          var par = pares[i + c];
          if (!par) continue;
          var x = MARGEM + c * larguraColuna;
          doc.setFont("helvetica", "bold");
          doc.setFontSize(8);
          doc.setTextColor.apply(doc, COR_SUAVE);
          doc.text(texto(par[0]).toUpperCase(), x, y);
          doc.setFont("helvetica", "normal");
          doc.setFontSize(10);
          doc.setTextColor.apply(doc, COR_TITULO);
          var valor = doc.splitTextToSize(texto(par[1]) || "—", larguraColuna - 6);
          doc.text(valor[0], x, y + 4.6);
        }
        y += 11;
      }
      y += 2;
    }

    /**
     * Tabela com quebra de página automática. `colunas` traz o título, o peso
     * relativo da largura e o alinhamento de cada coluna.
     */
    function tabela(colunas, linhas, vazio) {
      var pesoTotal = colunas.reduce(function (soma, c) {
        return soma + (c.peso || 1);
      }, 0);
      var larguras = colunas.map(function (c) {
        return ((c.peso || 1) / pesoTotal) * LARGURA_UTIL;
      });

      function cabecalhoTabela() {
        garantirEspaco(12);
        doc.setFillColor.apply(doc, COR_FUNDO_CABECALHO);
        doc.rect(MARGEM, y - 4.5, LARGURA_UTIL, 7, "F");
        doc.setFont("helvetica", "bold");
        doc.setFontSize(8.5);
        doc.setTextColor.apply(doc, COR_SUAVE);
        var x = MARGEM;
        colunas.forEach(function (coluna, i) {
          var alinhado = coluna.alinhamento === "direita";
          doc.text(texto(coluna.titulo).toUpperCase(), alinhado ? x + larguras[i] - 2 : x + 2, y,
            alinhado ? { align: "right" } : undefined);
          x += larguras[i];
        });
        y += 6;
      }

      if (!linhas.length) {
        paragrafo(vazio || "Nada registrado no período.", { cor: COR_SUAVE });
        return;
      }

      cabecalhoTabela();

      linhas.forEach(function (linha) {
        doc.setFont("helvetica", "normal");
        doc.setFontSize(9);

        // Mede a linha inteira antes de escrever, para não partir uma célula
        // alta no meio de uma quebra de página.
        var celulas = linha.map(function (valor, i) {
          return doc.splitTextToSize(texto(valor) || "—", larguras[i] - 4);
        });
        var alturaLinha = Math.max.apply(
          Math,
          celulas.map(function (c) {
            return c.length * 4.2;
          })
        ) + 3;

        if (y + alturaLinha > ALTURA - RODAPE) {
          novaPagina(false);
          cabecalhoTabela();
        }

        var x = MARGEM;
        celulas.forEach(function (conteudo, i) {
          var alinhado = colunas[i].alinhamento === "direita";
          doc.setTextColor.apply(doc, colunas[i].destaque ? COR_TITULO : COR_TEXTO);
          doc.setFont("helvetica", colunas[i].destaque ? "bold" : "normal");
          doc.text(conteudo, alinhado ? x + larguras[i] - 2 : x + 2, y,
            alinhado ? { align: "right" } : undefined);
          x += larguras[i];
        });

        y += alturaLinha;
        doc.setDrawColor.apply(doc, COR_LINHA);
        doc.setLineWidth(0.2);
        doc.line(MARGEM, y - 2.6, LARGURA - MARGEM, y - 2.6);
      });
      y += 4;
    }

    /** Faixa de números-resumo (ex.: total de consultas, faltas). */
    function indicadores(itens) {
      if (!itens.length) return;
      var largura = LARGURA_UTIL / itens.length;
      garantirEspaco(20);
      doc.setDrawColor.apply(doc, COR_LINHA);
      doc.setLineWidth(0.3);
      doc.roundedRect(MARGEM, y - 4, LARGURA_UTIL, 17, 2, 2, "S");
      itens.forEach(function (item, i) {
        var centro = MARGEM + largura * i + largura / 2;
        doc.setFont("helvetica", "bold");
        doc.setFontSize(13);
        doc.setTextColor.apply(doc, COR_MARCA);
        doc.text(texto(item.valor), centro, y + 3, { align: "center" });
        doc.setFont("helvetica", "normal");
        doc.setFontSize(8);
        doc.setTextColor.apply(doc, COR_SUAVE);
        doc.text(texto(item.rotulo), centro, y + 9, { align: "center" });
      });
      y += 22;
    }

    function finalizar(nomeBase) {
      var total = doc.getNumberOfPages();
      for (var pagina = 1; pagina <= total; pagina++) {
        doc.setPage(pagina);
        doc.setDrawColor.apply(doc, COR_LINHA);
        doc.setLineWidth(0.2);
        doc.line(MARGEM, ALTURA - 12, LARGURA - MARGEM, ALTURA - 12);
        doc.setFont("helvetica", "normal");
        doc.setFontSize(8);
        doc.setTextColor.apply(doc, COR_SUAVE);
        doc.text("Rehabit — documento gerado automaticamente", MARGEM, ALTURA - 7);
        doc.text("Página " + pagina + " de " + total, LARGURA - MARGEM, ALTURA - 7, { align: "right" });
      }
      doc.save(nomeDeArquivo(nomeBase));
    }

    novaPagina(true);

    return {
      tituloSecao: tituloSecao,
      paragrafo: paragrafo,
      fichaDeDados: fichaDeDados,
      tabela: tabela,
      indicadores: indicadores,
      avancar: avancar,
      finalizar: finalizar,
    };
  }

  var ROTULO_STATUS = {
    AGENDADA: "Agendada",
    REALIZADA: "Compareceu",
    FALTOU: "Faltou",
    REMARCADA: "Remarcada",
  };

  /**
   * Relatório de consultas passadas. Serve tanto à clínica (todos os
   * profissionais) quanto ao profissional (só as dele) — o que muda é a
   * lista recebida e se a coluna "Profissional" aparece.
   */
  function consultas(opcoes) {
    var lista = opcoes.consultas || [];
    return carregarBiblioteca().then(function (jsPDF) {
      var folha = criarFolha(jsPDF, {
        clinica: opcoes.clinica || "Rehabit",
        titulo: opcoes.titulo || "Relatório de consultas",
      });

      if (opcoes.subtitulo) {
        folha.paragrafo(opcoes.subtitulo, { negrito: true, tamanho: 11, cor: COR_TITULO });
      }

      var datas = lista
        .map(function (c) {
          return c.data;
        })
        .filter(Boolean)
        .sort();
      if (datas.length) {
        folha.paragrafo(
          "Período: " + formatarData(datas[0]) + " a " + formatarData(datas[datas.length - 1]),
          { cor: COR_SUAVE, tamanho: 9, espacoDepois: 6 }
        );
      }

      var pacientes = {};
      var porStatus = {};
      lista.forEach(function (c) {
        if (c.nomePaciente) pacientes[c.nomePaciente] = true;
        var status = c.status || "REALIZADA";
        porStatus[status] = (porStatus[status] || 0) + 1;
      });

      var resumo = [
        { valor: String(lista.length), rotulo: "Consultas" },
        { valor: String(Object.keys(pacientes).length), rotulo: "Pacientes" },
      ];
      if (porStatus.REALIZADA) resumo.push({ valor: String(porStatus.REALIZADA), rotulo: "Compareceram" });
      if (porStatus.FALTOU) resumo.push({ valor: String(porStatus.FALTOU), rotulo: "Faltas" });
      if (porStatus.REMARCADA) resumo.push({ valor: String(porStatus.REMARCADA), rotulo: "Remarcadas" });
      folha.indicadores(resumo);

      var colunas = [
        { titulo: "Data", peso: 1.1 },
        { titulo: "Hora", peso: 0.7 },
        { titulo: "Paciente", peso: 2.4, destaque: true },
      ];
      if (opcoes.mostrarProfissional) colunas.push({ titulo: "Profissional", peso: 2 });
      colunas.push({ titulo: "Situação", peso: 1.2 });
      colunas.push({ titulo: "Observação", peso: 2.2 });

      var linhas = lista.map(function (c) {
        var linha = [formatarData(c.data), formatarHora(c.hora), c.nomePaciente || "Paciente"];
        if (opcoes.mostrarProfissional) linha.push(c.nomeFisioterapeuta || "—");
        linha.push(ROTULO_STATUS[c.status] || "Realizada");
        linha.push(c.observacao || "—");
        return linha;
      });

      folha.tituloSecao("Consultas");
      folha.tabela(colunas, linhas, "Nenhuma consulta passada registrada.");
      folha.finalizar("relatorio-consultas-" + (opcoes.subtitulo || ""));
    });
  }

  /** Prontuário resumido de um paciente: ficha, meta, anamnese e sessões. */
  function paciente(opcoes) {
    var p = opcoes.paciente || {};
    var sessoes = opcoes.sessoes || [];
    var agendamentos = opcoes.agendamentos || [];

    return carregarBiblioteca().then(function (jsPDF) {
      var folha = criarFolha(jsPDF, {
        clinica: opcoes.clinica || "Rehabit",
        titulo: "Relatório de evolução do paciente",
      });

      folha.paragrafo(p.nome || "Paciente", { negrito: true, tamanho: 15, cor: COR_TITULO, espacoDepois: 5 });

      folha.tituloSecao("Identificação");
      folha.fichaDeDados([
        ["Idade", p.idade != null ? p.idade + " anos" : "—"],
        ["Sexo", p.sexo],
        ["Telefone", p.telefone],
        ["E-mail", p.email],
        ["Início do tratamento", formatarData(p.dataInicioTratamento)],
        ["Profissional responsável", p.nomeFisioterapeuta],
        ["Situação clínica", p.situacao],
        ["Status do tratamento", p.status || "Ativo"],
      ]);

      var comMedicao = sessoes.filter(function (s) {
        return s.amplitudeMedia != null;
      });
      var atual = comMedicao.length ? Number(comMedicao[0].amplitudeMedia) : null;
      var inicial = comMedicao.length ? Number(comMedicao[comMedicao.length - 1].amplitudeMedia) : null;

      var indicadores = [{ valor: String(sessoes.length), rotulo: "Sessões" }];
      if (atual != null) indicadores.push({ valor: atual + "°", rotulo: "Amplitude atual" });
      if (inicial != null && atual != null) {
        var ganho = atual - inicial;
        indicadores.push({ valor: (ganho >= 0 ? "+" : "") + ganho.toFixed(0) + "°", rotulo: "Ganho no período" });
      }
      if (p.metaAmplitude != null) {
        indicadores.push({ valor: Number(p.metaAmplitude) + "°", rotulo: "Meta" });
      }
      folha.indicadores(indicadores);

      if (p.metaAmplitude != null) {
        folha.tituloSecao("Meta de tratamento");
        var prazo = p.metaData ? " · prazo " + formatarData(p.metaData) : "";
        folha.paragrafo(
          atual != null
            ? "Amplitude atual de " + atual + "° para uma meta de " + Number(p.metaAmplitude) + "°" + prazo + "."
            : "Meta de " + Number(p.metaAmplitude) + "°, ainda sem medições registradas" + prazo + "."
        );
      }

      folha.tituloSecao("Anamnese");
      [
        ["Queixa principal", p.queixaPrincipal],
        ["Histórico clínico", p.historicoClinico],
        ["Medicamentos", p.medicamentos],
        ["Contraindicações", p.contraindicacoes],
      ].forEach(function (item) {
        folha.paragrafo(item[0], { negrito: true, tamanho: 9, cor: COR_SUAVE, espacoDepois: 0.5 });
        folha.paragrafo(item[1] || "Não informado", { espacoDepois: 3 });
      });

      folha.tituloSecao("Histórico de sessões");
      folha.tabela(
        [
          { titulo: "Data", peso: 1.1, destaque: true },
          { titulo: "Duração", peso: 1 },
          { titulo: "Amplitude", peso: 1 },
          { titulo: "Dor", peso: 0.8 },
          { titulo: "Prontuário", peso: 4 },
        ],
        sessoes.map(function (s) {
          return [
            formatarData(s.data),
            s.duracao != null ? s.duracao + " min" : "—",
            s.amplitudeMedia != null ? s.amplitudeMedia + "°" : "—",
            s.dor != null ? s.dor + "/10" : "—",
            s.observacoes || "—",
          ];
        }),
        "Ainda não há sessões registradas."
      );

      var passadas = agendamentos.filter(function (a) {
        return a.data && a.data < new Date().toISOString().slice(0, 10);
      });
      if (passadas.length) {
        folha.tituloSecao("Consultas passadas");
        folha.tabela(
          [
            { titulo: "Data", peso: 1.2, destaque: true },
            { titulo: "Hora", peso: 0.8 },
            { titulo: "Situação", peso: 1.4 },
            { titulo: "Observação", peso: 3 },
          ],
          passadas.map(function (a) {
            return [
              formatarData(a.data),
              formatarHora(a.hora),
              ROTULO_STATUS[a.status] || "Agendada",
              a.observacao || "—",
            ];
          }),
          "Nenhuma consulta passada."
        );
      }

      folha.finalizar("relatorio-" + (p.nome || "paciente"));
    });
  }

  return { consultas: consultas, paciente: paciente };
})();
