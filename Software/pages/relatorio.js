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
  var COR_DOR = [217, 119, 6];   // âmbar: a dor tem de se distinguir da amplitude
  var COR_META = [5, 150, 105];  // verde: a linha de referência do objetivo

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
  /**
   * Passo de grade em número redondo: sem isso o eixo saía com 129°, 108°,
   * 86° — números que ninguém lê de relance num relatório impresso.
   */
  function passoRedondo(bruto) {
    var potencia = Math.pow(10, Math.floor(Math.log(Math.max(bruto, 0.1)) / Math.LN10));
    var candidatos = [1, 2, 2.5, 5, 10];
    for (var i = 0; i < candidatos.length; i++) {
      if (candidatos[i] * potencia >= bruto) return candidatos[i] * potencia;
    }
    return 10 * potencia;
  }

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

    /**
     * Gráfico de evolução desenhado em vetor no próprio PDF — sem depender do
     * Chart.js nem de imagem da tela: o relatório pode ser gerado da Agenda,
     * onde gráfico nenhum está desenhado, e uma captura de canvas sairia
     * serrilhada na impressão.
     *
     *   rotulos    — legendas do eixo X, na ordem cronológica
     *   series     — [{ nome, cor, valores: [n|null], eixo: "esq"|"dir" }]
     *   eixoDir    — { titulo, min, max } quando houver série à direita
     *   referencia — { valor, rotulo } para a linha da meta
     */
    function grafico(config) {
      var series = (config.series || []).filter(function (s) {
        return s.valores.some(function (v) {
          return v != null;
        });
      });
      if (!series.length || !config.rotulos.length) return;

      var ALTURA_AREA = 46;
      var ALTURA_TOTAL = ALTURA_AREA + 22;
      garantirEspaco(ALTURA_TOTAL);

      var temDireita = series.some(function (s) {
        return s.eixo === "dir";
      });
      var esquerda = MARGEM + 11;
      var direita = LARGURA - MARGEM - (temDireita ? 11 : 4);
      var topo = y + 7; // espaço para a legenda
      var base = topo + ALTURA_AREA;

      // Escala da esquerda: a folga sai do intervalo dos dados, e só depois a
      // meta entra — folga calculada sobre a meta (que costuma estar bem
      // acima) achataria a evolução no rodapé do gráfico.
      var valoresEsq = [];
      series.forEach(function (s) {
        if (s.eixo !== "dir") {
          s.valores.forEach(function (v) {
            if (v != null) valoresEsq.push(Number(v));
          });
        }
      });
      var minEsq = Math.min.apply(null, valoresEsq);
      var maxEsq = Math.max.apply(null, valoresEsq);
      var folga = Math.max((maxEsq - minEsq) * 0.12, 3);
      minEsq -= folga;
      maxEsq += folga;
      if (config.referencia && config.referencia.valor != null) {
        var meta = Number(config.referencia.valor);
        minEsq = Math.min(minEsq, meta);
        maxEsq = Math.max(maxEsq, meta + folga / 2);
      }
      var passo = passoRedondo((maxEsq - minEsq) / 4);
      minEsq = Math.max(0, Math.floor(minEsq / passo) * passo);
      maxEsq = Math.ceil(maxEsq / passo) * passo;
      if (maxEsq === minEsq) maxEsq = minEsq + passo;
      var divisoes = Math.min(Math.round((maxEsq - minEsq) / passo), 6);

      var minDir = (config.eixoDir && config.eixoDir.min) || 0;
      var maxDir = (config.eixoDir && config.eixoDir.max) || 10;

      function xDe(i) {
        if (config.rotulos.length === 1) return (esquerda + direita) / 2;
        return esquerda + ((direita - esquerda) * i) / (config.rotulos.length - 1);
      }
      function yDe(valor, eixo) {
        var min = eixo === "dir" ? minDir : minEsq;
        var max = eixo === "dir" ? maxDir : maxEsq;
        return base - ((Number(valor) - min) / (max - min)) * ALTURA_AREA;
      }

      // Legenda
      var xLegenda = MARGEM + 1;
      doc.setFontSize(7.5);
      doc.setFont("helvetica", "bold");
      series.forEach(function (s) {
        doc.setDrawColor.apply(doc, s.cor);
        doc.setLineWidth(0.9);
        doc.line(xLegenda, y + 1.6, xLegenda + 5, y + 1.6);
        doc.setTextColor.apply(doc, COR_SUAVE);
        doc.text(texto(s.nome), xLegenda + 6.5, y + 2.6);
        xLegenda += 8 + doc.getTextWidth(texto(s.nome));
      });

      // Grade e rótulos do eixo esquerdo
      doc.setLineWidth(0.2);
      doc.setFont("helvetica", "normal");
      doc.setFontSize(7);
      for (var i = 0; i <= divisoes; i++) {
        var valor = minEsq + ((maxEsq - minEsq) * i) / divisoes;
        var linhaY = base - (ALTURA_AREA * i) / divisoes;
        doc.setDrawColor.apply(doc, COR_LINHA);
        doc.line(esquerda, linhaY, direita, linhaY);
        doc.setTextColor.apply(doc, COR_SUAVE);
        doc.text(String(Math.round(valor)) + "\u00B0", esquerda - 2, linhaY + 1, { align: "right" });
        if (temDireita) {
          var valorDir = minDir + ((maxDir - minDir) * i) / divisoes;
          doc.text(String(Math.round(valorDir)), direita + 2, linhaY + 1);
        }
      }

      // Linha da meta
      if (config.referencia && config.referencia.valor != null) {
        var yMeta = yDe(config.referencia.valor, "esq");
        doc.setDrawColor.apply(doc, COR_META);
        doc.setLineWidth(0.4);
        doc.setLineDashPattern([1.6, 1.4], 0);
        doc.line(esquerda, yMeta, direita, yMeta);
        doc.setLineDashPattern([], 0);
        doc.setTextColor.apply(doc, COR_META);
        doc.setFontSize(7);
        doc.text(texto(config.referencia.rotulo), direita, yMeta - 1.4, { align: "right" });
      }

      // Séries
      series.forEach(function (s) {
        doc.setDrawColor.apply(doc, s.cor);
        doc.setFillColor.apply(doc, s.cor);
        doc.setLineWidth(s.eixo === "dir" ? 0.5 : 0.8);
        if (s.eixo === "dir") doc.setLineDashPattern([1.4, 1.2], 0);

        var anterior = null;
        s.valores.forEach(function (valor, i) {
          if (valor == null) {
            anterior = null;
            return;
          }
          var ponto = { x: xDe(i), y: yDe(valor, s.eixo) };
          if (anterior) doc.line(anterior.x, anterior.y, ponto.x, ponto.y);
          anterior = ponto;
        });
        doc.setLineDashPattern([], 0);

        s.valores.forEach(function (valor, i) {
          if (valor == null) return;
          doc.circle(xDe(i), yDe(valor, s.eixo), s.eixo === "dir" ? 0.7 : 1, "F");
        });
      });

      // Rótulos do eixo X — no máximo seis, para não virar borrão.
      var passo = Math.ceil(config.rotulos.length / 6);
      doc.setFont("helvetica", "normal");
      doc.setFontSize(7);
      doc.setTextColor.apply(doc, COR_SUAVE);
      config.rotulos.forEach(function (rotulo, i) {
        if (i % passo !== 0 && i !== config.rotulos.length - 1) return;
        doc.text(texto(rotulo), xDe(i), base + 4.5, { align: "center" });
      });

      doc.setDrawColor.apply(doc, COR_LINHA);
      doc.setLineWidth(0.3);
      doc.line(esquerda, base, direita, base);

      y = base + 12;
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
      grafico: grafico,
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

      // Evolução em gráfico: a tabela de sessões diz os números, mas quem lê o
      // relatório quer ver de relance se a amplitude sobe e a dor cai.
      folha.tituloSecao("Evolução");
      var cronologicas = sessoes
        .filter(function (s) {
          return s.data && (s.amplitudeMedia != null || s.dor != null);
        })
        .sort(function (a, b) {
          return a.data < b.data ? -1 : a.data > b.data ? 1 : 0;
        });

      if (cronologicas.filter(function (s) { return s.amplitudeMedia != null; }).length >= 2) {
        folha.grafico({
          rotulos: cronologicas.map(function (s) {
            return formatarData(s.data).slice(0, 5);
          }),
          series: [
            {
              nome: "Amplitude (graus)",
              cor: COR_MARCA,
              eixo: "esq",
              valores: cronologicas.map(function (s) {
                return s.amplitudeMedia != null ? Number(s.amplitudeMedia) : null;
              }),
            },
            {
              nome: "Dor relatada (0-10)",
              cor: COR_DOR,
              eixo: "dir",
              valores: cronologicas.map(function (s) {
                return s.dor != null ? Number(s.dor) : null;
              }),
            },
          ],
          eixoDir: { min: 0, max: 10 },
          referencia:
            p.metaAmplitude != null
              ? { valor: Number(p.metaAmplitude), rotulo: "Meta " + Number(p.metaAmplitude) + "\u00B0" }
              : null,
        });
      } else {
        folha.paragrafo(
          "Ainda não há medições suficientes para desenhar a evolução — o gráfico aparece a partir de duas sessões com amplitude registrada."
        );
      }

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
