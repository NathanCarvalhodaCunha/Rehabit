(function editarPerfilInstituicao() {
  const form = document.querySelector(".edit-grid");
  if (!form || !document.getElementById("i-cnpj")) return;

  const sessao = getSessao();
  if (!sessao || sessao.tipo !== "CLINICA") return;

  let fotoAtual = null;

  apiGet(`/clinicas/${sessao.id}`)
    .then((clinica) => {
      fotoAtual = clinica.foto;
      document.querySelector(".edit-name").textContent = clinica.nome;
      document.querySelector(".edit-role").textContent = clinica.subtitulo || "";
      document.querySelector(".edit-id").textContent = `ID-${String(clinica.id).padStart(4, "0")}`;
      document.getElementById("i-cnpj").value = clinica.cnpj || "";
      document.getElementById("i-nome").value = clinica.nome || "";
      document.getElementById("i-email").value = clinica.email || "";
      document.getElementById("i-tel").value = clinica.telefone || "";
      document.getElementById("i-sub").value = clinica.subtitulo || "";
      document.getElementById("i-loc").value = clinica.endereco || "";
      document.getElementById("i-desc").value = clinica.descricao || "";

      // Só pinta com foto válida: sem ela, não apaga a prévia de um arquivo
      // que a pessoa tenha escolhido enquanto a tela carregava.
      if (urlFoto(clinica.foto)) aplicarFoto(document.querySelector(".edit-avatar-card .avatar-lg"), clinica.foto);
    })
    .catch((err) => RehabitToast.erro(err.message));

  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    if (!RehabitCampos.validar(form)) return;

    const senhaAtual = document.getElementById("i-senha-atual").value;
    const novaSenha = document.getElementById("i-senha-nova").value;
    if (novaSenha && novaSenha.length < 8) {
      RehabitCampos.marcar(document.getElementById("i-senha-nova"), "Use ao menos 8 caracteres.");
      RehabitToast.erro("A nova senha deve ter ao menos 8 caracteres.");
      return;
    }
    if (novaSenha && !senhaAtual) {
      RehabitCampos.marcar(document.getElementById("i-senha-atual"), "Obrigatória para trocar de senha.");
      RehabitToast.erro("Informe a senha atual para trocar de senha.");
      return;
    }

    const submitBtn = form.querySelector(".btn-primary");
    submitBtn.disabled = true;
    const textoOriginal = submitBtn.textContent;
    submitBtn.textContent = "Salvando...";

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

      const atualizado = await apiPut(`/clinicas/${sessao.id}`, {
        nome: document.getElementById("i-nome").value.trim(),
        cnpj: document.getElementById("i-cnpj").value.trim(),
        email: document.getElementById("i-email").value.trim(),
        telefone: document.getElementById("i-tel").value.trim() || null,
        endereco: document.getElementById("i-loc").value.trim() || null,
        subtitulo: document.getElementById("i-sub").value.trim() || null,
        descricao: document.getElementById("i-desc").value.trim() || null,
        foto,
        senhaAtual: senhaAtual || null,
        novaSenha: novaSenha || null,
      });

      localStorage.setItem(
        "rehabit_usuario",
        JSON.stringify(Object.assign({}, sessao, {
          nome: atualizado.nome,
          email: atualizado.email,
          foto: atualizado.foto,
        }))
      );

      // Com senha nova, a API encerra todas as sessões desta conta, esta
      // inclusive: em vez de cair no login pelo próximo 401, avisa antes.
      if (novaSenha) {
        RehabitToast.sucesso("Perfil e senha atualizados. Entre de novo com a nova senha.");
        localStorage.removeItem("rehabit_usuario");
        setTimeout(() => {
          window.location.href = paginaLogin();
        }, 1800);
        return;
      }
      RehabitToast.sucesso("Perfil atualizado com sucesso.");
      setTimeout(() => {
        window.location.href = paginaTema("perfil-instituicao");
      }, 1200);
    } catch (err) {
      RehabitToast.erro(err.message);
    } finally {
      submitBtn.disabled = false;
      submitBtn.textContent = textoOriginal;
    }
  });
})();
