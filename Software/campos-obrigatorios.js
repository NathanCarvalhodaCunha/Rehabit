/* Rehabit — campos obrigatórios dos formulários.

   O atributo required é a fonte única: o CSS põe o asterisco no rótulo a
   partir dele (styles.css, "Campos obrigatórios"), e este script confere o
   formulário inteiro no envio. Antes cada tela checava à mão e soltava um
   aviso genérico ("Preencha nome e CPF.") sem dizer onde estava o problema;
   agora cada campo que falta fica destacado, com o motivo embaixo dele, e o
   foco vai para o primeiro.

   Uso, no começo do submit de cada tela:
     if (!RehabitCampos.validar(form)) return;
   E, para uma regra que o HTML não expressa (senha e confirmação iguais):
     RehabitCampos.marcar(campo, "As senhas não conferem."); */
(function () {
  const CLASSE_ERRO = "campo-erro";

  // Limite de um campo como a pessoa lê: 2026-09-23 vira 23/09/2026.
  function limiteLegivel(campo, valor) {
    if (campo.type === "date") return valor.split("-").reverse().join("/");
    return valor;
  }

  function mensagemPara(campo) {
    const v = campo.validity;
    const ehQuando = campo.type === "date" || campo.type === "time";
    if (v.valueMissing) return campo.tagName === "SELECT" ? "Selecione uma opção." : "Campo obrigatório.";
    if (v.typeMismatch && campo.type === "email") return "Informe um e-mail válido.";
    if (v.tooShort) return `Use ao menos ${campo.minLength} caracteres.`;
    if (v.rangeUnderflow) {
      const min = limiteLegivel(campo, campo.min);
      return ehQuando ? `Escolha a partir de ${min}.` : `O valor mínimo é ${min}.`;
    }
    if (v.rangeOverflow) {
      const max = limiteLegivel(campo, campo.max);
      return ehQuando ? `Escolha até ${max}.` : `O valor máximo é ${max}.`;
    }
    if (v.badInput || v.stepMismatch) return "Valor inválido.";
    return campo.validationMessage || "Valor inválido.";
  }

  // O rótulo sem o asterisco, para citar o campo no aviso.
  function nomeDoCampo(campo) {
    const rotulo = campo.labels && campo.labels[0];
    return rotulo ? rotulo.textContent.replace(/\*/g, "").trim() : "";
  }

  function caixaDoCampo(campo) {
    return campo.closest(".field, .rh-field") || campo.parentElement;
  }

  function limpar(campo) {
    campo.removeAttribute("aria-invalid");
    const id = campo.dataset.erroId;
    if (!id) return;
    const aviso = document.getElementById(id);
    if (aviso) aviso.remove();
    const descritos = (campo.getAttribute("aria-describedby") || "").split(" ").filter((x) => x && x !== id);
    if (descritos.length) campo.setAttribute("aria-describedby", descritos.join(" "));
    else campo.removeAttribute("aria-describedby");
    delete campo.dataset.erroId;
  }

  function marcar(campo, texto) {
    limpar(campo);
    campo.setAttribute("aria-invalid", "true");
    const id = `erro-${campo.id || Math.random().toString(36).slice(2)}`;
    const aviso = document.createElement("p");
    aviso.className = CLASSE_ERRO;
    aviso.id = id;
    aviso.textContent = texto;
    caixaDoCampo(campo).appendChild(aviso);
    campo.dataset.erroId = id;
    const descritos = (campo.getAttribute("aria-describedby") || "").split(" ").filter(Boolean);
    campo.setAttribute("aria-describedby", descritos.concat(id).join(" "));
  }

  function validar(form) {
    const campos = Array.from(form.querySelectorAll("input, select, textarea")).filter(
      (c) => c.willValidate && c.type !== "file"
    );
    const invalidos = [];
    campos.forEach((campo) => {
      if (campo.checkValidity()) {
        limpar(campo);
      } else {
        marcar(campo, mensagemPara(campo));
        invalidos.push(campo);
      }
    });
    if (!invalidos.length) return true;

    invalidos[0].focus();
    const nomes = invalidos.map(nomeDoCampo).filter(Boolean);
    const lista = nomes.length ? `: ${nomes.join(", ")}.` : ".";
    RehabitToast.erro((invalidos.length > 1 ? "Confira os campos destacados" : "Confira o campo destacado") + lista);
    return false;
  }

  // O destaque sai assim que o campo fica certo, sem esperar outro envio.
  function aoEditar(e) {
    const campo = e.target;
    if (campo.getAttribute && campo.getAttribute("aria-invalid") === "true" && campo.checkValidity()) {
      limpar(campo);
    }
  }
  document.addEventListener("input", aoEditar, true);
  document.addEventListener("change", aoEditar, true);

  window.RehabitCampos = { validar, marcar, limpar };
})();
