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
  // Mesmo par de cores dos gráficos da tela: azul para amplitude, vermelho
  // suave para dor.
  var COR_DOR = [229, 115, 115];

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

  /** "dd/mm" — o ano não cabe nos rótulos do eixo x de um gráfico. */
  function formatarDataCurta(dataIso) {
    if (!dataIso) return "";
    var partes = texto(dataIso).slice(0, 10).split("-");
    if (partes.length !== 3) return texto(dataIso);
    return partes[2] + "/" + partes[1];
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

    /* ----------------------------------------------------------------
       Gráficos

       Desenhados com as primitivas do próprio jsPDF (linhas e retângulos),
       e não como uma imagem tirada dos canvas do Chart.js da tela. Três
       razões: o relatório de consultas é pedido de telas que não têm
       gráfico nenhum para fotografar; vetor não pixeliza no zoom nem na
       impressão, que é o destino do arquivo; e um canvas de tema escuro
       viraria um retângulo escuro no meio do papel branco.
       ---------------------------------------------------------------- */

    /** Escala "valor -> pixel", com uma folga para a linha não colar na borda. */
    function criarEscala(valores, base) {
      var numeros = valores.filter(function (v) {
        return typeof v === "number" && isFinite(v);
      });
      var minimo = Math.min.apply(Math, numeros);
      var maximo = Math.max.apply(Math, numeros);
      if (base != null) {
        minimo = Math.min(minimo, base);
        maximo = Math.max(maximo, base);
      }
      // Série constante (uma sessão só, ou todas iguais) não tem intervalo:
      // sem esta abertura a divisão daria zero e a linha sairia fora do quadro.
      if (minimo === maximo) {
        minimo -= 1;
        maximo += 1;
      }
      var folga = (maximo - minimo) * 0.12;
      return { minimo: minimo - folga, maximo: maximo + folga };
    }

    /** Mostra no máximo `limite` rótulos no eixo x, sem embolar o texto. */
    function rotulosRareados(rotulos, limite) {
      var passo = Math.ceil(rotulos.length / limite);
      return rotulos.map(function (rotulo, i) {
        return i % passo === 0 || i === rotulos.length - 1 ? rotulo : "";
      });
    }

    function desenharMolduraEEixos(caixa, escala, sufixo) {
      var linhas = 4;
      doc.setFontSize(7);
      doc.setFont("helvetica", "normal");
      for (var i = 0; i <= linhas; i++) {
        var yLinha = caixa.baixo - (caixa.altura / linhas) * i;
        doc.setDrawColor.apply(doc, COR_LINHA);
        doc.setLineWidth(0.15);
        doc.line(caixa.esquerda, yLinha, caixa.direita, yLinha);
        var valor = escala.minimo + ((escala.maximo - escala.minimo) / linhas) * i;
        doc.setTextColor.apply(doc, COR_SUAVE);
        doc.text(Math.round(valor) + (sufixo || ""), caixa.esquerda - 1.5, yLinha + 1, { align: "right" });
      }
    }

    function desenharRotulosX(caixa, rotulos) {
      var visiveis = rotulosRareados(rotulos, 9);
      var passo = caixa.largura / rotulos.length;
      doc.setFontSize(7);
      doc.setTextColor.apply(doc, COR_SUAVE);
      visiveis.forEach(function (rotulo, i) {
        if (!rotulo) return;
        doc.text(texto(rotulo), caixa.esquerda + passo * (i + 0.5), caixa.baixo + 4, { align: "center" });
      });
    }

    function legenda(caixa, itens) {
      if (itens.length < 2) return;
      var x = caixa.esquerda;
      var yLegenda = caixa.baixo + 8.5;
      doc.setFontSize(7.5);
      itens.forEach(function (item) {
        doc.setFillColor.apply(doc, item.cor);
        doc.circle(x + 1, yLegenda - 0.8, 1, "F");
        doc.setTextColor.apply(doc, COR_SUAVE);
        doc.setFont("helvetica", "normal");
        doc.text(texto(item.rotulo), x + 3.5, yLegenda);
        x += doc.getTextWidth(texto(item.rotulo)) + 11;
      });
    }

    /**
     * Reserva o espaço do gráfico e devolve a caixa de desenho em mm.
     *
     * O título da seção entra na mesma reserva, e é escrito só depois de a
     * folha estar garantida: quando a quebra de página acontecia na hora de
     * desenhar, o título ficava órfão no pé de uma folha e o gráfico
     * aparecia sozinho no alto da seguinte.
     */
    function reservarCaixa(altura, comEixoDireito, titulo, comLegenda) {
      // Abaixo do quadro cabem os rótulos do eixo x e, quando há mais de uma
      // série, a legenda. Reservar a legenda em gráfico que não tem uma
      // empurrava a folha adiante por poucos milímetros e deixava um vão em
      // branco no pé da página anterior.
      var alturaTotal = altura + (comLegenda ? 16 : 10);
      garantirEspaco(alturaTotal + (titulo ? 9 : 0));
      if (titulo) tituloSecao(titulo);
      var esquerda = MARGEM + 11;
      var direita = LARGURA - MARGEM - (comEixoDireito ? 11 : 1);
      var caixa = {
        esquerda: esquerda,
        direita: direita,
        largura: direita - esquerda,
        topo: y,
        baixo: y + altura,
        altura: altura,
      };
      y += alturaTotal;
      return caixa;
    }

    /**
     * Linhas com marcadores. `series` aceita duas escalas — a segunda sai
     * pelo eixo da direita — para a leitura que interessa no prontuário:
     * a amplitude subindo enquanto a dor cai.
     */
    function graficoLinha(opcoes) {
      function temNumero(s) {
        return (
          s &&
          s.valores &&
          s.valores.some(function (v) {
            return typeof v === "number" && isFinite(v);
          })
        );
      }

      var todas = opcoes.series || [];
      /* A primeira série é o assunto do gráfico: dá a escala da esquerda e é
         a dona da linha de meta. Sem ela não há gráfico — descartá-la e
         promover a segunda desenharia a dor sob o título da amplitude, com a
         meta de amplitude traçada na escala de 0 a 10. */
      if (!temNumero(todas[0]) || !opcoes.rotulos || opcoes.rotulos.length < 2) return false;

      // Já a série de apoio é opcional: sem nenhum número (sessões sem dor
      // registrada, por exemplo) ela sai, e com ela o eixo da direita e a
      // legenda que não teriam nada para mostrar.
      var series = todas.slice(0, 1).concat(todas.slice(1).filter(temNumero));

      var secundaria = series[1];
      var caixa = reservarCaixa(opcoes.altura || 46, !!secundaria, opcoes.titulo, series.length > 1);
      var escalaPrincipal = criarEscala(series[0].valores, opcoes.meta != null ? opcoes.meta : null);
      desenharMolduraEEixos(caixa, escalaPrincipal, series[0].sufixo);
      desenharRotulosX(caixa, opcoes.rotulos);

      var passo = caixa.largura / opcoes.rotulos.length;

      function pontoY(valor, escala) {
        var proporcao = (valor - escala.minimo) / (escala.maximo - escala.minimo);
        return caixa.baixo - proporcao * caixa.altura;
      }

      // Meta de tratamento: uma tracejada de referência atrás das séries.
      if (opcoes.meta != null) {
        var yMeta = pontoY(Number(opcoes.meta), escalaPrincipal);
        doc.setDrawColor(148, 163, 184);
        doc.setLineWidth(0.3);
        if (doc.setLineDashPattern) doc.setLineDashPattern([1.4, 1.2], 0);
        doc.line(caixa.esquerda, yMeta, caixa.direita, yMeta);
        if (doc.setLineDashPattern) doc.setLineDashPattern([], 0);
        doc.setFontSize(7);
        doc.setTextColor(148, 163, 184);
        doc.text("Meta " + Number(opcoes.meta) + (series[0].sufixo || ""), caixa.direita, yMeta - 1.2, {
          align: "right",
        });
      }

      series.forEach(function (serie, indice) {
        var escala = indice === 0 ? escalaPrincipal : criarEscala(serie.valores);
        if (indice === 1) {
          // Eixo da direita, na cor da própria série, para não confundir
          // com os números da esquerda.
          doc.setFontSize(7);
          doc.setTextColor.apply(doc, serie.cor);
          for (var i = 0; i <= 4; i++) {
            var yLinha = caixa.baixo - (caixa.altura / 4) * i;
            var valor = escala.minimo + ((escala.maximo - escala.minimo) / 4) * i;
            doc.text(Math.round(valor) + (serie.sufixo || ""), caixa.direita + 1.5, yLinha + 1);
          }
        }

        doc.setDrawColor.apply(doc, serie.cor);
        doc.setFillColor.apply(doc, serie.cor);
        doc.setLineWidth(indice === 0 ? 0.7 : 0.5);

        var anteriorX = null;
        var anteriorY = null;
        serie.valores.forEach(function (valor, i) {
          if (valor == null || !isFinite(valor)) return;
          var px = caixa.esquerda + passo * (i + 0.5);
          var py = pontoY(Number(valor), escala);
          if (anteriorX != null) doc.line(anteriorX, anteriorY, px, py);
          anteriorX = px;
          anteriorY = py;
        });

        // Os marcadores vêm depois das linhas para ficarem por cima delas.
        serie.valores.forEach(function (valor, i) {
          if (valor == null || !isFinite(valor)) return;
          doc.circle(caixa.esquerda + passo * (i + 0.5), pontoY(Number(valor), escala), indice === 0 ? 0.9 : 0.7, "F");
        });
      });

      legenda(
        caixa,
        series.map(function (s) {
          return { rotulo: s.rotulo, cor: s.cor };
        })
      );
      return true;
    }

    /** Barras verticais — uma série só. */
    function graficoBarras(opcoes) {
      var valores = (opcoes.valores || []).map(Number);
      if (!valores.length || !opcoes.rotulos || !opcoes.rotulos.length) return false;

      var caixa = reservarCaixa(opcoes.altura || 42, false, opcoes.titulo, false);
      // Barra que não começa no zero mente sobre a proporção entre elas.
      var maximo = Math.max.apply(Math, valores);
      var escala = { minimo: 0, maximo: maximo > 0 ? maximo * 1.15 : 1 };
      desenharMolduraEEixos(caixa, escala, opcoes.sufixo);
      desenharRotulosX(caixa, opcoes.rotulos);

      var passo = caixa.largura / valores.length;
      var largura = Math.min(passo * 0.6, 9);
      var cor = opcoes.cor || COR_MARCA;
      doc.setFillColor.apply(doc, cor);
      valores.forEach(function (valor, i) {
        if (!isFinite(valor)) return;
        var altura = (valor / escala.maximo) * caixa.altura;
        if (altura <= 0) return;
        doc.rect(caixa.esquerda + passo * (i + 0.5) - largura / 2, caixa.baixo - altura, largura, altura, "F");
      });
      return true;
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
      graficoLinha: graficoLinha,
      graficoBarras: graficoBarras,
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

      /* Volume por mês: a tabela abaixo diz o que aconteceu em cada dia, o
         gráfico diz se o movimento da clínica cresceu ou caiu no período —
         que é a pergunta que se faz a um relatório de consultas. */
      var porMes = {};
      lista.forEach(function (c) {
        if (!c.data) return;
        var chave = texto(c.data).slice(0, 7); // aaaa-mm
        porMes[chave] = (porMes[chave] || 0) + 1;
      });
      var meses = Object.keys(porMes).sort();
      if (meses.length >= 2) {
        folha.graficoBarras({
          titulo: "Consultas por mês",
          rotulos: meses.map(function (chave) {
            var partes = chave.split("-");
            return partes[1] + "/" + partes[0].slice(2);
          }),
          valores: meses.map(function (chave) {
            return porMes[chave];
          }),
        });
      }

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

      /* A API devolve as sessões da mais nova para a mais antiga, que é a
         ordem certa para a tabela do histórico e a errada para um gráfico de
         evolução: no eixo do tempo o tratamento anda para a direita. */
      var cronologicas = sessoes.slice().reverse();
      var rotulos = cronologicas.map(function (s) {
        return formatarDataCurta(s.data);
      });
      var amplitudes = cronologicas.map(function (s) {
        return s.amplitudeMedia != null ? Number(s.amplitudeMedia) : null;
      });
      var dores = cronologicas.map(function (s) {
        return s.dor != null ? Number(s.dor) : null;
      });

      if (rotulos.length >= 2) {
        var desenhou = folha.graficoLinha({
          titulo: "Evolução da amplitude",
          rotulos: rotulos,
          meta: p.metaAmplitude != null ? Number(p.metaAmplitude) : null,
          series: [
            { rotulo: "Amplitude (°)", valores: amplitudes, cor: COR_MARCA, sufixo: "°" },
            { rotulo: "Dor (0–10)", valores: dores, cor: COR_DOR, sufixo: "" },
          ],
        });
        if (!desenhou) {
          folha.tituloSecao("Evolução da amplitude");
          folha.paragrafo("Ainda não há amplitudes medidas para traçar a evolução.", { cor: COR_SUAVE });
        }

        folha.graficoBarras({
          titulo: "Duração das sessões",
          rotulos: rotulos,
          valores: cronologicas.map(function (s) {
            return s.duracao != null ? Number(s.duracao) : 0;
          }),
          sufixo: " min",
        });
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
