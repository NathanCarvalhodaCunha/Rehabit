(function editarPerfilProfissional() {
  const form = document.querySelector(".edit-grid");
  if (!form || !document.getElementById("p-coffito")) return;

  const sessao = getSessao();
  if (!sessao) return;

  // Uma clínica pode abrir esta tela para editar os dados de um dos seus
  // profissionais, vinda de perfil-profissional.html.
  const idParam = idDaUrl("id");
  const editandoOutroProfissional = sessao.tipo === "CLINICA" && !!idParam;
  if (sessao.tipo !== "FISIOTERAPEUTA" && !editandoOutroProfissional) return;
  const idAlvo = editandoOutroProfissional ? idParam : sessao.id;

  if (editandoOutroProfissional) {
    document.querySelector(".mobile-bottomnav")?.remove();
    document.querySelectorAll('.sidebar .nav a[href="./dispositivo.html"], .sidebar .nav a[href="./configuracoes.html"]')
      .forEach((el) => el.remove());
    const homeLink = document.querySelector('.sidebar .nav a[href="./profissional.html"]');
    if (homeLink) homeLink.href = paginaTema("instituicao");
    const perfilLink = document.querySelector('.sidebar .nav a[href="./perfil-profissional.html"]');
    if (perfilLink) perfilLink.href = `${paginaTema("perfil-profissional")}?id=${idAlvo}`;

    const campoSenha = document.getElementById("p-senha-atual");
    const secaoSenha = campoSenha ? campoSenha.closest(".edit-section") : null;
    if (secaoSenha) {
      const separador = secaoSenha.previousElementSibling;
      if (separador && separador.classList.contains("edit-sep")) separador.remove();
      secaoSenha.remove();
    }
  }

  let fotoAtual = null;

  apiGet(`/fisioterapeutas/${idAlvo}`)
    .then((f) => {
      fotoAtual = f.foto;
      document.querySelector(".edit-name").textContent = f.nome;
      document.querySelector(".edit-role").textContent = f.especialidade || "";
      document.querySelector(".edit-id").textContent = `ID-${String(f.id).padStart(4, "0")}`;
      document.getElementById("p-coffito").value = f.coffito || "";
      document.getElementById("p-nome").value = f.nome || "";
      document.getElementById("p-email").value = f.email || "";
      document.getElementById("p-tel").value = f.telefone || "";
      document.getElementById("p-esp").value = f.especialidade || "";
      document.getElementById("p-loc").value = f.localidade || "";
      document.getElementById("p-desc").value = f.descricao || "";

      // Só pinta com foto válida: sem ela, não apaga a prévia de um arquivo
      // que a pessoa tenha escolhido enquanto a tela carregava.
      if (urlFoto(f.foto)) aplicarFoto(document.querySelector(".edit-avatar-card .avatar-lg"), f.foto);
    })
    .catch((err) => RehabitToast.erro(err.message));

  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    if (!RehabitCampos.validar(form)) return;

    const senhaAtual = document.getElementById("p-senha-atual")?.value || "";
    const novaSenha = document.getElementById("p-senha-nova")?.value || "";
    if (novaSenha && novaSenha.length < 8) {
      RehabitCampos.marcar(document.getElementById("p-senha-nova"), "Use ao menos 8 caracteres.");
      RehabitToast.erro("A nova senha deve ter ao menos 8 caracteres.");
      return;
    }
    if (novaSenha && !senhaAtual) {
      RehabitCampos.marcar(document.getElementById("p-senha-atual"), "Obrigatória para trocar de senha.");
      RehabitToast.erro("Informe a senha atual para trocar de senha.");
      return;
    }

    const submitBtn = form.querySelector(".btn-primary");
    submitBtn.disabled = true;
    const textoOriginal = submitBtn.textContent;
    submitBtn.textContent = "Salvando...";
    // O loader cobre também o envio da foto, que não passa pelo apiPut; o
    // apiPut, chamado com ele já de pé, mantém o "Salvando".
    RehabitLoader.show("Salvando");

    try {
      // O seletor de foto (script.js) guarda o arquivo escolhido em
      // arquivoFotoProfissional; sem enviá-lo ao /uploads, "Alterar foto" só
      // trocava a prévia na tela e o perfil continuava com a imagem antiga.
      let foto = fotoAtual;
      if (arquivoFotoProfissional) {
        const formData = new FormData();
        formData.append("arquivo", arquivoFotoProfissional);
        const uploadResponse = await buscar(`${API_BASE_URL}/uploads`, {
          method: "POST",
          // O envio de fotos exige login, como qualquer outra rota da API.
          headers: cabecalhosAutenticados(),
          body: formData,
        });
        const uploadDados = await uploadResponse.json().catch(() => ({}));
        if (!uploadResponse.ok) {
          RehabitToast.erro(uploadDados.mensagem || "Não foi possível enviar a foto de perfil.");
          return;
        }
        foto = uploadDados.url;
      }

      const atualizado = await apiPut(`/fisioterapeutas/${idAlvo}`, {
        nome: document.getElementById("p-nome").value.trim(),
        email: document.getElementById("p-email").value.trim(),
        telefone: document.getElementById("p-tel").value.trim() || null,
        especialidade: document.getElementById("p-esp").value.trim() || null,
        localidade: document.getElementById("p-loc").value.trim() || null,
        descricao: document.getElementById("p-desc").value.trim() || null,
        foto,
        senhaAtual: senhaAtual || null,
        novaSenha: novaSenha || null,
      });

      if (!editandoOutroProfissional) {
        localStorage.setItem(
          "rehabit_usuario",
          JSON.stringify(Object.assign({}, sessao, {
            nome: atualizado.nome,
            email: atualizado.email,
            foto: atualizado.foto,
          }))
        );
      }

      // Com senha nova, a API encerra todas as sessões da conta editada: se
      // é a própria, avisa e leva ao login em vez de cair nele pelo próximo 401.
      if (novaSenha && !editandoOutroProfissional) {
        RehabitToast.sucesso("Perfil e senha atualizados. Entre de novo com a nova senha.");
        localStorage.removeItem("rehabit_usuario");
        setTimeout(() => {
          window.location.href = paginaLogin();
        }, 1800);
        return;
      }
      RehabitToast.sucesso("Perfil atualizado com sucesso.");
      setTimeout(() => {
        window.location.href = editandoOutroProfissional
          ? `${paginaTema("perfil-profissional")}?id=${idAlvo}`
          : paginaTema("perfil-profissional");
      }, 1200);
    } catch (err) {
      RehabitToast.erro(err.message);
    } finally {
      RehabitLoader.hide();
      submitBtn.disabled = false;
      submitBtn.textContent = textoOriginal;
    }
  });
})();
